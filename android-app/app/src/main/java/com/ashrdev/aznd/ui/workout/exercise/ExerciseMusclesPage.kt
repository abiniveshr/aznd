package com.ashrdev.aznd.ui.workout.exercise

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.domain.workout.Muscle
import com.ashrdev.aznd.domain.workout.displayName
import com.ashrdev.aznd.ui.common.BodyModelPlaceholder
import com.ashrdev.aznd.ui.components.Card

/**
 * The viewer's Exercise page: which muscles an exercise trains. The 3D model (placeholder for
 * now) will light up the active muscles; until then the primary and secondary muscles are listed.
 */
@Composable
fun ExerciseMusclesPage(item: Exercise, bottomPadding: Dp) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding + 16.dp
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
                "Type: ${item.type.formLabel()}",
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
