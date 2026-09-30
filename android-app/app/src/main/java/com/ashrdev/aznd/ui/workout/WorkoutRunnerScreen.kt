package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.ashrdev.aznd.data.workout.ActiveSetEntity
import com.ashrdev.aznd.data.workout.LoggedSetEntity
import com.ashrdev.aznd.data.workout.SetMode
import com.ashrdev.aznd.ui.common.PhotoViewerDialog
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedTextField

@Composable
fun WorkoutRunnerScreen(
    viewModel: WorkoutViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onFinished: (Long) -> Unit
) {
    val active by viewModel.activeSession.collectAsState()
    var showDiscard by remember { mutableStateOf(false) }
    var showReplacePhoto by remember { mutableStateOf(false) }
    var previousByExercise by remember { mutableStateOf<Map<String, List<LoggedSetEntity>>>(emptyMap()) }

    val imagePicker = rememberImagePicker(
        onImagePicked = { uri -> viewModel.attachPhoto(uri.toString()) }
    )

    val session = active
    LaunchedEffect(session) {
        if (session == null) onBack()
    }
    if (session == null) return

    LaunchedEffect(session.session.WorkoutId) {
        previousByExercise = viewModel.getPreviousSession(session.session.WorkoutId)
            ?.sets
            ?.sortedBy { it.orderIndex }
            ?.groupBy { it.exerciseName }
            ?: emptyMap()
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard session?") },
            text = { Text("Everything logged in this session will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    viewModel.discardSession()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text("Keep") }
            }
        )
    }

    if (showReplacePhoto) {
        AlertDialog(
            onDismissRequest = { showReplacePhoto = false },
            title = { Text("Replace photo?") },
            text = { Text("This session already has a photo. Choosing a new one will replace it.") },
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

    ScreenScaffold(
        title = session.session.WorkoutName,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        topBarActions = {
            IconButton(onClick = {
                if (session.session.photoUri != null) showReplacePhoto = true
                else imagePicker.launch()
            }) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "Add photo")
            }
            IconButton(onClick = { showDiscard = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Discard session")
            }
            TextButton(onClick = { viewModel.finishSession(onFinished) }) {
                Text("Finish")
            }
        }
    ) { padding ->
        val grouped = session.sets
            .sortedWith(compareBy({ it.exerciseIndex }, { it.setIndex }))
            .groupBy { it.exerciseIndex }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Started ${formatTimestamp(session.session.startedAt)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            session.session.photoUri?.let { uri ->
                item { SessionPhotoCard(uri) }
            }
            grouped.forEach { (_, sets) ->
                item {
                    Text(sets.first().exerciseName, style = MaterialTheme.typography.titleMedium)
                }
                sets.forEach { set ->
                    item(key = set.id) {
                        ActiveSetRow(
                            set = set,
                            previousSet = previousByExercise[set.exerciseName]?.getOrNull(set.setIndex),
                            onChange = { viewModel.updateActiveSet(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveSetRow(
    set: ActiveSetEntity,
    previousSet: LoggedSetEntity?,
    onChange: (ActiveSetEntity) -> Unit
) {
    var valueText by remember(set.id) { mutableStateOf(set.valueText) }
    var weightText by remember(set.id) { mutableStateOf(set.weightText) }
    var rpeText by remember(set.id) { mutableStateOf(set.rpeText) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Set ${set.setIndex + 1}", style = MaterialTheme.typography.labelLarge)
            if (previousSet != null) {
                Text(
                    "Previous: ${formatSet(previousSet)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (set.mode == SetMode.TIME) {
                TimeInput(
                    stateKey = set.id,
                    totalSeconds = valueText.toIntOrNull() ?: 0,
                    onTotalChange = {
                        valueText = if (it == 0) "" else it.toString()
                        onChange(set.copy(valueText = valueText, weightText = weightText, rpeText = rpeText))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { input ->
                        valueText = input.filter { it.isDigit() }
                        onChange(set.copy(valueText = valueText, weightText = weightText, rpeText = rpeText))
                    },
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { input ->
                        weightText = input
                        onChange(set.copy(valueText = valueText, weightText = weightText, rpeText = rpeText))
                    },
                    label = { Text("kg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = rpeText,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1) {
                            rpeText = filtered
                            onChange(set.copy(valueText = valueText, weightText = weightText, rpeText = rpeText))
                        }
                    },
                    label = { Text("RPE") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focus ->
                            if (!focus.isFocused) {
                                val clamped = rpeText.toDoubleOrNull()?.coerceIn(0.0, 10.0)
                                if (clamped != null) {
                                    val text = if (clamped % 1.0 == 0.0) clamped.toInt().toString()
                                    else clamped.toString()
                                    if (text != rpeText) {
                                        rpeText = text
                                        onChange(set.copy(valueText = valueText, weightText = weightText, rpeText = rpeText))
                                    }
                                }
                            }
                        }
                )
            }
        }
    }
}

@Composable
fun SessionPhotoCard(uriString: String, maxHeight: Int = 240) {
    val context = LocalContext.current
    val bitmap = remember(uriString) {
        PhotoStore.loadBitmap(context, uriString.toUri())
    }
    var showFull by remember { mutableStateOf(false) }
    if (bitmap == null) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showFull = true }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Session photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight.dp)
        )
    }

    if (showFull) {
        PhotoViewerDialog(uriString = uriString, onDismiss = { showFull = false })
    }
}
