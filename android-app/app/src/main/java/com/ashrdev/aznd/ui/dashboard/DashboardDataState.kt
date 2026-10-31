package com.ashrdev.aznd.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ashrdev.aznd.data.macro.MacroTargetsStore
import com.ashrdev.aznd.domain.macro.MacroStats
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.workout.TopExerciseStat
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import kotlinx.coroutines.delay

private const val TOP_LIFTS = 3
private const val CLOCK_TICK_MS = 60_000L

/**
 * Everything the dashboard's tiles show, kept up to date (the clock ticks each minute). The Home
 * dashboard and its edit mode both read it, so a page looks the same in both.
 */
@Composable
fun rememberDashboardData(
    workoutViewModel: WorkoutViewModel,
    streakViewModel: StreakViewModel,
    dashboardViewModel: DashboardViewModel,
    store: DashboardStore,
    macroTargets: MacroTargetsStore
): DashboardData {
    val streaks by streakViewModel.streaksWithCounts.collectAsState()
    val sessions by workoutViewModel.allSessions.collectAsState()
    val workouts by workoutViewModel.workouts.collectAsState()
    val food by dashboardViewModel.food.collectAsState()
    val weights by dashboardViewModel.weights.collectAsState()
    val measurements by dashboardViewModel.measurements.collectAsState()
    val burnt by dashboardViewModel.burntByDay.collectAsState()
    // Every preset's top-lift widgets, so a page carried or switched to never waits for its lifts.
    val liftRanks = store.presets.flatMap { p -> p.widgets.filter { it.type == DashWidgetType.TOP_LIFT }.map { it.rank } }
    val liftLimit = maxOf(TOP_LIFTS, liftRanks.maxOrNull() ?: 0)

    var topExercises by remember { mutableStateOf<List<TopExerciseStat>>(emptyList()) }
    LaunchedEffect(liftLimit, sessions.size) {
        topExercises = workoutViewModel.getTopExercises(liftLimit)
    }
    LaunchedEffect(sessions.size) { dashboardViewModel.refresh(sessions.size) }

    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(CLOCK_TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    val modes = rememberDashboardModes(dashboardViewModel, sessions)
    val dayStart = MacroStats.dayStart(nowMs)
    val today = remember(food, burnt, weights, measurements, dayStart, macroTargets.goal, macroTargets.advancedOffset, macroTargets.proteinG, macroTargets.carbsG, macroTargets.fatG) {
        DashboardSeries.today(
            food, burnt, weights, measurements, macroTargets.goal, macroTargets.advancedOffset,
            macroTargets.proteinG, macroTargets.carbsG, macroTargets.fatG, nowMs
        )
    }
    val minute = nowMs / CLOCK_TICK_MS
    return remember(streaks, topExercises, sessions, workouts, today, modes, minute) {
        DashboardData(streaks, topExercises, sessions, workouts, today, modes, nowMs)
    }
}
