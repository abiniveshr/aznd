package com.ashrdev.aznd.data.workout

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import com.ashrdev.aznd.data.workout.RoutineEntity
import com.ashrdev.aznd.data.workout.ExerciseEntity
import com.ashrdev.aznd.data.workout.ExerciseSetEntity
import com.ashrdev.aznd.data.workout.SessionEntity
import com.ashrdev.aznd.data.workout.LoggedSetEntity
import com.ashrdev.aznd.data.workout.ActiveSessionEntity
import com.ashrdev.aznd.data.workout.ActiveSetEntity
import com.ashrdev.aznd.data.workout.Converters
import com.ashrdev.aznd.data.workout.WorkoutHistorySummary
import com.ashrdev.aznd.data.workout.SessionWithSets
import com.ashrdev.aznd.data.workout.ExerciseSetPoint
import com.ashrdev.aznd.data.workout.RoutineWithExercises
import com.ashrdev.aznd.data.workout.ActiveSessionWithSets
import com.ashrdev.aznd.data.workout.ExerciseWithSets
import com.ashrdev.aznd.data.workout.SetMode

@Dao
interface RoutineDao {
    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long
    
    @Query("SELECT DISTINCT routineId, routineName FROM sessions ORDER BY routineName")
    fun getWorkoutHistorySummaries(): Flow<List<WorkoutHistorySummary>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE routineId = :routineId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestSessionForRoutine(routineId: Long): SessionWithSets?
    
    @Transaction
    @Query("SELECT * FROM sessions WHERE routineId = :routineId ORDER BY startedAt DESC")
    fun getSessionsForRoutine(routineId: Long): Flow<List<SessionWithSets>>

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
    @Query("SELECT * FROM routines ORDER BY name")
    fun getRoutinesWithExercises(): Flow<List<RoutineWithExercises>>

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
    @Query("SELECT * FROM active_session LIMIT 1")
    fun observeActiveSession(): Flow<List<ActiveSessionWithSets>>

    @Transaction
    @Query("SELECT * FROM active_session LIMIT 1")
    suspend fun getActiveSession(): ActiveSessionWithSets?

    @Insert
    suspend fun insertActiveSession(session: ActiveSessionEntity)

    @Insert
    suspend fun insertActiveSets(sets: List<ActiveSetEntity>)

    @Update
    suspend fun updateActiveSet(set: ActiveSetEntity)

    @Query("UPDATE active_session SET photoUri = :uri WHERE id = 1")
    suspend fun setActivePhoto(uri: String?)

    @Query("DELETE FROM active_session")
    suspend fun clearActiveSession()

    @Transaction
    suspend fun saveRoutine(routine: RoutineEntity, exercises: List<ExerciseWithSets>): Long {
        val routineId = if (routine.id == 0L) insertRoutine(routine) else {
            updateRoutine(routine)
            routine.id
        }
        deleteExercisesForRoutine(routineId)
        exercises.forEachIndexed { exIndex, ews ->
            val exerciseId = insertExercise(
                ews.exercise.copy(id = 0, routineId = routineId, orderIndex = exIndex)
            )
            insertExerciseSets(
                ews.sets.mapIndexed { i, s ->
                    s.copy(id = 0, exerciseId = exerciseId, orderIndex = i)
                }
            )
        }
        return routineId
    }

    @Transaction
    suspend fun startActiveSession(session: ActiveSessionEntity, sets: List<ActiveSetEntity>) {
        clearActiveSession()
        insertActiveSession(session)
        insertActiveSets(sets)
    }

    @Transaction
    suspend fun finishActiveSession(): Long? {
        val active = getActiveSession() ?: return null
        val sessionId = insertSession(
            SessionEntity(
                routineId = active.session.routineId,
                routineName = active.session.routineName,
                startedAt = active.session.startedAt,
                finishedAt = System.currentTimeMillis(),
                photoUri = active.session.photoUri
            )
        )
        val ordered = active.sets.sortedWith(compareBy({ it.exerciseIndex }, { it.setIndex }))
        insertLoggedSets(
            ordered.mapIndexed { i, s ->
                LoggedSetEntity(
                    sessionId = sessionId,
                    exerciseName = s.exerciseName,
                    mode = s.mode,
                    value = s.valueText.toIntOrNull() ?: 0,
                    weight = s.weightText.toDoubleOrNull() ?: 0.0,
                    rpe = s.rpeText.toDoubleOrNull()?.coerceIn(0.0, 10.0),
                    orderIndex = i
                )
            }
        )
        clearActiveSession()
        return sessionId
    }
}

@Database(
    entities = [
        RoutineEntity::class,
        ExerciseEntity::class,
        ExerciseSetEntity::class,
        SessionEntity::class,
        LoggedSetEntity::class,
        ActiveSessionEntity::class,
        ActiveSetEntity::class
    ],
    version = 7
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aznd.db"
                ).fallbackToDestructiveMigration(true)
                    .build()
                    .also { db ->
                        INSTANCE = db
                        CoroutineScope(Dispatchers.IO).launch {
                            if (db.routineDao().routineCount() == 0) {
                                seedSampleRoutine(db.routineDao())
                            }
                        }
                    }
            }
        }

        private suspend fun seedSampleRoutine(dao: RoutineDao) {
            dao.saveRoutine(
                RoutineEntity(name = "Workout 1"),
                listOf(
                    ExerciseWithSets(
                        exercise = ExerciseEntity(
                            routineId = 0,
                            name = "Exercise 1",
                            orderIndex = 0
                        ),
                        sets = List(3) { i ->
                            ExerciseSetEntity(
                                exerciseId = 0,
                                mode = SetMode.REPS,
                                orderIndex = i
                            )
                        }
                    ),
                    ExerciseWithSets(
                        exercise = ExerciseEntity(
                            routineId = 0,
                            name = "Exercise 2",
                            orderIndex = 1
                        ),
                        sets = List(3) { i ->
                            ExerciseSetEntity(
                                exerciseId = 0,
                                mode = SetMode.REPS,
                                orderIndex = i
                            )
                        }
                    )
                )
            )
        }
    }
}
