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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.ExerciseEntity

private data class ExerciseStat(
    val exercise: ExerciseEntity,
    val sessionCount: Int,
    val bestE1rm: Double?,
    val latestE1rm: Double?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineStatsScreen(
    routineId: Long,
    viewModel: WorkoutViewModel,
    onBack: () -> Unit,
    onOpenExercise: (Long, Long) -> Unit
) {
    var routineName by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf(listOf<ExerciseStat>()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(routineId) {
        val routine = viewModel.getRoutine(routineId)
        if (routine != null) {
            routineName = routine.routine.name
            stats = routine.exercises
                .sortedBy { it.exercise.orderIndex }
                .map { ews ->
                    val points = buildSessionE1rmPoints(
                        viewModel.getExerciseHistory(routineId, ews.exercise.name)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$routineName · Stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (stats.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
            ) {
                Text("No exercises in this routine yet.", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(stats, key = { it.exercise.id }) { stat ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenExercise(routineId, stat.exercise.id) }
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