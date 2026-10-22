package com.ashrdev.aznd.ui.workout.history

import com.ashrdev.aznd.data.workout.SessionWithSets
import com.ashrdev.aznd.domain.GraphRange
import com.ashrdev.aznd.domain.GraphWindow
import com.ashrdev.aznd.ui.calendar.dayKeyOf
import com.ashrdev.aznd.ui.calendar.todayDayKey
import java.util.Calendar

// Numbers behind the center page of Workout History. Pure functions over the history sessions,
// no UI, so they're easy to reason about (and to test).

internal data class NamedCount(val name: String, val count: Int)

internal data class HistoryStats(
    val totalSessions: Int,
    /** Sessions per week since the first session (never divided by less than one week). */
    val sessionsPerWeek: Double,
    /** Workout (preset) with the most sessions. Ties go to the one done most recently. */
    val topWorkout: NamedCount?,
    /** Exercise that appears in the most sessions (not sets). Ties go to the most recent. */
    val topExercise: NamedCount?
)

/** [sessions] are expected newest first, which is what decides the ties. */
internal fun computeHistoryStats(
    sessions: List<SessionWithSets>,
    nowMillis: Long = System.currentTimeMillis()
): HistoryStats {
    if (sessions.isEmpty()) return HistoryStats(0, 0.0, null, null)

    // The shared graph module's window maths: lifetime = from the first session until now, never
    // less than one week, so a brand-new log doesn't read as a huge per-week figure.
    val weeks = GraphWindow.windowWeeks(sessions, GraphRange.MAX, nowMillis) { it.session.startedAt }

    val topWorkout = sessions
        .groupBy { it.session.workoutId }
        .map { (_, group) ->
            val name = group.maxByOrNull { it.session.startedAt }?.session?.workoutName ?: ""
            NamedCount(name, group.size)
        }
        .maxByOrNull { it.count }

    val exerciseCounts = linkedMapOf<String, Int>()
    for (item in sessions) {
        for (name in item.sets.map { it.exerciseName }.toSet()) {
            exerciseCounts[name] = (exerciseCounts[name] ?: 0) + 1
        }
    }
    val topExercise = exerciseCounts.maxByOrNull { it.value }?.let { NamedCount(it.key, it.value) }

    return HistoryStats(
        totalSessions = sessions.size,
        sessionsPerWeek = sessions.size / weeks,
        topWorkout = topWorkout,
        topExercise = topExercise
    )
}

internal data class FrequencyPoint(val dayKey: String, val count: Int)

/**
 * For every day from the first session to today, how many sessions fall in the 7 days ending that
 * day (that day included). Plotted over time it shows whether you're training more or less often.
 * The graph's own time-range setting then decides how much of it is shown. Several sessions in one
 * day each count.
 */
internal fun rollingWeeklyCounts(sessions: List<SessionWithSets>): List<FrequencyPoint> {
    if (sessions.isEmpty()) return emptyList()
    val perDay = sessions.groupingBy { dayKeyOf(it.session.startedAt) }.eachCount()
    val today = todayDayKey()
    val start = perDay.keys.minOrNull() ?: return emptyList()

    val window = 7
    // Start the walk a window early, so the first plotted day already has its full 7 days behind it.
    val walkStart = shiftDay(start, -(window - 1))
    val keys = ArrayList<String>()
    val cal = parseDay(walkStart)
    var key = walkStart
    while (key <= today) {
        keys += key
        cal.add(Calendar.DAY_OF_YEAR, 1)
        key = dayKeyOf(cal.timeInMillis)
    }

    val counts = IntArray(keys.size) { perDay[keys[it]] ?: 0 }
    val points = ArrayList<FrequencyPoint>()
    var sum = 0
    for (i in keys.indices) {
        sum += counts[i]
        if (i >= window) sum -= counts[i - window]
        if (i >= window - 1) points += FrequencyPoint(keys[i], sum)
    }
    return points
}

private fun parseDay(dayKey: String): Calendar = Calendar.getInstance().apply {
    clear()
    set(dayKey.substring(0, 4).toInt(), dayKey.substring(5, 7).toInt() - 1, dayKey.substring(8, 10).toInt())
}

private fun shiftDay(dayKey: String, days: Int): String {
    val cal = parseDay(dayKey)
    cal.add(Calendar.DAY_OF_YEAR, days)
    return dayKeyOf(cal.timeInMillis)
}

/** Start of the given day (local time), as epoch millis: the timestamp a graph point needs. */
internal fun dayKeyMillis(dayKey: String): Long = parseDay(dayKey).timeInMillis
