package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.components.OutlinedTextField
import kotlinx.coroutines.launch

/**
 * Workout builder UI for editing and creating workout templates.
 * Contains the workout name field and list of exercise cards.
 */
@Composable
fun WorkoutBuilderScreen(
    workoutId: Long?,
    initialName: String,
    viewModel: BuilderViewModel,
    onSaveRecord: suspend (name: String) -> Long,
    onDelete: (() -> Unit)?,
    deleteTemplate: suspend (workoutId: Long) -> Unit,
    onDone: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    LaunchedEffect(workoutId) { viewModel.load(workoutId) }
    val ready by viewModel.ready.collectAsState()
    val state by viewModel.state.collectAsState()
    val exercises by viewModel.exercises.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var name by rememberSaveable { mutableStateOf(initialName) }
    var showDelete by remember { mutableStateOf(false) }
    // Blocks a second tap on Save while the first one is still writing (it would create a second workout).
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (!ready) return

    if (showDelete && workoutId != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete workout?") },
            text = { Text("\"$name\" and its exercises will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    scope.launch {
                        deleteTemplate(workoutId)
                        onDelete()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
        )
    }

    ScreenScaffold(
        title = if (workoutId == null) "New Workout" else "Edit Workout",
        onBack = onDone,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        topBarActions = {
            if (workoutId != null && onDelete != null) {
                IconButton(onClick = { showDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete workout", tint = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(
                enabled = name.isNotBlank() && !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            val id = onSaveRecord(name.trim())
                            viewModel.save(id)
                            onDone()
                        } catch (e: Exception) {
                            saving = false
                            throw e
                        }
                    }
                }
            ) { Text("Save") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "name") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workout name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            builderItems(
                state = state,
                exercises = exercises,
                showTargets = settings.targetReps,
                actions = viewModel.actions
            )
        }
    }
}
