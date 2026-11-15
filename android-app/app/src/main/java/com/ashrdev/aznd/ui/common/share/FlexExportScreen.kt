@file:OptIn(ExperimentalMaterial3Api::class)

package com.ashrdev.aznd.ui.common.share

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.saveImageToDevice
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.OutlinedButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** The preview is drawn once at this short side, whatever size is picked for the export. */
private const val PREVIEW_SHORT = 720

/** The file made for one size and format, so Share, Save and Copy one after the other draw the poster only once. */
private class MadeFile(val short: Int, val format: FlexImageFormat, val file: File)

/** Why a poster with a photo card that has no picture is not shared. */
internal const val FLEX_EMPTY_PHOTO_MESSAGE = "Add a picture to every photo card, or turn those cards off, before sharing."

/**
 * The Export screen, opened by Share on the Use screen: the whole poster as it will be exported, the image size
 * (the length of its shorter side) and format, and what to do with it: Share, Save to gallery or Copy to clipboard.
 * Every file made is also kept on the Saved page ([FlexSavedImages.keepFile], [onKept] says it was added) and the
 * choices behind the poster ([bindings]) are remembered as the next defaults of preset [presetId].
 *
 * [spec] is the poster as laid out on the Use screen (already fitted to [content]); it is drawn as it is.
 */
@Composable
internal fun FlexExportScreen(
    spec: FlexSpec,
    content: FlexContent,
    presetId: Long,
    presetName: String,
    bindings: FlexBindings,
    onKept: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    /** False when the poster was shared from a screen: nothing is remembered between such shares. */
    rememberChoices: Boolean = true
) {
    val context = LocalContext.current
    // Safety net: the Use screen does not open this screen for a poster with an empty photo card.
    if (spec.blocks.any { it.enabled && it.isEmptyPhoto() }) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, FLEX_EMPTY_PHOTO_MESSAGE, Toast.LENGTH_LONG).show()
            onBack()
        }
        return
    }
    val scope = rememberCoroutineScope()
    var short by rememberSaveable { mutableStateOf(FLEX_EXPORT_SIZES.first()) }
    var formatName by rememberSaveable { mutableStateOf(FlexImageFormat.PNG.name) }
    val format = FlexImageFormat.entries.firstOrNull { it.name == formatName } ?: FlexImageFormat.PNG
    var busy by remember { mutableStateOf(false) }
    var made by remember { mutableStateOf<MadeFile?>(null) }
    BackHandler { onBack() }

    val preview by produceState<Bitmap?>(null, spec, content) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val assets = loadFlexAssets(context, spec, content)
                FlexExport.render(spec, content, assets, short = PREVIEW_SHORT)
            }.getOrNull()
        }
    }

    /** Makes (or reuses) the file for the picked size and format, then hands its uri to [then]. */
    fun export(then: suspend (Uri) -> Unit) {
        if (busy) return
        busy = true
        val wantShort = short
        val wantFormat = format
        scope.launch {
            val reuse = made?.takeIf { it.short == wantShort && it.format == wantFormat && it.file.exists() }
            val file = try {
                reuse?.file ?: withContext(Dispatchers.Default) {
                    runCatching {
                        val assets = loadFlexAssets(
                            context, spec, content,
                            backgroundMax = maxOf(1400, wantShort),
                            imageMax = maxOf(720, wantShort / 2)
                        )
                        val bitmap = FlexExport.render(
                            spec, content, assets, short = wantShort, opaque = wantFormat == FlexImageFormat.JPG
                        )
                        try {
                            val written = FlexExport.write(context, bitmap, wantFormat)
                            if (written != null) {
                                FlexSavedImages.keepFile(context, written)
                                if (rememberChoices) FlexBindingStore.save(context, presetId, bindings)
                            }
                            written
                        } finally {
                            bitmap.recycle()
                        }
                    }.getOrNull()
                }
            } finally {
                busy = false
            }
            if (file == null) {
                Toast.makeText(context, "Couldn't make the image", Toast.LENGTH_SHORT).show()
            } else {
                if (reuse == null) {
                    made = MadeFile(wantShort, wantFormat, file)
                    onKept()
                }
                then(FlexExport.uriOf(context, file))
            }
        }
    }

    ScreenScaffold(
        title = "Export",
        subtitle = presetName,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val bmp = preview
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Poster preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }

            val bmp = preview
            if (bmp != null) {
                val ratio = bmp.height.toFloat() / bmp.width.coerceAtLeast(1)
                val (w, h) = if (ratio >= 1f) short to (short * ratio).roundToInt() else (short / ratio).roundToInt() to short
                Text(
                    "$w × $h px",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FLEX_EXPORT_SIZES.forEach { size ->
                    FilterChip(
                        selected = short == size,
                        onClick = { short = size },
                        label = { Text(size.toString()) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FlexImageFormat.entries.forEach { f ->
                    FilterChip(
                        selected = format == f,
                        onClick = { formatName = f.name },
                        label = { Text(f.label) }
                    )
                }
            }

            Button(
                onClick = { export { uri -> FlexExport.share(context, uri) } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Share") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { export { uri -> saveImageToDevice(context, uri) } },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Save to gallery", maxLines = 1) }
                OutlinedButton(
                    onClick = {
                        export { uri ->
                            FlexExport.copyToClipboard(context, uri)
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Copy to clipboard", maxLines = 1) }
            }
        }
    }
}
