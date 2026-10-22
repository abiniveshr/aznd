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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import com.ashrdev.aznd.ui.components.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.WorkoutWithExercises
import com.ashrdev.aznd.domain.workout.ExerciseType
import com.ashrdev.aznd.domain.workout.SetKind
import com.ashrdev.aznd.domain.workout.SetRow
import com.ashrdev.aznd.domain.workout.previousRowFor
import com.ashrdev.aznd.domain.workout.setLabels
import com.ashrdev.aznd.domain.workout.targetLabel
import com.ashrdev.aznd.ui.common.PrimaryAction
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.ThirdAction
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.workout.exercise.MuscleBadge
import com.ashrdev.aznd.ui.workout.session.formatPreviousSet

/**
 * Read-only view of a workout's template details. Tapping an exercise opens the exercise viewer.
 * Start opens the logger, which starts a new session or resumes the unfinished one of this workout.
 * Only one session can run at a time, so while a session of ANOTHER workout is unfinished, Start
 * offers to go there instead.
 *
 * [onOpenSession] receives the id of the workout whose logger should open.
 */
@Composable
fun WorkoutDetailScreen(
    workoutId: Long,
    viewModel: WorkoutViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    // Opens the exercise viewer for a catalog exercise id.
    onViewExercise: (Long) -> Unit,
    // Opens Flex on the routine preset for this workout.
    onShare: (Long) -> Unit
) {
    val active by viewModel.activeSession.collectAsState()
    var workout by remember { mutableStateOf<WorkoutWithExercises?>(null) }
    var template by remember { mutableStateOf<TemplateView?>(null) }
    var previous by remember { mutableStateOf<List<SetRow>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var blockedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId) {
        workout = viewModel.getWorkout(workoutId)
        template = viewModel.templateView(workoutId)
        previous = viewModel.previousRows(workoutId)
        loaded = true
    }

    if (!loaded) return
    val item = workout
    if (item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val view = template
    val hasRows = view != null && view.cards.isNotEmpty()

    if (blockedDialog) {
        val running = active
        AlertDialog(
            onDismissRequest = { blockedDialog = false },
            title = { Text("Session already running") },
            text = {
                Text("You have an unfinished ${running?.workoutName.orEmpty()} session. Finish or discard it before starting another.")
            },
            confirmButton = {
                TextButton(onClick = {
                    blockedDialog = false
                    if (running != null) onOpenSession(running.workoutId)
                }) { Text("Go to session") }
            },
            dismissButton = {
                TextButton(onClick = { blockedDialog = false }) { Text("Cancel") }
            }
        )
    }

    ScreenScaffold(
        title = item.workout.name,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        // Edit / View toggle: this is the View side; the editor shows "View" in the same slot.
        third = ThirdAction(
            icon = Icons.Default.Edit,
            label = "Edit",
            onClick = { onEdit(item.workout.id) }
        ),
        extraActions = listOf(
            ThirdAction(
                icon = Icons.Default.Share,
                label = "Share",
                enabled = hasRows,
                onClick = { onShare(item.workout.id) }
            )
        ),
        primaryAction = PrimaryAction(
            icon = Icons.Default.PlayArrow,
            label = if (active?.workoutId == item.workout.id) "Resume" else "Start",
            enabled = hasRows,
            onClick = {
                val running = active
                if (running != null && running.workoutId != item.workout.id) blockedDialog = true
                else onOpenSession(item.workout.id)
            }
        )
    ) { padding ->
        if (view == null || view.cards.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    "No exercises yet — tap Edit in the bar to add some.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            // Depth-first rows of the whole template, to pair each set with the same set last time.
            val allRows = remember(view) { view.cards.flatMap { it.rows } }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(view.cards, key = { it.rows.first().id }) { card ->
                    val exercise = card.exerciseId?.let { view.exercises[it] }
                    val labels = setLabels(card)
                    val exerciseId = card.exerciseId
                    // Tapping an exercise opens the exercise viewer.
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (exerciseId != null) Modifier.clickable { onViewExercise(exerciseId) }
                                else Modifier
                            )
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (exercise != null) MuscleBadge(exercise.primaryMuscle, exercise.photoPath)
                                Text(exercise?.name ?: "Unknown exercise", style = MaterialTheme.typography.titleMedium)
                            }
                            card.rows.forEach { row ->
                                val rowExercise = row.exerciseId?.let { view.exercises[it] } ?: exercise
                                val target = targetLabel(row, rowExercise?.type ?: ExerciseType.WEIGHTED, true)
                                val label = labels[row.id].orEmpty()
                                val name = if (row.kind == SetKind.SUPERSET) " · ${rowExercise?.name.orEmpty()}" else ""
                                val prev = previousRowFor(row, allRows, previous)
                                    ?.let { formatPreviousSet(rowExercise?.type ?: ExerciseType.WEIGHTED, it, false) }
                                Column(modifier = Modifier.padding(start = if (row.kind == SetKind.NORMAL) 0.dp else 12.dp)) {
                                    Text(
                                        text = label + name + if (target != null) " · target $target" else "",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (prev != null) {
                                        Text(
                                            text = "Previous: $prev",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
