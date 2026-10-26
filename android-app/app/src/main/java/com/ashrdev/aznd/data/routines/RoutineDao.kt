package com.ashrdev.aznd.data.routines

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY createdAt")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines ORDER BY createdAt")
    suspend fun routinesOnce(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun routine(id: Long): RoutineEntity?

    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Query("SELECT * FROM routine_logs")
    fun observeLogs(): Flow<List<RoutineLogEntity>>

    @Query("SELECT * FROM routine_logs")
    suspend fun logsOnce(): List<RoutineLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: RoutineLogEntity)

    @Query("DELETE FROM routine_logs WHERE routineId = :routineId AND date = :date")
    suspend fun deleteLog(routineId: Long, date: String)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders")
    suspend fun all(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun reminder(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY minuteOfDay")
    suspend fun forOwner(ownerType: String, ownerId: Long): List<ReminderEntity>

    @Insert
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Query("DELETE FROM reminders WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteForOwner(ownerType: String, ownerId: Long)

    @Query("DELETE FROM reminders WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Transaction
    suspend fun replaceForOwner(ownerType: String, ownerId: Long, reminders: List<ReminderEntity>) {
        deleteForOwner(ownerType, ownerId)
        if (reminders.isNotEmpty()) insertAll(reminders.map { it.copy(id = 0, ownerType = ownerType, ownerId = ownerId) })
    }
}
