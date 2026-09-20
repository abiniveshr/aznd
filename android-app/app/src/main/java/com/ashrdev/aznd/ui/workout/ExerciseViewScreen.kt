package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.LoggedSetEntity
import com.ashrdev.aznd.data.workout.SetMode

private enum class ChartRange { LIFETIME, LAST_5 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseViewScreen(
    routineId: Long,
    exerciseId: Long,
    viewModel: WorkoutViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var exerciseName by remember { mutableStateOf("") }
    var setModes by remember { mutableStateOf(listOf<SetMode>()) }
    var previousSets by remember { mutableStateOf(listOf<LoggedSetEntity>()) }
    var allPoints by remember { mutableStateOf(listOf<SessionE1rmPoint>()) }
    var loaded by remember { mutableStateOf(false) }
    var range by remember { mutableStateOf(ChartRange.LIFETIME) }

    LaunchedEffect(routineId, exerciseId) {
        val exercise = viewModel.getRoutine(routineId)?.exercises?.find { it.exercise.id == exerciseId }
        if (exercise != null) {
            exerciseName = exercise.exercise.name
            setModes = exercise.sets.sortedBy { it.orderIndex }.map { it.mode }
            previousSets = viewModel.getPreviousSession(routineId)
                ?.sets
                ?.sortedBy { it.orderIndex }
                ?.filter { it.exerciseName == exerciseName }
                ?: emptyList()
            allPoints = buildSessionE1rmPoints(viewModel.getExerciseHistory(routineId, exerciseName))
        }
        loaded = true
    }

    if (!loaded) return

    val displayedPoints = if (range == ChartRange.LIFETIME) allPoints else allPoints.takeLast(5)

    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exerciseName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (displayedPoints.isNotEmpty()) {
                        IconButton(onClick = {
                            val uri = ChartExport.exportChart(
                                context, displayedPoints, lineColor, textColor, gridColor, backgroundColor
                            )
                            SessionShare.share(
                                context = context,
                                subject = "$exerciseName progress",
                                body = "$exerciseName · ${if (range == ChartRange.LIFETIME) "lifetime" else "last 5 sessions"}\nEstimated 1 Rep Max = ${formatE1rm(displayedPoints.last().e1rm)}",
                                photoUri = uri
                            )
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share progress")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (allPoints.isNotEmpty()) {
                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = range == ChartRange.LIFETIME,
                            onClick = { range = ChartRange.LIFETIME },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("Lifetime") }
                        SegmentedButton(
                            selected = range == ChartRange.LAST_5,
                            onClick = { range = ChartRange.LAST_5 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("Last 5") }
                    }
                }
                item {
                    E1rmChart(
                        points = displayedPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }
                if (range == ChartRange.LAST_5) {
                    itemsIndexed(displayedPoints) { _, point ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    formatTimestamp(point.startedAt),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "${point.topSetReps} reps @ ${formatWeight(point.topSetWeight)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Estimated 1 Rep Max = ${formatE1rm(point.e1rm)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "No logged sets for this exercise yet.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (range == ChartRange.LIFETIME) {
                itemsIndexed(setModes) { index, mode ->
                    val prev = previousSets.getOrNull(index)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Set ${index + 1} · ${if (mode == SetMode.REPS) "Reps" else "Time"}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (prev != null) "Previous: ${formatSet(prev)}" else "No previous data",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}