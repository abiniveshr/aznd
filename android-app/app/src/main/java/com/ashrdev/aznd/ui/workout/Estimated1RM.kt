package com.ashrdev.aznd.ui.workout

import com.ashrdev.aznd.data.workout.ExerciseSetPoint

data class SessionE1rmPoint(
    val sessionId: Long,
    val startedAt: Long,
    val e1rm: Double,
    val topSetWeight: Double,
    val topSetReps: Int,
    val topSetRpe: Double?
)

fun estimatedOneRepMax(weight: Double, reps: Int, rpe: Double?): Double? {
    if (weight <= 0.0 || reps <= 0) return null
    val effort = rpe ?: 10.0
    val rir = (10.0 - effort).coerceAtLeast(0.0)
    val effectiveReps = reps + rir
    return if (effectiveReps <= 10.0) {
        weight * 36.0 / (37.0 - effectiveReps)
    } else {
        weight * (1.0 + effectiveReps / 30.0)
    }
}

fun buildSessionE1rmPoints(raw: List<ExerciseSetPoint>): List<SessionE1rmPoint> =
    raw.groupBy { it.sessionId }
        .mapNotNull { (sessionId, sets) ->
            val startedAt = sets.first().startedAt
            val best = sets.mapNotNull { s ->
                estimatedOneRepMax(s.weight, s.value, s.rpe)?.let { it to s }
            }.maxByOrNull { it.first }
            best?.let { (e1rm, s) ->
                SessionE1rmPoint(
                    sessionId = sessionId,
                    startedAt = startedAt,
                    e1rm = e1rm,
                    topSetWeight = s.weight,
                    topSetReps = s.value,
                    topSetRpe = s.rpe
                )
            }
        }
        .sortedBy { it.startedAt }

fun formatTopSet(point: SessionE1rmPoint): String {
    val base = "${point.topSetReps} reps @ ${formatWeight(point.topSetWeight)}"
    return if (point.topSetRpe != null) "$base (RPE ${formatRpe(point.topSetRpe)})" else base
}

fun formatE1rm(value: Double): String =
    if (value % 1.0 == 0.0) "${value.toInt()} kg" else "%.1f kg".format(value)