package com.ashrdev.aznd.data.manage

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.ashrdev.aznd.data.body.BodyMeasurement
import com.ashrdev.aznd.data.body.BodyWeightEntry
import com.ashrdev.aznd.data.macro.FoodEntry
import com.ashrdev.aznd.data.macro.Ingredient
import com.ashrdev.aznd.data.streaks.StreakBreakEntity
import com.ashrdev.aznd.data.streaks.StreakEntity
import com.ashrdev.aznd.data.streaks.StreakSavedDayEntity
import com.ashrdev.aznd.data.workout.LoggedSession
import com.ashrdev.aznd.data.workout.LoggedSet
import com.ashrdev.aznd.data.workout.LoggedSetEntity
import com.ashrdev.aznd.data.workout.SessionEntity
import com.ashrdev.aznd.data.workout.WorkoutEntity

/**
 * Every query the Manage data modules need that the feature DAOs don't already have: counts,
 * range reads, range deletes and duplicate checks. Time ranges are [from, to) in epoch millis,
 * day-key ranges (streak days) are inclusive "yyyy-MM-dd" strings.
 *
 * FUTURE: a new feature's module adds its own queries here (or uses its feature DAO directly).
 */
@Dao
interface ManageDao {

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun countWorkouts(): Int

    @Query("SELECT * FROM workouts ORDER BY name COLLATE NOCASE")
    suspend fun workouts(): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun workoutNamed(name: String): WorkoutEntity?

    @Query("SELECT name FROM workouts WHERE id = :id")
    suspend fun workoutName(id: Long): String?

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkoutRow(id: Long)

    @Query("DELETE FROM workouts")
    suspend fun deleteAllWorkoutRows()

    @Query("DELETE FROM template_set")
    suspend fun deleteAllTemplateRows()

    @Query("DELETE FROM logged_session WHERE finishedAt IS NULL AND workoutId != 0")
    suspend fun deleteRunningRoutineSessions()

    @Transaction
    suspend fun deleteAllRoutines() {
        deleteAllWorkoutRows()
        deleteAllTemplateRows()
        deleteRunningRoutineSessions()
    }

    @Query("SELECT COUNT(*) FROM sessions WHERE startedAt >= :from AND startedAt < :to")
    suspend fun countLegacySessions(from: Long, to: Long): Int

    @Query(
        "SELECT COUNT(*) FROM logged_session WHERE finishedAt IS NOT NULL AND legacySessionId IS NULL " +
            "AND startedAt >= :from AND startedAt < :to"
    )
    suspend fun countUnlinkedLoggedSessions(from: Long, to: Long): Int

    @Query("SELECT * FROM sessions WHERE startedAt >= :from AND startedAt < :to ORDER BY startedAt")
    suspend fun legacySessions(from: Long, to: Long): List<SessionEntity>

    @Query(
        "SELECT ls.* FROM logged_sets ls INNER JOIN sessions s ON ls.sessionId = s.id " +
            "WHERE s.startedAt >= :from AND s.startedAt < :to ORDER BY ls.sessionId, ls.orderIndex"
    )
    suspend fun legacySets(from: Long, to: Long): List<LoggedSetEntity>

    @Query(
        "SELECT * FROM logged_session WHERE finishedAt IS NOT NULL AND startedAt >= :from AND startedAt < :to " +
            "ORDER BY startedAt"
    )
    suspend fun finishedLoggedSessions(from: Long, to: Long): List<LoggedSession>

    @Query(
        "SELECT ss.* FROM session_set ss INNER JOIN logged_session s ON ss.sessionId = s.id " +
            "WHERE s.finishedAt IS NOT NULL AND s.startedAt >= :from AND s.startedAt < :to " +
            "ORDER BY ss.sessionId, ss.id"
    )
    suspend fun loggedSetsOfFinished(from: Long, to: Long): List<LoggedSet>

    @Query("SELECT photoUri FROM sessions WHERE photoUri IS NOT NULL AND startedAt >= :from AND startedAt < :to")
    suspend fun legacyPhotoUris(from: Long, to: Long): List<String>

    @Query(
        "SELECT photoUri FROM logged_session WHERE photoUri IS NOT NULL AND finishedAt IS NOT NULL " +
            "AND startedAt >= :from AND startedAt < :to"
    )
    suspend fun loggedPhotoUris(from: Long, to: Long): List<String>

    @Query("DELETE FROM sessions WHERE startedAt >= :from AND startedAt < :to")
    suspend fun deleteLegacySessions(from: Long, to: Long)

    @Query("DELETE FROM logged_session WHERE finishedAt IS NOT NULL AND startedAt >= :from AND startedAt < :to")
    suspend fun deleteFinishedLoggedSessions(from: Long, to: Long)

    @Transaction
    suspend fun deleteHistory(from: Long, to: Long) {
        deleteFinishedLoggedSessions(from, to)
        deleteLegacySessions(from, to)
    }

    @Query("SELECT COUNT(*) FROM sessions WHERE startedAt = :startedAt AND workoutName = :workoutName COLLATE NOCASE")
    suspend fun legacySessionExists(startedAt: Long, workoutName: String): Int

    @Query("SELECT COUNT(*) FROM logged_session WHERE startedAt = :startedAt AND workoutId = :workoutId")
    suspend fun loggedSessionExists(startedAt: Long, workoutId: Long): Int

    @Query("SELECT COUNT(*) FROM streaks")
    suspend fun countStreaks(): Int

    @Query("SELECT * FROM streaks ORDER BY name COLLATE NOCASE")
    suspend fun streaks(): List<StreakEntity>

    @Query("SELECT * FROM streak_breaks ORDER BY streakId, date")
    suspend fun streakBreaks(): List<StreakBreakEntity>

    @Query("SELECT * FROM streaks WHERE name = :name AND startDate = :startDate LIMIT 1")
    suspend fun streakNamed(name: String, startDate: String): StreakEntity?

    @Query("SELECT photoUri FROM streaks WHERE photoUri IS NOT NULL UNION SELECT bannerUri FROM streaks WHERE bannerUri IS NOT NULL")
    suspend fun streakPhotoUris(): List<String>

    @Query("SELECT photoUri FROM streak_saved_days WHERE photoUri IS NOT NULL AND date >= :fromKey AND date <= :toKey")
    suspend fun savedDayPhotoUris(fromKey: String, toKey: String): List<String>

    @Query("DELETE FROM streaks")
    suspend fun deleteAllStreakRows()

    @Query("SELECT COUNT(*) FROM streak_saved_days WHERE date >= :fromKey AND date <= :toKey")
    suspend fun countSavedDays(fromKey: String, toKey: String): Int

    @Query("SELECT * FROM streak_saved_days WHERE date >= :fromKey AND date <= :toKey ORDER BY streakId, date")
    suspend fun savedDays(fromKey: String, toKey: String): List<StreakSavedDayEntity>

    @Query("DELETE FROM streak_saved_days WHERE date >= :fromKey AND date <= :toKey")
    suspend fun deleteSavedDays(fromKey: String, toKey: String)

    @Query("SELECT COUNT(*) FROM streak_saved_days WHERE streakId = :streakId AND date = :date")
    suspend fun savedDayExists(streakId: Long, date: String): Int

    @Query("SELECT COUNT(*) FROM body_weight WHERE measuredAt >= :from AND measuredAt < :to")
    suspend fun countWeights(from: Long, to: Long): Int

    @Query("SELECT * FROM body_weight WHERE measuredAt >= :from AND measuredAt < :to ORDER BY measuredAt")
    suspend fun weights(from: Long, to: Long): List<BodyWeightEntry>

    @Query("DELETE FROM body_weight WHERE measuredAt >= :from AND measuredAt < :to")
    suspend fun deleteWeights(from: Long, to: Long)

    @Query("SELECT COUNT(*) FROM body_weight WHERE measuredAt = :measuredAt AND weightKg = :weightKg")
    suspend fun weightExists(measuredAt: Long, weightKg: Double): Int

    @Query(
        "SELECT COUNT(*) FROM body_measurement WHERE (site = 'BODY_FAT') = :bodyFat " +
            "AND measuredAt >= :from AND measuredAt < :to"
    )
    suspend fun countMeasurements(bodyFat: Boolean, from: Long, to: Long): Int

    @Query(
        "SELECT * FROM body_measurement WHERE (site = 'BODY_FAT') = :bodyFat " +
            "AND measuredAt >= :from AND measuredAt < :to ORDER BY measuredAt"
    )
    suspend fun measurements(bodyFat: Boolean, from: Long, to: Long): List<BodyMeasurement>

    @Query(
        "DELETE FROM body_measurement WHERE (site = 'BODY_FAT') = :bodyFat " +
            "AND measuredAt >= :from AND measuredAt < :to"
    )
    suspend fun deleteMeasurements(bodyFat: Boolean, from: Long, to: Long)

    @Query("SELECT COUNT(*) FROM body_measurement WHERE site = :site AND measuredAt = :measuredAt AND valueCm = :valueCm")
    suspend fun measurementExists(site: String, measuredAt: Long, valueCm: Double): Int

    @Query("SELECT COUNT(*) FROM food_entry WHERE eatenAt >= :from AND eatenAt < :to")
    suspend fun countFood(from: Long, to: Long): Int

    @Query("SELECT * FROM food_entry WHERE eatenAt >= :from AND eatenAt < :to ORDER BY eatenAt")
    suspend fun foodEntries(from: Long, to: Long): List<FoodEntry>

    @Query("DELETE FROM food_entry WHERE eatenAt >= :from AND eatenAt < :to")
    suspend fun deleteFood(from: Long, to: Long)

    @Query("SELECT COUNT(*) FROM food_entry WHERE eatenAt = :eatenAt AND name = :name AND calories = :calories")
    suspend fun foodExists(eatenAt: Long, name: String, calories: Double): Int

    @Query("SELECT COUNT(*) FROM ingredient")
    suspend fun countIngredients(): Int

    @Query("SELECT * FROM ingredient ORDER BY name COLLATE NOCASE")
    suspend fun ingredients(): List<Ingredient>

    @Query("DELETE FROM ingredient")
    suspend fun deleteAllIngredients()

    @Query("SELECT COUNT(*) FROM ingredient WHERE name = :name COLLATE NOCASE")
    suspend fun ingredientExists(name: String): Int
}
