package com.ashrdev.aznd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ashrdev.aznd.data.AppDatabase
import com.ashrdev.aznd.ui.home.HomeScreen
import com.ashrdev.aznd.ui.navigation.Screen
import com.ashrdev.aznd.ui.settings.SettingsScreen
import com.ashrdev.aznd.ui.settings.ThemeSettingsScreen
import com.ashrdev.aznd.ui.streaks.SavedDayDetailScreen
import com.ashrdev.aznd.ui.streaks.SavedDaysForStreakScreen
import com.ashrdev.aznd.ui.streaks.SavedDaysScreen
import com.ashrdev.aznd.ui.streaks.StreakDetailScreen
import com.ashrdev.aznd.ui.streaks.StreakEditorScreen
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.streaks.StreakViewModelFactory
import com.ashrdev.aznd.ui.streaks.StreaksScreen
import com.ashrdev.aznd.ui.theme.AzndTheme
import com.ashrdev.aznd.ui.theme.ThemeStore
import com.ashrdev.aznd.ui.workout.ExerciseViewScreen
import com.ashrdev.aznd.ui.workout.HistoryScreen
import com.ashrdev.aznd.ui.workout.SessionDetailScreen
import com.ashrdev.aznd.ui.workout.SessionHistoryScreen
import com.ashrdev.aznd.ui.workout.WorkoutEditorScreen
import com.ashrdev.aznd.ui.workout.WorkoutLogScreen
import com.ashrdev.aznd.ui.workout.WorkoutRunnerScreen
import com.ashrdev.aznd.ui.workout.WorkoutStatsScreen
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import com.ashrdev.aznd.ui.workout.WorkoutViewModelFactory
import com.ashrdev.aznd.ui.workout.WorkoutViewScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeStore.init(this)
        val database = AppDatabase.getInstance(this)
        val dao = database.WorkoutDao()
        val streakDao = database.streakDao()
        setContent {
            AzndTheme {
                val navController = rememberNavController()
                val workoutViewModel: WorkoutViewModel =
                    viewModel(factory = WorkoutViewModelFactory(dao))
                val streakViewModel: StreakViewModel =
                    viewModel(factory = StreakViewModelFactory(streakDao))

                // Shared by every screen's bottom bar.
                val goBack: () -> Unit = { navController.popBackStack() }
                val goHome: () -> Unit = { navController.popBackStack(Screen.Home.route, false) }
                val openSettings: () -> Unit = {
                    navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                }

                // Very subtle cross-fade between screens.
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    enterTransition = { fadeIn(animationSpec = tween(160)) },
                    exitTransition = { fadeOut(animationSpec = tween(100)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(160)) },
                    popExitTransition = { fadeOut(animationSpec = tween(100)) }
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(
                            workoutViewModel = workoutViewModel,
                            streakViewModel = streakViewModel,
                            onNavigate = { route -> navController.navigate(route) }
                        )
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            onBack = goBack,
                            onHome = goHome,
                            onOpenSettings = openSettings,
                            onOpenAppearance = { navController.navigate(Screen.Appearance.route) }
                        )
                    }
                    composable(Screen.Appearance.route) {
                        ThemeSettingsScreen(
                            onBack = goBack,
                            onHome = goHome,
                            onOpenSettings = openSettings
                        )
                    }

                    // ---- Workouts ----
                    composable(Screen.WorkoutLog.route) {
                        WorkoutLogScreen(
                            viewModel = workoutViewModel,
                            onOpenWorkout = { id ->
                                navController.navigate(Screen.WorkoutView.createRoute(id))
                            },
                            onEditWorkout = { id ->
                                navController.navigate(Screen.WorkoutEditor.createRoute(id))
                            },
                            onAddWorkout = {
                                navController.navigate(Screen.WorkoutEditor.createRoute(null))
                            },
                            onOpenSession = { navController.navigate(Screen.WorkoutRunner.route) },
                            onBack = goBack,
                            onHome = goHome,
                            onOpenSettings = openSettings
                        )
                    }
                    composable(Screen.History.route) {
                        HistoryScreen(
                            viewModel = workoutViewModel,
                            onOpenWorkout = { id ->
                                navController.navigate(Screen.WorkoutHistory.createRoute(id))
                            },
                            onBack = goBack,
                            onHome = goHome,
                            onOpenSettings = openSettings
                        )
                    }
                    composable(
                        route = Screen.WorkoutHistory.route,
                        arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: -1L
                        SessionHistoryScreen(
                            workoutId = workoutId,
                            viewModel = workoutViewModel,
                            onOpenSession = { id ->
                                navController.navigate(Screen.SessionDetail.createRoute(id))
                            },
                            onOpenStats = { id ->
                                navController.navigate(Screen.WorkoutStats.createRoute(id))
                            },
                            onBack = goBack,
                            onHome = goHome,
                            onOpenSettings = openSettings
                        )
                    }
                    composable(
                        route = Screen.WorkoutStats.route,
                        arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: -1L
                        WorkoutStatsScreen(
                            workoutId = workoutId,
                            viewModel = workoutViewModel,
                            onBack = goBack,
                            onHome = goHome,
                            onOpenSettings = openSettings,
                            onOpenExercise = { wId, exId ->
                                navController.navigate(Screen.ExerciseView.createRoute(wId, exId))
                            }
                        )
                    }
                    composable(
                        route = Screen.WorkoutView.route,
                        arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: -1L
                        WorkoutViewScreen(
                            workoutId = workoutId,
                            viewModel = workoutViewModel,
                            onBack = goBack,
                            onHome = goHome,
                            onOpenSettings = openSettings,
                            onEdit = { id -> navController.navigate(Screen.WorkoutEditor.createRoute(id)) },
                            onOpenSession = { navController.navigate(Screen.WorkoutRunner.route) },
                            onViewExercise = { wId, exId ->
                                navController.navigate(Screen.ExerciseView.createRoute(wId, exId))
                            },
                            onOpenStats = { id -> navController.navigate(Screen.WorkoutStats.createRoute(id)) }
                        )
                    }
                    composable(
                        route = Screen.ExerciseView.route,
                        arguments = listOf(
                            navArgument("workoutId") { type = NavType.LongType },
                            navArgument("exerciseId") { type = NavType.LongType }
                        )
                    ) { backStackEntry ->
                        val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: -1L
                        val exerciseId = backStackEntry.arguments?.getLong("exerciseId") ?: -1L
                        ExerciseViewScreen(
                            workoutId = workoutId,
                            exerciseId = exerciseId,
                            viewModel = workoutViewModel,
                            onBack = goBack,
                            onHome = goHome,
                            onOpenSettings = openSettings,
                            onOpenSession = { id -> navController.navigate(Screen.SessionDetail.createRoute(id)) }
                        )
                    }
                    composable(Screen.WorkoutRunner.route) {
                        WorkoutRunnerScreen(
                            viewModel = workoutViewModel,
                            onBack = goBack,
                            onHome = goHome,
                            onOpenSettings = openSettings,
                            onFinished = { id ->
                                navController.navigate(Screen.SessionDetail.createRoute(id)) {
                                    popUpTo(Screen.WorkoutLog.route)
                                }
                            }
                        )
                    }
                    composable(
                        route = Screen.SessionDetail.route,
                        arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: -1L
                        SessionDetailScreen(
                            sessionId = sessionId,
                            viewModel = workoutViewModel,
                            onBack = goBack,
                            onHome = goHome,
                            onOpenSettings = openSettings
                        )
                    }
                    composable(
                        route = Screen.WorkoutEditor.route,
                        arguments = listOf(navArgument("workoutId") {
                            type = NavType.LongType
                            defaultValue = -1L
                        })
                    ) { backStackEntry ->
                        val workoutId = backStackEntry.arguments
                            ?.getLong("workoutId")
                            ?.takeIf { it != -1L }
                        WorkoutEditorScreen(
                            workoutId = workoutId,
                            viewModel = workoutViewModel,
                            onDone = goBack,
                            onDeleted = { navController.popBackStack(Screen.WorkoutLog.route, false) },
                            onHome = goHome,
                            onOpenSettings = openSettings
                        )
                    }

                    // ---- Streaks ----
                    composable(Screen.Streaks.route) {
                        StreaksScreen(
                            viewModel = streakViewModel,
                            onOpenStreak = { id -> navController.navigate(Screen.StreakDetail.createRoute(id)) },
                            onAddStreak = { navController.navigate(Screen.StreakEditor.route) },
                            onBack = goBack,
                            onHome = goHome,
                            onOpenSettings = openSettings
                        )
                    }
                    composable(Screen.StreakEditor.route) {
                        StreakEditorScreen(
                            viewModel = streakViewModel,
                            onDone = goBack,
                            onHome = goHome,
                            onOpenSettings = openSettings
                        )
                    }
                    composable(
                        route = Screen.StreakDetail.route,
                        arguments = listOf(navArgument("streakId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val streakId = backStackEntry.arguments?.getLong("streakId") ?: -1L
                        StreakDetailScreen(
                            streakId = streakId,
                            viewModel = streakViewModel,
                            onBack = goBack,
                            onHome = goHome,
                            onOpenSettings = openSettings,
                            onSaveDay = { sId, date ->
                                navController.navigate(Screen.SavedDayDetail.createRoute(sId, date))
                            }
                        )
                    }
                    composable(Screen.SavedDays.route) {
                        SavedDaysScreen(
                            viewModel = streakViewModel,
                            onOpenStreak = { id ->
                                navController.navigate(Screen.SavedDaysForStreak.createRoute(id))
                            },
                            onBack = goBack,
                            onHome = goHome,
                            onOpenSettings = openSettings
                        )
                    }
                    composable(
                        route = Screen.SavedDaysForStreak.route,
                        arguments = listOf(navArgument("streakId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val streakId = backStackEntry.arguments?.getLong("streakId") ?: -1L
                        SavedDaysForStreakScreen(
                            streakId = streakId,
                            viewModel = streakViewModel,
                            onOpenDay = { sId, date ->
                                navController.navigate(Screen.SavedDayDetail.createRoute(sId, date))
                            },
                            onBack = goBack,
                            onHome = goHome,
                            onOpenSettings = openSettings
                        )
                    }
                    composable(
                        route = Screen.SavedDayDetail.route,
                        arguments = listOf(
                            navArgument("streakId") { type = NavType.LongType },
                            navArgument("date") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val streakId = backStackEntry.arguments?.getLong("streakId") ?: -1L
                        val date = backStackEntry.arguments?.getString("date") ?: ""
                        SavedDayDetailScreen(
                            streakId = streakId,
                            date = date,
                            viewModel = streakViewModel,
                            onDone = goBack,
                            onHome = goHome,
                            onOpenSettings = openSettings
                        )
                    }
                }
            }
        }
    }
}
