package com.ashrdev.aznd.domain

/** "UPPER_BACK" -> "Upper back". */
fun Muscle.displayName(): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/** What one finished session did for one muscle. */
data class MuscleSession(
    val sessionId: Long,
    val finishedAt: Long,
    /** Primary sets count 1.0 each, secondary sets 0.5 each. */
    val sets: Double,
    /** Sum of total load x reps (bodyweight x share + added weight for bodyweight exercises), scaled the same way. */
    val volumeKg: Double,
    /** Best estimated 1RM of that session among exercises where this muscle is the PRIMARY one. */
    val e1rmKg: Double?,
    /** How that best set was loaded, e.g. "BW 76 + 20 kg × 5 · Pull-Up". */
    val e1rmDetail: String?,
    /**
     * Bodyweight exercises only: the 1RM split into bodyweight and added part, e.g.
     * "BW 76 + 19 = 95 kg" (null for weighted exercises, which just show their number).
     */
    val e1rmDisplay: String? = null
)

/**
 * Pure maths behind the Muscle data screen. A set counts for a muscle when the muscle is the
 * exercise's primary (weight 1.0) or one of its secondaries (weight 0.5) and the set has reps or a
 * held time. Only finished sessions are used.
 */
object MuscleStatsCalculator {
    const val PRIMARY_WEIGHT = 1.0
    const val SECONDARY_WEIGHT = 0.5

    fun involvement(ex: StatsExercise, muscle: Muscle): Double = when {
        ex.primaryMuscle == muscle -> PRIMARY_WEIGHT
        muscle in ex.secondaryMuscles -> SECONDARY_WEIGHT
        else -> 0.0
    }

    /** One entry per finished session that trained [muscle], oldest first. */
    fun sessions(
        muscle: Muscle,
        rows: List<LoggedSetData>,
        exercises: Map<Long, StatsExercise>,
        calculator: E1rmCalculator = E1rmCalculator()
    ): List<MuscleSession> {
        val bySession = LinkedHashMap<Long, MutableList<Pair<LoggedSetData, StatsExercise>>>()
        for (r in rows) {
            if (!r.isLogged) continue
            val ex = exercises[r.exerciseId] ?: continue
            if (involvement(ex, muscle) <= 0.0) continue
            bySession.getOrPut(r.sessionId) { mutableListOf() }.add(r to ex)
        }
        return bySession.map { (sessionId, list) ->
            var sets = 0.0
            var volume = 0.0
            var bestE1rm: Double? = null
            var bestDetail: String? = null
            var bestDisplay: String? = null
            for ((r, ex) in list) {
                val w = involvement(ex, muscle)
                sets += w
                val profile = ExerciseProfile(ex.type, ex.bodyweightShare)
                val reps = r.reps ?: 0
                if (reps > 0 && ex.type != ExerciseType.TIME_HELD) {
                    val load = calculator.load(profile, r.bodyweightKg, r.weightKg)
                    if (load > 0.0) volume += load * reps * w
                }
                if (w == PRIMARY_WEIGHT) {
                    val e = calculator.e1rm(profile, r.bodyweightKg, SetEntry(r.kind, r.weightKg, r.reps, r.durationSec, r.rpe))
                    val current = bestE1rm
                    if (e != null && (current == null || e > current)) {
                        bestE1rm = e
                        bestDetail = liftDescription(ex, r)
                        bestDisplay = if (ex.type.isBodyweight) e1rmBreakdown(ex, r.bodyweightKg, e) else null
                    }
                }
            }
            MuscleSession(
                sessionId = sessionId,
                finishedAt = list.maxOf { it.first.finishedAt },
                sets = sets,
                volumeKg = volume,
                e1rmKg = bestE1rm,
                e1rmDetail = bestDetail,
                e1rmDisplay = bestDisplay
            )
        }.sortedBy { it.finishedAt }
    }

    private fun num(v: Double): String =
        if (kotlin.math.abs(v - Math.rint(v)) < 0.05) Math.rint(v).toLong().toString()
        else String.format(java.util.Locale.getDefault(), "%.1f", v)

    /** "BW 76 + 19 = 95 kg": the bodyweight moved plus the 1RM of what was added (minus for assisted). */
    fun e1rmBreakdown(ex: StatsExercise, bodyweightKg: Double, totalE1rmKg: Double): String {
        val bwPart = bodyweightKg * ex.bodyweightShare
        val added = totalE1rmKg - bwPart
        val sign = if (added >= 0.0) "+" else "−"
        return "BW ${num(bwPart)} $sign ${num(kotlin.math.abs(added))} = ${num(totalE1rmKg)} kg"
    }

    /**
     * Bodyweight exercises show the bodyweight that was actually moved PLUS the added weight
     * ("BW 76 + 20 kg × 5"); assisted ones subtract the assistance; weighted ones just the weight.
     */
    fun liftDescription(ex: StatsExercise, r: LoggedSetData): String {
        val reps = r.reps ?: 0
        val extra = r.weightKg ?: 0.0
        val load = if (ex.type.isBodyweight) {
            val bw = "BW ${num(r.bodyweightKg * ex.bodyweightShare)}"
            when {
                extra > 0.0 -> "$bw + ${num(extra)} kg"
                extra < 0.0 -> "$bw − ${num(-extra)} kg assist"
                else -> bw
            }
        } else {
            "${num(extra)} kg"
        }
        return "$load × $reps  ·  ${ex.name}"
    }
}
