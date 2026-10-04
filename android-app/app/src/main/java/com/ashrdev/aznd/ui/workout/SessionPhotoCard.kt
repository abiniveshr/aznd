package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.ashrdev.aznd.ui.common.PhotoViewerDialog
import com.ashrdev.aznd.ui.components.Card

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
