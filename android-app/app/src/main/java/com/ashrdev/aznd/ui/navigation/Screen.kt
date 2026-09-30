package com.ashrdev.aznd.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Settings : Screen("settings")
    object Appearance : Screen("settings_appearance")

    object WorkoutLog : Screen("workout_log")
    object History : Screen("history")
    object WorkoutHistory : Screen("workout_history/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_history/$workoutId"
    }
    object WorkoutStats : Screen("workout_stats/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_stats/$workoutId"
    }
    object WorkoutRunner : Screen("workout_runner")
    object WorkoutView : Screen("workout_view/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_view/$workoutId"
    }
    object ExerciseView : Screen("exercise_view/{workoutId}/{exerciseId}") {
        fun createRoute(workoutId: Long, exerciseId: Long) = "exercise_view/$workoutId/$exerciseId"
    }
    object WorkoutEditor : Screen("workout_editor?workoutId={workoutId}") {
        fun createRoute(workoutId: Long?) = "workout_editor?workoutId=${workoutId ?: -1}"
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
