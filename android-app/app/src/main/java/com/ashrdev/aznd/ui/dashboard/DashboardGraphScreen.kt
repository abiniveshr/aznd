package com.ashrdev.aznd.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ashrdev.aznd.data.workout.SessionWithSets
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.ui.common.CompareGraph
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.StatsGraph
import com.ashrdev.aznd.ui.common.ThirdAction
import com.ashrdev.aznd.ui.theme.Space

@Composable
fun rememberDashboardModes(viewModel: DashboardViewModel, sessions: List<SessionWithSets>): List<GraphMode> {
    // Used until the dashboard's own sources are bound (they are, from Home).
    LaunchedEffect(viewModel, sessions) { viewModel.seedSessions(sessions) }
    val modes by viewModel.modes.collectAsState()
    return modes
}

@Composable
fun DashboardGraphScreen(
    widget: DashWidget?,
    modes: List<GraphMode>,
    onEdit: (Int) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val chosen = widget?.variables.orEmpty().mapNotNull { id -> modes.firstOrNull { it.id == id } }
    val withData = modes.filter { it.points.isNotEmpty() }
    val shown = (chosen + withData).distinctBy { it.id }
    ScreenScaffold(
        title = if (chosen.isEmpty()) "Graph" else chosen.joinToString(" vs ") { it.label },
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        third = widget?.let { w -> ThirdAction(Icons.Default.Tune, "Edit widget") { onEdit(w.id) } }
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(start = Space.lg, end = Space.lg, top = Space.md, bottom = padding.calculateBottomPadding() + Space.md)
        when {
            shown.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) {
                Text(
                    "Nothing logged yet to graph.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            chosen.size >= 2 -> CompareGraph(
                variables = shown,
                modifier = modifier,
                title = null,
                fillHeight = true,
                initial = chosen.map { it.id }
            )
            else -> StatsGraph(
                modes = shown,
                modifier = modifier,
                title = null,
                fillHeight = true,
                comparable = true
            )
        }
    }
}
