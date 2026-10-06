package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.domain.BestLift
import com.ashrdev.aznd.domain.ExerciseContribution
import com.ashrdev.aznd.domain.MuscleStats
import com.ashrdev.aznd.domain.StatsRange
import com.ashrdev.aznd.domain.WeekBucket
import com.ashrdev.aznd.domain.displayName
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.components.Card
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Statistics of ONE muscle group, reached from Dashboard -> Muscle data. The body diagram will
 * later open this same screen. A primary muscle counts 1 per set, a secondary muscle 0.5.
 */
@Composable
fun MuscleStatsScreen(
    viewModel: MuscleStatsViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val stats by viewModel.stats.collectAsState()
    val range by viewModel.range.collectAsState()

    ScreenScaffold(
        title = viewModel.muscle.displayName(),
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StatsRange.values().toList(), key = { it.name }) { r ->
                        FilterChip(
                            selected = r == range,
                            onClick = { viewModel.setRange(r) },
                            label = { Text(r.label) }
                        )
                    }
                }
            }

            val s = stats
            when {
                s == null -> item { Text("Loading…", style = MaterialTheme.typography.bodyMedium) }
                !s.hasHistory -> item {
                    Text(
                        "No sets logged for ${s.muscle.displayName().lowercase()} yet. Finish a workout with an " +
                            "exercise that trains it and the numbers show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> statItems(s)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.statItems(s: MuscleStats) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatRow("Weighted sets", one(s.weightedSets), "Sets per week", one(s.setsPerWeek))
            StatRow("Sessions", s.sessions.toString(), "Sessions per week", one(s.sessionsPerWeek))
            StatRow("Volume", "${whole(s.volumeKg)} kg", "Reps (weighted)", whole(s.weightedReps))
            StatRow("Held time", clock(s.heldSeconds), "Last trained", lastTrained(s))
        }
    }
    s.bestLift?.let { best -> item { BestLiftCard(best) } }
    item { WeeklyChart(s.weeks) }
    if (s.topExercises.isNotEmpty()) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Top exercises", style = MaterialTheme.typography.titleMedium)
                    s.topExercises.forEach { ContributionLine(it) }
                }
            }
        }
    }
    item {
        Text(
            "A set counts 1 for the primary muscle of an exercise and 0.5 for a secondary one. " +
                "Only finished sessions are included.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatRow(labelA: String, valueA: String, labelB: String, valueB: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard(labelA, valueA, Modifier.weight(1f))
        StatCard(labelB, valueB, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BestLiftCard(best: BestLift) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Best estimated 1RM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${one(best.e1rmKg)} kg", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            val set = best.weightKg?.let { "${trimmed(it)} kg × ${best.reps}" } ?: "${best.reps} reps"
            Text("${best.exerciseName}  •  $set", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WeeklyChart(weeks: List<WeekBucket>) {
    val max = (weeks.maxOfOrNull { it.weightedSets } ?: 0.0).coerceAtLeast(1.0)
    val fmt = SimpleDateFormat("d/M", Locale.getDefault())
    val barHeight = 110.dp
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weighted sets per week", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().height(barHeight + 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                weeks.forEach { w ->
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            if (w.weightedSets > 0) one(w.weightedSets) else "",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((barHeight.value * (w.weightedSets / max)).dp.coerceAtLeast(2.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (w.weightedSets > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                weeks.forEach { w ->
                    Text(
                        fmt.format(Date(w.startMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                "Last 8 weeks, each bar is the 7 days starting on the date below it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContributionLine(c: ExerciseContribution) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (c.isPrimary) "primary" else "secondary",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("${one(c.weightedSets)} sets", style = MaterialTheme.typography.titleSmall)
    }
}

// ---- formatting ----

private fun one(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)
private fun whole(v: Double): String = String.format(Locale.getDefault(), "%,.0f", v)
private fun trimmed(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.getDefault(), "%.1f", v)

private fun clock(seconds: Double): String {
    val total = seconds.toLong()
    if (total <= 0) return "—"
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${sec}s"
        else -> "${sec}s"
    }
}

private fun lastTrained(s: MuscleStats): String {
    val at = s.lastTrainedAt ?: return "—"
    val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(at))
    return when (val d = s.daysSinceLast) {
        null -> date
        0 -> "Today"
        1 -> "Yesterday"
        else -> "$d days ago\n$date"
    }
}
