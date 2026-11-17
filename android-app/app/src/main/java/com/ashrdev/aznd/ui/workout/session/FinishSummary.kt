package com.ashrdev.aznd.ui.workout.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.domain.workout.LoggedSetData
import com.ashrdev.aznd.domain.workout.SessionStatsCalculator
import com.ashrdev.aznd.domain.workout.SetRow
import com.ashrdev.aznd.domain.workout.StatsExercise
import com.ashrdev.aznd.domain.workout.formatKcal
import com.ashrdev.aznd.domain.workout.formatWeightKg
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.workout.formatDuration
import com.ashrdev.aznd.ui.theme.Space

/** The numbers shown before a session is finished. */
class FinishSummary(
    val durationSeconds: Int,
    val exercises: Int,
    val sets: Int,
    val volumeKg: Double,
    val kcal: Double
)

/**
 * Volume, set count and kcal come from the same calculator the history stats use, so the summary
 * matches what the finished session will show. A set counts once it has reps or seconds.
 */
fun buildFinishSummary(session: LoggerSession, rows: List<SetRow>, exercises: List<Exercise>, now: Long): FinishSummary {
    val known = exercises.associate {
        it.id to StatsExercise(it.id, it.name, it.type, it.bodyweightShare, it.primaryMuscle, it.secondaryMuscles)
    }
    val data = rows.mapNotNull { r ->
        r.exerciseId?.let { ex ->
            LoggedSetData(
                exerciseId = ex,
                sessionId = session.id,
                workoutId = session.workoutId,
                startedAt = session.startedAt,
                finishedAt = now,
                bodyweightKg = session.bodyweightKg,
                kind = r.kind,
                weightKg = r.weightKg,
                reps = r.reps,
                durationSec = r.durationSec,
                rpe = r.rpe
            )
        }
    }
    val stat = SessionStatsCalculator.all(data, known).firstOrNull()
    val logged = data.filter { it.isLogged && known.containsKey(it.exerciseId) }
    return FinishSummary(
        durationSeconds = ((now - session.startedAt) / 1000L).toInt().coerceAtLeast(0),
        exercises = logged.map { it.exerciseId }.distinct().size,
        sets = stat?.sets ?: 0,
        volumeKg = stat?.volumeKg ?: 0.0,
        kcal = stat?.kcal ?: 0.0
    )
}

/** Finish: summary with "Finish" / "Keep going", plus "Finish and share" when [onFinishAndShare] is given. */
@Composable
fun FinishSummaryDialog(
    summary: FinishSummary,
    onFinish: () -> Unit,
    onFinishAndShare: (() -> Unit)?,
    onKeepGoing: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKeepGoing,
        title = { Text("Finish this workout?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SummaryLine("Duration", formatDuration(summary.durationSeconds))
                SummaryLine("Exercises", summary.exercises.toString())
                SummaryLine("Sets", summary.sets.toString())
                SummaryLine("Volume", formatWeightKg(summary.volumeKg))
                SummaryLine("Estimated", formatKcal(summary.kcal))
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (onFinishAndShare != null) TextButton(onClick = onFinishAndShare) { Text("Finish and share") }
                TextButton(onClick = onFinish) { Text("Finish") }
            }
        },
        dismissButton = { TextButton(onClick = onKeepGoing) { Text("Keep going") } }
    )
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
