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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.RoutineWithExercises
import com.ashrdev.aznd.data.workout.SetMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineViewScreen(
    routineId: Long,
    viewModel: WorkoutViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenSession: () -> Unit,
    onViewExercise: (Long, Long) -> Unit
) {
    val active by viewModel.activeSession.collectAsState()
    var routine by remember { mutableStateOf<RoutineWithExercises?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var blockedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(routineId) {
        routine = viewModel.getRoutine(routineId)
        loaded = true
    }

    if (!loaded) return
    val item = routine
    if (item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.routine.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(item.routine.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit routine")
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    if (active != null) blockedDialog = true
                    else {
                        viewModel.startSession(item)
                        onOpenSession()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Start")
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
            items(item.exercises, key = { it.exercise.id }) { ews ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onViewExercise(item.routine.id, ews.exercise.id) }
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