package com.ashrdev.aznd.ui.streaks

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val displayFormat = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault())
private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

fun todayKey(): String = keyFormat.format(Date())

fun dateKey(year: Int, month: Int, dayOfMonth: Int): String =
    String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)

fun parseKey(key: String): Calendar {
    val cal = Calendar.getInstance()
    cal.time = keyFormat.parse(key) ?: Date()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal
}

fun nextDayKey(key: String): String {
    val cal = parseKey(key)
    cal.add(Calendar.DAY_OF_YEAR, 1)
    return keyFormat.format(cal.time)
}

fun previousDayKey(key: String): String {
    val cal = parseKey(key)
    cal.add(Calendar.DAY_OF_YEAR, -1)
    return keyFormat.format(cal.time)
}

fun daysBetweenInclusive(startKey: String, endKey: String): Int {
    val startMillis = parseKey(startKey).timeInMillis
    val endMillis = parseKey(endKey).timeInMillis
    val diff = ((endMillis - startMillis) / (24L * 60 * 60 * 1000)).toInt()
    return diff + 1
}

fun displayDate(key: String): String {
    val date = keyFormat.parse(key) ?: return key
    return displayFormat.format(date)
}

fun monthLabel(year: Int, month: Int): String {
    val cal = Calendar.getInstance()
    cal.set(year, month, 1)
    return monthFormat.format(cal.time)
}

fun monthsBetween(startKey: String, endKey: String): List<Pair<Int, Int>> {
    val start = parseKey(startKey)
    val end = parseKey(endKey)
    val result = mutableListOf<Pair<Int, Int>>()
    val cursor = Calendar.getInstance()
    cursor.set(start.get(Calendar.YEAR), start.get(Calendar.MONTH), 1)
    val endMarker = Calendar.getInstance()
    endMarker.set(end.get(Calendar.YEAR), end.get(Calendar.MONTH), 1)
    while (!cursor.after(endMarker)) {
        result += cursor.get(Calendar.YEAR) to cursor.get(Calendar.MONTH)
        cursor.add(Calendar.MONTH, 1)
    }
    return result
}