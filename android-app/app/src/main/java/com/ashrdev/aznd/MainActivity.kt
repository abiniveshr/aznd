package com.ashrdev.aznd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ashrdev.aznd.data.workout.AppDatabase
import com.ashrdev.aznd.ui.home.HomeScreen
import com.ashrdev.aznd.ui.navigation.Screen
import com.ashrdev.aznd.ui.theme.AzndTheme
import com.ashrdev.aznd.ui.workout.RoutineEditorScreen
import com.ashrdev.aznd.ui.workout.RoutineRunnerScreen
import com.ashrdev.aznd.ui.workout.SessionDetailScreen
import com.ashrdev.aznd.ui.workout.SessionHistoryScreen
import com.ashrdev.aznd.ui.workout.WorkoutLogScreen
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import com.ashrdev.aznd.ui.workout.WorkoutViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dao = AppDatabase.getInstance(this).routineDao()
        setContent {
            AzndTheme {
                val navController = rememberNavController()
                val workoutViewModel: WorkoutViewModel =
                    viewModel(factory = WorkoutViewModelFactory(dao))
                NavHost(navController = navController, startDestination = Screen.Home.route) {
                    composable(Screen.Home.route) {
                        HomeScreen(onNavigate = { route -> navController.navigate(route) })
                    }
                    composable(Screen.WorkoutLog.route) {
                        WorkoutLogScreen(
                            viewModel = workoutViewModel,
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
                    composable(Screen.History.route) {
                        SessionHistoryScreen(
                            viewModel = workoutViewModel,
                            onOpenSession = { id ->
                                navController.navigate(Screen.SessionDetail.createRoute(id))
                            },
                            onBack = { navController.popBackStack() }
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
                            onDone = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}