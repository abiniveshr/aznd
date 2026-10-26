package com.ashrdev.aznd.domain.streaks

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class TrackMode(val id: String, val label: String, val blurb: String) {
    AUTO("auto", "Automatic check-in", "Checked in for you every day until you break the streak"),
    CHECK_IN("check_in", "Manual check-in", "A day counts only when you check in; a missed day breaks it");

    companion object {
        fun fromId(id: String?): TrackMode = if (id == CHECK_IN.id) CHECK_IN else AUTO
    }
}

enum class RoutineType(val id: String, val label: String, val blurb: String) {
    CUSTOM("custom", "Custom", "You tick it off yourself"),
    WORKOUT("workout", "Workout", "Done when a workout is logged"),
    NUTRITION("nutrition", "Nutrition", "Done when food is logged"),
    BODY("body", "Body log", "Done when bodyweight or a measurement is logged");

    companion object {
        fun fromId(id: String?): RoutineType = entries.firstOrNull { it.id == id } ?: CUSTOM
    }
}

enum class RoutineFrequency(val id: String, val label: String) {
    DAILY("daily", "Every day"),
    WEEKDAYS("weekdays", "Chosen days"),
    PER_WEEK("per_week", "Times a week"),
    CUSTOM("custom", "Custom");

    companion object {
        fun fromId(id: String?): RoutineFrequency = entries.firstOrNull { it.id == id } ?: DAILY
    }
}

enum class RoutineUnit(val id: String, val label: String, val singular: String) {
    HOURS("hours", "hours", "hour"),
    DAYS("days", "days", "day"),
    WEEKS("weeks", "weeks", "week"),
    MONTHS("months", "months", "month");

    companion object {
        fun fromId(id: String?): RoutineUnit = entries.firstOrNull { it.id == id } ?: DAYS
    }
}

object Weekdays {
    const val ALL = 0b1111111
    val SHORT = listOf("M", "T", "W", "T", "F", "S", "S")
    val NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun bit(index: Int): Int = 1 shl index

    fun indexOf(key: String): Int = (parseKey(key).get(Calendar.DAY_OF_WEEK) + 5) % 7

    fun has(mask: Int, key: String): Boolean = mask == 0 || mask and bit(indexOf(key)) != 0

    fun label(mask: Int): String = when {
        mask == 0 || mask == ALL -> "Every day"
        mask == 0b0011111 -> "Weekdays"
        mask == 0b1100000 -> "Weekends"
        else -> NAMES.indices.filter { mask and bit(it) != 0 }.joinToString(" ") { NAMES[it] }
    }
}

class PeriodState(val perDay: Boolean, val done: Boolean)

class RoutineSchedule(
    val frequency: RoutineFrequency,
    val weekdays: Int,
    val perWeek: Int,
    every: Int = 1,
    val unit: RoutineUnit = RoutineUnit.DAYS
) {
    val every: Int = every.coerceIn(1, MAX_EVERY)

    private val subDay: Boolean get() = frequency == RoutineFrequency.CUSTOM && unit == RoutineUnit.HOURS && every < 24

    private val dayLength: Int
        get() = when (unit) {
            RoutineUnit.HOURS -> kotlin.math.ceil(every / 24.0).toInt()
            RoutineUnit.DAYS -> every
            RoutineUnit.WEEKS -> every * 7
            RoutineUnit.MONTHS -> 0
        }

    private val customDaily: Boolean
        get() = frequency == RoutineFrequency.CUSTOM && (subDay || (unit != RoutineUnit.MONTHS && dayLength == 1))

    val windowed: Boolean get() = frequency == RoutineFrequency.CUSTOM && !customDaily

    val perDay: Boolean
        get() = when (frequency) {
            RoutineFrequency.DAILY -> true
            RoutineFrequency.WEEKDAYS -> weekdays == 0 || weekdays == Weekdays.ALL
            RoutineFrequency.PER_WEEK -> false
            RoutineFrequency.CUSTOM -> !subDay && customDaily
        }

    fun label(): String = when (frequency) {
        RoutineFrequency.DAILY -> "Every day"
        RoutineFrequency.WEEKDAYS -> Weekdays.label(weekdays)
        RoutineFrequency.PER_WEEK -> "$perWeek× a week"
        RoutineFrequency.CUSTOM -> if (every == 1) "Every ${unit.singular}" else "Every $every ${unit.label}"
    }

    fun due(key: String): Boolean = when (frequency) {
        RoutineFrequency.DAILY -> true
        RoutineFrequency.WEEKDAYS -> Weekdays.has(weekdays, key)
        RoutineFrequency.PER_WEEK -> false
        RoutineFrequency.CUSTOM -> customDaily
    }

    fun windowOf(anchor: String, key: String): Pair<String, String> {
        if (unit == RoutineUnit.MONTHS) {
            fun startOf(i: Int): String {
                val cal = parseKey(anchor)
                cal.add(Calendar.MONTH, i * every)
                return dateKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            }
            val a = parseKey(anchor)
            val c = parseKey(key)
            val months = (c.get(Calendar.YEAR) - a.get(Calendar.YEAR)) * 12 + c.get(Calendar.MONTH) - a.get(Calendar.MONTH)
            var i = (months / every).coerceAtLeast(0)
            while (i > 0 && startOf(i) > key) i--
            while (startOf(i + 1) <= key) i++
            return startOf(i) to previousDayKey(startOf(i + 1))
        }
        val length = dayLength.coerceAtLeast(1)
        val index = Math.floorDiv(daysBetween(anchor, key), length).coerceAtLeast(0)
        val start = addDaysKey(anchor, index * length)
        return start to addDaysKey(start, length - 1)
    }

    fun periodState(anchor: String, today: String, done: Set<String>, lastAt: Long, nowMs: Long): PeriodState = when (frequency) {
        RoutineFrequency.DAILY -> PeriodState(true, today in done)
        RoutineFrequency.WEEKDAYS -> if (perDay) PeriodState(true, today in done) else {
            var start = today
            var steps = 0
            while (!due(start) && steps < 7) {
                start = previousDayKey(start)
                steps++
            }
            var end = nextDayKey(today)
            steps = 0
            while (!due(end) && steps < 7) {
                end = nextDayKey(end)
                steps++
            }
            PeriodState(false, done.any { it >= start && it < end })
        }
        RoutineFrequency.PER_WEEK -> {
            val monday = StreakTracking.weekStart(today)
            PeriodState(false, today in done || done.count { it >= monday && it <= today } >= perWeek.coerceIn(1, 7))
        }
        RoutineFrequency.CUSTOM -> when {
            subDay -> PeriodState(false, if (lastAt > 0) nowMs < lastAt + every * 3_600_000L else today in done)
            windowed -> {
                val (start, end) = windowOf(anchor, today)
                PeriodState(false, done.any { it >= start && it <= end })
            }
            else -> PeriodState(true, today in done)
        }
    }

    companion object {
        const val MAX_EVERY = 999
        val DAILY = RoutineSchedule(RoutineFrequency.DAILY, 0, 3)
    }
}

class CheckInfo(
    val schedule: RoutineSchedule,
    val anchor: String,
    val today: String,
    val done: Set<String>,
    val lastAt: Long
) {
    fun state(nowMs: Long): PeriodState = schedule.periodState(anchor, today, done, lastAt, nowMs)
}

class Tracked(val breaks: Set<String>, val asOf: String)

class BrokenStats(val events: Int)

object StreakTracking {
    private val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun dayKeyOf(ms: Long): String = synchronized(keyFormat) { keyFormat.format(Date(ms)) }

    fun weekStart(key: String): String = addDaysKey(key, -Weekdays.indexOf(key))

    fun tracked(
        mode: TrackMode,
        startDate: String,
        today: String,
        manualBreaks: Set<String>,
        checkIns: Set<String>,
        done: Set<String>?,
        schedule: RoutineSchedule?,
        trustBeforeFirst: Boolean = false
    ): Tracked = when {
        mode == TrackMode.CHECK_IN -> {
            val from = checkIns.minOrNull()?.let { maxOf(it, startDate) } ?: startDate
            routineTracked(startDate, from, today, manualBreaks, checkIns, schedule ?: RoutineSchedule.DAILY)
        }
        mode == TrackMode.AUTO && done != null && schedule != null -> {
            val from = if (trustBeforeFirst) done.minOrNull()?.let { maxOf(it, startDate) } ?: startDate else startDate
            routineTracked(startDate, from, today, manualBreaks, done, schedule)
        }
        else -> Tracked(manualBreaks, today)
    }

    private fun routineTracked(anchor: String, from: String, today: String, manualBreaks: Set<String>, done: Set<String>, schedule: RoutineSchedule): Tracked {
        val yesterday = previousDayKey(today)
        val missed = HashSet<String>()
        when {
            schedule.frequency == RoutineFrequency.PER_WEEK -> {
                if (from <= yesterday) {
                    var monday = weekStart(from)
                    val thisWeek = weekStart(today)
                    while (monday < thisWeek) {
                        val sunday = addDaysKey(monday, 6)
                        val first = maxOf(monday, from)
                        val days = dayKeysBetween(first, sunday)
                        val need = kotlin.math.ceil(schedule.perWeek.coerceIn(1, 7) * days.size / 7.0).toInt()
                        if (days.count { it in done } < need) missed += sunday
                        monday = addDaysKey(monday, 7)
                    }
                }
            }
            schedule.windowed -> {
                var (start, end) = schedule.windowOf(anchor, maxOf(from, anchor))
                while (end < today) {
                    if (done.none { it >= start && it <= end }) missed += end
                    val next = schedule.windowOf(anchor, nextDayKey(end))
                    start = next.first
                    end = next.second
                }
            }
            else -> {
                for (day in dayKeysBetween(from, yesterday)) if (schedule.due(day) && day !in done) missed += day
            }
        }
        val pending = schedule.due(today) && today !in done
        return Tracked(missed + manualBreaks, if (pending) yesterday else today)
    }

    fun broken(stats: StreakStats, breaks: Set<String>): BrokenStats {
        var events = 0
        var previousBroken = false
        for (day in stats.days) {
            val broken = day in breaks
            if (broken && !previousBroken) events++
            previousBroken = broken
        }
        return BrokenStats(events)
    }
}
