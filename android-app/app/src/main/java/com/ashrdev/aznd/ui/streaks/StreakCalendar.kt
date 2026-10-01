package com.ashrdev.aznd.ui.streaks

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ashrdev.aznd.ui.calendar.DayBadge
import com.ashrdev.aznd.ui.calendar.DayDecoration
import com.ashrdev.aznd.ui.calendar.DayFill
import com.ashrdev.aznd.ui.calendar.MonthCalendar
import com.ashrdev.aznd.ui.calendar.currentMonth
import com.ashrdev.aznd.ui.calendar.monthKeyOf
import com.ashrdev.aznd.ui.calendar.monthRange
import com.ashrdev.aznd.ui.calendar.rememberCalendarState
import com.ashrdev.aznd.ui.calendar.todayDayKey

// Thin adapter: turns a streak's data into day decorations for the common MonthCalendar.
//   streak days       -> outlined
//   saved days        -> highlighted
//   favourite saved   -> highlighted and outlined
//   slip days         -> small red dot (not part of the run, so no outline)
@Composable
fun StreakCalendar(
    startDate: String,
    breaks: Set<String>,
    savedDays: Set<String>,
    favoriteDays: Set<String>,
    onDayClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = todayDayKey()
    val slipColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
    val validMonths = monthRange(monthKeyOf(startDate), currentMonth())

    MonthCalendar(
        decorate = { day ->
            val inRun = day >= startDate && day <= today
            val slip = inRun && day in breaks
            val saved = day in savedDays
            val favorite = saved && day in favoriteDays
            DayDecoration(
                outline = favorite || (inRun && !slip && !saved),
                fill = if (saved) DayFill.Highlight else DayFill.None,
                badges = if (slip) listOf(DayBadge.Dot(slipColor)) else emptyList()
            )
        },
        validMonths = validMonths,
        state = rememberCalendarState(),
        selectable = false,
        onDayClick = onDayClick,
        modifier = modifier
    )
}
