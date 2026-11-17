package com.ashrdev.aznd.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.data.workout.WorkoutSettingsStore
import com.ashrdev.aznd.ui.workout.WorkoutSettingsSection
import com.ashrdev.aznd.ui.theme.Space

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenPerformance: () -> Unit,
    workoutSettings: WorkoutSettingsStore
) {
    ScreenScaffold(
        title = "Settings",
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        settingsEnabled = false
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = Space.lg, end = Space.lg, top = Space.lg, bottom = padding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            SettingsRow(
                icon = Icons.Default.Palette,
                title = "Appearance",
                subtitle = "Colours, corners, outlines, theme files",
                onClick = onOpenAppearance
            )
            SettingsRow(
                icon = Icons.Default.Dashboard,
                title = "Dashboard",
                subtitle = "Widgets, order, sizes and what each one shows",
                onClick = onOpenDashboard
            )
            SettingsRow(
                icon = Icons.Default.Speed,
                title = "Performance",
                subtitle = "Theme patterns and body model",
                onClick = onOpenPerformance
            )
            WorkoutSettingsSection(workoutSettings)
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(Space.lg))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
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
