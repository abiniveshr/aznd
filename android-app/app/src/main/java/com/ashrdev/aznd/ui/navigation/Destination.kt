package com.ashrdev.aznd.ui.navigation

import com.ashrdev.aznd.data.manage.DataApp
import com.ashrdev.aznd.domain.workout.Muscle
import com.ashrdev.aznd.ui.manage.ManagePage
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
 *  |- Streaks (pager: Discipline | Streaks | New) ------- Streaks
 *  |     |- Streaks page: a streak ---------------------- StreakDetail (pager: Progress | Details | Calendar)
 *  |     |                                                   |- Details page: Saved days -- SavedDaysForStreak -- SavedDayDetail
 *  |     |                                                   '- Details page: Save today -- SavedDayDetail
 *  |     |- Discipline page: placeholder (level system to come)
 *  |     '- New page: create a streak in place (Dashboard's add button -> StreakEditor, same form)
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
 * Flex (share card maker): Share in the pull-up bar on Home, and the Share button of SessionDetail,
 * WorkoutDetail, ExerciseProgress and SavedDayDetail, each with its own preset.
 * Settings -> Appearance opens from the pull-up bar on every screen.
 * Manage data (Delete | Export | Import) opens from the pull-up bar on every screen: from Home,
 * Settings and Appearance it lists all data, from a screen inside an app it is pre-filtered to
 * that app (DataApp.fromRoute).
 */
sealed class Destination(val route: String) {
    object Home : Destination("home")
    object Settings : Destination("settings")
    object Appearance : Destination("appearance")
    object ManageData : Destination("manage_data?app={app}&page={page}") {
        fun createRoute(app: DataApp?, page: ManagePage = ManagePage.DELETE) =
            "manage_data?app=${app?.id ?: "all"}&page=${page.id}"
    }

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

    /**
     * Flex, the share-card maker. [preset] is a FlexPreset id; a / b are ids and s a text key whose meaning
     * depends on the preset (see FlexPreset). Callers use FlexPreset.route(...), which also encodes [s].
     */
    object Flex : Destination("flex/{preset}?a={a}&b={b}&s={s}") {
        fun createRoute(preset: String, a: Long = -1L, b: Long = -1L, s: String = "") =
            "flex/$preset?a=$a&b=$b&s=$s"
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
