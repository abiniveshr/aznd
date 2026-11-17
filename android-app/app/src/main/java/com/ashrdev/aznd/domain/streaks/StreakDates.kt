package com.ashrdev.aznd.domain.streaks

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Day keys are "yyyy-MM-dd" in local time. Inside the streak code a day is an "epoch day": whole days
// since 1970-01-01, counted on the calendar (not by dividing milliseconds), so DST can't skew it.
// All of this is plain arithmetic, so it needs no Calendar or formatter and is safe on any thread.

private const val DAY_MS = 86_400_000L

/** Days since 1970-01-01 for a civil date. [month] is 1..12; out-of-range months and days roll over. */
internal fun epochDayOf(year: Int, month: Int, day: Int): Int {
    val y0 = year + Math.floorDiv(month - 1, 12)
    val m = Math.floorMod(month - 1, 12) + 1
    val y = if (m <= 2) y0 - 1 else y0
    val era = Math.floorDiv(y, 400)
    val yoe = y - era * 400
    val doy = (153 * ((m + 9) % 12) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

/** Writes year, month (1..12) and day of month for [epochDay] into [out]. */
private fun civilOf(epochDay: Int, out: IntArray) {
    val z = epochDay + 719468
    val era = Math.floorDiv(z, 146097)
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val m = if (mp < 10) mp + 3 else mp - 9
    out[0] = yoe + era * 400 + if (m <= 2) 1 else 0
    out[1] = m
    out[2] = doy - (153 * mp + 2) / 5 + 1
}

private fun digit(c: Char): Int = c - '0'

/** The epoch day of [key], or null if it isn't a date. */
internal fun epochDayOrNull(key: String): Int? {
    if (key.length == 10 && key[4] == '-' && key[7] == '-') {
        var ok = true
        for (i in 0 until 10) if (i != 4 && i != 7 && key[i] !in '0'..'9') ok = false
        if (ok) {
            val y = digit(key[0]) * 1000 + digit(key[1]) * 100 + digit(key[2]) * 10 + digit(key[3])
            val m = digit(key[5]) * 10 + digit(key[6])
            val d = digit(key[8]) * 10 + digit(key[9])
            return epochDayOf(y, m, d)
        }
    }
    val parts = key.split('-')
    if (parts.size != 3) return null
    val y = parts[0].trim().toIntOrNull() ?: return null
    val m = parts[1].trim().toIntOrNull() ?: return null
    val d = parts[2].trim().toIntOrNull() ?: return null
    return epochDayOf(y, m, d)
}

/** Like [epochDayOrNull], falling back to today for something that isn't a date. */
internal fun epochDayOfKey(key: String): Int = epochDayOrNull(key) ?: todayEpochDay()

internal fun keyOfEpochDay(epochDay: Int): String {
    val c = IntArray(3)
    civilOf(epochDay, c)
    val sb = StringBuilder(10)
    val year = c[0]
    if (year < 1000) {
        sb.append('0')
        if (year < 100) sb.append('0')
        if (year < 10) sb.append('0')
    }
    sb.append(year).append('-')
    if (c[1] < 10) sb.append('0')
    sb.append(c[1]).append('-')
    if (c[2] < 10) sb.append('0')
    sb.append(c[2])
    return sb.toString()
}

/** Monday = 0 .. Sunday = 6. 1970-01-01 was a Thursday. */
internal fun weekdayOfEpochDay(epochDay: Int): Int = Math.floorMod(epochDay + 3, 7)

/** The local calendar day containing [ms], in the device's current time zone. */
internal fun epochDayOfMillis(ms: Long): Int =
    Math.floorDiv(ms + TimeZone.getDefault().getOffset(ms), DAY_MS).toInt()

internal fun todayEpochDay(): Int = epochDayOfMillis(System.currentTimeMillis())

internal fun dayKeyOfMillis(ms: Long): String = keyOfEpochDay(epochDayOfMillis(ms))

/** Local midnight of [epochDay]. Pass one [cal] when converting many days. */
internal fun localMidnightMillis(epochDay: Int, cal: Calendar = Calendar.getInstance()): Long {
    val c = IntArray(3)
    civilOf(epochDay, c)
    cal.clear()
    cal.set(c[0], c[1] - 1, c[2], 0, 0, 0)
    return cal.timeInMillis
}

/** Local noon of [epochDay], for formatting a date without landing on a DST gap at midnight. */
internal fun noonOfEpochDay(epochDay: Int): Date {
    val c = IntArray(3)
    civilOf(epochDay, c)
    val cal = Calendar.getInstance()
    cal.clear()
    cal.set(c[0], c[1] - 1, c[2], 12, 0, 0)
    return cal.time
}

/** A SimpleDateFormat per thread, following the device's current locale and time zone. */
internal class LocalFormat(private val pattern: String) {
    private class Entry(val locale: Locale, val format: SimpleDateFormat)

    private val local = ThreadLocal<Entry>()

    fun format(date: Date): String {
        val locale = Locale.getDefault()
        var entry = local.get()
        if (entry == null || entry.locale != locale) {
            entry = Entry(locale, SimpleDateFormat(pattern, locale))
            local.set(entry)
        }
        entry.format.timeZone = TimeZone.getDefault()
        return entry.format.format(date)
    }
}

private val displayFormat = LocalFormat("EEE d MMM yyyy")
private val monthFormat = LocalFormat("MMMM yyyy")
private val shortFormat = LocalFormat("d MMM yy")

fun todayKey(): String = keyOfEpochDay(todayEpochDay())

fun dateKey(year: Int, month: Int, dayOfMonth: Int): String =
    String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)

/** Local midnight of [key] (today's if it isn't a date). */
fun parseKey(key: String): Calendar {
    val cal = Calendar.getInstance()
    val c = IntArray(3)
    civilOf(epochDayOfKey(key), c)
    cal.clear()
    cal.set(c[0], c[1] - 1, c[2], 0, 0, 0)
    return cal
}

fun nextDayKey(key: String): String = keyOfEpochDay(epochDayOfKey(key) + 1)

fun previousDayKey(key: String): String = keyOfEpochDay(epochDayOfKey(key) - 1)

fun daysBetweenInclusive(startKey: String, endKey: String): Int =
    epochDayOfKey(endKey) - epochDayOfKey(startKey) + 1

fun displayDate(key: String): String {
    val day = epochDayOrNull(key) ?: return key
    return displayFormat.format(noonOfEpochDay(day))
}

fun monthLabel(year: Int, month: Int): String {
    val cal = Calendar.getInstance()
    cal.set(year, month, 1)
    return monthFormat.format(cal.time)
}

fun monthsBetween(startKey: String, endKey: String): List<Pair<Int, Int>> {
    val c = IntArray(3)
    civilOf(epochDayOfKey(startKey), c)
    val first = c[0] * 12 + c[1] - 1
    civilOf(epochDayOfKey(endKey), c)
    val last = c[0] * 12 + c[1] - 1
    val result = ArrayList<Pair<Int, Int>>()
    for (n in first..last) result += Math.floorDiv(n, 12) to Math.floorMod(n, 12)
    return result
}

/** "5 Mar 26": compact label for graph axes. */
fun shortDate(key: String): String {
    val day = epochDayOrNull(key) ?: return key
    return shortFormat.format(noonOfEpochDay(day))
}

fun addDaysKey(key: String, days: Int): String = keyOfEpochDay(epochDayOfKey(key) + days)

/** Every day key from [startKey] to [endKey], inclusive. Empty if start is after end. */
fun dayKeysBetween(startKey: String, endKey: String): List<String> {
    if (startKey > endKey) return emptyList()
    val first = epochDayOfKey(startKey)
    val last = epochDayOfKey(endKey)
    if (last < first) return emptyList()
    val result = ArrayList<String>(last - first + 1)
    for (day in first..last) result.add(keyOfEpochDay(day))
    return result
}

/** Whole days from [fromKey] to [toKey] (negative if toKey is earlier). */
fun daysBetween(fromKey: String, toKey: String): Int = epochDayOfKey(toKey) - epochDayOfKey(fromKey)
