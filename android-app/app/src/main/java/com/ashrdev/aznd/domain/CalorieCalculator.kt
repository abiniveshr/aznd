package com.ashrdev.aznd.domain

const val CALORIE_SECONDS_PER_REP = 3
const val CALORIE_REST_SECONDS = 90

/** All the logged sets of one exercise in a session. */
data class ExerciseWork(
    val type: ExerciseType,
    val secondaryMuscleCount: Int,
    val sets: List<SetEntry>
)

/**
 * Estimated calories. Label it "estimated" in the UI.
 *   kcal = MET x bodyweightKg x hours, hours = sum over logged sets of
 *   ((durationSec ?: reps x 3) + 90 s assumed rest) / 3600.
 * MET values are gross Compendium-style numbers (rest included), accurate to about +-25%.
 * Afterburn (EPOC) is ignored.
 */
object CalorieCalculator {

    fun met(type: ExerciseType, secondaryMuscleCount: Int): Double = when (type) {
        ExerciseType.WEIGHTED -> if (secondaryMuscleCount > 0) 5.0 else 3.5
        ExerciseType.BODYWEIGHT_REPS, ExerciseType.ASSISTED_BODYWEIGHT -> 3.8
        ExerciseType.TIME_HELD -> 3.0
    }

    /** Drop and superset rows count; sets with no logged reps or seconds do not. */
    fun exerciseKcal(work: ExerciseWork, bodyweightKg: Double): Double {
        val seconds = work.sets.sumOf { set ->
            workSeconds(set)?.let { it + CALORIE_REST_SECONDS } ?: 0.0
        }
        return met(work.type, work.secondaryMuscleCount) * bodyweightKg * seconds / 3600.0
    }

    fun sessionKcal(works: List<ExerciseWork>, bodyweightKg: Double): Double =
        works.sumOf { exerciseKcal(it, bodyweightKg) }

    private fun workSeconds(set: SetEntry): Double? {
        val duration = set.durationSec
        if (duration != null && duration > 0) return duration.toDouble()
        val reps = set.reps
        if (reps != null && reps > 0) return (reps * CALORIE_SECONDS_PER_REP).toDouble()
        return null
    }
}
