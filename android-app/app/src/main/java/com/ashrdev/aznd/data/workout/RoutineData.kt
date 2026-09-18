package com.ashrdev.aznd.data.workout

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter

enum class SetMode { REPS, TIME }

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "exercises",
    foreignKeys = [ForeignKey(
        entity = RoutineEntity::class,
        parentColumns = ["id"],
        childColumns = ["routineId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("routineId")]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val name: String,
    val orderIndex: Int
)

@Entity(
    tableName = "exercise_sets",
    foreignKeys = [ForeignKey(
        entity = ExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("exerciseId")]
)
data class ExerciseSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val mode: SetMode,
    val orderIndex: Int
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val routineName: String,
    val startedAt: Long,
    val finishedAt: Long,
    val photoUri: String? = null
)

@Entity(
    tableName = "logged_sets",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class LoggedSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseName: String,
    val mode: SetMode,
    val value: Int,
    val weight: Double,
    val orderIndex: Int
)

@Entity(tableName = "active_session")
data class ActiveSessionEntity(
    @PrimaryKey val id: Long = 1,
    val routineId: Long,
    val routineName: String,
    val startedAt: Long,
    val photoUri: String? = null
)

@Entity(
    tableName = "active_sets",
    foreignKeys = [ForeignKey(
        entity = ActiveSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class ActiveSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long = 1,
    val exerciseName: String,
    val exerciseIndex: Int,
    val setIndex: Int,
    val mode: SetMode,
    val valueText: String,
    val weightText: String
)

data class ExerciseWithSets(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exerciseId")
    val sets: List<ExerciseSetEntity>
)

data class RoutineWithExercises(
    @Embedded val routine: RoutineEntity,
    @Relation(entity = ExerciseEntity::class, parentColumn = "id", entityColumn = "routineId")
    val exercises: List<ExerciseWithSets>
)

data class SessionWithSets(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val sets: List<LoggedSetEntity>
)

data class ActiveSessionWithSets(
    @Embedded val session: ActiveSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val sets: List<ActiveSetEntity>
)

class Converters {
    @TypeConverter
    fun fromSetMode(mode: SetMode): String = mode.name

    @TypeConverter
    fun toSetMode(value: String): SetMode = SetMode.valueOf(value)
}