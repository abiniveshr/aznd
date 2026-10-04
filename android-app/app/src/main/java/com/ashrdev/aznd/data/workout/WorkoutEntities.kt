package com.ashrdev.aznd.data.workout

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ashrdev.aznd.domain.SetKind
import com.ashrdev.aznd.domain.SetRow

/** One row of a workout's template (what the builder edits). One flat table, no per-exercise table. */
@Entity(tableName = "template_set", indices = [Index("workoutId"), Index("exerciseId")])
data class TemplateSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ID of the associated workout record. */
    val workoutId: Long,
    /** Order among siblings with the same parent. */
    val position: Int,
    /** Null = no exercise chosen yet; such rows are dropped on save. */
    val exerciseId: Long?,
    val kind: SetKind,
    val parentSetId: Long?,
    val targetMin: Int?,
    val targetMax: Int?
)

@Entity(tableName = "logged_session", indices = [Index("workoutId"), Index("finishedAt"), Index("legacySessionId")])
data class LoggedSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ID of the associated workout record. */
    val workoutId: Long,
    val startedAt: Long,
    /** Set only by the Finish button; null means unfinished. */
    val finishedAt: Long? = null,
    /** Taken from BodyweightProvider when the session starts. */
    val bodyweightKgSnapshot: Double,
    /**
     * Id of the matching row in the history tables (`sessions`) that the History / Stats / Dashboard
     * screens read. Set when the session is finished; deleting that history row deletes this one too.
     */
    val legacySessionId: Long? = null
)

/** One row of a session. Assisted rows store NEGATIVE weightKg. */
@Entity(
    tableName = "session_set",
    foreignKeys = [
        ForeignKey(
            entity = LoggedSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("exerciseId")]
)
data class LoggedSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val position: Int,
    val exerciseId: Long,
    val kind: SetKind,
    val parentSetId: Long?,
    val targetMin: Int?,
    val targetMax: Int?,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val rpe: Double? = null
)

fun TemplateSet.toRow(): SetRow = SetRow(
    id = id, position = position, exerciseId = exerciseId, kind = kind, parentId = parentSetId,
    targetMin = targetMin, targetMax = targetMax
)

fun LoggedSet.toRow(): SetRow = SetRow(
    id = id, position = position, exerciseId = exerciseId, kind = kind, parentId = parentSetId,
    targetMin = targetMin, targetMax = targetMax,
    weightKg = weightKg, reps = reps, durationSec = durationSec, rpe = rpe
)

internal fun SetRow.toLoggedSet(sessionId: Long, exerciseId: Long, parentSetId: Long?, id: Long = 0): LoggedSet =
    LoggedSet(
        id = id, sessionId = sessionId, position = position, exerciseId = exerciseId, kind = kind,
        parentSetId = parentSetId, targetMin = targetMin, targetMax = targetMax,
        weightKg = weightKg, reps = reps, durationSec = durationSec, rpe = rpe
    )
