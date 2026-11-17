package com.ashrdev.aznd.domain.macro

import com.ashrdev.aznd.domain.workout.SessionStat
import java.util.Calendar
import java.util.TimeZone

/** One logged food, free of the Room entity. */
data class FoodLog(
    val eatenAt: Long,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double
)

data class MacroTotals(
    val calories: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0
) {
    operator fun plus(o: MacroTotals) =
        MacroTotals(calories + o.calories, proteinG + o.proteinG, carbsG + o.carbsG, fatG + o.fatG)
}

object MacroStats {
    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_CARBS = 4.0
    private const val KCAL_PER_G_FAT = 9.0

    /** Calories implied by the macros, used when only macros are typed in. */
    fun kcalFromMacros(proteinG: Double, carbsG: Double, fatG: Double): Double =
        proteinG * KCAL_PER_G_PROTEIN + carbsG * KCAL_PER_G_CARBS + fatG * KCAL_PER_G_FAT

    /** The last local day looked up on a thread: its [start], the next day's [end] and the zone it was worked out in. */
    private class DayCache {
        val cal: Calendar = Calendar.getInstance()
        var zoneId: String? = null
        var start = 0L
        var end = 0L
    }

    private val dayCache = object : ThreadLocal<DayCache>() {
        override fun initialValue() = DayCache()
    }

    /** Local midnight of the day containing [ms]. */
    fun dayStart(ms: Long): Long {
        val c = dayCache.get()!!
        val zone = TimeZone.getDefault()
        if (ms >= c.start && ms < c.end && zone.id == c.zoneId) return c.start
        val cal = c.cal
        cal.timeZone = zone
        cal.timeInMillis = ms
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        c.start = start
        c.end = cal.timeInMillis
        c.zoneId = zone.id
        return start
    }

    /** Local midnight [delta] days after [dayStartMs] (negative = before). */
    fun shiftDays(dayStartMs: Long, delta: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = dayStartMs
            add(Calendar.DAY_OF_YEAR, delta)
        }.timeInMillis

    fun totalsByDay(entries: List<FoodLog>): Map<Long, MacroTotals> =
        entries.groupBy { dayStart(it.eatenAt) }.mapValues { (_, list) ->
            list.fold(MacroTotals()) { acc, e -> acc + MacroTotals(e.calories, e.proteinG, e.carbsG, e.fatG) }
        }

    /** Estimated kcal burnt by finished workout sessions, per local day. */
    fun burntByDay(sessions: List<SessionStat>): Map<Long, Double> =
        sessions.groupBy { dayStart(it.finishedAt) }.mapValues { (_, list) -> list.sumOf { it.kcal } }
}
