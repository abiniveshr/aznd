package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.data.workout.WorkoutSettings
import com.ashrdev.aznd.data.workout.WorkoutSettingsStore

/**
 * Settings section for workout logging preferences (RPE tracking, target reps).
 */
@Composable
fun WorkoutSettingsSection(store: WorkoutSettingsStore, modifier: Modifier = Modifier) {
    val settings by store.settings.collectAsState()
    WorkoutSettingsSwitches(
        settings = settings,
        onTrackRpe = store::setTrackRpe,
        onTargetReps = store::setTargetReps,
        modifier = modifier
    )
}

@Composable
fun WorkoutSettingsSwitches(
    settings: WorkoutSettings,
    onTrackRpe: (Boolean) -> Unit,
    onTargetReps: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Workout", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        SwitchCard(
            title = "Track RPE",
            subtitle = "Log effort (RPE 5-10) and use it in 1RM estimates. Off hides it; saved values are kept.",
            checked = settings.trackRpe,
            onChange = onTrackRpe
        )
        SwitchCard(
            title = "Target reps",
            subtitle = "Set rep targets per set and get weight suggestions. Off hides them; saved targets are kept.",
            checked = settings.targetReps,
            onChange = onTargetReps
        )
    }
}

@Composable
private fun SwitchCard(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
