package com.ashrdev.aznd.ui.common

import android.content.Intent
import android.net.Uri
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

class ImagePicker internal constructor(private val request: () -> Unit) {
    fun launch() = request()
}

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

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { intent -> UCrop.getOutput(intent)?.let(onImagePicked) }
        }
    }

    fun launchCrop(sourceUri: Uri) {
        val dir = File(context.filesDir, "picked_images").apply { mkdirs() }
        val destFile = File(dir, "crop_${System.currentTimeMillis()}.jpg")
        val destUri = FileProvider.getUriForFile(context, PhotoStore.authority(context), destFile)
        var uCrop = UCrop.of(sourceUri, destUri)
            .withMaxResultSize(outputWidth, outputHeight)
        if (aspect != null) uCrop = uCrop.withAspectRatio(aspect.first, aspect.second)
        val intent = uCrop.getIntent(context)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        cropLauncher.launch(intent)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) launchCrop(uri)
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { launchCrop(it) } }

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