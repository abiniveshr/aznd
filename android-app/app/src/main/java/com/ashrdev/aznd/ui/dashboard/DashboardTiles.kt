package com.ashrdev.aznd.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashrdev.aznd.domain.CompareBucket
import com.ashrdev.aznd.domain.GraphCompare
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.GraphPoint
import com.ashrdev.aznd.domain.GraphStyle
import com.ashrdev.aznd.domain.GraphWindow
import com.ashrdev.aznd.ui.common.BodyFigure
import com.ashrdev.aznd.ui.common.compareColors
import com.ashrdev.aznd.ui.common.rememberGraphRangeState
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.macro.GaugeArc
import com.ashrdev.aznd.ui.macro.GaugeSegment
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val M = DashMetrics
private const val LINE = 1.3f
private const val GAUGE_MAX = 340f

private fun style(sp: Float, k: Float, weight: FontWeight = FontWeight.Normal): TextStyle =
    TextStyle(fontSize = (sp * k).sp, lineHeight = (sp * k * LINE).sp, fontWeight = weight)

@Composable
fun rememberDashMeasure(): DashMeasure {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, density) {
        object : DashMeasure {
            override fun width(text: String, sp: Float, bold: Boolean): Float {
                if (text.isEmpty()) return 0f
                val r = measurer.measure(
                    text,
                    style(sp, 1f, if (bold) FontWeight.Bold else FontWeight.Normal),
                    softWrap = false,
                    maxLines = 1
                )
                return with(density) { r.size.width.toDp().value } + 1f
            }

            override fun line(sp: Float): Float = with(density) { (sp * LINE).sp.toDp().value }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashTile(
    widget: DashWidget,
    cell: DashCell,
    data: DashboardData,
    onOpen: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier = Modifier
) {
    val k = cell.scale * widget.size.factor
    Card(
        modifier = modifier
            .clip(CardDefaults.shape)
            .combinedClickable(onClick = onOpen, onLongClick = onHold)
    ) {
        Box(Modifier.fillMaxSize()) {
            when (widget.type) {
                DashWidgetType.CALORIES -> CaloriesTile(widget.calorieView, data.today, cell)
                DashWidgetType.STREAK -> StreakTile(DashboardText.streak(widget, data), cell.columns, k, data.streakFor(widget) == null)
                DashWidgetType.LATEST_WORKOUT -> WorkoutTile(DashboardText.workout(data), cell.columns, k)
                DashWidgetType.GRAPH -> GraphTile(widget, data, cell)
                DashWidgetType.BODY_MODEL -> ModelTile(widget.model, cell)
                DashWidgetType.SHORTCUT -> ShortcutTile(widget, DashboardText.shortcut(widget, data), cell.columns, k)
                DashWidgetType.TOP_LIFT -> LiftTile(DashboardText.lift(widget, data), cell.columns, k)
            }
        }
    }
}

@Composable
private fun Line(text: String, sp: Float, k: Float, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal, color: Color = Color.Unspecified, align: TextAlign? = null) {
    Text(
        text,
        modifier = modifier,
        style = style(sp, k, weight),
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = align
    )
}

@Composable
private fun muted(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun StreakTile(c: StreakContent, columns: Int, k: Float, missing: Boolean) {
    val flame = if (missing) muted() else MaterialTheme.colorScheme.primary
    val header: @Composable (Modifier) -> Unit = { mod ->
        Row(mod, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = flame, modifier = Modifier.size((M.ICON * k).dp))
            Spacer(Modifier.width((6f * k).dp))
            Line(c.name, M.NAME, k, weight = FontWeight.SemiBold)
        }
    }
    val count: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.Bottom) {
            Line(c.days, M.BIG, k, weight = FontWeight.Bold, color = if (missing) muted() else Color.Unspecified)
            Spacer(Modifier.width((6f * k).dp))
            Line(c.unit, M.LABEL, k, color = muted(), modifier = Modifier.padding(bottom = (5f * k).dp))
        }
    }
    val pad = (M.PAD * k).dp
    if (columns == M.STREAK_ROW) {
        Row(Modifier.fillMaxSize().padding(pad), verticalAlignment = Alignment.CenterVertically) {
            header(Modifier.weight(1f))
            Spacer(Modifier.width((M.SIDE_GAP * k).dp))
            count()
        }
    } else {
        Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.Center) {
            header(Modifier)
            Spacer(Modifier.height((4f * k).dp))
            count()
        }
    }
}

@Composable
private fun WorkoutTile(c: WorkoutContent, columns: Int, k: Float) {
    val header: @Composable (Modifier) -> Unit = { mod ->
        Column(mod) {
            Line(c.heading, M.LABEL, k, color = muted())
            Spacer(Modifier.height((2f * k).dp))
            Line(c.name, M.NAME, k, weight = FontWeight.SemiBold)
            Spacer(Modifier.height((2f * k).dp))
            Line(c.whenText, M.LABEL, k, color = muted())
        }
    }
    val figures: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy((M.FIGURE_GAP * k).dp)) {
            for (f in c.figures) {
                Column {
                    Line(f.value, M.VALUE, k, weight = FontWeight.Bold)
                    Line(f.label, M.SMALL, k, color = muted())
                }
            }
        }
    }
    val pad = (M.PAD * k).dp
    when {
        c.figures.isEmpty() -> Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.Center) { header(Modifier) }
        columns == M.WORKOUT_ROW -> Row(Modifier.fillMaxSize().padding(pad), verticalAlignment = Alignment.CenterVertically) {
            header(Modifier.weight(1f))
            Spacer(Modifier.width((M.SIDE_GAP * k).dp))
            figures()
        }
        columns == M.WORKOUT_SPLIT -> Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.Center) {
            header(Modifier)
            Spacer(Modifier.height((10f * k).dp))
            figures()
        }
        else -> Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.Center) {
            header(Modifier)
            Spacer(Modifier.height((8f * k).dp))
            Column(verticalArrangement = Arrangement.spacedBy((2f * k).dp)) {
                for (f in c.figures) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Line(f.label, M.LABEL, k, color = muted(), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width((12f * k).dp))
                        Line(f.value, M.ROW, k, weight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutTile(w: DashWidget, c: ShortcutContent, columns: Int, k: Float) {
    val icon = DashboardTargets.icon(w.shortcut)
    val pad = (M.PAD * k).dp
    val iconSize = (M.SHORTCUT_ICON * k).dp
    if (columns == M.SHORTCUT_ROW) {
        Row(Modifier.fillMaxSize().padding(pad), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width((10f * k).dp))
            Line(c.label, M.NAME, k, weight = FontWeight.SemiBold)
        }
    } else {
        Column(
            Modifier.fillMaxSize().padding(pad),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(iconSize))
            Spacer(Modifier.height((6f * k).dp))
            Line(c.label, M.NAME, k, weight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LiftTile(c: LiftContent, columns: Int, k: Float) {
    val pad = (M.PAD * k).dp
    val accent = MaterialTheme.colorScheme.primary
    if (columns == M.LIFT_ROW) {
        Row(Modifier.fillMaxSize().padding(pad), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Line(c.heading, M.LABEL, k, color = muted())
                Line(c.name, M.NAME, k, weight = FontWeight.SemiBold)
                Line(c.topSet, M.LABEL, k, color = muted())
            }
            Spacer(Modifier.width((M.SIDE_GAP * k).dp))
            Line(c.e1rm, M.VALUE, k, weight = FontWeight.Bold, color = accent)
        }
    } else {
        Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.Center) {
            Line(c.heading, M.LABEL, k, color = muted())
            Line(c.name, M.NAME, k, weight = FontWeight.SemiBold)
            Line(c.e1rm, M.VALUE, k, weight = FontWeight.Bold, color = accent)
            Line(c.topSet, M.LABEL, k, color = muted())
        }
    }
}

private fun kcal(v: Double): String = GraphWindow.formatValue(v.roundToInt().toDouble())

@Composable
private fun CaloriesTile(view: DashCalorieView, t: TodayCalories, cell: DashCell) {
    val k = min(cell.w / 220f, cell.h / 200f).coerceIn(0.8f, 1.4f)
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val protein = MaterialTheme.colorScheme.primary
    val carbs = MaterialTheme.colorScheme.tertiary
    val fat = MaterialTheme.colorScheme.secondary
    val goals = t.goals
    val hasGoals = goals.proteinG > 0 || goals.carbsG > 0 || goals.fatG > 0
    fun part(eaten: Double, goal: Int) = if (goal > 0) (eaten / goal).toFloat() else 0f
    val segments = if (view == DashCalorieView.NET) {
        listOf(GaugeSegment(1f, if (t.allowance > 0.0) (t.totals.calories / t.allowance).toFloat() else 0f, if (t.over) error else primary))
    } else if (hasGoals) {
        listOf(
            GaugeSegment((goals.proteinG * 4f).coerceAtLeast(1f), part(t.totals.proteinG, goals.proteinG), protein),
            GaugeSegment((goals.carbsG * 4f).coerceAtLeast(1f), part(t.totals.carbsG, goals.carbsG), carbs),
            GaugeSegment((goals.fatG * 9f).coerceAtLeast(1f), part(t.totals.fatG, goals.fatG), fat)
        )
    } else {
        listOf(GaugeSegment(1f, 0f, protein), GaugeSegment(1f, 0f, carbs), GaugeSegment(1f, 0f, fat))
    }
    val of = when {
        view == DashCalorieView.NET && t.allowance > 0.0 -> "of ${kcal(t.allowance)} kcal"
        view == DashCalorieView.MACROS && hasGoals -> "of ${kcal(goals.kcal)} kcal"
        else -> "kcal eaten"
    }
    val figures: List<Triple<String, String, Color?>> = if (view == DashCalorieView.NET) {
        listOf(
            Triple("Target", t.target?.let { kcal(it) } ?: "–", null),
            Triple("Burned", kcal(t.burnt), null),
            Triple(if (t.left >= 0) "Left" else "Over", if (t.target != null) kcal(abs(t.left)) else "–", if (t.over) error else null)
        )
    } else {
        listOf(
            Triple("Protein", grams(t.totals.proteinG, goals.proteinG), protein),
            Triple("Carbs", grams(t.totals.carbsG, goals.carbsG), carbs),
            Triple("Fat", grams(t.totals.fatG, goals.fatG), fat)
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding((12f * k).dp)) {
        val w = maxWidth.value
        val h = maxHeight.value
        val labelH = M.LABEL * k * LINE
        val wide = w / h.coerceAtLeast(1f) > 1.45f
        Column(Modifier.fillMaxSize()) {
            Line(if (view == DashCalorieView.NET) "Calories today" else "Macros today", M.LABEL, k, color = muted())
            if (wide) {
                val gw = min((h - labelH) * 1.25f, w * 0.58f).coerceAtLeast(40f)
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Gauge(segments, gw, t.totals.calories, of)
                    Spacer(Modifier.width((12f * k).dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((6f * k).dp)) {
                        for ((label, value, color) in figures) Figure(label, value, color, k, Alignment.Start)
                    }
                }
            } else {
                val figH = (M.LABEL + M.ROW) * k * LINE + 6f * k
                val room = h - labelH
                val withFigures = room - figH >= 90f * k
                val gw = min(w, (room - if (withFigures) figH else 0f) * 1.25f).coerceAtLeast(40f)
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Gauge(segments, gw, t.totals.calories, of)
                    if (withFigures) {
                        Spacer(Modifier.height((6f * k).dp))
                        Row(Modifier.fillMaxWidth()) {
                            for ((label, value, color) in figures) Figure(label, value, color, k, Alignment.CenterHorizontally, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private fun grams(eaten: Double, goal: Int): String =
    if (goal > 0) "${GraphWindow.formatValue(eaten)}/$goal g" else "${GraphWindow.formatValue(eaten)} g"

@Composable
private fun Gauge(segments: List<GaugeSegment>, width: Float, consumed: Double, of: String) {
    val gw = min(width, GAUGE_MAX)
    GaugeArc(segments = segments, modifier = Modifier.width(gw.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(kcal(consumed), style = TextStyle(fontSize = (gw * 0.16f).sp, lineHeight = (gw * 0.19f).sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(of, style = TextStyle(fontSize = (gw * 0.062f).sp, lineHeight = (gw * 0.08f).sp), color = muted(), maxLines = 1)
        }
    }
}

@Composable
private fun Figure(label: String, value: String, color: Color?, k: Float, align: Alignment.Horizontal, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = align) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (color != null) {
                Box(Modifier.size((8f * k).dp).clip(CircleShape).background(color))
                Spacer(Modifier.width((4f * k).dp))
            }
            Line(label, M.LABEL, k, color = muted())
        }
        Line(value, M.ROW, k, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun GraphTile(w: DashWidget, data: DashboardData, cell: DashCell) {
    val k = min(cell.w / 240f, cell.h / 150f).coerceIn(0.85f, 1.3f)
    val modes = data.modesFor(w)
    val range = rememberGraphRangeState().range
    val colors = compareColors()
    val shown = remember(modes, range, data.nowMs) { modes.map { GraphWindow.selectPoints(it.points, range, data.nowMs) } }
    val corr = remember(modes, range, data.nowMs) {
        if (modes.size < 2) null
        else GraphCompare.table(modes.take(2), CompareBucket.DAY, GraphCompare.windowStart(modes, range, data.nowMs), data.nowMs).correlation(0, 1)
    }
    val enough = shown.any { it.size >= 2 }
    if (modes.isEmpty()) {
        Column(Modifier.fillMaxSize().padding((12f * k).dp)) {
            Line("Graph", M.NAME, k, weight = FontWeight.SemiBold)
            Line("Pick a variable in dashboard settings", M.LABEL, k, color = muted())
        }
        return
    }
    Column(Modifier.fillMaxSize().padding((12f * k).dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            modes.forEachIndexed { i, m ->
                if (i > 0) Spacer(Modifier.width((10f * k).dp))
                Box(Modifier.size((8f * k).dp).clip(CircleShape).background(colors[i % colors.size]))
                Spacer(Modifier.width((4f * k).dp))
                Line(m.label, M.LABEL, k, color = muted(), modifier = Modifier.weight(1f, fill = false))
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            modes.forEachIndexed { i, m ->
                if (i > 0) Spacer(Modifier.width((12f * k).dp))
                val last = shown[i].lastOrNull()
                Line(
                    if (last == null) "–" else "${GraphWindow.formatValue(last.value)} ${m.unit}".trim(),
                    M.VALUE, k, weight = FontWeight.Bold, color = if (modes.size > 1) colors[i % colors.size] else Color.Unspecified
                )
            }
        }
        Spacer(Modifier.height((6f * k).dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (enough) {
                MiniChart(modes, shown, colors, k, Modifier.fillMaxSize())
            } else {
                Line("Not enough data yet", M.LABEL, k, color = muted(), modifier = Modifier.align(Alignment.Center))
            }
        }
        if (corr != null) {
            Spacer(Modifier.height((4f * k).dp))
            Line(
                if (corr.enough) "r ${GraphCompare.formatR(corr.pearson)} · ${corr.strength.label}" else "Too few days to correlate",
                M.SMALL, k, color = muted()
            )
        }
    }
}

@Composable
private fun MiniChart(modes: List<GraphMode>, shown: List<List<GraphPoint>>, colors: List<Color>, k: Float, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Canvas(modifier) {
        val all = shown.flatten()
        if (all.isEmpty()) return@Canvas
        val t0 = all.minOf { it.timeMs }
        val t1 = all.maxOf { it.timeMs }
        val span = (t1 - t0).coerceAtLeast(1L).toFloat()
        val inset = 4.dp.toPx() * k
        val cw = size.width - inset * 2
        val ch = size.height - inset * 2
        fun x(t: Long): Float = inset + if (t1 == t0) cw / 2f else (t - t0) / span * cw
        drawLine(track, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx())
        shown.forEachIndexed { i, pts ->
            if (pts.isEmpty()) return@forEachIndexed
            val m = modes[i]
            val color = colors[i % colors.size]
            var lo = pts.minOf { it.value }
            var hi = pts.maxOf { it.value }
            if (m.startAtZero) lo = min(0.0, lo)
            if (hi - lo < 1e-9) {
                hi += 1.0
                lo -= if (m.startAtZero && lo >= 0.0) 0.0 else 1.0
            }
            fun y(v: Double): Float = inset + ch - ((v - lo) / (hi - lo)).toFloat() * ch
            if (m.style == GraphStyle.BARS && i == 0) {
                val bw = max(2.dp.toPx(), min(cw / (pts.size * 1.6f), 14.dp.toPx() * k))
                for (p in pts) {
                    val top = y(p.value)
                    drawRect(color.copy(alpha = 0.85f), Offset(x(p.timeMs) - bw / 2f, top), Size(bw, inset + ch - top))
                }
            } else {
                val path = Path()
                pts.forEachIndexed { j, p -> if (j == 0) path.moveTo(x(p.timeMs), y(p.value)) else path.lineTo(x(p.timeMs), y(p.value)) }
                drawPath(path, color, style = Stroke(width = 2.5.dp.toPx() * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
                val last = pts.last()
                drawCircle(color, radius = 3.5.dp.toPx() * k, center = Offset(x(last.timeMs), y(last.value)))
            }
        }
    }
}

@Composable
private fun ModelTile(model: DashModel, cell: DashCell) {
    val k = min(cell.w / 180f, cell.h / 220f).coerceIn(0.85f, 1.3f)
    val pad = (12f * k).dp
    val wide = cell.w / cell.h.coerceAtLeast(1f) > 0.95f
    if (wide) {
        Row(Modifier.fillMaxSize().padding(pad), verticalAlignment = Alignment.CenterVertically) {
            BodyFigure(Modifier.fillMaxHeight().aspectRatio(0.5f))
            Spacer(Modifier.width((12f * k).dp))
            Column(Modifier.weight(1f)) {
                Line(model.label, M.NAME, k, weight = FontWeight.SemiBold)
                Line("3D model coming soon", M.SMALL, k, color = muted())
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally) {
            BodyFigure(Modifier.weight(1f).aspectRatio(0.5f))
            Spacer(Modifier.height((6f * k).dp))
            Line(model.label, M.NAME, k, weight = FontWeight.SemiBold)
            Line("3D model coming soon", M.SMALL, k, color = muted())
        }
    }
}
