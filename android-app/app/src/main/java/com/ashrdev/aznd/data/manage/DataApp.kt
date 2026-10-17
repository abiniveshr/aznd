package com.ashrdev.aznd.data.manage

enum class DataApp(
    val id: String,
    val label: String,
    private val routePrefixes: List<String>
) {
    WORKOUTS(
        "workouts", "Workouts",
        listOf("workout", "custom_exercise", "muscle_stats", "exercise_", "session_detail")
    ),
    STREAKS("streaks", "Streaks", listOf("streak", "saved_day")),
    BODY("body", "Body", listOf("body")),
    MACROS("macros", "Macros", listOf("macro"));

    // FUTURE: a new app (e.g. MEALS("meals", "Meals", listOf("meal"))) gets one entry here. Its
    // routePrefixes decide which screens open Manage data pre-filtered to it.

    companion object {
        fun fromId(id: String?): DataApp? = entries.firstOrNull { it.id == id }

        fun fromRoute(route: String?): DataApp? {
            if (route == null) return null
            return entries.firstOrNull { app -> app.routePrefixes.any { route.startsWith(it) } }
        }
    }
}
