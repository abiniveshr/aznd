package com.ashrdev.aznd.data.streaks

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "streak_days",
    indices = [Index(value = ["date"], unique = true)]
)
data class StreakDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val remark: String = "",
    val photoUri: String? = null,
    val savedAt: Long = System.currentTimeMillis()
)