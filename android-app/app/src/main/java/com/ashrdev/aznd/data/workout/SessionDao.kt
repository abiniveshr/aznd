package com.ashrdev.aznd.data.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ashrdev.aznd.domain.LoggedSetData
import com.ashrdev.aznd.domain.SetRow
import com.ashrdev.aznd.domain.SetTree
import kotlinx.coroutines.flow.Flow

/** Data access object for logged sessions and sets. */
@Dao
interface SessionDao {

    @Insert
    suspend fun insertSession(session: LoggedSession): Long

    @Insert
    suspend fun insertSet(row: LoggedSet): Long

    @Update
    suspend fun updateSet(row: LoggedSet)

    @Query("UPDATE logged_session SET finishedAt = :finishedAt WHERE id = :id")
    suspend fun setFinished(id: Long, finishedAt: Long)

    /** Also deletes the session's sets (foreign key cascade). */
    @Query("DELETE FROM logged_session WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("UPDATE logged_session SET legacySessionId = :legacyId WHERE id = :id")
    suspend fun setLegacySessionId(id: Long, legacyId: Long)

    /** The history screens deleted their copy of a session; remove ours too. */
    @Query("DELETE FROM logged_session WHERE legacySessionId = :legacyId")
    suspend fun deleteByLegacySession(legacyId: Long)

    /** "Delete history" of one workout: completed sessions only (an unfinished one is still running). */
    @Query("DELETE FROM logged_session WHERE workoutId = :workoutId AND finishedAt IS NOT NULL")
    suspend fun deleteCompletedForWorkout(workoutId: Long)

    /** A workout was deleted while a session of it was still running: that session can never be resumed. */
    @Query("DELETE FROM logged_session WHERE workoutId = :workoutId AND finishedAt IS NULL")
    suspend fun deleteUnfinishedForWorkout(workoutId: Long)

    @Query("DELETE FROM session_set WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("UPDATE session_set SET weightKg = :weightKg, reps = :reps, durationSec = :durationSec, rpe = :rpe WHERE id = :id")
    suspend fun updateValues(id: Long, weightKg: Double?, reps: Int?, durationSec: Int?, rpe: Double?)

    @Query("SELECT * FROM logged_session WHERE id = :id")
    suspend fun getSession(id: Long): LoggedSession?

    /** The unfinished session, if any. */
    @Query("SELECT * FROM logged_session WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<LoggedSession?>

    @Query("SELECT * FROM session_set WHERE sessionId = :sessionId")
    suspend fun setsFor(sessionId: Long): List<LoggedSet>

    @Query("SELECT * FROM session_set WHERE sessionId = :sessionId")
    fun observeSets(sessionId: Long): Flow<List<LoggedSet>>

    @Query("SELECT * FROM logged_session WHERE workoutId = :workoutId AND finishedAt IS NOT NULL ORDER BY finishedAt DESC LIMIT 1")
    suspend fun lastCompletedForWorkout(workoutId: Long): LoggedSession?

    @Query(
        "SELECT DISTINCT ls.* FROM logged_session ls INNER JOIN session_set ss ON ss.sessionId = ls.id " +
            "WHERE ls.finishedAt IS NOT NULL AND ss.exerciseId = :exerciseId " +
            "ORDER BY ls.finishedAt DESC LIMIT :limit"
    )
    suspend fun completedWithExercise(exerciseId: Long, limit: Int): List<LoggedSession>

    @Query(
        "SELECT DISTINCT ls.* FROM logged_session ls INNER JOIN session_set ss ON ss.sessionId = ls.id " +
            "WHERE ls.finishedAt IS NOT NULL AND ss.exerciseId = :exerciseId AND ls.workoutId = :workoutId " +
            "ORDER BY ls.finishedAt DESC LIMIT :limit"
    )
    suspend fun completedWithExerciseForWorkout(exerciseId: Long, workoutId: Long, limit: Int): List<LoggedSession>

    @Query("SELECT * FROM session_set WHERE exerciseId = :exerciseId AND sessionId IN (:sessionIds)")
    suspend fun setsForExercise(exerciseId: Long, sessionIds: List<Long>): List<LoggedSet>

    /** Every set of every FINISHED session plus the session's workout, times and bodyweight, for the statistics graphs. */
    @Query(
        "SELECT ss.exerciseId AS exerciseId, ss.sessionId AS sessionId, ls.workoutId AS workoutId, " +
            "ls.startedAt AS startedAt, ls.finishedAt AS finishedAt, " +
            "ls.bodyweightKgSnapshot AS bodyweightKg, ss.kind AS kind, ss.weightKg AS weightKg, " +
            "ss.reps AS reps, ss.durationSec AS durationSec, ss.rpe AS rpe " +
            "FROM session_set ss INNER JOIN logged_session ls ON ls.id = ss.sessionId " +
            "WHERE ls.finishedAt IS NOT NULL"
    )
    suspend fun completedSetData(): List<LoggedSetData>

    /**
     * Create a session from a workout's template rows: bodyweight snapshot taken now, empty
     * values, targets copied. Rows with no exercise are skipped. Returns null if nothing is left.
     */
    @Transaction
    suspend fun startSession(workoutId: Long, bodyweightKg: Double, startedAt: Long, template: List<SetRow>): Long? {
        val rows = SetTree.dropUnsetRows(template)
        if (rows.isEmpty()) return null
        val sessionId = insertSession(
            LoggedSession(workoutId = workoutId, startedAt = startedAt, bodyweightKgSnapshot = bodyweightKg)
        )
        val ids = HashMap<Long, Long>()
        for (r in rows) {
            val exerciseId = r.exerciseId ?: continue
            val empty = r.copy(weightKg = null, reps = null, durationSec = null, rpe = null)
            ids[r.id] = insertSet(empty.toLoggedSet(sessionId, exerciseId, r.parentId?.let { ids[it] }))
        }
        return sessionId
    }

    /**
     * Make the session's rows equal [desired] (the logger's list after Duplicate / Delete / Drop /
     * Superset / typing). Rows that already exist keep their ids, new rows (temporary ids) are
     * inserted, rows that are gone are deleted. Returns old-or-temporary id -> real id.
     */
    @Transaction
    suspend fun syncRows(sessionId: Long, desired: List<SetRow>): Map<Long, Long> {
        val existing = setsFor(sessionId).map { it.id }.toSet()
        val idMap = HashMap<Long, Long>()
        for (r in SetTree.dropUnsetRows(desired)) {
            val exerciseId = r.exerciseId ?: continue
            val parent = r.parentId?.let { idMap[it] }
            if (r.id in existing) {
                updateSet(r.toLoggedSet(sessionId, exerciseId, parent, id = r.id))
                idMap[r.id] = r.id
            } else {
                idMap[r.id] = insertSet(r.toLoggedSet(sessionId, exerciseId, parent))
            }
        }
        val kept = idMap.values.toSet()
        existing.filter { it !in kept }.forEach { deleteSet(it) }
        return idMap
    }
}
