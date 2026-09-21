package com.ashrdev.aznd.ui.streaks

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