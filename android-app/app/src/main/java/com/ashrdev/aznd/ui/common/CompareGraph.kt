package com.ashrdev.aznd.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import com.ashrdev.aznd.domain.CompareBucket
import com.ashrdev.aznd.domain.ComparePair
import com.ashrdev.aznd.domain.CompareTable
import com.ashrdev.aznd.domain.CompareView
import com.ashrdev.aznd.domain.Correlation
import com.ashrdev.aznd.domain.GraphCompare
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.GraphWindow
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun CompareGraph(
    variables: List<GraphMode>,
    modifier: Modifier = Modifier,
    title: String? = "Compare",
    footnote: String? = null,
    nowMs: Long = System.currentTimeMillis(),
    bordered: Boolean = true,
    fillHeight: Boolean = false,
    initial: List<String> = emptyList()
) {
    if (variables.size < 2) return
    val body: @Composable () -> Unit = {
        Column(
            (if (bordered) Modifier else modifier).then(if (fillHeight) Modifier.fillMaxSize() else Modifier).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            CompareBody(variables, nowMs, fillHeight, initial)
            if (footnote != null) {
                Text(footnote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (bordered) Card(modifier = modifier.fillMaxWidth()) { body() } else body()
}

@Composable
fun compareColors(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    Color(0xFFF59E0B),
    Color(0xFF14B8A6),
    Color(0xFFEC4899)
)

private const val ID_SEPARATOR = "\n"

@Composable
internal fun ColumnScope.CompareBody(
    variables: List<GraphMode>,
    nowMs: Long,
    fillHeight: Boolean,
    initial: List<String> = emptyList()
) {
    val defaults = (initial.filter { id -> variables.any { it.id == id } } + variables.map { it.id })
        .distinct()
        .take(if (initial.isEmpty()) 2 else maxOf(2, initial.size).coerceAtMost(GraphCompare.MAX_SERIES))
    var pickedKey by rememberSaveable { mutableStateOf(defaults.joinToString(ID_SEPARATOR)) }
    val picked = pickedKey.split(ID_SEPARATOR).filter { id -> id.isNotEmpty() && variables.any { it.id == id } }
    val chosen = picked.mapNotNull { id -> variables.firstOrNull { it.id == id } }
    var viewName by rememberSaveable { mutableStateOf(CompareView.OVERLAY.name) }
    val view = CompareView.entries.firstOrNull { it.name == viewName } ?: CompareView.OVERLAY
    var bucketName by rememberSaveable { mutableStateOf(CompareBucket.DAY.name) }
    val bucket = CompareBucket.entries.firstOrNull { it.name == bucketName } ?: CompareBucket.DAY
    var pairX by rememberSaveable { mutableIntStateOf(0) }
    var pairY by rememberSaveable { mutableIntStateOf(1) }
    val rangeState = rememberGraphRangeState()
    val range = rangeState.range
    val colors = compareColors()

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(variables, key = { it.id }) { v ->
            val on = v.id in picked
            FilterChip(
                selected = on,
                enabled = on || picked.size < GraphCompare.MAX_SERIES,
                onClick = {
                    val next = if (on) picked - v.id else picked + v.id
                    pickedKey = next.joinToString(ID_SEPARATOR)
                    pairX = 0
                    pairY = 1
                },
                label = { Text(v.label, color = if (on) colors[picked.indexOf(v.id).coerceIn(0, colors.lastIndex)] else Color.Unspecified) }
            )
        }
    }

    if (chosen.size < 2) {
        Text(
            "Pick at least two to compare.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        CompareView.entries.forEach { v ->
            FilterChip(selected = v == view, onClick = { viewName = v.name }, label = { Text(v.label) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        GraphRangeButton(rangeState)
        CompareBucket.entries.forEach { b ->
            FilterChip(selected = b == bucket, onClick = { bucketName = b.name }, label = { Text(b.label) })
        }
    }

    val start = remember(chosen, range, nowMs) { GraphCompare.windowStart(chosen, range, nowMs) }
    val table = remember(chosen, bucket, start) { GraphCompare.table(chosen, bucket, start) }
    val x = pairX.coerceIn(0, chosen.lastIndex)
    val y = (if (pairY == x) (x + 1) % chosen.size else pairY).coerceIn(0, chosen.lastIndex)
    val correlation = remember(table, x, y) { table.correlation(x, y) }
    val chartModifier = Modifier.fillMaxWidth().then(if (fillHeight) Modifier.weight(1f) else Modifier.height(220.dp))
    val dateFmt = remember { SimpleDateFormat(if (bucket == CompareBucket.WEEK) "'Week of' d MMM yyyy" else "d MMM yyyy", Locale.getDefault()) }
    val unitText: (GraphMode, Double, Boolean) -> String = { m, v, est -> (if (est) "≈ " else "") + "${GraphWindow.formatValue(v)} ${m.unit}".trim() }

    if (table.rows.isEmpty()) {
        Text(
            "Nothing logged in this range.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    when (view) {
        CompareView.OVERLAY -> {
            var selected by remember(table) { mutableIntStateOf(table.rows.lastIndex) }
            val row = table.rows[selected.coerceIn(0, table.rows.lastIndex)]
            Column {
                Text(dateFmt.format(Date(row.timeMs)), style = MaterialTheme.typography.titleSmall)
                chosen.forEachIndexed { i, m ->
                    Text(
                        "${m.label}: " + (if (row.has(i)) unitText(m, row.values[i], row.filled[i]) else "—"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors[i]
                    )
                }
            }
            OverlayCanvas(table, colors, selected.coerceIn(0, table.rows.lastIndex), { selected = it }, chartModifier)
            if (chosen.size > 2) {
                Text(
                    "Each line uses its own scale.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        CompareView.SCATTER -> {
            val pairs = remember(table, x, y) { table.pairs(x, y) }
            if (pairs.isEmpty()) {
                Text(
                    "No ${bucket.label.lowercase(Locale.getDefault())} entries have both ${chosen[x].label} and ${chosen[y].label}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                var selected by remember(pairs) { mutableIntStateOf(pairs.lastIndex) }
                val p = pairs[selected.coerceIn(0, pairs.lastIndex)]
                Column {
                    Text(dateFmt.format(Date(p.timeMs)), style = MaterialTheme.typography.titleSmall)
                    Text("${chosen[x].label}: " + unitText(chosen[x], p.x, false), style = MaterialTheme.typography.bodySmall, color = colors[x])
                    Text("${chosen[y].label}: " + unitText(chosen[y], p.y, false), style = MaterialTheme.typography.bodySmall, color = colors[y])
                }
                ScatterCanvas(
                    pairs, correlation, chosen[x], chosen[y], colors[x], colors[y],
                    selected.coerceIn(0, pairs.lastIndex), { selected = it }, chartModifier
                )
            }
            OutlinedButton(onClick = {
                pairX = y
                pairY = x
            }) { Text("Swap axes") }
        }
    }

    CorrelationSummary(chosen[x], chosen[y], colors[x], colors[y], correlation, bucket)

    if (chosen.size > 2) {
        val all = remember(table) { table.matrix() }
        Text("All pairs", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in chosen.indices) for (j in i + 1 until chosen.size) {
                val c = all[i][j]
                val active = (i == x && j == y) || (i == y && j == x)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            pairX = i
                            pairY = j
                        }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(chosen[i].label, color = colors[i], style = MaterialTheme.typography.bodyMedium)
                    Text("×", style = MaterialTheme.typography.bodyMedium)
                    Text(chosen[j].label, color = colors[j], style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        if (c == null) "—" else "r ${GraphCompare.formatR(c.pearson)}",
                        style = if (active) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (table.rows.any { r -> r.filled.any { it } }) {
        Text(
            "≈ values are estimated between logs.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text(
        "Correlation shows things moving together, not that one causes the other.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CorrelationSummary(a: GraphMode, b: GraphMode, ca: Color, cb: Color, c: Correlation?, bucket: CompareBucket) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(a.label, color = ca, style = MaterialTheme.typography.titleSmall)
            Text("×", style = MaterialTheme.typography.titleSmall)
            Text(b.label, color = cb, style = MaterialTheme.typography.titleSmall)
        }
        if (c == null) {
            Text(
                "Not enough overlap to measure (they need to vary on the same ${if (bucket == CompareBucket.WEEK) "weeks" else "days"}).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        Text(
            "r = ${GraphCompare.formatR(c.pearson)}  •  ${c.summary()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        val unit = if (bucket == CompareBucket.WEEK) "weeks" else "days"
        Text(
            "ρ = ${GraphCompare.formatR(c.spearman)}  •  R² = ${String.format(Locale.getDefault(), "%.2f", c.rSquared)}  •  n = ${c.n} $unit  •  p ${if (c.pValue < 0.001) "< 0.001" else "= " + GraphCompare.formatP(c.pValue)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (c.estimated > 0) {
            Text(
                "${c.estimated} of ${c.n} use an estimated value.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        c.caution()?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun axisName(m: GraphMode): String = if (m.unit.isBlank()) m.label else "${m.label} (${m.unit})"

private fun paddedRange(lo: Double, hi: Double): Pair<Double, Double> {
    val span = hi - lo
    val pad = if (span <= 0.0) maxOf(1.0, abs(hi) * 0.05) else span * 0.12
    return (lo - pad) to (hi + pad)
}

@Composable
private fun OverlayCanvas(
    table: CompareTable,
    colors: List<Color>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val rows = table.rows
    val n = rows.size
    val m = table.modes.size
    val ranges = remember(table) { (0 until m).map { i -> table.range(i)?.let { paddedRange(it.first, it.second) } } }
    val dual = m == 2
    val tickStyles = colors.map { TextStyle(fontSize = 10.sp, color = it) }
    val axisStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val tickText: (Int, Double) -> String = { i, f ->
        val r = ranges[i]
        if (r == null) "" else GraphWindow.formatValue(r.first + (r.second - r.first) * f)
    }
    val fractions = listOf(0.0, 0.5, 1.0)
    val leftPad = with(density) { 8.dp.toPx() } + if (dual) fractions.maxOf { measurer.measure(tickText(0, it), tickStyles[0]).size.width } else 0
    val rightPad = with(density) { 10.dp.toPx() } + if (dual) fractions.maxOf { measurer.measure(tickText(1, it), tickStyles[1]).size.width } else 0
    val topPad = with(density) { 8.dp.toPx() }
    val bottomPad = with(density) { 22.dp.toPx() }
    val inset = with(density) { 8.dp.toPx() }
    val spanDays = (rows.last().timeMs - rows.first().timeMs) / 86_400_000.0
    val axisFmt = remember(spanDays > 300) { SimpleDateFormat(if (spanDays > 300) "MMM yy" else "d MMM", Locale.getDefault()) }

    fun xAt(k: Int, width: Float): Float {
        val chartW = width - leftPad - rightPad
        if (n == 1) return leftPad + chartW / 2
        val t0 = rows.first().timeMs
        val t1 = rows.last().timeMs
        val frac = if (t1 == t0) k / (n - 1).toFloat() else (rows[k].timeMs - t0).toFloat() / (t1 - t0).toFloat()
        return leftPad + inset + frac * (chartW - 2 * inset)
    }

    fun nearest(px: Float, width: Float): Int = (0 until n).minByOrNull { abs(xAt(it, width) - px) } ?: 0

    Canvas(
        modifier = modifier
            .pointerInput(table) { detectTapGestures { o -> onSelect(nearest(o.x, size.width.toFloat())) } }
            .pointerInput(table) {
                detectHorizontalDragGestures { change, _ -> onSelect(nearest(change.position.x, size.width.toFloat())) }
            }
    ) {
        val w = size.width
        val bottom = size.height - bottomPad
        val chartH = bottom - topPad
        fun yAt(i: Int, v: Double): Float {
            val r = ranges[i] ?: return bottom
            return bottom - ((v - r.first) / (r.second - r.first)).toFloat() * chartH
        }

        fractions.forEach { f ->
            val yy = bottom - f.toFloat() * chartH
            drawLine(grid, Offset(leftPad, yy), Offset(w - rightPad, yy), strokeWidth = 1.dp.toPx())
            if (dual) {
                val l = measurer.measure(tickText(0, f), tickStyles[0])
                drawText(l, topLeft = Offset(leftPad - 6.dp.toPx() - l.size.width, yy - l.size.height / 2f))
                val r = measurer.measure(tickText(1, f), tickStyles[1])
                drawText(r, topLeft = Offset(w - rightPad + 6.dp.toPx(), yy - r.size.height / 2f))
            }
        }

        for (i in 0 until m) {
            val color = colors[i]
            val path = Path()
            var down = false
            var segments = 0
            for (k in 0 until n) {
                val row = rows[k]
                if (!row.has(i)) {
                    down = false
                    continue
                }
                if (row.filled[i]) continue
                val px = xAt(k, w)
                val py = yAt(i, row.values[i])
                if (down) {
                    path.lineTo(px, py)
                    segments++
                } else {
                    path.moveTo(px, py)
                    down = true
                }
            }
            if (segments > 0) drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (n <= 60) {
                for (k in 0 until n) if (rows[k].real(i)) drawCircle(color, 3.dp.toPx(), Offset(xAt(k, w), yAt(i, rows[k].values[i])))
            }
        }

        val sx = xAt(selected, w)
        drawLine(labelColor.copy(alpha = 0.35f), Offset(sx, topPad), Offset(sx, bottom), strokeWidth = 1.dp.toPx())
        val sel = rows[selected]
        for (i in 0 until m) {
            if (!sel.has(i)) continue
            val c = Offset(sx, yAt(i, sel.values[i]))
            if (sel.filled[i]) drawCircle(colors[i], 5.dp.toPx(), c, style = Stroke(width = 2.dp.toPx()))
            else drawCircle(colors[i], 5.dp.toPx(), c)
        }

        val labelY = bottom + 4.dp.toPx()
        val first = measurer.measure(axisFmt.format(Date(rows.first().timeMs)), axisStyle)
        drawText(first, topLeft = Offset(leftPad, labelY))
        if (n > 1) {
            val last = measurer.measure(axisFmt.format(Date(rows.last().timeMs)), axisStyle)
            drawText(last, topLeft = Offset(w - rightPad - last.size.width, labelY))
        }
    }
}

@Composable
private fun ScatterCanvas(
    pairs: List<ComparePair>,
    correlation: Correlation?,
    xMode: GraphMode,
    yMode: GraphMode,
    xColor: Color,
    yColor: Color,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val dot = MaterialTheme.colorScheme.primary
    val trend = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val xr = remember(pairs) { paddedRange(pairs.minOf { it.x }, pairs.maxOf { it.x }) }
    val yr = remember(pairs) { paddedRange(pairs.minOf { it.y }, pairs.maxOf { it.y }) }
    val xStyle = TextStyle(fontSize = 10.sp, color = xColor)
    val yStyle = TextStyle(fontSize = 10.sp, color = yColor)
    val fractions = listOf(0.0, 0.5, 1.0)
    val yTicks = fractions.map { GraphWindow.formatValue(yr.first + (yr.second - yr.first) * it) }
    val xTicks = fractions.map { GraphWindow.formatValue(xr.first + (xr.second - xr.first) * it) }
    val leftPad = with(density) { 8.dp.toPx() } + yTicks.maxOf { measurer.measure(it, yStyle).size.width }
    val rightPad = with(density) { 12.dp.toPx() }
    val topPad = with(density) { 18.dp.toPx() }
    val bottomPad = with(density) { 34.dp.toPx() }

    fun px(v: Double, width: Float): Float = leftPad + ((v - xr.first) / (xr.second - xr.first)).toFloat() * (width - leftPad - rightPad)
    fun py(v: Double, height: Float): Float {
        val bottom = height - bottomPad
        return bottom - ((v - yr.first) / (yr.second - yr.first)).toFloat() * (bottom - topPad)
    }
    fun nearest(o: Offset, width: Float, height: Float): Int = pairs.indices.minByOrNull { k ->
        val dx = px(pairs[k].x, width) - o.x
        val dy = py(pairs[k].y, height) - o.y
        dx * dx + dy * dy
    } ?: 0

    Canvas(
        modifier = modifier
            .pointerInput(pairs) { detectTapGestures { o -> onSelect(nearest(o, size.width.toFloat(), size.height.toFloat())) } }
    ) {
        val w = size.width
        val h = size.height
        val bottom = h - bottomPad

        fractions.forEachIndexed { k, f ->
            val yy = bottom - f.toFloat() * (bottom - topPad)
            drawLine(grid, Offset(leftPad, yy), Offset(w - rightPad, yy), strokeWidth = 1.dp.toPx())
            val l = measurer.measure(yTicks[k], yStyle)
            drawText(l, topLeft = Offset(leftPad - 6.dp.toPx() - l.size.width, yy - l.size.height / 2f))
            val xx = leftPad + f.toFloat() * (w - leftPad - rightPad)
            drawLine(grid, Offset(xx, topPad), Offset(xx, bottom), strokeWidth = 1.dp.toPx())
            val t = measurer.measure(xTicks[k], xStyle)
            val tx = when (k) {
                0 -> xx
                fractions.lastIndex -> xx - t.size.width
                else -> xx - t.size.width / 2f
            }
            drawText(t, topLeft = Offset(tx, bottom + 4.dp.toPx()))
        }

        if (correlation != null) {
            val path = Path()
            val steps = 24
            var started = false
            for (s in 0..steps) {
                val xv = xr.first + (xr.second - xr.first) * s / steps
                val yv = correlation.yAt(xv)
                if (yv < yr.first || yv > yr.second) {
                    started = false
                    continue
                }
                if (started) path.lineTo(px(xv, w), py(yv, h)) else path.moveTo(px(xv, w), py(yv, h))
                started = true
            }
            drawPath(
                path, trend.copy(alpha = 0.7f),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            )
        }

        val radius = (if (pairs.size > 80) 2.5.dp else 3.5.dp).toPx()
        pairs.forEachIndexed { k, p ->
            val c = Offset(px(p.x, w), py(p.y, h))
            if (p.estimated) drawCircle(dot.copy(alpha = 0.7f), radius, c, style = Stroke(width = 1.5.dp.toPx()))
            else drawCircle(dot.copy(alpha = 0.75f), radius, c)
        }
        val s = pairs[selected]
        drawCircle(dot, 6.dp.toPx(), Offset(px(s.x, w), py(s.y, h)), style = Stroke(width = 2.dp.toPx()))

        val yName = measurer.measure(axisName(yMode), yStyle)
        drawText(yName, topLeft = Offset(leftPad, 0f))
        val xName = measurer.measure(axisName(xMode), xStyle)
        drawText(xName, topLeft = Offset(w - rightPad - xName.size.width, h - xName.size.height))
    }
}
