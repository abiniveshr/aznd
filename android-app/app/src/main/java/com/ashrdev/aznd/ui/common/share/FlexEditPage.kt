@file:OptIn(ExperimentalMaterial3Api::class)

package com.ashrdev.aznd.ui.common.share

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.ui.common.tiling.TileTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The undo / redo history of one visit to the edit page. Every change is a new [FlexSpec]; the page works
 * on this copy and the card only gets it when Finish is tapped. Changes from a slider or a text field
 * ([change] with `continuous = true`) that follow each other closely count as one step, so one drag
 * of a slider is one Undo, not hundreds.
 */
internal class FlexEditHistory(private val initial: FlexSpec) {
    private val stack = UndoStack<FlexSpec>()

    var current by mutableStateOf(initial)
        private set

    val canUndo: Boolean get() = stack.canUndo
    val canRedo: Boolean get() = stack.canRedo

    /** True once the page's copy differs from what it started with. */
    val dirty: Boolean get() = current != initial

    fun change(continuous: Boolean = false, transform: (FlexSpec) -> FlexSpec) {
        val next = transform(current)
        if (next == current) return
        stack.push(current, continuous)
        current = next
    }

    fun undo() {
        stack.undo(current)?.let { current = it }
    }

    fun redo() {
        stack.redo(current)?.let { current = it }
    }
}

/**
 * Edit page of one block. Top: the card with everything dimmed except this block's tile, so its position and
 * size are what you see. Below: its size and alignment,
 * then everything else the block can do (look, items, text, photo). Top bar: Discard (leave without keeping this
 * visit's changes, like closing a screen) and Finish (keep the changes and go back).
 */
@Composable
internal fun FlexEditPage(
    startId: Int,
    initial: FlexSpec,
    content: FlexContent,
    onDone: (edited: FlexSpec, last: Int) -> Unit,
    onFinish: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var blockId by rememberSaveable { mutableStateOf(startId) }
    val history = remember { FlexEditHistory(initial) }
    val spec = history.current
    val assets = rememberFlexAssets(spec, content)
    val plan = remember(spec, content, assets) { FlexPainter.plan(spec, content, assets) }
    val planNow by rememberUpdatedState(plan)
    val block = spec.blocks.firstOrNull { it.id == blockId }
    val primary = MaterialTheme.colorScheme.primary

    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    fun requestFinish() {
        if (history.dirty) {
            confirmDiscard = true
        } else {
            onFinish()
        }
    }
    BackHandler { requestFinish() }

    // The card's content budget holds here too: a change that would take the card past what it can hold (more items,
    // a bigger size, a longer text, ...) is not made, and the page says the card cannot fit any more content.
    var full by remember { mutableStateOf(false) }
    fun change(continuous: Boolean = false, transform: (FlexSpec) -> FlexSpec) {
        val before = history.current
        val next = transform(before)
        if (next != before && FlexPainter.refuses(before, next, content, assets)) {
            full = true
            return
        }
        history.change(continuous) { next }
    }

    val picker = rememberImagePicker(aspect = null, outputWidth = 1600, outputHeight = 1600) { uri ->
        val uriText = uri.toString()
        change { sp ->
            sp.copy(blocks = sp.blocks.map { if (it.id == blockId) it.copy(keys = listOf(uriText)) else it })
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val veil = remember { FlexVeil() }
    var aligning by remember { mutableStateOf(false) }
    fun bestFit() {
        if (aligning) return
        val sp = history.current
        val snapshot = assets
        aligning = true
        scope.launch {
            val run = veil.Run(this)
            val result = try {
                withContext(Dispatchers.Default) { runCatching { FlexPainter.autoAlign(sp, content, snapshot) }.getOrNull() }
            } finally {
                aligning = false
            }
            run.finish(if (result != null && result.changed && history.current == sp) ({ if (history.current == sp) history.change { result.spec } }) else null)
            if (result == null) Toast.makeText(context, "Couldn't align the card", Toast.LENGTH_SHORT).show()
            else if (history.current == sp || history.current == result.spec) Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
        }
    }

    fun onChange(changed: FlexBlock, continuous: Boolean = false) {
        change(continuous) { sp ->
            sp.copy(blocks = sp.blocks.map { if (it.id == blockId) changed else it })
        }
    }

    ScreenScaffold(
        title = "Edit block",
        subtitle = block?.type?.label,
        onBack = { requestFinish() },
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        topBarActions = {
            IconButton(onClick = { requestFinish() }) {
                Icon(Icons.Default.Close, contentDescription = "Discard")
            }
            IconButton(onClick = { onDone(history.current, blockId) }) {
                Icon(Icons.Default.Check, contentDescription = "Finish")
            }
        }
    ) { padding ->
        if (block == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This block is no longer on the card.")
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val previewHeight = maxHeight * 0.34f
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        FlexPreview(
                            spec = spec,
                            content = content,
                            assets = assets,
                            plan = plan,
                            boxHeight = previewHeight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            veiled = veil.up,
                            canvasModifier = Modifier.pointerInput(Unit) {
                                detectTapGestures { p ->
                                    val now = planNow
                                    val k = now.width / size.width.toFloat()
                                    now.hit(p.x * k, p.y * k)?.id?.let { blockId = it }
                                }
                            },
                            overlay = { k ->
                                val box = plan.box(blockId)
                                val radius = CornerRadius(10.dp.toPx())
                                val scrim = Path().apply {
                                    fillType = PathFillType.EvenOdd
                                    addRect(Rect(0f, 0f, size.width, size.height))
                                    if (box != null) {
                                        addRoundRect(
                                            RoundRect(
                                                Rect(box.left * k, box.top * k, (box.left + box.width) * k, (box.top + box.height) * k),
                                                radius
                                            )
                                        )
                                    }
                                }
                                drawPath(scrim, Color.Black.copy(alpha = 0.8f))
                                if (box != null) {
                                    drawRoundRect(
                                        primary,
                                        Offset(box.left * k, box.top * k),
                                        Size(box.width * k, box.height * k),
                                        radius,
                                        style = Stroke(width = 2.5.dp.toPx())
                                    )
                                }
                            }
                        )
                        FlexFloatingMenu(
                            actions = listOfNotNull(
                                FlexMenuAction(Icons.AutoMirrored.Filled.Undo, "Undo", history.canUndo) { history.undo() },
                                FlexMenuAction(Icons.AutoMirrored.Filled.Redo, "Redo", history.canRedo) { history.redo() },
                                if (spec.style.layoutMode == FlexLayoutMode.SPLIT) {
                                    FlexMenuAction(Icons.Default.AutoFixHigh, if (aligning) "Fitting…" else "Best fit", !aligning) { bestFit() }
                                } else null
                            ),
                            modifier = Modifier
                                .matchParentSize()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }

                    val tile = plan.box(blockId)
                    Text(
                        text = if (tile != null) {
                            "Tile ${TileTree.leaves(plan.tree).indexOf(blockId) + 1} of ${plan.boxes.size} · " +
                                "${(tile.width / plan.contentWidth * 100).roundToInt()}% of the width, " +
                                "${(tile.height / plan.contentHeight * 100).roundToInt()}% of the height" +
                                if (tile.scale < 0.999f) " · content shrunk to ${(tile.scale * 100).roundToInt()}%" else ""
                        } else if (!content.has(block.type)) {
                            "Nothing to show for this block here."
                        } else if (!block.enabled) {
                            "This block is turned off, so it has no tile."
                        } else {
                            "Not shown: this card cannot fit any more content."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FlexSection("Size and alignment") {
                            Text("Across", style = MaterialTheme.typography.labelMedium)
                            ChipChoice(
                                options = FlexAlign.entries,
                                selected = block.hAlign,
                                label = { it.label },
                                onSelect = { onChange(block.copy(hAlign = it)) }
                            )
                            Text("Up and down", style = MaterialTheme.typography.labelMedium)
                            ChipChoice(
                                options = FlexVAlign.entries,
                                selected = block.vAlign,
                                label = { it.label },
                                onSelect = { onChange(block.copy(vAlign = it)) }
                            )
                            if (block.type != FlexBlockType.PHOTO) {
                                LabeledSlider(
                                    label = "Size",
                                    valueText = "${(block.size * 100).roundToInt()}%",
                                    value = block.size,
                                    range = 0.5f..2f
                                ) { onChange(block.copy(size = it), continuous = true) }
                            }
                        }

                        BlockOptions(
                            block = block,
                            spec = spec,
                            content = content,
                            onChange = { changed, continuous -> onChange(changed, continuous) },
                            onPickPhoto = { picker.launch() }
                        )

                        TextButton(onClick = { confirmRemove = true }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Remove block", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (full) FlexFullDialog { full = false }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard your changes?") },
            text = { Text("Discard leaves this page without keeping what you changed here. Finish keeps it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onFinish()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } }
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove this block?") },
            text = { Text("It is taken off the card. Your other changes on this page are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onDone(history.current.let { sp -> sp.copy(blocks = sp.blocks.filter { it.id != blockId }) }, blockId)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } }
        )
    }
}

/** What a block shows and how: look variant, items, detail switch, text, photo and its crop. */
@Composable
private fun BlockOptions(
    block: FlexBlock,
    spec: FlexSpec,
    content: FlexContent,
    onChange: (changed: FlexBlock, continuous: Boolean) -> Unit,
    onPickPhoto: () -> Unit
) {
    val type = block.type
    val hasOptions = type.variants.size > 1 || type.listy || type.flagLabel != null ||
        type == FlexBlockType.HEADER || type == FlexBlockType.TEXT || type == FlexBlockType.PHOTO
    if (!hasOptions) return

    FlexSection(type.label) {
        if (type.variants.size > 1) {
            ChipChoice(
                options = type.variants.indices.toList(),
                selected = block.variant,
                label = { type.variants[it] },
                onSelect = { onChange(block.copy(variant = it), false) }
            )
        }
        if (type.listy) {
            val choices = content.choices(type)
            if (choices.isNotEmpty()) {
                MultiSelectDropdown("Show", choices, block.keys) { onChange(block.copy(keys = it), false) }
            }
            if (block.keys == null) {
                LabeledSlider(
                    label = "Max items",
                    valueText = block.limit.toString(),
                    value = block.limit.toFloat(),
                    range = 1f..20f,
                    steps = 18
                ) { onChange(block.copy(limit = it.roundToInt()), true) }
            }
        }
        type.flagLabel?.let { label ->
            SwitchRow(label, block.flag) { onChange(block.copy(flag = it), false) }
        }
        if (type == FlexBlockType.HEADER || type == FlexBlockType.TEXT) {
            OutlinedTextField(
                value = block.text,
                onValueChange = { onChange(block.copy(text = it.take(300)), true) },
                label = { Text(if (type == FlexBlockType.TEXT) "Text" else "Title (empty = default)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (type == FlexBlockType.PHOTO) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onPickPhoto) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose photo")
                }
                if (block.keys != null) {
                    TextButton(onClick = { onChange(block.copy(keys = null), false) }) { Text("Use default") }
                }
            }
            // A photo takes at most half the card, so the shapes on offer are those that suit that half.
            val limit = spec.style.photoLimit()
            val options = FlexPhotoRatio.entries.filter { it.allowedOn(limit) }
            val current = options.firstOrNull { abs(it.ratio - block.photoRatio) < 0.01f } ?: FlexPhotoRatio.AUTO
            val real = options.filter { it.ratio > 0f }
            val lowest = real.minByOrNull { it.ratio }?.label
            val highest = real.maxByOrNull { it.ratio }?.label
            DropdownField(
                label = "Crop shape (at most half the card" + if (lowest != null && highest != null) ", $lowest to $highest)" else ")",
                options = options,
                selected = current,
                optionLabel = { it.label }
            ) { onChange(block.copy(photoRatio = it.ratio), false) }
            LabeledSlider("Zoom", "${(block.photoZoom * 10).roundToInt() / 10f}×", block.photoZoom, 1f..3f) {
                onChange(block.copy(photoZoom = it), true)
            }
            LabeledSlider("Move left / right", "${(block.photoX * 100).roundToInt()}%", block.photoX, 0f..1f) {
                onChange(block.copy(photoX = it), true)
            }
            LabeledSlider("Move up / down", "${(block.photoY * 100).roundToInt()}%", block.photoY, 0f..1f) {
                onChange(block.copy(photoY = it), true)
            }
        }
    }
}
