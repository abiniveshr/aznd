package com.ashrdev.aznd.data.streaks

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StreakDao {
    @Query("SELECT * FROM streaks ORDER BY name")
    fun getStreaks(): Flow<List<StreakEntity>>

    @Query("SELECT * FROM streaks WHERE id = :id")
    suspend fun getStreak(id: Long): StreakEntity?

    @Query("SELECT COUNT(*) FROM streaks")
    suspend fun streakCount(): Int

    @Insert
    suspend fun insertStreak(streak: StreakEntity): Long

    @Query("UPDATE streaks SET photoUri = :uri WHERE id = :id")
    suspend fun updateStreakPhoto(id: Long, uri: String?)

    @Query("DELETE FROM streaks WHERE id = :id")
    suspend fun deleteStreak(id: Long)

    @Query("SELECT date FROM streak_breaks WHERE streakId = :streakId")
    fun getBreaksForStreak(streakId: Long): Flow<List<String>>

    @Query("SELECT date FROM streak_breaks WHERE streakId = :streakId")
    suspend fun getBreaksForStreakOnce(streakId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBreak(entity: StreakBreakEntity)

    @Query("DELETE FROM streak_breaks WHERE streakId = :streakId AND date = :date")
    suspend fun deleteBreak(streakId: Long, date: String)

    @Query("SELECT * FROM streak_saved_days WHERE streakId = :streakId ORDER BY date DESC")
    fun getSavedDaysForStreak(streakId: Long): Flow<List<StreakSavedDayEntity>>

    @Query("""
        SELECT DISTINCT streaks.* FROM streaks
        INNER JOIN streak_saved_days ON streaks.id = streak_saved_days.streakId
        ORDER BY streaks.name
    """)
    fun getStreaksWithSavedDays(): Flow<List<StreakEntity>>

    @Query("SELECT * FROM streak_saved_days WHERE streakId = :streakId AND date = :date LIMIT 1")
    suspend fun getSavedDay(streakId: Long, date: String): StreakSavedDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedDay(entity: StreakSavedDayEntity)

    @Query("DELETE FROM streak_saved_days WHERE streakId = :streakId AND date = :date")
    suspend fun deleteSavedDay(streakId: Long, date: String)
}