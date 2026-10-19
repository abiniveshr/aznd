package com.ashrdev.aznd.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.theme.LocalAzndStyle
import java.util.Calendar

internal val weekdayLetters = listOf("S", "M", "T", "W", "T", "F", "S")

/**
 * The app's one calendar. It knows nothing about workouts or streaks:
 *
 *  - [decorate] says how each day looks (outline, fill, badges). Called only for visible days.
 *  - [validMonths] are the months that contain data. They're what the month picker lists and
 *    how far you can page; the current month is always included.
 *  - [state] holds the visible month and selected day (hoist it to read or drive them).
 *  - [onDayClick] fires on every tap. [selectable] = false turns the selection highlight off.
 *  - [cellContent] replaces the default day square entirely, if a screen ever needs to.
 *    (Month view only.)
 *  - [CalendarState.mode] switches between the month grid and a GitHub-style year view. A
 *    toggle sits in the header unless [showModeToggle] is false; set the state's mode to start
 *    in either one. Year view draws each day's fill, outline and selection; fills such as
 *    [DayFill.Intensity] give the heat-map shading. Tapping a day behaves exactly as in month
 *    view, so the selected-day panel under the calendar works unchanged.
 *
 * Sizing knobs for screens where the calendar is the main content:
 *  - [cellAspectRatio] is width / height of a month-view cell. Below 1 the cells get taller.
 *  - [fixedRows] always draws 6 week rows, so the calendar keeps one height from month to month.
 *  - [yearCellSize] is the smallest a year-view cell may be (it scrolls sideways when the year
 *    doesn't fit).
 *  - [swipeNavigation] = false turns off swipe-to-change-month, for calendars inside a pager
 *    where a sideways swipe should change page instead. The chevrons still work.
 *
 * Corner rounding follows the button rounding from Appearance, and cells are padded so squares
 * never merge.
 */
@Composable
fun MonthCalendar(
    decorate: (String) -> DayDecoration,
    validMonths: Collection<MonthKey>,
    modifier: Modifier = Modifier,
    state: CalendarState = rememberCalendarState(),
    selectable: Boolean = true,
    firstDayOfWeek: Int = Calendar.SUNDAY,
    showModeToggle: Boolean = true,
    swipeNavigation: Boolean = true,
    cellAspectRatio: Float = 1f,
    fixedRows: Boolean = false,
    yearCellSize: Dp = 12.dp,
    onDayClick: (String) -> Unit = {},
    cellContent: (@Composable BoxScope.(DayCellInfo) -> Unit)? = null
) {
    val style = LocalAzndStyle.current
    val today = todayDayKey()
    val thisMonth = monthKeyOf(today)
    val allowed = remember(validMonths, thisMonth) {
        (validMonths + thisMonth).toSortedSet().toList()
    }
    val first = allowed.first()
    val last = allowed.last()
    val month = state.visibleMonth.coerceIn(first, last)
    var pickerOpen by remember { mutableStateOf(false) }

    if (pickerOpen) {
        MonthYearPickerDialog(
            months = allowed,
            selected = month,
            onPick = {
                state.visibleMonth = it
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false }
        )
    }

    val isYear = state.mode == CalendarMode.YEAR
    val onToggleMode: (() -> Unit)? = if (showModeToggle) {
        {
            state.mode = if (isYear) CalendarMode.MONTH else CalendarMode.YEAR
        }
    } else null

    if (isYear) {
        Column(modifier = modifier) {
            CalendarHeader(
                title = month.year.toString(),
                previousLabel = "Previous year",
                nextLabel = "Next year",
                canGoPrevious = month.year > first.year,
                canGoNext = month.year < last.year,
                onPrevious = {
                    state.visibleMonth = MonthKey(month.year - 1, month.month).coerceIn(first, last)
                },
                onNext = {
                    state.visibleMonth = MonthKey(month.year + 1, month.month).coerceIn(first, last)
                },
                onTitleClick = null,
                mode = state.mode,
                onToggleMode = onToggleMode
            )
            YearGrid(
                year = month.year,
                first = first,
                last = last,
                selectedDay = state.selectedDay,
                selectable = selectable,
                firstDayOfWeek = firstDayOfWeek,
                decorate = decorate,
                minCellSize = yearCellSize,
                onDayClick = { dayKey ->
                    if (selectable) state.selectedDay = dayKey
                    // So switching back to month view lands on the day you tapped.
                    state.visibleMonth = monthKeyOf(dayKey)
                    onDayClick(dayKey)
                }
            )
        }
        return
    }

    Column(modifier = modifier) {
        CalendarHeader(
            title = month.title(),
            previousLabel = "Previous month",
            nextLabel = "Next month",
            canGoPrevious = month > first,
            canGoNext = month < last,
            onPrevious = { state.visibleMonth = month.previous() },
            onNext = { state.visibleMonth = month.next() },
            onTitleClick = { pickerOpen = true },
            mode = state.mode,
            onToggleMode = onToggleMode
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    weekdayLetters[(firstDayOfWeek - 1 + i) % 7],
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        val leading = (month.firstWeekday() - firstDayOfWeek + 7) % 7
        val days = month.daysInMonth()
        val rows = if (fixedRows) 6 else (leading + days + 6) / 7

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (swipeNavigation) {
                        Modifier.pointerInput(month, first, last) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    if (total > 120f && month > first) state.visibleMonth = month.previous()
                                    else if (total < -120f && month < last) state.visibleMonth = month.next()
                                    total = 0f
                                },
                                onHorizontalDrag = { _, amount -> total += amount }
                            )
                        }
                    } else Modifier
                )
        ) {
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val dayOfMonth = row * 7 + col - leading + 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(cellAspectRatio)
                        ) {
                            if (dayOfMonth in 1..days) {
                                val dayKey = month.dayKey(dayOfMonth)
                                val info = DayCellInfo(
                                    dayKey = dayKey,
                                    dayOfMonth = dayOfMonth,
                                    decoration = decorate(dayKey),
                                    isToday = dayKey == today,
                                    isSelected = selectable && dayKey == state.selectedDay
                                )
                                // The padding around each cell is what keeps neighbouring
                                // outlines from touching.
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(3.dp)
                                        .clip(style.buttonShape)
                                        .clickable {
                                            if (selectable) state.selectedDay = dayKey
                                            onDayClick(dayKey)
                                        }
                                ) {
                                    if (cellContent != null) cellContent(info)
                                    else DefaultDayCell(info)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The standard day square. Public so a custom `cellContent` can wrap or extend it. */
@Composable
fun BoxScope.DefaultDayCell(info: DayCellInfo) {
    val style = LocalAzndStyle.current
    val shape = style.buttonShape
    val deco = info.decoration
    val colors = MaterialTheme.colorScheme

    val fillColor = deco.fill.resolveColor()
    val hasFill = deco.fill != DayFill.None
    val textColor = deco.textColor ?: deco.fill.defaultTextColor()

    Box(
        modifier = Modifier
            .matchParentSize()
            .then(if (hasFill) Modifier.background(fillColor, shape) else Modifier)
            .then(
                if (info.isSelected) Modifier.background(colors.onSurface.copy(alpha = 0.12f), shape)
                else Modifier
            )
            .then(
                if (deco.outline) Modifier.border(2.dp, deco.outlineColor ?: colors.primary, shape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = info.dayOfMonth.toString(),
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (info.isToday || info.isSelected) FontWeight.Bold else null,
            textDecoration = if (info.isToday) TextDecoration.Underline else null
        )
    }

    deco.badges.forEach { badge ->
        val alignment = when (badge.position) {
            BadgePosition.TOP_START -> Alignment.TopStart
            BadgePosition.TOP_END -> Alignment.TopEnd
            BadgePosition.BOTTOM_CENTER -> Alignment.BottomCenter
        }
        when (badge) {
            is DayBadge.Dot -> Box(
                modifier = Modifier
                    .align(alignment)
                    .padding(4.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(badge.color)
            )
            is DayBadge.Symbol -> Icon(
                imageVector = badge.icon,
                contentDescription = null,
                tint = badge.tint,
                modifier = Modifier
                    .align(alignment)
                    .padding(3.dp)
                    .size(10.dp)
            )
        }
    }
}

/**
 * Prev / title / next, plus the month-year toggle on the right. When the toggle is shown, an
 * equal spacer on the left keeps the title centred between the chevrons.
 */
@Composable
private fun CalendarHeader(
    title: String,
    previousLabel: String,
    nextLabel: String,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTitleClick: (() -> Unit)?,
    mode: CalendarMode,
    onToggleMode: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onToggleMode != null) Spacer(modifier = Modifier.size(48.dp))
        IconButton(onClick = onPrevious, enabled = canGoPrevious) {
            Icon(Icons.Default.ChevronLeft, contentDescription = previousLabel)
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = nextLabel)
        }
        if (onToggleMode != null) {
            IconButton(onClick = onToggleMode) {
                if (mode == CalendarMode.MONTH) {
                    Icon(Icons.Default.CalendarViewMonth, contentDescription = "Show full year")
                } else {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Show single month")
                }
            }
        }
    }
}
