package com.ashrdev.aznd.domain

import java.util.TimeZone

/** "UPPER_BACK" -> "Upper back". */
fun Muscle.displayName(): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Time window of the muscle statistics. [days] = null means everything ever logged. */
enum class StatsRange(val label: String, val days: Int?) {
    WEEK("7 days", 7),
    MONTH("30 days", 30),
    QUARTER("90 days", 90),
    ALL("All time", null)
}

/** One logged set of a FINISHED session, with that session's bodyweight snapshot. */
data class MuscleSetData(
    val exerciseId: Long,
    val sessionId: Long,
    val finishedAt: Long,
    val bodyweightKg: Double,
    val kind: SetKind,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val rpe: Double?
)

/** The part of an exercise the muscle statistics need (kept free of the Room entity). */
data class MuscleExercise(
    val id: Long,
    val name: String,
    val type: ExerciseType,
    val bodyweightShare: Double,
    val primaryMuscle: Muscle,
    val secondaryMuscles: List<Muscle>
)

data class ExerciseContribution(
    val exerciseId: Long,
    val name: String,
    /** Primary sets count 1.0 each, secondary sets 0.5 each. */
    val weightedSets: Double,
    val rawSets: Int,
    val isPrimary: Boolean
)

data class BestLift(
    val exerciseName: String,
    val e1rmKg: Double,
    val weightKg: Double?,
    val reps: Int,
    val at: Long
)

/** [weightedSets] logged in the 7 days starting at [startMs]. */
data class WeekBucket(val startMs: Long, val weightedSets: Double)

data class MuscleStats(
    val muscle: Muscle,
    val range: StatsRange,
    val weightedSets: Double,
    val setsPerWeek: Double,
    /** Sum of load x reps, each set scaled by how much it involves the muscle. Time-held sets excluded. */
    val volumeKg: Double,
    val weightedReps: Double,
    val heldSeconds: Double,
    val sessions: Int,
    val sessionsPerWeek: Double,
    /** Ignores the range: the last time this muscle was trained at all. */
    val lastTrainedAt: Long?,
    val daysSinceLast: Int?,
    /** Best estimated 1RM in the range among exercises where this muscle is the PRIMARY one. */
    val bestLift: BestLift?,
    val topExercises: List<ExerciseContribution>,
    /** The last [MuscleStatsCalculator.TREND_WEEKS] weeks, oldest first; ignores the range. */
    val weeks: List<WeekBucket>
) {
    val hasHistory: Boolean get() = lastTrainedAt != null
}

private class Hit(val row: MuscleSetData, val ex: MuscleExercise, val weight: Double)

/**
 * Pure maths behind the Muscle data screen. A set counts for a muscle when the muscle is the
 * exercise's primary (weight 1.0) or one of its secondaries (weight 0.5), and the set has reps or
 * a held time. Only finished sessions are used.
 */
object MuscleStatsCalculator {
    const val PRIMARY_WEIGHT = 1.0
    const val SECONDARY_WEIGHT = 0.5
    const val TREND_WEEKS = 8
    private const val DAY_MS = 86_400_000L
    private const val WEEK_MS = 7 * DAY_MS

    fun involvement(ex: MuscleExercise, muscle: Muscle): Double = when {
        ex.primaryMuscle == muscle -> PRIMARY_WEIGHT
        muscle in ex.secondaryMuscles -> SECONDARY_WEIGHT
        else -> 0.0
    }

    private fun isLogged(r: MuscleSetData): Boolean = (r.reps ?: 0) > 0 || (r.durationSec ?: 0) > 0

    fun compute(
        muscle: Muscle,
        rows: List<MuscleSetData>,
        exercises: Map<Long, MuscleExercise>,
        range: StatsRange,
        nowMs: Long,
        calculator: E1rmCalculator = E1rmCalculator(),
        utcOffsetMs: (Long) -> Long = { TimeZone.getDefault().getOffset(it).toLong() }
    ): MuscleStats {
        val hits = rows.mapNotNull { r ->
            val ex = exercises[r.exerciseId] ?: return@mapNotNull null
            val w = involvement(ex, muscle)
            if (w <= 0.0 || !isLogged(r)) null else Hit(r, ex, w)
        }
        val last = hits.maxOfOrNull { it.row.finishedAt }
        val first = hits.minOfOrNull { it.row.finishedAt }

        val days = range.days
        val startMs: Long? = if (days != null) nowMs - days * DAY_MS else first
        val inRange = hits.filter { startMs == null || it.row.finishedAt >= startMs }

        val spanDays: Double = when {
            days != null -> days.toDouble()
            first == null -> 7.0
            else -> ((nowMs - first) / DAY_MS.toDouble()).coerceAtLeast(7.0)
        }
        val weeks = spanDays / 7.0

        var sets = 0.0
        var volume = 0.0
        var reps = 0.0
        var held = 0.0
        var best: BestLift? = null
        val sessionIds = HashSet<Long>()
        val byExercise = LinkedHashMap<Long, MutableList<Hit>>()

        for (h in inRange) {
            val r = h.row
            sets += h.weight
            sessionIds.add(r.sessionId)
            byExercise.getOrPut(r.exerciseId) { mutableListOf() }.add(h)

            val profile = ExerciseProfile(h.ex.type, h.ex.bodyweightShare)
            val repCount = r.reps ?: 0
            if (repCount > 0 && h.ex.type != ExerciseType.TIME_HELD) {
                reps += repCount * h.weight
                val load = calculator.load(profile, r.bodyweightKg, r.weightKg)
                if (load > 0.0) volume += load * repCount * h.weight
            }
            val dur = r.durationSec ?: 0
            if (dur > 0) held += dur * h.weight

            if (h.weight == PRIMARY_WEIGHT) {
                val e = calculator.e1rm(
                    profile, r.bodyweightKg,
                    SetEntry(r.kind, r.weightKg, r.reps, r.durationSec, r.rpe)
                )
                val current = best
                if (e != null && (current == null || e > current.e1rmKg)) {
                    best = BestLift(h.ex.name, e, r.weightKg, repCount, r.finishedAt)
                }
            }
        }

        val top = byExercise.map { (id, list) ->
            val ex = list.first().ex
            ExerciseContribution(id, ex.name, list.sumOf { it.weight }, list.size, ex.primaryMuscle == muscle)
        }.sortedByDescending { it.weightedSets }.take(6)

        val trend = (0 until TREND_WEEKS).map { i ->
            val start = nowMs - (TREND_WEEKS - i) * WEEK_MS
            val isLastBucket = i == TREND_WEEKS - 1
            WeekBucket(
                start,
                hits.filter {
                    it.row.finishedAt >= start && (isLastBucket || it.row.finishedAt < start + WEEK_MS)
                }.sumOf { it.weight }
            )
        }

        fun dayIndex(ms: Long): Long = Math.floorDiv(ms + utcOffsetMs(ms), DAY_MS)
        val daysSince = last?.let { (dayIndex(nowMs) - dayIndex(it)).toInt().coerceAtLeast(0) }

        return MuscleStats(
            muscle = muscle,
            range = range,
            weightedSets = sets,
            setsPerWeek = sets / weeks,
            volumeKg = volume,
            weightedReps = reps,
            heldSeconds = held,
            sessions = sessionIds.size,
            sessionsPerWeek = sessionIds.size / weeks,
            lastTrainedAt = last,
            daysSinceLast = daysSince,
            bestLift = best,
            topExercises = top,
            weeks = trend
        )
    }
}
