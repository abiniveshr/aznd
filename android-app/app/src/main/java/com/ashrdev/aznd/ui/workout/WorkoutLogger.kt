package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.data.workout.toMeta
import com.ashrdev.aznd.domain.ExerciseMeta
import com.ashrdev.aznd.domain.LogField
import com.ashrdev.aznd.domain.SetCard
import com.ashrdev.aznd.domain.SetKind
import com.ashrdev.aznd.domain.SetRow
import com.ashrdev.aznd.domain.Suggestion
import com.ashrdev.aznd.domain.canonicalFieldText
import com.ashrdev.aznd.domain.fieldLabelWithTarget
import com.ashrdev.aznd.domain.fieldLayout
import com.ashrdev.aznd.domain.fieldText
import com.ashrdev.aznd.domain.formatKcal
import com.ashrdev.aznd.domain.formatPrevious
import com.ashrdev.aznd.domain.label
import com.ashrdev.aznd.domain.previousRowFor
import com.ashrdev.aznd.domain.rowsKcal
import com.ashrdev.aznd.domain.setLabels
import com.ashrdev.aznd.domain.suggestionField
import com.ashrdev.aznd.domain.suggestionText
import com.ashrdev.aznd.domain.targetLabel

private val Indent = 20.dp
private val SmallIcon = 40.dp

/** Everything the card list needs; bundled so the stateless composables stay readable. */
class LoggerData(
    val rows: List<SetRow>,
    val cards: List<SetCard>,
    val depth: Map<Long, Int>,
    val exercises: List<Exercise>,
    val previous: List<SetRow>,
    val suggestions: Map<Long, Suggestion>,
    val bodyweightKg: Double,
    val showRpe: Boolean,
    val showTargets: Boolean
) {
    val byId: Map<Long, Exercise> = exercises.associateBy { it.id }
    val meta: (Long) -> ExerciseMeta? = { id -> byId[id]?.toMeta() }
    val totalKcal: Double get() = rowsKcal(rows, meta, bodyweightKg)
}

/**
 * Emits the estimated total under the session title, one card per exercise, and "Add exercise"
 * into the caller's LazyColumn. Rows are inline: no dialogs, no focus moves, no scrolling.
 */
fun LazyListScope.loggerItems(data: LoggerData, actions: LoggerActions) {
    item(key = "total") {
        Text(
            text = "Estimated total: ${formatKcal(data.totalKcal)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    items(data.cards, key = { it.rows.first().id }) { card -> LoggerCard(card, data, actions) }
    item(key = "add-exercise") {
        OutlinedButton(onClick = actions.onAddCard, modifier = Modifier.fillMaxWidth()) { Text("Add exercise") }
    }
}

@Composable
private fun LoggerCard(card: SetCard, data: LoggerData, actions: LoggerActions) {
    val first = card.rows.first()
    val exercise = card.exerciseId?.let { data.byId[it] }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (exercise != null) MuscleBadge(exercise.primaryMuscle, exercise.photoPath)
                Column(modifier = Modifier.weight(1f)) {
                    ExercisePicker(
                        selected = exercise,
                        exercises = data.exercises,
                        onPick = { actions.onPickCardExercise(first.id, it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (exercise != null) {
                        Text(
                            text = "≈ ${formatKcal(rowsKcal(card.rows, data.meta, data.bodyweightKg))} (estimated)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (card.exerciseId == null) {
                    IconButton(onClick = { actions.onDelete(first.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove card", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (card.exerciseId != null) {
                val labels = setLabels(card)
                card.rows.forEach { row ->
                    key(row.id) {
                        LoggerRow(row, data.depth[row.id] ?: 0, labels[row.id].orEmpty(), data, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoggerRow(row: SetRow, depth: Int, label: String, data: LoggerData, actions: LoggerActions) {
    val exercise = row.exerciseId?.let { data.byId[it] }
    val connector = MaterialTheme.colorScheme.outline
    val nested = if (depth > 0) {
        Modifier.drawBehind {
            val x = -(Indent / 2).toPx()
            drawLine(connector, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            drawLine(connector, Offset(x, size.height / 2), Offset(0f, size.height / 2), strokeWidth = 2.dp.toPx())
        }
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Indent * depth)
            .then(nested),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (row.kind == SetKind.SUPERSET) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (exercise != null) MuscleBadge(exercise.primaryMuscle, exercise.photoPath, size = 28.dp)
                ExercisePicker(
                    selected = exercise,
                    exercises = data.exercises,
                    onPick = { actions.onPickRowExercise(row.id, it) },
                    modifier = Modifier.weight(1f),
                    label = "Superset exercise"
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { actions.onDuplicate(row.id) }, modifier = Modifier.size(SmallIcon)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate")
            }
            IconButton(onClick = { actions.onDelete(row.id) }, modifier = Modifier.size(SmallIcon)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
            LoggerMenu(onSuperset = { actions.onSuperset(row.id) }, onDrop = { actions.onDrop(row.id) })
        }
        if (exercise != null) {
            LoggerFields(row, exercise, data, actions)
            val prev = previousRowFor(row, data.rows, data.previous)
                ?.let { formatPrevious(exercise.type, it, data.showRpe) }
            if (prev != null) {
                Text(
                    text = "Previous: $prev",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LoggerFields(row: SetRow, exercise: Exercise, data: LoggerData, actions: LoggerActions) {
    val layout = fieldLayout(exercise.type, data.showRpe)
    val target = targetLabel(row, exercise.type, data.showTargets)
    val suggestion = data.suggestions[row.id]
    val fillsField = suggestionField(exercise.type)
    // Hide the hint once the field already holds that value.
    val hint = suggestion?.takeIf { fillsField != null && fieldText(row, fillsField) != fieldText(applySuggestion(row, exercise, it), fillsField) }

    @Composable
    fun hintFor(field: LogField) {
        if (hint != null && fillsField == field) {
            Text(
                text = suggestionText(exercise.type, hint.fieldKg),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { actions.onSuggest(row.id, hint) }
                    .padding(vertical = 4.dp)
            )
        }
    }

    hintFor(layout.big)
    LogInput(row, layout.big, fieldLabelWithTarget(layout.big, target), big = true, actions = actions, modifier = Modifier.fillMaxWidth())
    if (layout.second.isNotEmpty()) {
        Column {
            layout.second.forEach { hintFor(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                layout.second.forEach { f ->
                    LogInput(row, f, fieldLabelWithTarget(f, target), big = false, actions = actions, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** What the row would hold after tapping the hint (only used to decide whether to still show it). */
private fun applySuggestion(row: SetRow, exercise: Exercise, s: Suggestion): SetRow =
    com.ashrdev.aznd.domain.applySuggestionTo(row, exercise.type, s.fieldKg)

/**
 * One input box. The user's text is kept locally while typing; only valid text is stored (invalid
 * keeps the old value, blank clears it). The text is replaced from the stored value only when that
 * value CHANGED from outside (a tapped suggestion) or when focus leaves the box.
 */
@Composable
private fun LogInput(
    row: SetRow,
    field: LogField,
    label: String,
    big: Boolean,
    actions: LoggerActions,
    modifier: Modifier
) {
    val stored = fieldText(row, field)
    var text by remember(row.id, field) { mutableStateOf(stored) }
    val latestStored by rememberUpdatedState(stored)
    LaunchedEffect(stored) {
        if (canonicalFieldText(field, text) != stored) text = stored
    }
    val whole = field == LogField.REPS || field == LogField.SECONDS
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            actions.onField(row.id, field, it)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (whole) KeyboardType.Number else KeyboardType.Decimal),
        textStyle = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
        modifier = modifier.onFocusChanged { if (!it.isFocused) text = latestStored }
    )
}

@Composable
private fun LoggerMenu(onSuperset: () -> Unit, onDrop: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(SmallIcon)) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Superset") }, onClick = { open = false; onSuperset() })
            DropdownMenuItem(text = { Text("Drop set") }, onClick = { open = false; onDrop() })
        }
    }
}
