package com.ashrdev.aznd.data.routines

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ashrdev.aznd.data.streaks.DEFAULT_MILESTONES_CSV
import com.ashrdev.aznd.domain.streaks.RoutineFrequency
import com.ashrdev.aznd.domain.streaks.RoutineSchedule
import com.ashrdev.aznd.domain.streaks.RoutineType
import com.ashrdev.aznd.domain.streaks.RoutineUnit
import com.ashrdev.aznd.domain.streaks.TrackMode

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String = RoutineType.CUSTOM.id,
    val workoutId: Long = ANY_WORKOUT,
    val frequency: String = RoutineFrequency.DAILY.id,
    val weekdays: Int = 0,
    val perWeek: Int = 3,
    @ColumnInfo(defaultValue = "1")
    val everyCount: Int = 1,
    @ColumnInfo(defaultValue = "'days'")
    val everyUnit: String = RoutineUnit.DAYS.id,
    val startDate: String,
    val photoUri: String? = null,
    val bannerUri: String? = null,
    val milestones: String = DEFAULT_MILESTONES_CSV,
    val milestoneAlerts: Boolean = false,
    val trackAsStreak: Boolean = true,
    val trackMode: String = TrackMode.AUTO.id,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val ANY_WORKOUT = -1L
    }
}

val RoutineEntity.routineType: RoutineType get() = RoutineType.fromId(type)

val RoutineEntity.schedule: RoutineSchedule get() = RoutineSchedule(RoutineFrequency.fromId(frequency), weekdays, perWeek, everyCount, RoutineUnit.fromId(everyUnit))

@Entity(
    tableName = "routine_logs",
    foreignKeys = [ForeignKey(
        entity = RoutineEntity::class,
        parentColumns = ["id"],
        childColumns = ["routineId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["routineId", "date"], unique = true)]
)
data class RoutineLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val date: String,
    @ColumnInfo(defaultValue = "0")
    val at: Long = 0
)

@Entity(tableName = "reminders", indices = [Index(value = ["ownerType", "ownerId"])])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerType: String,
    val ownerId: Long,
    val minuteOfDay: Int,
    val weekdays: Int = 0,
    val message: String = ""
) {
    companion object {
        const val STREAK = "streak"
        const val ROUTINE = "routine"
    }
}

data class SessionTime(val workoutId: Long, val startedAt: Long)
