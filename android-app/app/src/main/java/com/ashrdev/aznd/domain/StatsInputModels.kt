package com.ashrdev.aznd.domain

/** One logged set of a FINISHED session, with that session's workout, times and bodyweight snapshot. */
data class LoggedSetData(
    val exerciseId: Long,
    val sessionId: Long,
    val workoutId: Long,
    val startedAt: Long,
    val finishedAt: Long,
    val bodyweightKg: Double,
    val kind: SetKind,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val rpe: Double?
) {
    /** A set only counts once it has reps or a held time (empty template rows don't). */
    val isLogged: Boolean get() = (reps ?: 0) > 0 || (durationSec ?: 0) > 0
}

/** The part of an exercise the statistics need (kept free of the Room entity). */
data class StatsExercise(
    val id: Long,
    val name: String,
    val type: ExerciseType,
    val bodyweightShare: Double,
    val primaryMuscle: Muscle,
    val secondaryMuscles: List<Muscle>
)
