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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.ExerciseEntity
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.ThirdAction
import com.ashrdev.aznd.ui.components.Card

private data class ExerciseStat(
    val exercise: ExerciseEntity,
    val sessionCount: Int,
    val bestE1rm: Double?,
    val latestE1rm: Double?
)

@Composable
fun WorkoutStatsScreen(
    workoutId: Long,
    viewModel: WorkoutViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExercise: (Long, Long) -> Unit
) {
    var workoutName by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf(listOf<ExerciseStat>()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId) {
        val workout = viewModel.getWorkout(workoutId)
        if (workout != null) {
            workoutName = workout.Workout.name
            stats = workout.exercises
                .sortedBy { it.exercise.orderIndex }
                .map { ews ->
                    val points = buildSessionE1rmPoints(
                        viewModel.getExerciseHistory(workoutId, ews.exercise.name)
                    )
                    ExerciseStat(
                        exercise = ews.exercise,
                        sessionCount = points.size,
                        bestE1rm = points.maxOfOrNull { it.e1rm },
                        latestE1rm = points.lastOrNull()?.e1rm
                    )
                }
        }
        loaded = true
    }

    if (!loaded) return

    ScreenScaffold(
        title = "$workoutName · Stats",
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        third = ThirdAction(icon = Icons.Default.Share, label = "Share", enabled = false, onClick = {})
    ) { padding ->
        if (stats.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text("No exercises in this workout yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(stats, key = { it.exercise.id }) { stat ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenExercise(workoutId, stat.exercise.id) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stat.exercise.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${stat.sessionCount} sessions logged",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (stat.bestE1rm != null) {
                                Text(
                                    "Best estimated 1RM: ${formatE1rm(stat.bestE1rm)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (stat.latestE1rm != null) {
                                Text(
                                    "Latest: ${formatE1rm(stat.latestE1rm)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
