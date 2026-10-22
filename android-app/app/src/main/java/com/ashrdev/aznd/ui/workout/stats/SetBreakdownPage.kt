package com.ashrdev.aznd.ui.workout.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import com.ashrdev.aznd.ui.workout.formatSet
import com.ashrdev.aznd.ui.workout.formatTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Set-by-set breakdown of one workout: pick a logged session (newest first), then see every
 * exercise's sets, each next to the same set from the session before it.
 */
@Composable
fun SetBreakdownPage(
    workoutId: Long,
    viewModel: WorkoutViewModel,
    bottomPadding: Dp
) {
    val flow = remember(workoutId) { viewModel.sessionsForWorkout(workoutId) }
    val sessions by flow.collectAsState(initial = emptyList())
    var selectedId by rememberSaveable { mutableStateOf(-1L) }

    if (sessions.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                "No sessions logged yet. Finish a session of this workout and its sets show up here.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    // Newest first. A stale or unset selection falls back to the latest session.
    val index = sessions.indexOfFirst { it.session.id == selectedId }.let { if (it < 0) 0 else it }
    val current = sessions[index]
    val previous = sessions.getOrNull(index + 1)

    val currentByExercise = remember(current) {
        current.sets.sortedBy { it.orderIndex }.groupBy { it.exerciseName }
    }
    val previousByExercise = remember(previous) {
        previous?.sets?.sortedBy { it.orderIndex }?.groupBy { it.exerciseName }.orEmpty()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "sessions") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions.take(20), key = { it.session.id }) { s ->
                    FilterChip(
                        selected = s.session.id == current.session.id,
                        onClick = { selectedId = s.session.id },
                        label = { Text(chipDate(s.session.startedAt)) }
                    )
                }
            }
        }
        item(key = "header") {
            Column {
                Text(formatTimestamp(current.session.startedAt), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (previous != null) "Compared with ${chipDate(previous.session.startedAt)}"
                    else "First logged session, nothing to compare with.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(currentByExercise.entries.toList(), key = { it.key }) { (exerciseName, sets) ->
            val before = previousByExercise[exerciseName].orEmpty()
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(exerciseName, style = MaterialTheme.typography.titleMedium)
                    sets.forEachIndexed { i, set ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Set ${i + 1}: ${formatSet(set)}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        val prev = before.getOrNull(i)
                        Text(
                            if (prev != null) "Before: ${formatSet(prev)}" else "Before: no data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun chipDate(ms: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))
