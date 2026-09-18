package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.ashrdev.aznd.data.workout.ActiveSetEntity
import com.ashrdev.aznd.data.workout.SetMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineRunnerScreen(
    viewModel: WorkoutViewModel,
    onBack: () -> Unit,
    onFinished: (Long) -> Unit
) {
    val context = LocalContext.current
    val active by viewModel.activeSession.collectAsState()
    var pendingPhotoUri by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) viewModel.attachPhoto(pendingPhotoUri)
        pendingPhotoUri = null
    }

    val session = active
    LaunchedEffect(session) {
        if (session == null) onBack()
    }
    if (session == null) return

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session.session.routineName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val uri = PhotoStore.newPhotoUri(context)
                        pendingPhotoUri = uri.toString()
                        cameraLauncher.launch(uri)
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
            )
        }
    ) { innerPadding ->
        val grouped = session.sets
            .sortedWith(compareBy({ it.exerciseIndex }, { it.setIndex }))
            .groupBy { it.exerciseIndex }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
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
                    item {
                        ActiveSetRow(
                            set = set,
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
    onChange: (ActiveSetEntity) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Set ${set.setIndex + 1}", style = MaterialTheme.typography.labelLarge)
            if (set.mode == SetMode.TIME) {
                TimeInput(
                    stateKey = set.id,
                    totalSeconds = set.valueText.toIntOrNull() ?: 0,
                    onTotalChange = { onChange(set.copy(valueText = if (it == 0) "" else it.toString())) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = set.weightText,
                    onValueChange = { onChange(set.copy(weightText = it)) },
                    label = { Text("kg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = set.valueText,
                        onValueChange = { input ->
                            onChange(set.copy(valueText = input.filter { it.isDigit() }))
                        },
                        label = { Text("Reps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = set.weightText,
                        onValueChange = { onChange(set.copy(weightText = it)) },
                        label = { Text("kg") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
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
        FullPhotoDialog(uriString = uriString, onDismiss = { showFull = false })
    }
}

@Composable
private fun FullPhotoDialog(uriString: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(uriString) {
        PhotoStore.loadBitmap(context, uriString.toUri(), maxDimension = 2000)
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Session photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
        }
    }
}