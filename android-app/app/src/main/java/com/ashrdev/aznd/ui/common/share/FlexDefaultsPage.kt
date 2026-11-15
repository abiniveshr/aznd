package com.ashrdev.aznd.ui.common.share

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.components.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Defaults page: one row for each screen that has a Share button ([FLEX_DEFAULT_SOURCES]; Home is not one, its Share
 * opens the Presets list). A row shows the screen's blueprint (the layout a share from that screen starts from) drawn
 * with sample content; tapping it opens the editor on that blueprint ([onEdit]); its menu has "Reset to original".
 * [refreshKey] changes when a blueprint was saved, so the pictures are drawn again.
 */
@Composable
internal fun FlexDefaultsPage(
    bottom: Dp,
    refreshKey: Int,
    onEdit: (FlexPreset) -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    var resetting by remember { mutableStateOf<FlexPreset?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(FLEX_DEFAULT_SOURCES, key = { it.name }) { source ->
            DefaultRow(
                source = source,
                refreshKey = refreshKey,
                onOpen = { onEdit(source) },
                onReset = { resetting = source }
            )
        }
    }

    resetting?.let { target ->
        AlertDialog(
            onDismissRequest = { resetting = null },
            title = { Text("Reset \"${target.defaultsName()} default\"?") },
            text = { Text("It goes back to the design it came with. Your changes to it are lost.") },
            confirmButton = {
                TextButton(onClick = {
                    resetting = null
                    FlexDefaultsStore.reset(context, target)
                    onChanged()
                    Toast.makeText(context, "Default reset", Toast.LENGTH_SHORT).show()
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { resetting = null }) { Text("Keep") } }
        )
    }
}

@Composable
private fun DefaultRow(
    source: FlexPreset,
    refreshKey: Int,
    onOpen: () -> Unit,
    onReset: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DefaultThumbnail(source, refreshKey, modifier = Modifier.size(width = 84.dp, height = 96.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(source.defaultsName(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Where you share a ${source.defaultsName().lowercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Reset to original") }, onClick = {
                        menu = false
                        onReset()
                    })
                }
            }
        }
    }
}

/** A small picture of the blueprint on sample content, loaded and rendered off the main thread. */
@Composable
private fun DefaultThumbnail(source: FlexPreset, refreshKey: Int, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, source, refreshKey) {
        value = withContext(Dispatchers.Default) {
            runCatching { defaultThumbnailOf(context, FlexDefaultsStore.load(context, source)) }.getOrNull()
        }
    }
    Box(modifier = modifier.clip(MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

private const val DEFAULT_THUMB_SHORT = 360

/** Thumbnails by the blueprint they show (the sample content is the same every time). */
private val defaultThumbCache = LruCache<FlexSpec, Bitmap>(8)

/** Blocking work: call from Dispatchers.Default. */
private suspend fun defaultThumbnailOf(context: Context, spec: FlexSpec): Bitmap {
    defaultThumbCache.get(spec)?.let { return it }
    val content = FlexSampleContent.load(context)
    val assets = loadFlexAssets(context, spec, content, backgroundMax = 480, imageMax = 360)
    return FlexExport.render(fittedTo(spec, content, assets), content, assets, short = DEFAULT_THUMB_SHORT)
        .also { defaultThumbCache.put(spec, it) }
}
