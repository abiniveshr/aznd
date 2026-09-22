package com.ashrdev.aznd.ui.streaks

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.streaks.StreakEntity
import com.ashrdev.aznd.ui.common.rememberImagePicker
import java.util.Calendar

private val slipPhrases = listOf(
    "Do not worry, we start again",
    "Get back up",
    "On your feet soldier"
)

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
    var menuExpanded by remember { mutableStateOf(false) }
    var tappedDate by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(streakId) {
        streak = viewModel.getStreak(streakId)
        loaded = true
    }

    val imagePicker = rememberImagePicker(
        aspect = 16f to 9f,
        outputWidth = 1200,
        outputHeight = 675,
        onImagePicked = { uri -> viewModel.updateStreakPhoto(streakId, uri.toString()) }
    )

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

    fun markSlip(date: String) {
        Toast.makeText(context, slipPhrases[breaksList.size % slipPhrases.size], Toast.LENGTH_SHORT).show()
        viewModel.markBreak(streakId, date)
    }

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

    tappedDate?.let { date ->
        val lengthOnDate = computeCurrentStreak(current.startDate, breaks, date)
        val isBroken = date in breaks
        AlertDialog(
            onDismissRequest = { tappedDate = null },
            title = { Text(displayDate(date)) },
            text = { Text("Streak length on this day: $lengthOnDate day${if (lengthOnDate == 1) "" else "s"}") },
            confirmButton = {
                TextButton(onClick = {
                    if (isBroken) viewModel.unmarkBreak(streakId, date)
                    else markSlip(date)
                    tappedDate = null
                }) {
                    Text(if (isBroken) "Undo slip" else "Mark as slip")
                }
            },
            dismissButton = {
                TextButton(onClick = { tappedDate = null }) { Text("Close") }
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
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Streak options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (current.photoUri != null) "Replace photo" else "Add photo") },
                                onClick = {
                                    menuExpanded = false
                                    imagePicker.launch()
                                }
                            )
                            if (current.photoUri != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove photo") },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.updateStreakPhoto(streakId, null)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete streak") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    showDelete = true
                                }
                            )
                        }
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
            current.photoUri?.let { uri -> StreakBanner(uri) }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = { onSaveDay(streakId, today) }) {
                    Icon(Icons.Default.Favorite, contentDescription = "Save today")
                }
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val picked = dateKey(year, month, dayOfMonth)
                                if (picked in breaks) viewModel.unmarkBreak(streakId, picked)
                                else markSlip(picked)
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
                onDayClick = { date -> tappedDate = date },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}