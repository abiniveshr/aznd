package com.ashrdev.aznd.data.streaks

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "streaks")
data class StreakEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: String,
    val photoUri: String? = null
)

@Entity(
    tableName = "streak_breaks",
    foreignKeys = [ForeignKey(
        entity = StreakEntity::class,
        parentColumns = ["id"],
        childColumns = ["streakId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["streakId", "date"], unique = true)]
)
data class StreakBreakEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val streakId: Long,
    val date: String
)

@Entity(
    tableName = "streak_saved_days",
    foreignKeys = [ForeignKey(
        entity = StreakEntity::class,
        parentColumns = ["id"],
        childColumns = ["streakId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["streakId", "date"], unique = true)]
)
data class StreakSavedDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val streakId: Long,
    val date: String,
    val remark: String = "",
    val photoUri: String? = null,
    val streakCountAtSave: Int = 0,
    val savedAt: Long = System.currentTimeMillis()
)