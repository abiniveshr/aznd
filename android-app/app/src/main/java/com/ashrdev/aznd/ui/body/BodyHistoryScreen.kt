package com.ashrdev.aznd.ui.body

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.domain.GraphWindow
import com.ashrdev.aznd.domain.MeasurementSite
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.components.Card

private const val HISTORY_MEASURE = 0
private const val HISTORY_STRENGTH = 1
private const val HISTORY_COMPOSITION = 2

private val BodyHistoryPages = listOf(
    BarPage("Measure", Icons.Default.Straighten),
    BarPage("Strength", Icons.Default.FitnessCenter),
    BarPage("Composition", Icons.Default.Person)
)

/**
 * History > Body Stats History (replaces the old Weight History screen). Same page order as the
 * Body screen:
 *   Measure | Strength | Composition
 *  - Composition (swipe right): pick Bodyweight or Body fat to open its history
 *    (calendar | graph | logs, see [CompositionHistoryScreen]).
 *  - Strength (centre) and Measure (swipe left): placeholders for now.
 * Opens on Composition, the only page with content yet; make it HISTORY_STRENGTH to open in the
 * middle like the Body screen does.
 */
@Composable
fun BodyHistoryScreen(
    viewModel: BodyViewModel,
    onOpenComposition: (CompositionMetric) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val weights by viewModel.weights.collectAsState()
    val measurements by viewModel.measurements.collectAsState()
    val pagerState = rememberPagerState(initialPage = HISTORY_COMPOSITION) { BodyHistoryPages.size }
    val subPages = rememberSubPageBar(pagerState, BodyHistoryPages)

    ScreenScaffold(
        title = when (pagerState.currentPage) {
            HISTORY_MEASURE -> "Body history • Measurements"
            HISTORY_COMPOSITION -> "Body history • Composition"
            else -> "Body history • Strength"
        },
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages
    ) { padding ->
        val bottom = padding.calculateBottomPadding() + 16.dp
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                HISTORY_MEASURE -> HistoryPlaceholderPage("Measurements history is coming soon.")
                HISTORY_COMPOSITION -> CompositionHistoryPage(
                    latestWeight = weights?.firstOrNull()?.let { "${GraphWindow.formatValue(it.weightKg)} kg · ${entryLabel(it.measuredAt)}" },
                    latestBodyFat = measurements.orEmpty()
                        .firstOrNull { it.site == MeasurementSite.BODY_FAT.name }
                        ?.let { "${GraphWindow.formatValue(it.valueCm)} % · ${entryLabel(it.measuredAt)}" },
                    bottomPadding = bottom,
                    onOpen = onOpenComposition
                )
                else -> HistoryPlaceholderPage("Strength history is coming soon.")
            }
        }
    }
}

@Composable
private fun HistoryPlaceholderPage(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CompositionHistoryPage(
    latestWeight: String?,
    latestBodyFat: String?,
    bottomPadding: Dp,
    onOpen: (CompositionMetric) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "weight") {
            MetricCard(
                icon = Icons.Default.MonitorWeight,
                title = CompositionMetric.WEIGHT.label,
                subtitle = latestWeight ?: "Nothing logged yet.",
                onClick = { onOpen(CompositionMetric.WEIGHT) }
            )
        }
        item(key = "body-fat") {
            MetricCard(
                icon = Icons.Default.Person,
                title = CompositionMetric.BODY_FAT.label,
                subtitle = latestBodyFat ?: "Nothing logged yet.",
                onClick = { onOpen(CompositionMetric.BODY_FAT) }
            )
        }
    }
}

@Composable
private fun MetricCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
