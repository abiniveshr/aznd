@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ashrdev.aznd.ui.common.share

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.FillCopySource
import com.ashrdev.aznd.ui.common.FillEditorDialog
import com.ashrdev.aznd.ui.common.ReorderableItem
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.dragReorderHandle
import com.ashrdev.aznd.ui.common.dragReorderOnLongPress
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.common.rememberReorderState
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.common.saveImageToDevice
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.theme.FillBase
import com.ashrdev.aznd.ui.theme.FillStyle
import com.ashrdev.aznd.ui.theme.GradientKind
import com.ashrdev.aznd.ui.theme.GradientSpec
import com.ashrdev.aznd.ui.theme.LocalAzndStyle
import com.ashrdev.aznd.ui.theme.ResolvedFill
import com.ashrdev.aznd.ui.theme.ThemeColor
import com.ashrdev.aznd.ui.theme.ThemePresets
import com.ashrdev.aznd.ui.theme.azndFill
import com.ashrdev.aznd.ui.theme.azndStyleOf
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** Left to right: how the card looks, what is on it, the card itself. Opens on the middle one. */
private val FlexBarPages = listOf(
    BarPage("Look", Icons.Default.Palette),
    BarPage("Content", Icons.Default.Widgets),
    BarPage("Flex", Icons.Default.FitnessCenter)
)
private const val PAGE_CONTENT = 1

/** What the shared image picker is currently choosing a photo for. */
private sealed interface PickTarget {
    data object Background : PickTarget
    data class BlockPhoto(val blockId: Int) : PickTarget
}

/**
 * Flex: the card maker behind every share button. Three swipeable pages, also tabs in the bottom bar:
 * Look (colours, text, background), Content (the blocks; hold and drag to reorder) and Flex (the card,
 * large; later the place to edit the content right on the card). Share and Save sit in the top bar.
 * The card starts from the [preset]'s defaults and comes back the way it was last left.
 */
@Composable
fun FlexScreen(
    preset: FlexPreset,
    a: Long,
    b: Long,
    s: String,
    workoutViewModel: WorkoutViewModel,
    streakViewModel: StreakViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { FlexStore(context) }

    val content by produceState<FlexContent?>(null, preset, a, b, s) {
        value = FlexContentLoader.load(preset, a, b, s, workoutViewModel, streakViewModel)
    }
    val defaultSpec = remember(content) { content?.let { preset.defaultSpec(it) } }

    var spec by remember(preset) { mutableStateOf<FlexSpec?>(null) }
    LaunchedEffect(content) {
        val c = content ?: return@LaunchedEffect
        if (spec == null) spec = store.load(preset)?.reconciled() ?: preset.defaultSpec(c)
    }

    // Edits are kept per preset. A card still equal to the preset's default is not stored, so a later
    // workout with a photo still starts from the photo look.
    fun persist(sp: FlexSpec) {
        if (sp == defaultSpec) store.reset(preset) else store.save(preset, sp)
    }
    LaunchedEffect(spec) {
        val sp = spec ?: return@LaunchedEffect
        delay(400)
        persist(sp)
    }
    val latestSpec by rememberUpdatedState(spec)
    val latestDefault by rememberUpdatedState(defaultSpec)
    DisposableEffect(preset) {
        onDispose {
            latestSpec?.let { sp -> if (sp == latestDefault) store.reset(preset) else store.save(preset, sp) }
        }
    }

    val assets = rememberFlexAssets(spec, content)
    // The layout of the card as it is right now: its height, shrink, block positions, what is hidden or cut off.
    val plan = remember(spec, content, assets) {
        val sp = spec
        val ct = content
        if (sp != null && ct != null) FlexPainter.plan(sp, ct, assets) else null
    }
    var previewCollapsed by rememberSaveable { mutableStateOf(false) }

    fun update(transform: (FlexSpec) -> FlexSpec) {
        spec = spec?.let(transform)
    }
    fun updateStyle(transform: (FlexStyle) -> FlexStyle) = update { it.copy(style = transform(it.style)) }

    var pickTarget by remember { mutableStateOf<PickTarget?>(null) }
    val picker = rememberImagePicker(aspect = null, outputWidth = 1600, outputHeight = 1600) { uri ->
        val uriText = uri.toString()
        when (val target = pickTarget) {
            PickTarget.Background -> updateStyle { it.copy(photoUri = uriText, bgMode = FlexBgMode.PHOTO) }
            is PickTarget.BlockPhoto -> update { sp ->
                sp.copy(blocks = sp.blocks.map { bl -> if (bl.id == target.blockId) bl.copy(keys = listOf(uriText)) else bl })
            }
            null -> Unit
        }
    }

    val pagerState = rememberPagerState(initialPage = PAGE_CONTENT, pageCount = { FlexBarPages.size })
    val bar = rememberSubPageBar(pagerState, FlexBarPages)
    val ready = spec != null && content != null

    fun export(then: suspend (Uri) -> Unit) {
        val sp = spec ?: return
        val ct = content ?: return
        val snapshot = assets
        scope.launch {
            val uri = withContext(Dispatchers.Default) {
                FlexExport.writePng(context, FlexExport.render(sp, ct, snapshot))
            }
            if (uri == null) Toast.makeText(context, "Couldn't make the image", Toast.LENGTH_SHORT).show()
            else then(uri)
        }
    }

    ScreenScaffold(
        title = "Flex",
        subtitle = preset.label,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = bar,
        topBarActions = {
            IconButton(enabled = ready, onClick = { export { uri -> saveImageToDevice(context, uri) } }) {
                Icon(Icons.Default.Download, contentDescription = "Save to gallery")
            }
            IconButton(enabled = ready, onClick = { export { uri -> FlexExport.share(context, uri) } }) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
        }
    ) { padding ->
        val sp = spec
        val ct = content
        val pl = plan
        if (sp == null || ct == null || pl == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val bottom = padding.calculateBottomPadding()
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val previewHeight = maxHeight * 0.38f
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> EditorPage(sp, ct, assets, pl, previewHeight) {
                            LookTab(
                                spec = sp,
                                hasDefaultPhoto = ct.photoUri != null,
                                bottom = bottom,
                                style = { updateStyle(it) },
                                onPickPhoto = {
                                    pickTarget = PickTarget.Background
                                    picker.launch()
                                },
                                onReset = {
                                    defaultSpec?.let { spec = it }
                                    store.reset(preset)
                                }
                            )
                        }
                        1 -> EditorPage(
                            spec = sp,
                            content = ct,
                            assets = assets,
                            plan = pl,
                            previewHeight = previewHeight,
                            collapsible = true,
                            collapsed = previewCollapsed,
                            onToggleCollapsed = { previewCollapsed = !previewCollapsed }
                        ) {
                            ContentTab(
                                spec = sp,
                                content = ct,
                                assets = assets,
                                plan = pl,
                                bottom = bottom,
                                update = { update(it) },
                                onPickPhoto = { blockId ->
                                    pickTarget = PickTarget.BlockPhoto(blockId)
                                    picker.launch()
                                }
                            )
                        }
                        else -> FlexViewPage(
                            spec = sp,
                            content = ct,
                            assets = assets,
                            plan = pl,
                            bottom = bottom,
                            onRowsChange = { shown, rows, old, moved -> update { applyRows(it, shown, rows, old, moved) } },
                            onWidths = { weights ->
                                update { sp -> sp.copy(blocks = sp.blocks.map { b -> weights[b.id]?.let { b.copy(width = it) } ?: b }) }
                            },
                            onSizeBlock = { id, size ->
                                update { sp -> sp.copy(blocks = sp.blocks.map { if (it.id == id) it.copy(size = size) else it }) }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A page with the live card on top and one tab's controls below it. The card can be collapsible (Content
 * page) to give the list the whole screen. A warning appears under it when the card is too full.
 */
@Composable
private fun EditorPage(
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    plan: FlexPlan,
    previewHeight: Dp,
    collapsible: Boolean = false,
    collapsed: Boolean = false,
    onToggleCollapsed: () -> Unit = {},
    controls: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (collapsible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleCollapsed)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Preview", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Icon(
                    if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = if (collapsed) "Show preview" else "Hide preview"
                )
            }
        }
        AnimatedVisibility(visible = !collapsible || !collapsed) {
            FlexPreview(
                spec = spec,
                content = content,
                assets = assets,
                plan = plan,
                boxHeight = previewHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        FlexWarning(spec, plan, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()) { controls() }
    }
}

/**
 * The card, drawn by the same painter that makes the exported image. Its shape glides when the size or the
 * content's height changes (Free size follows the content) instead of snapping. [canvasModifier] takes
 * gestures; [overlay] draws on top, given the scale from card pixels to canvas pixels.
 */
@Composable
fun FlexPreview(
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    plan: FlexPlan,
    boxHeight: Dp,
    modifier: Modifier = Modifier,
    canvasModifier: Modifier = Modifier,
    overlay: (DrawScope.(scale: Float) -> Unit)? = null,
    /** Read while drawing (so changes redraw without recomposing): blocks being held or gliding. */
    poses: (() -> FlexPoses?)? = null
) {
    val ratio by animateFloatAsState(
        targetValue = FLEX_CARD_WIDTH / plan.height,
        animationSpec = tween(250),
        label = "flexRatio"
    )
    BoxWithConstraints(
        modifier = modifier.height(boxHeight),
        contentAlignment = Alignment.Center
    ) {
        val width = minOf(maxWidth, boxHeight * ratio)
        Canvas(
            modifier = Modifier
                .width(width)
                .aspectRatio(ratio)
                .then(canvasModifier)
        ) {
            val k = size.width / FLEX_CARD_WIDTH
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.clipRect(0f, 0f, size.width, size.height)
                FlexPainter.draw(native, size.width, spec, content, assets, plan, poses?.invoke())
                native.restore()
            }
            overlay?.invoke(this, k)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Content tab: the blocks
// ---------------------------------------------------------------------------------------------

@Composable
private fun ContentTab(
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    plan: FlexPlan,
    bottom: Dp,
    update: ((FlexSpec) -> FlexSpec) -> Unit,
    onPickPhoto: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }

    // The hint is the first list item, so the blocks start at index 1.
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(
        listState = listState,
        firstItemIndex = 1,
        itemCount = spec.blocks.size
    ) { from, to ->
        update { sp ->
            if (from !in sp.blocks.indices || to !in sp.blocks.indices) sp
            else sp.copy(blocks = sp.blocks.toMutableList().also { it.add(to, it.removeAt(from)) })
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "hint") {
            Text(
                "Drag the handle, or hold a block, to reorder. Opening a block shows it on its own.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(spec.blocks, key = { it.id }) { block ->
            ReorderableItem(reorder, block.id) {
                BlockCard(
                    block = block,
                    spec = spec,
                    content = content,
                    assets = assets,
                    plan = plan,
                    expanded = expanded == block.id,
                    onToggleExpanded = { expanded = if (expanded == block.id) null else block.id },
                    onChange = { changed ->
                        update { sp -> sp.copy(blocks = sp.blocks.map { if (it.id == block.id) changed else it }) }
                    },
                    onDelete = {
                        update { sp -> sp.copy(blocks = sp.blocks.filter { it.id != block.id }) }
                    },
                    onPickPhoto = { onPickPhoto(block.id) },
                    onWidth = { v ->
                        update { sp ->
                            val widths = resizeWidth(plan, block.id, v)
                            if (widths.isEmpty()) {
                                sp.copy(blocks = sp.blocks.map { if (it.id == block.id) it.copy(width = v) else it })
                            } else {
                                sp.copy(blocks = sp.blocks.map { b -> widths[b.id]?.let { b.copy(width = it) } ?: b })
                            }
                        }
                    },
                    onPlaceBeside = { beside ->
                        // The first explicit choice freezes the current arrangement, so nothing else moves.
                        update { sp ->
                            val frozen = applyRows(sp, plan.shownIds, plan.rows)
                            frozen.copy(blocks = frozen.blocks.map {
                                if (it.id == block.id) it.copy(place = if (beside) FlexPlace.BESIDE else FlexPlace.BELOW) else it
                            })
                        }
                    },
                    dragModifier = Modifier.dragReorderOnLongPress(reorder, block.id),
                    handleModifier = Modifier.dragReorderHandle(reorder, block.id)
                )
            }
        }
        item(key = "add") {
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add block")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    FlexBlockType.entries.filter { content.has(it) }.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            onClick = {
                                menu = false
                                update { sp ->
                                    sp.copy(
                                        blocks = sp.blocks + FlexBlock(
                                            id = sp.nextBlockId(),
                                            type = type,
                                            limit = if (type == FlexBlockType.ROUTINE) 10 else 3,
                                            text = if (type == FlexBlockType.TEXT) "Your text here" else ""
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockCard(
    block: FlexBlock,
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    plan: FlexPlan,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onChange: (FlexBlock) -> Unit,
    onDelete: () -> Unit,
    onPickPhoto: () -> Unit,
    onPlaceBeside: (Boolean) -> Unit,
    onWidth: (Float) -> Unit,
    /** Put on the header row: hold it, then drag the block to a new place. */
    dragModifier: Modifier = Modifier,
    /** Put on the hamburger icon: drag straight away. */
    handleModifier: Modifier = Modifier
) {
    val type = block.type
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = dragModifier, verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Drag to reorder",
                    modifier = handleModifier
                        .size(40.dp)
                        .padding(8.dp)
                )
                Switch(checked = block.enabled, onCheckedChange = { onChange(block.copy(enabled = it)) })
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggleExpanded)) {
                    Text(type.label, style = MaterialTheme.typography.titleMedium)
                    val note = when {
                        !content.has(type) -> "Nothing to show here"
                        block.enabled && block.id in plan.hidden -> "Not shown: too many blocks for this size"
                        else -> null
                    }
                    if (note != null) {
                        Text(
                            note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Hide options" else "Show options"
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BlockPreview(spec, content, assets, plan, block)
                LabeledSlider(
                    label = "Size",
                    valueText = "${(block.size * 100).roundToInt()}%",
                    value = block.size,
                    range = 0.5f..2f
                ) { onChange(block.copy(size = it)) }
                val row = plan.rows.firstOrNull { block.id in it }
                val beside = if (row != null) row.indexOf(block.id) > 0 else block.place == FlexPlace.BESIDE
                SwitchRow("Place beside the previous block", beside, onPlaceBeside)
                val share = if (row != null) widthFraction(plan, block.id) else block.width.coerceIn(MIN_BLOCK_WIDTH, 1f)
                LabeledSlider(
                    label = "Width",
                    valueText = "${(share * 100).roundToInt()}% of the row",
                    value = share,
                    range = MIN_BLOCK_WIDTH..1f
                ) { onWidth(it) }
                if (type.variants.size > 1) {
                    ChipChoice(
                        options = type.variants.indices.toList(),
                        selected = block.variant,
                        label = { type.variants[it] },
                        onSelect = { onChange(block.copy(variant = it)) }
                    )
                }
                if (type.listy) {
                    val choices = content.choices(type)
                    if (choices.isNotEmpty()) {
                        MultiSelectDropdown("Show", choices, block.keys) { onChange(block.copy(keys = it)) }
                    }
                    if (block.keys == null) {
                        LabeledSlider(
                            label = "Max items",
                            valueText = block.limit.toString(),
                            value = block.limit.toFloat(),
                            range = 1f..20f,
                            steps = 18
                        ) { onChange(block.copy(limit = it.roundToInt())) }
                    }
                }
                type.flagLabel?.let { label ->
                    SwitchRow(label, block.flag) { onChange(block.copy(flag = it)) }
                }
                if (type == FlexBlockType.HEADER || type == FlexBlockType.TEXT) {
                    OutlinedTextField(
                        value = block.text,
                        onValueChange = { onChange(block.copy(text = it.take(300))) },
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
                            TextButton(onClick = { onChange(block.copy(keys = null)) }) { Text("Use default") }
                        }
                    }
                    // Crop: what shapes are allowed depends on the card size; the crop can be changed any time.
                    val aspect = spec.style.aspect
                    val options = FlexPhotoRatio.entries.filter { it.allowedOn(aspect) }
                    val current = options.firstOrNull { it != FlexPhotoRatio.ORIGINAL && abs(it.ratio - block.photoRatio) < 0.01f }
                        ?: FlexPhotoRatio.ORIGINAL
                    val real = options.filter { it != FlexPhotoRatio.ORIGINAL }
                    val lowest = real.minByOrNull { it.ratio }?.label
                    val highest = real.maxByOrNull { it.ratio }?.label
                    DropdownField(
                        label = "Crop shape" + if (lowest != null && highest != null) " (this size allows $lowest to $highest)" else "",
                        options = options,
                        selected = current,
                        optionLabel = { if (it == FlexPhotoRatio.ORIGINAL) "Original, within what fits" else it.label }
                    ) { onChange(block.copy(photoRatio = it.ratio)) }
                    LabeledSlider("Zoom", "${(block.photoZoom * 10).roundToInt() / 10f}×", block.photoZoom, 1f..3f) {
                        onChange(block.copy(photoZoom = it))
                    }
                    LabeledSlider("Move left / right", "${(block.photoX * 100).roundToInt()}%", block.photoX, 0f..1f) {
                        onChange(block.copy(photoX = it))
                    }
                    LabeledSlider("Move up / down", "${(block.photoY * 100).roundToInt()}%", block.photoY, 0f..1f) {
                        onChange(block.copy(photoY = it))
                    }
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remove block", color = MaterialTheme.colorScheme.error)
                }
              }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Look tab: colours, text, panels, card shape
// ---------------------------------------------------------------------------------------------

@Composable
private fun LookTab(
    spec: FlexSpec,
    hasDefaultPhoto: Boolean,
    bottom: Dp,
    style: ((FlexStyle) -> FlexStyle) -> Unit,
    onPickPhoto: () -> Unit,
    onReset: () -> Unit
) {
    val st = spec.style
    val app = LocalAzndStyle.current
    // The app theme's elements, for the Copy button of the fill dialogs.
    val themeSources = ThemeColor.entries.map { e ->
        val resolved = app.fill(e)
        FillCopySource(e.label, resolved.base.toArgb(), resolved.style)
    }
    var editBackground by remember { mutableStateOf(false) }
    var editPanels by remember { mutableStateOf(false) }

    if (editBackground) {
        FillEditorDialog(
            title = "Background",
            baseLabel = "Colour",
            baseColor = st.bgA,
            fill = st.backgroundFill(),
            onDismiss = { editBackground = false },
            onApply = { color, fill ->
                style { s ->
                    if (fill.base == FillBase.GRADIENT) {
                        val stops = fill.gradient.cleanStops()
                        s.copy(
                            bgMode = FlexBgMode.GRADIENT,
                            bgA = stops.first().color,
                            bgB = stops.last().color,
                            gradientAngle = fill.gradient.angle,
                            bgGradient = fill.gradient,
                            bgPatterns = fill.patterns
                        )
                    } else {
                        s.copy(bgMode = FlexBgMode.SOLID, bgA = color, bgGradient = null, bgPatterns = fill.patterns)
                    }
                }
                editBackground = false
            },
            copyFrom = themeSources
        )
    }
    if (editPanels) {
        FillEditorDialog(
            title = "Panels",
            baseLabel = "Panel colour",
            baseColor = st.panelColor,
            fill = st.panelFill,
            onDismiss = { editPanels = false },
            onApply = { color, fill ->
                style { it.copy(panelColor = color, panelFill = fill) }
                editPanels = false
            },
            copyFrom = themeSources
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            FlexSection("Starter looks") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        // The app's own theme: screen fill, card fill, borders, corners and colours carry over.
                        val bg = app.fill(ThemeColor.BACKGROUND)
                        LookChip("System theme", bg, app.button.toArgb()) { style { it.withAppTheme(app) } }
                    }
                    items(FlexPalettes.all) { p ->
                        val fill = ResolvedFill(
                            Color(p.bgA),
                            FillStyle.ofGradient(GradientSpec.of(GradientKind.LINEAR, 135, p.bgA, p.bgB))
                        )
                        LookChip(p.label, fill, p.accent) { style { it.withPalette(p) } }
                    }
                }
                Text("App theme presets", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(ThemePresets.all) { preset ->
                        val presetStyle = azndStyleOf(preset.settings, dark = true)
                        val look = FlexStyle().withAppTheme(presetStyle)
                        LookChip(preset.name, ResolvedFill(Color(look.bgA), look.backgroundFill()), look.accent) {
                            style { it.withAppTheme(presetStyle) }
                        }
                    }
                }
            }
        }
        backgroundItems(st, hasDefaultPhoto, style, onPickPhoto) { editBackground = true }
        item {
            FlexSection("Size") {
                DropdownField("Card size", FlexAspect.entries, st.aspect, { it.label }) { picked ->
                    style { it.copy(aspect = picked) }
                }
                Text(
                    if (st.aspect == FlexAspect.FREE) "Grows or shrinks with the content, so nothing is squeezed."
                    else "Fits up to ${st.aspect.maxBlocks} blocks.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            FlexSection("Colours") {
                ColorRow("Text", st.textColor) { c -> style { it.copy(textColor = c) } }
                ColorRow("Accent", st.accent) { c -> style { it.copy(accent = c) } }
                ColorRow("Panels", st.panelColor) { c -> style { it.copy(panelColor = c) } }
            }
        }
        item {
            FlexSection("Text") {
                DropdownField("Font", FlexFont.entries, st.font, { it.label }) { picked -> style { it.copy(font = picked) } }
                LabeledSlider("Size", "${(st.textScale * 100).roundToInt()}%", st.textScale, 0.7f..1.5f) { v ->
                    style { it.copy(textScale = v) }
                }
                Text("Align", style = MaterialTheme.typography.labelMedium)
                ChipChoice(FlexAlign.entries, st.align, { it.label }) { picked -> style { it.copy(align = picked) } }
                Text("Vertical position", style = MaterialTheme.typography.labelMedium)
                ChipChoice(FlexVAlign.entries, st.vAlign, { it.label }) { picked -> style { it.copy(vAlign = picked) } }
            }
        }
        item {
            FlexSection("Spacing") {
                LabeledSlider("Margins", "${st.padding}", st.padding.toFloat(), 24f..160f) { v ->
                    style { it.copy(padding = v.roundToInt()) }
                }
                LabeledSlider("Gap between blocks", "${st.spacing}", st.spacing.toFloat(), 8f..72f) { v ->
                    style { it.copy(spacing = v.roundToInt()) }
                }
            }
        }
        item {
            FlexSection("Panels") {
                ChipChoice(FlexPanel.entries, st.panel, { it.label }) { picked -> style { it.copy(panel = picked) } }
                if (st.panel == FlexPanel.FILLED) {
                    // The slider stops at 60% for the see-through look; a themed panel can be fully opaque.
                    LabeledSlider("Opacity", "${(st.panelAlpha * 100).roundToInt()}%", st.panelAlpha, 0.04f..1f) { v ->
                        style { it.copy(panelAlpha = v) }
                    }
                    Text(
                        "Fill: ${st.panelFill.summary()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { editPanels = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Panel gradient & pattern")
                    }
                }
                if (st.panel != FlexPanel.NONE) {
                    LabeledSlider(
                        "Border",
                        if (st.panelBorderWidth == 0) "default" else "${st.panelBorderWidth}",
                        st.panelBorderWidth.toFloat(), 0f..24f
                    ) { v -> style { it.copy(panelBorderWidth = v.roundToInt()) } }
                    if (st.panelBorderWidth > 0) {
                        ColorRow("Panel border colour", st.panelBorderColor) { c -> style { it.copy(panelBorderColor = c) } }
                    }
                }
                if (st.panel != FlexPanel.NONE) {
                    LabeledSlider("Corner radius", "${st.panelRadius}", st.panelRadius.toFloat(), 0f..80f) { v ->
                        style { it.copy(panelRadius = v.roundToInt()) }
                    }
                }
            }
        }
        item {
            FlexSection("Card") {
                LabeledSlider("Corner radius", "${st.cardRadius}", st.cardRadius.toFloat(), 0f..160f) { v ->
                    style { it.copy(cardRadius = v.roundToInt()) }
                }
                LabeledSlider("Border", if (st.borderWidth == 0) "off" else "${st.borderWidth}", st.borderWidth.toFloat(), 0f..48f) { v ->
                    style { it.copy(borderWidth = v.roundToInt()) }
                }
                if (st.borderWidth > 0) {
                    ColorRow("Border colour", st.borderColor) { c -> style { it.copy(borderColor = c) } }
                }
                SwitchRow("Watermark", st.watermark) { on -> style { it.copy(watermark = on) } }
                if (st.watermark) {
                    OutlinedTextField(
                        value = st.watermarkText,
                        onValueChange = { text -> style { it.copy(watermarkText = text.take(40)) } },
                        label = { Text("Watermark text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item {
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Reset to default") }
        }
    }
}

@Composable
private fun LookChip(label: String, fillOf: ResolvedFill, accent: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .azndFill(fillOf, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(accent))
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Background (part of the Look tab)
// ---------------------------------------------------------------------------------------------

/** The background sections of the Look tab: solid / gradient / photo and their settings. */
private fun LazyListScope.backgroundItems(
    st: FlexStyle,
    hasDefaultPhoto: Boolean,
    style: ((FlexStyle) -> FlexStyle) -> Unit,
    onPickPhoto: () -> Unit,
    onAdvanced: () -> Unit
) {
    item {
        FlexSection("Background") {
            ChipChoice(FlexBgMode.entries, st.bgMode, { it.label }) { picked -> style { it.copy(bgMode = picked) } }
        }
    }
    if (st.bgMode != FlexBgMode.PHOTO) {
        item {
            FlexSection(if (st.bgMode == FlexBgMode.GRADIENT) "Gradient" else "Colour") {
                val advancedGradient = st.bgMode == FlexBgMode.GRADIENT && st.bgGradient != null
                if (advancedGradient) {
                    Text(
                        "Advanced gradient: ${st.backgroundFill().summary()}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { style { it.copy(bgGradient = null) } }) { Text("Back to a simple two-colour gradient") }
                } else {
                    ColorRow(if (st.bgMode == FlexBgMode.GRADIENT) "Start colour" else "Colour", st.bgA) { c ->
                        style { it.copy(bgA = c) }
                    }
                    if (st.bgMode == FlexBgMode.GRADIENT) {
                        ColorRow("End colour", st.bgB) { c -> style { it.copy(bgB = c) } }
                        LabeledSlider("Angle", "${st.gradientAngle}°", st.gradientAngle.toFloat(), 0f..360f) { v ->
                            style { it.copy(gradientAngle = v.roundToInt()) }
                        }
                    }
                }
                val activePatterns = st.bgPatterns.filter { it.isActive }
                if (activePatterns.isNotEmpty()) {
                    Text(
                        if (activePatterns.size == 1) "Pattern: ${activePatterns[0].kind.label}"
                        else "Patterns: ${activePatterns.joinToString(", ") { it.kind.label }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onAdvanced, modifier = Modifier.fillMaxWidth()) {
                    Text("Multi-colour gradient & pattern")
                }
            }
        }
    } else {
        item {
            FlexSection("Photo") {
                val note = when {
                    st.photoUri != null -> "Using the photo you chose."
                    hasDefaultPhoto -> "Using the photo from this share. Choose another to replace it."
                    else -> "No photo yet. Choose one to use as the background."
                }
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onPickPhoto) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose photo")
                    }
                    if (st.photoUri != null) {
                        TextButton(onClick = { style { it.copy(photoUri = null) } }) { Text("Clear") }
                    }
                }
                LabeledSlider("Blur", "${(st.photoBlur * 100).roundToInt()}%", st.photoBlur, 0f..1f) { v ->
                    style { it.copy(photoBlur = v) }
                }
                LabeledSlider("Darken", "${(st.photoDim * 100).roundToInt()}%", st.photoDim, 0f..0.9f) { v ->
                    style { it.copy(photoDim = v) }
                }
            }
        }
    }
}
