package com.ashrdev.aznd.ui.common

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.GraphPoint
import com.ashrdev.aznd.domain.GraphRange
import com.ashrdev.aznd.domain.GraphStyle
import com.ashrdev.aznd.domain.GraphWindow
import com.ashrdev.aznd.ui.components.Card
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The ONE time-window setting every graph in the app shares (Recent 5 ... Max). Create a single
 * instance in MainActivity and provide it with [LocalGraphRangeStore]; pages never touch it
 * directly, they just place a [StatsGraph] and, if they need it, read [rememberGraphRangeState].
 */
class GraphRangeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("graph_settings", Context.MODE_PRIVATE)

    var range: GraphRange by mutableStateOf(GraphRange.fromName(prefs.getString(KEY, null)))
        private set

    fun select(range: GraphRange) {
        this.range = range
        prefs.edit().putString(KEY, range.name).apply()
    }

    private companion object {
        const val KEY = "range"
    }
}

val LocalGraphRangeStore = compositionLocalOf<GraphRangeStore?> { null }

class GraphRangeState(val range: GraphRange, val select: (GraphRange) -> Unit)

/** The shared range (falls back to a screen-local one if no store was provided). */
@Composable
fun rememberGraphRangeState(): GraphRangeState {
    val store = LocalGraphRangeStore.current
    var fallback by remember { mutableStateOf(GraphRange.MONTHS_3) }
    return if (store != null) GraphRangeState(store.range) { store.select(it) }
    else GraphRangeState(fallback) { fallback = it }
}

/**
 * Reusable progress graph. The PAGE supplies [modes] (what can be graphed: volume, sets, ...) and
 * picks nothing else; the selected mode is local to the page, the time range is global.
 * Tap or drag on the chart to read a point.
 */
@Composable
fun StatsGraph(
    modes: List<GraphMode>,
    modifier: Modifier = Modifier,
    title: String = "Progress",
    footnote: String? = null,
    nowMs: Long = System.currentTimeMillis()
) {
    if (modes.isEmpty()) return
    var modeId by rememberSaveable { mutableStateOf(modes.first().id) }
    val mode = modes.firstOrNull { it.id == modeId } ?: modes.first()
    val rangeState = rememberGraphRangeState()
    val range = rangeState.range

    val shown = remember(mode.points, range) { GraphWindow.selectPoints(mode.points, range, nowMs) }
    val coversAll = remember(mode.points, range) {
        GraphWindow.coversAll(mode.points, range, nowMs) { it.timeMs }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(modes, key = { it.id }) { m ->
                    FilterChip(selected = m.id == mode.id, onClick = { modeId = m.id }, label = { Text(m.label) })
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GraphRange.values().toList(), key = { it.name }) { r ->
                    FilterChip(selected = r == range, onClick = { rangeState.select(r) }, label = { Text(r.label) })
                }
            }

            if (shown.isEmpty()) {
                Text(
                    "Nothing logged in this range.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                var selected by remember(shown, mode.id) { mutableIntStateOf(shown.lastIndex) }
                val point = shown[selected.coerceIn(0, shown.lastIndex)]
                val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
                Column {
                    Text(
                        "${dateFmt.format(Date(point.timeMs))}  •  " +
                            (point.valueText ?: "${GraphWindow.formatValue(point.value)} ${mode.unit}"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        point.detail ?: " ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GraphCanvas(
                    points = shown,
                    mode = mode,
                    selected = selected.coerceIn(0, shown.lastIndex),
                    onSelect = { selected = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
                if (range.months != null && coversAll) {
                    Text(
                        "Showing all available data.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (footnote != null) {
                Text(footnote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GraphCanvas(
    points: List<GraphPoint>,
    mode: GraphMode,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val density = LocalDensity.current
    val n = points.size
    val bars = mode.style == GraphStyle.BARS

    // vertical scale
    val maxV = points.maxOf { it.value }
    val minV = points.minOf { it.value }
    val yMin: Double
    val yMax: Double
    if (mode.startAtZero) {
        yMin = 0.0
        yMax = if (maxV <= 0.0) 1.0 else maxV * 1.08
    } else {
        val span = maxV - minV
        val pad = if (span <= 0.0) maxOf(1.0, kotlin.math.abs(maxV) * 0.05) else span * 0.15
        yMin = (minV - pad).coerceAtLeast(0.0)
        yMax = maxV + pad
    }
    val ticks = listOf(yMin, (yMin + yMax) / 2, yMax)
    val tickLayouts = remember(ticks, labelStyle) { ticks.map { measurer.measure(GraphWindow.formatValue(it), labelStyle) } }

    // x axis labels
    val spanDays = (points.last().timeMs - points.first().timeMs) / 86_400_000.0
    val axisFmt = remember(spanDays > 300) { SimpleDateFormat(if (spanDays > 300) "MMM yy" else "d MMM", Locale.getDefault()) }
    val firstLabel = remember(points, labelStyle) { measurer.measure(axisFmt.format(Date(points.first().timeMs)), labelStyle) }
    val lastLabel = remember(points, labelStyle) { measurer.measure(axisFmt.format(Date(points.last().timeMs)), labelStyle) }

    val leftPad = (tickLayouts.maxOf { it.size.width }) + with(density) { 8.dp.toPx() }
    val rightPad = with(density) { 10.dp.toPx() }
    val topPad = with(density) { 8.dp.toPx() }
    val bottomPad = with(density) { 22.dp.toPx() }
    val maxBarHalf = with(density) { 9.dp.toPx() }
    val inset = with(density) { 8.dp.toPx() }

    fun barHalf(width: Float): Float {
        val chartW = width - leftPad - rightPad
        return minOf(maxBarHalf, chartW / n * 0.35f).coerceAtLeast(1f)
    }

    fun xAt(i: Int, width: Float): Float {
        val chartW = width - leftPad - rightPad
        if (n == 1) return leftPad + chartW / 2
        val edge = if (bars) barHalf(width) + 2f else inset
        val t0 = points.first().timeMs
        val t1 = points.last().timeMs
        val frac = if (t1 == t0) i / (n - 1).toFloat() else (points[i].timeMs - t0).toFloat() / (t1 - t0).toFloat()
        return leftPad + edge + frac * (chartW - 2 * edge)
    }

    fun nearest(x: Float, width: Float): Int =
        (0 until n).minByOrNull { kotlin.math.abs(xAt(it, width) - x) } ?: 0

    Canvas(
        modifier = modifier
            .pointerInput(points) { detectTapGestures { o -> onSelect(nearest(o.x, size.width.toFloat())) } }
            .pointerInput(points) {
                detectHorizontalDragGestures { change, _ -> onSelect(nearest(change.position.x, size.width.toFloat())) }
            }
    ) {
        val w = size.width
        val bottom = size.height - bottomPad
        val chartH = bottom - topPad
        fun yAt(v: Double): Float = bottom - ((v - yMin) / (yMax - yMin)).toFloat() * chartH

        // grid + y labels
        ticks.forEachIndexed { k, tv ->
            val y = yAt(tv)
            drawLine(grid, Offset(leftPad, y), Offset(w - rightPad, y), strokeWidth = 1.dp.toPx())
            val layout = tickLayouts[k]
            drawText(
                layout,
                topLeft = Offset(leftPad - 6.dp.toPx() - layout.size.width, y - layout.size.height / 2f)
            )
        }

        if (bars) {
            val half = barHalf(w)
            for (i in 0 until n) {
                val x = xAt(i, w)
                val top = yAt(points[i].value)
                drawRoundRect(
                    color = if (i == selected) primary else primary.copy(alpha = 0.55f),
                    topLeft = Offset(x - half, top),
                    size = Size(half * 2, (bottom - top).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
            }
        } else {
            if (n > 1) {
                val path = Path()
                for (i in 0 until n) {
                    val x = xAt(i, w)
                    val y = yAt(points[i].value)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (n <= 60) {
                for (i in 0 until n) drawCircle(primary, 3.dp.toPx(), Offset(xAt(i, w), yAt(points[i].value)))
            }
            val sx = xAt(selected, w)
            drawLine(primary.copy(alpha = 0.35f), Offset(sx, topPad), Offset(sx, bottom), strokeWidth = 1.dp.toPx())
            drawCircle(primary, 6.dp.toPx(), Offset(sx, yAt(points[selected].value)))
        }

        // x labels: first and last date
        val labelY = bottom + 4.dp.toPx()
        drawText(firstLabel, topLeft = Offset(leftPad, labelY))
        if (n > 1) drawText(lastLabel, topLeft = Offset(w - rightPad - lastLabel.size.width, labelY))
    }
}
