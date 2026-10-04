package com.ashrdev.aznd.data.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ashrdev.aznd.domain.SetTree
import kotlinx.coroutines.flow.Flow

/** Data access object for catalog and custom exercise entities. */
@Dao
interface ExerciseDao {

    /** INSERT OR IGNORE: a row whose seedKey already exists is skipped and reports -1. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<Exercise>): List<Long>

    /** For custom exercises (Phase 5). */
    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Query("SELECT seedKey FROM catalog_exercise WHERE seedKey IS NOT NULL")
    suspend fun seedKeys(): List<String>

    @Query("SELECT * FROM catalog_exercise ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM catalog_exercise WHERE id = :id")
    suspend fun get(id: Long): Exercise?

    @Query("SELECT * FROM catalog_exercise WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Exercise>

    @Query("SELECT * FROM catalog_exercise WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Exercise?

    @Query("SELECT COUNT(*) FROM catalog_exercise")
    suspend fun count(): Int

    // ---------------------------------------------------------------------------------------
    // Editing / deleting custom exercises.
    // New tables (template_set, session_set) point at the exercise by id; the older history
    // tables that History / Stats / Dashboard read (exercises, logged_sets, active_sets) point
    // at it by NAME. Each operation below runs inside one transaction.
    // ---------------------------------------------------------------------------------------

    @Update
    suspend fun update(exercise: Exercise)

    @Query("SELECT (SELECT COUNT(*) FROM template_set WHERE exerciseId = :id) + (SELECT COUNT(*) FROM session_set WHERE exerciseId = :id)")
    suspend fun usageCount(id: Long): Int

    @Query("UPDATE exercises SET name = :new WHERE name = :old")
    suspend fun renameLegacyTemplateExercises(old: String, new: String)

    @Query("UPDATE logged_sets SET exerciseName = :new WHERE exerciseName = :old")
    suspend fun renameLegacyLoggedSets(old: String, new: String)

    @Query("UPDATE active_sets SET exerciseName = :new WHERE exerciseName = :old")
    suspend fun renameLegacyActiveSets(old: String, new: String)

    @Query("SELECT DISTINCT workoutId FROM template_set WHERE exerciseId = :id")
    suspend fun workoutsUsing(id: Long): List<Long>

    @Query("SELECT * FROM template_set WHERE workoutId = :workoutId")
    suspend fun templateSetsOf(workoutId: Long): List<TemplateSet>

    @Update
    suspend fun updateTemplateSet(row: TemplateSet)

    @Query("DELETE FROM template_set WHERE id = :id")
    suspend fun deleteTemplateSet(id: Long)

    @Query("SELECT DISTINCT sessionId FROM session_set WHERE exerciseId = :id")
    suspend fun sessionsUsing(id: Long): List<Long>

    @Query("SELECT * FROM session_set WHERE sessionId = :sessionId")
    suspend fun sessionSetsOf(sessionId: Long): List<LoggedSet>

    @Update
    suspend fun updateSessionSet(row: LoggedSet)

    @Query("DELETE FROM session_set WHERE id = :id")
    suspend fun deleteSessionSet(id: Long)

    @Query("SELECT legacySessionId FROM logged_session WHERE id = :id")
    suspend fun legacySessionIdOf(id: Long): Long?

    /** Also deletes the session's sets (foreign key cascade). */
    @Query("DELETE FROM logged_session WHERE id = :id")
    suspend fun deleteLoggedSession(id: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteLegacySession(id: Long)

    @Query("SELECT DISTINCT sessionId FROM logged_sets WHERE exerciseName = :name")
    suspend fun legacySessionsUsing(name: String): List<Long>

    @Query("DELETE FROM logged_sets WHERE exerciseName = :name")
    suspend fun deleteLegacyLoggedSets(name: String)

    @Query("DELETE FROM active_sets WHERE exerciseName = :name")
    suspend fun deleteLegacyActiveSets(name: String)

    /** Old-style template copy (one `exercises` row per distinct exercise per workout); its sets cascade. */
    @Query("DELETE FROM exercises WHERE name = :name")
    suspend fun deleteLegacyTemplateExercises(name: String)

    /** Old-style sessions that this delete left with no logged sets at all. */
    @Query("DELETE FROM sessions WHERE id IN (:ids) AND id NOT IN (SELECT sessionId FROM logged_sets)")
    suspend fun deleteEmptiedLegacySessions(ids: List<Long>)

    @Query("DELETE FROM catalog_exercise WHERE id = :id")
    suspend fun deleteExerciseRow(id: Long)

    /**
     * Saves the edited exercise. Same id, so workouts and logged sets follow automatically; the
     * old history tables match by name, so a rename is applied there too.
     */
    @Transaction
    suspend fun updateCustom(old: Exercise, edited: Exercise) {
        update(edited)
        if (old.name != edited.name) {
            renameLegacyTemplateExercises(old.name, edited.name)
            renameLegacyLoggedSets(old.name, edited.name)
            renameLegacyActiveSets(old.name, edited.name)
        }
    }

    /**
     * Removes the exercise as if it had never existed: its rows leave every workout template and
     * every logged session (a session with nothing left is deleted, history copy included), its
     * old-style history rows are removed, and finally the exercise itself. Superset children of
     * a removed row move up, exactly like the Delete button on a set row.
     */
    @Transaction
    suspend fun deleteCustomCascade(exercise: Exercise) {
        val id = exercise.id

        // 1) workout templates
        for (workoutId in workoutsUsing(id)) {
            val sets = templateSetsOf(workoutId)
            val rows = sets.map { it.toRow() }
            var tree = SetTree.ordered(rows)
            for (r in rows) if (r.exerciseId == id) tree = SetTree.delete(tree, r.id)
            val keep = tree.associateBy { it.id }
            for (s in sets) {
                val r = keep[s.id]
                if (r == null) deleteTemplateSet(s.id)
                else updateTemplateSet(s.copy(position = r.position, parentSetId = r.parentId, kind = r.kind))
            }
        }

        // 2) logged sessions (finished and running)
        for (sessionId in sessionsUsing(id)) {
            val sets = sessionSetsOf(sessionId)
            val rows = sets.map { it.toRow() }
            var tree = SetTree.ordered(rows)
            for (r in rows) if (r.exerciseId == id) tree = SetTree.delete(tree, r.id)
            if (tree.isEmpty()) {
                legacySessionIdOf(sessionId)?.let { deleteLegacySession(it) }
                deleteLoggedSession(sessionId)
            } else {
                val keep = tree.associateBy { it.id }
                for (s in sets) {
                    val r = keep[s.id]
                    if (r == null) deleteSessionSet(s.id)
                    else updateSessionSet(s.copy(position = r.position, parentSetId = r.parentId, kind = r.kind))
                }
            }
        }

        // 3) old-style history tables (matched by name)
        val name = exercise.name
        val legacySessions = legacySessionsUsing(name)
        deleteLegacyLoggedSets(name)
        if (legacySessions.isNotEmpty()) deleteEmptiedLegacySessions(legacySessions)
        deleteLegacyActiveSets(name)
        deleteLegacyTemplateExercises(name)

        // 4) the exercise itself
        deleteExerciseRow(id)
    }
}
