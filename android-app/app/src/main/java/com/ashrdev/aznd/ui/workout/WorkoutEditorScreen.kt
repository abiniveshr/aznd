package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.ExerciseEntity
import com.ashrdev.aznd.data.workout.ExerciseSetEntity
import com.ashrdev.aznd.data.workout.ExerciseWithSets
import com.ashrdev.aznd.data.workout.SetMode
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField

private data class EditableExercise(
    val name: String,
    val sets: List<SetMode>
)

// Back in the bottom bar cancels (same as the old X). Save and Delete stay in the top bar so
// they're still reachable while the keyboard is open and the bottom bar is tucked away.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditorScreen(
    workoutId: Long?,
    viewModel: WorkoutViewModel,
    onDone: () -> Unit,
    onDeleted: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var exercises by remember {
        mutableStateOf(
            if (workoutId == null) listOf(EditableExercise("", listOf(SetMode.REPS)))
            else listOf()
        )
    }
    var loaded by remember { mutableStateOf(workoutId == null) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId) {
        if (workoutId != null) {
            viewModel.getWorkout(workoutId)?.let { existing ->
                name = existing.Workout.name
                exercises = existing.exercises
                    .sortedBy { it.exercise.orderIndex }
                    .map { ews ->
                        EditableExercise(
                            name = ews.exercise.name,
                            sets = ews.sets.sortedBy { it.orderIndex }.map { it.mode }
                        )
                    }
            }
            loaded = true
        }
    }

    if (!loaded) return

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete workout?") },
            text = { Text("\"$name\" and its exercises will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.deleteWorkout(workoutId!!)
                    onDeleted()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            }
        )
    }

    ScreenScaffold(
        title = if (workoutId == null) "New Workout" else "Edit Workout",
        onBack = onDone,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        topBarActions = {
            if (workoutId != null) {
                IconButton(onClick = { showDelete = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete workout",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            TextButton(onClick = {
                val payload = exercises.mapIndexed { exIndex, ex ->
                    ExerciseWithSets(
                        exercise = ExerciseEntity(
                            WorkoutId = workoutId ?: 0,
                            name = ex.name,
                            orderIndex = exIndex
                        ),
                        sets = ex.sets.mapIndexed { i, mode ->
                            ExerciseSetEntity(exerciseId = 0, mode = mode, orderIndex = i)
                        }
                    )
                }
                viewModel.saveWorkout(workoutId ?: 0, name, payload)
                onDone()
            }) {
                Text("Save")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workout name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            itemsIndexed(exercises) { index, exercise ->
                ExerciseCard(
                    exercise = exercise,
                    onChange = { updated ->
                        exercises = exercises.toMutableList().also { it[index] = updated }
                    },
                    onDelete = {
                        exercises = exercises.toMutableList().also { it.removeAt(index) }
                    }
                )
            }
            item {
                OutlinedButton(
                    onClick = { exercises = exercises + EditableExercise("", emptyList()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add exercise")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseCard(
    exercise: EditableExercise,
    onChange: (EditableExercise) -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = exercise.name,
                    onValueChange = { onChange(exercise.copy(name = it)) },
                    label = { Text("Exercise") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete exercise",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            exercise.sets.forEachIndexed { setIndex, mode ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Set ${setIndex + 1}", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = mode == SetMode.REPS,
                            onClick = {
                                onChange(
                                    exercise.copy(
                                        sets = exercise.sets.toMutableList()
                                            .also { it[setIndex] = SetMode.REPS }
                                    )
                                )
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("Reps") }
                        SegmentedButton(
                            selected = mode == SetMode.TIME,
                            onClick = {
                                onChange(
                                    exercise.copy(
                                        sets = exercise.sets.toMutableList()
                                            .also { it[setIndex] = SetMode.TIME }
                                    )
                                )
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("Time") }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        onChange(
                            exercise.copy(
                                sets = exercise.sets.toMutableList().also { it.removeAt(setIndex) }
                            )
                        )
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete set")
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    onChange(exercise.copy(sets = exercise.sets + SetMode.REPS))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add set")
            }
        }
    }
}
