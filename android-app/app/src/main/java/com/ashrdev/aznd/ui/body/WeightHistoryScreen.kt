package com.ashrdev.aznd.ui.body

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.StatsGraph

/**
 * History > Weight History: the trend graphs of bodyweight, 7-day average, body fat and lean mass.
 * (Logging weights and body fat stays on Body > Composition.)
 */
@Composable
fun WeightHistoryScreen(
    viewModel: BodyViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val weights by viewModel.weights.collectAsState()
    val measurements by viewModel.measurements.collectAsState()

    ScreenScaffold(
        title = "Weight history",
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings
    ) { padding ->
        val list = weights
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp)
        ) {
            when {
                list == null -> Unit
                list.isEmpty() -> Text(
                    "No weights logged yet. Log your weight on Body > Composition and its graphs show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> {
                    val modes = remember(list, measurements) { weightTrendModes(list, measurements.orEmpty()) }
                    StatsGraph(modes = modes, title = "Trend")
                }
            }
        }
    }
}
