package com.ashrdev.aznd.ui.common.share

import android.content.Context
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
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Presets page: the built-in presets first (one per share source), then the person's own ([FlexCardPreset]),
 * each with a small picture of the poster filled with the person's real data (the choices Configure would start
 * from, see [defaultBindings]). Tapping a row starts Configure ([onConfigure]); the row's menu has Edit (the preset
 * editor on sample data, [onEdit]), Duplicate, Delete (own presets only) and Reset (a built-in preset goes back to
 * what it ships with; an own preset goes back to before its last update).
 *
 * Not to be confused with [FlexPreset], which is the share source (home, workout, routine ...).
 *
 * [library] is null while the person's data is still loading (thumbnails wait for it); [share] is where Flex was
 * opened from, null for the home screen. [onChanged] says the stored presets changed.
 */
@Composable
internal fun FlexPresetsPage(
    bottom: Dp,
    library: FlexLibrary?,
    share: FlexShareContext?,
    workoutViewModel: WorkoutViewModel,
    streakViewModel: StreakViewModel,
    presets: List<FlexCardPreset>,
    onConfigure: (FlexCardPreset) -> Unit,
    onEdit: (FlexCardPreset) -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presetStore = remember { FlexPresetStore(context) }
    var deleting by remember { mutableStateOf<FlexCardPreset?>(null) }
    var resetting by remember { mutableStateOf<FlexCardPreset?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(presets, key = { "p${it.id}" }) { preset ->
            PresetRow(
                preset = preset,
                library = library,
                share = share,
                workoutViewModel = workoutViewModel,
                streakViewModel = streakViewModel,
                onOpen = { onConfigure(preset) },
                onEdit = { onEdit(preset) },
                onDuplicate = {
                    presetStore.duplicate(preset.id)
                    onChanged()
                },
                onDelete = { deleting = preset },
                onReset = {
                    if (preset.isShipped) {
                        resetting = preset
                    } else {
                        presetStore.undo(preset.id)
                        onChanged()
                        Toast.makeText(context, "Went back to before the last update", Toast.LENGTH_SHORT).show()
                    }
                }
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
    resetting?.let { target ->
        AlertDialog(
            onDismissRequest = { resetting = null },
            title = { Text("Reset \"${target.name}\"?") },
            text = { Text("The preset goes back to the design it came with. Your changes to it are lost.") },
            confirmButton = {
                TextButton(onClick = {
                    resetting = null
                    scope.launch {
                        val done = presetStore.resetShipped(target.id)
                        onChanged()
                        Toast.makeText(
                            context,
                            if (done != null) "Preset reset" else "Couldn't reset the preset",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { resetting = null }) { Text("Keep") } }
        )
    }
}

@Composable
private fun PresetRow(
    preset: FlexCardPreset,
    library: FlexLibrary?,
    share: FlexShareContext?,
    workoutViewModel: WorkoutViewModel,
    streakViewModel: StreakViewModel,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val detail = when {
        preset.isShipped -> "Built in"
        else -> "Updated " + DateUtils.getRelativeTimeSpanString(
            preset.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
        )
    }
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
            CardThumbnail(
                preset, library, share, workoutViewModel, streakViewModel,
                modifier = Modifier.size(width = 84.dp, height = 96.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(preset.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    detail,
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
                    DropdownMenuItem(text = { Text("Edit") }, onClick = {
                        menu = false
                        onEdit()
                    })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                        menu = false
                        onDuplicate()
                    })
                    if (!preset.isShipped) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menu = false
                                onDelete()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (preset.isShipped) "Reset to original" else "Undo last change") },
                        enabled = preset.isShipped || preset.history.isNotEmpty(),
                        onClick = {
                            menu = false
                            onReset()
                        }
                    )
                }
            }
        }
    }
}

/** A small picture of the poster with the person's real data, rendered off the main thread. */
@Composable
private fun CardThumbnail(
    preset: FlexCardPreset,
    library: FlexLibrary?,
    share: FlexShareContext?,
    workoutViewModel: WorkoutViewModel,
    streakViewModel: StreakViewModel,
    modifier: Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, preset.spec, library, share) {
        val lib = library
        value = if (lib == null) null else withContext(Dispatchers.Default) {
            runCatching { thumbnailOf(context, preset, lib, share, workoutViewModel, streakViewModel) }.getOrNull()
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

/** What a thumbnail shows: the preset's spec and the choices it was filled with (the library is one load of the person's data). */
private data class ThumbKey(val spec: FlexSpec, val bindings: FlexBindings, val library: FlexLibrary)

/** Thumbnails by what they show; a few are enough for a screenful. */
private val thumbCache = LruCache<ThumbKey, Bitmap>(16)

/** Blocking work and the content load: call from Dispatchers.Default. */
private suspend fun thumbnailOf(
    context: Context,
    preset: FlexCardPreset,
    library: FlexLibrary,
    share: FlexShareContext?,
    workoutViewModel: WorkoutViewModel,
    streakViewModel: StreakViewModel
): Bitmap? {
    val spec = preset.spec
    val bindings = defaultBindings(spec, library, FlexBindingStore.load(context, preset.id), share)
    val key = ThumbKey(spec, bindings, library)
    thumbCache.get(key)?.let { return it }
    val content = loadBoundContent(context, spec, bindings, library, workoutViewModel, streakViewModel)
    val bound = spec.bound(bindings)
    val assets = loadFlexAssets(context, bound, content, backgroundMax = 480, imageMax = 360)
    return FlexExport.render(fittedTo(bound, content, assets), content, assets, short = THUMB_SHORT)
        .also { thumbCache.put(key, it) }
}

/**
 * The card laid out for this content, the way the editor fits a card when it opens: a tiled card with no tiling yet
 * gets a quiet auto align, one with a tiling is rescued (the content can differ from the one it was made on, e.g. fewer
 * pictures), a free card has its boxes placed. Falls back to the card as it is. Blocking; call it off the main thread.
 */
internal fun fittedTo(spec: FlexSpec, content: FlexContent, assets: FlexAssets): FlexSpec = runCatching {
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

    val needed = flexNeededImages(spec, content)
    val images = needed.mapNotNull { uri -> FlexBitmaps.load(context, uri, imageMax)?.let { uri to it } }.toMap()
    return FlexAssets(background, images, ready = true)
}
