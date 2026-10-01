package com.ashrdev.aznd.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.theme.LocalAzndStyle
import com.ashrdev.aznd.ui.theme.readableOn
import java.util.Calendar

private val weekdayLetters = listOf("S", "M", "T", "W", "T", "F", "S")

/**
 * The app's one calendar. It knows nothing about workouts or streaks:
 *
 *  - [decorate] says how each day looks (outline, fill, badges). Called only for visible days.
 *  - [validMonths] are the months that contain data. They're what the month picker lists and
 *    how far you can page; the current month is always included.
 *  - [state] holds the visible month and selected day (hoist it to read or drive them).
 *  - [onDayClick] fires on every tap. [selectable] = false turns the selection highlight off.
 *  - [cellContent] replaces the default day square entirely, if a screen ever needs to.
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

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { state.visibleMonth = month.previous() },
                enabled = month > first
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
            }
            Text(
                month.title(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { pickerOpen = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            IconButton(
                onClick = { state.visibleMonth = month.next() },
                enabled = month < last
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
            }
        }

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
        val rows = (leading + days + 6) / 7

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(month, first, last) {
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
        ) {
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val dayOfMonth = row * 7 + col - leading + 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
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

    val fillColor = when (val fill = deco.fill) {
        DayFill.None -> Color.Transparent
        DayFill.Highlight -> colors.primaryContainer
        is DayFill.Custom -> fill.color
    }
    val hasFill = deco.fill != DayFill.None
    val textColor = deco.textColor ?: when {
        deco.fill == DayFill.Highlight -> colors.onPrimaryContainer
        deco.fill is DayFill.Custom -> readableOn(fillColor)
        else -> colors.onSurface
    }

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
