package com.ashrdev.aznd.data.manage

import com.ashrdev.aznd.data.AppDatabase
import com.ashrdev.aznd.data.streaks.DEFAULT_MILESTONES_CSV
import com.ashrdev.aznd.data.streaks.StreakBreakEntity
import com.ashrdev.aznd.data.streaks.StreakEntity
import com.ashrdev.aznd.data.streaks.StreakSavedDayEntity
import com.ashrdev.aznd.ui.common.DayRange
import org.json.JSONArray
import org.json.JSONObject

class StreaksModule(
    private val db: AppDatabase,
    private val photos: PhotoFiles
) : DataModule {
    override val id = "streaks"
    override val app = DataApp.STREAKS
    override val title = "Streaks"
    override val description = "Your streaks with their start dates, slips, milestones and quotes"
    override val unit = "streaks"
    override val deleteNote = "Their saved days are deleted with them."
    override val canExport = true
    override val canImport = true

    private val dao get() = db.manageDao()

    override suspend fun count(range: DayRange): Int = dao.countStreaks()

    override suspend fun delete(range: DayRange) {
        val uris = dao.streakPhotoUris() + dao.savedDayPhotoUris(DayRange.All.fromKey, DayRange.All.toKey)
        dao.deleteAllStreakRows()
        photos.delete(uris)
    }

    override suspend fun export(range: DayRange): ModuleExport {
        val streaks = dao.streaks()
        val breaks = dao.streakBreaks().groupBy { it.streakId }
        return ModuleExport(
            streaks.size,
            JSONObject().put(
                "streaks",
                streaks.toJsonArray { s ->
                    JSONObject()
                        .put("name", s.name)
                        .put("startDate", s.startDate)
                        .put("quote", s.quote)
                        .put("milestones", s.milestones)
                        .put("breaks", JSONArray(breaks[s.id].orEmpty().map { it.date }))
                }
            )
        )
    }

    override suspend fun import(data: JSONObject, version: Int): ImportOutcome {
        var added = 0
        var skipped = 0
        for (s in data.arrayOrEmpty("streaks").mapObjects { it }) {
            val name = s.stringOrNull("name")?.trim().orEmpty()
            val startDate = s.stringOrNull("startDate").orEmpty()
            if (name.isEmpty() || startDate.isEmpty()) {
                skipped++
                continue
            }
            val existing = dao.streakNamed(name, startDate)
            val streakId = existing?.id ?: db.streakDao().insertStreak(
                StreakEntity(
                    name = name,
                    startDate = startDate,
                    quote = s.stringOrNull("quote"),
                    milestones = s.stringOrNull("milestones") ?: DEFAULT_MILESTONES_CSV
                )
            )
            if (existing == null) added++ else skipped++
            val dates = s.arrayOrEmpty("breaks")
            for (i in 0 until dates.length()) {
                db.streakDao().insertBreak(StreakBreakEntity(streakId = streakId, date = dates.getString(i)))
            }
        }
        return ImportOutcome(added, skipped)
    }
}

class StreakSavedDaysModule(
    private val db: AppDatabase,
    private val photos: PhotoFiles
) : DataModule {
    override val id = "streak_saved_days"
    override val app = DataApp.STREAKS
    override val title = "Saved streak days"
    override val description = "The days you saved on a streak, with their remark and day count"
    override val unit = "saved days"
    override val supportsDateRange = true
    override val dateRangeNote = "by the saved day"
    override val canExport = true
    override val canImport = true

    private val dao get() = db.manageDao()

    override suspend fun count(range: DayRange): Int = dao.countSavedDays(range.fromKey, range.toKey)

    override suspend fun delete(range: DayRange) {
        val uris = dao.savedDayPhotoUris(range.fromKey, range.toKey)
        dao.deleteSavedDays(range.fromKey, range.toKey)
        photos.delete(uris)
    }

    override suspend fun export(range: DayRange): ModuleExport {
        val streaks = dao.streaks().associateBy { it.id }
        val days = dao.savedDays(range.fromKey, range.toKey).filter { streaks.containsKey(it.streakId) }
        return ModuleExport(
            days.size,
            JSONObject().put(
                "savedDays",
                days.toJsonArray { d ->
                    val streak = streaks.getValue(d.streakId)
                    JSONObject()
                        .put("streakName", streak.name)
                        .put("streakStartDate", streak.startDate)
                        .put("date", d.date)
                        .put("remark", d.remark)
                        .put("streakCountAtSave", d.streakCountAtSave)
                        .put("savedAt", d.savedAt)
                }
            )
        )
    }

    override suspend fun import(data: JSONObject, version: Int): ImportOutcome {
        var added = 0
        var skipped = 0
        var createdStreaks = 0
        val streakIds = HashMap<Pair<String, String>, Long>()
        for (d in data.arrayOrEmpty("savedDays").mapObjects { it }) {
            val name = d.stringOrNull("streakName")?.trim().orEmpty()
            val start = d.stringOrNull("streakStartDate").orEmpty()
            val date = d.stringOrNull("date").orEmpty()
            if (name.isEmpty() || start.isEmpty() || date.isEmpty()) {
                skipped++
                continue
            }
            val key = name to start
            val streakId = streakIds[key] ?: (dao.streakNamed(name, start)?.id
                ?: db.streakDao().insertStreak(StreakEntity(name = name, startDate = start)).also { createdStreaks++ })
                .also { streakIds[key] = it }
            if (dao.savedDayExists(streakId, date) > 0) {
                skipped++
                continue
            }
            db.streakDao().upsertSavedDay(
                StreakSavedDayEntity(
                    streakId = streakId,
                    date = date,
                    remark = d.optString("remark"),
                    streakCountAtSave = d.optInt("streakCountAtSave"),
                    savedAt = d.optLong("savedAt", System.currentTimeMillis())
                )
            )
            added++
        }
        val notes = if (createdStreaks > 0) listOf("$createdStreaks streaks were created to hold the saved days") else emptyList()
        return ImportOutcome(added, skipped, notes)
    }
}
