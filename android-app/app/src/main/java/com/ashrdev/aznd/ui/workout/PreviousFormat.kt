package com.ashrdev.aznd.ui.workout

import com.ashrdev.aznd.domain.ExerciseType
import com.ashrdev.aznd.domain.SetRow
import com.ashrdev.aznd.domain.formatWeightKg
import com.ashrdev.aznd.domain.formatWeight as formatNumber
import kotlin.math.abs

/**
 * How a set of the previous session reads, e.g. "80 kg for 8 reps", "+10 kg for 12 reps",
 * "assist 20 kg for 10 reps", "45 s" (plus ", RPE 8" when [showRpe]). Null when that set had
 * nothing logged. Shown after "Previous:" on the workout view and the running workout.
 */
fun formatPreviousSet(type: ExerciseType, row: SetRow, showRpe: Boolean): String? {
    val main = when (type) {
        ExerciseType.TIME_HELD -> row.durationSec?.takeIf { it > 0 }?.let { "$it s" }
        else -> row.reps?.takeIf { it > 0 }?.let { if (it == 1) "1 rep" else "$it reps" }
    }
    val w = row.weightKg
    val weight = when (type) {
        ExerciseType.WEIGHTED -> w?.takeIf { it != 0.0 }?.let { formatWeightKg(it) }
        ExerciseType.BODYWEIGHT_REPS, ExerciseType.TIME_HELD -> w?.takeIf { it > 0.0 }?.let { "+${formatWeightKg(it)}" }
        ExerciseType.ASSISTED_BODYWEIGHT -> w?.takeIf { it != 0.0 }?.let { "assist ${formatWeightKg(abs(it))}" }
    }
    val core = when {
        main != null && weight != null -> "$weight for $main"
        main != null -> main
        weight != null -> weight
        else -> return null
    }
    val rpe = if (showRpe) row.rpe?.let { ", RPE ${formatNumber(it)}" }.orEmpty() else ""
    return core + rpe
}
