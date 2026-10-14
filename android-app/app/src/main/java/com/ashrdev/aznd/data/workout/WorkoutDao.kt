package com.ashrdev.aznd.data.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT workoutName, startedAt FROM sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun getMostRecentSession(): RecentWorkoutSession?
    
    @Insert
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    /** Save only the workout record for the new template-based builder and return its real id. */
    @Transaction
    suspend fun saveWorkoutRecord(id: Long?, name: String): Long {
        if (id == null) return insertWorkout(WorkoutEntity(name = name))
        updateWorkout(WorkoutEntity(id = id, name = name))
        deleteExercisesForWorkout(id)
        return id
    }

    @Query("SELECT DISTINCT workoutId, workoutName FROM sessions ORDER BY workoutName")
    fun getWorkoutHistorySummaries(): Flow<List<WorkoutHistorySummary>>

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY name")
    fun getWorkoutsWithExercises(): Flow<List<WorkoutWithExercises>>

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY name")
    suspend fun getAllWorkoutsWithExercisesOnce(): List<WorkoutWithExercises>

    @Transaction
    @Query("SELECT * FROM sessions WHERE workoutId = :workoutId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestSessionForWorkout(workoutId: Long): SessionWithSets?

    @Transaction
    @Query("SELECT * FROM sessions WHERE workoutId = :workoutId ORDER BY startedAt DESC")
    fun getSessionsForWorkout(workoutId: Long): Flow<List<SessionWithSets>>

    @Query("DELETE FROM sessions WHERE workoutId = :workoutId")
    suspend fun deleteSessionsForWorkout(workoutId: Long)

    @Query("""
        SELECT sessions.id AS sessionId, sessions.startedAt AS startedAt,
               logged_sets.value AS value, logged_sets.weight AS weight, logged_sets.rpe AS rpe
        FROM logged_sets
        INNER JOIN sessions ON logged_sets.sessionId = sessions.id
        WHERE logged_sets.exerciseName = :exerciseName
          AND sessions.workoutId = :workoutId
          AND logged_sets.mode = 'REPS'
        ORDER BY sessions.startedAt ASC
    """)
    suspend fun getExerciseHistory(workoutId: Long, exerciseName: String): List<ExerciseSetPoint>

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Insert
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Insert
    suspend fun insertExerciseSets(sets: List<ExerciseSetEntity>)

    @Query("DELETE FROM exercises WHERE workoutId = :workoutId")
    suspend fun deleteExercisesForWorkout(workoutId: Long)

    @Query("DELETE FROM workouts WHERE id = :workoutId")
    suspend fun deleteWorkout(workoutId: Long)

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun workoutCount(): Int

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    suspend fun getWorkoutWithExercises(workoutId: Long): WorkoutWithExercises?

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
    suspend fun saveWorkout(workout: WorkoutEntity, exercises: List<ExerciseWithSets>) {
        val workoutId = if (workout.id == 0L) {
            insertWorkout(workout)
        } else {
            updateWorkout(workout)
            deleteExercisesForWorkout(workout.id)
            workout.id
        }
        exercises.forEachIndexed { exIndex, ews ->
            val exId = insertExercise(ews.exercise.copy(workoutId = workoutId, orderIndex = exIndex))
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
                workoutId = active.session.workoutId,
                workoutName = active.session.workoutName,
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
