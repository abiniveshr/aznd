package com.ashrdev.aznd.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.body.BodyMeasurement
import com.ashrdev.aznd.data.body.BodyWeightEntry
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.GraphWindow
import com.ashrdev.aznd.domain.body.MeasurementSite
import com.ashrdev.aznd.ui.calendar.BottomAnchoredPage
import com.ashrdev.aznd.ui.calendar.DayDecoration
import com.ashrdev.aznd.ui.calendar.DayFill
import com.ashrdev.aznd.ui.calendar.ZoomCalendar
import com.ashrdev.aznd.ui.calendar.SelectedDayHeader
import com.ashrdev.aznd.ui.calendar.dayKeyOf
import com.ashrdev.aznd.ui.calendar.monthKeyOf
import com.ashrdev.aznd.ui.calendar.rememberCalendarState
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.LogItem
import com.ashrdev.aznd.ui.common.LogsViewer
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.StatsGraph
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.components.Card
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The two things on the Composition history page. [routeId] is what the nav route carries. */
enum class CompositionMetric(val routeId: String, val label: String, val unit: String) {
    WEIGHT("weight", "Bodyweight", "kg"),
    BODY_FAT("body_fat", "Body fat", "%");

    companion object {
        fun fromRouteId(id: String?): CompositionMetric = entries.firstOrNull { it.routeId == id } ?: WEIGHT
    }
}

private const val PAGE_CALENDAR = 0
private const val PAGE_GRAPH = 1
private const val PAGE_LOGS = 2

private val CompositionHistoryPages = listOf(
    BarPage("Calendar", Icons.Default.Today),
    BarPage("Graph", Icons.Default.BarChart),
    BarPage("Logs", Icons.AutoMirrored.Filled.List)
)

/** One reading of the opened metric, whichever table it comes from. */
private data class MetricEntry(val id: Long, val timeMs: Long, val value: Double)

private val timeOnly = SimpleDateFormat("HH:mm", Locale.getDefault())

/**
 * History > Body Stats History > Composition > Bodyweight / Body fat. Three swipeable pages,
 * opening on the middle one:
 *   Calendar | Graph | Logs
 *  - Calendar (swipe left): days with a reading are highlighted; tap a day to see its readings.
 *  - Graph (centre): the trend, using the app-wide graph range.
 *  - Logs (swipe right): every reading, filtered with the common date-range selector.
 */
@Composable
fun CompositionHistoryScreen(
    metric: CompositionMetric,
    viewModel: BodyViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val weights by viewModel.weights.collectAsState()
    val measurements by viewModel.measurements.collectAsState()
    val pagerState = rememberPagerState(initialPage = PAGE_GRAPH) { CompositionHistoryPages.size }
    val subPages = rememberSubPageBar(pagerState, CompositionHistoryPages)

    // null while the first read is still running
    val entries: List<MetricEntry>? = remember(metric, weights, measurements) {
        when (metric) {
            CompositionMetric.WEIGHT -> weights?.map { MetricEntry(it.id, it.measuredAt, it.weightKg) }
            CompositionMetric.BODY_FAT -> measurements
                ?.filter { it.site == MeasurementSite.BODY_FAT.name }
                ?.map { MetricEntry(it.id, it.measuredAt, it.valueCm) }
        }
    }

    ScreenScaffold(
        title = "${metric.label} • " + when (pagerState.currentPage) {
            PAGE_CALENDAR -> "Calendar"
            PAGE_LOGS -> "Logs"
            else -> "Graph"
        },
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages
    ) { padding ->
        val bottom = padding.calculateBottomPadding() + 16.dp
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                PAGE_CALENDAR -> CalendarPage(metric, entries, bottom)
                PAGE_LOGS -> LogsPage(metric, entries, viewModel, bottom)
                else -> GraphPage(metric, weights, measurements, entries, bottom)
            }
        }
    }
}

// ---- Calendar ----

@Composable
private fun CalendarPage(metric: CompositionMetric, entries: List<MetricEntry>?, bottomPadding: Dp) {
    val calendarState = rememberCalendarState()
    val byDay = remember(entries) { entries.orEmpty().groupBy { dayKeyOf(it.timeMs) } }
    val validMonths = remember(byDay) { byDay.keys.map { monthKeyOf(it) }.distinct() }

    BottomAnchoredPage(
        bottomPadding = bottomPadding,
        top = {
            val day = calendarState.selectedDay
            if (day != null) {
                SelectedDayHeader(dayKey = day, isFavorite = false, onToggleFavorite = null)
                val dayEntries = byDay[day].orEmpty().sortedByDescending { it.timeMs }
                if (dayEntries.isEmpty()) {
                    Text(
                        "No ${metric.label.lowercase()} logged on this day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    dayEntries.forEach { e ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${GraphWindow.formatValue(e.value)} ${metric.unit}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    timeOnly.format(Date(e.timeMs)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        bottom = {
            ZoomCalendar(
                decorate = { dayKey ->
                    if (dayKey in byDay) DayDecoration(fill = DayFill.Highlight) else DayDecoration.None
                },
                validMonths = validMonths,
                state = calendarState
            )
        }
    )
}

// ---- Graph ----

@Composable
private fun GraphPage(
    metric: CompositionMetric,
    weights: List<BodyWeightEntry>?,
    measurements: List<BodyMeasurement>?,
    entries: List<MetricEntry>?,
    bottomPadding: Dp
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding)
    ) {
        when {
            entries == null -> Unit
            entries.isEmpty() -> Text(
                "No ${metric.label.lowercase()} logged yet. Log it on Body > Composition and its graph shows up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> {
                val modes = remember(metric, weights, measurements) {
                    val all: List<GraphMode> = weightTrendModes(weights.orEmpty(), measurements.orEmpty())
                    val wanted = when (metric) {
                        CompositionMetric.WEIGHT -> listOf("weight", "avg", "lean")
                        CompositionMetric.BODY_FAT -> listOf("fat")
                    }
                    all.filter { it.id in wanted }
                }
                StatsGraph(modes = modes, title = metric.label)
            }
        }
    }
}

// ---- Logs ----

@Composable
private fun LogsPage(
    metric: CompositionMetric,
    entries: List<MetricEntry>?,
    viewModel: BodyViewModel,
    bottomPadding: Dp
) {
    val items = remember(metric, entries) {
        entries?.map { e ->
            LogItem(
                key = "${metric.routeId}-${e.id}",
                timeMs = e.timeMs,
                title = "${GraphWindow.formatValue(e.value)} ${metric.unit}",
                onDelete = {
                    when (metric) {
                        CompositionMetric.WEIGHT -> viewModel.deleteWeight(e.id)
                        CompositionMetric.BODY_FAT -> viewModel.deleteMeasurement(e.id)
                    }
                }
            )
        }
    }
    val valueByKey = remember(items, entries) {
        entries.orEmpty().associate { "${metric.routeId}-${it.id}" to it.value }
    }

    LogsViewer(
        logs = items,
        bottomPadding = bottomPadding,
        emptyText = "No ${metric.label.lowercase()} logged yet.",
        summary = { shown ->
            val values = shown.mapNotNull { valueByKey[it.key] }
            if (values.isEmpty()) null
            else "avg ${GraphWindow.formatValue(values.average())} ${metric.unit}"
        }
    )
}
