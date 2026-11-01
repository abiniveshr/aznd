package com.ashrdev.aznd.ui.workout.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.workout.WorkoutViewModel

private const val STATS_WORKOUT = 0
private const val STATS_EXERCISES = 1
private const val STATS_PAGES = 2

/**
 * One workout's statistics: the workout screen's Stats page. Two pages, one above the other — swipe
 * up and down between them:
 *  - the workout's graph views (volume, weight moved, calories, sets, duration);
 *  - its exercises (pick one from the menu to see its graph views).
 */
@Composable
fun WorkoutStatsPages(
    workoutId: Long,
    viewModel: WorkoutViewModel,
    hasExercises: Boolean,
    useTargets: Boolean,
    graphViewModel: WorkoutGraphViewModel?,
    bottomPadding: Dp
) {
    val pager = rememberPagerState { STATS_PAGES }
    Box(Modifier.fillMaxSize()) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            Column(Modifier.fillMaxSize()) {
                Text(
                    if (page == STATS_WORKOUT) "Workout · swipe up for exercises" else "Exercises · swipe down for the workout",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 24.dp, top = 8.dp)
                )
                Box(Modifier.weight(1f)) {
                    if (page == STATS_WORKOUT) {
                        StatsPage(hasExercises, graphViewModel, bottomPadding)
                    } else {
                        ExerciseStatsPage(
                            workoutId = workoutId,
                            viewModel = viewModel,
                            useTargets = useTargets,
                            bottomPadding = bottomPadding
                        )
                    }
                }
            }
        }
        PageDots(
            count = STATS_PAGES,
            position = { pager.currentPage + pager.currentPageOffsetFraction },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp, bottom = bottomPadding)
        )
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

/** Which of the stacked pages is showing: a dot per page, the highlight sliding with the swipe. */
@Composable
private fun PageDots(count: Int, position: () -> Float, modifier: Modifier = Modifier) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Canvas(modifier.size(DotSize, DotSize * count + DotGap * (count - 1))) {
        val step = (DotSize + DotGap).toPx()
        val r = DotSize.toPx() / 2f
        for (i in 0 until count) drawCircle(off, r * 0.7f, Offset(r, r + i * step))
        drawCircle(on, r, Offset(r, r + position().coerceIn(0f, (count - 1).toFloat()) * step))
    }
}

private val DotSize = 7.dp
private val DotGap = 6.dp
