package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.WorkoutWithExercises
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.ThirdAction
import com.ashrdev.aznd.ui.components.Card

@Composable
fun WorkoutLogScreen(
    viewModel: WorkoutViewModel,
    onOpenWorkout: (Long) -> Unit,
    onEditWorkout: (Long) -> Unit,
    onAddWorkout: () -> Unit,
    onOpenSession: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val workouts by viewModel.Workouts.collectAsState()
    val active by viewModel.activeSession.collectAsState()
    var workoutToDelete by remember { mutableStateOf<WorkoutWithExercises?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    workoutToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { workoutToDelete = null },
            title = { Text("Delete workout?") },
            text = { Text("\"${target.Workout.name}\" and its exercises will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWorkout(target.Workout.id)
                    workoutToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { workoutToDelete = null }) { Text("Cancel") }
            }
        )
    }

    ScreenScaffold(
        title = "Workouts",
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        third = ThirdAction(icon = Icons.Default.Add, label = "Add workout", onClick = onAddWorkout),
        topBarActions = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text("Delete data") }, enabled = false, onClick = {})
                    DropdownMenuItem(text = { Text("Export data") }, enabled = false, onClick = {})
                }
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
            active?.let { running ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenSession)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Session in progress", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${running.session.WorkoutName} · started ${formatClock(running.session.startedAt)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            if (workouts.isEmpty()) {
                item {
                    Text(
                        "No workouts yet — tap Add workout to create one.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(workouts, key = { it.Workout.id }) { item ->
                    WorkoutCard(
                        item = item,
                        onOpen = { onOpenWorkout(item.Workout.id) },
                        onEdit = { onEditWorkout(item.Workout.id) },
                        onRequestDelete = { workoutToDelete = item }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutCard(
    item: WorkoutWithExercises,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(item.Workout.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${item.exercises.size} exercises · ${item.exercises.sumOf { it.sets.size }} sets",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Workout options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRequestDelete()
                        }
                    )
                }
            }
        }
    }
}
