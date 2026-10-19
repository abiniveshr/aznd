package com.ashrdev.aznd.ui.workout

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.ThirdAction

@Composable
fun ExerciseProgressScreen(
    workoutId: Long,
    exerciseId: Long,
    viewModel: WorkoutViewModel,
    useTargets: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    // Opens Flex on the exercise preset (1RM, top set and chart of this exercise).
    onShare: () -> Unit
) {
    val data = rememberExerciseStats(viewModel, workoutId, exerciseId, useTargets)

    if (!data.loaded) return

    ScreenScaffold(
        title = data.name,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        third = ThirdAction(
            icon = Icons.Default.Share,
            label = "Share",
            enabled = data.points.isNotEmpty(),
            onClick = onShare
        )
    ) { padding ->
        ExerciseStatsView(
            data = data,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
        )
    }
}
