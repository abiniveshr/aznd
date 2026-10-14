package com.ashrdev.aznd.ui.workout

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.ashrdev.aznd.domain.GraphWindow
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.ThirdAction
import com.ashrdev.aznd.ui.common.rememberGraphRangeState

@Composable
fun ExerciseProgressScreen(
    workoutId: Long,
    exerciseId: Long,
    viewModel: WorkoutViewModel,
    useTargets: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val data = rememberExerciseStats(viewModel, workoutId, exerciseId, useTargets)
    val rangeState = rememberGraphRangeState()

    if (!data.loaded) return

    val shown = GraphWindow.select(data.points, rangeState.range, System.currentTimeMillis()) { it.startedAt }
    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()

    ScreenScaffold(
        title = data.name,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        third = ThirdAction(
            icon = Icons.Default.Share,
            label = "Share",
            enabled = shown.isNotEmpty(),
            onClick = {
                val uri = ChartExport.exportChart(
                    context, shown, lineColor, textColor, gridColor, backgroundColor
                )
                SessionShare.share(
                    context = context,
                    subject = "${data.name} progress",
                    body = "${data.name} · ${rangeState.range.label.lowercase()}\nEstimated 1 Rep Max = ${formatE1rm(shown.last().e1rm)}",
                    photoUri = uri
                )
            }
        )
    ) { padding ->
        ExerciseStatsView(
            data = data,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
        )
    }
}
