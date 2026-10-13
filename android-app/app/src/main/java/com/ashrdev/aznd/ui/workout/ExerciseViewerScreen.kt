package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberSubPageBar

private const val VIEWER_TUTORIAL = 0
private const val VIEWER_EXERCISE = 1
private const val VIEWER_STATS = 2

private val ViewerPages = listOf(
    BarPage("Tutorial", Icons.Default.PlayArrow),
    BarPage("Exercise", Icons.Default.FitnessCenter),
    BarPage("Stats", Icons.Default.BarChart)
)

/**
 * The exercise viewer, opened from the Exercises page and from an exercise inside a workout.
 * Three swipable pages, opening on the middle one:
 *   Tutorial | Exercise | Stats
 *  - Tutorial: looping demo frames and written steps (see ExerciseTutorialPage), when the exercise has one.
 *  - Exercise: the muscles the exercise trains.
 *  - Stats: graphs over every session of every workout, empty workouts included.
 * Custom exercises get an Edit button in the top bar; catalog exercises are read-only.
 */
@Composable
fun ExerciseViewerScreen(
    exerciseId: Long,
    viewModel: WorkoutViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onEdit: (Long) -> Unit
) {
    var exercise by remember { mutableStateOf<Exercise?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = VIEWER_EXERCISE) { ViewerPages.size }
    val subPages = rememberSubPageBar(pagerState, ViewerPages)

    LaunchedEffect(exerciseId) {
        exercise = viewModel.exercise(exerciseId)
        loaded = true
    }

    if (!loaded) return
    val item = exercise
    if (item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    ScreenScaffold(
        title = item.name,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages,
        topBarActions = {
            if (item.isCustom) {
                IconButton(onClick = { onEdit(item.id) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit exercise")
                }
            }
        }
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            when (index) {
                VIEWER_TUTORIAL -> ExerciseTutorialPage(exercise = item, bottomPadding = bottom)
                VIEWER_STATS -> ViewerStatsPage(item = item, viewModel = viewModel, bottomPadding = bottom)
                else -> ExerciseMusclesPage(item = item, bottomPadding = bottom)
            }
        }
    }
}

@Composable
private fun ViewerStatsPage(item: Exercise, viewModel: WorkoutViewModel, bottomPadding: Dp) {
    val data by produceState(ExerciseStatsData(), item.id) {
        value = loadExerciseStatsAllSessions(viewModel, item)
    }
    if (data.loaded) {
        ExerciseStatsView(data, Modifier.padding(bottom = bottomPadding))
    }
}
