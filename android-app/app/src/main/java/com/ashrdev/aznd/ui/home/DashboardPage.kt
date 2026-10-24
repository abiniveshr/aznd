package com.ashrdev.aznd.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.dashboard.DashArranged
import com.ashrdev.aznd.ui.dashboard.DashCell
import com.ashrdev.aznd.ui.dashboard.DashMetrics
import com.ashrdev.aznd.ui.dashboard.DashTile
import com.ashrdev.aznd.ui.dashboard.DashWidget
import com.ashrdev.aznd.ui.dashboard.DashboardData
import com.ashrdev.aznd.ui.dashboard.DashboardLayout
import com.ashrdev.aznd.ui.dashboard.DashboardTargets
import com.ashrdev.aznd.ui.dashboard.DashboardText
import com.ashrdev.aznd.ui.dashboard.rememberDashMeasure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

private val Side = 16.dp
private val Top = 12.dp

@Composable
fun DashboardPage(
    widgets: List<DashWidget>,
    data: DashboardData,
    onNavigate: (String) -> Unit,
    onOpenSettings: (Int) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    if (widgets.isEmpty()) {
        EmptyDashboard(onOpenSettings, bottomPadding, modifier)
        return
    }
    val measure = rememberDashMeasure()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = (maxWidth - Side * 2).value
        val viewport = (maxHeight - Top - bottomPadding).value.coerceAtLeast(DashMetrics.GAP * 10)
        val tiles = remember(widgets, data, measure) { widgets.map { it.id to DashboardText.shape(it, data, measure) } }
        val signature = remember(widgets, data) { widgets.joinToString("~") { DashboardText.signature(it, data) } }
        val cacheKey = "$signature@$width@$viewport"
        val arranged by produceState(DashboardLayout.cached(cacheKey), cacheKey) {
            value = withContext(Dispatchers.Default) { DashboardLayout.arrangeCached(cacheKey, tiles, width, viewport, DashMetrics.GAP) }
        }
        arranged?.let { a -> DashboardGrid(a, widgets, data, viewport, bottomPadding, onNavigate, onOpenSettings) }
    }
}

@Composable
private fun DashboardGrid(
    a: DashArranged,
    widgets: List<DashWidget>,
    data: DashboardData,
    viewport: Float,
    bottomPadding: Dp,
    onNavigate: (String) -> Unit,
    onOpenSettings: (Int) -> Unit
) {
    val content = max(a.height, 0f)
    val byId = widgets.associateBy { it.id }
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), enabled = content > viewport + 1f)
            .padding(start = Side, end = Side, top = Top, bottom = bottomPadding)
    ) {
        Box(Modifier.fillMaxWidth().height(content.dp)) {
            for (cell in a.cells) {
                val widget = byId[cell.id] ?: continue
                key(cell.id) {
                    PlacedTile(
                        widget = widget,
                        cell = cell,
                        data = data,
                        onOpen = { onNavigate(DashboardTargets.route(widget, data)) },
                        onHold = { onOpenSettings(widget.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlacedTile(widget: DashWidget, cell: DashCell, data: DashboardData, onOpen: () -> Unit, onHold: () -> Unit) {
    val x by animateDpAsState(cell.x.dp, label = "tileX")
    val y by animateDpAsState(cell.y.dp, label = "tileY")
    val w by animateDpAsState(cell.w.dp, label = "tileW")
    val h by animateDpAsState(cell.h.dp, label = "tileH")
    DashTile(
        widget = widget,
        cell = cell,
        data = data,
        onOpen = onOpen,
        onHold = onHold,
        modifier = Modifier.offset(x, y).size(w, h)
    )
}

@Composable
private fun EmptyDashboard(onOpenSettings: (Int) -> Unit, bottomPadding: Dp, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(bottom = bottomPadding), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Your dashboard is empty", style = MaterialTheme.typography.titleMedium)
            Text(
                "Add calories, streaks, graphs, shortcuts and more.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = { onOpenSettings(-1) }) { Text("Add widgets") }
        }
    }
}
