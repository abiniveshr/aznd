package com.ashrdev.aznd.ui.dashboard

import com.ashrdev.aznd.ui.common.share.FlexFont
import com.ashrdev.aznd.ui.common.tiling.TileNode
import com.ashrdev.aznd.ui.theme.BorderKind
import com.ashrdev.aznd.ui.theme.FillBase
import com.ashrdev.aznd.ui.theme.FillStyle
import com.ashrdev.aznd.ui.theme.GradientKind
import com.ashrdev.aznd.ui.theme.GradientSpec
import com.ashrdev.aznd.ui.theme.PatternSpec
import com.ashrdev.aznd.ui.theme.TaperLine

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
    ROUTINE("routine", "Workout routine"),
    WORKOUT_HISTORY("workout_history", "Workout history"),
    STREAKS("streaks", "Routines"),
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
    const val MAX_PRESETS = 20
    const val NAME_MAX = 30
    const val FIRST_PRESET_NAME = "Main"
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

/**
 * A saved dashboard: its widgets and, once arranged by hand, its tiling. The dashboard shows the
 * presets listed as its active pages, one above the other.
 */
data class DashPreset(
    val id: Int,
    val name: String,
    val widgets: List<DashWidget> = emptyList(),
    val layout: TileNode? = null
)

/** What a new preset starts with. */
enum class DashPresetStart(val label: String) {
    DEFAULTS("Default widgets"),
    EMPTY("Empty")
}

enum class DashBgMode(val id: String, val label: String) {
    THEME("theme", "App theme"),
    SOLID("solid", "Solid"),
    GRADIENT("gradient", "Gradient"),
    PHOTO("photo", "Photo");

    companion object {
        fun fromId(id: String?): DashBgMode = entries.firstOrNull { it.id == id } ?: THEME
    }
}

enum class DashTileStyle(val id: String, val label: String) {
    THEME("theme", "App theme"),
    FILLED("filled", "Filled"),
    OUTLINE("outline", "Outline"),
    GLASS("glass", "Glass"),
    NONE("none", "None");

    companion object {
        fun fromId(id: String?): DashTileStyle = entries.firstOrNull { it.id == id } ?: THEME
    }
}

/**
 * How every dashboard page looks: Flex's Look settings without its themes. The defaults follow the
 * app theme, so a dashboard nobody has styled looks exactly as before. Sizes are in dp.
 */
data class DashLook(
    val bgMode: DashBgMode = DashBgMode.THEME,
    val bgA: Int = 0xFF141B2D.toInt(),
    val bgB: Int = 0xFF3A2152.toInt(),
    val gradientAngle: Int = 135,
    /** An advanced gradient (any number of colours). Null = the two-colour one from [bgA], [bgB] and [gradientAngle]. */
    val bgGradient: GradientSpec? = null,
    val bgPatterns: List<PatternSpec> = emptyList(),
    val photoUri: String? = null,
    val photoBlur: Float = 0.3f,
    val photoDim: Float = 0.4f,
    /** Off: text, accent and tiles take the app theme's colours. */
    val customColors: Boolean = false,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val accent: Int = 0xFF8AB4FF.toInt(),
    val tileColor: Int = 0xFF1E2638.toInt(),
    val font: FlexFont = FlexFont.SANS,
    val textScale: Float = 1f,
    /** Every tile's content is drawn this much bigger or smaller (on top of each widget's own S / M / L). */
    val moduleScale: Float = 1f,
    val side: Float = 16f,
    val top: Float = 12f,
    val gap: Float = DashMetrics.GAP,
    val tiles: DashTileStyle = DashTileStyle.THEME,
    val tileAlpha: Float = 1f,
    val tileFill: FillStyle = FillStyle.Plain,
    val tileBorderKind: BorderKind = BorderKind.OUTLINE,
    /** 0 keeps each tile style's own edge (none, a thin outline, a glass rim). */
    val tileBorder: Float = 0f,
    val tileBorderColor: Int = 0xFF8AB4FF.toInt(),
    val tileRadius: Float = 16f,
    val lineTaper: TaperLine = TaperLine()
) {
    fun backgroundFill(): FillStyle = FillStyle(
        base = if (bgMode == DashBgMode.GRADIENT) FillBase.GRADIENT else FillBase.SOLID,
        gradient = bgGradient ?: GradientSpec.of(GradientKind.LINEAR, gradientAngle, bgA, bgB),
        patterns = bgPatterns
    )

    /** What changes the size of a tile's content, so its layout must be worked out again. */
    val sizing: String get() = "$moduleScale/$textScale/${font.name}/$gap"
}
