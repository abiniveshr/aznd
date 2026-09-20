package com.ashrdev.aznd.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object WorkoutLog : Screen("workout_log")
    object History : Screen("history")
    object RoutineHistory : Screen("routine_history/{routineId}") {
        fun createRoute(routineId: Long) = "routine_history/$routineId"
    }
    object RoutineRunner : Screen("routine_runner")
    object RoutineView : Screen("routine_view/{routineId}") {
        fun createRoute(routineId: Long) = "routine_view/$routineId"
    }
    object ExerciseView : Screen("exercise_view/{routineId}/{exerciseId}") {
        fun createRoute(routineId: Long, exerciseId: Long) = "exercise_view/$routineId/$exerciseId"
    }
    object RoutineEditor : Screen("routine_editor?routineId={routineId}") {
        fun createRoute(routineId: Long?) = "routine_editor?routineId=${routineId ?: -1}"
    }
    object SessionDetail : Screen("session/{sessionId}") {
        fun createRoute(sessionId: Long) = "session/$sessionId"
    }
}