package com.ashrdev.aznd.ui.calendar

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Everything in the calendar package talks in "day keys": plain "yyyy-MM-dd" strings, the same
// format the streak code already stores. They sort correctly as text, so `a < b` just works.

private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val longDayFormat = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault())
private val monthTitleFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
private val monthShortFormat = SimpleDateFormat("MMM", Locale.getDefault())

/** A calendar month. [month] is 1..12. */
data class MonthKey(val year: Int, val month: Int) : Comparable<MonthKey> {
    override fun compareTo(other: MonthKey): Int =
        compareValuesBy(this, other, { it.year }, { it.month })

    fun next(): MonthKey = if (month == 12) MonthKey(year + 1, 1) else MonthKey(year, month + 1)
    fun previous(): MonthKey = if (month == 1) MonthKey(year - 1, 12) else MonthKey(year, month - 1)

    private fun firstOfMonth(): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, 1)
        }

    fun title(): String = monthTitleFormat.format(firstOfMonth().time)
    fun shortName(): String = monthShortFormat.format(firstOfMonth().time)
    fun daysInMonth(): Int = firstOfMonth().getActualMaximum(Calendar.DAY_OF_MONTH)

    /** Weekday of the 1st, as a Calendar constant (Calendar.SUNDAY..Calendar.SATURDAY). */
    fun firstWeekday(): Int = firstOfMonth().get(Calendar.DAY_OF_WEEK)

    fun dayKey(dayOfMonth: Int): String =
        String.format(Locale.US, "%04d-%02d-%02d", year, month, dayOfMonth)
}

fun todayDayKey(): String = dayKeyFormat.format(Date())

fun dayKeyOf(millis: Long): String = dayKeyFormat.format(Date(millis))

fun monthKeyOf(dayKey: String): MonthKey =
    MonthKey(dayKey.substring(0, 4).toInt(), dayKey.substring(5, 7).toInt())

fun currentMonth(): MonthKey = monthKeyOf(todayDayKey())

fun dayOfMonthOf(dayKey: String): Int = dayKey.substring(8, 10).toInt()

fun formatDayLong(dayKey: String): String =
    runCatching { longDayFormat.format(dayKeyFormat.parse(dayKey)!!) }.getOrDefault(dayKey)

/** Every month from [start] to [end], inclusive. Empty if [start] is after [end]. */
fun monthRange(start: MonthKey, end: MonthKey): List<MonthKey> {
    val result = mutableListOf<MonthKey>()
    var cursor = start
    while (cursor <= end) {
        result += cursor
        cursor = cursor.next()
    }
    return result
}
