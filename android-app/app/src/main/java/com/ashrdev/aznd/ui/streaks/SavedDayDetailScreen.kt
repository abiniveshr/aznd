package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.ui.workout.SessionPhotoCard
import com.ashrdev.aznd.ui.workout.SessionShare

private const val REMARK_LIMIT = 50

// Back in the bottom bar cancels. Save, Share and Delete stay in the top bar.
@Composable
fun SavedDayDetailScreen(
    streakId: Long,
    date: String,
    viewModel: StreakViewModel,
    onDone: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var streakName by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var streakCount by remember { mutableStateOf(0) }
    var isExisting by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
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

    ScreenScaffold(
        title = displayDate(date),
        onBack = onDone,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        topBarActions = {
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
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            TextButton(onClick = {
                viewModel.saveDay(streakId, date, remark, photoUri)
                onDone()
            }) {
                Text("Save")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding()
            ),
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
            if (photoUri == null) {
                item {
                    OutlinedButton(
                        onClick = { imagePicker.launch() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add photo")
                    }
                }
            }
        }
    }
}
