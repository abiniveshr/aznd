package com.ashrdev.aznd.ui.workout

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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.SessionWithSets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistoryScreen(
    routineId: Long,
    viewModel: WorkoutViewModel,
    onOpenSession: (Long) -> Unit,
    onOpenStats: (Long) -> Unit,
    onBack: () -> Unit
) {
    val sessions by viewModel.sessionsForRoutine(routineId).collectAsState(initial = emptyList())
    var sessionToDelete by remember { mutableStateOf<SessionWithSets?>(null) }

    sessionToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete session?") },
            text = { Text("This session log${if (target.session.photoUri != null) " and its photo" else ""} will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(target.session.id)
                    sessionToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sessions.firstOrNull()?.session?.routineName ?: "History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenStats(routineId) }) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Detailed statistics")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (sessions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
            ) {
                Text("No sessions logged yet.", style = MaterialTheme.typography.bodyMedium)
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
            items(sessions, key = { it.session.id }) { item ->
                HistoryCard(
                    item = item,
                    onClick = { onOpenSession(item.session.id) },
                    onDelete = { sessionToDelete = item }
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(
    item: SessionWithSets,
    onClick: () -> Unit,
    onDelete: () -> Unit
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(formatTimestamp(item.session.startedAt), style = MaterialTheme.typography.titleMedium)
                val elapsed = ((item.session.finishedAt - item.session.startedAt) / 1000L).toInt()
                Text(
                    "${item.sets.size} sets · ${formatDuration(elapsed)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.session.photoUri != null) {
                    Icon(Icons.Default.Photo, contentDescription = "Has photo")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete session", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}