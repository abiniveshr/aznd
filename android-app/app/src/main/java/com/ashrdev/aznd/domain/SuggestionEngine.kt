package com.ashrdev.aznd.domain

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

/** Reps left in reserve when working out the target load (one rep short of failure). */
const val TARGET_RIR = 1

/** A suggested weight may never exceed this multiple of the source set's weight (WEIGHTED only). */
const val MAX_WEIGHT_JUMP = 1.1

/**
 * Sets from a completed session for the exercise being suggested.
 */
data class SessionHistory(
    val workoutId: Long,
    val finishedAt: Long,
    val bodyweightKg: Double,
    val sets: List<SetEntry>
)

/** The best e1RM found in history, and the weight of the set that produced it. */
data class SuggestionSource(val e1rm: Double, val sourceWeightKg: Double)

/** [fieldKg] is what to type into the weight field (assistance is positive for ASSISTED). */
data class Suggestion(val fieldKg: Double)

/** 1 kg steps below 20 kg, 2.5 kg steps from 20 kg up. */
fun stepFor(value: Double): Double = if (abs(value) < 20.0) 1.0 else 2.5

/** Round to the nearest step (halves round away from zero). */
fun roundToStep(value: Double): Double {
    val step = stepFor(value)
    return sign(value) * floor(abs(value) / step + 0.5) * step
}

/** 110% of the source weight, rounded down to the step, but never below the source weight. */
fun weightCap(sourceWeightKg: Double): Double {
    val raw = sourceWeightKg * MAX_WEIGHT_JUMP
    val step = stepFor(raw)
    return maxOf(floor(raw / step + 1e-9) * step, sourceWeightKg)
}

/**
 * Weight hints for rows that have a target. Never auto-fills; the UI shows a tappable hint.
 * Callers skip DROP rows, rows without a target, and everything when "Target reps" is off.
 */
class SuggestionEngine(private val calculator: E1rmCalculator) {

    /**
     * Best eligible e1RM from the most recent completed session of [workoutId] that has one;
     * otherwise from the most recent completed session of any workout; otherwise null.
     * (A session "contains" the exercise when it has at least one eligible set.)
     */
    fun findSource(
        profile: ExerciseProfile,
        workoutId: Long,
        history: List<SessionHistory>
    ): SuggestionSource? {
        val recentFirst = history.sortedByDescending { it.finishedAt }
        fun sourceFrom(candidates: List<SessionHistory>): SuggestionSource? {
            for (session in candidates) {
                val best = calculator.best(profile, session.bodyweightKg, session.sets) ?: continue
                return SuggestionSource(best.e1rm, best.set.weightKg ?: 0.0)
            }
            return null
        }
        return sourceFrom(recentFirst.filter { it.workoutId == workoutId }) ?: sourceFrom(recentFirst)
    }

    /**
     * Weight to try for a row whose target starts at [targetMin] reps (one number means min = max).
     * Target load = formula.loadFor(previous e1RM, targetMin + TARGET_RIR), converted to the
     * field for this exercise type and rounded to the step. Null means "show nothing".
     */
    fun suggest(
        profile: ExerciseProfile,
        targetMin: Int?,
        source: SuggestionSource?,
        bodyweightKg: Double
    ): Suggestion? {
        if (source == null || targetMin == null || targetMin < 1) return null
        if (profile.type == ExerciseType.TIME_HELD) return null
        val r = minOf((targetMin + TARGET_RIR).toDouble(), MAX_EFFECTIVE_REPS)
        val load = calculator.formula.loadFor(source.e1rm, r)
        val bodyPart = bodyweightKg * profile.bodyweightShare
        val raw = when (profile.type) {
            ExerciseType.WEIGHTED -> load
            ExerciseType.BODYWEIGHT_REPS -> maxOf(0.0, load - bodyPart)
            ExerciseType.ASSISTED_BODYWEIGHT -> -minOf(0.0, load - bodyPart)
            ExerciseType.TIME_HELD -> return null
        }
        var value = roundToStep(raw)
        if (profile.type == ExerciseType.WEIGHTED) value = minOf(value, weightCap(source.sourceWeightKg))
        if (profile.type != ExerciseType.ASSISTED_BODYWEIGHT && value <= 0.0) return null
        return Suggestion(value)
    }
}
