package com.ashrdev.aznd.ui.navigation

/*
 * Route map (what opens what). Back always returns to the screen you came from;
 * the Home button returns to the Dashboard page of the Home pager.
 *
 * Home (pager: History | Dashboard | Apps) - opens on Dashboard
 *  |- Workouts (pager: Exercises | Workouts | Stats) -------- WorkoutLog
 *  |     |- Workouts page: Start on a card ------------- WorkoutLogger -- Finish --> (back)
 *  |     |                 a workout ------------------- WorkoutView
 *  |     |                                                 |- Start ---- WorkoutLogger
 *  |     |                                                 '- exercise -- ExerciseViewer
 *  |     |- Exercises page: an exercise ---------------- ExerciseViewer (pager: Tutorial | Exercise | Stats; Edit on custom exercises -> CustomExercise)
 *  |     |                   + (top bar, every page) --- CustomExercise (add)
 *  |     |- Add workout button (Workouts page; "Empty workout" beside it starts a session with no template) or Edit in WorkoutView --------- WorkoutEditor (Add exercise opens the picker,
 *  |     |                                                 which has "Add custom exercise" -> CustomExercise)
 *  |     |- Stats page: a workout ---------------------- WorkoutStats (pager: Stats | Exercises; exercise menu on the 2nd)
 *  |     '- session in progress ------------------------- WorkoutLogger
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

    object WorkoutLog : Screen("workout_log")
    object History : Screen("history")
    object WorkoutHistory : Screen("workout_history/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_history/$workoutId"
    }
    object WorkoutStats : Screen("workout_stats/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_stats/$workoutId"
    }
    object WorkoutLogger : Screen("workout_logger/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_logger/$workoutId"
    }
    object CustomExercises : Screen("custom_exercises")
    object MuscleStats : Screen("muscle_stats/{muscle}") {
        fun createRoute(muscle: com.ashrdev.aznd.domain.Muscle) = "muscle_stats/${muscle.name}"
    }
    object CustomExercise : Screen("custom_exercise?exerciseId={exerciseId}") {
        fun createRoute(exerciseId: Long?) = "custom_exercise?exerciseId=${exerciseId ?: -1}"
    }
    object WorkoutView : Screen("workout_view/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_view/$workoutId"
    }
    object ExerciseView : Screen("exercise_view/{workoutId}/{exerciseId}") {
        fun createRoute(workoutId: Long, exerciseId: Long) = "exercise_view/$workoutId/$exerciseId"
    }
    object ExerciseViewer : Screen("exercise_viewer/{exerciseId}") {
        fun createRoute(exerciseId: Long) = "exercise_viewer/$exerciseId"
    }
    object WorkoutEditor : Screen("workout_editor?workoutId={workoutId}") {
        fun createRoute(workoutId: Long?) = "workout_editor?workoutId=${workoutId ?: -1}"
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
