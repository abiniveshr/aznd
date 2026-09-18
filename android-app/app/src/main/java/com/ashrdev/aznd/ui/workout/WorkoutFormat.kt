package com.ashrdev.aznd.ui.workout

import com.ashrdev.aznd.data.workout.LoggedSetEntity
import com.ashrdev.aznd.data.workout.SessionEntity
import com.ashrdev.aznd.data.workout.SetMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault())
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

fun formatTimestamp(millis: Long): String = dateFormat.format(Date(millis))

fun formatClock(millis: Long): String = timeFormat.format(Date(millis))

fun formatDuration(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0s"
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return buildList {
        if (h > 0) add("${h}h")
        if (m > 0) add("${m}m")
        if (s > 0) add("${s}s")
    }.joinToString(" ")
}

fun formatWeight(weight: Double): String =
    if (weight % 1.0 == 0.0) "${weight.toInt()} kg" else "$weight kg"

fun formatSetValue(mode: SetMode, value: Int): String =
    if (mode == SetMode.REPS) "$value reps" else formatDuration(value)

fun formatSet(set: LoggedSetEntity): String {
    val base = formatSetValue(set.mode, set.value)
    return if (set.weight > 0.0) "$base @ ${formatWeight(set.weight)}" else base
}

fun formatExerciseBlock(exerciseName: String, sets: List<LoggedSetEntity>): String = buildString {
    appendLine(exerciseName)
    sets.forEachIndexed { i, s ->
        appendLine("  Set ${i + 1}: ${formatSet(s)}")
    }
}

fun formatSessionText(session: SessionEntity, sets: List<LoggedSetEntity>): String = buildString {
    appendLine(session.routineName)
    appendLine(formatTimestamp(session.startedAt) + " – " + formatClock(session.finishedAt))
    val elapsed = ((session.finishedAt - session.startedAt) / 1000L).toInt()
    appendLine("Duration: ${formatDuration(elapsed)}")
    appendLine()
    sets.sortedBy { it.orderIndex }
        .groupBy { it.exerciseName }
        .forEach { (name, group) -> append(formatExerciseBlock(name, group)) }
    appendLine()
    append("logged with aznd")
}

fun formatExerciseShare(
    session: SessionEntity,
    exerciseName: String,
    sets: List<LoggedSetEntity>
): String = buildString {
    appendLine("${session.routineName} · ${formatTimestamp(session.startedAt)}")
    appendLine()
    append(formatExerciseBlock(exerciseName, sets))
    appendLine()
    append("logged with aznd")
}

fun formatSingleSetShare(
    session: SessionEntity,
    exerciseName: String,
    index: Int,
    set: LoggedSetEntity
): String = buildString {
    appendLine("${session.routineName} · ${formatTimestamp(session.startedAt)}")
    appendLine("$exerciseName — Set ${index + 1}: ${formatSet(set)}")
    append("logged with aznd")
}