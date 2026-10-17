package com.ashrdev.aznd.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.ashrdev.aznd.data.body.BodyDao
import com.ashrdev.aznd.data.manage.ManageDao
import com.ashrdev.aznd.data.body.BodyMeasurement
import com.ashrdev.aznd.data.body.BodyWeightEntry
import com.ashrdev.aznd.data.body.MIGRATION_13_14
import com.ashrdev.aznd.data.macro.FoodDao
import com.ashrdev.aznd.data.macro.FoodEntry
import com.ashrdev.aznd.data.macro.Ingredient
import com.ashrdev.aznd.data.macro.IngredientDao
import com.ashrdev.aznd.data.macro.MIGRATION_14_15
import com.ashrdev.aznd.data.macro.MIGRATION_15_16
import com.ashrdev.aznd.data.streaks.MIGRATION_18_19
import com.ashrdev.aznd.data.streaks.MIGRATION_19_20
import com.ashrdev.aznd.data.streaks.StreakBreakEntity
import com.ashrdev.aznd.data.streaks.StreakDao
import com.ashrdev.aznd.data.streaks.StreakEntity
import com.ashrdev.aznd.data.streaks.StreakSavedDayEntity
import com.ashrdev.aznd.data.workout.ActiveSessionEntity
import com.ashrdev.aznd.data.workout.MIGRATION_16_17
import com.ashrdev.aznd.data.workout.MIGRATION_17_18
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
import com.ashrdev.aznd.domain.Muscle
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
        LoggedSet::class,
        BodyWeightEntry::class,
        BodyMeasurement::class,
        FoodEntry::class,
        Ingredient::class
    ],
    version = 20
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun streakDao(): StreakDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun templateDao(): TemplateDao
    abstract fun sessionDao(): SessionDao
    abstract fun bodyDao(): BodyDao
    abstract fun foodDao(): FoodDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun manageDao(): ManageDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aznd.db"
                ).addMigrations(MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
                    .fallbackToDestructiveMigration(true)
                    .addCallback(object : Callback() {
                        // Runs on every open, before the first query: the old single "SHOULDERS"
                        // muscle no longer exists, so any stored value is moved to SIDE_DELTS
                        // (catalog rows are then corrected exactly by the seed sync).
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            db.execSQL("UPDATE catalog_exercise SET primaryMuscle = 'SIDE_DELTS' WHERE primaryMuscle = 'SHOULDERS'")
                            // Safety net: Room throws when it reads an enum value it doesn't know, which
                            // would crash every screen that lists exercises. Anything unrecognized becomes
                            // CHEST (catalog rows are then corrected exactly by the seed sync).
                            val known = Muscle.entries.joinToString(",") { "'" + it.name + "'" }
                            db.execSQL("UPDATE catalog_exercise SET primaryMuscle = 'CHEST' WHERE primaryMuscle NOT IN ($known)")
                            db.execSQL(
                                "UPDATE catalog_exercise SET secondaryMuscles = REPLACE(secondaryMuscles, 'SHOULDERS', 'SIDE_DELTS') " +
                                    "WHERE secondaryMuscles LIKE '%SHOULDERS%'"
                            )
                        }
                    })
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
                                val seeded = context.applicationContext.getSharedPreferences("seed_state", Context.MODE_PRIVATE)
                                if (!seeded.getBoolean("sample_workout", false)) {
                                    if (db.workoutDao().workoutCount() == 0) {
                                        seedSampleWorkout(db)
                                    }
                                    seeded.edit().putBoolean("sample_workout", true).apply()
                                }
                                if (!seeded.getBoolean("sample_streak", false)) {
                                    if (db.streakDao().streakCount() == 0) {
                                        db.streakDao().insertStreak(
                                            StreakEntity(name = "My Streak", startDate = todayKeyForSeed())
                                        )
                                    }
                                    seeded.edit().putBoolean("sample_streak", true).apply()
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
            val workoutId = db.workoutDao().insertWorkout(WorkoutEntity(name = "Workout 1"))
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
            TrainingRepository(db.exerciseDao(), db.templateDao(), db.sessionDao(), legacy = db.workoutDao())
                .saveTemplate(workoutId, rows)
        }
    }
}
