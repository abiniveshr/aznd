package com.ashrdev.aznd.ui.common

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.ashrdev.aznd.ui.workout.PhotoStore
import com.yalantis.ucrop.UCrop
import java.io.File

private const val TAG = "ImagePicker"

private fun Throwable?.detail(): String =
    if (this == null) "no exception attached" else "${javaClass.simpleName}: ${message ?: "(no message)"}"

class ImagePicker internal constructor(private val request: () -> Unit) {
    fun launch() = request()
}

// NOTE: UCrop needs androidx.exifinterface:exifinterface declared explicitly in build.gradle,
// otherwise cropping fails with NoClassDefFoundError (this was the original "photo vanishes" bug).
@Composable
fun rememberImagePicker(
    aspect: Pair<Float, Float>? = null,
    outputWidth: Int = 1600,
    outputHeight: Int = 1600,
    onImagePicked: (Uri) -> Unit
): ImagePicker {
    val context = LocalContext.current
    var showChooser by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDestFile by remember { mutableStateOf<File?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val destFile = pendingDestFile
        pendingDestFile = null
        when {
            result.resultCode == android.app.Activity.RESULT_OK &&
                destFile != null && destFile.exists() && destFile.length() > 0L -> {
                runCatching {
                    FileProvider.getUriForFile(context, PhotoStore.authority(context), destFile)
                }.onSuccess { finalUri ->
                    onImagePicked(finalUri)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to resolve cropped file to a content uri", e)
                    Toast.makeText(context, "Couldn't save the cropped photo — ${e.detail()}", Toast.LENGTH_LONG).show()
                }
            }
            result.resultCode == UCrop.RESULT_ERROR -> {
                val error = result.data?.let { UCrop.getError(it) }
                Log.e(TAG, "UCrop returned an error", error)
                Toast.makeText(context, "Crop failed — ${error.detail()}", Toast.LENGTH_LONG).show()
            }
            result.resultCode == android.app.Activity.RESULT_OK -> {
                Log.e(TAG, "UCrop reported success but output file is missing/empty: $destFile")
                Toast.makeText(
                    context,
                    "Couldn't save the cropped photo — output file missing or empty",
                    Toast.LENGTH_LONG
                ).show()
            }
            // RESULT_CANCELED: user backed out — nothing to do
        }
    }

    fun launchCrop(sourceUri: Uri) {
        val dir = File(context.filesDir, "picked_images").apply { mkdirs() }
        val destFile = File(dir, "crop_${System.currentTimeMillis()}.jpg")
        pendingDestFile = destFile
        val destUri = Uri.fromFile(destFile)
        var uCrop = UCrop.of(sourceUri, destUri)
            .withMaxResultSize(outputWidth, outputHeight)
        if (aspect != null) uCrop = uCrop.withAspectRatio(aspect.first, aspect.second)
        runCatching {
            cropLauncher.launch(uCrop.getIntent(context))
        }.onFailure { e ->
            Log.e(TAG, "Failed to launch crop activity", e)
            Toast.makeText(context, "Couldn't open the crop screen — ${e.detail()}", Toast.LENGTH_LONG).show()
        }
    }

    // Gallery/file-picker URIs aren't guaranteed readable once handed to a second
    // Activity (UCropActivity). Copy the bytes into our own storage first so UCrop
    // always reads a URI this app unambiguously owns.
    fun copyThenCrop(sourceUri: Uri) {
        runCatching {
            val dir = File(context.filesDir, "picked_images").apply { mkdirs() }
            val stagedFile = File(dir, "staged_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                stagedFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("openInputStream returned null for $sourceUri")
            stagedFile
        }.onSuccess { stagedFile ->
            launchCrop(Uri.fromFile(stagedFile))
        }.onFailure { e ->
            Log.e(TAG, "Failed to read picked image: $sourceUri", e)
            Toast.makeText(context, "Couldn't open that image — ${e.detail()}", Toast.LENGTH_LONG).show()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) launchCrop(uri)
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { copyThenCrop(it) } }

    if (showChooser) {
        AlertDialog(
            onDismissRequest = { showChooser = false },
            title = { Text("Add image") },
            text = { Text("Choose a source") },
            confirmButton = {
                TextButton(onClick = {
                    showChooser = false
                    val uri = PhotoStore.newPhotoUri(context)
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                }) { Text("Camera") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showChooser = false
                    fileLauncher.launch("image/*")
                }) { Text("Files") }
            }
        )
    }

    return remember { ImagePicker(request = { showChooser = true }) }
}
