package com.ashrdev.aznd.domain

/** What the calculations need to know about an exercise. */
data class ExerciseProfile(
    val type: ExerciseType,
    val bodyweightShare: Double = 1.0
)

/**
 * One logged set reduced to the metrics used in 1RM calculations.
 * ASSISTED rows carry a NEGATIVE [weightKg] (the assistance), so one load formula fits every type.
 */
data class SetEntry(
    val kind: SetKind = SetKind.NORMAL,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val rpe: Double? = null
)

/** The best estimate found, with the effective load and the set that produced it. */
data class E1rmResult(val e1rm: Double, val load: Double, val set: SetEntry)

enum class ProgressUnit {
    E1RM_KG,
    EXTRA_WEIGHT_E1RM_KG,
    TOTAL_LOAD_E1RM_KG,
    REPS,
    SECONDS,
    LOWEST_ASSIST_KG
}

/** [atWeightKg] is only set when the value was achieved at a specific extra weight. */
data class ProgressMetric(
    val value: Double,
    val unit: ProgressUnit,
    val atWeightKg: Double? = null
)

data class ProgressSummary(
    val primary: ProgressMetric,
    val secondary: ProgressMetric? = null
)

const val MAX_E1RM_REPS = 12
const val MAX_EFFECTIVE_REPS = 20.0

/**
 * e1RM and progress maths. Pure and computed at read time, never stored.
 * [rpeTracking] mirrors the "Track RPE" setting: when off, stored RPE is ignored (not deleted).
 */
class E1rmCalculator(
    val formula: E1rmFormula = DEFAULT_FORMULA,
    val rpeTracking: Boolean = true
) {

    /**
     * Effective load in kg. WEIGHTED = the weight. Both bodyweight types =
     * bodyweight x share + weight (assisted weights are negative). TIME_HELD = the extra weight.
     */
    fun load(profile: ExerciseProfile, bodyweightKg: Double, weightKg: Double?): Double {
        val extra = weightKg ?: 0.0
        return if (profile.type.isBodyweight) bodyweightKg * profile.bodyweightShare + extra else extra
    }

    /** reps + (10 - RPE) when RPE is tracked and present, otherwise reps. Capped at 20. */
    fun effectiveReps(reps: Int, rpe: Double?): Double {
        val r = if (rpeTracking && rpe != null) {
            reps + (10.0 - rpe.coerceIn(5.0, 10.0))
        } else {
            reps.toDouble()
        }
        return minOf(r, MAX_EFFECTIVE_REPS)
    }

    /** Not TIME_HELD, not DROP, reps 1..12, load > 0. */
    fun isEligible(profile: ExerciseProfile, bodyweightKg: Double, set: SetEntry): Boolean {
        if (profile.type == ExerciseType.TIME_HELD || set.kind == SetKind.DROP) return false
        val reps = set.reps ?: return false
        if (reps !in 1..MAX_E1RM_REPS) return false
        return load(profile, bodyweightKg, set.weightKg) > 0.0
    }

    /** e1RM of one set, or null if the set is not eligible. */
    fun e1rm(profile: ExerciseProfile, bodyweightKg: Double, set: SetEntry): Double? {
        if (!isEligible(profile, bodyweightKg, set)) return null
        val reps = set.reps ?: return null
        val l = load(profile, bodyweightKg, set.weightKg)
        return formula.estimate(l, effectiveReps(reps, set.rpe))
    }

    /** Best eligible e1RM among [sets] (first one wins a tie), or null if none qualifies. */
    fun best(profile: ExerciseProfile, bodyweightKg: Double, sets: List<SetEntry>): E1rmResult? =
        sets.mapNotNull { s ->
            e1rm(profile, bodyweightKg, s)?.let {
                E1rmResult(it, load(profile, bodyweightKg, s.weightKg), s)
            }
        }.maxByOrNull { it.e1rm }

    /**
     * One session's progress numbers for one exercise (DROP rows ignored), by type:
     *  - WEIGHTED: best e1RM.
     *  - BODYWEIGHT_REPS: no extra weight -> best reps (secondary: total-load e1RM);
     *    with extra weight -> extra-weight e1RM (total e1RM minus bodyweight x share),
     *    secondary: total-load e1RM.
     *  - ASSISTED_BODYWEIGHT: e1RM of total load, secondary: lowest assistance used.
     *  - TIME_HELD: best duration, secondary: best duration at the heaviest extra weight.
     * Returns null when the session has nothing usable.
     */
    fun progress(profile: ExerciseProfile, bodyweightKg: Double, sets: List<SetEntry>): ProgressSummary? {
        val counted = sets.filter { it.kind != SetKind.DROP }
        return when (profile.type) {
            ExerciseType.WEIGHTED -> best(profile, bodyweightKg, counted)?.let {
                ProgressSummary(ProgressMetric(it.e1rm, ProgressUnit.E1RM_KG))
            }
            ExerciseType.BODYWEIGHT_REPS -> bodyweightRepsProgress(profile, bodyweightKg, counted)
            ExerciseType.ASSISTED_BODYWEIGHT -> {
                val top = best(profile, bodyweightKg, counted) ?: return null
                val lowestAssist = counted
                    .filter { (it.reps ?: 0) > 0 && (it.weightKg ?: 0.0) < 0.0 }
                    .mapNotNull { s -> s.weightKg?.let { -it } }
                    .minOrNull()
                ProgressSummary(
                    ProgressMetric(top.e1rm, ProgressUnit.E1RM_KG),
                    lowestAssist?.let { ProgressMetric(it, ProgressUnit.LOWEST_ASSIST_KG) }
                )
            }
            ExerciseType.TIME_HELD -> timeHeldProgress(counted)
        }
    }

    private fun bodyweightRepsProgress(
        profile: ExerciseProfile,
        bodyweightKg: Double,
        sets: List<SetEntry>
    ): ProgressSummary? {
        val withReps = sets.filter { (it.reps ?: 0) > 0 }
        if (withReps.isEmpty()) return null
        val total = best(profile, bodyweightKg, sets)
            ?.let { ProgressMetric(it.e1rm, ProgressUnit.TOTAL_LOAD_E1RM_KG) }
        val bodyPart = bodyweightKg * profile.bodyweightShare
        val extra = sets
            .filter { (it.weightKg ?: 0.0) > 0.0 }
            .mapNotNull { e1rm(profile, bodyweightKg, it) }
            .maxOrNull()
            ?.let { it - bodyPart }
        if (extra != null) {
            return ProgressSummary(ProgressMetric(extra, ProgressUnit.EXTRA_WEIGHT_E1RM_KG), total)
        }
        val bestReps = withReps.maxOf { it.reps ?: 0 }
        return ProgressSummary(ProgressMetric(bestReps.toDouble(), ProgressUnit.REPS), total)
    }

    private fun timeHeldProgress(sets: List<SetEntry>): ProgressSummary? {
        val perWeight = bestDurationPerExtraWeight(sets)
        val bestDuration = perWeight.values.maxOrNull() ?: return null
        val heaviest = perWeight.keys.filter { it > 0.0 }.maxOrNull()
        val atHeaviest = heaviest?.let { w ->
            ProgressMetric(perWeight.getValue(w).toDouble(), ProgressUnit.SECONDS, atWeightKg = w)
        }
        return ProgressSummary(ProgressMetric(bestDuration.toDouble(), ProgressUnit.SECONDS), atHeaviest)
    }
}

/** Best hold (seconds) for each extra weight used (0.0 = no extra weight), lightest first. */
fun bestDurationPerExtraWeight(sets: List<SetEntry>): Map<Double, Int> =
    sets.filter { it.kind != SetKind.DROP && (it.durationSec ?: 0) > 0 }
        .groupBy { s -> s.weightKg?.takeIf { it > 0.0 } ?: 0.0 }
        .mapValues { (_, group) -> group.maxOf { it.durationSec ?: 0 } }
        .toSortedMap()
