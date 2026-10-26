package com.ashrdev.aznd.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ashrdev.aznd.data.AppDatabase
import com.ashrdev.aznd.data.routines.ReminderEntity
import com.ashrdev.aznd.data.routines.TrackingSnapshot
import com.ashrdev.aznd.data.routines.TrackingSource
import com.ashrdev.aznd.data.routines.nextMilestone
import com.ashrdev.aznd.data.routines.schedule
import com.ashrdev.aznd.data.routines.reachedToday
import com.ashrdev.aznd.domain.streaks.CheckInfo
import com.ashrdev.aznd.domain.streaks.TrackMode
import com.ashrdev.aznd.domain.streaks.Weekdays
import com.ashrdev.aznd.domain.streaks.parseKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

object ReminderScheduler {
    const val CHANNEL = "routine_reminders"
    const val ACTION = "com.ashrdev.aznd.REMINDER"
    const val EXTRA_KIND = "kind"
    const val EXTRA_ID = "id"
    const val KIND_REMINDER = "reminder"
    const val KIND_MILESTONE = "milestone"
    const val MILESTONE_HOUR = 9

    private const val PREFS = "reminder_alarms"
    private const val KEY_CODES = "codes"
    private const val KEY_ALERTED = "alerted"
    private const val MILESTONE_BASE = 1_000_000
    private const val NOTIFY_MILESTONE_BASE = 2_000_000
    private const val SOON_MS = 3_000L
    private const val DAY_MS = 86_400_000L

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun syncAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { sync(app) } }
    }

    private fun source(context: Context): TrackingSource {
        val db = AppDatabase.getInstance(context)
        return TrackingSource(db.streakDao(), db.routineDao(), db.workoutDao(), db.foodDao(), db.bodyDao())
    }

    suspend fun sync(context: Context) {
        val db = AppDatabase.getInstance(context)
        val snapshot = source(context).once()
        val all = db.reminderDao().all()
        val streakIds = snapshot.streaks.map { it.id }.toSet()
        val routineIds = snapshot.routines.map { it.id }.toSet()
        val orphans = all.filter {
            (it.ownerType == ReminderEntity.STREAK && it.ownerId !in streakIds) ||
                (it.ownerType == ReminderEntity.ROUTINE && it.ownerId !in routineIds)
        }
        if (orphans.isNotEmpty()) db.reminderDao().deleteIds(orphans.map { it.id })
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val alerted = prefs.getStringSet(KEY_ALERTED, emptySet()).orEmpty()
        val now = System.currentTimeMillis()
        val codes = HashSet<Int>()
        for (r in all - orphans.toSet()) {
            val code = r.id.toInt()
            schedule(context, alarm, code, nextTime(r.minuteOfDay, r.weekdays, now), KIND_REMINDER, r.id)
            codes += code
        }
        for (track in snapshot.tracks.values) {
            if (!track.streak.milestoneAlerts) continue
            val code = MILESTONE_BASE + track.streak.id.toInt()
            val reached = track.reachedToday(snapshot.today)
            val at = if (reached != null && alertKey(track.streak.id, snapshot.today) !in alerted) {
                maxOf(now + SOON_MS, timeOf(snapshot.today, MILESTONE_HOUR))
            } else {
                track.nextMilestone()?.let { timeOf(it.date, MILESTONE_HOUR) }?.takeIf { it > now }
            } ?: continue
            schedule(context, alarm, code, at, KIND_MILESTONE, track.streak.id)
            codes += code
        }
        val previous = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }
        for (code in previous) if (code !in codes) cancel(context, alarm, code)
        val today = snapshot.today
        prefs.edit()
            .putStringSet(KEY_CODES, codes.map { it.toString() }.toSet())
            .putStringSet(KEY_ALERTED, alerted.filter { it.endsWith("@$today") }.toSet())
            .apply()
    }

    suspend fun handle(context: Context, kind: String?, id: Long) {
        val db = AppDatabase.getInstance(context)
        val snapshot = source(context).once()
        when (kind) {
            KIND_REMINDER -> db.reminderDao().reminder(id)?.let { r -> remind(context, snapshot, r) }
            KIND_MILESTONE -> {
                val track = snapshot.tracks[id]
                val reached = track?.reachedToday(snapshot.today)
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val alerted = prefs.getStringSet(KEY_ALERTED, emptySet()).orEmpty()
                val key = alertKey(id, snapshot.today)
                if (track != null && reached != null && key !in alerted) {
                    notify(context, NOTIFY_MILESTONE_BASE + id.toInt(), "$reached-day milestone", "${track.streak.name}: $reached days. Keep going!")
                    prefs.edit().putStringSet(KEY_ALERTED, alerted + key).apply()
                }
            }
        }
        sync(context)
    }

    private fun remind(context: Context, snapshot: TrackingSnapshot, r: ReminderEntity) {
        val title: String
        val fallback: String
        when (r.ownerType) {
            ReminderEntity.ROUTINE -> {
                val routine = snapshot.routines.firstOrNull { it.id == r.ownerId } ?: return
                val done = snapshot.doneDays(routine)
                val info = CheckInfo(routine.schedule, routine.startDate, snapshot.today, done, snapshot.routineCheck(routine)?.info?.lastAt ?: 0L)
                if (info.state(System.currentTimeMillis()).done) return
                title = routine.name
                fallback = "Time for your routine"
            }
            else -> {
                val track = snapshot.tracks[r.ownerId] ?: return
                if (track.check?.state(System.currentTimeMillis())?.done == true) return
                title = track.streak.name
                fallback = if (track.mode == TrackMode.CHECK_IN) "Don't forget to check in today" else "Keep your streak going"
            }
        }
        notify(context, r.id.toInt(), title, r.message.ifBlank { fallback })
    }

    private fun alertKey(streakId: Long, day: String) = "$streakId@$day"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Routine reminders")
                .setDescription("Reminders and milestone alerts for routines and streaks")
                .build()
        )
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val open = launch?.let { PendingIntent.getActivity(context, id, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        manager.notify(id, notification)
    }

    private fun intent(context: Context, kind: String, id: Long): Intent =
        Intent(context, ReminderReceiver::class.java).setAction(ACTION).putExtra(EXTRA_KIND, kind).putExtra(EXTRA_ID, id)

    private fun schedule(context: Context, alarm: AlarmManager, code: Int, at: Long, kind: String, id: Long) {
        val pi = PendingIntent.getBroadcast(context, code, intent(context, kind, id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) ?: return
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun cancel(context: Context, alarm: AlarmManager, code: Int) {
        val pi = PendingIntent.getBroadcast(
            context, code, Intent(context, ReminderReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarm.cancel(pi)
        pi.cancel()
    }

    fun nextTime(minuteOfDay: Int, weekdays: Int, now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        repeat(7) {
            val index = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
            if (weekdays == 0 || weekdays and Weekdays.bit(index) != 0) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return now + DAY_MS
    }

    private fun timeOf(day: String, hour: Int): Long = parseKey(day).apply { set(Calendar.HOUR_OF_DAY, hour) }.timeInMillis
}
