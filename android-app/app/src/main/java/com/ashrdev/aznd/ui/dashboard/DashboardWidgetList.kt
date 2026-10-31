package com.ashrdev.aznd.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.WorkoutWithExercises
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.ui.common.DropdownField
import com.ashrdev.aznd.ui.common.ReorderableItem
import com.ashrdev.aznd.ui.common.dragReorderHandle
import com.ashrdev.aznd.ui.common.dragReorderOnLongPress
import com.ashrdev.aznd.ui.common.rememberReorderState
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.streaks.StreakWithCount

/**
 * One dashboard page's widgets: their options (size, what they show), removing them, and their
 * reading order (hold a row or drag its handle). Edit mode's Tuning page.
 */
@Composable
internal fun WidgetTuningList(
    preset: DashPreset,
    streaks: List<StreakWithCount>,
    workouts: List<WorkoutWithExercises>,
    modes: List<GraphMode>,
    expandedId: Int,
    onExpand: (Int) -> Unit,
    onChange: (DashWidget) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    bottom: Dp
) {
    val widgets = preset.widgets
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, firstItemIndex = 1, itemCount = widgets.size, onMove = onMove)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "intro") {
            Text(
                "The widgets on ${preset.name}. Tiles size themselves to fill the page; this order is the reading order. " +
                    "Hold a widget or drag its handle to reorder. Up to ${DashDefaults.MAX_WIDGETS} widgets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(widgets, key = { it.id }) { w ->
            ReorderableItem(reorder, w.id) {
                WidgetRow(
                    widget = w,
                    summary = summaryOf(w, streaks, workouts, modes),
                    expanded = expandedId == w.id,
                    onToggle = { onExpand(if (expandedId == w.id) -1 else w.id) },
                    dragModifier = Modifier.dragReorderOnLongPress(reorder, w.id),
                    handleModifier = Modifier.dragReorderHandle(reorder, w.id)
                ) {
                    WidgetOptions(
                        widget = w,
                        streaks = streaks,
                        workouts = workouts,
                        modes = modes,
                        onChange = onChange,
                        onRemove = {
                            onExpand(-1)
                            onRemove(w.id)
                        }
                    )
                }
            }
        }
        if (widgets.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No widgets on this page yet. Add one below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal fun summaryOf(w: DashWidget, streaks: List<StreakWithCount>, workouts: List<WorkoutWithExercises>, modes: List<GraphMode>): String {
    val size = "Size ${w.size.label}"
    val what = when (w.type) {
        DashWidgetType.CALORIES -> w.calorieView.label
        DashWidgetType.STREAK -> if (w.streakId >= 0) streaks.firstOrNull { it.streak.id == w.streakId }?.streak?.name ?: "Removed streak" else "Top #${w.rank}"
        DashWidgetType.LATEST_WORKOUT -> "Most recent session"
        DashWidgetType.GRAPH -> w.variables.mapNotNull { id -> modes.firstOrNull { it.id == id }?.label }.joinToString(" vs ").ifEmpty { "No variable" }
        DashWidgetType.BODY_MODEL -> w.model.label
        DashWidgetType.SHORTCUT -> if (w.shortcut == DashShortcut.ROUTINE) workouts.firstOrNull { it.workout.id == w.workoutId }?.workout?.name ?: "Routine" else w.shortcut.label
        DashWidgetType.TOP_LIFT -> "Top #${w.rank}"
    }
    return "$what · $size"
}

@Composable
internal fun WidgetRow(
    widget: DashWidget,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    dragModifier: Modifier,
    handleModifier: Modifier,
    options: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), highlight = expanded) {
        Row(
            modifier = dragModifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = "Drag to reorder",
                modifier = handleModifier.size(40.dp).padding(8.dp)
            )
            Icon(DashboardTargets.icon(widget.type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggle)
                    .padding(vertical = 8.dp)
            ) {
                Text(widget.type.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggle) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "Close options" else "Open options")
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) { options() }
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) FilterChip(selected = o == selected, onClick = { onPick(o) }, label = { Text(label(o)) })
    }
}

private class StreakChoice(val label: String, val rank: Int, val streakId: Long)

@Composable
internal fun WidgetOptions(
    widget: DashWidget,
    streaks: List<StreakWithCount>,
    workouts: List<WorkoutWithExercises>,
    modes: List<GraphMode>,
    onChange: (DashWidget) -> Unit,
    onRemove: () -> Unit
) {
    Text("Size", style = MaterialTheme.typography.labelLarge)
    ChipRow(DashSize.entries, widget.size, { it.label }) { onChange(widget.copy(size = it)) }
    when (widget.type) {
        DashWidgetType.CALORIES -> {
            Text("Diagram", style = MaterialTheme.typography.labelLarge)
            ChipRow(DashCalorieView.entries, widget.calorieView, { it.label }) { onChange(widget.copy(calorieView = it)) }
        }
        DashWidgetType.STREAK -> {
            val choices = (1..DashDefaults.MAX_RANK).map { StreakChoice("Top #$it running", it, -1L) } +
                streaks.sortedBy { it.streak.name.lowercase() }.map { StreakChoice(it.streak.name, 1, it.streak.id) }
            val current = if (widget.streakId >= 0) streaks.firstOrNull { it.streak.id == widget.streakId }?.streak?.name ?: "Removed streak"
            else "Top #${widget.rank} running"
            DropdownField(
                label = "Shows",
                valueText = current,
                options = choices,
                optionText = { it.label },
                onSelect = { onChange(widget.copy(rank = it.rank, streakId = it.streakId)) }
            )
        }
        DashWidgetType.TOP_LIFT -> {
            DropdownField(
                label = "Shows",
                valueText = "Top #${widget.rank}",
                options = (1..DashDefaults.MAX_RANK).toList(),
                optionText = { "Top #$it" },
                onSelect = { onChange(widget.copy(rank = it)) }
            )
        }
        DashWidgetType.GRAPH -> {
            val first = widget.variables.firstOrNull()
            val second = widget.variables.getOrNull(1)
            DropdownField(
                label = "Variable",
                valueText = modes.firstOrNull { it.id == first }?.label ?: "Pick one",
                options = modes,
                optionText = { it.label },
                onSelect = { m -> onChange(widget.copy(variables = listOfNotNull(m.id, second?.takeIf { it != m.id }))) }
            )
            DropdownField(
                label = "Compare with",
                valueText = modes.firstOrNull { it.id == second }?.label ?: "Nothing",
                options = listOf<GraphMode?>(null) + modes.filter { it.id != first },
                optionText = { it?.label ?: "Nothing" },
                onSelect = { m -> onChange(widget.copy(variables = listOfNotNull(first ?: modes.firstOrNull()?.id, m?.id).distinct())) }
            )
            Text(
                "With two variables the tile shows how closely they move together (r), and opens the comparison.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DashWidgetType.BODY_MODEL -> {
            Text("Shows", style = MaterialTheme.typography.labelLarge)
            ChipRow(DashModel.entries, widget.model, { it.label }) { onChange(widget.copy(model = it)) }
        }
        DashWidgetType.SHORTCUT -> {
            DropdownField(
                label = "Opens",
                valueText = widget.shortcut.label,
                options = DashShortcut.entries,
                optionText = { it.label },
                onSelect = { onChange(widget.copy(shortcut = it)) }
            )
            if (widget.shortcut == DashShortcut.ROUTINE) {
                DropdownField(
                    label = "Routine",
                    valueText = workouts.firstOrNull { it.workout.id == widget.workoutId }?.workout?.name ?: "Pick one",
                    options = workouts,
                    optionText = { it.workout.name },
                    onSelect = { onChange(widget.copy(workoutId = it.workout.id)) }
                )
            }
        }
        DashWidgetType.LATEST_WORKOUT -> Unit
    }
    TextButton(onClick = onRemove) {
        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(6.dp))
        Text("Remove widget", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun AddWidgetDialog(onPick: (DashWidgetType) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add widget") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (type in DashWidgetType.entries) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(type) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(DashboardTargets.icon(type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(type.label, style = MaterialTheme.typography.titleSmall)
                            Text(type.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
