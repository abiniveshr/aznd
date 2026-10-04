package com.ashrdev.aznd.ui.workout

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.data.workout.CustomExerciseForm
import com.ashrdev.aznd.data.workout.FormError
import com.ashrdev.aznd.data.workout.formLabel
import com.ashrdev.aznd.data.workout.message
import com.ashrdev.aznd.domain.ExerciseType
import com.ashrdev.aznd.domain.Muscle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "New exercise" / "Edit exercise" form, reached from the Custom exercises list (three-dot menu on the
 * workouts screen). In edit mode the existing photo file is only deleted after a successful Save.
 * Name, type, muscles (chips, max 4: the first tapped is primary, the star on another chip makes
 * it primary), bodyweight share % (bodyweight types only, default 100) and an optional photo
 * through the existing camera/file + crop flow (copied into app-private storage). Save is in the
 * top bar so it stays reachable with the keyboard open. [onSaved] should just go back.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomExerciseScreen(
    viewModel: CustomExerciseViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val form by viewModel.form.collectAsState()
    val errors by viewModel.errors.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val typeLocked by viewModel.typeLocked.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(saved) {
        if (saved) {
            val original = viewModel.originalPhoto
            if (original != null && original != form.photoPath) {
                withContext(Dispatchers.IO) { deletePrivatePhoto(context, original) }
            }
            onSaved()
        }
    }

    val picker = rememberImagePicker(aspect = 1f to 1f, outputWidth = 512, outputHeight = 512) { uri ->
        scope.launch {
            val path = withContext(Dispatchers.IO) { copyToPrivateStorage(context, uri) }
            if (path != null) {
                val old = form.photoPath
                viewModel.update { it.withPhoto(path) }
                if (old != null && old != viewModel.originalPhoto) {
                    withContext(Dispatchers.IO) { deletePrivatePhoto(context, old) }
                }
            }
        }
    }

    ScreenScaffold(
        title = if (viewModel.isEdit) "Edit exercise" else "New exercise",
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        topBarActions = { TextButton(onClick = viewModel::save) { Text("Save") } }
    ) { padding ->
        if (!loaded) return@ScreenScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.update { it.withName(v) } },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ErrorText(errors, FormError.NAME_BLANK, FormError.NAME_TAKEN)

            Section("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseType.values().forEach { t ->
                    FilterChip(
                        selected = form.type == t,
                        onClick = { viewModel.update { it.withType(t) } },
                        enabled = !typeLocked,
                        label = { Text(t.formLabel()) }
                    )
                }
            }
            ErrorText(errors, FormError.TYPE_MISSING)
            if (typeLocked) {
                Text(
                    "Type can't be changed because this exercise is already used in a workout or in your history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Section("Muscles (up to 4, first = primary)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Muscle.values().forEach { m ->
                    FilterChip(
                        selected = m in form.muscles,
                        onClick = { viewModel.update { it.toggleMuscle(m) } },
                        label = { Text(m.name.lowercase().replace('_', ' ')) }
                    )
                }
            }
            form.muscles.forEachIndexed { index, m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.update { it.makePrimary(m) } }) {
                        Icon(
                            if (index == 0) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (index == 0) "Primary muscle" else "Make primary",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = m.name.lowercase().replace('_', ' ') + if (index == 0) "  (primary)" else "",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            ErrorText(errors, FormError.NO_PRIMARY)

            if (form.showShare) {
                OutlinedTextField(
                    value = form.sharePercentText,
                    onValueChange = { v -> viewModel.update { it.withShareText(v) } },
                    label = { Text("Share of bodyweight moved (%)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                ErrorText(errors, FormError.SHARE_INVALID)
            }

            Section("Photo (optional)")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MuscleBadge(form.primary ?: Muscle.CHEST, form.photoPath, size = 72.dp)
                OutlinedButton(onClick = { picker.launch() }) {
                    Text(if (form.photoPath == null) "Add photo" else "Change photo")
                }
                if (form.photoPath != null) {
                    TextButton(onClick = {
                        val old = form.photoPath
                        viewModel.update { it.withPhoto(null) }
                        if (old != null && old != viewModel.originalPhoto) {
                            scope.launch { withContext(Dispatchers.IO) { deletePrivatePhoto(context, old) } }
                        }
                    }) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) =
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

@Composable
private fun ErrorText(errors: Set<FormError>, vararg which: FormError) {
    which.filter { it in errors }.forEach {
        Text(it.message(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

private const val PHOTO_DIR = "exercise_photos"

/** Copies the cropped image into filesDir/exercise_photos and returns the absolute path (null on failure). */
private fun copyToPrivateStorage(context: Context, uri: Uri): String? = runCatching {
    val dir = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }
    val out = File(dir, "ex_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        out.outputStream().use { input.copyTo(it) }
    } ?: error("openInputStream returned null")
    out.absolutePath
}.getOrNull()

/** Only deletes files this form created. */
internal fun deletePrivatePhoto(context: Context, path: String) {
    val dir = File(context.filesDir, PHOTO_DIR)
    val f = File(path)
    if (f.parentFile?.absolutePath == dir.absolutePath) f.delete()
}
