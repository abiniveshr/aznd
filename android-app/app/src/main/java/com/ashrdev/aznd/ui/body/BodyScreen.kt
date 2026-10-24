package com.ashrdev.aznd.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Straighten
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
import com.ashrdev.aznd.data.body.BodyMeasurement
import com.ashrdev.aznd.data.body.BodyWeightEntry
import com.ashrdev.aznd.domain.workout.BodyRegion
import com.ashrdev.aznd.domain.body.BodyStats
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.GraphPoint
import com.ashrdev.aznd.domain.GraphRange
import com.ashrdev.aznd.domain.GraphStyle
import com.ashrdev.aznd.domain.GraphWindow
import com.ashrdev.aznd.domain.body.MeasurementSite
import com.ashrdev.aznd.domain.workout.Muscle
import com.ashrdev.aznd.domain.workout.displayName
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.BodyModelPlaceholder
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.StatsGraph
import com.ashrdev.aznd.ui.common.rememberGraphRangeState
import com.ashrdev.aznd.ui.common.rememberSubPageBar

private const val PAGE_MEASURE = 0
private const val PAGE_STRENGTH = 1
private const val PAGE_COMPOSITION = 2

private val BodyPages = listOf(
    BarPage("Measure", Icons.Default.Straighten),
    BarPage("Strength", Icons.Default.FitnessCenter),
    BarPage("Composition", Icons.Default.Person)
)

/**
 * The Body screen (was "Measurements"): three swipeable pages, opening on the middle one.
 *   Measure | Strength | Composition
 *  - Strength: the body model; pick a muscle to open its stats.
 *  - Measure (swipe left): height and tape measurements, also shown on the model.
 *  - Composition (swipe right): bodyweight, body fat % and the lean mass worked out from them.
 * The model is a placeholder (drawn figure + dropdowns) until the real 3D view exists.
 */
@Composable
fun BodyScreen(
    viewModel: BodyViewModel,
    onOpenMuscle: (Muscle) -> Unit,
    onOpenCompositionHistory: (CompositionMetric) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    initialPage: Int = PAGE_STRENGTH
) {
    val weights by viewModel.weights.collectAsState()
    val all by viewModel.measurements.collectAsState()
    val range = rememberGraphRangeState().range
    var measurementToDelete by remember { mutableStateOf<BodyMeasurement?>(null) }
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, BodyPages.size - 1)) { BodyPages.size }
    val subPages = rememberSubPageBar(pagerState, BodyPages)

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

    ScreenScaffold(
        title = when (pagerState.currentPage) {
            PAGE_MEASURE -> "Body • Measurements"
            PAGE_COMPOSITION -> "Body • Composition"
            else -> "Body • Strength"
        },
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages
    ) { padding ->
        val bottom = padding.calculateBottomPadding() + 16.dp
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                PAGE_MEASURE -> MeasurePage(
                    all = all,
                    range = range,
                    bottomPadding = bottom,
                    onLog = { site, cm, at -> viewModel.addMeasurement(site, cm, at) },
                    onDelete = { measurementToDelete = it }
                )
                PAGE_COMPOSITION -> CompositionPage(
                    weights = weights,
                    measurements = all,
                    range = range,
                    bottomPadding = bottom,
                    onLogWeight = { kg, at -> viewModel.addWeight(kg, at) },
                    onLogBodyFat = { pct, at -> viewModel.addMeasurement(MeasurementSite.BODY_FAT, pct, at) },
                    onOpenHistory = onOpenCompositionHistory
                )
                else -> StrengthPage(bottomPadding = bottom, onOpenMuscle = onOpenMuscle)
            }
        }
    }
}

// ---- Strength ----

@Composable
private fun StrengthPage(bottomPadding: Dp, onOpenMuscle: (Muscle) -> Unit) {
    val regions = remember(onOpenMuscle) {
        BodyRegion.entries.map { region ->
            ModelRegion(
                label = region.label,
                entries = region.muscles.distinct().map { muscle ->
                    ModelEntry(muscle.displayName()) { onOpenMuscle(muscle) }
                }
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "model") {
            BodyModelPlaceholder(
                title = "Strength",
                detail = "Pick a body region, then a muscle, to open its stats."
            )
        }
        item(key = "regions") { RegionDropdownGrid(regions) }
        item(key = "hint") {
            Text(
                "Once the 3D model is in, tapping a muscle on it does the same as these dropdowns.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---- Measure ----

private val MeasureRegions = listOf(
    "Height" to listOf(MeasurementSite.HEIGHT),
    "Neck" to listOf(MeasurementSite.NECK),
    "Shoulders" to listOf(MeasurementSite.SHOULDERS),
    "Chest" to listOf(MeasurementSite.CHEST),
    "Arms" to listOf(
        MeasurementSite.BICEP_LEFT, MeasurementSite.BICEP_RIGHT,
        MeasurementSite.FOREARM_LEFT, MeasurementSite.FOREARM_RIGHT
    ),
    "Waist" to listOf(MeasurementSite.WAIST),
    "Hips" to listOf(MeasurementSite.HIPS),
    "Thighs" to listOf(MeasurementSite.THIGH_LEFT, MeasurementSite.THIGH_RIGHT),
    "Calves" to listOf(MeasurementSite.CALF_LEFT, MeasurementSite.CALF_RIGHT)
)

@Composable
private fun MeasurePage(
    all: List<BodyMeasurement>?,
    range: GraphRange,
    bottomPadding: Dp,
    onLog: (MeasurementSite, Double, Long) -> Unit,
    onDelete: (BodyMeasurement) -> Unit
) {
    var siteName by rememberSaveable { mutableStateOf(MeasurementSite.HEIGHT.name) }
    val site = MeasurementSite.fromName(siteName)?.takeIf { it != MeasurementSite.BODY_FAT } ?: MeasurementSite.HEIGHT
    val latest = all?.firstOrNull { it.site == site.name }
    val regions = remember(all) {
        MeasureRegions.map { (label, sites) ->
            ModelRegion(
                label = label,
                entries = sites.map { s ->
                    val value = all?.firstOrNull { it.site == s.name }
                        ?.let { " · ${GraphWindow.formatValue(it.valueCm)} ${s.unit}" }.orEmpty()
                    ModelEntry(s.label + value) { siteName = s.name }
                }
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "model") {
            BodyModelPlaceholder(
                title = "Measurements",
                detail = "${site.label}: ${latest?.let { "${GraphWindow.formatValue(it.valueCm)} ${site.unit}" } ?: "not logged yet"}"
            )
        }
        item(key = "regions") { RegionDropdownGrid(regions) }
        measurementSection(
            site = site,
            all = all,
            range = range,
            onLog = { cm, at -> onLog(site, cm, at) },
            onDelete = onDelete
        )
    }
}

/** One tape / height section: log form, latest + change, graph, history. */
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
        HistoryRow(
            title = "${GraphWindow.formatValue(e.valueCm)} ${site.unit}",
            subtitle = entryLabel(e.measuredAt),
            onDelete = { onDelete(e) }
        )
    }
}

// ---- Composition ----

@Composable
private fun CompositionPage(
    weights: List<BodyWeightEntry>?,
    measurements: List<BodyMeasurement>?,
    range: GraphRange,
    bottomPadding: Dp,
    onLogWeight: (Double, Long) -> Unit,
    onLogBodyFat: (Double, Long) -> Unit,
    onOpenHistory: (CompositionMetric) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        compositionSection(
            weights = weights,
            measurements = measurements,
            range = range,
            onLogWeight = onLogWeight,
            onLogBodyFat = onLogBodyFat,
            onOpenHistory = onOpenHistory
        )
    }
}
