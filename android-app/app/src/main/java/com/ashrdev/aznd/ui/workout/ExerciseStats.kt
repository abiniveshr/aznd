package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.data.workout.ExerciseSetPoint
import com.ashrdev.aznd.data.workout.toProfile
import com.ashrdev.aznd.domain.E1rmCalculator
import com.ashrdev.aznd.domain.ExerciseType
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.GraphPoint
import com.ashrdev.aznd.domain.GraphStyle
import com.ashrdev.aznd.domain.LoggedSetData
import com.ashrdev.aznd.domain.PointTint
import com.ashrdev.aznd.domain.SetKind
import com.ashrdev.aznd.ui.common.StatsGraph

/*
 * Per-exercise statistics, built as three independent layers so any screen can reuse them:
 *
 *  1. DATA   [ExerciseStatsData] + [buildExerciseStatsData]: one exercise's history, whatever
 *            produced it. Knows nothing about workouts or screens.
 *  2. SOURCE a loader that fills the data. [loadExerciseStats] is the workout-scoped one (used by
 *            the workout stats screen). [loadExerciseStatsAllSessions] reads every session of the
 *            exercise (TrainingRepository.loggedSetDataForExercise, which includes empty-workout
 *            sessions) and is used by the exercise viewer's Stats page.
 *  3. VIEW   [ExerciseStatsView]: the graph views for one exercise. Not scrollable, no border;
 *            it fills the space it is given. [ExerciseStatsPane] adds the universal exercise picker on top.
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
    return loadExerciseStatsByName(viewModel, workoutId, exercise.exercise.name, useTargets)
}

/** Source: one exercise (by name) of one workout, from that workout's own history. */
suspend fun loadExerciseStatsByName(
    viewModel: WorkoutViewModel,
    workoutId: Long,
    name: String,
    useTargets: Boolean
): ExerciseStatsData {
    return buildExerciseStatsData(
        name = name,
        raw = viewModel.getExerciseHistory(workoutId, name),
        repRange = if (useTargets) viewModel.repRangeFor(workoutId, name) else null
    )
}

/**
 * Source: one exercise across ALL sessions of every workout, empty workouts included, so it has
 * no rep range.
 */
suspend fun loadExerciseStatsAllSessions(viewModel: WorkoutViewModel, exercise: Exercise): ExerciseStatsData {
    // Sessions logged before the new logger exist only in the legacy history tables and are not included here.
    return buildExerciseStatsData(
        name = exercise.name,
        raw = allSessionSetPoints(exercise, viewModel.loggedSetsForExercise(exercise.id)),
        repRange = null
    )
}

/**
 * Converts logged sets to the shape [buildExerciseStatsData] expects, following [E1rmCalculator]:
 * DROP rows and sets without reps are skipped, and the weight handed on is the effective load
 * (bodyweight x share + extra for the two bodyweight types, so assisted negative weights reduce
 * the load; the plain weight otherwise). Sets whose load is not above zero are skipped, as the
 * calculator treats them as ineligible. Timed holds have no 1RM, so they yield no points.
 */
fun allSessionSetPoints(
    exercise: Exercise,
    rows: List<LoggedSetData>,
    calculator: E1rmCalculator = E1rmCalculator()
): List<ExerciseSetPoint> {
    if (exercise.type == ExerciseType.TIME_HELD) return emptyList()
    val profile = exercise.toProfile()
    return rows.mapNotNull { r ->
        val reps = r.reps ?: return@mapNotNull null
        if (r.kind == SetKind.DROP || reps <= 0) return@mapNotNull null
        val load = calculator.load(profile, r.bodyweightKg, r.weightKg)
        if (load <= 0.0) return@mapNotNull null
        ExerciseSetPoint(sessionId = r.sessionId, startedAt = r.startedAt, value = reps, weight = load, rpe = r.rpe)
    }
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

/**
 * The universal exercise stats pane: the app's regular exercise picker (full-screen search with
 * muscle / type filters) on top, the chosen exercise's graph views below.
 *  [pool]   which exercises can be picked. The Exercise page passes the whole catalog; a workout's
 *           stats pass only that workout's exercises (the catalog intersected with the workout).
 *  [loader] where the numbers come from for the picked exercise.
 */
@Composable
fun ExerciseStatsPane(
    pool: List<Exercise>,
    loader: suspend (Exercise) -> ExerciseStatsData,
    modifier: Modifier = Modifier
) {
    if (pool.isEmpty()) {
        Text(
            "No exercises to show stats for yet.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.padding(24.dp)
        )
        return
    }
    var selectedId by rememberSaveable { mutableStateOf(pool.first().id) }
    val selected = pool.firstOrNull { it.id == selectedId } ?: pool.first()
    val data by produceState(ExerciseStatsData(), selected.id) { value = loader(selected) }

    Column(modifier = modifier.fillMaxSize().padding(top = 16.dp)) {
        ExercisePicker(
            selected = selected,
            exercises = pool,
            onPick = { selectedId = it.id },
            modifier = Modifier.padding(horizontal = 16.dp),
            allowAddCustom = false
        )
        if (data.loaded) ExerciseStatsView(data, Modifier.weight(1f))
    }
}

/** Workout stats, "Exercises" page: the universal pane, limited to this workout's exercises. */
@Composable
fun ExerciseStatsPage(
    workoutId: Long,
    viewModel: WorkoutViewModel,
    useTargets: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    val pool by produceState<List<Exercise>?>(null, workoutId) {
        value = viewModel.workoutCatalogExercises(workoutId)
    }
    val list = pool ?: return
    ExerciseStatsPane(
        pool = list,
        loader = { ex -> loadExerciseStatsByName(viewModel, workoutId, ex.name, useTargets) },
        modifier = Modifier.padding(bottom = bottomPadding)
    )
}
