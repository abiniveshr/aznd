package com.ashrdev.aznd.data.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Query("SELECT DISTINCT routineId, routineName FROM sessions ORDER BY routineName")
    fun getWorkoutHistorySummaries(): Flow<List<WorkoutHistorySummary>>

    @Transaction
    @Query("SELECT * FROM routines ORDER BY name")
    fun getRoutinesWithExercises(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routines ORDER BY name")
    suspend fun getAllRoutinesWithExercisesOnce(): List<RoutineWithExercises>

    @Transaction
    @Query("SELECT * FROM sessions WHERE routineId = :routineId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestSessionForRoutine(routineId: Long): SessionWithSets?

    @Transaction
    @Query("SELECT * FROM sessions WHERE routineId = :routineId ORDER BY startedAt DESC")
    fun getSessionsForRoutine(routineId: Long): Flow<List<SessionWithSets>>

    @Query("DELETE FROM sessions WHERE routineId = :routineId")
    suspend fun deleteSessionsForRoutine(routineId: Long)

    @Query("""
        SELECT sessions.id AS sessionId, sessions.startedAt AS startedAt,
               logged_sets.value AS value, logged_sets.weight AS weight, logged_sets.rpe AS rpe
        FROM logged_sets
        INNER JOIN sessions ON logged_sets.sessionId = sessions.id
        WHERE logged_sets.exerciseName = :exerciseName
          AND sessions.routineId = :routineId
          AND logged_sets.mode = 'REPS'
        ORDER BY sessions.startedAt ASC
    """)
    suspend fun getExerciseHistory(routineId: Long, exerciseName: String): List<ExerciseSetPoint>

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Insert
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Insert
    suspend fun insertExerciseSets(sets: List<ExerciseSetEntity>)

    @Query("DELETE FROM exercises WHERE routineId = :routineId")
    suspend fun deleteExercisesForRoutine(routineId: Long)

    @Query("DELETE FROM routines WHERE id = :routineId")
    suspend fun deleteRoutine(routineId: Long)

    @Query("SELECT COUNT(*) FROM routines")
    suspend fun routineCount(): Int

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :routineId")
    suspend fun getRoutineWithExercises(routineId: Long): RoutineWithExercises?

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert
    suspend fun insertLoggedSets(sets: List<LoggedSetEntity>)

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun getSessions(): Flow<List<SessionWithSets>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): SessionWithSets?

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("SELECT * FROM logged_sets WHERE exerciseName = :name ORDER BY id DESC LIMIT :limit")
    suspend fun getRecentSets(name: String, limit: Int): List<LoggedSetEntity>

    @Transaction
    suspend fun saveRoutine(routine: RoutineEntity, exercises: List<ExerciseWithSets>) {
        val routineId = if (routine.id == 0L) {
            insertRoutine(routine)
        } else {
            updateRoutine(routine)
            deleteExercisesForRoutine(routine.id)
            routine.id
        }
        exercises.forEachIndexed { exIndex, ews ->
            val exId = insertExercise(ews.exercise.copy(routineId = routineId, orderIndex = exIndex))
            val setEntities = ews.sets.mapIndexed { setIndex, set ->
                set.copy(exerciseId = exId, orderIndex = setIndex)
            }
            insertExerciseSets(setEntities)
        }
    }

    @Transaction
    @Query("SELECT * FROM active_session")
    fun observeActiveSession(): Flow<List<ActiveSessionWithSets>>

    @Transaction
    @Query("SELECT * FROM active_session WHERE id = 1")
    suspend fun getActiveSessionOnce(): ActiveSessionWithSets?

    @Insert
    suspend fun insertActiveSession(session: ActiveSessionEntity)

    @Insert
    suspend fun insertActiveSets(sets: List<ActiveSetEntity>)

    @Query("DELETE FROM active_session")
    suspend fun clearActiveSession()

    @Transaction
    suspend fun startActiveSession(session: ActiveSessionEntity, sets: List<ActiveSetEntity>) {
        clearActiveSession()
        insertActiveSession(session)
        insertActiveSets(sets)
    }

    @Update
    suspend fun updateActiveSet(set: ActiveSetEntity)

    @Query("UPDATE active_session SET photoUri = :uri WHERE id = 1")
    suspend fun setActivePhoto(uri: String?)

    @Transaction
    suspend fun finishActiveSession(): Long? {
        val active = getActiveSessionOnce() ?: return null
        val sessionId = insertSession(
            SessionEntity(
                routineId = active.session.routineId,
                routineName = active.session.routineName,
                startedAt = active.session.startedAt,
                finishedAt = System.currentTimeMillis(),
                photoUri = active.session.photoUri
            )
        )
        val loggedSets = active.sets.mapIndexed { index, activeSet ->
            val valInt = activeSet.valueText.toIntOrNull() ?: 0
            val weightDbl = activeSet.weightText.toDoubleOrNull() ?: 0.0
            val rpeDbl = activeSet.rpeText.toDoubleOrNull()
            LoggedSetEntity(
                sessionId = sessionId,
                exerciseName = activeSet.exerciseName,
                mode = activeSet.mode,
                value = valInt,
                weight = weightDbl,
                rpe = rpeDbl,
                orderIndex = index
            )
        }
        insertLoggedSets(loggedSets)
        clearActiveSession()
        return sessionId
    }
}
