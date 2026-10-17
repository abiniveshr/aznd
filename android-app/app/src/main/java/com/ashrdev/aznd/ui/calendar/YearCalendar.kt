package com.ashrdev.aznd.ui.calendar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashrdev.aznd.ui.theme.LocalAzndStyle

// Smallest a cell may get. Narrower screens scroll sideways instead of squashing the cells
// into slivers that are impossible to tap; wide screens (tablets, landscape) fit the whole year.
private val MinCellSize = 12.dp
private val CellGap = 1.dp
private val WeekdayLabelWidth = 16.dp
private val MonthLabelHeight = 14.dp

/**
 * One calendar year laid out like GitHub's contribution graph: each column is a week, each row a
 * weekday, and the first column is padded with blanks before 1 January.
 */
internal class YearLayout(val year: Int, firstDayOfWeek: Int) {
    /** Every day key of the year, in order. */
    val dayKeys: List<String> = (1..12).flatMap { m ->
        val month = MonthKey(year, m)
        (1..month.daysInMonth()).map { month.dayKey(it) }
    }

    /** Blank cells before 1 January in the first column. */
    val leading: Int = (MonthKey(year, 1).firstWeekday() - firstDayOfWeek + 7) % 7

    val weeks: Int = (leading + dayKeys.size + 6) / 7

    /** The column each month starts in, for months 1..12 (index 0..11). */
    val monthStartWeek: List<Int> = run {
        var ordinal = 0
        (1..12).map { m ->
            ((leading + ordinal) / 7).also { ordinal += MonthKey(year, m).daysInMonth() }
        }
    }

    /** Day key for a cell, or null for the blanks before 1 January and after 31 December. */
    fun dayKeyAt(week: Int, row: Int): String? = dayKeys.getOrNull(week * 7 + row - leading)

    /** Column that holds [dayKey], or null if it isn't in this year. */
    fun weekOf(dayKey: String): Int? =
        dayKeys.indexOf(dayKey).takeIf { it >= 0 }?.let { (leading + it) / 7 }
}

/**
 * The year view behind `MonthCalendar` (see [CalendarMode.YEAR]). Same contract as the month
 * view: [decorate] says how a day looks, [onDayClick] fires on tap. Only the fill, outline and
 * selection of a decoration are drawn here; badges and day numbers don't fit at this size.
 *
 * Days in months outside [first]..[last] are drawn faint and can't be tapped, matching what the
 * month view lets you reach. When the year is the current one it opens scrolled to today.
 */
@Composable
internal fun YearGrid(
    year: Int,
    first: MonthKey,
    last: MonthKey,
    selectedDay: String?,
    selectable: Boolean,
    firstDayOfWeek: Int,
    decorate: (String) -> DayDecoration,
    onDayClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val today = todayDayKey()
    val layout = remember(year, firstDayOfWeek) { YearLayout(year, firstDayOfWeek) }
    val monthNames = remember(year) { (1..12).map { MonthKey(year, it).shortName() } }
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewport = maxWidth - WeekdayLabelWidth
        val cell: Dp = maxOf(viewport / layout.weeks, MinCellSize)

        // A ScrollState starting past the end is clamped to the end once measured, so this
        // needs no waiting for layout. For the current year, put today's week at the right edge.
        val scroll = remember(year, cell, viewport) {
            val todayWeek = layout.weekOf(today)
            val initial = if (todayWeek == null) 0 else with(density) {
                (cell * (todayWeek + 1) - viewport).roundToPx().coerceAtLeast(0)
            }
            ScrollState(initial)
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            // Weekday letters stay put while the grid scrolls. Every other row, like GitHub.
            Column(modifier = Modifier.width(WeekdayLabelWidth)) {
                Spacer(modifier = Modifier.height(MonthLabelHeight))
                for (row in 0 until 7) {
                    Box(
                        modifier = Modifier.size(WeekdayLabelWidth, cell),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (row % 2 == 1) {
                            Text(
                                weekdayLetters[(firstDayOfWeek - 1 + row) % 7],
                                style = labelStyle,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.horizontalScroll(scroll)) {
                Row {
                    for (week in 0 until layout.weeks) {
                        val monthIndex = layout.monthStartWeek.indexOf(week)
                        Box(modifier = Modifier.size(cell, MonthLabelHeight)) {
                            if (monthIndex >= 0) {
                                Text(
                                    monthNames[monthIndex],
                                    style = labelStyle,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    // The label is wider than one column; let it spill over.
                                    modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                                )
                            }
                        }
                    }
                }
                Row {
                    for (week in 0 until layout.weeks) {
                        Column {
                            for (row in 0 until 7) {
                                val dayKey = layout.dayKeyAt(week, row)
                                Box(modifier = Modifier.size(cell).padding(CellGap)) {
                                    if (dayKey != null) {
                                        YearCell(
                                            decoration = decorate(dayKey),
                                            reachable = monthKeyOf(dayKey) in first..last,
                                            isToday = dayKey == today,
                                            isSelected = selectable && dayKey == selectedDay,
                                            onClick = { onDayClick(dayKey) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearCell(
    decoration: DayDecoration,
    reachable: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val style = LocalAzndStyle.current
    val colors = MaterialTheme.colorScheme
    val shape = style.buttonShape

    // The empty square every day gets, like GitHub's grey. A fill with opacity (Intensity) is
    // painted over it, so a faint day is the highlight colour mixed with this base.
    val base = colors.onSurface.copy(alpha = if (reachable) 0.08f else 0.03f)

    // One border at most. Selection wins, then the day's own outline, then the today marker.
    val borderWidth: Dp
    val borderColor: Color
    when {
        isSelected -> {
            borderWidth = 2.dp
            borderColor = colors.onSurface
        }
        decoration.outline -> {
            borderWidth = 1.dp
            borderColor = decoration.outlineColor ?: colors.primary
        }
        isToday -> {
            borderWidth = 1.dp
            borderColor = colors.onSurface.copy(alpha = 0.6f)
        }
        else -> {
            borderWidth = 0.dp
            borderColor = Color.Transparent
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(base)
            .then(
                if (decoration.fill != DayFill.None) Modifier.background(decoration.fill.resolveColor())
                else Modifier
            )
            .then(
                if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, shape)
                else Modifier
            )
            .then(if (reachable) Modifier.clickable(onClick = onClick) else Modifier)
    )
}
