package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

private data class EditableExercise(
    val name: String,
    val sets: List<SetMode>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    routineId: Long?,
    viewModel: WorkoutViewModel,
    onDone: () -> Unit,
    onDeleted: () -> Unit
) {
        var name by remember { mutableStateOf("") }
    var exercises by remember {
        mutableStateOf(
            if (routineId == null) listOf(EditableExercise("", listOf(SetMode.REPS)))
            else listOf()
        )
    }
    var loaded by remember { mutableStateOf(routineId == null) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(routineId) {
        if (routineId != null) {
            viewModel.getRoutine(routineId)?.let { existing ->
                name = existing.routine.name
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
            title = { Text("Delete routine?") },
            text = { Text("\"$name\" and its exercises will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.deleteRoutine(routineId!!)
                    onDeleted()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (routineId == null) "New Routine" else "Edit Routine") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    if (routineId != null) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete routine", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = {
                        val payload = exercises.mapIndexed { exIndex, ex ->
                            ExerciseWithSets(
                                exercise = ExerciseEntity(
                                    routineId = routineId ?: 0,
                                    name = ex.name,
                                    orderIndex = exIndex
                                ),
                                sets = ex.sets.mapIndexed { i, mode ->
                                    ExerciseSetEntity(exerciseId = 0, mode = mode, orderIndex = i)
                                }
                            )
                        }
                        viewModel.saveRoutine(routineId ?: 0, name, payload)
                        onDone()
                    }) {
                        Text("Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine name") },
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
                    Icon(Icons.Default.Delete, contentDescription = "Delete routine", tint = MaterialTheme.colorScheme.error)
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