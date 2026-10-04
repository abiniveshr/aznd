package com.ashrdev.aznd.data

import android.content.Context
import android.util.Log
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
import com.ashrdev.aznd.data.workout.LoggedSetEntity
import com.ashrdev.aznd.data.workout.WorkoutDao
import com.ashrdev.aznd.data.workout.WorkoutEntity
import com.ashrdev.aznd.data.workout.SessionEntity
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.data.workout.ExerciseDao
import com.ashrdev.aznd.data.workout.ExerciseSeeder
import com.ashrdev.aznd.data.workout.TemplateSet
import com.ashrdev.aznd.data.workout.LoggedSession
import com.ashrdev.aznd.data.workout.LoggedSet
import com.ashrdev.aznd.data.workout.TemplateDao
import com.ashrdev.aznd.data.workout.SessionDao
import com.ashrdev.aznd.data.workout.TrainingRepository
import com.ashrdev.aznd.domain.SetRow
import com.ashrdev.aznd.data.workout.assetSeedReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(
    entities = [
        WorkoutEntity::class,
        ExerciseEntity::class,
        ExerciseSetEntity::class,
        SessionEntity::class,
        LoggedSetEntity::class,
        ActiveSessionEntity::class,
        ActiveSetEntity::class,
        StreakEntity::class,
        StreakBreakEntity::class,
        StreakSavedDayEntity::class,
        Exercise::class,
        TemplateSet::class,
        LoggedSession::class,
        LoggedSet::class
    ],
    version = 13
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun WorkoutDao(): WorkoutDao
    abstract fun streakDao(): StreakDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun templateDao(): TemplateDao
    abstract fun sessionDao(): SessionDao

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
                            // First-run seeding must never crash the app; the catalog is retried on every start.
                            try {
                                ExerciseSeeder(
                                    db.exerciseDao(),
                                    assetSeedReader(context),
                                    onSkippedLines = { Log.w("AppDatabase", "Skipped catalog lines: $it") }
                                ).seedMissing()
                                if (db.WorkoutDao().WorkoutCount() == 0) {
                                    seedSampleWorkout(db)
                                }
                                if (db.streakDao().streakCount() == 0) {
                                    db.streakDao().insertStreak(
                                        StreakEntity(name = "My Streak", startDate = todayKeyForSeed())
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e("AppDatabase", "Seeding failed", e)
                            }
                        }
                    }
            }
        }

        private fun todayKeyForSeed(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        /**
         * First start only: one sample workout with a real template (three sets each of two catalog
         * exercises), so the Start button works straight away. Falls back to an empty workout if the
         * catalog could not be read.
         */
        private suspend fun seedSampleWorkout(db: AppDatabase) {
            val workoutId = db.WorkoutDao().insertWorkout(WorkoutEntity(name = "Workout 1"))
            val ids = listOfNotNull(
                db.exerciseDao().findByName("Barbell Bench Press")?.id,
                db.exerciseDao().findByName("Barbell Row")?.id
            )
            if (ids.isEmpty()) return
            val rows = ids.flatMapIndexed { exerciseIndex, exerciseId ->
                List(3) { setIndex ->
                    SetRow(
                        id = -(exerciseIndex * 3 + setIndex + 1).toLong(),
                        position = exerciseIndex * 3 + setIndex,
                        exerciseId = exerciseId
                    )
                }
            }
            TrainingRepository(db.exerciseDao(), db.templateDao(), db.sessionDao(), legacy = db.WorkoutDao())
                .saveTemplate(workoutId, rows)
        }
    }
}
