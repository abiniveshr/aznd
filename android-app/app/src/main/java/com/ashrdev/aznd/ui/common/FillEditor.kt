@file:OptIn(ExperimentalMaterial3Api::class)

package com.ashrdev.aznd.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.share.ChipChoice
import com.ashrdev.aznd.ui.common.share.ColorRow
import com.ashrdev.aznd.ui.common.share.LabeledSlider
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.theme.FillBase
import com.ashrdev.aznd.ui.theme.FillStyle
import com.ashrdev.aznd.ui.theme.GradientKind
import com.ashrdev.aznd.ui.theme.GradientSpec
import com.ashrdev.aznd.ui.theme.GradientStop
import com.ashrdev.aznd.ui.theme.PatternKind
import com.ashrdev.aznd.ui.theme.PatternSpec
import com.ashrdev.aznd.ui.theme.ResolvedFill
import com.ashrdev.aznd.ui.theme.azndFill
import com.ashrdev.aznd.ui.theme.mixArgb
import java.util.Locale
import kotlin.math.roundToInt

private class GradientPreset(val name: String, val kind: GradientKind, val angle: Int, val colors: IntArray)

private val GradientPresets = listOf(
    GradientPreset("Sunset", GradientKind.LINEAR, 135, intArrayOf(0xFF2B0F3A.toInt(), 0xFFD9482B.toInt(), 0xFFFFD166.toInt())),
    GradientPreset("Ocean", GradientKind.LINEAR, 160, intArrayOf(0xFF031B34.toInt(), 0xFF0B6E8E.toInt(), 0xFF3DD6D0.toInt())),
    GradientPreset("Fire", GradientKind.LINEAR, 90, intArrayOf(0xFF3A0605.toInt(), 0xFFE11D2E.toInt(), 0xFFFF8A4C.toInt(), 0xFFFFD166.toInt())),
    GradientPreset("Gold", GradientKind.LINEAR, 90, intArrayOf(0xFFFFD54A.toInt(), 0xFFF2B705.toInt(), 0xFFC98F00.toInt())),
    GradientPreset("Steel", GradientKind.LINEAR, 160, intArrayOf(0xFFE8ECF2.toInt(), 0xFFB5BDC9.toInt(), 0xFF6D7580.toInt(), 0xFF2E3238.toInt())),
    GradientPreset("Glow", GradientKind.RADIAL, 0, intArrayOf(0xFF7A1220.toInt(), 0xFF3A0A12.toInt(), 0xFF0A0A0B.toInt())),
    GradientPreset("Aurora", GradientKind.LINEAR, 120, intArrayOf(0xFF0B1F3A.toInt(), 0xFF1FBF6B.toInt(), 0xFF7C9CFF.toInt(), 0xFFC58CFF.toInt())),
    GradientPreset("Rainbow", GradientKind.SWEEP, 0, intArrayOf(0xFFFF5A5F.toInt(), 0xFFFFD166.toInt(), 0xFF5CE0A0.toInt(), 0xFF7C9CFF.toInt(), 0xFFC58CFF.toInt(), 0xFFFF5A5F.toInt()))
)

/** Another element of the theme the editor can copy from: its name, its base colour and its fill. */
class FillCopySource(val label: String, val baseColor: Int, val fill: FillStyle)

/**
 * Edits how one surface is filled, right where it is placed (no popup): solid or a multi-colour
 * gradient (linear, radial, sweep, any number of colours and positions) with any number of patterns
 * laid over it. Patterns sit in their own collapsible section; each pattern is a collapsible card
 * (settings, on/off, reload, add another, remove). Used for the app's surfaces (Appearance), the Flex
 * card's background and panels, and the dashboard's background and tiles.
 *
 * It holds no fill of its own: every change goes out through [onChange] at once, and the caller hands
 * the new [baseColor] and [fill] back. [baseColor] is the solid colour / the colour under the patterns.
 *
 * [chooseBase] shows the Solid / Gradient switch; off when the caller picks that elsewhere (Flex's
 * background has Solid / Gradient / Photo above it).
 * [copyFrom] lists the other elements of the theme. The Copy button lets the person pick one and take its
 * colour, gradient and patterns as the starting point; with an empty list there is no Copy button.
 */
@Composable
fun FillEditor(
    baseLabel: String,
    baseColor: Int,
    fill: FillStyle,
    onChange: (baseColor: Int, fill: FillStyle) -> Unit,
    modifier: Modifier = Modifier,
    chooseBase: Boolean = true,
    copyFrom: List<FillCopySource> = emptyList()
) {
    val color = baseColor
    val patterns = fill.patterns
    // Each pattern keeps an id while others are added and removed, so open/closed state follows the card.
    var ids by remember { mutableStateOf(List(patterns.size) { it }) }
    var nextId by remember { mutableIntStateOf(patterns.size) }
    var expanded by remember { mutableStateOf(emptySet<Int>()) }
    var pickStop by remember { mutableStateOf<Int?>(null) }
    var pickTintFor by remember { mutableStateOf<Int?>(null) }
    var showCopy by remember { mutableStateOf(false) }
    var patternsOpen by rememberSaveable { mutableStateOf(patterns.isNotEmpty()) }

    // The caller changed the patterns itself (a reset, a starter look): give them fresh ids.
    LaunchedEffect(patterns.size) {
        if (ids.size != patterns.size) {
            ids = List(patterns.size) { nextId + it }
            nextId += patterns.size
            expanded = emptySet()
        }
    }
    val layerIds = if (ids.size == patterns.size) ids else List(patterns.size) { -1 - it }

    fun emit(c: Int = color, f: FillStyle = fill) = onChange(c, f)

    fun gradient(change: (GradientSpec) -> GradientSpec) {
        emit(f = fill.copy(gradient = change(fill.gradient)))
    }

    fun updateLayer(id: Int, change: (PatternSpec) -> PatternSpec) {
        val at = layerIds.indexOf(id)
        if (at < 0) return
        emit(f = fill.copy(patterns = patterns.mapIndexed { i, p -> if (i == at) change(p) else p }))
    }

    fun insertLayer(spec: PatternSpec, after: Int? = null) {
        val id = nextId
        nextId += 1
        val at = if (after == null) -1 else layerIds.indexOf(after)
        val place = if (at < 0) patterns.size else at + 1
        ids = layerIds.take(place) + id + layerIds.drop(place)
        expanded = expanded + id
        emit(f = fill.copy(patterns = patterns.take(place) + spec + patterns.drop(place)))
    }

    fun removeLayer(id: Int) {
        val at = layerIds.indexOf(id)
        if (at < 0) return
        ids = layerIds.filterIndexed { i, _ -> i != at }
        expanded = expanded - id
        emit(f = fill.copy(patterns = patterns.filterIndexed { i, _ -> i != at }))
    }

    /** Another one of the same pattern, with its settings, right under it and with its own random arrangement. */
    fun duplicateLayer(id: Int) {
        val src = patterns.getOrNull(layerIds.indexOf(id)) ?: return
        val seed = patterns.filter { it.kind == src.kind }.maxOf { it.seed } + 1
        insertLayer(src.copy(seed = seed, enabled = true), after = id)
    }

    /** Replaces the colour, gradient and patterns with another element's. Nothing is kept of the old ones. */
    fun adopt(source: FillCopySource) {
        ids = List(source.fill.patterns.size) { nextId + it }
        nextId += source.fill.patterns.size
        expanded = emptySet()
        emit(source.baseColor, source.fill)
    }

    pickStop?.let { index ->
        val stop = fill.gradient.stops.getOrNull(index)
        if (stop != null) {
            AdvancedColorPickerDialog(
                title = "Gradient colour ${index + 1}",
                initial = stop.color,
                onDismiss = { pickStop = null },
                onPick = { picked ->
                    gradient { g -> g.copy(stops = g.stops.mapIndexed { i, s -> if (i == index) s.copy(color = picked) else s }) }
                    pickStop = null
                }
            )
        } else pickStop = null
    }
    pickTintFor?.let { id ->
        val layer = patterns.getOrNull(layerIds.indexOf(id))
        if (layer != null) {
            AdvancedColorPickerDialog(
                title = "Pattern colour",
                initial = layer.tint,
                onDismiss = { pickTintFor = null },
                onPick = { picked ->
                    updateLayer(id) { it.copy(tint = picked) }
                    pickTintFor = null
                }
            )
        } else pickTintFor = null
    }

    if (showCopy) {
        AlertDialog(
            onDismissRequest = { showCopy = false },
            title = { Text("Copy from") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    copyFrom.forEach { source ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    adopt(source)
                                    showCopy = false
                                }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .azndFill(ResolvedFill(Color(source.baseColor), source.fill), CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(source.label)
                                Text(
                                    if (source.fill.isPlain) String.format(Locale.US, "#%06X", 0xFFFFFF and source.baseColor) else source.fill.summary(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showCopy = false }) { Text("Cancel") } }
        )
    }

    val shape = RoundedCornerShape(12.dp)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .azndFill(ResolvedFill(Color(color), fill), shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
        )
        if (chooseBase) {
            ChipChoice(FillBase.entries, fill.base, { it.label }) { picked ->
                emit(
                    f = if (picked == FillBase.GRADIENT && fill.gradient == GradientSpec()) {
                        // First time: start from the current colour fading to a darker one.
                        fill.copy(
                            base = picked,
                            gradient = GradientSpec.of(GradientKind.LINEAR, 90, color, mixArgb(color, 0xFF000000.toInt(), 0.55f))
                        )
                    } else fill.copy(base = picked)
                )
            }
        }

        if (fill.base == FillBase.SOLID) {
            ColorRow(baseLabel, color) { emit(c = it) }
        } else {
            GradientSection(
                g = fill.gradient,
                onChange = { change -> gradient(change) },
                onPickStop = { pickStop = it },
                onPreset = { p -> gradient { cur -> GradientSpec.of(p.kind, p.angle, *p.colors).copy(centerX = cur.centerX, centerY = cur.centerY, radius = cur.radius) } }
            )
        }

        val under = ResolvedFill(Color(fill.representative(color)), FillStyle.Plain)
        FillFold(
            title = "Patterns",
            summary = patternSummary(patterns),
            open = patternsOpen,
            onToggle = { patternsOpen = !patternsOpen }
        ) {
            // The patterns to pick from. Tapping one adds it as a card below (or opens its card if it is already there).
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(PatternKind.entries.filter { it != PatternKind.NONE }) { kind ->
                    PatternTile(
                        kind = kind,
                        under = under,
                        count = patterns.count { it.kind == kind },
                        tint = patterns.firstOrNull { it.kind == kind }?.tint ?: 0xFFFFFFFF.toInt()
                    ) {
                        val at = patterns.indexOfFirst { it.kind == kind }
                        if (at < 0) insertLayer(PatternSpec.of(kind)) else expanded = expanded + layerIds[at]
                    }
                }
            }
            if (patterns.isEmpty()) {
                Text(
                    "No patterns yet. Tap one above to add it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            patterns.forEachIndexed { index, spec ->
                val id = layerIds[index]
                key(id) {
                    val sameKind = patterns.filter { it.kind == spec.kind }
                    PatternLayerCard(
                        spec = spec,
                        // "Dots 2" when there are several of one kind, plain "Dots" otherwise.
                        number = if (sameKind.size > 1) patterns.take(index).count { it.kind == spec.kind } + 1 else 0,
                        under = under.base,
                        expanded = id in expanded,
                        onToggleExpanded = { expanded = if (id in expanded) expanded - id else expanded + id },
                        onChange = { change -> updateLayer(id, change) },
                        onPickTint = { pickTintFor = id },
                        onAdd = { duplicateLayer(id) },
                        onRemove = { removeLayer(id) }
                    )
                }
            }
        }

        if (copyFrom.isNotEmpty()) {
            OutlinedButton(onClick = { showCopy = true }, modifier = Modifier.fillMaxWidth()) { Text("Copy from another element") }
        }
    }
}

private fun patternSummary(patterns: List<PatternSpec>): String {
    val on = patterns.filter { it.enabled }
    return when {
        patterns.isEmpty() -> "None"
        on.isEmpty() -> "${patterns.size} off"
        else -> on.joinToString(", ") { it.kind.label }
    }
}

/** A dropdown inside the editor: a header showing [title] and [summary] that opens and closes its [content]. */
@Composable
private fun FillFold(
    title: String,
    summary: String,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (open) "Collapse $title" else "Expand $title",
                    modifier = Modifier.rotate(if (open) 180f else 0f)
                )
            }
            AnimatedVisibility(visible = open) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) { content() }
            }
        }
    }
}

@Composable
private fun GradientSection(
    g: GradientSpec,
    onChange: ((GradientSpec) -> GradientSpec) -> Unit,
    onPickStop: (Int) -> Unit,
    onPreset: (GradientPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(GradientPresets) { p ->
                val spec = GradientSpec.of(p.kind, p.angle, *p.colors)
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onPreset(p) }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .azndFill(ResolvedFill(Color.Black, FillStyle.ofGradient(spec)), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Text(p.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }

        ChipChoice(GradientKind.entries, g.kind, { it.label }) { picked -> onChange { it.copy(kind = picked) } }

        if (g.kind != GradientKind.RADIAL) {
            LabeledSlider(
                if (g.kind == GradientKind.LINEAR) "Direction" else "Start angle",
                "${g.angle}°", g.angle.toFloat(), 0f..360f
            ) { v -> onChange { it.copy(angle = v.roundToInt()) } }
        }
        if (g.kind != GradientKind.LINEAR) {
            LabeledSlider("Centre across", "${(g.centerX * 100).roundToInt()}%", g.centerX, 0f..1f) { v -> onChange { it.copy(centerX = v) } }
            LabeledSlider("Centre down", "${(g.centerY * 100).roundToInt()}%", g.centerY, 0f..1f) { v -> onChange { it.copy(centerY = v) } }
        }
        if (g.kind == GradientKind.RADIAL) {
            LabeledSlider("Reach", "${(g.radius * 100).roundToInt()}%", g.radius, 0.1f..2.5f) { v -> onChange { it.copy(radius = v) } }
        }

        Text("Colours", style = MaterialTheme.typography.labelLarge)
        g.stops.forEachIndexed { index, stop ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(stop.color))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { onPickStop(index) }
                )
                Column(modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)) {
                    Text("At ${(stop.position * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = stop.position,
                        onValueChange = { v ->
                            onChange { cur -> cur.copy(stops = cur.stops.mapIndexed { i, s -> if (i == index) s.copy(position = v) else s }) }
                        },
                        valueRange = 0f..1f
                    )
                }
                IconButton(
                    enabled = g.stops.size > 2,
                    onClick = { onChange { cur -> cur.copy(stops = cur.stops.filterIndexed { i, _ -> i != index }) } }
                ) { Icon(Icons.Default.Delete, contentDescription = "Remove colour") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = g.stops.size < 8,
                onClick = {
                    onChange { cur ->
                        // New colour goes into the widest gap, mixed from its two neighbours.
                        val sorted = cur.cleanStops()
                        var at = 0
                        var gap = -1f
                        for (i in 0 until sorted.size - 1) {
                            val d = sorted[i + 1].position - sorted[i].position
                            if (d > gap) {
                                gap = d
                                at = i
                            }
                        }
                        val a = sorted[at]
                        val b = sorted[at + 1]
                        cur.copy(stops = cur.stops + GradientStop((a.position + b.position) / 2f, mixArgb(a.color, b.color, 0.5f)))
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add colour")
            }
            OutlinedButton(onClick = { onChange { it.reversed() } }) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Reverse")
            }
            OutlinedButton(onClick = {
                onChange { cur ->
                    val sorted = cur.cleanStops()
                    val n = sorted.size
                    cur.copy(stops = sorted.mapIndexed { i, s -> GradientStop(i / (n - 1).toFloat(), s.color) })
                }
            }) { Text("Even") }
        }
    }
}

/** One pattern in the editor. [id] stays the same while others are added and removed, so open/closed state follows the card. */
private class PatternLayer(val id: Int, val spec: PatternSpec)

@Composable
private fun PatternTile(kind: PatternKind, under: ResolvedFill, count: Int, tint: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val selected = count > 0
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val sample = FillStyle(patterns = listOf(PatternSpec.of(kind, intensity = maxOf(kind.defaultIntensity, 0.3f), tint = tint)))
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(shape)
                .azndFill(ResolvedFill(under.base, sample), shape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = shape
                )
        )
        Text(if (count > 1) "${kind.label} ×$count" else kind.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/**
 * One pattern as a collapsible card. The header (tap it to open or close the card) always shows the
 * pattern's name, a reload button (a new random arrangement), the on/off switch, + (another one of the
 * same pattern) and - (remove it). Open, the card shows Strength, Size and, for coloured patterns, the colour.
 */
@Composable
private fun PatternLayerCard(
    spec: PatternSpec,
    number: Int,
    under: Color,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onChange: ((PatternSpec) -> PatternSpec) -> Unit,
    onPickTint: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    val swatchShape = RoundedCornerShape(8.dp)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val preview = FillStyle(patterns = listOf(spec.copy(enabled = true, intensity = maxOf(spec.intensity, 0.3f))))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .alpha(if (spec.enabled) 1f else 0.4f)
                        .clip(swatchShape)
                        .azndFill(ResolvedFill(under, preview), swatchShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, swatchShape)
                )
                Text(
                    text = if (number > 0) "${spec.kind.label} $number" else spec.kind.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (spec.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                MiniIconButton(Icons.Default.Refresh, "Reload pattern", enabled = spec.kind.seeded) {
                    onChange { it.copy(seed = it.seed + 1) }
                }
                // Material's switch is 52 dp wide; a smaller one keeps the header on one line on narrow phones.
                Box(modifier = Modifier.size(width = 44.dp, height = 30.dp), contentAlignment = Alignment.Center) {
                    Switch(
                        checked = spec.enabled,
                        onCheckedChange = { on -> onChange { it.copy(enabled = on) } },
                        modifier = Modifier.scale(0.8f)
                    )
                }
                MiniIconButton(Icons.Default.Add, "Add another ${spec.kind.label.lowercase()} pattern", onClick = onAdd)
                MiniIconButton(Icons.Default.Remove, "Remove pattern", onClick = onRemove)
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(20.dp)
                        .rotate(if (expanded) 180f else 0f)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabeledSlider("Strength", "${(spec.intensity * 100).roundToInt()}%", spec.intensity, 0f..1f) { v ->
                        onChange { it.copy(intensity = v) }
                    }
                    LabeledSlider("Size", "${(spec.scale * 100).roundToInt()}%", spec.scale, 0.5f..4f) { v ->
                        onChange { it.copy(scale = v) }
                    }
                    if (spec.kind.tinted) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(spec.tint))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .clickable(onClick = onPickTint)
                            )
                            Text("Pattern colour", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onPickTint) { Text("Custom") }
                        }
                    }
                }
            }
        }
    }
}

/** A compact icon-only button (34 dp) for the pattern card header. */
@Composable
private fun MiniIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = LocalContentColor.current.copy(alpha = if (enabled) 1f else 0.38f)
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp), tint = tint)
    }
}
