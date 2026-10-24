package com.ashrdev.aznd.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ZoomOut
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import java.util.Calendar

private const val SWIPE_THRESHOLD_PX = 120f
private const val ZOOM_ANIM_MS = 220

@Composable
fun ZoomCalendar(
    decorate: (String) -> DayDecoration,
    validMonths: Collection<MonthKey>,
    modifier: Modifier = Modifier,
    state: CalendarState = rememberCalendarState(),
    selectable: Boolean = true,
    firstDayOfWeek: Int = Calendar.SUNDAY,
    swipeNavigation: Boolean = true,
    maxGridHeight: Dp = Dp.Unspecified,
    dayTooltip: ((String) -> String)? = null,
    onDayDoubleClick: ((String) -> Unit)? = null,
    onDayClick: (String) -> Unit = {}
) {
    val thisMonth = monthKeyOf(todayDayKey())
    val allowed = remember(validMonths, thisMonth) {
        (validMonths + thisMonth).toSortedSet().toList()
    }
    val first = allowed.first()
    val last = allowed.last()
    val month = state.visibleMonth.coerceIn(first, last)
    val view = CalendarView.of(state.zoom, month)
    var pickerOpen by remember { mutableStateOf(false) }

    if (pickerOpen) {
        MonthYearPickerDialog(
            months = allowed,
            selected = month,
            onPick = {
                state.visibleMonth = it
                state.zoom = CalendarZoom.MONTH
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false }
        )
    }

    val previous = view.step(-1, first, last)
    val next = view.step(1, first, last)
    val goPrevious: () -> Unit = { if (previous != null) state.visibleMonth = previous }
    val goNext: () -> Unit = { if (next != null) state.visibleMonth = next }

    Column(modifier = modifier) {
        CalendarHeader(
            title = view.title(),
            canGoPrevious = previous != null,
            canGoNext = next != null,
            onPrevious = goPrevious,
            onNext = goNext,
            onTitleClick = { pickerOpen = true },
            onZoomOut = if (state.zoom != CalendarZoom.YEAR) {
                { state.zoom = state.zoom.zoomedOut() }
            } else null
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (swipeNavigation) {
                        Modifier.pointerInput(previous, next) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    if (total > SWIPE_THRESHOLD_PX) goPrevious()
                                    else if (total < -SWIPE_THRESHOLD_PX) goNext()
                                    total = 0f
                                },
                                onHorizontalDrag = { _, amount -> total += amount }
                            )
                        }
                    } else Modifier
                )
        ) {
            val natural = naturalGridHeight(maxWidth)
            val gridHeight = if (maxGridHeight.isSpecified) minOf(natural, maxGridHeight) else natural

            AnimatedContent(
                targetState = view,
                transitionSpec = {
                    val inward = targetState.zoom.ordinal - initialState.zoom.ordinal
                    when {
                        inward > 0 ->
                            (fadeIn(tween(ZOOM_ANIM_MS)) + scaleIn(tween(ZOOM_ANIM_MS), initialScale = 0.8f)) togetherWith
                                (fadeOut(tween(ZOOM_ANIM_MS)) + scaleOut(tween(ZOOM_ANIM_MS), targetScale = 1.2f))
                        inward < 0 ->
                            (fadeIn(tween(ZOOM_ANIM_MS)) + scaleIn(tween(ZOOM_ANIM_MS), initialScale = 1.2f)) togetherWith
                                (fadeOut(tween(ZOOM_ANIM_MS)) + scaleOut(tween(ZOOM_ANIM_MS), targetScale = 0.8f))
                        else -> {
                            val forward = targetState.firstMonth() > initialState.firstMonth()
                            val sign = if (forward) 1 else -1
                            (fadeIn(tween(ZOOM_ANIM_MS)) + slideInHorizontally(tween(ZOOM_ANIM_MS)) { sign * it / 4 }) togetherWith
                                (fadeOut(tween(ZOOM_ANIM_MS)) + slideOutHorizontally(tween(ZOOM_ANIM_MS)) { -sign * it / 4 })
                        }
                    }
                },
                label = "calendarZoom"
            ) { shown ->
                CalendarGrid(
                    view = shown,
                    first = first,
                    last = last,
                    selectedDay = state.selectedDay,
                    selectable = selectable,
                    firstDayOfWeek = firstDayOfWeek,
                    decorate = decorate,
                    gridHeight = gridHeight,
                    dayTooltip = dayTooltip,
                    onDayTap = { dayKey ->
                        if (selectable) state.selectedDay = dayKey
                        state.visibleMonth = monthKeyOf(dayKey)
                        onDayClick(dayKey)
                    },
                    onDayDoubleTap = onDayDoubleClick,
                    onPinch = { zoomIn, near ->
                        if (zoomIn) {
                            if (state.zoom != CalendarZoom.MONTH) {
                                state.visibleMonth = near
                                state.zoom = state.zoom.zoomedIn()
                            }
                        } else if (state.zoom != CalendarZoom.YEAR) {
                            state.zoom = state.zoom.zoomedOut()
                        }
                    }
                )
            }
        }
    }
}

internal fun CalendarZoom.zoomedIn(): CalendarZoom = when (this) {
    CalendarZoom.YEAR -> CalendarZoom.QUADRANT
    CalendarZoom.QUADRANT, CalendarZoom.MONTH -> CalendarZoom.MONTH
}

internal fun CalendarZoom.zoomedOut(): CalendarZoom = when (this) {
    CalendarZoom.MONTH -> CalendarZoom.QUADRANT
    CalendarZoom.QUADRANT, CalendarZoom.YEAR -> CalendarZoom.YEAR
}

private fun CalendarView.firstMonth(): MonthKey = MonthKey(year, monthRanges.first().first)

private fun CalendarView.lastMonth(): MonthKey = MonthKey(year, monthRanges.last().last)

private fun CalendarView.title(): String = when (zoom) {
    CalendarZoom.YEAR -> year.toString()
    CalendarZoom.QUADRANT -> "${firstMonth().shortName()} – ${lastMonth().shortName()} $year"
    CalendarZoom.MONTH -> MonthKey(year, index).title()
}

private fun CalendarView.step(direction: Int, first: MonthKey, last: MonthKey): MonthKey? {
    val target = when (zoom) {
        CalendarZoom.YEAR -> CalendarView(zoom, year + direction, 0)
        CalendarZoom.QUADRANT -> {
            val raw = index + direction
            CalendarView(zoom, year + Math.floorDiv(raw, 3), Math.floorMod(raw, 3))
        }
        CalendarZoom.MONTH -> CalendarView.of(zoom, MonthKey(year, index).let { if (direction < 0) it.previous() else it.next() })
    }
    val start = maxOf(target.firstMonth(), first)
    val end = minOf(target.lastMonth(), last)
    return if (start <= end) start else null
}

@Composable
private fun CalendarHeader(
    title: String,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTitleClick: () -> Unit,
    onZoomOut: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.size(48.dp))
        IconButton(onClick = onPrevious, enabled = canGoPrevious) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous")
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onTitleClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next")
        }
        if (onZoomOut != null) {
            IconButton(onClick = onZoomOut) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}
