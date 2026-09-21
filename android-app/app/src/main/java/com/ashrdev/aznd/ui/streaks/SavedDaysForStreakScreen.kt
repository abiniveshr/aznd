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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedDaysForStreakScreen(
    streakId: Long,
    viewModel: StreakViewModel,
    onOpenDay: (Long, String) -> Unit,
    onBack: () -> Unit
) {
    var streakName by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }

    LaunchedEffect(streakId) {
        val s = viewModel.getStreak(streakId)
        streakName = s?.name ?: ""
        startDate = s?.startDate ?: todayKey()
    }

    val savedDays by viewModel.savedDaysForStreak(streakId).collectAsState(initial = emptyList())
    val breaksList by viewModel.breaksForStreak(streakId).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(streakName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (savedDays.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
            ) {
                Text("No saved days for this streak yet.", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(savedDays, key = { it.date }) { day ->
                val countAtDay = computeCurrentStreak(startDate, breaksList.toSet(), day.date)
                SavedDayCard(
                    day = day,
                    streakCount = countAtDay,
                    onClick = { onOpenDay(streakId, day.date) }
                )
            }
        }
    }
}

@Composable
private fun SavedDayCard(
    day: StreakSavedDayEntity,
    streakCount: Int,
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(displayDate(day.date), style = MaterialTheme.typography.titleMedium)
                Text(
                    "$streakCount day${if (streakCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (day.photoUri != null) {
                Icon(Icons.Default.Photo, contentDescription = "Has photo")
            }
        }
    }
}