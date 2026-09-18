package com.ashrdev.aznd.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object WorkoutLog : Screen("workout_log")
    object History : Screen("history")
    object RoutineRunner : Screen("routine_runner")
    object RoutineEditor : Screen("routine_editor?routineId={routineId}") {
        fun createRoute(routineId: Long?) = "routine_editor?routineId=${routineId ?: -1}"
    }
    object SessionDetail : Screen("session/{sessionId}") {
        fun createRoute(sessionId: Long) = "session/$sessionId"
    }
}