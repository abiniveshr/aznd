package com.ashrdev.aznd.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ashrdev.aznd.ui.calendar.MonthKey
import com.ashrdev.aznd.ui.calendar.currentMonth
import com.ashrdev.aznd.ui.calendar.dayKeyOf
import com.ashrdev.aznd.ui.calendar.todayDayKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// The universal date-range structure. Pure logic, no UI: the selector UI (DateRangeSelector) and
// any screen that filters by date (LogsViewer today) are built on these.
//
// Complexity levels, simplest first:
//   1. Presets  - ALL, DAYS_7, DAYS_30, DAYS_90, YEAR_1
//   2. A period - MONTH (one calendar month), YEAR (one calendar year)
//   3. CUSTOM   - any start and end day
// Everything resolves to one [DayRange] of inclusive "yyyy-MM-dd" day keys (the same keys the
// calendar package uses), so filtering never has to know which level was picked.

enum class DateRangeKind(val label: String) {
    ALL("All"),
    DAYS_7("7 days"),
    DAYS_30("30 days"),
    DAYS_90("90 days"),
    YEAR_1("1 year"),
    MONTH("Month"),
    YEAR("Year"),
    CUSTOM("Custom")
}

/** Inclusive span of day keys. A null side is unbounded. */
data class DayRange(val startKey: String?, val endKey: String?) {
    fun contains(dayKey: String): Boolean =
        (startKey == null || dayKey >= startKey) && (endKey == null || dayKey <= endKey)

    companion object {
        val All = DayRange(null, null)
    }
}

/** "All time", "From 1 Sep 2026" or "1 Sep 2026 – 30 Sep 2026": the caption for any range. */
fun DayRange.describe(): String {
    val start = startKey ?: return if (endKey == null) "All time" else "Until ${shortDay(endKey)}"
    val end = endKey ?: return "From ${shortDay(start)}"
    return "${shortDay(start)} – ${shortDay(end)}"
}

/** The items whose timestamp falls on a day inside [range], in their original order. */
fun <T> List<T>.filterInRange(range: DayRange, time: (T) -> Long): List<T> =
    if (range == DayRange.All) this else filter { range.contains(dayKeyOf(time(it))) }

private fun keyOf(year: Int, month: Int, day: Int): String =
    String.format(Locale.US, "%04d-%02d-%02d", year, month, day)

private fun daysAgoKey(days: Int): String =
    dayKeyOf(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }.timeInMillis)

private fun shortDay(dayKey: String): String = runCatching {
    val cal = Calendar.getInstance().apply {
        clear()
        set(dayKey.substring(0, 4).toInt(), dayKey.substring(5, 7).toInt() - 1, dayKey.substring(8, 10).toInt())
    }
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(cal.time)
}.getOrDefault(dayKey)

/**
 * What the user picked in a [DateRangeSelector]. Hoist it ([rememberDateRangeState]) to read
 * [range] and filter with it. Survives rotation and navigating away and back.
 */
@Stable
class DateRangeState(
    initialKind: DateRangeKind,
    initialMonth: MonthKey,
    initialYear: Int,
    initialCustomStart: String,
    initialCustomEnd: String
) {
    var kind by mutableStateOf(initialKind)
    var month by mutableStateOf(initialMonth)
    var year by mutableStateOf(initialYear)
    var customStart by mutableStateOf(initialCustomStart)
    var customEnd by mutableStateOf(initialCustomEnd)

    /** The resolved span for the current pick. */
    val range: DayRange
        get() = when (kind) {
            DateRangeKind.ALL -> DayRange.All
            DateRangeKind.DAYS_7 -> DayRange(daysAgoKey(6), todayDayKey())
            DateRangeKind.DAYS_30 -> DayRange(daysAgoKey(29), todayDayKey())
            DateRangeKind.DAYS_90 -> DayRange(daysAgoKey(89), todayDayKey())
            DateRangeKind.YEAR_1 -> DayRange(
                dayKeyOf(
                    Calendar.getInstance().apply {
                        add(Calendar.YEAR, -1)
                        add(Calendar.DAY_OF_YEAR, 1)
                    }.timeInMillis
                ),
                todayDayKey()
            )
            DateRangeKind.MONTH -> DayRange(month.dayKey(1), month.dayKey(month.daysInMonth()))
            DateRangeKind.YEAR -> DayRange(keyOf(year, 1, 1), keyOf(year, 12, 31))
            DateRangeKind.CUSTOM -> DayRange(
                minOf(customStart, customEnd),
                maxOf(customStart, customEnd)
            )
        }

    /** "All time" or "1 Sep 2026 - 30 Sep 2026", for a caption under the selector. */
    fun describe(): String = range.describe()

    companion object {
        val Saver: Saver<DateRangeState, Any> = listSaver<DateRangeState, Any>(
            save = {
                listOf(it.kind.name, it.month.year, it.month.month, it.year, it.customStart, it.customEnd)
            },
            restore = {
                DateRangeState(
                    initialKind = DateRangeKind.entries.firstOrNull { k -> k.name == it[0] as String } ?: DateRangeKind.ALL,
                    initialMonth = MonthKey(it[1] as Int, it[2] as Int),
                    initialYear = it[3] as Int,
                    initialCustomStart = it[4] as String,
                    initialCustomEnd = it[5] as String
                )
            }
        )
    }
}

@Composable
fun rememberDateRangeState(initialKind: DateRangeKind = DateRangeKind.ALL): DateRangeState =
    rememberSaveable(saver = DateRangeState.Saver) {
        val now = currentMonth()
        DateRangeState(
            initialKind = initialKind,
            initialMonth = now,
            initialYear = now.year,
            initialCustomStart = daysAgoKey(29),
            initialCustomEnd = todayDayKey()
        )
    }
