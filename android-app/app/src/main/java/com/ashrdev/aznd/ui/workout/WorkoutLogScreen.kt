package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.RoutineWithExercises

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLogScreen(
    viewModel: WorkoutViewModel,
    onEditRoutine: (Long) -> Unit,
    onAddRoutine: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSession: () -> Unit,
    onBack: () -> Unit
) {
    val routines by viewModel.routines.collectAsState()
    val active by viewModel.activeSession.collectAsState()
    var editMode by remember { mutableStateOf(false) }
    var blockedDialog by remember { mutableStateOf(false) }
    var routineToDelete by remember { mutableStateOf<RoutineWithExercises?>(null) }

    if (blockedDialog) {
        AlertDialog(
            onDismissRequest = { blockedDialog = false },
            title = { Text("Session already running") },
            text = {
                Text("You have an unfinished ${active?.session?.routineName.orEmpty()} session. Finish or discard it before starting another.")
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

    routineToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { routineToDelete = null },
            title = { Text("Delete routine?") },
            text = { Text("\"${target.routine.name}\" and its exercises will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRoutine(target.routine.id)
                    routineToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { routineToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editMode) "Edit Routines" else "Workouts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = { editMode = !editMode }) {
                        Icon(
                            imageVector = if (editMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = "Toggle edit mode"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (editMode) {
                FloatingActionButton(onClick = onAddRoutine) {
                    Icon(Icons.Default.Add, contentDescription = "Add routine")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
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
                            Text(
                                "Session in progress",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "${running.session.routineName} · started ${formatClock(running.session.startedAt)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            items(routines, key = { it.routine.id }) { item ->
                RoutineCard(
                    item = item,
                    editMode = editMode,
                    onClick = {
                        when {
                            editMode -> onEditRoutine(item.routine.id)
                            active != null -> blockedDialog = true
                            else -> {
                                viewModel.startSession(item)
                                onOpenSession()
                            }
                        }
                    },
                    onDelete = { routineToDelete = item }
                )
            }
        }
    }
}

@Composable
private fun RoutineCard(
    item: RoutineWithExercises,
    editMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(item.routine.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${item.exercises.size} exercises · ${item.exercises.sumOf { it.sets.size }} sets",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (editMode) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete routine")
                }
            }
        }
    }
}