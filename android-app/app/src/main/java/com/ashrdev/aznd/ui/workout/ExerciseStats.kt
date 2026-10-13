package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.ExerciseEntity
import com.ashrdev.aznd.data.workout.ExerciseSetPoint
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.GraphPoint
import com.ashrdev.aznd.domain.GraphStyle
import com.ashrdev.aznd.domain.PointTint
import com.ashrdev.aznd.ui.common.StatsGraph
import com.ashrdev.aznd.ui.components.OutlinedButton

/*
 * Per-exercise statistics, built as three independent layers so any screen can reuse them:
 *
 *  1. DATA   [ExerciseStatsData] + [buildExerciseStatsData]: one exercise's history, whatever
 *            produced it. Knows nothing about workouts or screens.
 *  2. SOURCE a loader that fills the data. [loadExerciseStats] is the workout-scoped one (used by
 *            the workout stats screen). The planned Exercise page writes its own that reads every
 *            session of the exercise (TrainingRepository.loggedSetDataForExercise, which includes
 *            empty-workout sessions) and calls [buildExerciseStatsData].
 *  3. VIEW   [ExerciseStatsView]: the graph views for one exercise. Not scrollable, no border;
 *            it fills the space it is given. [ExerciseMenu] is the optional exercise chooser.
 */

/** One exercise's history, ready to graph. */
data class ExerciseStatsData(
    val name: String = "",
    val points: List<SessionE1rmPoint> = emptyList(),
    val volume: List<GraphPoint> = emptyList(),
    val repRange: IntRange? = null,
    val loaded: Boolean = false
)

/** Turns raw per-set history (any source) into [ExerciseStatsData]. */
fun buildExerciseStatsData(name: String, raw: List<ExerciseSetPoint>, repRange: IntRange?): ExerciseStatsData {
    val volume = raw.groupBy { it.sessionId }
        .map { (_, sets) ->
            GraphPoint(
                timeMs = sets.first().startedAt,
                value = sets.sumOf { it.weight * it.value },
                detail = "${sets.size} sets"
            )
        }
        .sortedBy { it.timeMs }
    return ExerciseStatsData(
        name = name,
        points = buildSessionE1rmPoints(raw),
        volume = volume,
        repRange = repRange,
        loaded = true
    )
}

/** Source: one exercise of one workout, from that workout's own history. */
suspend fun loadExerciseStats(
    viewModel: WorkoutViewModel,
    workoutId: Long,
    exerciseId: Long,
    useTargets: Boolean
): ExerciseStatsData {
    val exercise = viewModel.getWorkout(workoutId)?.exercises?.find { it.exercise.id == exerciseId }
        ?: return ExerciseStatsData(loaded = true)
    val name = exercise.exercise.name
    return buildExerciseStatsData(
        name = name,
        raw = viewModel.getExerciseHistory(workoutId, name),
        repRange = if (useTargets) viewModel.repRangeFor(workoutId, name) else null
    )
}

@Composable
fun rememberExerciseStats(
    viewModel: WorkoutViewModel,
    workoutId: Long,
    exerciseId: Long,
    useTargets: Boolean
): ExerciseStatsData {
    val state = produceState(ExerciseStatsData(), workoutId, exerciseId, useTargets) {
        value = loadExerciseStats(viewModel, workoutId, exerciseId, useTargets)
    }
    return state.value
}

/** The graph views of one exercise: 1RM, top set reps (when targets are on) and volume. */
fun exerciseGraphModes(data: ExerciseStatsData): List<GraphMode> {
    val modes = mutableListOf<GraphMode>()
    modes += GraphMode(
        id = "e1rm",
        label = "1RM",
        unit = "kg",
        style = GraphStyle.LINE,
        points = data.points.map { GraphPoint(it.startedAt, it.e1rm, formatTopSet(it)) },
        startAtZero = false
    )
    val range = data.repRange
    if (range != null) {
        modes += GraphMode(
            id = "reps",
            label = "Top set reps",
            unit = "reps",
            style = GraphStyle.LINE,
            points = data.points.map { p ->
                GraphPoint(
                    timeMs = p.startedAt,
                    value = p.topSetReps.toDouble(),
                    detail = formatTopSet(p),
                    tint = when {
                        p.topSetReps < range.first -> PointTint.BELOW
                        p.topSetReps > range.last -> PointTint.ABOVE
                        else -> PointTint.NORMAL
                    }
                )
            },
            startAtZero = false,
            bandMin = range.first.toDouble(),
            bandMax = range.last.toDouble()
        )
    }
    modes += GraphMode(
        id = "volume",
        label = "Volume",
        unit = "kg",
        style = GraphStyle.BARS,
        points = data.volume
    )
    return modes
}

private fun repRangeFootnote(range: IntRange): String {
    val text = if (range.first == range.last) "${range.first} reps" else "${range.first}–${range.last} reps"
    return "Preferred range $text is shaded. Darker dots fell below it, brighter dots went above."
}

/**
 * View: the graph views of one exercise (pick the view with the chips, the time range with the
 * dropdown). No border and no scrolling: the graph fills [modifier]'s space.
 */
@Composable
fun ExerciseStatsView(data: ExerciseStatsData, modifier: Modifier = Modifier, title: String? = null) {
    if (data.points.isEmpty()) {
        Text(
            "No logged sets for this exercise yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp)
        )
        return
    }
    StatsGraph(
        modes = exerciseGraphModes(data),
        modifier = modifier.fillMaxSize(),
        title = title,
        footnote = data.repRange?.let { repRangeFootnote(it) },
        bordered = false,
        fillHeight = true
    )
}

/** A dropdown for choosing one exercise out of [items] (id to name). */
@Composable
fun ExerciseMenu(
    items: List<Pair<Long, String>>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val current = items.firstOrNull { it.first == selectedId }?.second ?: ""
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(current, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose exercise")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        open = false
                        onSelect(id)
                    }
                )
            }
        }
    }
}

/** Workout stats, "Exercises" page: choose an exercise of this workout from the menu, see its graphs. */
@Composable
fun ExerciseStatsPage(
    workoutId: Long,
    exercises: List<ExerciseEntity>,
    viewModel: WorkoutViewModel,
    useTargets: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    if (exercises.isEmpty()) {
        Text(
            "No exercises in this workout yet.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(24.dp)
        )
        return
    }
    var selectedId by rememberSaveable { mutableStateOf(exercises.first().id) }
    val activeId = exercises.firstOrNull { it.id == selectedId }?.id ?: exercises.first().id

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        ExerciseMenu(
            items = exercises.map { it.id to it.name },
            selectedId = activeId,
            onSelect = { selectedId = it },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        key(activeId) {
            val data = rememberExerciseStats(viewModel, workoutId, activeId, useTargets)
            if (data.loaded) ExerciseStatsView(data, Modifier.weight(1f))
        }
    }
}
