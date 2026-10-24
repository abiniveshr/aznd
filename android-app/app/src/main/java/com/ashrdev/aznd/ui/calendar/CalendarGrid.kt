package com.ashrdev.aznd.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashrdev.aznd.ui.theme.LocalAzndStyle
import kotlin.math.roundToInt

internal val weekdayLetters = listOf("S", "M", "T", "W", "T", "F", "S")

internal val WeekdayLabelWidth = 16.dp
internal val MonthLabelHeight = 14.dp
internal val BandGap = 10.dp
private val NumberMinCell = 20.dp
private val BadgeMinCell = 30.dp
private val InnerRingMinCell = 24.dp
private val ReferenceButtonHeight = 48.dp
private const val MAX_BUTTON_CORNER_DP = 50f

internal class YearLayout(val year: Int, firstDayOfWeek: Int) {
    val dayKeys: List<String> = (1..12).flatMap { m ->
        val month = MonthKey(year, m)
        (1..month.daysInMonth()).map { month.dayKey(it) }
    }

    val leading: Int = (MonthKey(year, 1).firstWeekday() - firstDayOfWeek + 7) % 7

    val weeks: Int = (leading + dayKeys.size + 6) / 7

    val monthStartWeek: List<Int> = run {
        var ordinal = 0
        (1..12).map { m ->
            ((leading + ordinal) / 7).also { ordinal += MonthKey(year, m).daysInMonth() }
        }
    }

    fun dayKeyAt(week: Int, row: Int): String? = dayKeys.getOrNull(week * 7 + row - leading)

    fun weekOf(dayKey: String): Int? =
        dayKeys.indexOf(dayKey).takeIf { it >= 0 }?.let { (leading + it) / 7 }

    fun monthEndWeek(month: Int): Int =
        weekOf(MonthKey(year, month).let { it.dayKey(it.daysInMonth()) }) ?: (weeks - 1)
}

internal class YearBand(val months: IntRange, val startWeek: Int, val endWeek: Int) {
    val weekCount: Int get() = endWeek - startWeek + 1

    fun dayKeyAt(layout: YearLayout, week: Int, row: Int): String? =
        layout.dayKeyAt(week, row)?.takeIf { monthKeyOf(it).month in months }
}

internal data class CalendarView(val zoom: CalendarZoom, val year: Int, val index: Int) {
    val monthRanges: List<IntRange>
        get() = when (zoom) {
            CalendarZoom.YEAR -> listOf(1..4, 5..8, 9..12)
            CalendarZoom.QUADRANT -> {
                val start = index * 4 + 1
                listOf(start..start + 1, start + 2..start + 3)
            }
            CalendarZoom.MONTH -> listOf(index..index)
        }

    val columns: Int
        get() = when (zoom) {
            CalendarZoom.YEAR -> 19
            CalendarZoom.QUADRANT -> 10
            CalendarZoom.MONTH -> 6
        }

    val months: List<MonthKey>
        get() = monthRanges.flatMap { range -> range.map { MonthKey(year, it) } }

    companion object {
        fun of(zoom: CalendarZoom, month: MonthKey): CalendarView = CalendarView(
            zoom,
            month.year,
            when (zoom) {
                CalendarZoom.YEAR -> 0
                CalendarZoom.QUADRANT -> (month.month - 1) / 4
                CalendarZoom.MONTH -> month.month
            }
        )
    }
}

internal fun YearLayout.bandsFor(view: CalendarView): List<YearBand> =
    view.monthRanges.map { range ->
        YearBand(range, monthStartWeek[range.first - 1], monthEndWeek(range.last))
    }

internal fun cellSizeFor(view: CalendarView, width: Dp, height: Dp): Dp {
    val rows = view.monthRanges.size
    val byWidth = (width - WeekdayLabelWidth) / view.columns
    val byHeight = (height - MonthLabelHeight * rows - BandGap * (rows - 1)) / (7 * rows)
    return minOf(byWidth, byHeight).coerceAtLeast(4.dp)
}

internal fun naturalGridHeight(width: Dp): Dp =
    MonthLabelHeight + (width - WeekdayLabelWidth) / CalendarView(CalendarZoom.MONTH, 0, 1).columns * 7

@Composable
internal fun rememberCellShape(): Shape {
    val base = LocalAzndStyle.current.buttonShape
    val density = LocalDensity.current
    return remember(base, density) {
        val cornerDp = (base as? CornerBasedShape)
            ?.topStart
            ?.toPx(Size(10_000f, 10_000f), density)
            ?.let { it / density.density }
            ?: 0f
        val clamped = cornerDp.coerceIn(0f, MAX_BUTTON_CORNER_DP)
        val percent = (clamped / ReferenceButtonHeight.value * 100f).coerceIn(0f, 50f)
        RoundedCornerShape(percent = percent.roundToInt())
    }
}

@Composable
internal fun CalendarGrid(
    view: CalendarView,
    first: MonthKey,
    last: MonthKey,
    selectedDay: String?,
    selectable: Boolean,
    firstDayOfWeek: Int,
    decorate: (String) -> DayDecoration,
    gridHeight: Dp,
    dayTooltip: ((String) -> String)?,
    onDayTap: (String) -> Unit,
    onDayDoubleTap: ((String) -> Unit)?,
    onPinch: (zoomIn: Boolean, near: MonthKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val today = todayDayKey()
    val layout = remember(view.year, firstDayOfWeek) { YearLayout(view.year, firstDayOfWeek) }
    val bands = remember(layout, view) { layout.bandsFor(view) }
    val monthNames = remember(view.year) { (1..12).map { MonthKey(view.year, it).shortName() } }
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val hover = remember { DayHoverState() }
    val shape = rememberCellShape()
    LaunchedEffect(view) { hover.clear() }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(gridHeight)) {
        val cell = cellSizeFor(view, maxWidth, gridHeight)
        val cellGap = (cell * 0.06f).coerceIn(1.dp, 3.dp)
        val bandHeight = MonthLabelHeight + cell * 7
        val everyWeekday = cell >= NumberMinCell

        val geometry = GridGeometry(bands, layout, first, last, cell, bandHeight, cellGap)
        val hitTest: DayHitTest = { position, _, density -> geometry.hit(position, density) }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row {
                Column(
                    modifier = Modifier.width(WeekdayLabelWidth),
                    verticalArrangement = Arrangement.spacedBy(BandGap)
                ) {
                    bands.forEach { _ ->
                        Column {
                            Spacer(modifier = Modifier.height(MonthLabelHeight))
                            for (row in 0 until 7) {
                                Box(
                                    modifier = Modifier.size(WeekdayLabelWidth, cell),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (everyWeekday || row % 2 == 1) {
                                        Text(
                                            weekdayLetters[(firstDayOfWeek - 1 + row) % 7],
                                            style = labelStyle,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .dayGestures(
                            hover = hover,
                            showHover = dayTooltip != null,
                            hitTest = hitTest,
                            onTap = onDayTap,
                            onDoubleTap = onDayDoubleTap
                        )
                        .pinchSteps { zoomIn, centroid, density ->
                            onPinch(zoomIn, geometry.monthNear(centroid, density))
                        }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(BandGap)) {
                        bands.forEach { band ->
                            Column {
                                Row {
                                    for (week in band.startWeek..band.endWeek) {
                                        val monthIndex = band.months
                                            .firstOrNull { layout.monthStartWeek[it - 1] == week }
                                            ?.minus(1) ?: -1
                                        Box(modifier = Modifier.size(cell, MonthLabelHeight)) {
                                            if (monthIndex >= 0) {
                                                Text(
                                                    monthNames[monthIndex],
                                                    style = labelStyle,
                                                    color = colors.onSurfaceVariant,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                                                )
                                            }
                                        }
                                    }
                                }
                                Row {
                                    for (week in band.startWeek..band.endWeek) {
                                        Column {
                                            for (row in 0 until 7) {
                                                val dayKey = band.dayKeyAt(layout, week, row)
                                                Box(modifier = Modifier.size(cell).padding(cellGap)) {
                                                    if (dayKey != null) {
                                                        CalendarCell(
                                                            dayKey = dayKey,
                                                            cellSize = cell,
                                                            shape = shape,
                                                            decoration = decorate(dayKey),
                                                            reachable = monthKeyOf(dayKey) in first..last,
                                                            isToday = dayKey == today,
                                                            isSelected = selectable && dayKey == selectedDay,
                                                            hover = if (dayTooltip != null) hover else null
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
                    DayHoverOverlay(hover, dayTooltip)
                }
            }
        }
    }
}

private class GridGeometry(
    val bands: List<YearBand>,
    val layout: YearLayout,
    val first: MonthKey,
    val last: MonthKey,
    val cell: Dp,
    val bandHeight: Dp,
    val cellGap: Dp
) {
    fun hit(position: Offset, density: Density): DayHit? = with(density) {
        val cellPx = cell.toPx()
        val bandPx = (bandHeight + BandGap).toPx()
        val labelPx = MonthLabelHeight.toPx()
        val gapPx = cellGap.toPx()
        val bandIndex = (position.y / bandPx).toInt()
        val band = bands.getOrNull(bandIndex)
        val yInBand = position.y - bandIndex * bandPx - labelPx
        val col = (position.x / cellPx).toInt()
        val row = (yInBand / cellPx).toInt()
        if (band == null || position.x < 0f || position.y < 0f || yInBand < 0f ||
            row !in 0 until 7 || col !in 0 until band.weekCount
        ) {
            return@with null
        }
        val dayKey = band.dayKeyAt(layout, band.startWeek + col, row)
        if (dayKey == null || monthKeyOf(dayKey) !in first..last) return@with null
        val left = col * cellPx + gapPx
        val top = bandIndex * bandPx + labelPx + row * cellPx + gapPx
        DayHit(
            dayKey,
            IntRect(
                left.roundToInt(),
                top.roundToInt(),
                (left + cellPx - gapPx * 2).roundToInt(),
                (top + cellPx - gapPx * 2).roundToInt()
            )
        )
    }

    fun monthNear(position: Offset, density: Density): MonthKey = with(density) {
        val cellPx = cell.toPx()
        val bandPx = (bandHeight + BandGap).toPx()
        val labelPx = MonthLabelHeight.toPx()
        val bandIndex = (position.y / bandPx).toInt().coerceIn(0, bands.size - 1)
        val band = bands[bandIndex]
        val col = (position.x / cellPx).toInt().coerceIn(0, band.weekCount - 1)
        val row = ((position.y - bandIndex * bandPx - labelPx) / cellPx).toInt().coerceIn(0, 6)
        val week = band.startWeek + col
        val month = band.dayKeyAt(layout, week, row)?.let { monthKeyOf(it).month }
            ?: (0 until 7).firstNotNullOfOrNull { r -> band.dayKeyAt(layout, week, r) }?.let { monthKeyOf(it).month }
            ?: band.months.first
        MonthKey(layout.year, month).coerceIn(first, last)
    }
}

@Composable
private fun CalendarCell(
    dayKey: String,
    cellSize: Dp,
    shape: Shape,
    decoration: DayDecoration,
    reachable: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    hover: DayHoverState?
) {
    val showSelected by remember(hover, dayKey, isSelected) {
        derivedStateOf {
            val hoveredDay = hover?.day
            if (hoveredDay != null) hoveredDay == dayKey else isSelected
        }
    }
    val colors = MaterialTheme.colorScheme
    val base = colors.onSurface.copy(alpha = if (reachable) 0.08f else 0.03f)
    val hasFill = decoration.fill != DayFill.None
    val fillColor = decoration.fill.resolveColor()
    val fillText = decoration.fill.defaultTextColor()
    val showNumber = cellSize >= NumberMinCell
    val showBadges = cellSize >= BadgeMinCell
    val ring = if (cellSize < 16.dp) 1.5.dp else 2.dp
    val outlineColor = decoration.outlineColor ?: colors.primary
    val innerRing = showSelected && decoration.outline && cellSize >= InnerRingMinCell

    val borderWidth: Dp
    val borderColor: Color
    when {
        innerRing -> {
            borderWidth = ring
            borderColor = outlineColor
        }
        showSelected -> {
            borderWidth = ring
            borderColor = colors.onSurface
        }
        decoration.outline -> {
            borderWidth = ring
            borderColor = outlineColor
        }
        isToday && !showNumber -> {
            borderWidth = 1.dp
            borderColor = colors.onSurface.copy(alpha = 0.6f)
        }
        else -> {
            borderWidth = 0.dp
            borderColor = Color.Transparent
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(base)
                .then(if (hasFill) Modifier.background(fillColor) else Modifier)
                .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, shape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (showNumber) {
                val textColor = decoration.textColor ?: if (hasFill) fillText else colors.onSurface
                Text(
                    text = dayOfMonthOf(dayKey).toString(),
                    color = if (reachable) textColor else textColor.copy(alpha = 0.4f),
                    fontSize = (cellSize.value * 0.38f).coerceAtMost(14f).sp,
                    fontWeight = if (isToday || showSelected) FontWeight.Bold else null,
                    textDecoration = if (isToday) TextDecoration.Underline else null,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        if (innerRing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(ring + 1.dp)
                    .border(1.5.dp, colors.onSurface, shape)
            )
        }

        if (showBadges) {
            val dotSize = (cellSize * 0.12f).coerceIn(3.dp, 5.dp)
            val symbolSize = (cellSize * 0.24f).coerceIn(8.dp, 12.dp)
            val inset = (cellSize * 0.08f).coerceIn(2.dp, 4.dp)
            decoration.badges.forEach { badge ->
                val alignment = when (badge.position) {
                    BadgePosition.TOP_START -> Alignment.TopStart
                    BadgePosition.TOP_END -> Alignment.TopEnd
                    BadgePosition.BOTTOM_CENTER -> Alignment.BottomCenter
                }
                when (badge) {
                    is DayBadge.Dot -> Box(
                        modifier = Modifier
                            .align(alignment)
                            .padding(inset)
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(badge.color)
                    )
                    is DayBadge.Symbol -> Icon(
                        imageVector = badge.icon,
                        contentDescription = null,
                        tint = badge.tint,
                        modifier = Modifier
                            .align(alignment)
                            .padding(inset)
                            .size(symbolSize)
                    )
                }
            }
        }
    }
}
