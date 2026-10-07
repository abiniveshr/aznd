package com.ashrdev.aznd.domain

/** Numbers of one finished session of one workout. */
data class SessionStat(
    val sessionId: Long,
    val startedAt: Long,
    val finishedAt: Long,
    val sets: Int,
    /** Sum of added weight x reps (bodyweight not counted). */
    val volumeKg: Double,
    /** Sum of total load x reps: bodyweight x share + added weight for bodyweight exercises. */
    val weightMovedKg: Double,
    /** Estimated, see CalorieCalculator. */
    val kcal: Double,
    val durationMin: Double
)

object SessionStatsCalculator {

    /** Finished sessions of [workoutId] that have at least one logged set, oldest first. */
    fun forWorkout(
        workoutId: Long,
        rows: List<LoggedSetData>,
        exercises: Map<Long, StatsExercise>,
        calculator: E1rmCalculator = E1rmCalculator()
    ): List<SessionStat> =
        rows.filter { it.workoutId == workoutId && it.isLogged && exercises.containsKey(it.exerciseId) }
            .groupBy { it.sessionId }
            .map { (sessionId, sets) ->
                val head = sets.first()
                var volume = 0.0
                var moved = 0.0
                for (r in sets) {
                    val ex = exercises.getValue(r.exerciseId)
                    val reps = r.reps ?: 0
                    if (reps > 0 && ex.type != ExerciseType.TIME_HELD) {
                        volume += (r.weightKg ?: 0.0).coerceAtLeast(0.0) * reps
                        val load = calculator.load(ExerciseProfile(ex.type, ex.bodyweightShare), r.bodyweightKg, r.weightKg)
                        moved += load.coerceAtLeast(0.0) * reps
                    }
                }
                val works = sets.groupBy { it.exerciseId }.map { (exerciseId, list) ->
                    val ex = exercises.getValue(exerciseId)
                    ExerciseWork(
                        ex.type,
                        ex.secondaryMuscles.size,
                        list.map { SetEntry(it.kind, it.weightKg, it.reps, it.durationSec, it.rpe) }
                    )
                }
                SessionStat(
                    sessionId = sessionId,
                    startedAt = head.startedAt,
                    finishedAt = head.finishedAt,
                    sets = sets.size,
                    volumeKg = volume,
                    weightMovedKg = moved,
                    kcal = CalorieCalculator.sessionKcal(works, head.bodyweightKg),
                    durationMin = ((head.finishedAt - head.startedAt) / 60_000.0).coerceAtLeast(0.0)
                )
            }
            .sortedBy { it.finishedAt }
}
