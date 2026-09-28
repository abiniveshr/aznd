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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.navigation.Screen
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.workout.WorkoutViewModel

@Composable
fun HistoryPage(
    workoutViewModel: WorkoutViewModel,
    streakViewModel: StreakViewModel,
    onNavigate: (String) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val workoutHistory by workoutViewModel.workoutHistorySummaries.collectAsState()
    val streaksWithDays by streakViewModel.streaksWithSavedDays.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "workouts-header") { SectionHeader("Workouts") }
        if (workoutHistory.isEmpty()) {
            item(key = "workouts-empty") { EmptyNote("No sessions logged yet.") }
        } else {
            items(workoutHistory, key = { "workout-${it.routineId}" }) { entry ->
                HistoryEntryCard(
                    icon = Icons.Default.FitnessCenter,
                    title = entry.routineName,
                    subtitle = "Session history",
                    onClick = { onNavigate(Screen.RoutineHistory.createRoute(entry.routineId)) }
                )
            }
        }

        item(key = "streaks-header") {
            SectionHeader("Streaks", modifier = Modifier.padding(top = 12.dp))
        }
        if (streaksWithDays.isEmpty()) {
            item(key = "streaks-empty") { EmptyNote("No saved days yet.") }
        } else {
            items(streaksWithDays, key = { "streak-${it.id}" }) { streak ->
                HistoryEntryCard(
                    icon = Icons.Default.LocalFireDepartment,
                    title = streak.name,
                    subtitle = "Saved days",
                    onClick = { onNavigate(Screen.SavedDaysForStreak.createRoute(streak.id)) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
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
            .padding(horizontal = 16.dp)
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