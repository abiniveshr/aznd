package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.ScreenScaffold

/**
 * Screen for logging an active workout session. Allows entering reps, weights, or durations
 * per set, adding photos, and completing or discarding the session.
 */
@Composable
fun WorkoutLoggerScreen(
    workoutId: Long,
    workoutName: String,
    viewModel: LoggerViewModel,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    LaunchedEffect(workoutId) { viewModel.begin(workoutId) }
    val phase by viewModel.phase.collectAsState()
    val session by viewModel.session.collectAsState()
    val state by viewModel.state.collectAsState()
    val exercises by viewModel.exercises.collectAsState()
    val previous by viewModel.previousRows.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(phase) { if (phase == LoggerPhase.Closed) onFinished() }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this workout?") },
            text = { Text("Everything logged in this session will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    viewModel.discard()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep") } }
        )
    }

    ScreenScaffold(
        title = workoutName,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        topBarActions = {
            if (phase == LoggerPhase.Active) {
                IconButton(onClick = { confirmDiscard = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Discard workout", tint = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = viewModel::finish) { Text("Finish") }
            }
        }
    ) { padding ->
        when (phase) {
            LoggerPhase.EmptyWorkout -> Text(
                text = "This workout has no exercises yet. Edit it to add some.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
            LoggerPhase.Active -> {
                val data = LoggerData(
                    rows = state.rows,
                    cards = state.cards,
                    depth = state.depthById,
                    exercises = exercises,
                    previous = previous,
                    suggestions = suggestions,
                    bodyweightKg = session?.bodyweightKg ?: 0.0,
                    showRpe = settings.trackRpe,
                    showTargets = settings.targetReps
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    loggerItems(data, viewModel.actions)
                }
            }
            else -> Unit
        }
    }
}
