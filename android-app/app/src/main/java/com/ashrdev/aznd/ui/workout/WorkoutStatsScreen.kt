package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.ExerciseEntity
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.common.ThirdAction

private val StatsPages = listOf(
    BarPage("Stats", Icons.Default.BarChart),
    BarPage("Exercises", Icons.Default.FitnessCenter)
)

/**
 * One workout's statistics, two swipable pages: Stats (the workout's graph views) and
 * Exercises (pick an exercise from the menu to see its graph views, swipe right).
 */
@Composable
fun WorkoutStatsScreen(
    workoutId: Long,
    viewModel: WorkoutViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    useTargets: Boolean,
    graphViewModel: WorkoutGraphViewModel? = null
) {
    var workoutName by remember { mutableStateOf("") }
    var exercises by remember { mutableStateOf(listOf<ExerciseEntity>()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId) {
        val workout = viewModel.getWorkout(workoutId)
        if (workout != null) {
            workoutName = workout.Workout.name
            exercises = workout.exercises.sortedBy { it.exercise.orderIndex }.map { it.exercise }
        }
        loaded = true
    }

    if (!loaded) return

    val pagerState = rememberPagerState { StatsPages.size }
    val subPages = rememberSubPageBar(pagerState, StatsPages)

    ScreenScaffold(
        title = if (pagerState.currentPage == 0) "$workoutName • Stats" else "$workoutName • Exercises",
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages,
        third = ThirdAction(icon = Icons.Default.Share, label = "Share", enabled = false, onClick = {})
    ) { padding ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            if (index == 0) {
                StatsPage(
                    hasExercises = exercises.isNotEmpty(),
                    graphViewModel = graphViewModel,
                    bottomPadding = padding.calculateBottomPadding()
                )
            } else {
                ExerciseStatsPage(
                    workoutId = workoutId,
                    viewModel = viewModel,
                    useTargets = useTargets,
                    bottomPadding = padding.calculateBottomPadding()
                )
            }
        }
    }
}

/** Workout-level graph views (volume, weight moved, calories, sets, duration). No border, no scrolling. */
@Composable
private fun StatsPage(
    hasExercises: Boolean,
    graphViewModel: WorkoutGraphViewModel?,
    bottomPadding: Dp
) {
    Box(modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
        if (!hasExercises) {
            Text(
                "No exercises in this workout yet.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp)
            )
        } else if (graphViewModel != null) {
            WorkoutGraphSection(graphViewModel, Modifier.fillMaxSize())
        }
    }
}
