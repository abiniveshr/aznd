package com.ashrdev.aznd.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.StatsGraph
import com.ashrdev.aznd.ui.common.rememberGraphRangeState
import com.ashrdev.aznd.ui.components.Card

private const val SECTION_WEIGHT = "WEIGHT"

/**
 * Everything about the body in one page, as sections (chips): Bodyweight, Body fat %, and tape
 * measurements in cm. Each section has a log form, latest/change cards, a graph and its history.
 */
@Composable
fun MeasurementsScreen(
    viewModel: BodyViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val weights by viewModel.weights.collectAsState()
    val all by viewModel.measurements.collectAsState()
    val range = rememberGraphRangeState().range
    var section by rememberSaveable { mutableStateOf(SECTION_WEIGHT) }
    val site = MeasurementSite.fromName(section) // null = the Bodyweight section
    var weightToDelete by remember { mutableStateOf<BodyWeightEntry?>(null) }
    var measurementToDelete by remember { mutableStateOf<BodyMeasurement?>(null) }

    weightToDelete?.let { e ->
        ConfirmDeleteDialog(
            what = "${GraphWindow.formatValue(e.weightKg)} kg on ${entryLabel(e.measuredAt)}",
            onConfirm = {
                viewModel.deleteWeight(e.id)
                weightToDelete = null
            },
            onDismiss = { weightToDelete = null }
        )
    }
    measurementToDelete?.let { e ->
        val s = MeasurementSite.fromName(e.site)
        ConfirmDeleteDialog(
            what = "${s?.label ?: "Entry"}: ${GraphWindow.formatValue(e.valueCm)} ${s?.unit ?: ""} on ${entryLabel(e.measuredAt)}",
            onConfirm = {
                viewModel.deleteMeasurement(e.id)
                measurementToDelete = null
            },
            onDismiss = { measurementToDelete = null }
        )
    }

    ScreenScaffold(title = "Measurements", onBack = onBack, onHome = onHome, onOpenSettings = onOpenSettings) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "sections") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = SECTION_WEIGHT) {
                        FilterChip(
                            selected = site == null,
                            onClick = { section = SECTION_WEIGHT },
                            label = { Text("Bodyweight") }
                        )
                    }
                    items(MeasurementSite.values().toList(), key = { it.name }) { s ->
                        FilterChip(selected = s == site, onClick = { section = s.name }, label = { Text(s.label) })
                    }
                }
            }
            if (site == null) {
                bodyweightSection(
                    entries = weights,
                    measurements = all,
                    range = range,
                    onLog = { kg, at -> viewModel.addWeight(kg, at) },
                    onDelete = { weightToDelete = it }
                )
            } else {
                measurementSection(
                    site = site,
                    all = all,
                    range = range,
                    onLog = { cm, at -> viewModel.addMeasurement(site, cm, at) },
                    onDelete = { measurementToDelete = it }
                )
            }
        }
    }
}

/** One tape / body-fat section: log form, latest + change, graph, history. */
private fun LazyListScope.measurementSection(
    site: MeasurementSite,
    all: List<BodyMeasurement>?,
    range: GraphRange,
    onLog: (value: Double, atMs: Long) -> Unit,
    onDelete: (BodyMeasurement) -> Unit
) {
    item(key = "m-log-${site.name}") {
        LogEntryCard(
            label = site.label, unit = site.unit,
            min = BodyStats.min(site), max = BodyStats.max(site)
        ) { v, at -> onLog(v, at) }
    }

    val list = all ?: return
    val mine = list.filter { it.site == site.name } // newest first
    if (mine.isEmpty()) {
        item(key = "m-empty-${site.name}") {
            Text(
                "No ${site.label.lowercase()} entries yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val ascending = mine.sortedBy { it.measuredAt }
    item(key = "m-cards-${site.name}") {
        val shown = GraphWindow.select(ascending, range, System.currentTimeMillis()) { it.measuredAt }
        val change = if (shown.size >= 2) shown.last().valueCm - shown.first().valueCm else 0.0
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Latest", "${GraphWindow.formatValue(mine.first().valueCm)} ${site.unit}", Modifier.weight(1f))
            StatCard("Change in range", "${BodyStats.signed(change)} ${site.unit}", Modifier.weight(1f))
        }
    }
    item(key = "m-graph-${site.name}") {
        val modes = remember(ascending) {
            listOf(
                GraphMode(
                    site.name, site.label, site.unit, GraphStyle.LINE,
                    ascending.map { GraphPoint(it.measuredAt, it.valueCm) },
                    startAtZero = false
                )
            )
        }
        StatsGraph(modes = modes, title = site.label)
    }
    item(key = "m-history-title-${site.name}") { Text("History", style = MaterialTheme.typography.titleMedium) }
    items(mine, key = { "m${it.id}" }) { e ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${GraphWindow.formatValue(e.valueCm)} ${site.unit}", style = MaterialTheme.typography.titleMedium)
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
