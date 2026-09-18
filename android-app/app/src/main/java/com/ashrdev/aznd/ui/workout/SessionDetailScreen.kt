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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.ashrdev.aznd.data.workout.LoggedSetEntity
import com.ashrdev.aznd.data.workout.SessionEntity
import com.ashrdev.aznd.data.workout.SessionWithSets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    viewModel: WorkoutViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var data by remember { mutableStateOf<SessionWithSets?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(sessionId) {
        data = viewModel.getSession(sessionId)
        loaded = true
    }

    if (!loaded) return
    val item = data
    if (item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val grouped = item.sets.sortedBy { it.orderIndex }.groupBy { it.exerciseName }
    val elapsed = ((item.session.finishedAt - item.session.startedAt) / 1000L).toInt()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.session.routineName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        SessionShare.share(
                            context = context,
                            subject = "${item.session.routineName} · ${formatTimestamp(item.session.startedAt)}",
                            body = formatSessionText(item.session, item.sets),
                            photoUri = item.session.photoUri?.toUri()
                        )
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share session")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        formatTimestamp(item.session.startedAt),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Finished ${formatClock(item.session.finishedAt)} · ${formatDuration(elapsed)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            item.session.photoUri?.let { uri ->
                item { SessionPhotoCard(uri) }
            }
            grouped.forEach { (exerciseName, sets) ->
                item {
                    ExerciseGroupCard(
                        session = item.session,
                        exerciseName = exerciseName,
                        sets = sets,
                        expanded = exerciseName in expanded,
                        onToggle = {
                            expanded = if (exerciseName in expanded) expanded - exerciseName
                            else expanded + exerciseName
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseGroupCard(
    session: SessionEntity,
    exerciseName: String,
    sets: List<LoggedSetEntity>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(exerciseName, style = MaterialTheme.typography.titleMedium)
                    Text("${sets.size} sets", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        SessionShare.share(
                            context = context,
                            subject = "$exerciseName · ${formatTimestamp(session.startedAt)}",
                            body = formatExerciseShare(session, exerciseName, sets)
                        )
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share exercise")
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            if (expanded) {
                sets.forEachIndexed { index, set ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Set ${index + 1}: ${formatSet(set)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = {
                            SessionShare.share(
                                context = context,
                                subject = "$exerciseName set ${index + 1}",
                                body = formatSingleSetShare(session, exerciseName, index, set)
                            )
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share set")
                        }
                    }
                }
            } else {
                Text(
                    sets.joinToString("   ") { formatSet(it) },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}