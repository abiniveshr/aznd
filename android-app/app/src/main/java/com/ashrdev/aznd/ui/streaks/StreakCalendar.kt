package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.domain.streaks.addDaysKey
import com.ashrdev.aznd.ui.calendar.CalendarState
import com.ashrdev.aznd.ui.calendar.DayBadge
import com.ashrdev.aznd.ui.calendar.DayDecoration
import com.ashrdev.aznd.ui.calendar.DayFill
import com.ashrdev.aznd.ui.calendar.ZoomCalendar
import com.ashrdev.aznd.ui.calendar.currentMonth
import com.ashrdev.aznd.ui.calendar.monthKeyOf
import com.ashrdev.aznd.ui.calendar.monthRange
import com.ashrdev.aznd.ui.calendar.todayDayKey

// Thin adapter: turns a streak's data into day decorations for the common ZoomCalendar.
//   streak days          -> outlined
//   saved days           -> highlighted
//   slip days            -> small red dot (not part of the run, so no outline)
//   milestones reached   -> accent outline + filled star
//   milestones coming up -> faded accent outline + hollow star (only for the next year, so the
//                           month picker never lists hundreds of empty months)
@Composable
fun StreakCalendar(
    startDate: String,
    breaks: Set<String>,
    savedDays: Set<String>,
    reachedMilestones: Map<String, Int>,
    upcomingMilestones: Map<String, Int>,
    state: CalendarState,
    onDayClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = todayDayKey()
    val slipColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
    val milestoneColor = MaterialTheme.colorScheme.tertiary

    val validMonths = remember(startDate, upcomingMilestones, today) {
        val horizon = addDaysKey(today, 365)
        val lastUpcoming = upcomingMilestones.keys.filter { it <= horizon }.maxOrNull()?.let { monthKeyOf(it) }
        monthRange(monthKeyOf(startDate), maxOf(currentMonth(), lastUpcoming ?: currentMonth()))
    }

    ZoomCalendar(
        decorate = { day ->
            val inRun = day >= startDate && day <= today
            val slip = inRun && day in breaks
            val saved = day in savedDays
            val reached = day in reachedMilestones
            val upcoming = day in upcomingMilestones
            DayDecoration(
                outline = reached || upcoming || (inRun && !slip && !saved),
                outlineColor = when {
                    reached -> milestoneColor
                    upcoming -> milestoneColor.copy(alpha = 0.5f)
                    else -> null
                },
                fill = if (saved) DayFill.Highlight else DayFill.None,
                badges = buildList {
                    if (slip) add(DayBadge.Dot(slipColor))
                    if (reached) add(DayBadge.Symbol(Icons.Default.Star, milestoneColor))
                    else if (upcoming) add(DayBadge.Symbol(Icons.Default.StarBorder, milestoneColor.copy(alpha = 0.7f)))
                }
            )
        },
        validMonths = validMonths,
        state = state,
        selectable = false,
        onDayClick = onDayClick,
        modifier = modifier
    )
}

/** Key to the symbols used by [StreakCalendar]. */
@Composable
fun StreakCalendarLegend(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "\u2605 milestone reached    \u2606 milestone coming up",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "\u25CF red dot: slip    filled day: saved",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
