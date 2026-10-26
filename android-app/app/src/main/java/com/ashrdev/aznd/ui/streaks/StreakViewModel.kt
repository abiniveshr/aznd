package com.ashrdev.aznd.ui.streaks

import com.ashrdev.aznd.data.routines.schedule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ashrdev.aznd.data.routines.ReminderDao
import com.ashrdev.aznd.data.routines.ReminderEntity
import com.ashrdev.aznd.data.routines.RoutineDao
import com.ashrdev.aznd.data.routines.RoutineEntity
import com.ashrdev.aznd.data.routines.RoutineLogEntity
import com.ashrdev.aznd.data.routines.StreakTrack
import com.ashrdev.aznd.data.routines.TrackingSnapshot
import com.ashrdev.aznd.data.routines.TrackingSource
import com.ashrdev.aznd.data.streaks.StreakBreakEntity
import com.ashrdev.aznd.data.streaks.StreakCheckInEntity
import com.ashrdev.aznd.data.streaks.StreakDao
import com.ashrdev.aznd.data.streaks.StreakEntity
import com.ashrdev.aznd.data.streaks.StreakSavedDayEntity
import com.ashrdev.aznd.domain.streaks.CheckInfo
import com.ashrdev.aznd.domain.streaks.StreakTracking
import com.ashrdev.aznd.domain.streaks.TrackMode
import com.ashrdev.aznd.domain.streaks.computeCurrentStreak
import com.ashrdev.aznd.domain.streaks.computeStreakStats
import com.ashrdev.aznd.domain.streaks.dayKeysBetween
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StreakWithCount(
    val streak: StreakEntity,
    val currentStreak: Int,
    val mode: TrackMode = TrackMode.AUTO,
    val checkedInToday: Boolean = false,
    val check: CheckInfo? = null
)

data class StreakSnapshot(val streak: StreakEntity, val current: Int, val longest: Int)

class RoutineStatus(
    val routine: RoutineEntity,
    val doneToday: Boolean,
    val dueToday: Boolean,
    val doneThisWeek: Int,
    val streak: StreakWithCount?,
    val info: CheckInfo,
    val manual: Boolean,
    val checkStreakId: Long?
)

class StreakViewModel(
    private val dao: StreakDao,
    private val routineDao: RoutineDao,
    private val reminderDao: ReminderDao,
    private val tracking: TrackingSource,
    private val onChanged: () -> Unit
) : ViewModel() {

    val snapshot: StateFlow<TrackingSnapshot?> = tracking.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val streaksWithCounts: StateFlow<List<StreakWithCount>> = snapshot
        .map { s -> s?.let { counts(it) }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val routines: StateFlow<List<RoutineStatus>> = snapshot
        .map { s -> s?.let { routineStatuses(it) }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streaksWithSavedDays: StateFlow<List<StreakEntity>> = dao.getStreaksWithSavedDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun countOf(t: StreakTrack): Int = computeCurrentStreak(t.streak.startDate, t.tracked.breaks, t.tracked.asOf)

    private fun withCount(t: StreakTrack, today: String) = StreakWithCount(t.streak, countOf(t), t.mode, today in t.checkIns, t.check)

    private fun counts(s: TrackingSnapshot): List<StreakWithCount> = s.streaks.mapNotNull { st -> s.tracks[st.id]?.let { withCount(it, s.today) } }

    private fun routineStatuses(s: TrackingSnapshot): List<RoutineStatus> = s.routines.map { r ->
        val done = s.doneDays(r)
        val monday = StreakTracking.weekStart(s.today)
        val linked = s.streakOf(r)?.let { st -> s.tracks[st.id]?.let { withCount(it, s.today) } }
        val control = s.routineCheck(r)
        RoutineStatus(
            routine = r,
            doneToday = s.today in done,
            dueToday = r.schedule.due(s.today),
            doneThisWeek = dayKeysBetween(monday, s.today).count { it in done },
            streak = linked,
            info = control?.info ?: CheckInfo(r.schedule, r.startDate, s.today, done, 0L),
            manual = control != null,
            checkStreakId = control?.streakId
        )
    }

    fun trackFor(streakId: Long): Flow<StreakTrack?> = snapshot.map { it?.tracks?.get(streakId) }

    suspend fun getStreak(id: Long): StreakEntity? = dao.getStreak(id)

    suspend fun streakSnapshots(): List<StreakSnapshot> {
        val s = tracking.once()
        return s.streaks.mapNotNull { st ->
            val t = s.tracks[st.id] ?: return@mapNotNull null
            val stats = computeStreakStats(st.startDate, t.tracked.breaks, emptyList(), t.tracked.asOf)
            StreakSnapshot(st, stats.current, stats.longest)
        }
    }

    suspend fun loadStreak(id: Long): Pair<StreakEntity, List<ReminderEntity>>? {
        val streak = dao.getStreak(id) ?: return null
        return streak to reminderDao.forOwner(ReminderEntity.STREAK, id)
    }

    suspend fun loadRoutine(id: Long): Pair<RoutineEntity, List<ReminderEntity>>? {
        val routine = routineDao.routine(id) ?: return null
        return routine to reminderDao.forOwner(ReminderEntity.ROUTINE, id)
    }

    fun saveStreak(streak: StreakEntity, reminders: List<ReminderEntity>) {
        viewModelScope.launch {
            val id = if (streak.id == 0L) dao.insertStreak(streak) else streak.id.also { dao.updateStreak(streak) }
            reminderDao.replaceForOwner(ReminderEntity.STREAK, id, reminders)
            onChanged()
        }
    }

    fun saveRoutine(routine: RoutineEntity, reminders: List<ReminderEntity>) {
        viewModelScope.launch {
            val id = if (routine.id == 0L) routineDao.insertRoutine(routine) else routine.id.also { routineDao.updateRoutine(routine) }
            reminderDao.replaceForOwner(ReminderEntity.ROUTINE, id, reminders)
            val linked = dao.streakForRoutine(id)
            if (routine.trackAsStreak) {
                val streak = StreakEntity(
                    id = linked?.id ?: 0L,
                    name = routine.name,
                    startDate = routine.startDate,
                    photoUri = routine.photoUri,
                    quote = linked?.quote,
                    milestones = routine.milestones,
                    bannerUri = routine.bannerUri,
                    trackMode = routine.trackMode,
                    milestoneAlerts = routine.milestoneAlerts,
                    routineId = id
                )
                if (linked == null) dao.insertStreak(streak) else dao.updateStreak(streak)
            } else if (linked != null) {
                dao.deleteStreak(linked.id)
                reminderDao.deleteForOwner(ReminderEntity.STREAK, linked.id)
            }
            onChanged()
        }
    }

    fun deleteRoutine(id: Long) {
        viewModelScope.launch {
            dao.streakForRoutine(id)?.let {
                dao.deleteStreak(it.id)
                reminderDao.deleteForOwner(ReminderEntity.STREAK, it.id)
            }
            reminderDao.deleteForOwner(ReminderEntity.ROUTINE, id)
            routineDao.deleteRoutine(id)
            onChanged()
        }
    }

    fun toggleRoutineDone(routineId: Long, date: String, done: Boolean) {
        viewModelScope.launch {
            if (done) routineDao.insertLog(RoutineLogEntity(routineId = routineId, date = date, at = System.currentTimeMillis()))
            else routineDao.deleteLog(routineId, date)
            onChanged()
        }
    }

    fun updateStreakPhoto(streakId: Long, photoUri: String?) {
        viewModelScope.launch { dao.updateStreakPhoto(streakId, photoUri) }
    }

    fun updateStreakBanner(streakId: Long, bannerUri: String?) {
        viewModelScope.launch { dao.updateStreakBanner(streakId, bannerUri) }
    }

    fun deleteStreak(id: Long) {
        viewModelScope.launch {
            val streak = dao.getStreak(id)
            streak?.routineId?.let { rid -> routineDao.routine(rid)?.let { routineDao.updateRoutine(it.copy(trackAsStreak = false)) } }
            dao.deleteStreak(id)
            reminderDao.deleteForOwner(ReminderEntity.STREAK, id)
            onChanged()
        }
    }

    fun breaksForStreak(streakId: Long): Flow<List<String>> = dao.getBreaksForStreak(streakId)

    fun markBreak(streakId: Long, date: String) {
        viewModelScope.launch {
            dao.insertBreak(StreakBreakEntity(streakId = streakId, date = date))
            onChanged()
        }
    }

    fun unmarkBreak(streakId: Long, date: String) {
        viewModelScope.launch {
            dao.deleteBreak(streakId, date)
            onChanged()
        }
    }

    fun setCheckIn(streakId: Long, date: String, checked: Boolean) {
        viewModelScope.launch {
            if (checked) dao.insertCheckIn(StreakCheckInEntity(streakId = streakId, date = date, at = System.currentTimeMillis()))
            else dao.deleteCheckIn(streakId, date)
            onChanged()
        }
    }

    val noSavedDays: Flow<List<StreakSavedDayEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())

    fun savedDaysForStreak(streakId: Long): Flow<List<StreakSavedDayEntity>> =
        dao.getSavedDaysForStreak(streakId)

    suspend fun getSavedDay(streakId: Long, date: String): StreakSavedDayEntity? =
        dao.getSavedDay(streakId, date)

    fun saveDay(streakId: Long, date: String, remark: String, photoUri: String?) {
        viewModelScope.launch {
            val track = tracking.once().tracks[streakId] ?: return@launch
            val count = computeCurrentStreak(track.streak.startDate, track.tracked.breaks, minOf(date, track.tracked.asOf))
            dao.upsertSavedDay(
                StreakSavedDayEntity(
                    streakId = streakId,
                    date = date,
                    remark = remark,
                    photoUri = photoUri,
                    streakCountAtSave = count
                )
            )
        }
    }

    fun deleteSavedDay(streakId: Long, date: String) {
        viewModelScope.launch { dao.deleteSavedDay(streakId, date) }
    }
}

class StreakViewModelFactory(
    private val dao: StreakDao,
    private val routineDao: RoutineDao,
    private val reminderDao: ReminderDao,
    private val tracking: TrackingSource,
    private val onChanged: () -> Unit
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        StreakViewModel(dao, routineDao, reminderDao, tracking, onChanged) as T
}
