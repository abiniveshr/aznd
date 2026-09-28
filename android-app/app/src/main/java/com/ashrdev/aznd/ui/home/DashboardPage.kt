package com.ashrdev.aznd.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.navigation.Screen
import com.ashrdev.aznd.ui.streaks.StreakWithCount
import com.ashrdev.aznd.ui.workout.TopExerciseStat
import com.ashrdev.aznd.ui.workout.formatE1rm
import com.ashrdev.aznd.ui.workout.formatTopSet

@Composable
fun DashboardPage(
    runningStreaks: List<StreakWithCount>,
    topExercises: List<TopExerciseStat>,
    topExercisesLoaded: Boolean,
    onNavigate: (String) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Streaks", style = MaterialTheme.typography.titleLarge)
                if (runningStreaks.isEmpty()) {
                    Text(
                        "No streaks running right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { onNavigate(Screen.StreakEditor.route) }) {
                        Text("Add streak")
                    }
                }
            }
        }
        if (runningStreaks.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(runningStreaks, key = { it.streak.id }) { item ->
                        StreakSummaryCard(
                            item = item,
                            onClick = { onNavigate(Screen.StreakDetail.createRoute(item.streak.id)) }
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Top Lifts",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (topExercisesLoaded && topExercises.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "No sets logged yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { onNavigate(Screen.WorkoutLog.route) }) {
                        Text("Go to workouts")
                    }
                }
            }
        }
        items(topExercises, key = { it.exerciseId }) { stat ->
            TopExerciseCard(
                stat = stat,
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = {
                    onNavigate(Screen.ExerciseView.createRoute(stat.routineId, stat.exerciseId))
                }
            )
        }
    }
}

@Composable
private fun StreakSummaryCard(
    item: StreakWithCount,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                item.streak.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${item.currentStreak}",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                if (item.currentStreak == 1) "day" else "days",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TopExerciseCard(
    stat: TopExerciseStat,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stat.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatTopSet(stat.bestPoint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                formatE1rm(stat.bestPoint.e1rm),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}