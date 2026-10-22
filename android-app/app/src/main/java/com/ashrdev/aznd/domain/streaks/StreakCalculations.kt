package com.ashrdev.aznd.domain.streaks

enum class DayStatus { NONE, ON_STREAK, BROKEN }

fun computeCurrentStreak(startDate: String, breaks: Set<String>, asOfDate: String = todayKey()): Int {
    if (startDate > asOfDate) return 0
    val lastBreak = breaks.filter { it <= asOfDate }.maxOrNull()
    if (lastBreak == asOfDate) return 0
    val runStart = if (lastBreak != null) nextDayKey(lastBreak) else startDate
    if (runStart > asOfDate) return 0
    return daysBetweenInclusive(runStart, asOfDate)
}

fun dayStatus(date: String, startDate: String, todayKey: String, breaks: Set<String>): DayStatus {
    if (date < startDate || date > todayKey) return DayStatus.NONE
    return if (date in breaks) DayStatus.BROKEN else DayStatus.ON_STREAK
}

/**
 * Everything the streak screens show, worked out once from the start date, the slip days and the
 * milestone list. A slip day counts as 0 and the next day starts again at 1.
 *
 *  - [days] / [counts]: one entry per day from the start date to today (the graph's data).
 *  - [reachedMilestones]: date -> milestone, for every day a milestone was hit (any run).
 *  - [upcomingMilestones]: date -> milestone, where the current run will hit each milestone still ahead.
 */
data class StreakStats(
    val days: List<String>,
    val counts: List<Int>,
    val current: Int,
    val longest: Int,
    val timesBroken: Int,
    val reachedMilestones: Map<String, Int>,
    val upcomingMilestones: Map<String, Int>,
    val previousMilestone: Int,
    val nextMilestone: Int?,
    val nextMilestoneDate: String?,
    val daysLeft: Int?
)

fun computeStreakStats(
    startDate: String,
    breaks: Set<String>,
    milestones: List<Int>,
    today: String = todayKey()
): StreakStats {
    val days = dayKeysBetween(startDate, today)
    val counts = ArrayList<Int>(days.size)
    var run = 0
    for (day in days) {
        run = if (day in breaks) 0 else run + 1
        counts.add(run)
    }
    val current = counts.lastOrNull() ?: 0
    val sorted = milestones.filter { it > 0 }.distinct().sorted()
    val milestoneSet = sorted.toSet()

    val reached = LinkedHashMap<String, Int>()
    for (i in days.indices) {
        if (counts[i] > 0 && counts[i] in milestoneSet) reached[days[i]] = counts[i]
    }

    // A streak that hasn't started yet reaches milestone m on startDate + m - 1; a running one
    // reaches it (m - current) days from today.
    val upcoming = LinkedHashMap<String, Int>()
    for (m in sorted) {
        if (m <= current) continue
        val date = if (days.isEmpty()) addDaysKey(startDate, m - 1) else addDaysKey(today, m - current)
        upcoming[date] = m
    }

    val next = sorted.firstOrNull { it > current }
    val nextDate = upcoming.entries.firstOrNull { it.value == next }?.key
    return StreakStats(
        days = days,
        counts = counts,
        current = current,
        longest = counts.maxOrNull() ?: 0,
        timesBroken = breaks.count { it >= startDate && it <= today },
        reachedMilestones = reached,
        upcomingMilestones = upcoming,
        previousMilestone = sorted.filter { it <= current }.maxOrNull() ?: 0,
        nextMilestone = next,
        nextMilestoneDate = nextDate,
        daysLeft = nextDate?.let { daysBetween(today, it).coerceAtLeast(0) }
    )
}
