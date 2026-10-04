package com.ashrdev.aznd.data.workout

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings -> Workout. Both switches default ON. Switching OFF only HIDES things (RPE field and
 * RPE maths / target fields and suggestions); stored RPE and targets are never deleted.
 */
data class WorkoutSettings(
    val trackRpe: Boolean = true,
    val targetReps: Boolean = true
)

/**
 * ASSUMPTION: the spec asks for Preferences DataStore only "if the app has no settings store".
 * The app already has one (SharedPreferences, same as ThemeStore), so this uses it and adds no
 * dependency. Create ONE instance (next to ThemeStore) and share it between screens/ViewModels.
 */
class WorkoutSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(
        WorkoutSettings(
            trackRpe = prefs.getBoolean(KEY_RPE, true),
            targetReps = prefs.getBoolean(KEY_TARGETS, true)
        )
    )
    val settings: StateFlow<WorkoutSettings> = state.asStateFlow()

    fun setTrackRpe(on: Boolean) {
        prefs.edit().putBoolean(KEY_RPE, on).apply()
        state.value = state.value.copy(trackRpe = on)
    }

    fun setTargetReps(on: Boolean) {
        prefs.edit().putBoolean(KEY_TARGETS, on).apply()
        state.value = state.value.copy(targetReps = on)
    }

    private companion object {
        const val FILE = "workout_settings"
        const val KEY_RPE = "track_rpe"
        const val KEY_TARGETS = "target_reps"
    }
}
