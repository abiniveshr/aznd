package com.ashrdev.aznd.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.RecentWorkoutSession
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.navigation.Screen
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import com.ashrdev.aznd.ui.workout.formatTimestamp

@Composable
fun HistoryPage(
    workoutViewModel: WorkoutViewModel,
    onNavigate: (String) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    var recentSession by remember { mutableStateOf<RecentWorkoutSession?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        recentSession = workoutViewModel.getMostRecentSession()
        loaded = true
    }

    val workoutSubtitle = when {
        !loaded -> ""
        recentSession == null -> "No sessions logged yet."
        else -> "${recentSession!!.workoutName} · ${formatTimestamp(recentSession!!.startedAt)}"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "workout-history") {
            HistoryEntryCard(
                icon = Icons.Default.FitnessCenter,
                title = "Workout History",
                subtitle = workoutSubtitle,
                onClick = { onNavigate(Screen.History.route) }
            )
        }
        item(key = "saved-streaks") {
            HistoryEntryCard(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "Saved Streaks",
                subtitle = "Photos and remarks from your streak days.",
                onClick = { onNavigate(Screen.SavedDays.route) }
            )
        }
    }
}

@Composable
private fun HistoryEntryCard(
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
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