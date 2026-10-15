package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
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
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.ThirdAction
import com.ashrdev.aznd.ui.common.rememberImagePicker

/**
 * Screen for logging an active workout session. Exercises are swipeable cards (see [LoggerPager]).
 * The bottom bar holds Photo, Discard and Finish. Photo adds a picture when there is none; once
 * there is one it opens the picture with Replace / Remove / Save to gallery.
 */
@Composable
fun WorkoutSessionScreen(
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
    var showAddPicker by remember { mutableStateOf(false) }
    var showPhoto by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState { state.cards.size + 1 }
    val photoPicker = rememberImagePicker { uri -> viewModel.setPhoto(uri.toString()) }

    if (showAddPicker) {
        ExercisePickerDialog(
            exercises = exercises,
            onDismiss = { showAddPicker = false },
            onPick = {
                showAddPicker = false
                viewModel.addCardWithExercise(it)
            }
        )
    }

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

    val photoUri = session?.photoUri
    if (showPhoto && photoUri != null) {
        SessionPhotoDialog(
            uriString = photoUri,
            onDismiss = { showPhoto = false },
            onReplace = {
                showPhoto = false
                photoPicker.launch()
            },
            onRemove = {
                showPhoto = false
                viewModel.setPhoto(null)
            }
        )
    }

    val barActions = if (phase == LoggerPhase.Active) {
        listOf(
            ThirdAction(
                icon = if (photoUri == null) Icons.Default.AddAPhoto else Icons.Default.Photo,
                label = "Photo",
                onClick = { if (photoUri == null) photoPicker.launch() else showPhoto = true }
            ),
            ThirdAction(icon = Icons.Default.Delete, label = "Discard", onClick = { confirmDiscard = true }),
            ThirdAction(icon = Icons.Default.Check, label = "Finish", onClick = viewModel::finish)
        )
    } else {
        emptyList()
    }

    // The top bar shows the exercise on screen, with the workout name under it.
    val currentExercise = state.cards.getOrNull(pagerState.currentPage)?.exerciseId
        ?.let { id -> exercises.firstOrNull { it.id == id }?.name }

    ScreenScaffold(
        title = currentExercise ?: workoutName,
        subtitle = if (currentExercise != null) workoutName else null,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        extraActions = barActions
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
                LoggerPager(
                    data = data,
                    actions = viewModel.actions,
                    pagerState = pagerState,
                    bottomPadding = padding.calculateBottomPadding(),
                    onAddExercise = { showAddPicker = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> Unit
        }
    }
}
