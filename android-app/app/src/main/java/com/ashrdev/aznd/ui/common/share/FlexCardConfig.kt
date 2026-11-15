@file:OptIn(ExperimentalMaterial3Api::class)

package com.ashrdev.aznd.ui.common.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.calendar.CalendarZoom
import com.ashrdev.aznd.ui.calendar.MonthKey
import com.ashrdev.aznd.ui.calendar.inZoom
import com.ashrdev.aznd.ui.calendar.quadrantTitles
import com.ashrdev.aznd.ui.calendar.title
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.common.tiling.TileTree
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The settings of one card (one FlexBlock) on a poster: the "rules in card edit".
 *
 * Layout, the same for every card, top to bottom:
 *   1. Universal: the 3 × 3 alignment pad (across + down) beside the Size slider; under the slider, in PANEL mode,
 *      Duplicate and Delete (Delete asks first).
 *   2. What the card shows (per type): the stat of a Workout stats card, a Graph's variables and range, a Calendar's
 *      source and period, a Photo's picture.
 *   3. How it looks: Style (the type's variants), which items and how many, the type's detail switch, its text, a
 *      photo's crop.
 * Every change goes through [FlexCardConfig]'s onChange as a transform of the card with this id inside the poster the
 * caller holds (the caller budget-checks it and records undo, and may refuse it), and the panel always draws from the
 * spec it is given, never from what it asked for.
 */

/** Where the card settings are shown: under the poster for a card on it, or in the Add popup for a card on a draft. */
enum class FlexConfigMode { PANEL, ADD }

/** Sizes a card's content may be drawn at next to the others (its [FlexBlock.size]). */
private val SIZE_RANGE = 0.5f..2f

/** A card this panel put on the poster, to be selected once the poster has it ([before] = the poster when asked). */
private class PendingSelect(val id: Int, val before: FlexSpec)

/**
 * The settings of the card [blockId] on [spec] (in [FlexConfigMode.ADD] the draft, which already holds the new card).
 * Plain content (a Column, no scrolling of its own): the caller puts it in a scrolling bottom half or a popup.
 * [onSelect] moves the caller's focus: to a card this panel added (Duplicate, "Add another"), once that card is on
 * the poster, or to null after Delete.
 */
@Composable
internal fun FlexCardConfig(
    blockId: Int,
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    mode: FlexConfigMode,
    onChange: (continuous: Boolean, transform: (FlexSpec) -> FlexSpec) -> Unit,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val block = spec.blocks.firstOrNull { it.id == blockId }
    val selectNow by rememberUpdatedState(onSelect)

    // The caller may refuse a change (the poster is full), so a card added here is selected only once it is really
    // on the poster; a later change that does not hold it means it was refused, and nothing is selected.
    var pending by remember(blockId) { mutableStateOf<PendingSelect?>(null) }
    LaunchedEffect(spec, pending) {
        val p = pending ?: return@LaunchedEffect
        if (spec.blocks.any { it.id == p.id }) {
            pending = null
            selectNow(p.id)
        } else if (spec != p.before) {
            pending = null
        }
    }

    /** Changes this card, as it is in the poster at the time the change is made. */
    fun edit(continuous: Boolean = false, f: (FlexBlock) -> FlexBlock) {
        onChange(continuous) { sp -> sp.copy(blocks = sp.blocks.map { if (it.id == blockId) f(it) else it }) }
    }

    /** Puts a new card made from this one ([make] gets this card and the new id) right after it, then selects it. */
    fun addAfter(make: (FlexBlock, Int) -> FlexBlock) {
        val newId = spec.nextBlockId()
        onChange(false) { sp ->
            val from = sp.blocks.firstOrNull { it.id == blockId }
            if (from == null || sp.blocks.any { it.id == newId }) sp
            else sp.withBlockAfter(blockId, make(from, newId).unplaced())
        }
        pending = PendingSelect(newId, spec)
    }

    var confirmDelete by remember(blockId) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (block == null) {
            ConfigHint("This card is no longer on the poster.")
        } else {
            when {
                !block.enabled -> ConfigHint("This card is hidden. Turn it on in Cards to show it on the poster.")
                !content.has(block.type) -> ConfigHint("Nothing to show for this card here.")
            }

            // 1. Universal: alignment, size, and (on the poster) duplicate / delete.
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ConfigLabel("Align")
                    FlexAlignPad(block.hAlign, block.vAlign) { h, v -> edit { it.copy(hAlign = h, vAlign = v) } }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    LabeledSlider(
                        label = "Size",
                        valueText = "${(block.size * 100).roundToInt()}%",
                        value = block.size,
                        range = SIZE_RANGE
                    ) { v -> edit(continuous = true) { it.copy(size = v) } }
                    if (mode == FlexConfigMode.PANEL) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                onClick = { addAfter { from, id -> from.copy(id = id) } },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Duplicate")
                            }
                            TextButton(
                                onClick = { confirmDelete = true },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            HorizontalDivider()

            // 2 and 3. What the card shows and how it looks.
            val type = block.type
            when (type) {
                FlexBlockType.WORKOUT_STATS -> WorkoutStatsOptions(
                    block = block,
                    spec = spec,
                    content = content,
                    edit = { c, f -> edit(c, f) },
                    onAddStat = { stat -> addAfter { from, id -> from.copy(id = id, stat = stat, variant = 0, keys = null) } }
                )
                FlexBlockType.GRAPH -> GraphOptions(block, content) { f -> edit { it.copy(graph = f(it.graph)) } }
                FlexBlockType.CALENDAR -> CalendarOptions(
                    block = block,
                    spec = spec,
                    content = content,
                    edit = { f -> edit { it.copy(cal = f(it.cal)) } },
                    onAdd = { cal -> addAfter { from, id -> from.copy(id = id, cal = cal) } }
                )
                FlexBlockType.PHOTO -> PhotoPick(block) { c, f -> edit(c, f) }
                else -> Unit
            }

            // The look variants, the items and the detail switch come from the card (a Workout stats card's from its
            // stat): a Graph has one look (bars or line is each variable's own), so it shows no Style.
            val variants = block.variantLabels
            StyleChoice(variants, block.variant) { v -> edit { it.copy(variant = v) } }
            if (block.isListy) {
                // A hero look shows one item (the first picked), so it has no count.
                val hero = variants.getOrNull(block.variant) == "Hero"
                ItemOptions(content.choices(type), block, hideCount = hero) { c, f -> edit(c, f) }
            }
            block.flagText?.let { label -> SwitchRow(label, block.flag) { on -> edit { it.copy(flag = on) } } }
            if (type == FlexBlockType.HEADER || type == FlexBlockType.TEXT) {
                OutlinedTextField(
                    value = block.text,
                    onValueChange = { t -> edit(continuous = true) { it.copy(text = t.take(300)) } },
                    label = { Text(if (type == FlexBlockType.TEXT) "Text" else "Title (empty = default)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (type == FlexBlockType.PHOTO) {
                PhotoCrop(block, spec, content, assets) { c, f -> edit(c, f) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this card?") },
            text = { Text("It is taken off the poster. Undo brings it back.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onChange(false) { sp -> sp.withoutBlock(blockId) }
                    selectNow(null)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Spec edits
// ---------------------------------------------------------------------------------------------

/** The block with no box of its own in Free mode (so a copy is put below the others instead of on top of its source). */
private fun FlexBlock.unplaced(): FlexBlock = copy(freeX = 0f, freeY = 0f, freeW = 0f, freeH = 0f)

/**
 * The poster with [block] put right after the card [afterId]: next in the card list, and in the tiling a row of its
 * own right after the row that holds [afterId] (what the painter does by itself for a card the tiling does not hold
 * yet, see [TileTree.effective], so the poster shows the same either way, but kept in the layout so it stays there).
 * An automatic tiling (null layout) stays automatic: there the list order is the order on the poster.
 */
internal fun FlexSpec.withBlockAfter(afterId: Int, block: FlexBlock): FlexSpec {
    val at = blocks.indexOfFirst { it.id == afterId }
    val list = blocks.toMutableList().also { it.add(if (at < 0) it.size else at + 1, block) }
    val tree = layout
    val leaves = TileTree.leaves(tree)
    val placed = if (tree != null && afterId in leaves && block.id !in leaves) {
        val order = leaves.toMutableList().also { it.add(it.indexOf(afterId) + 1, block.id) }
        TileTree.effective(tree, order, style.aspect == FlexAspect.WIDE) ?: tree
    } else tree
    return copy(blocks = list, layout = placed)
}

/** The poster without the card [id], taken out of the tiling too. */
internal fun FlexSpec.withoutBlock(id: Int): FlexSpec {
    val rest = blocks.filter { it.id != id }
    return copy(blocks = rest, layout = layout?.let { TileTree.prune(it, rest.map { b -> b.id }.toSet()) })
}

// ---------------------------------------------------------------------------------------------
// Small pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun ConfigLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One line of help under a control (why something is off or empty). */
@Composable
private fun ConfigHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The card's look variant: a segmented row for up to four, a dropdown for more, nothing for one. */
@Composable
private fun StyleChoice(labels: List<String>, variant: Int, onPick: (Int) -> Unit) {
    if (labels.size < 2) return
    val current = variant.coerceIn(0, labels.lastIndex)
    if (labels.size <= 4) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ConfigLabel("Style")
            FlexSegmented(labels.indices.toList(), current, { labels[it] }, onSelect = onPick)
        }
    } else {
        DropdownField("Style", labels.indices.toList(), current, { labels[it] }, onPick)
    }
}

/** Which items a list card shows ("Auto" = the first few) and, for Auto, how many. */
@Composable
private fun ItemOptions(
    choices: List<Pair<String, String>>,
    block: FlexBlock,
    hideCount: Boolean = false,
    edit: (continuous: Boolean, (FlexBlock) -> FlexBlock) -> Unit
) {
    if (choices.isNotEmpty()) {
        MultiSelectDropdown("Show", choices, block.keys) { keys -> edit(false) { it.copy(keys = keys) } }
    }
    if (block.keys == null && !hideCount) {
        LabeledSlider(
            label = "Max items",
            valueText = block.limit.toString(),
            value = block.limit.toFloat(),
            range = 1f..20f,
            steps = 18
        ) { v -> edit(true) { it.copy(limit = v.roundToInt()) } }
    }
}

// ---------------------------------------------------------------------------------------------
// Workout stats
// ---------------------------------------------------------------------------------------------

/**
 * A Workout stats card: which stat (Top set / One rep max / Session stats) and "Add another" (a second card with a
 * stat no Workout stats card on the poster shows yet). The stat's own look, lifts and detail switch follow in the
 * common part of the panel (through [variantLabels], [isListy] and [flagText], which ask the stat).
 */
@Composable
private fun WorkoutStatsOptions(
    block: FlexBlock,
    spec: FlexSpec,
    content: FlexContent,
    edit: (continuous: Boolean, (FlexBlock) -> FlexBlock) -> Unit,
    onAddStat: (FlexWorkoutStat) -> Unit
) {
    val stat = block.stat
    val next = FlexWorkoutStat.entries.firstOrNull { s ->
        spec.blocks.none { it.type == FlexBlockType.WORKOUT_STATS && it.stat == s }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        Box(modifier = Modifier.weight(1f)) {
            DropdownField("Shows", FlexWorkoutStat.entries, stat, { it.label }) { s ->
                // Each stat has its own looks: keep the look's number when the new stat has it, else its last.
                edit(false) { it.copy(stat = s, variant = it.variant.coerceIn(0, s.variants.lastIndex.coerceAtLeast(0))) }
            }
        }
        OutlinedButton(onClick = { next?.let(onAddStat) }, enabled = next != null) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add another")
        }
    }
    if (next == null) ConfigHint("Top set, one rep max and session stats are all on the poster already.")
    val empty = when (stat) {
        FlexWorkoutStat.TOP_SET, FlexWorkoutStat.ONE_RM -> content.lifts.isEmpty()
        FlexWorkoutStat.SESSION -> content.stats.isEmpty()
    }
    if (empty) ConfigHint("There is no ${stat.label.lowercase()} to show here.")
}

// ---------------------------------------------------------------------------------------------
// Graph
// ---------------------------------------------------------------------------------------------

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Whether [series] has a point inside [range] (counted back from now). */
private fun hasPointsIn(series: FlexGraphSeries, range: FlexGraphRange): Boolean {
    val days = range.days ?: return series.points.isNotEmpty()
    val from = System.currentTimeMillis() - days * DAY_MS
    return series.points.any { it.timeMs >= from }
}

/** How a variable is listed in the pickers: its name, its unit, and "no data" when it has no points at all. */
private fun seriesName(s: FlexGraphSeries): String {
    val named = if (s.unit.isNotBlank()) "${s.label} (${s.unit})" else s.label
    return if (s.points.isEmpty()) "$named · no data" else named
}

/** "No Bodyweight data in the last 3 months", for a variable with nothing in the graph's range. */
private fun noDataText(series: FlexGraphSeries, range: FlexGraphRange): String =
    if (range.days == null) "No ${series.label} data yet."
    else "No ${series.label} data in the last ${range.label.lowercase().removePrefix("1 ")}."

/**
 * A Graph card: the variable (every graph the app has, under its group's heading; "Pick a variable" until one is
 * picked), what to compare it with (Nothing or any other variable) and the range. A variable with no points in the
 * range gets a line saying so.
 */
@Composable
private fun GraphOptions(block: FlexBlock, content: FlexContent, editGraph: ((FlexGraphConfig) -> FlexGraphConfig) -> Unit) {
    val graphs = content.graphs
    val cfg = block.graph
    val first = cfg.variables.getOrNull(0)
    val second = cfg.variables.getOrNull(1)
    val main = graphs.firstOrNull { it.id == first }
    val other = graphs.firstOrNull { it.id == second }

    if (graphs.isEmpty()) ConfigHint("There are no graphs to show yet.")
    FlexPickField(
        label = "Variable",
        valueText = main?.label ?: if (first != null) "No longer available" else "Pick a variable",
        options = graphs,
        optionLabel = { seriesName(it) },
        isSelected = { it.id == first },
        placeholder = main == null,
        group = { it.group }
    ) { picked ->
        editGraph { g -> g.copy(variables = listOfNotNull(picked.id, g.variables.getOrNull(1)?.takeIf { it != picked.id })) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            FlexPickField(
                label = "Compare with",
                valueText = other?.label ?: if (second != null) "No longer available" else "Nothing",
                options = listOf<FlexGraphSeries?>(null) + graphs.filter { it.id != first },
                optionLabel = { if (it == null) "Nothing" else seriesName(it) },
                isSelected = { it?.id == second },
                group = { it?.group ?: "" },
                enabled = first != null
            ) { picked ->
                editGraph { g ->
                    val main0 = g.variables.firstOrNull()
                    if (main0 == null) g else g.copy(variables = listOfNotNull(main0, picked?.id?.takeIf { it != main0 }))
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            DropdownField("Range", FlexGraphRange.entries, cfg.range, { it.label }) { r -> editGraph { it.copy(range = r) } }
        }
    }
    val missing = remember(main, other, cfg.range) {
        listOfNotNull(main, other).filter { !hasPointsIn(it, cfg.range) }.map { noDataText(it, cfg.range) }
    }
    missing.forEach { ConfigHint(it) }
}

// ---------------------------------------------------------------------------------------------
// Calendar
// ---------------------------------------------------------------------------------------------

private fun zoomName(zoom: CalendarZoom): String = when (zoom) {
    CalendarZoom.MONTH -> "Month"
    CalendarZoom.QUADRANT -> "Quadrant"
    CalendarZoom.YEAR -> "Year"
}

/**
 * A Calendar card: which days (the source; "Pick a source" while none is picked, as a new card starts), how much of
 * the year (month, quadrant or year), which one (year, and month or quadrant), where its weeks start, and "Add
 * another" which puts a second calendar card after this one for a different period of the same days.
 */
@Composable
private fun CalendarOptions(
    block: FlexBlock,
    spec: FlexSpec,
    content: FlexContent,
    edit: ((FlexCalendarSpec) -> FlexCalendarSpec) -> Unit,
    onAdd: (FlexCalendarSpec) -> Unit
) {
    val cal = block.cal
    val anchor = flexCalendarAnchor(content.calendarDay, content.calendarToday)
    val view = cal.view(anchor)
    val picked = content.calendars.firstOrNull { it.key == cal.source }
    // The days the card draws: the picked source, else (not picked yet) the first, as the painter does.
    val source = picked ?: content.calendars.sourceFor(cal.source)
    val (first, last) = flexCalendarRange(source, flexCalendarAnchor("", content.calendarToday))

    if (content.calendars.isEmpty()) ConfigHint("There are no days to show yet.")
    if (content.calendars.size > 1 || picked == null) {
        FlexPickField(
            label = "Days shown",
            valueText = picked?.label ?: "Pick a source",
            options = content.calendars,
            optionLabel = { it.label },
            isSelected = { it.key == cal.source },
            placeholder = picked == null
        ) { s -> edit { it.copy(source = s.key) } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ConfigLabel("Shows a")
        FlexSegmented(listOf(CalendarZoom.MONTH, CalendarZoom.QUADRANT, CalendarZoom.YEAR), view.zoom, { zoomName(it) }) { zoom ->
            // A card on the screen's own month keeps following it; a picked period moves to the one of the new mode that holds it.
            edit { c ->
                if (c.year <= 0) c.copy(zoom = zoom) else {
                    val moved = c.view(anchor).inZoom(zoom)
                    c.copy(zoom = zoom, year = moved.year, index = moved.index)
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            DropdownField("Year", flexCalendarYears(first, last, view.year), view.year, { it.toString() }) { y ->
                edit { it.copy(year = y, index = view.index) }
            }
        }
        when (view.zoom) {
            CalendarZoom.MONTH -> Box(modifier = Modifier.weight(1f)) {
                DropdownField("Month", (1..12).toList(), view.index, { MonthKey(view.year, it).name() }) { m ->
                    edit { it.copy(year = view.year, index = m) }
                }
            }
            CalendarZoom.QUADRANT -> Box(modifier = Modifier.weight(1f)) {
                DropdownField("Quadrant", (0..2).toList(), view.index, { quadrantTitles(view.year)[it] }) { q ->
                    edit { it.copy(year = view.year, index = q) }
                }
            }
            CalendarZoom.YEAR -> Unit
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Week starts on", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        FlexSegmented(
            options = listOf(Calendar.SUNDAY, Calendar.MONDAY),
            selected = cal.weekStart,
            label = { if (it == Calendar.SUNDAY) "Sunday" else "Monday" },
            modifier = Modifier.weight(1.3f)
        ) { day -> edit { it.copy(weekStart = day) } }
    }

    // Periods of this mode that no calendar card of the same days shows yet.
    val taken = spec.blocks
        .filter { it.type == FlexBlockType.CALENDAR && content.calendars.sourceFor(it.cal.source)?.key == source?.key }
        .map { it.cal.view(anchor) }
        .toSet()
    val choices = flexCalendarChoices(view.zoom, first, last, taken)
    var menu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { menu = true }, enabled = choices.isNotEmpty()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add another ${zoomName(view.zoom).lowercase()}")
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            choices.forEach { period ->
                DropdownMenuItem(
                    text = { Text(period.title()) },
                    onClick = {
                        menu = false
                        onAdd(cal.copy(year = period.year, index = period.index))
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Photo
// ---------------------------------------------------------------------------------------------

/** A Photo card's picture: choose one (gallery or camera, through the app's picker), or go back to the screen's own. */
@Composable
private fun PhotoPick(block: FlexBlock, edit: (continuous: Boolean, (FlexBlock) -> FlexBlock) -> Unit) {
    val picker = rememberImagePicker(aspect = null, outputWidth = 1600, outputHeight = 1600) { uri ->
        val uriText = uri.toString()
        edit(false) { it.copy(keys = listOf(uriText), text = if (it.text == FLEX_SCREEN_PHOTO) "" else it.text) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { picker.launch() }) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Choose photo")
        }
        if (block.keys != null) {
            TextButton(onClick = { edit(false) { it.copy(keys = null) } }) { Text("Use default") }
        }
    }
}

/**
 * A Photo card's crop: its shape (fixed shapes keep to at most half the poster, so only those that suit that half are
 * offered; "Fill its tile" and "Free crop" are always there), then zoom and move for a fixed shape or the frame editor
 * for a free crop.
 */
@Composable
private fun PhotoCrop(
    block: FlexBlock,
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    edit: (continuous: Boolean, (FlexBlock) -> FlexBlock) -> Unit
) {
    val limit = spec.style.photoLimit()
    val options = FlexPhotoRatio.entries.filter { it.allowedOn(limit) }
    val current = options.firstOrNull { abs(it.ratio - block.photoRatio) < 0.01f } ?: FlexPhotoRatio.AUTO
    val real = options.filter { it.ratio > 0f }
    val lowest = real.minByOrNull { it.ratio }?.label
    val highest = real.maxByOrNull { it.ratio }?.label
    DropdownField(
        label = "Crop shape" + if (lowest != null && highest != null) " (fixed: $lowest to $highest)" else "",
        options = options,
        selected = current,
        optionLabel = { it.label }
    ) { r -> edit(false) { it.copy(photoRatio = r.ratio) } }
    if (current == FlexPhotoRatio.FREE) {
        // Free crop: the frame is dragged on the photo itself, so zoom and move have no part in it.
        val photo = (block.keys?.firstOrNull() ?: content.photoUri)?.let { assets.images[it] }
        FlexCropEditor(block = block, photo = photo) { crop, continuous ->
            edit(continuous) { it.copy(cropL = crop.l, cropT = crop.t, cropR = crop.r, cropB = crop.b) }
        }
    } else {
        LabeledSlider("Zoom", "${(block.photoZoom * 10).roundToInt() / 10f}×", block.photoZoom, 1f..3f) { v ->
            edit(true) { it.copy(photoZoom = v) }
        }
        LabeledSlider("Move left / right", "${(block.photoX * 100).roundToInt()}%", block.photoX, 0f..1f) { v ->
            edit(true) { it.copy(photoX = v) }
        }
        LabeledSlider("Move up / down", "${(block.photoY * 100).roundToInt()}%", block.photoY, 0f..1f) { v ->
            edit(true) { it.copy(photoY = v) }
        }
    }
}
