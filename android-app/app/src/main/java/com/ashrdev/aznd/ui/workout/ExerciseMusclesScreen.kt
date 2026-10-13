package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.domain.ExerciseType
import com.ashrdev.aznd.domain.Muscle
import com.ashrdev.aznd.domain.displayName
import com.ashrdev.aznd.ui.common.BodyModelPlaceholder
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.components.Card

/**
 * Exercise viewer: which muscles an exercise trains. The 3D model (placeholder for now) will
 * light up the active muscles; until then the primary and secondary muscles are listed.
 */
@Composable
fun ExerciseMusclesScreen(
    exerciseId: Long,
    viewModel: WorkoutViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var exercise by remember { mutableStateOf<Exercise?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(exerciseId) {
        exercise = viewModel.exercise(exerciseId)
        loaded = true
    }

    if (!loaded) return
    val item = exercise
    if (item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    ScreenScaffold(
        title = item.name,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "model") {
                BodyModelPlaceholder(
                    title = "Active muscles",
                    detail = "${item.primaryMuscle.displayName()} highlighted as primary"
                )
            }
            item(key = "type") {
                Text(
                    "Type: ${item.type.label()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item(key = "primary-title") { Text("Primary muscle", style = MaterialTheme.typography.titleMedium) }
            item(key = "primary") { MuscleRow(item.primaryMuscle, primary = true) }
            item(key = "secondary-title") { Text("Secondary muscles", style = MaterialTheme.typography.titleMedium) }
            if (item.secondaryMuscles.isEmpty()) {
                item(key = "secondary-none") {
                    Text(
                        "None listed for this exercise.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(item.secondaryMuscles, key = { it.name }) { muscle -> MuscleRow(muscle, primary = false) }
            }
        }
    }
}

@Composable
private fun MuscleRow(muscle: Muscle, primary: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(muscle.displayName(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                if (primary) "Primary" else "Secondary",
                style = MaterialTheme.typography.labelMedium,
                color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun ExerciseType.label(): String = when (this) {
    ExerciseType.WEIGHTED -> "Weighted"
    ExerciseType.BODYWEIGHT_REPS -> "Bodyweight"
    ExerciseType.ASSISTED_BODYWEIGHT -> "Assisted bodyweight"
    ExerciseType.TIME_HELD -> "Timed hold"
}
