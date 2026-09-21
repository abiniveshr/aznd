package com.ashrdev.aznd.ui.streaks

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.streaks.StreakEntity
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakDetailScreen(
    streakId: Long,
    viewModel: StreakViewModel,
    onBack: () -> Unit,
    onSaveDay: (Long, String) -> Unit
) {
    val context = LocalContext.current
    var streak by remember { mutableStateOf<StreakEntity?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(streakId) {
        streak = viewModel.getStreak(streakId)
        loaded = true
    }

    if (!loaded) return
    val current = streak
    if (current == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val breaksList by viewModel.breaksForStreak(streakId).collectAsState(initial = emptyList())
    val breaks = breaksList.toSet()
    val today = todayKey()
    val currentCount = computeCurrentStreak(current.startDate, breaks, today)

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete streak?") },
            text = { Text("\"${current.name}\" and all its saved days will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.deleteStreak(streakId)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete streak", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "$currentCount day${if (currentCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(20.dp)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onSaveDay(streakId, today) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save today")
                }
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val picked = dateKey(year, month, dayOfMonth)
                                if (picked in breaks) viewModel.unmarkBreak(streakId, picked)
                                else viewModel.markBreak(streakId, picked)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Log a slip")
                }
            }
            StreakCalendar(
                startDate = current.startDate,
                breaks = breaks,
                onDayClick = { date -> onSaveDay(streakId, date) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}