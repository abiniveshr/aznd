package com.ashrdev.aznd.ui.workout.summary

import com.ashrdev.aznd.data.workout.LoggedSetEntity
import com.ashrdev.aznd.data.workout.SetMode
import com.ashrdev.aznd.domain.workout.E1rmCalculator
import com.ashrdev.aznd.domain.workout.ExerciseProfile
import com.ashrdev.aznd.domain.workout.SetEntry
import com.ashrdev.aznd.domain.workout.SetKind
import com.ashrdev.aznd.domain.workout.SetRow
import com.ashrdev.aznd.domain.workout.SetTree
import com.ashrdev.aznd.domain.workout.formatWeightKg
import com.ashrdev.aznd.ui.workout.formatDuration
import com.ashrdev.aznd.ui.workout.stats.estimatedOneRepMax
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class SetLine(val mode: SetMode, val value: Int, val weight: Double, val rpe: Double?)

fun LoggedSetEntity.toLine(): SetLine = SetLine(mode, value, weight, rpe)

data class E1rmContext(val profile: ExerciseProfile?, val bodyweightKg: Double?) {
    val addedWeight: Boolean get() = profile?.type?.isBodyweight == true
}

private val e1rmCalculator = E1rmCalculator()

data class Delta(val text: String, val direction: Int)

data class ExerciseComparison(
    val previousAt: Long,
    val setCount: Delta,
    val volume: Delta?,
    val e1rm: Delta?,
    val sets: List<Delta?>
)

data class ExerciseSummary(
    val name: String,
    val sets: List<SetLine>,
    val volumeKg: Double,
    val bestE1rm: Double?,
    val comparison: ExerciseComparison?,
    val e1rmContext: E1rmContext? = null,
    val notInCompared: Boolean = false
)

data class ComparePick(val sessionId: Long, val startedAt: Long, val workoutName: String, val topSet: String)

data class SummaryTotal(val label: String, val value: String)

data class SummaryData(
    val historyId: Long,
    val workoutId: Long,
    val title: String,
    val startedAt: Long,
    val finishedAt: Long,
    val photoUri: String?,
    val totals: List<SummaryTotal>,
    val exercises: List<ExerciseSummary>,
    val freestyle: Boolean,
    val templateRows: List<SetRow>,
    val canSaveAsPreset: Boolean,
    val canUpdatePreset: Boolean,
    val canRename: Boolean,
    val suggestedName: String,
    val compareWithId: Long? = null,
    val compareWithAt: Long? = null,
    val exerciseCompareAt: Map<String, Long> = emptyMap(),
    val lastTimeId: Long? = null,
    val fallbackThenId: Long? = null,
    val overallPicks: Map<String, ComparePick> = emptyMap(),
    val exercisePicks: Map<String, Map<String, ComparePick>> = emptyMap()
)

private val compareDateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

fun formatCompareDate(millis: Long): String = compareDateFormat.format(Date(millis))

fun roundKg(kg: Double): Double = (kg * 10.0).roundToLong() / 10.0

fun volumeOf(lines: List<SetLine>): Double =
    lines.filter { it.mode == SetMode.REPS && it.weight > 0.0 }.sumOf { it.weight * it.value }

fun bestE1rm(lines: List<SetLine>, context: E1rmContext? = null): Double? =
    lines.mapNotNull { setE1rm(it, context) }.maxOrNull()

private fun directionOf(diff: Double): Int = when {
    diff > 0.0 -> 1
    diff < 0.0 -> -1
    else -> 0
}

private fun signOf(direction: Int): String = if (direction < 0) "−" else "+"

fun kgDelta(now: Double, before: Double): Delta {
    val diff = roundKg(now - before)
    val direction = directionOf(diff)
    return Delta(signOf(direction) + formatWeightKg(abs(diff)), direction)
}

fun countDelta(now: Int, before: Int): Delta {
    val diff = now - before
    val direction = if (diff > 0) 1 else if (diff < 0) -1 else 0
    return Delta(signOf(direction) + abs(diff), direction)
}

fun durationDelta(nowSeconds: Int, beforeSeconds: Int): Delta {
    val diff = nowSeconds - beforeSeconds
    val direction = if (diff > 0) 1 else if (diff < 0) -1 else 0
    return Delta(signOf(direction) + formatDuration(abs(diff)), direction)
}

fun intensityPercent(lines: List<SetLine>): Int? {
    val rpes = lines.mapNotNull { it.rpe }
    if (rpes.isEmpty()) return null
    return (rpes.average() * 10.0).roundToInt()
}

fun setE1rm(set: SetLine, context: E1rmContext? = null): Double? {
    if (set.mode != SetMode.REPS) return null
    val profile = context?.profile
    if (profile == null || !profile.type.isBodyweight) return estimatedOneRepMax(set.weight, set.value, set.rpe)
    val bodyweight = context?.bodyweightKg ?: return null
    val entry = SetEntry(SetKind.NORMAL, set.weight, set.value, null, set.rpe)
    val total = e1rmCalculator.e1rm(profile, bodyweight, entry) ?: return null
    return total - bodyweight * profile.bodyweightShare
}

fun formatE1rmText(value: Double, context: E1rmContext? = null): String {
    val rounded = roundKg(value)
    if (context == null || !context.addedWeight) return formatWeightKg(rounded)
    return signOf(directionOf(rounded)) + formatWeightKg(abs(rounded))
}

fun setDelta(now: SetLine, before: SetLine, nowContext: E1rmContext? = null, beforeContext: E1rmContext? = null): Delta {
    if (now.mode == SetMode.TIME) return durationDelta(now.value, before.value)
    val nowE1rm = setE1rm(now, nowContext)
    val beforeE1rm = setE1rm(before, beforeContext)
    if (nowE1rm != null && beforeE1rm != null) return kgDelta(nowE1rm, beforeE1rm)
    return countDelta(now.value, before.value)
}

fun compareExercise(
    current: List<SetLine>,
    previous: List<SetLine>,
    previousAt: Long,
    currentContext: E1rmContext? = null,
    previousContext: E1rmContext? = null
): ExerciseComparison {
    val currentVolume = volumeOf(current)
    val previousVolume = volumeOf(previous)
    val currentBest = bestE1rm(current, currentContext)
    val previousBest = bestE1rm(previous, previousContext)
    return ExerciseComparison(
        previousAt = previousAt,
        setCount = countDelta(current.size, previous.size),
        volume = if (currentVolume > 0.0 || previousVolume > 0.0) kgDelta(currentVolume, previousVolume) else null,
        e1rm = if (currentBest != null && previousBest != null) kgDelta(currentBest, previousBest) else null,
        sets = current.mapIndexed { i, set ->
            previous.getOrNull(i)?.takeIf { it.mode == set.mode }?.let { setDelta(set, it, currentContext, previousContext) }
        }
    )
}

fun formatSetValueText(set: SetLine): String =
    if (set.mode == SetMode.TIME) {
        if (set.weight != 0.0) "${formatDuration(set.value)} @ ${formatWeightKg(set.weight)}" else formatDuration(set.value)
    } else {
        if (set.weight != 0.0) "${formatWeightKg(set.weight)} × ${set.value}" else "${set.value} reps"
    }

fun templateShape(rows: List<SetRow>): List<Pair<Long?, List<Pair<SetKind, Long?>>>> =
    SetTree.cards(SetTree.dropUnsetRows(rows)).map { card -> card.exerciseId to card.rows.map { it.kind to it.exerciseId } }

fun templateFromSession(rows: List<SetRow>): List<SetRow> =
    SetTree.dropUnsetRows(rows).map { it.copy(weightKg = null, reps = null, durationSec = null, rpe = null) }

fun topSetOf(lines: List<SetLine>, context: E1rmContext? = null): Pair<SetLine, Double?>? {
    val scored = lines.mapNotNull { line -> setE1rm(line, context)?.let { line to it } }.maxByOrNull { it.second }
    if (scored != null) return scored.first to scored.second
    val timed = lines.filter { it.mode == SetMode.TIME }.maxByOrNull { it.value }
    if (timed != null) return timed to null
    val any = lines.maxByOrNull { it.value } ?: return null
    return any to null
}

fun topSetText(top: Pair<SetLine, Double?>, context: E1rmContext? = null): String {
    val e1rm = top.second ?: return formatSetValueText(top.first)
    return formatSetValueText(top.first) + " · e1RM " + formatE1rmText(e1rm, context)
}
