package com.ashrdev.aznd.ui.common.share

import android.graphics.Bitmap
import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.saveImageToDevice
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.components.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Presets page: the built-in Default card of the current source first, then the card presets the person made
 * ([FlexCardPreset]), each with a small picture of the card filled with the source's real content.
 *
 * Not to be confused with [FlexPreset], which is the share source (home, workout, routine ...).
 *
 * [content] is null while the source is still loading. [onEdit] gets the preset to edit; for the Default entry it is
 * an unsaved one (id 0) made from the current card. [onChanged] says the stored presets changed (duplicate, delete)
 * and [onKept] that a copy of an exported card was added to the Saved page.
 */
@Composable
internal fun FlexPresetsPage(
    bottom: Dp,
    source: FlexPreset,
    content: FlexContent?,
    presets: List<FlexCardPreset>,
    onEdit: (FlexCardPreset) -> Unit,
    onChanged: () -> Unit,
    onKept: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presetStore = remember { FlexPresetStore(context) }
    var deleting by remember { mutableStateOf<FlexCardPreset?>(null) }
    var busy by remember { mutableStateOf(false) }

    // The source's own card, the way it was last left (or its default).
    val defaultSpec by produceState<FlexSpec?>(null, source, content) {
        val c = content
        value = if (c == null) null else withContext(Dispatchers.Default) {
            FlexStore(context).load(source)?.reconciled()?.withoutStalePhotos(c) ?: source.defaultSpec(c)
        }
    }

    /** "Edit" on Default: a new, unsaved preset made from the current card, with the sample photos in place of the real ones. */
    fun editDefault(spec: FlexSpec) {
        scope.launch {
            val sample = FlexSampleContent.load(context)
            val template = withContext(Dispatchers.Default) { spec.asTemplate(sample) }
            onEdit(FlexCardPreset(id = 0, name = presetStore.nextDefaultName(), spec = template, updatedAt = 0))
        }
    }

    /** Renders [spec] (a template is fitted to the content first) at full size, keeps a copy and hands the file on. */
    fun export(spec: FlexSpec, template: Boolean, then: suspend (android.net.Uri) -> Unit) {
        val c = content ?: return
        if (busy) return
        busy = true
        scope.launch {
            val uri = try {
                withContext(Dispatchers.Default) {
                    val applied = if (template) spec.appliedTo(c) else spec
                    val assets = loadFlexAssets(context, applied, c)
                    val card = fittedTo(applied, c, assets)
                    val bitmap = FlexExport.render(card, c, assets)
                    FlexSavedImages.keep(context, bitmap)
                    FlexExport.writePng(context, bitmap)
                }
            } catch (e: Exception) {
                null
            } finally {
                busy = false
            }
            if (uri == null) {
                Toast.makeText(context, "Couldn't make the image", Toast.LENGTH_SHORT).show()
            } else {
                onKept()
                then(uri)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "default") {
            val spec = defaultSpec
            PresetRow(
                name = "Default",
                detail = "Your card for ${source.label.lowercase()}, as it is now",
                spec = spec,
                template = false,
                content = content,
                canExport = spec != null && content != null && !busy,
                isDefault = true,
                onOpen = { spec?.let { editDefault(it) } },
                onShare = { spec?.let { export(it, false) { uri -> FlexExport.share(context, uri) } } },
                onSave = { spec?.let { export(it, false) { uri -> saveImageToDevice(context, uri) } } },
                onDuplicate = {},
                onDelete = {}
            )
        }
        items(presets, key = { "p${it.id}" }) { preset ->
            PresetRow(
                name = preset.name,
                detail = "Updated " + DateUtils.getRelativeTimeSpanString(
                    preset.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                ),
                spec = preset.spec,
                template = true,
                content = content,
                canExport = content != null && !busy,
                isDefault = false,
                onOpen = { onEdit(preset) },
                onShare = { export(preset.spec, true) { uri -> FlexExport.share(context, uri) } },
                onSave = { export(preset.spec, true) { uri -> saveImageToDevice(context, uri) } },
                onDuplicate = {
                    presetStore.duplicate(preset.id)
                    onChanged()
                },
                onDelete = { deleting = preset }
            )
        }
        if (presets.isEmpty()) {
            item(key = "hint") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Dashboard,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap + to make your first preset",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("The preset is removed. Pictures you already saved from it stay on the Saved page.") },
            confirmButton = {
                TextButton(onClick = {
                    presetStore.delete(target.id)
                    deleting = null
                    onChanged()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } }
        )
    }
}

@Composable
private fun PresetRow(
    name: String,
    detail: String,
    spec: FlexSpec?,
    template: Boolean,
    content: FlexContent?,
    canExport: Boolean,
    isDefault: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
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
            CardThumbnail(spec, content, template, modifier = Modifier.size(width = 84.dp, height = 96.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(enabled = canExport, onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
            IconButton(enabled = canExport, onClick = onSave) {
                Icon(Icons.Default.Download, contentDescription = "Save to gallery")
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (isDefault) {
                        DropdownMenuItem(text = { Text("Edit as new preset") }, onClick = {
                            menu = false
                            onOpen()
                        })
                    } else {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = {
                            menu = false
                            onOpen()
                        })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                            menu = false
                            onDuplicate()
                        })
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

/** A small picture of the card, rendered off the main thread and remembered by what was drawn. */
@Composable
private fun CardThumbnail(spec: FlexSpec?, content: FlexContent?, template: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, spec, content, template) {
        val sp = spec
        val c = content
        value = if (sp == null || c == null) null else withContext(Dispatchers.Default) {
            thumbnailOf(context, sp, c, template)
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

private const val THUMB_SHORT = 360

/** Thumbnails by what they show (the card as drawn plus its content); a few are enough for a screenful. */
private val thumbCache = LruCache<Any, Bitmap>(16)

private fun thumbnailOf(context: android.content.Context, spec: FlexSpec, content: FlexContent, template: Boolean): Bitmap? {
    val applied = if (template) spec.appliedTo(content) else spec
    val key = applied to content
    thumbCache.get(key)?.let { return it }
    return runCatching {
        val assets = loadFlexAssets(context, applied, content, backgroundMax = 480, imageMax = 360)
        FlexExport.render(fittedTo(applied, content, assets), content, assets, short = THUMB_SHORT)
    }.getOrNull()?.also { thumbCache.put(key, it) }
}

/**
 * The card laid out for this content, the way the editor fits a card when it opens: a tiled card with no tiling yet
 * gets a quiet auto align, one with a tiling is rescued (the content can differ from the one it was made on, e.g. fewer
 * pictures), a free card has its boxes placed. Falls back to the card as it is. Blocking; call it off the main thread.
 */
private fun fittedTo(spec: FlexSpec, content: FlexContent, assets: FlexAssets): FlexSpec = runCatching {
    when (spec.style.layoutMode) {
        FlexLayoutMode.SPLIT ->
            if (spec.layout == null) FlexPainter.autoAlign(spec, content, assets, rescale = false).spec
            else FlexPainter.rescue(spec, content, assets) ?: spec
        FlexLayoutMode.FREE -> FlexPainter.placeBoxes(spec, content, assets)
    }
}.getOrDefault(spec)

/**
 * [rememberFlexAssets] without composition: loads (blocking, call it off the main thread) the background and
 * the pictures [spec] needs, the way rememberFlexAssets does, so a card can be rendered from a coroutine.
 */
internal fun loadFlexAssets(
    context: android.content.Context,
    spec: FlexSpec,
    content: FlexContent,
    backgroundMax: Int = 1400,
    imageMax: Int = 720
): FlexAssets {
    FlexFonts.attach(context)
    val style = spec.style
    val bgUri = if (style.bgMode == FlexBgMode.PHOTO) style.photoUri ?: content.photoUri else null
    val raw = bgUri?.let { FlexBitmaps.load(context, it, backgroundMax) }
    val background = raw?.let { FlexBitmaps.blur(it, (style.photoBlur * 20f).toInt() / 20f) }

    val needed = LinkedHashSet<String>()
    if (spec.blocks.any { it.enabled && it.type == FlexBlockType.STREAKS }) {
        content.streaks.forEach { s -> s.logoUri?.let { needed += it } }
    }
    spec.blocks.filter { it.enabled && it.type == FlexBlockType.PHOTO }.forEach { b ->
        (b.keys?.firstOrNull() ?: content.photoUri)?.let { needed += it }
    }
    if (spec.blocks.any { it.enabled && it.type == FlexBlockType.MODEL }) content.modelUri?.let { needed += it }
    val images = needed.mapNotNull { uri -> FlexBitmaps.load(context, uri, imageMax)?.let { uri to it } }.toMap()
    return FlexAssets(background, images, ready = true)
}
