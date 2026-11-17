package com.ashrdev.aznd.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.ashrdev.aznd.data.macro.MacroTargetsStore
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.workout.WorkoutViewModel

/**
 * Everything the dashboard's tiles show. It is worked out once, off the main thread, in
 * [DashboardViewModel.data]; Home, its edit mode and the graph screen all read that one result.
 */
@Composable
fun rememberDashboardData(
    workoutViewModel: WorkoutViewModel,
    streakViewModel: StreakViewModel,
    dashboardViewModel: DashboardViewModel,
    store: DashboardStore,
    macroTargets: MacroTargetsStore
): DashboardData {
    remember(dashboardViewModel) { dashboardViewModel.bind(workoutViewModel, streakViewModel, macroTargets, store) }
    val data by dashboardViewModel.data.collectAsState()
    return data
}
