package com.ashrdev.aznd.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.ashrdev.aznd.data.streaks.StreakBreakEntity
import com.ashrdev.aznd.data.streaks.StreakDao
import com.ashrdev.aznd.data.streaks.StreakEntity
import com.ashrdev.aznd.data.streaks.StreakSavedDayEntity
import com.ashrdev.aznd.data.workout.ActiveSessionEntity
import com.ashrdev.aznd.data.workout.ActiveSetEntity
import com.ashrdev.aznd.data.workout.Converters
import com.ashrdev.aznd.data.workout.ExerciseEntity
import com.ashrdev.aznd.data.workout.ExerciseSetEntity
import com.ashrdev.aznd.data.workout.ExerciseWithSets
import com.ashrdev.aznd.data.workout.LoggedSetEntity
import com.ashrdev.aznd.data.workout.RoutineDao
import com.ashrdev.aznd.data.workout.RoutineEntity
import com.ashrdev.aznd.data.workout.SessionEntity
import com.ashrdev.aznd.data.workout.SetMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(
    entities = [
        RoutineEntity::class,
        ExerciseEntity::class,
        ExerciseSetEntity::class,
        SessionEntity::class,
        LoggedSetEntity::class,
        ActiveSessionEntity::class,
        ActiveSetEntity::class,
        StreakEntity::class,
        StreakBreakEntity::class,
        StreakSavedDayEntity::class
    ],
    version = 10
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun streakDao(): StreakDao

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
                            if (db.streakDao().streakCount() == 0) {
                                db.streakDao().insertStreak(
                                    StreakEntity(name = "My Streak", startDate = todayKeyForSeed())
                                )
                            }
                        }
                    }
            }
        }

        private fun todayKeyForSeed(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        private suspend fun seedSampleRoutine(dao: RoutineDao) {
            dao.saveRoutine(
                RoutineEntity(name = "Workout 1"),
                listOf(
                    ExerciseWithSets(
                        exercise = ExerciseEntity(routineId = 0, name = "Exercise 1", orderIndex = 0),
                        sets = List(3) { i -> ExerciseSetEntity(exerciseId = 0, mode = SetMode.REPS, orderIndex = i) }
                    ),
                    ExerciseWithSets(
                        exercise = ExerciseEntity(routineId = 0, name = "Exercise 2", orderIndex = 1),
                        sets = List(3) { i -> ExerciseSetEntity(exerciseId = 0, mode = SetMode.REPS, orderIndex = i) }
                    )
                )
            )
        }
    }
}