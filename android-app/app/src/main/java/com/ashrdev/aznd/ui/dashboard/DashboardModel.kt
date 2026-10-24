package com.ashrdev.aznd.ui.dashboard

enum class DashWidgetType(val id: String, val label: String, val blurb: String) {
    CALORIES("calories", "Calories", "Today's calories on a ring"),
    STREAK("streak", "Streak counter", "A running streak and its days"),
    LATEST_WORKOUT("latest_workout", "Latest workout", "Your last session at a glance"),
    GRAPH("graph", "Graph", "One or two variables over time"),
    BODY_MODEL("body_model", "3D model", "The body model for strength, tape or composition"),
    SHORTCUT("shortcut", "Shortcut", "Jump straight to a screen"),
    TOP_LIFT("top_lift", "Top lift", "One of your heaviest lifts by estimated 1RM");

    companion object {
        fun fromId(id: String?): DashWidgetType? = entries.firstOrNull { it.id == id }
    }
}

enum class DashSize(val id: String, val label: String, val factor: Float) {
    SMALL("s", "S", 0.85f),
    NORMAL("m", "M", 1f),
    LARGE("l", "L", 1.25f);

    companion object {
        fun fromId(id: String?): DashSize = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

enum class DashCalorieView(val id: String, val label: String) {
    NET("net", "Net"),
    MACROS("macros", "Macros");

    companion object {
        fun fromId(id: String?): DashCalorieView = entries.firstOrNull { it.id == id } ?: NET
    }
}

enum class DashModel(val id: String, val label: String) {
    STRENGTH("strength", "Strength"),
    MEASUREMENTS("measurements", "Measurements"),
    COMPOSITION("composition", "Composition");

    companion object {
        fun fromId(id: String?): DashModel = entries.firstOrNull { it.id == id } ?: STRENGTH
    }
}

enum class DashShortcut(val id: String, val label: String) {
    WORKOUTS("workouts", "Workouts"),
    ROUTINE("routine", "Routine"),
    WORKOUT_HISTORY("workout_history", "Workout history"),
    STREAKS("streaks", "Streaks"),
    NEW_STREAK("new_streak", "New streak"),
    SAVED_DAYS("saved_days", "Saved days"),
    MACROS("macros", "Macros"),
    BODY("body", "Body"),
    BODY_HISTORY("body_history", "Body history"),
    SHARE("share", "Flex"),
    SETTINGS("settings", "Settings"),
    MANAGE_DATA("manage_data", "Manage data");

    companion object {
        fun fromId(id: String?): DashShortcut = entries.firstOrNull { it.id == id } ?: WORKOUTS
    }
}

data class DashWidget(
    val id: Int,
    val type: DashWidgetType,
    val size: DashSize = DashSize.NORMAL,
    val rank: Int = 1,
    val streakId: Long = -1L,
    val variables: List<String> = emptyList(),
    val shortcut: DashShortcut = DashShortcut.WORKOUTS,
    val workoutId: Long = -1L,
    val calorieView: DashCalorieView = DashCalorieView.NET,
    val model: DashModel = DashModel.STRENGTH
)

object DashDefaults {
    const val MAX_WIDGETS = 12
    const val MAX_VARIABLES = 2
    const val MAX_RANK = 10
    const val FIRST_GRAPH_VARIABLE = "bodyweight"

    fun widgets(): List<DashWidget> = listOf(
        DashWidget(1, DashWidgetType.CALORIES),
        DashWidget(2, DashWidgetType.STREAK, rank = 1),
        DashWidget(3, DashWidgetType.STREAK, rank = 2),
        DashWidget(4, DashWidgetType.LATEST_WORKOUT)
    )

    fun fresh(type: DashWidgetType, existing: List<DashWidget>): DashWidget {
        val id = (existing.maxOfOrNull { it.id } ?: 0) + 1
        return when (type) {
            DashWidgetType.STREAK -> DashWidget(id, type, rank = (existing.count { it.type == type } + 1).coerceAtMost(MAX_RANK))
            DashWidgetType.TOP_LIFT -> DashWidget(id, type, rank = (existing.count { it.type == type } + 1).coerceAtMost(MAX_RANK))
            DashWidgetType.GRAPH -> DashWidget(id, type, variables = listOf(FIRST_GRAPH_VARIABLE))
            else -> DashWidget(id, type)
        }
    }
}
