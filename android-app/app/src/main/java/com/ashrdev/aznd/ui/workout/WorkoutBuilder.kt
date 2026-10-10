package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.domain.BuilderState
import com.ashrdev.aznd.domain.ExerciseType
import com.ashrdev.aznd.domain.SetCard
import com.ashrdev.aznd.domain.SetKind
import com.ashrdev.aznd.domain.SetRow
import com.ashrdev.aznd.domain.formatTarget
import com.ashrdev.aznd.domain.setLabels

/** Everything the builder UI can ask for; BuilderViewModel.actions implements it. All are instant, inline edits. */
class BuilderActions(
    val onPickCardExercise: (rowId: Long, exerciseId: Long) -> Unit,
    val onPickRowExercise: (rowId: Long, exerciseId: Long) -> Unit,
    val onDuplicate: (rowId: Long) -> Unit,
    val onDelete: (rowId: Long) -> Unit,
    val onDrop: (rowId: Long) -> Unit,
    val onSuperset: (rowId: Long) -> Unit,
    val onTargetText: (rowId: Long, text: String) -> Unit,
    val onAddCard: () -> Unit
)

private val Indent = 20.dp
private val SmallIcon = 40.dp

/**
 * Emits the exercise cards plus the "Add exercise" button into the caller's LazyColumn.
 * Rows appear/disappear in place: nothing here opens a dialog or sheet, requests focus or scrolls.
 * [showTargets] = the "Target reps" setting (OFF hides all target fields; stored targets are kept).
 */
fun LazyListScope.builderItems(
    state: BuilderState,
    exercises: List<Exercise>,
    showTargets: Boolean,
    actions: BuilderActions
) {
    val byId = exercises.associateBy { it.id }
    val depth = state.depthById
    items(state.cards, key = { it.rows.first().id }) { card ->
        ExerciseCard(card, byId, exercises, depth, showTargets, actions)
    }
    item(key = "add-exercise") {
        OutlinedButton(onClick = actions.onAddCard, modifier = Modifier.fillMaxWidth()) {
            Text("Add exercise")
        }
    }
}

@Composable
private fun ExerciseCard(
    card: SetCard,
    byId: Map<Long, Exercise>,
    all: List<Exercise>,
    depth: Map<Long, Int>,
    showTargets: Boolean,
    actions: BuilderActions
) {
    val first = card.rows.first()
    val exercise = card.exerciseId?.let { byId[it] }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (exercise != null) MuscleBadge(exercise.primaryMuscle, exercise.photoPath)
                ExercisePicker(
                    selected = exercise,
                    exercises = all,
                    onPick = { actions.onPickCardExercise(first.id, it.id) },
                    modifier = Modifier.weight(1f)
                )
                if (card.exerciseId == null) {
                    // An empty card has no rows to edit yet; this removes it.
                    IconButton(onClick = { actions.onDelete(first.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove card", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (card.exerciseId != null) {
                val labels = setLabels(card)
                card.rows.forEach { row ->
                    key(row.id) {
                        SetRowLine(
                            row = row,
                            depth = depth[row.id] ?: 0,
                            label = labels[row.id].orEmpty(),
                            exercise = row.exerciseId?.let { byId[it] },
                            all = all,
                            showTargets = showTargets,
                            actions = actions
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetRowLine(
    row: SetRow,
    depth: Int,
    label: String,
    exercise: Exercise?,
    all: List<Exercise>,
    showTargets: Boolean,
    actions: BuilderActions
) {
    val connector = MaterialTheme.colorScheme.outline
    val nested = if (depth > 0) {
        Modifier.drawBehind {
            // vertical connector line in the left gutter + a short tick to this row
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
            // A superset part has its own exercise dropdown (closed until tapped).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (exercise != null) MuscleBadge(exercise.primaryMuscle, exercise.photoPath, size = 28.dp)
                ExercisePicker(
                    selected = exercise,
                    exercises = all,
                    onPick = { actions.onPickRowExercise(row.id, it.id) },
                    modifier = Modifier.weight(1f),
                    label = "Superset exercise"
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(76.dp)
            )
            if (showTargets && row.kind != SetKind.DROP) {
                TargetField(
                    row = row,
                    seconds = exercise?.type == ExerciseType.TIME_HELD,
                    onText = { actions.onTargetText(row.id, it) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                Box(modifier = Modifier.weight(1f))
            }
            IconButton(onClick = { actions.onDuplicate(row.id) }, modifier = Modifier.size(SmallIcon)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate")
            }
            IconButton(onClick = { actions.onDelete(row.id) }, modifier = Modifier.size(SmallIcon)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
            RowMenu(onSuperset = { actions.onSuperset(row.id) }, onDrop = { actions.onDrop(row.id) })
        }
    }
}

@Composable
private fun TargetField(row: SetRow, seconds: Boolean, onText: (String) -> Unit, modifier: Modifier) {
    var text by remember(row.id) { mutableStateOf(formatTarget(row.targetMin, row.targetMax)) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onText(it)
        },
        label = { Text(if (seconds) "Target s" else "Target reps") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = modifier.padding(end = 4.dp)
    )
}

@Composable
private fun RowMenu(onSuperset: () -> Unit, onDrop: () -> Unit) {
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
