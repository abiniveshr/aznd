package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.ashrdev.aznd.ui.common.PhotoViewerDialog
import com.ashrdev.aznd.ui.workout.PhotoStore

const val STREAK_IMAGE_ASPECT = 16f / 9f

@Composable
fun StreakBanner(photoUri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(photoUri) { PhotoStore.loadBitmap(context, photoUri.toUri()) }
    var showFull by remember { mutableStateOf(false) }
    if (bitmap == null) return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Streak banner",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(STREAK_IMAGE_ASPECT)
            .clickable { showFull = true }
    )
    if (showFull) {
        PhotoViewerDialog(uriString = photoUri, onDismiss = { showFull = false })
    }
}