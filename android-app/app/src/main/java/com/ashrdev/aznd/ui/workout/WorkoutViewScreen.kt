package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.SetMode
import com.ashrdev.aznd.data.workout.WorkoutWithExercises
import com.ashrdev.aznd.ui.common.PrimaryAction
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.ThirdAction
import com.ashrdev.aznd.ui.components.Card

@Composable
fun WorkoutViewScreen(
    workoutId: Long,
    viewModel: WorkoutViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenSession: () -> Unit,
    onViewExercise: (Long, Long) -> Unit,
    onOpenStats: (Long) -> Unit
) {
    val active by viewModel.activeSession.collectAsState()
    var workout by remember { mutableStateOf<WorkoutWithExercises?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var blockedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId) {
        workout = viewModel.getWorkout(workoutId)
        loaded = true
    }

    if (!loaded) return
    val item = workout
    if (item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    if (blockedDialog) {
        AlertDialog(
            onDismissRequest = { blockedDialog = false },
            title = { Text("Session already running") },
            text = {
                Text("You have an unfinished ${active?.session?.WorkoutName.orEmpty()} session. Finish or discard it before starting another.")
            },
            confirmButton = {
                TextButton(onClick = {
                    blockedDialog = false
                    onOpenSession()
                }) { Text("Go to session") }
            },
            dismissButton = {
                TextButton(onClick = { blockedDialog = false }) { Text("Cancel") }
            }
        )
    }

    ScreenScaffold(
        title = item.Workout.name,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        third = ThirdAction(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            label = "Stats",
            onClick = { onOpenStats(item.Workout.id) }
        ),
        primaryAction = PrimaryAction(
            icon = Icons.Default.PlayArrow,
            label = "Start",
            enabled = item.exercises.isNotEmpty(),
            onClick = {
                if (active != null) blockedDialog = true
                else viewModel.startSession(item) { onOpenSession() }
            }
        ),
        topBarActions = {
            IconButton(onClick = { onEdit(item.Workout.id) }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit workout")
            }
        }
    ) { padding ->
        if (item.exercises.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    "No exercises yet — tap Edit to add some.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(item.exercises, key = { it.exercise.id }) { ews ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewExercise(item.Workout.id, ews.exercise.id) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(ews.exercise.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                ews.sets.joinToString(" · ") {
                                    if (it.mode == SetMode.REPS) "Reps" else "Time"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
