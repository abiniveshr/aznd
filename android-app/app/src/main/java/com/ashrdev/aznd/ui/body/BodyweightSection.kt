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
import com.ashrdev.aznd.ui.components.Card

/**
 * The Composition page of the Body screen: log bodyweight and body fat %, see the latest numbers
 * with lean mass (calculated from the latest weight and body fat), the trends, and the history.
 * The latest bodyweight is what new workouts use; the Macros goal reads weight and body fat from here.
 */
fun LazyListScope.compositionSection(
    weights: List<BodyWeightEntry>?,
    measurements: List<BodyMeasurement>?,
    range: GraphRange,
    onLogWeight: (kg: Double, atMs: Long) -> Unit,
    onLogBodyFat: (percent: Double, atMs: Long) -> Unit,
    onDeleteWeight: (BodyWeightEntry) -> Unit,
    onDeleteBodyFat: (BodyMeasurement) -> Unit
) {
    item(key = "weight-log") {
        LogEntryCard(
            label = "Bodyweight", unit = "kg",
            min = BodyStats.MIN_WEIGHT_KG, max = BodyStats.MAX_WEIGHT_KG
        ) { kg, at -> onLogWeight(kg, at) }
    }
    item(key = "fat-log") {
        LogEntryCard(
            label = "Body fat", unit = "%",
            min = BodyStats.MIN_BODY_FAT, max = BodyStats.MAX_BODY_FAT
        ) { pct, at -> onLogBodyFat(pct, at) }
    }

    val weightList = weights ?: return
    val fatList = measurements.orEmpty().filter { it.site == MeasurementSite.BODY_FAT.name } // newest first

    if (weightList.isEmpty()) {
        item(key = "weight-empty") {
            Text(
                "Log your first weight above. New workouts use your latest weight for bodyweight " +
                    "exercises (75 kg until you log one).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        val ascending = weightList.sortedBy { it.measuredAt }
        item(key = "cards") {
            val shown = GraphWindow.select(ascending, range, System.currentTimeMillis()) { it.measuredAt }
            val change = if (shown.size >= 2) shown.last().weightKg - shown.first().weightKg else 0.0
            val weight = weightList.first().weightKg
            val bodyFat = fatList.firstOrNull()?.valueCm
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard("Weight", "${GraphWindow.formatValue(weight)} kg", Modifier.weight(1f))
                    StatCard("Change in range", "${BodyStats.signed(change)} kg", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard("Body fat", bodyFat?.let { "${GraphWindow.formatValue(it)} %" } ?: "—", Modifier.weight(1f))
                    StatCard(
                        "Lean mass",
                        bodyFat?.let { "${GraphWindow.formatValue(BodyStats.leanMassKg(weight, it))} kg" } ?: "—",
                        Modifier.weight(1f)
                    )
                }
                Text(
                    if (bodyFat == null) "Log a body fat % above to see your lean mass."
                    else "Lean mass = weight × (1 − body fat %), from your latest entries.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "weight-history-title") { Text("Weight history", style = MaterialTheme.typography.titleMedium) }
        items(weightList, key = { "w${it.id}" }) { e ->
            HistoryRow(
                title = "${GraphWindow.formatValue(e.weightKg)} kg",
                subtitle = entryLabel(e.measuredAt),
                onDelete = { onDeleteWeight(e) }
            )
        }
    }

    if (fatList.isNotEmpty()) {
        item(key = "fat-history-title") { Text("Body fat history", style = MaterialTheme.typography.titleMedium) }
        items(fatList, key = { "f${it.id}" }) { e ->
            HistoryRow(
                title = "${GraphWindow.formatValue(e.valueCm)} %",
                subtitle = entryLabel(e.measuredAt),
                onDelete = { onDeleteBodyFat(e) }
            )
        }
    }
}

/**
 * The trend graph views of the weight history screen: weight, 7-day average and, when body fat is
 * logged, body fat and lean mass. Both lists in any order.
 */
fun weightTrendModes(weights: List<BodyWeightEntry>, measurements: List<BodyMeasurement>): List<GraphMode> {
    val raw = weights.sortedBy { it.measuredAt }.map { GraphPoint(it.measuredAt, it.weightKg) }
    val fat = measurements.filter { it.site == MeasurementSite.BODY_FAT.name }
        .sortedBy { it.measuredAt }
        .map { GraphPoint(it.measuredAt, it.valueCm) }
    return buildList {
        add(GraphMode("weight", "Weight", "kg", GraphStyle.LINE, raw, startAtZero = false))
        add(GraphMode("avg", "7-day average", "kg", GraphStyle.LINE, BodyStats.rollingAverage(raw), startAtZero = false))
        if (fat.isNotEmpty()) {
            add(GraphMode("fat", "Body fat", "%", GraphStyle.LINE, fat, startAtZero = false))
            add(GraphMode("lean", "Lean mass", "kg", GraphStyle.LINE, BodyStats.leanMassSeries(raw, fat), startAtZero = false))
        }
    }
}

/** One history line with a delete button. */
@Composable
internal fun HistoryRow(title: String, subtitle: String, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete entry", tint = MaterialTheme.colorScheme.error)
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
