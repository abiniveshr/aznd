package com.ashrdev.aznd.data.workout

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.ashrdev.aznd.domain.ExerciseProfile
import com.ashrdev.aznd.domain.ExerciseType
import com.ashrdev.aznd.domain.Muscle
import com.ashrdev.aznd.domain.SeedExercise

/**
 * One catalog or custom exercise. Enums are stored as strings and
 * [secondaryMuscles] as a comma list through [MuscleListConverter].
 * The table is named catalog_exercise so it cannot clash with an older "exercises" table.
 */
@Entity(
    tableName = "catalog_exercise",
    indices = [Index(value = ["seedKey"], unique = true), Index(value = ["name"])]
)
@TypeConverters(MuscleListConverter::class)
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Slug of the catalog name; null for custom exercises. Unique (many nulls are allowed). */
    val seedKey: String? = null,
    val name: String,
    val type: ExerciseType,
    val primaryMuscle: Muscle,
    /** 0 to 3 muscles. */
    val secondaryMuscles: List<Muscle> = emptyList(),
    /** 0..1. Only used by the two bodyweight types. */
    val bodyweightShare: Double = 1.0,
    /** App-private file path of the photo, if any. */
    val photoPath: String? = null,
    val isCustom: Boolean = false
)

class MuscleListConverter {
    @TypeConverter
    fun toText(list: List<Muscle>): String = list.joinToString(",") { it.name }

    @TypeConverter
    fun toList(text: String): List<Muscle> =
        if (text.isBlank()) {
            emptyList()
        } else {
            text.split(",").mapNotNull { n -> Muscle.values().firstOrNull { it.name == n } }
        }
}

fun SeedExercise.toExercise(): Exercise = Exercise(
    seedKey = seedKey,
    name = name,
    type = type,
    primaryMuscle = primaryMuscle,
    secondaryMuscles = secondaryMuscles,
    bodyweightShare = bodyweightShare,
    isCustom = false
)

/** What the e1RM maths needs to know about this exercise. */
fun Exercise.toProfile(): ExerciseProfile = ExerciseProfile(type, bodyweightShare)
