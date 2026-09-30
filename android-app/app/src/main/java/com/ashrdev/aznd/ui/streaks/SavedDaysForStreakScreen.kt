package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.streaks.StreakSavedDayEntity
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.ThirdAction
import com.ashrdev.aznd.ui.components.Card

@Composable
fun SavedDaysForStreakScreen(
    streakId: Long,
    viewModel: StreakViewModel,
    onOpenDay: (Long, String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var streakName by remember { mutableStateOf("") }

    LaunchedEffect(streakId) {
        streakName = viewModel.getStreak(streakId)?.name ?: ""
    }

    val savedDays by viewModel.savedDaysForStreak(streakId).collectAsState(initial = emptyList())

    ScreenScaffold(
        title = streakName,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        third = ThirdAction(icon = Icons.Default.Share, label = "Share", enabled = false, onClick = {})
    ) { padding ->
        if (savedDays.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text("No saved days for this streak yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(savedDays, key = { it.date }) { day ->
                    SavedDayCard(day = day, onClick = { onOpenDay(streakId, day.date) })
                }
            }
        }
    }
}

@Composable
private fun SavedDayCard(day: StreakSavedDayEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
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
            Column {
                Text(displayDate(day.date), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${day.streakCountAtSave} day${if (day.streakCountAtSave == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (day.photoUri != null) {
                Icon(Icons.Default.Photo, contentDescription = "Has photo")
            }
        }
    }
}
