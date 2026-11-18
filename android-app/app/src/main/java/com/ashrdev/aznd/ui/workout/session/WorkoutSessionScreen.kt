package com.ashrdev.aznd.ui.workout.session

import android.content.ContextWrapper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import com.ashrdev.aznd.ui.components.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.ThirdAction
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.data.workout.toMeta
import com.ashrdev.aznd.ui.workout.exercise.ExercisePickerDialog
import com.ashrdev.aznd.ui.theme.Space

/**
 * Screen for logging an active workout session. Exercises are swipeable cards (see [LoggerPager]).
 * The bottom bar holds Photo, Discard and Finish. Photo adds a picture when there is none; once
 * there is one it opens the picture with Replace / Remove / Save to gallery. Finish first shows a
 * summary; with [onSummary] set it finishes and then calls [onSummary] with the history session id
 * instead of [onFinished] (when there is no history row, [onFinished] is called): the caller swaps this
 * screen for the workout summary screen.
 */
@Composable
fun WorkoutSessionScreen(
    workoutId: Long,
    workoutName: String,
    viewModel: LoggerViewModel,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onSummary: ((historySessionId: Long) -> Unit)? = null
) {
    LaunchedEffect(workoutId) { viewModel.begin(workoutId) }
    // Typed values are written shortly after the last keystroke; leaving the app writes them at once.
    val context = LocalContext.current
    DisposableEffect(context, viewModel) {
        val owner = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<LifecycleOwner>()
            .firstOrNull()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.saveNow()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    val phase by viewModel.phase.collectAsState()
    val session by viewModel.session.collectAsState()
    val state by viewModel.state.collectAsState()
    val exercises by viewModel.exercises.collectAsState()
    val previous by viewModel.previousRows.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val deleted by viewModel.deletedSet.collectAsState()
    var confirmDiscard by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<FinishSummary?>(null) }
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

    // The summary replaces this screen in one step: leaving first would start a
    // screen transition, and navigation is ignored while one is running.
    LaunchedEffect(phase) {
        if (phase == LoggerPhase.Closed) {
            // Read from the view model: it is set before the phase closes, the collected copy may lag a frame.
            val id = viewModel.shareId.value
            if (id != null && onSummary != null) onSummary(id) else onFinished()
        }
    }

    summary?.let { s ->
        FinishSummaryDialog(
            summary = s,
            onFinish = {
                summary = null
                viewModel.finish(share = true)
            },
            onKeepGoing = { summary = null }
        )
    }

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
            ThirdAction(
                icon = Icons.Default.Check,
                label = "Finish",
                onClick = {
                    val sess = session
                    if (sess != null) {
                        summary = buildFinishSummary(sess, state.rows, exercises, System.currentTimeMillis())
                    }
                }
            )
        )
    } else {
        emptyList()
    }

    // The top bar shows the exercise on screen, with the workout name under it. It follows the
    // card swipe (drift + cross-fade) instead of snapping at the halfway point; the Add page has
    // no exercise, so it shows just the workout name.
    val exerciseById = remember(exercises) { exercises.associateBy { it.id } }
    val metaById = remember(exercises) { exercises.associate { it.id to it.toMeta() } }
    val derivedCache = remember { LoggerDerivedCache() }
    val derived = remember(state, previous) {
        deriveLogger(state, previous, derivedCache.last).also { derivedCache.last = it }
    }
    val cards = derived.cards
    val exerciseNames = remember(cards, exerciseById) {
        cards.map { card -> card.exerciseId?.let { id -> exerciseById[id]?.name } }
    }
    val bodyweightKg = session?.bodyweightKg ?: 0.0
    val env = remember(exercises, exerciseById, metaById, suggestions, bodyweightKg, settings.trackRpe, settings.targetReps) {
        LoggerEnv(
            exercises = exercises,
            byId = exerciseById,
            meta = metaById,
            suggestions = suggestions,
            bodyweightKg = bodyweightKg,
            showRpe = settings.trackRpe,
            showTargets = settings.targetReps
        )
    }

    ScreenScaffold(
        title = workoutName,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        extraActions = barActions,
        pageCount = if (phase == LoggerPhase.Active) pagerState.pageCount else 0,
        pagePosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
        pageTitle = { page -> exerciseNames.getOrNull(page) ?: workoutName },
        pageSubtitle = { page -> if (exerciseNames.getOrNull(page) != null) workoutName else null }
    ) { padding ->
        when (phase) {
            LoggerPhase.EmptyWorkout -> Text(
                text = "This workout has no exercises yet. Edit it to add some.",
                modifier = Modifier.padding(Space.lg),
                style = MaterialTheme.typography.bodyLarge
            )
            LoggerPhase.Active -> {
                val data = LoggerData(
                    rows = state.rows,
                    cards = cards,
                    depth = derived.depth,
                    previousOf = derived.previousOf,
                    env = env
                )
                LoggerPager(
                    data = data,
                    actions = viewModel.actions,
                    pagerState = pagerState,
                    bottomPadding = padding.calculateBottomPadding(),
                    onAddExercise = { showAddPicker = true },
                    modifier = Modifier.fillMaxSize(),
                    undoVisible = deleted != null
                )
            }
            else -> Unit
        }
    }
}
