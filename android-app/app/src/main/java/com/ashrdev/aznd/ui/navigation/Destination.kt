package com.ashrdev.aznd.ui.navigation

import com.ashrdev.aznd.domain.Muscle
import com.ashrdev.aznd.ui.body.CompositionMetric

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
 *  |     |- Exercises page: an exercise ---------------- ExerciseViewer (pager: Tutorial | Exercise | Stats; Edit on custom exercises -> CustomExerciseForm)
 *  |     |                   + (top bar, every page) --- CustomExerciseForm (add)
 *  |     |- Add workout button (Workouts page; "Empty workout" beside it starts a session with no template) or Edit in WorkoutDetail --------- WorkoutBuilder (Add exercise opens the picker,
 *  |     |                                                 which has "Add custom exercise" -> CustomExerciseForm)
 *  |     |- Stats page: a workout ---------------------- WorkoutStats (pager: Stats | Exercises; exercise menu on the 2nd)
 *  |     '- session in progress ------------------------- WorkoutSession
 *  |- Streaks --------- Streaks
 *  |                     |- Add streak ------- StreakEditor
 *  |                     '- a streak ---------- StreakDetail
 *  |                                             |- Saved (book) -- SavedDaysForStreak -- SavedDayDetail
 *  |                                             '- Save today ---- SavedDayDetail
 *  |- Macros (pager: Graphs | Today | Edit) ------------- Macro
 *  |- Body (pager: Measurements | Strength | Composition) - Body
 *  |                     '- Strength page: a muscle ---- MuscleStats
 *  |- History page
 *  |    |- Workout History -- WorkoutHistoryList (pick a workout) -- WorkoutHistory (calendar) -- SessionDetail
 *  |    |                                                              '- Stats -- WorkoutStats
 *  |    |- Body Stats History (pager: Measurements | Strength | Composition; only Composition has content yet)
 *  |    |     '- Composition: Bodyweight or Body fat -- BodyCompositionHistory (pager: Calendar | Graph | Logs)
 *  |    '- Saved Streaks ---- SavedDays (pick a streak) -- SavedDaysForStreak (calendar) -- SavedDayDetail
 *  '- Dashboard page: streak card -> StreakDetail, top lift -> ExerciseProgress,
 *                     Muscle data (dropdown) -> MuscleStats
 *
 * Settings -> Appearance opens from the pull-up bar on every screen.
 */
sealed class Destination(val route: String) {
    object Home : Destination("home")
    object Settings : Destination("settings")
    object Appearance : Destination("appearance")

    object Workouts : Destination("workouts")
    object WorkoutHistoryList : Destination("workout_history_list")
    object WorkoutHistory : Destination("workout_history/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_history/$workoutId"
    }
    object WorkoutStats : Destination("workout_stats/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_stats/$workoutId"
    }
    object WorkoutSession : Destination("workout_session/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_session/$workoutId"
    }
    object CustomExerciseList : Destination("custom_exercise_list")
    object MuscleStats : Destination("muscle_stats/{muscle}") {
        fun createRoute(muscle: Muscle) = "muscle_stats/${muscle.name}"
    }
    object CustomExerciseForm : Destination("custom_exercise_form?exerciseId={exerciseId}") {
        fun createRoute(exerciseId: Long?) = "custom_exercise_form?exerciseId=${exerciseId ?: -1}"
    }
    object WorkoutDetail : Destination("workout_detail/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_detail/$workoutId"
    }
    object ExerciseProgress : Destination("exercise_progress/{workoutId}/{exerciseId}") {
        fun createRoute(workoutId: Long, exerciseId: Long) = "exercise_progress/$workoutId/$exerciseId"
    }
    object ExerciseViewer : Destination("exercise_viewer/{exerciseId}") {
        fun createRoute(exerciseId: Long) = "exercise_viewer/$exerciseId"
    }
    object WorkoutBuilder : Destination("workout_builder?workoutId={workoutId}") {
        fun createRoute(workoutId: Long?) = "workout_builder?workoutId=${workoutId ?: -1}"
    }
    object SessionDetail : Destination("session_detail/{sessionId}") {
        fun createRoute(sessionId: Long) = "session_detail/$sessionId"
    }

    object Streaks : Destination("streaks")
    object Macro : Destination("macro")
    object Body : Destination("body")
    object BodyHistory : Destination("body_history")
    object BodyCompositionHistory : Destination("body_history_composition/{metric}") {
        fun createRoute(metric: CompositionMetric) = "body_history_composition/${metric.routeId}"
    }
    object StreakEditor : Destination("streak_editor")
    object StreakDetail : Destination("streak_detail/{streakId}") {
        fun createRoute(streakId: Long) = "streak_detail/$streakId"
    }
    object SavedDays : Destination("saved_days")
    object SavedDaysForStreak : Destination("saved_days_for_streak/{streakId}") {
        fun createRoute(streakId: Long) = "saved_days_for_streak/$streakId"
    }
    object SavedDayDetail : Destination("saved_day_detail/{streakId}/{date}") {
        fun createRoute(streakId: Long, date: String) = "saved_day_detail/$streakId/$date"
    }
}
