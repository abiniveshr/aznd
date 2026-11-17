package com.ashrdev.aznd.ui.common.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ashrdev.aznd.ui.common.saveImageToDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.ashrdev.aznd.ui.theme.Space

/**
 * The Saved page: every card that was shared or saved from Flex, newest first, as a grid of small pictures.
 * Tap one to see it large (share, save to the gallery, delete). [refreshKey] changes when a card was added.
 */
@Composable
internal fun FlexSavedPage(bottom: Dp, refreshKey: Int) {
    val context = LocalContext.current
    var localKey by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<File?>(null) }
    val files by produceState<List<File>?>(null, refreshKey, localKey) {
        value = withContext(Dispatchers.IO) { FlexSavedImages.list(context) }
    }
    val list = files

    if (list != null && list.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = Space.xxl, end = Space.xxl, bottom = bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Space.md))
            Text(
                "Cards you share or save show up here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else if (list != null) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.md, end = Space.md, top = Space.sm, bottom = bottom + Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            items(list, key = { it.absolutePath }) { file ->
                SavedThumb(file, modifier = Modifier.clickable { open = file })
            }
        }
    }

    open?.let { file ->
        SavedViewer(
            file = file,
            onDismiss = { open = null },
            onDeleted = {
                open = null
                localKey++
            }
        )
    }
}

@Composable
private fun SavedThumb(file: File, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { decodeScaled(file, 320) }
    }
    Box(
        modifier = modifier
            .aspectRatio(0.8f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** One saved card, large, with Share, Save to gallery and Delete. */
@Composable
private fun SavedViewer(file: File, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitmap by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { decodeScaled(file, 1600) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss)
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Saved card",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = Space.md, end = Space.md, top = Space.xl, bottom = 96.dp)
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(Space.lg),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { FlexExport.share(context, FlexSavedImages.uriOf(context, file)) }) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }
                IconButton(onClick = {
                    scope.launch { saveImageToDevice(context, FlexSavedImages.uriOf(context, file)) }
                }) {
                    Icon(Icons.Default.Download, contentDescription = "Save to gallery", tint = Color.White)
                }
                IconButton(onClick = {
                    FlexSavedImages.delete(file)
                    onDeleted()
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                }
            }
        }
    }
}

/** Decodes [file] with its longer side at most about [maxSide] pixels (power-of-two subsampling, cheap on memory). */
private fun decodeScaled(file: File, maxSide: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    while (longest / (sample * 2) >= maxSide) sample *= 2
    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()
