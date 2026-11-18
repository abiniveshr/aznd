package com.ashrdev.aznd.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashrdev.aznd.ui.theme.LocalAzndStyle
import kotlin.math.roundToInt

internal val WeekdayLabelWidth = 16.dp
internal val MonthLabelHeight = 14.dp
internal val BandGap = 10.dp
private val NumberMinCell = 20.dp
private val BadgeMinCell = 30.dp
private val InnerRingMinCell = 24.dp
private val ReferenceButtonHeight = 48.dp
private const val MAX_BUTTON_CORNER_DP = 50f
private const val CHOOSER_DIM_ALPHA = 0.38f

private fun Color.fade(scale: Float): Color = if (scale >= 1f) this else copy(alpha = alpha * scale)

internal fun cellSizeFor(view: CalendarView, width: Dp, height: Dp): Dp {
    val rows = view.monthRanges.size
    val byWidth = (width - WeekdayLabelWidth) / view.columns
    val byHeight = (height - MonthLabelHeight * rows - BandGap * (rows - 1)) / (7 * rows)
    return minOf(byWidth, byHeight).coerceAtLeast(4.dp)
}

internal fun naturalGridHeight(width: Dp): Dp =
    MonthLabelHeight + (width - WeekdayLabelWidth) / CalendarView(CalendarZoom.MONTH, 0, 1).columns * 7

/** How round the cells' corners are, as a percent (0..50) of the cell's side; follows the buttons' corners. */
@Composable
internal fun rememberCellCornerPercent(): Int {
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
        percent.roundToInt()
    }
}

@Composable
internal fun rememberCellShape(): Shape {
    val percent = rememberCellCornerPercent()
    return remember(percent) { RoundedCornerShape(percent = percent) }
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
    modifier: Modifier = Modifier,
    pickableDays: ((String) -> Boolean)? = null,
    chosenDay: String? = null
) {
    val colors = rememberCalendarColors()
    val today = todayDayKey()
    val layout = remember(view.year, firstDayOfWeek) { YearLayout.of(view.year, firstDayOfWeek) }
    val plan = remember(view, firstDayOfWeek) { CalendarGridPlan(view, firstDayOfWeek) }
    val bands = plan.bands
    val description = remember(view) { "Calendar, ${view.title()}" }
    val measurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    val labelStyle = remember(typography) { typography.labelSmall.copy(fontSize = 10.sp) }
    val monthLabels = remember(measurer, labelStyle, view.year) {
        (1..12).map { measurer.measure(MonthKey(view.year, it).shortName(), style = labelStyle, softWrap = false, maxLines = 1) }
    }
    val letterLabels = remember(measurer, labelStyle) {
        weekdayLetters.map { measurer.measure(it, style = labelStyle) }
    }
    val numberStyle = LocalTextStyle.current
    val cornerPercent = rememberCellCornerPercent()
    val hover = remember { DayHoverState() }
    val showHover = dayTooltip != null
    val badgePainter = remember { VectorBadgePainter() }
    val currentDecorate = rememberUpdatedState(decorate)
    val decorations = remember(plan) {
        derivedStateOf {
            val decorateDay = currentDecorate.value
            val slots = plan.slots
            ArrayList<DayDecoration>(slots.size).also { out -> for (slot in slots) out.add(decorateDay(slot.dayKey)) }
        }
    }
    LaunchedEffect(view) { hover.clear() }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(gridHeight)) {
        val cell = cellSizeFor(view, maxWidth, gridHeight)
        val cellGap = (cell * 0.06f).coerceIn(1.dp, 3.dp)
        val bandHeight = MonthLabelHeight + cell * 7
        val everyWeekday = cell >= NumberMinCell
        val widest = plan.weekCounts.maxOrNull() ?: view.columns
        val gridWidth = cell * widest
        val totalHeight = bandHeight * bands.size + BandGap * (bands.size - 1)
        val numbers = remember(measurer, numberStyle, cell) {
            DayNumberLayouts(measurer, numberStyle, (cell.value * 0.38f).coerceAtMost(14f).sp)
        }

        val geometry = remember(bands, layout, first, last, cell, bandHeight, cellGap, pickableDays) {
            GridGeometry(bands, layout, first, last, cell, bandHeight, cellGap, pickableDays)
        }
        val hitTest = remember<DayHitTest>(geometry) { { position, _, density -> geometry.hit(position, density) } }
        val paint = GridPaint(
            plan = plan,
            colors = colors,
            cell = cell,
            bandHeight = bandHeight,
            cellGap = cellGap,
            cornerPercent = cornerPercent,
            today = today,
            selectedDay = selectedDay,
            selectable = selectable,
            firstMonth = first.year * 12 + first.month,
            lastMonth = last.year * 12 + last.month,
            monthLabels = monthLabels,
            numbers = numbers,
            badges = badgePainter,
            pickable = pickableDays,
            chosenDay = chosenDay
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            Row {
                Canvas(modifier = Modifier.size(WeekdayLabelWidth, totalHeight)) {
                    drawWeekdayLetters(plan, letterLabels, colors.onSurfaceVariant, firstDayOfWeek, cell, bandHeight, everyWeekday)
                }

                Box(
                    modifier = Modifier
                        .size(gridWidth, totalHeight)
                        .dayGestures(
                            hover = hover,
                            showHover = showHover,
                            hitTest = hitTest,
                            onTap = onDayTap,
                            onDoubleTap = onDayDoubleTap
                        )
                        .pinchSteps { zoomIn, centroid, density ->
                            onPinch(zoomIn, geometry.monthNear(centroid, density))
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawDays(paint, if (showHover) hover.day else null, decorations.value)
                    }
                    DayHoverOverlay(hover, dayTooltip)
                    if (pickableDays != null && view.zoom == CalendarZoom.MONTH) {
                        for (slot in plan.slots) {
                            val dayKey = slot.dayKey
                            if (!pickableDays(dayKey)) continue
                            Box(
                                modifier = Modifier
                                    .offset(cell * slot.col, (bandHeight + BandGap) * slot.band + MonthLabelHeight + cell * slot.row)
                                    .size(cell)
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = formatDayLong(dayKey)
                                        onClick(label = "Choose " + formatDayLong(dayKey)) {
                                            onDayTap(dayKey)
                                            true
                                        }
                                    }
                            )
                        }
                    }
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
    val cellGap: Dp,
    val pickable: ((String) -> Boolean)?
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
        val week = band.startWeek + col
        val dayKey = band.dayKeyAt(layout, week, row)
        if (dayKey == null || MonthKey(layout.year, layout.monthAt(week, row)) !in first..last) return@with null
        if (pickable != null && !pickable.invoke(dayKey)) return@with null
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
        val month = monthIn(band, week, row)
            ?: (0 until 7).firstNotNullOfOrNull { r -> monthIn(band, week, r) }
            ?: band.months.first
        MonthKey(layout.year, month).coerceIn(first, last)
    }

    private fun monthIn(band: YearBand, week: Int, row: Int): Int? =
        layout.monthAt(week, row).takeIf { it in band.months }
}

/** Everything one draw of the day cells needs besides the hovered day and the decorations, which are read live. */
private class GridPaint(
    val plan: CalendarGridPlan,
    val colors: CalendarColors,
    val cell: Dp,
    val bandHeight: Dp,
    val cellGap: Dp,
    val cornerPercent: Int,
    val today: String,
    val selectedDay: String?,
    val selectable: Boolean,
    val firstMonth: Int,
    val lastMonth: Int,
    val monthLabels: List<TextLayoutResult>,
    val numbers: DayNumberLayouts,
    val badges: VectorBadgePainter,
    val pickable: ((String) -> Boolean)?,
    val chosenDay: String?
)

/** Day numbers 1-31 measured once per style; the colour is given when drawing. */
private class DayNumberLayouts(private val measurer: TextMeasurer, base: TextStyle, fontSize: TextUnit) {
    private val styles = arrayOf(
        base.copy(fontSize = fontSize),
        base.copy(fontSize = fontSize, fontWeight = FontWeight.Bold),
        base.copy(fontSize = fontSize, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
    )
    private val cache = arrayOfNulls<TextLayoutResult>(styles.size * 32)

    fun get(day: Int, bold: Boolean, underline: Boolean): TextLayoutResult {
        val kind = if (underline) 2 else if (bold) 1 else 0
        val index = kind * 32 + day
        return cache[index] ?: measurer.measure(
            text = day.toString(),
            style = styles[kind],
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1
        ).also { cache[index] = it }
    }
}

/** A text the way Text lays it out in a box: placed at the start or centred, and cut at the box if it does not fit. */
private fun DrawScope.drawTextIn(
    layout: TextLayoutResult,
    color: Color,
    left: Float,
    top: Float,
    boxWidth: Float,
    boxHeight: Float,
    centerX: Boolean,
    centerY: Boolean
) {
    val textWidth = layout.size.width.toFloat()
    val textHeight = layout.size.height.toFloat()
    val w = minOf(textWidth, boxWidth)
    val h = minOf(textHeight, boxHeight)
    val x = left + if (centerX) (boxWidth - w) / 2f else 0f
    val y = top + if (centerY) (boxHeight - h) / 2f else 0f
    if (textWidth > boxWidth || textHeight > boxHeight) {
        clipRect(x, y, x + w, y + h) { drawText(layout, color = color, topLeft = Offset(x, y)) }
    } else {
        drawText(layout, color = color, topLeft = Offset(x, y))
    }
}

/** What Modifier.border draws for a rounded shape: a stroke kept inside the shape's edge. */
private fun DrawScope.drawCellBorder(color: Color, left: Float, top: Float, side: Float, percent: Int, strokePx: Float) {
    val radius = side * percent / 100f
    if (strokePx * 2f > side) {
        drawRoundRect(color, Offset(left, top), Size(side, side), CornerRadius(radius))
        return
    }
    val half = strokePx / 2f
    drawRoundRect(
        color,
        Offset(left + half, top + half),
        Size(side - strokePx, side - strokePx),
        CornerRadius((radius - half).coerceAtLeast(0f)),
        style = Stroke(strokePx)
    )
}

private fun DrawScope.drawWeekdayLetters(
    plan: CalendarGridPlan,
    letters: List<TextLayoutResult>,
    color: Color,
    firstDayOfWeek: Int,
    cell: Dp,
    bandHeight: Dp,
    everyWeekday: Boolean
) {
    val cellPx = cell.toPx()
    val bandPx = (bandHeight + BandGap).toPx()
    val labelPx = MonthLabelHeight.toPx()
    for (band in plan.bands.indices) {
        for (row in 0 until 7) {
            if (!everyWeekday && row % 2 != 1) continue
            val top = band * bandPx + labelPx + row * cellPx
            drawTextIn(
                letters[(firstDayOfWeek - 1 + row) % 7], color,
                0f, top, WeekdayLabelWidth.toPx(), cellPx, centerX = false, centerY = true
            )
        }
    }
}

private fun DrawScope.drawDays(g: GridPaint, hovered: String?, decorations: List<DayDecoration>) {
    val cellPx = g.cell.toPx()
    val bandPx = (g.bandHeight + BandGap).toPx()
    val labelPx = MonthLabelHeight.toPx()
    val gapPx = g.cellGap.toPx()
    val inner = cellPx - gapPx * 2f
    val colors = g.colors
    val showNumber = g.cell >= NumberMinCell
    val showBadges = g.cell >= BadgeMinCell
    val ring = if (g.cell < 16.dp) 1.5.dp else 2.dp
    val ringPx = ring.toPx()
    val innerRingAllowed = g.cell >= InnerRingMinCell
    val reachableBase = colors.onSurface.copy(alpha = 0.08f)
    val unreachableBase = colors.onSurface.copy(alpha = 0.03f)
    val todayRing = colors.onSurface.copy(alpha = 0.6f)
    val corner = CornerRadius(inner * g.cornerPercent / 100f)
    val dotPx = (g.cell * 0.12f).coerceIn(3.dp, 5.dp).toPx()
    val symbolPx = (g.cell * 0.24f).coerceIn(8.dp, 12.dp).toPx()
    val insetPx = (g.cell * 0.08f).coerceIn(2.dp, 4.dp).toPx()
    val pickable = g.pickable

    for (label in g.plan.labels) {
        drawTextIn(
            g.monthLabels[label.month - 1], colors.onSurfaceVariant,
            label.col * cellPx, label.band * bandPx, Float.POSITIVE_INFINITY, labelPx,
            centerX = false, centerY = false
        )
    }

    val slots = g.plan.slots
    for (i in slots.indices) {
        val slot = slots[i]
        val decoration = decorations[i]
        val left = slot.col * cellPx + gapPx
        val top = slot.band * bandPx + labelPx + slot.row * cellPx + gapPx
        val monthIndex = g.plan.view.year * 12 + slot.month
        val reachable = monthIndex in g.firstMonth..g.lastMonth
        val isToday = slot.dayKey == g.today
        val isPickable = pickable != null && pickable(slot.dayKey)
        val isChosen = pickable != null && slot.dayKey == g.chosenDay
        val dimmed = pickable != null && !isPickable && !isChosen
        val fade = if (dimmed) CHOOSER_DIM_ALPHA else 1f
        val isSelected = pickable == null && g.selectable && slot.dayKey == g.selectedDay
        val showSelected = if (hovered != null) hovered == slot.dayKey else isSelected

        val hasFill = isChosen || isPickable || decoration.fill != DayFill.None
        val fillColor = when {
            isChosen -> colors.primary
            isPickable -> colors.primaryContainer
            else -> colors.fillColor(decoration.fill)
        }.fade(fade)
        val outlineColor = decoration.outlineColor ?: colors.primary
        val innerRing = showSelected && decoration.outline && innerRingAllowed

        var borderPx = 0f
        var borderColor = Color.Transparent
        when {
            innerRing -> {
                borderPx = ringPx
                borderColor = outlineColor
            }
            showSelected -> {
                borderPx = ringPx
                borderColor = colors.onSurface
            }
            isPickable && !isChosen -> {
                borderPx = 1.dp.toPx()
                borderColor = colors.primary
            }
            decoration.outline -> {
                borderPx = ringPx
                borderColor = outlineColor
            }
            isToday && !showNumber -> {
                borderPx = 1.dp.toPx()
                borderColor = todayRing
            }
        }

        val origin = Offset(left, top)
        val cellSize = Size(inner, inner)
        drawRoundRect(if (reachable && !dimmed) reachableBase else unreachableBase, origin, cellSize, corner)
        if (hasFill) drawRoundRect(fillColor, origin, cellSize, corner)

        if (showNumber) {
            val textColor = when {
                isChosen -> colors.onPrimary
                isPickable -> colors.onPrimaryContainer
                else -> decoration.textColor ?: if (hasFill) colors.fillTextColor(decoration.fill) else colors.onSurface
            }.fade(fade)
            drawTextIn(
                g.numbers.get(slot.dayOfMonth, isToday || showSelected || isChosen, isToday),
                if (reachable) textColor else textColor.copy(alpha = 0.4f),
                left, top, inner, inner, centerX = true, centerY = true
            )
        }

        if (borderPx > 0f) drawCellBorder(borderColor.fade(fade), left, top, inner, g.cornerPercent, borderPx)

        if (innerRing) {
            val pad = (ring + 1.dp).toPx()
            drawCellBorder(colors.onSurface, left + pad, top + pad, inner - pad * 2f, g.cornerPercent, 1.5.dp.toPx())
        }

        if (showBadges) {
            for (badge in decoration.badges) {
                val size = if (badge is DayBadge.Dot) dotPx else symbolPx
                val x = left + when (badge.position) {
                    BadgePosition.TOP_START -> insetPx
                    BadgePosition.TOP_END -> inner - insetPx - size
                    BadgePosition.BOTTOM_CENTER -> (inner - size) / 2f
                }
                val y = top + when (badge.position) {
                    BadgePosition.TOP_START, BadgePosition.TOP_END -> insetPx
                    BadgePosition.BOTTOM_CENTER -> inner - insetPx - size
                }
                when (badge) {
                    is DayBadge.Dot -> drawCircle(badge.color.fade(fade), size / 2f, Offset(x + size / 2f, y + size / 2f))
                    is DayBadge.Symbol -> g.badges.draw(
                        this, badge.icon, (if (badge.tint.isSpecified) badge.tint else colors.onSurface).fade(fade), x, y, size
                    )
                }
            }
        }
    }
}
