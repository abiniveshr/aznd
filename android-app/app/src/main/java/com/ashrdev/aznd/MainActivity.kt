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
import com.ashrdev.aznd.ui.streaks.SavedDayDetailScreen
import com.ashrdev.aznd.ui.streaks.SavedDaysForStreakScreen
import com.ashrdev.aznd.ui.streaks.SavedDaysScreen
import com.ashrdev.aznd.ui.streaks.StreakDetailScreen
import com.ashrdev.aznd.ui.streaks.StreakEditorScreen
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.streaks.StreakViewModelFactory
import com.ashrdev.aznd.ui.streaks.StreaksScreen
import com.ashrdev.aznd.ui.theme.AzndTheme
import com.ashrdev.aznd.ui.workout.ExerciseViewScreen
import com.ashrdev.aznd.ui.workout.HistoryScreen
import com.ashrdev.aznd.ui.workout.RoutineEditorScreen
import com.ashrdev.aznd.ui.workout.RoutineRunnerScreen
import com.ashrdev.aznd.ui.workout.RoutineStatsScreen
import com.ashrdev.aznd.ui.workout.RoutineViewScreen
import com.ashrdev.aznd.ui.workout.SessionDetailScreen
import com.ashrdev.aznd.ui.workout.SessionHistoryScreen
import com.ashrdev.aznd.ui.workout.WorkoutLogScreen
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import com.ashrdev.aznd.ui.workout.WorkoutViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = AppDatabase.getInstance(this)
        val dao = database.routineDao()
        val streakDao = database.streakDao()
        setContent {
            AzndTheme {
                val navController = rememberNavController()
                val workoutViewModel: WorkoutViewModel =
                    viewModel(factory = WorkoutViewModelFactory(dao))
                val streakViewModel: StreakViewModel =
                    viewModel(factory = StreakViewModelFactory(streakDao))
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    enterTransition = { fadeIn(animationSpec = tween(30)) },
                    exitTransition = { fadeOut(animationSpec = tween(30)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(30)) },
                    popExitTransition = { fadeOut(animationSpec = tween(30)) }
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(onNavigate = { route -> navController.navigate(route) })
                    }
                    composable(Screen.WorkoutLog.route) {
                        WorkoutLogScreen(
                            viewModel = workoutViewModel,
                            onOpenRoutine = { id ->
                                navController.navigate(Screen.RoutineView.createRoute(id))
                            },
                            onEditRoutine = { id ->
                                navController.navigate(Screen.RoutineEditor.createRoute(id))
                            },
                            onAddRoutine = {
                                navController.navigate(Screen.RoutineEditor.createRoute(null))
                            },
                            onOpenHistory = { navController.navigate(Screen.History.route) },
                            onOpenSession = { navController.navigate(Screen.RoutineRunner.route) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.History.route) {
                        HistoryScreen(
                            viewModel = workoutViewModel,
                            onOpenRoutine = { id ->
                                navController.navigate(Screen.RoutineHistory.createRoute(id))
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = Screen.RoutineHistory.route,
                        arguments = listOf(navArgument("routineId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val routineId = backStackEntry.arguments?.getLong("routineId") ?: -1L
                        SessionHistoryScreen(
                            routineId = routineId,
                            viewModel = workoutViewModel,
                            onOpenSession = { id ->
                                navController.navigate(Screen.SessionDetail.createRoute(id))
                            },
                            onOpenStats = { id ->
                                navController.navigate(Screen.RoutineStats.createRoute(id))
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = Screen.RoutineStats.route,
                        arguments = listOf(navArgument("routineId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val routineId = backStackEntry.arguments?.getLong("routineId") ?: -1L
                        RoutineStatsScreen(
                            routineId = routineId,
                            viewModel = workoutViewModel,
                            onBack = { navController.popBackStack() },
                            onOpenExercise = { rId, exId ->
                                navController.navigate(Screen.ExerciseView.createRoute(rId, exId))
                            }
                        )
                    }
                    composable(
                        route = Screen.RoutineView.route,
                        arguments = listOf(navArgument("routineId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val routineId = backStackEntry.arguments?.getLong("routineId") ?: -1L
                        RoutineViewScreen(
                            routineId = routineId,
                            viewModel = workoutViewModel,
                            onBack = { navController.popBackStack() },
                            onEdit = { id -> navController.navigate(Screen.RoutineEditor.createRoute(id)) },
                            onOpenSession = { navController.navigate(Screen.RoutineRunner.route) },
                            onViewExercise = { rId, exId ->
                                navController.navigate(Screen.ExerciseView.createRoute(rId, exId))
                            },
                            onOpenStats = { id -> navController.navigate(Screen.RoutineStats.createRoute(id)) }
                        )
                    }
                    composable(
                        route = Screen.ExerciseView.route,
                        arguments = listOf(
                            navArgument("routineId") { type = NavType.LongType },
                            navArgument("exerciseId") { type = NavType.LongType }
                        )
                    ) { backStackEntry ->
                        val routineId = backStackEntry.arguments?.getLong("routineId") ?: -1L
                        val exerciseId = backStackEntry.arguments?.getLong("exerciseId") ?: -1L
                        ExerciseViewScreen(
                            routineId = routineId,
                            exerciseId = exerciseId,
                            viewModel = workoutViewModel,
                            onBack = { navController.popBackStack() },
                            onOpenSession = { id -> navController.navigate(Screen.SessionDetail.createRoute(id)) }
                        )
                    }
                    composable(Screen.RoutineRunner.route) {
                        RoutineRunnerScreen(
                            viewModel = workoutViewModel,
                            onBack = { navController.popBackStack() },
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
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = Screen.RoutineEditor.route,
                        arguments = listOf(navArgument("routineId") {
                            type = NavType.LongType
                            defaultValue = -1L
                        })
                    ) { backStackEntry ->
                        val routineId = backStackEntry.arguments
                            ?.getLong("routineId")
                            ?.takeIf { it != -1L }
                        RoutineEditorScreen(
                            routineId = routineId,
                            viewModel = workoutViewModel,
                            onDone = { navController.popBackStack() },
                            onDeleted = { navController.popBackStack(Screen.WorkoutLog.route, false) }
                        )
                    }
                    composable(Screen.Streaks.route) {
                        StreaksScreen(
                            viewModel = streakViewModel,
                            onOpenStreak = { id -> navController.navigate(Screen.StreakDetail.createRoute(id)) },
                            onAddStreak = { navController.navigate(Screen.StreakEditor.route) },
                            onOpenSavedDays = { navController.navigate(Screen.SavedDays.route) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.StreakEditor.route) {
                        StreakEditorScreen(
                            viewModel = streakViewModel,
                            onDone = { navController.popBackStack() }
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
                            onBack = { navController.popBackStack() },
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
                            onBack = { navController.popBackStack() }
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
                            onBack = { navController.popBackStack() }
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
                            onDone = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}