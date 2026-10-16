package com.ashrdev.aznd.data.streaks

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "streaks")
data class StreakEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: String,
    // The streak's logo (square crop). Column name kept from when it was a 16:9 banner.
    val photoUri: String? = null,
    // Optional one-liner shown under the name on the Streaks screen.
    val quote: String? = null,
    // Comma-separated day counts, e.g. "10,25,50". Use parseMilestones() to read it.
    @ColumnInfo(defaultValue = "'10,25,50,100,200,300,400,500,1000'")
    val milestones: String = DEFAULT_MILESTONES_CSV,
    // The wide photo at the top of the streak's Details page (16:9 crop). Separate from the logo.
    val bannerUri: String? = null
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
