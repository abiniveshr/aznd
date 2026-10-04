package com.ashrdev.aznd.data.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT WorkoutName, startedAt FROM sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun getMostRecentSession(): RecentWorkoutSession?
    
    @Insert
    suspend fun insertWorkout(Workout: WorkoutEntity): Long

    /** Save only the workout record for the new template-based builder and return its real id. */
    @Transaction
    suspend fun saveWorkoutRecord(id: Long?, name: String): Long {
        if (id == null) return insertWorkout(WorkoutEntity(name = name))
        updateWorkout(WorkoutEntity(id = id, name = name))
        deleteExercisesForWorkout(id)
        return id
    }

    @Query("SELECT DISTINCT WorkoutId, WorkoutName FROM sessions ORDER BY WorkoutName")
    fun getWorkoutHistorySummaries(): Flow<List<WorkoutHistorySummary>>

    @Transaction
    @Query("SELECT * FROM Workouts ORDER BY name")
    fun getWorkoutsWithExercises(): Flow<List<WorkoutWithExercises>>

    @Transaction
    @Query("SELECT * FROM Workouts ORDER BY name")
    suspend fun getAllWorkoutsWithExercisesOnce(): List<WorkoutWithExercises>

    @Transaction
    @Query("SELECT * FROM sessions WHERE WorkoutId = :WorkoutId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestSessionForWorkout(WorkoutId: Long): SessionWithSets?

    @Transaction
    @Query("SELECT * FROM sessions WHERE WorkoutId = :WorkoutId ORDER BY startedAt DESC")
    fun getSessionsForWorkout(WorkoutId: Long): Flow<List<SessionWithSets>>

    @Query("DELETE FROM sessions WHERE WorkoutId = :WorkoutId")
    suspend fun deleteSessionsForWorkout(WorkoutId: Long)

    @Query("""
        SELECT sessions.id AS sessionId, sessions.startedAt AS startedAt,
               logged_sets.value AS value, logged_sets.weight AS weight, logged_sets.rpe AS rpe
        FROM logged_sets
        INNER JOIN sessions ON logged_sets.sessionId = sessions.id
        WHERE logged_sets.exerciseName = :exerciseName
          AND sessions.WorkoutId = :WorkoutId
          AND logged_sets.mode = 'REPS'
        ORDER BY sessions.startedAt ASC
    """)
    suspend fun getExerciseHistory(WorkoutId: Long, exerciseName: String): List<ExerciseSetPoint>

    @Update
    suspend fun updateWorkout(Workout: WorkoutEntity)

    @Insert
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Insert
    suspend fun insertExerciseSets(sets: List<ExerciseSetEntity>)

    @Query("DELETE FROM exercises WHERE WorkoutId = :WorkoutId")
    suspend fun deleteExercisesForWorkout(WorkoutId: Long)

    @Query("DELETE FROM Workouts WHERE id = :WorkoutId")
    suspend fun deleteWorkout(WorkoutId: Long)

    @Query("SELECT COUNT(*) FROM Workouts")
    suspend fun WorkoutCount(): Int

    @Transaction
    @Query("SELECT * FROM Workouts WHERE id = :WorkoutId")
    suspend fun getWorkoutWithExercises(WorkoutId: Long): WorkoutWithExercises?

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
    suspend fun saveWorkout(Workout: WorkoutEntity, exercises: List<ExerciseWithSets>) {
        val WorkoutId = if (Workout.id == 0L) {
            insertWorkout(Workout)
        } else {
            updateWorkout(Workout)
            deleteExercisesForWorkout(Workout.id)
            Workout.id
        }
        exercises.forEachIndexed { exIndex, ews ->
            val exId = insertExercise(ews.exercise.copy(WorkoutId = WorkoutId, orderIndex = exIndex))
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
                WorkoutId = active.session.WorkoutId,
                WorkoutName = active.session.WorkoutName,
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
