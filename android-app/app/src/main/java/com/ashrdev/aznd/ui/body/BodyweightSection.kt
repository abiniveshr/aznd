package com.ashrdev.aznd.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.body.BodyMeasurement
import com.ashrdev.aznd.data.body.BodyWeightEntry
import com.ashrdev.aznd.domain.BodyStats
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.GraphPoint
import com.ashrdev.aznd.domain.GraphRange
import com.ashrdev.aznd.domain.GraphStyle
import com.ashrdev.aznd.domain.GraphWindow
import com.ashrdev.aznd.domain.MeasurementSite
import com.ashrdev.aznd.ui.common.StatsGraph
import com.ashrdev.aznd.ui.components.Card

/**
 * The "Bodyweight" section of the Measurements page: log a weight, see the latest weight, body fat
 * and lean mass, the trend, and the history. The latest entry is what new workouts use.
 */
fun LazyListScope.bodyweightSection(
    entries: List<BodyWeightEntry>?,
    measurements: List<BodyMeasurement>?,
    range: GraphRange,
    onLog: (kg: Double, atMs: Long) -> Unit,
    onDelete: (BodyWeightEntry) -> Unit
) {
    item(key = "weight-log") {
        LogEntryCard(
            label = "Bodyweight", unit = "kg",
            min = BodyStats.MIN_WEIGHT_KG, max = BodyStats.MAX_WEIGHT_KG
        ) { kg, at -> onLog(kg, at) }
    }

    val list = entries ?: return
    if (list.isEmpty()) {
        item(key = "weight-empty") {
            Text(
                "Log your first weight above. New workouts use your latest weight for bodyweight " +
                    "exercises (75 kg until you log one).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val ascending = list.sortedBy { it.measuredAt }
    item(key = "weight-cards") {
        val shown = GraphWindow.select(ascending, range, System.currentTimeMillis()) { it.measuredAt }
        val change = if (shown.size >= 2) shown.last().weightKg - shown.first().weightKg else 0.0
        val bodyFat = measurements?.firstOrNull { it.site == MeasurementSite.BODY_FAT.name }?.valueCm
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Current", "${GraphWindow.formatValue(list.first().weightKg)} kg", Modifier.weight(1f))
                StatCard("Change in range", "${BodyStats.signed(change)} kg", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Body fat", bodyFat?.let { "${GraphWindow.formatValue(it)} %" } ?: "—", Modifier.weight(1f))
                StatCard(
                    "Lean mass",
                    bodyFat?.let { "${GraphWindow.formatValue(BodyStats.leanMassKg(list.first().weightKg, it))} kg" } ?: "—",
                    Modifier.weight(1f)
                )
            }
            Text(
                "Log body fat % in the Body fat section above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    item(key = "weight-graph") {
        val modes = remember(ascending) {
            val raw = ascending.map { GraphPoint(it.measuredAt, it.weightKg) }
            listOf(
                GraphMode("weight", "Weight", "kg", GraphStyle.LINE, raw, startAtZero = false),
                GraphMode("avg", "7-day average", "kg", GraphStyle.LINE, BodyStats.rollingAverage(raw), startAtZero = false)
            )
        }
        StatsGraph(modes = modes, title = "Trend")
    }
    item(key = "weight-history-title") { Text("History", style = MaterialTheme.typography.titleMedium) }
    items(list, key = { "w${it.id}" }) { e ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${GraphWindow.formatValue(e.weightKg)} kg", style = MaterialTheme.typography.titleMedium)
                    Text(
                        entryLabel(e.measuredAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onDelete(e) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete entry", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
internal fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
