package com.ashrdev.aznd.data.workout

import com.ashrdev.aznd.domain.ExerciseType
import com.ashrdev.aznd.domain.Muscle
import com.ashrdev.aznd.domain.parseCount

const val MAX_FORM_MUSCLES = 4
const val DEFAULT_SHARE_PERCENT = "100"

enum class FormError { NAME_BLANK, NAME_TAKEN, TYPE_MISSING, NO_PRIMARY, SHARE_INVALID }

/**
 * State of the custom-exercise form (pure, no Android). [muscles] is ordered: index 0 is the
 * PRIMARY muscle, the others (max 3) are secondary. Tapping a chip adds it (the first one tapped
 * becomes primary); tapping a selected chip removes it (if that was the primary, the next one
 * is promoted); the star on another chip moves it to the front.
 */
data class CustomExerciseForm(
    val name: String = "",
    val type: ExerciseType? = null,
    val muscles: List<Muscle> = emptyList(),
    val sharePercentText: String = DEFAULT_SHARE_PERCENT,
    val photoPath: String? = null
) {
    val primary: Muscle? get() = muscles.firstOrNull()
    val secondary: List<Muscle> get() = muscles.drop(1)
    val showShare: Boolean get() = type?.isBodyweight == true

    companion object {
        /** Form pre-filled from a stored exercise (edit mode). */
        fun from(e: Exercise) = CustomExerciseForm(
            name = e.name,
            type = e.type,
            muscles = listOf(e.primaryMuscle) + e.secondaryMuscles,
            sharePercentText = Math.round(e.bodyweightShare * 100).toString(),
            photoPath = e.photoPath
        )
    }

    /** Trimmed, inner whitespace collapsed: what is stored and what the duplicate check uses. */
    val cleanName: String get() = name.trim().replace(Regex("\\s+"), " ")

    fun withName(v: String) = copy(name = v)
    fun withType(v: ExerciseType) = copy(type = v)
    fun withShareText(v: String) = copy(sharePercentText = v)
    fun withPhoto(path: String?) = copy(photoPath = path)

    fun toggleMuscle(m: Muscle): CustomExerciseForm = when {
        m in muscles -> copy(muscles = muscles - m)
        muscles.size >= MAX_FORM_MUSCLES -> this
        else -> copy(muscles = muscles + m)
    }

    fun makePrimary(m: Muscle): CustomExerciseForm =
        if (m in muscles) copy(muscles = listOf(m) + (muscles - m)) else this

    /** Share in percent (1..100), or null if the text is not valid. Only checked for bodyweight types. */
    private fun sharePercent(): Int? = parseCount(sharePercentText)?.takeIf { it in 1..100 }

    /** Everything wrong with the form right now. [nameTaken] comes from the repository. */
    fun errors(nameTaken: Boolean = false): Set<FormError> = buildSet {
        if (name.isBlank()) add(FormError.NAME_BLANK) else if (nameTaken) add(FormError.NAME_TAKEN)
        if (type == null) add(FormError.TYPE_MISSING)
        if (muscles.isEmpty()) add(FormError.NO_PRIMARY)
        if (showShare && sharePercent() == null) add(FormError.SHARE_INVALID)
    }

    /** The row to insert (TrainingRepository.addCustomExercise sets isCustom and clears seedKey), or null if invalid. */
    fun toExercise(): Exercise? {
        if (errors().isNotEmpty()) return null
        val t = type ?: return null
        return Exercise(
            name = cleanName,
            type = t,
            primaryMuscle = muscles.first(),
            secondaryMuscles = muscles.drop(1),
            bodyweightShare = if (t.isBodyweight) (sharePercent() ?: 100) / 100.0 else 1.0,
            photoPath = photoPath,
            isCustom = true
        )
    }
}

fun FormError.message(): String = when (this) {
    FormError.NAME_BLANK -> "Enter a name"
    FormError.NAME_TAKEN -> "An exercise with this name already exists"
    FormError.TYPE_MISSING -> "Choose a type"
    FormError.NO_PRIMARY -> "Pick at least one muscle (the first one is primary)"
    FormError.SHARE_INVALID -> "Bodyweight share must be 1 to 100 %"
}

fun ExerciseType.formLabel(): String = when (this) {
    ExerciseType.WEIGHTED -> "Weight × reps"
    ExerciseType.BODYWEIGHT_REPS -> "Bodyweight reps"
    ExerciseType.ASSISTED_BODYWEIGHT -> "Assisted bodyweight"
    ExerciseType.TIME_HELD -> "Time held"
}
