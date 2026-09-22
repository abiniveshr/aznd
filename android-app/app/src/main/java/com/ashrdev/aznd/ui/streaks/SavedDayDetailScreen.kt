package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.workout.SessionPhotoCard
import com.ashrdev.aznd.ui.workout.SessionShare

private const val REMARK_LIMIT = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedDayDetailScreen(
    streakId: Long,
    date: String,
    viewModel: StreakViewModel,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var streakName by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var streakCount by remember { mutableStateOf(0) }
    var isExisting by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var showReplacePhoto by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val imagePicker = rememberImagePicker(
        onImagePicked = { uri -> photoUri = uri.toString() }
    )

    LaunchedEffect(streakId, date) {
        streakName = viewModel.getStreak(streakId)?.name ?: ""
        viewModel.getSavedDay(streakId, date)?.let { existing ->
            remark = existing.remark
            photoUri = existing.photoUri
            streakCount = existing.streakCountAtSave
            isExisting = true
        }
        loaded = true
    }

    if (!loaded) return

    if (showReplacePhoto) {
        AlertDialog(
            onDismissRequest = { showReplacePhoto = false },
            title = { Text("Replace photo?") },
            text = { Text("This day already has a photo. Choosing a new one will replace it.") },
            confirmButton = {
                TextButton(onClick = {
                    showReplacePhoto = false
                    imagePicker.launch()
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { showReplacePhoto = false }) { Text("Cancel") }
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete this saved day?") },
            text = { Text("The remark and photo for ${displayDate(date)} will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.deleteSavedDay(streakId, date)
                    onDone()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(displayDate(date)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    if (isExisting) {
                        IconButton(onClick = {
                            SessionShare.share(
                                context = context,
                                subject = "$streakName · ${displayDate(date)}",
                                body = "$streakName · Day $streakCount\n${displayDate(date)}\n\n$remark\n\nlogged with aznd",
                                photoUri = photoUri?.toUri()
                            )
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = {
                        viewModel.saveDay(streakId, date, remark, photoUri)
                        onDone()
                    }) {
                        Text("Save")
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
            if (isExisting) {
                item {
                    Text(
                        "Streak length: $streakCount day${if (streakCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = remark,
                    onValueChange = { if (it.length <= REMARK_LIMIT) remark = it },
                    label = { Text("Remark (${remark.length}/$REMARK_LIMIT)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            photoUri?.let { uri ->
                item { SessionPhotoCard(uri) }
            }
            item {
                OutlinedButton(
                    onClick = {
                        if (photoUri != null) showReplacePhoto = true
                        else imagePicker.launch()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (photoUri != null) "Replace photo" else "Add photo")
                }
            }
        }
    }
}