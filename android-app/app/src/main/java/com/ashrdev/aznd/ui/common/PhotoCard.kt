package com.ashrdev.aznd.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Card

@Composable
fun PhotoCard(uriString: String, maxHeight: Int = 240) {
    val load = rememberPhotoLoad(uriString, maxDimension = 1200)
    var showFull by remember { mutableStateOf(false) }
    val bitmap = load.bitmap
    if (bitmap == null) {
        // Keep the room the photo will take so the page doesn't jump when it arrives.
        if (load.loading) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.fillMaxWidth().height(maxHeight.dp))
            }
        }
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showFull = true }
    ) {
        Image(
            bitmap = bitmap,
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
