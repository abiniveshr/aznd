package com.ashrdev.aznd.data.manage

import com.ashrdev.aznd.ui.calendar.dayOfMonthOf
import com.ashrdev.aznd.ui.calendar.monthKeyOf
import com.ashrdev.aznd.ui.common.DayRange
import java.util.Calendar

val DayRange.isAll: Boolean get() = startKey == null && endKey == null

val DayRange.fromMillis: Long get() = startKey?.let { dayStartMillis(it, 0) } ?: Long.MIN_VALUE

val DayRange.toMillisExclusive: Long get() = endKey?.let { dayStartMillis(it, 1) } ?: Long.MAX_VALUE

val DayRange.fromKey: String get() = startKey ?: "0000-00-00"

val DayRange.toKey: String get() = endKey ?: "9999-99-99"

private fun dayStartMillis(dayKey: String, plusDays: Int): Long {
    val month = monthKeyOf(dayKey)
    return Calendar.getInstance().apply {
        clear()
        set(month.year, month.month - 1, dayOfMonthOf(dayKey))
        add(Calendar.DAY_OF_YEAR, plusDays)
    }.timeInMillis
}
