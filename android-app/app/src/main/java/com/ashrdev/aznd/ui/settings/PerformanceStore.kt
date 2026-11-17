package com.ashrdev.aznd.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PatternMode { BAKED, LIVE, OFF }

enum class BodyModelMode { THREE_D, TWO_D }

data class PerformanceSettings(
    val patterns: PatternMode = PatternMode.BAKED,
    val bodyModel: BodyModelMode = BodyModelMode.THREE_D
)

object PerformanceStore {
    private const val PREFS = "aznd_performance"
    private const val KEY_PATTERNS = "patterns"
    private const val KEY_BODY_MODEL = "body_model"

    private var prefs: SharedPreferences? = null
    private val state = MutableStateFlow(PerformanceSettings())
    private var patternState by mutableStateOf(PatternMode.BAKED)
    private var bodyModelState by mutableStateOf(BodyModelMode.THREE_D)

    val settings: StateFlow<PerformanceSettings> = state.asStateFlow()

    val patternMode: PatternMode get() = patternState

    val bodyModel: BodyModelMode get() = bodyModelState

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val patterns = PatternMode.entries.firstOrNull { it.name == p.getString(KEY_PATTERNS, null) } ?: PatternMode.BAKED
        val body = BodyModelMode.entries.firstOrNull { it.name == p.getString(KEY_BODY_MODEL, null) } ?: BodyModelMode.THREE_D
        patternState = patterns
        bodyModelState = body
        state.value = PerformanceSettings(patterns, body)
    }

    @Synchronized
    fun setPatterns(mode: PatternMode) {
        patternState = mode
        state.value = state.value.copy(patterns = mode)
        prefs?.edit()?.putString(KEY_PATTERNS, mode.name)?.apply()
    }

    @Synchronized
    fun setBodyModel(mode: BodyModelMode) {
        bodyModelState = mode
        state.value = state.value.copy(bodyModel = mode)
        prefs?.edit()?.putString(KEY_BODY_MODEL, mode.name)?.apply()
    }
}
