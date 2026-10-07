package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.GraphPoint
import com.ashrdev.aznd.domain.GraphStyle
import com.ashrdev.aznd.domain.GraphWindow
import com.ashrdev.aznd.domain.displayName
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.StatsGraph
import com.ashrdev.aznd.ui.common.rememberGraphRangeState
import com.ashrdev.aznd.ui.components.Card

/**
 * Statistics of ONE muscle group (Dashboard -> Muscle data). Two numbers for the chosen time
 * range, then the shared graph with three local modes: volume per session, estimated 1RM, sets.
 */
@Composable
fun MuscleStatsScreen(
    viewModel: MuscleStatsViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val sessions by viewModel.sessions.collectAsState()
    val range = rememberGraphRangeState().range

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
            val list = sessions
            when {
                list == null -> item { Text("Loading…", style = MaterialTheme.typography.bodyMedium) }
                list.isEmpty() -> item {
                    Text(
                        "No sets logged for ${viewModel.muscle.displayName().lowercase()} yet. Finish a workout " +
                            "with an exercise that trains it and the numbers show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    item {
                        val now = System.currentTimeMillis()
                        val shown = GraphWindow.select(list, range, now) { it.finishedAt }
                        val weeks = GraphWindow.windowWeeks(list, range, now) { it.finishedAt }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            StatCard("Sets per week", GraphWindow.formatValue(shown.sumOf { it.sets } / weeks), Modifier.weight(1f))
                            StatCard("Total volume", "${GraphWindow.formatValue(shown.sumOf { it.volumeKg })} kg", Modifier.weight(1f))
                        }
                    }
                    item {
                        val modes = remember(list) {
                            listOf(
                                GraphMode(
                                    "volume", "Volume / session", "kg", GraphStyle.BARS,
                                    list.map { GraphPoint(it.finishedAt, it.volumeKg) }
                                ),
                                GraphMode(
                                    "e1rm", "Estimated 1RM", "kg", GraphStyle.LINE,
                                    list.filter { it.e1rmKg != null }.map { GraphPoint(it.finishedAt, it.e1rmKg ?: 0.0, it.e1rmDetail, it.e1rmDisplay) },
                                    startAtZero = false
                                ),
                                GraphMode(
                                    "sets", "Sets", "sets", GraphStyle.BARS,
                                    list.map { GraphPoint(it.finishedAt, it.sets) }
                                )
                            )
                        }
                        StatsGraph(
                            modes = modes,
                            title = "Per session",
                            footnote = "Primary muscle counts fully, secondary muscles count half. " +
                                "Bodyweight exercises use bodyweight + added weight. 1RM uses exercises where this is the primary muscle."
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
