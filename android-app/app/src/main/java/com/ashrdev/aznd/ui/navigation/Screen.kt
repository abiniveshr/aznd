package com.ashrdev.aznd.ui.navigation

import com.ashrdev.aznd.domain.Muscle

/*
 * Route map (what opens what). Back always returns to the screen you came from;
 * the Home button returns to the Dashboard page of the Home pager.
 *
 * Home (pager: History | Dashboard | Apps) - opens on Dashboard
 *  |- Workouts (pager: Exercises | Workouts | Stats) -------- Workouts
 *  |     |- Workouts page: Start on a card ------------- WorkoutSession -- Finish --> (back)
 *  |     |                 a workout ------------------- WorkoutDetail
 *  |     |                                                 |- Start ---- WorkoutSession
 *  |     |                                                 '- exercise -- ExerciseViewer
 *  |     |- Exercises page: an exercise ---------------- ExerciseViewer (pager: Tutorial | Exercise | Stats; Edit on custom exercises -> CustomExercise)
 *  |     |                   + (top bar, every page) --- CustomExercise (add)
 *  |     |- Add workout button (Workouts page; "Empty workout" beside it starts a session with no template) or Edit in WorkoutDetail --------- WorkoutBuilder (Add exercise opens the picker,
 *  |     |                                                 which has "Add custom exercise" -> CustomExercise)
 *  |     |- Stats page: a workout ---------------------- WorkoutStats (pager: Stats | Exercises; exercise menu on the 2nd)
 *  |     '- session in progress ------------------------- WorkoutSession
 *  |- Streaks --------- Streaks
 *  |                     |- Add streak ------- StreakEditor
 *  |                     '- a streak ---------- StreakDetail
 *  |                                             |- Saved (book) -- SavedDaysForStreak -- SavedDayDetail
 *  |                                             '- Save today ---- SavedDayDetail
 *  |- Macros (pager: Graphs | Today | Edit) ------------- Macros
 *  |- Body (pager: Measurements | Strength | Composition) - BodyMeasurements
 *  |                     '- Strength page: a muscle ---- MuscleStats
 *  |- History page
 *  |    |- Workout History -- History (pick a workout) -- WorkoutHistory (calendar) -- SessionDetail
 *  |    |                                                   '- Stats -- WorkoutStats
 *  |    |- Weight History (trend graphs of weight, body fat, lean mass)
 *  |    '- Saved Streaks ---- SavedDays (pick a streak) -- SavedDaysForStreak (calendar) -- SavedDayDetail
 *  '- Dashboard page: streak card -> StreakDetail, top lift -> ExerciseView,
 *                     Muscle data (dropdown) -> MuscleStats
 *
 * Settings -> Appearance opens from the pull-up bar on every screen.
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Settings : Screen("settings")
    object Appearance : Screen("settings_appearance")

    object Workouts : Screen("workouts")
    object History : Screen("history")
    object WorkoutHistory : Screen("workout_history/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_history/$workoutId"
    }
    object WorkoutStats : Screen("workout_stats/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_stats/$workoutId"
    }
    object WorkoutSession : Screen("workout_session/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_session/$workoutId"
    }
    object CustomExercises : Screen("custom_exercises")
    object MuscleStats : Screen("muscle_stats/{muscle}") {
        fun createRoute(muscle: Muscle) = "muscle_stats/${muscle.name}"
    }
    object CustomExercise : Screen("custom_exercise?exerciseId={exerciseId}") {
        fun createRoute(exerciseId: Long?) = "custom_exercise?exerciseId=${exerciseId ?: -1}"
    }
    object WorkoutDetail : Screen("workout_detail/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_detail/$workoutId"
    }
    object ExerciseView : Screen("exercise_view/{workoutId}/{exerciseId}") {
        fun createRoute(workoutId: Long, exerciseId: Long) = "exercise_view/$workoutId/$exerciseId"
    }
    object ExerciseViewer : Screen("exercise_viewer/{exerciseId}") {
        fun createRoute(exerciseId: Long) = "exercise_viewer/$exerciseId"
    }
    object WorkoutBuilder : Screen("workout_builder?workoutId={workoutId}") {
        fun createRoute(workoutId: Long?) = "workout_builder?workoutId=${workoutId ?: -1}"
    }
    object SessionDetail : Screen("session/{sessionId}") {
        fun createRoute(sessionId: Long) = "session/$sessionId"
    }

    object Streaks : Screen("streaks")
    object Macros : Screen("macros")
    object BodyMeasurements : Screen("body_measurements")
    object WeightHistory : Screen("weight_history")
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
