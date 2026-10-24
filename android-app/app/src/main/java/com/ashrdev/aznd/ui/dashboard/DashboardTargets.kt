package com.ashrdev.aznd.ui.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector
import com.ashrdev.aznd.ui.common.share.FlexPreset
import com.ashrdev.aznd.ui.common.share.route
import com.ashrdev.aznd.ui.navigation.Destination

object DashboardTargets {

    fun icon(type: DashWidgetType): ImageVector = when (type) {
        DashWidgetType.CALORIES -> Icons.Default.DonutLarge
        DashWidgetType.STREAK -> Icons.Default.LocalFireDepartment
        DashWidgetType.LATEST_WORKOUT -> Icons.Default.FitnessCenter
        DashWidgetType.GRAPH -> Icons.Default.ShowChart
        DashWidgetType.BODY_MODEL -> Icons.Default.Accessibility
        DashWidgetType.SHORTCUT -> Icons.Default.TouchApp
        DashWidgetType.TOP_LIFT -> Icons.Default.EmojiEvents
    }

    fun icon(shortcut: DashShortcut): ImageVector = when (shortcut) {
        DashShortcut.WORKOUTS -> Icons.Default.FitnessCenter
        DashShortcut.ROUTINE -> Icons.Default.PlayArrow
        DashShortcut.WORKOUT_HISTORY -> Icons.Default.History
        DashShortcut.STREAKS -> Icons.Default.LocalFireDepartment
        DashShortcut.NEW_STREAK -> Icons.Default.AddCircle
        DashShortcut.SAVED_DAYS -> Icons.Default.Bookmark
        DashShortcut.MACROS -> Icons.Default.Restaurant
        DashShortcut.BODY -> Icons.Default.Straighten
        DashShortcut.BODY_HISTORY -> Icons.Default.Timeline
        DashShortcut.SHARE -> Icons.Default.Share
        DashShortcut.SETTINGS -> Icons.Default.Settings
        DashShortcut.MANAGE_DATA -> Icons.Default.Storage
    }

    fun bodyPage(model: DashModel): Int = when (model) {
        DashModel.MEASUREMENTS -> 0
        DashModel.STRENGTH -> 1
        DashModel.COMPOSITION -> 2
    }

    fun route(w: DashWidget, data: DashboardData): String = when (w.type) {
        DashWidgetType.CALORIES -> Destination.Macro.route
        DashWidgetType.STREAK -> data.streakFor(w)?.let { Destination.StreakDetail.createRoute(it.streak.id) }
            ?: if (w.streakId >= 0) Destination.Streaks.route else Destination.StreakEditor.route
        DashWidgetType.LATEST_WORKOUT -> data.sessions.firstOrNull()?.let { Destination.SessionDetail.createRoute(it.session.id) }
            ?: Destination.Workouts.route
        DashWidgetType.GRAPH -> Destination.DashboardGraph.createRoute(w.id)
        DashWidgetType.BODY_MODEL -> Destination.BodyPage.createRoute(bodyPage(w.model))
        DashWidgetType.SHORTCUT -> shortcutRoute(w, data)
        DashWidgetType.TOP_LIFT -> data.liftFor(w)?.let { Destination.ExerciseProgress.createRoute(it.workoutId, it.exerciseId) }
            ?: Destination.Workouts.route
    }

    private fun shortcutRoute(w: DashWidget, data: DashboardData): String = when (w.shortcut) {
        DashShortcut.WORKOUTS -> Destination.Workouts.route
        DashShortcut.ROUTINE -> if (data.workoutName(w.workoutId) != null) Destination.WorkoutDetail.createRoute(w.workoutId) else Destination.Workouts.route
        DashShortcut.WORKOUT_HISTORY -> Destination.WorkoutHistoryList.route
        DashShortcut.STREAKS -> Destination.Streaks.route
        DashShortcut.NEW_STREAK -> Destination.StreakEditor.route
        DashShortcut.SAVED_DAYS -> Destination.SavedDays.route
        DashShortcut.MACROS -> Destination.Macro.route
        DashShortcut.BODY -> Destination.Body.route
        DashShortcut.BODY_HISTORY -> Destination.BodyHistory.route
        DashShortcut.SHARE -> FlexPreset.HOME.route()
        DashShortcut.SETTINGS -> Destination.Settings.route
        DashShortcut.MANAGE_DATA -> Destination.ManageData.createRoute(null)
    }
}
