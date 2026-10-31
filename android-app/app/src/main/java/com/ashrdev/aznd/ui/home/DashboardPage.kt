package com.ashrdev.aznd.ui.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.dashboard.DashBackground
import com.ashrdev.aznd.ui.dashboard.DashGrid
import com.ashrdev.aznd.ui.dashboard.DashMetrics
import com.ashrdev.aznd.ui.dashboard.DashPageDots
import com.ashrdev.aznd.ui.dashboard.DashPreset
import com.ashrdev.aznd.ui.dashboard.DashboardData
import com.ashrdev.aznd.ui.dashboard.DashboardStore
import com.ashrdev.aznd.ui.dashboard.DashboardTargets
import com.ashrdev.aznd.ui.dashboard.LocalDashLook
import com.ashrdev.aznd.ui.dashboard.ProvideDashLook
import com.ashrdev.aznd.ui.dashboard.rememberDashArrangement
import com.ashrdev.aznd.ui.navigation.Destination

/**
 * The dashboard: its active preset pages, one above the other — scroll up and down to move between
 * them (a tall page scrolls through itself first). Tap a tile to open it; long-press anywhere to
 * edit the page you're on ([onEdit] with -1, or [Destination.DashboardEdit.ADD_WIDGET] from an empty page).
 */
@Composable
fun DashboardPage(
    store: DashboardStore,
    data: DashboardData,
    onNavigate: (String) -> Unit,
    onEdit: (presetId: Int, widget: Int) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val pages = store.activePresets
    val pager = rememberPagerState { pages.size }
    ProvideDashLook(store.look) {
        Box(modifier.fillMaxSize()) {
            DashBackground(store.look)
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                key = { pages.getOrNull(it)?.id ?: -it - 1 }
            ) { index ->
                val preset = pages.getOrNull(index) ?: return@VerticalPager
                PresetPage(preset, data, bottomPadding, onNavigate, onEdit)
            }
            if (pages.size > 1) {
                DashPageDots(
                    count = pages.size,
                    position = { pager.currentPage + pager.currentPageOffsetFraction },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp, bottom = bottomPadding)
                )
            }
        }
    }
}

@Composable
private fun PresetPage(
    preset: DashPreset,
    data: DashboardData,
    bottomPadding: Dp,
    onNavigate: (String) -> Unit,
    onEdit: (presetId: Int, widget: Int) -> Unit
) {
    val look = LocalDashLook.current
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = (maxWidth - look.side.dp * 2).value
        val viewport = (maxHeight - look.top.dp - bottomPadding).value.coerceAtLeast(DashMetrics.GAP * 10)
        val arrangement = rememberDashArrangement(preset.widgets, preset.layout, data, width, viewport)
        val a = arrangement.arranged
        val aNow by rememberUpdatedState(a)
        val dataNow by rememberUpdatedState(data)
        val presetNow by rememberUpdatedState(preset)
        val lookNow by rememberUpdatedState(look)
        // Taps and holds are taken here, around the scrolling grid: the point is turned into grid
        // coordinates by hand (side and top margins, and how far the page is scrolled).
        val gestures = Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { p ->
                    val cell = aNow?.hit(p.x / density - lookNow.side, (p.y + scroll.value) / density - lookNow.top) ?: return@detectTapGestures
                    val widget = presetNow.widgets.firstOrNull { it.id == cell.id } ?: return@detectTapGestures
                    onNavigate(DashboardTargets.route(widget, dataNow, presetNow.id))
                },
                onLongPress = { onEdit(presetNow.id, -1) }
            )
        }
        if (preset.widgets.isEmpty()) {
            EmptyPage(preset.name, bottomPadding, gestures) { onEdit(preset.id, Destination.DashboardEdit.ADD_WIDGET) }
        } else if (a != null) {
            DashGrid(
                a = a,
                widgets = preset.widgets,
                data = data,
                viewport = viewport,
                bottomPadding = bottomPadding,
                scroll = scroll,
                modifier = gestures
            )
        }
    }
}

@Composable
private fun EmptyPage(name: String, bottomPadding: Dp, gestures: Modifier, onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize().then(gestures).padding(bottom = bottomPadding), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("$name is empty", style = MaterialTheme.typography.titleMedium)
            Text(
                "Add calories, streaks, graphs, shortcuts and more.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onAdd) { Text("Add widgets") }
        }
    }
}
