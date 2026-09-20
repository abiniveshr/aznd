package com.ashrdev.aznd.data.streaks

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StreakDao {
    @Query("SELECT * FROM streak_days ORDER BY date DESC")
    fun getAllDays(): Flow<List<StreakDayEntity>>

    @Query("SELECT * FROM streak_days WHERE date = :date LIMIT 1")
    suspend fun getDay(date: String): StreakDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: StreakDayEntity)

    @Query("DELETE FROM streak_days WHERE date = :date")
    suspend fun deleteDay(date: String)
}