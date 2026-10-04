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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.WorkoutWithExercises
import com.ashrdev.aznd.domain.ExerciseType
import com.ashrdev.aznd.domain.SetKind
import com.ashrdev.aznd.domain.setLabels
import com.ashrdev.aznd.domain.targetLabel
import com.ashrdev.aznd.ui.common.PrimaryAction
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.ThirdAction
import com.ashrdev.aznd.ui.components.Card

/**
 * Read-only view of a workout's template. Start opens the logger, which starts a new session or
 * resumes the unfinished one of this workout. Only one session can run at a time, so while a
 * session of ANOTHER workout is unfinished, Start offers to go there instead.
 *
 * [onOpenSession] receives the id of the workout whose logger should open.
 */
@Composable
fun WorkoutViewScreen(
    workoutId: Long,
    viewModel: WorkoutViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onViewExercise: (Long, Long) -> Unit,
    onOpenStats: (Long) -> Unit
) {
    val active by viewModel.activeSession.collectAsState()
    var workout by remember { mutableStateOf<WorkoutWithExercises?>(null) }
    var template by remember { mutableStateOf<TemplateView?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var blockedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId) {
        workout = viewModel.getWorkout(workoutId)
        template = viewModel.templateView(workoutId)
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
            label = if (active?.workoutId == item.Workout.id) "Resume" else "Start",
            enabled = hasRows,
            onClick = {
                val running = active
                if (running != null && running.workoutId != item.Workout.id) blockedDialog = true
                else onOpenSession(item.Workout.id)
            }
        ),
        topBarActions = {
            IconButton(onClick = { onEdit(item.Workout.id) }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit workout")
            }
        }
    ) { padding ->
        if (view == null || view.cards.isEmpty()) {
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
                items(view.cards, key = { it.rows.first().id }) { card ->
                    val exercise = card.exerciseId?.let { view.exercises[it] }
                    // The History/Stats screens know exercises by their old per-workout id.
                    val statsId = item.exercises.firstOrNull { it.exercise.name == exercise?.name }?.exercise?.id
                    val labels = setLabels(card)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (statsId != null) Modifier.clickable { onViewExercise(item.Workout.id, statsId) }
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
                                Text(
                                    text = label + name + if (target != null) " · target $target" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = if (row.kind == SetKind.NORMAL) 0.dp else 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
