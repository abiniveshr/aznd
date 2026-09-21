package com.ashrdev.aznd.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object WorkoutLog : Screen("workout_log")
    object History : Screen("history")
    object RoutineHistory : Screen("routine_history/{routineId}") {
        fun createRoute(routineId: Long) = "routine_history/$routineId"
    }
    object RoutineStats : Screen("routine_stats/{routineId}") {
        fun createRoute(routineId: Long) = "routine_stats/$routineId"
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
    object Streaks : Screen("streaks")
    object StreakEditor : Screen("streak_editor")
    object StreakDetail : Screen("streak_detail/{streakId}") {
        fun createRoute(streakId: Long) = "streak_detail/$streakId"
    }
    object SavedDays : Screen("saved_days")
    object SavedDaysForStreak : Screen("saved_days/{streakId}") {
        fun createRoute(streakId: Long) = "saved_days/$streakId"
    }
    object SavedDayDetail : Screen("saved_day/{streakId}/{date}") {
        fun createRoute(streakId: Long, date: String) = "saved_day/$streakId/$date"
    }
}