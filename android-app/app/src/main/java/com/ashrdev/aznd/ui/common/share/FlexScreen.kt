@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ashrdev.aznd.ui.common.share

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shadow
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.material.icons.filled.Dashboard
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
import androidx.compose.runtime.key
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

/** Left to right: how the card looks, the card with its blocks, card presets. Opens on the middle one. */
private val FlexBarPages = listOf(
    BarPage("Look", Icons.Default.Palette),
    BarPage("Flex", Icons.Default.FitnessCenter),
    BarPage("Presets", Icons.Default.Dashboard)
)
private const val PAGE_FLEX = 1

/** Best fit waits this long after a change before it runs by itself, so a slider or typing settles first. */
private const val FIT_DELAY_MS = 600L

/** A block as fitting by itself sees it: without what the fits set themselves (filling the cell, photo crops). */
private fun fitBlockOf(b: FlexBlock): FlexBlock =
    b.copy(fillWidth = false, photoRatio = 0f, photoZoom = 1f, photoX = 0.5f, photoY = 0.5f, freeX = 0f, freeY = 0f, freeW = 0f, freeH = 0f)

/**
 * What fitting by itself looks at: what is on the card and how big it is drawn, not where tiles are, nor what the
 * fits set themselves (the order of the blocks, tiles filling their cells, photo crops, growth, free boxes) nor colours.
 * In flow mode the order counts: it is what the card is tiled from.
 */
private fun fitKeyOf(spec: FlexSpec): Any {
    val st = spec.style
    val blocks = spec.blocks.sortedBy { it.id }.map { fitBlockOf(it) }
    return listOf(
        blocks, st.aspect, st.moduleScale, st.textScale, st.font, st.padding, st.spacing, st.panel,
        st.watermark && st.watermarkText.isNotBlank(),
        // Flow mode: the order is what the tiling is made from (reordered in the list, it flows again).
        if (st.layoutMode == FlexLayoutMode.FLOW) spec.blocks.map { it.id } else emptyList()
    )
}

/**
 * Flex: the card maker behind every share button. Three swipeable pages, also tabs in the bottom bar:
 * Look (colours, text, background), Flex (the card, large, edited right on the card, with its list of blocks
 * under it; tap a block for its Edit button) and Presets (a placeholder for now). A block's edit page
 * replaces the screen while it is open. Share and Save sit in the top bar.
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
    /** The block selected on the Flex page (card and list share it), and the one whose edit page is open. */
    var selectedBlock by remember(preset) { mutableStateOf<Int?>(null) }
    var editingId by rememberSaveable(preset) { mutableStateOf<Int?>(null) }

    // Everything done to the card on this screen can be undone and redone (from the drop-down over the card).
    val history = remember(preset) { UndoStack<FlexSpec>() }
    // The card's content budget: a change that would take the card past what it can hold (a block added or turned on,
    // more items, a bigger size, a smaller card ...) is not made, and the person is told the card cannot fit any more.
    var full by remember(preset) { mutableStateOf(false) }
    /**
     * [join]: the change belongs to the undo step before it (best fit following the change that set it off). [force]:
     * not held to the content budget (fits, which never add content, and putting the preset's card back).
     */
    fun commit(next: FlexSpec, continuous: Boolean = false, join: Boolean = false, force: Boolean = false) {
        val before = spec
        val ct = content
        if (!force && before != null && ct != null && fitKeyOf(before) != fitKeyOf(next) &&
            FlexPainter.refuses(before, next, ct, assets)
        ) {
            full = true
            return
        }
        if (before != null && before != next && !(join && history.canUndo)) history.push(before, continuous)
        spec = next
    }
    /** [continuous]: changes that follow each other closely (a slider, a dragged handle) are one undo step. */
    fun update(continuous: Boolean = false, transform: (FlexSpec) -> FlexSpec) {
        spec?.let { commit(transform(it), continuous) }
    }
    fun updateStyle(transform: (FlexStyle) -> FlexStyle) = update(continuous = true) { it.copy(style = transform(it.style)) }

    // The card fits itself when what is on it changes: blocks added, removed, edited or resized, the card size, the
    // module or text size, the spacing. Not when tiles are only moved, or colours change. How depends on the mode:
    // tiles get the nearest fit, flow its tiling for the order it has, free boxes only a box for a new block.
    var fittedKey by remember(preset) { mutableStateOf<Any?>(null) }
    // The blocks as they were at the last fit: the ones that differ now are what nearest fit may move.
    var fittedBlocks by remember(preset) { mutableStateOf<Map<Int, FlexBlock>>(emptyMap()) }
    fun markFitted(sp: FlexSpec?) {
        if (sp == null) return
        fittedKey = fitKeyOf(sp)
        fittedBlocks = sp.blocks.associate { it.id to fitBlockOf(it) }
    }
    fun undo() {
        val now = spec ?: return
        history.undo(now)?.let {
            spec = it
            markFitted(it)   // an undone card is not fitted again
        }
    }
    fun redo() {
        val now = spec ?: return
        history.redo(now)?.let {
            spec = it
            markFitted(it)
        }
    }

    var aligning by remember { mutableStateOf(false) }
    var alignAgain by remember { mutableStateOf(false) }
    // The blur over the preview while a tiling change is worked out (and at least half a second when it is quicker).
    val veil = remember(preset) { FlexVeil() }
    // The tilings the last best fit found, best first: "Another layout" steps through them.
    var layouts by remember(preset) { mutableStateOf<List<FlexSpec>>(emptyList()) }
    /**
     * Fits the card, off the main thread, and applies the result. [nearest]: the nearest fit (the closest stable
     * layout: small edits of the tiling the card has, FlexPainter.nearestFit), else best fit (the best tiling there is,
     * which can rearrange everything). [quiet]: no message (running by itself). [join]: one undo step with the change
     * before it. [then]: runs with the result once applied.
     */
    fun autoAlign(quiet: Boolean = false, join: Boolean = false, nearest: Boolean = false, then: ((FlexAutoResult) -> Unit)? = null) {
        val sp = spec ?: return
        val ct = content ?: return
        if (aligning) {
            // A change came in while a fit was busy: once it is done, the nearest fit runs again on the card as it is then.
            if (quiet) alignAgain = true
            return
        }
        aligning = true
        val snapshot = assets
        // New blocks, and blocks changed since the last fit: the ones nearest fit may move.
        val focus = sp.blocks.filter { fittedBlocks[it.id] != fitBlockOf(it) }.map { it.id }.toSet()
        scope.launch {
            val run = veil.Run(this)
            val result = try {
                withContext(Dispatchers.Default) {
                    if (nearest) FlexPainter.nearestFit(sp, ct, snapshot, focus) else FlexPainter.autoAlign(sp, ct, snapshot)
                }
            } catch (e: Exception) {
                null
            } finally {
                aligning = false
            }
            // Only if the card is still the one that was aligned; the new tiling goes in under the veil.
            run.finish(if (result != null && result.changed && spec == sp) ({ if (spec == sp) commit(result.spec, join = join, force = true) }) else null)
            if (result == null) {
                if (!quiet) Toast.makeText(context, "Couldn't align the card", Toast.LENGTH_SHORT).show()
            } else if (spec == sp || spec == result.spec) {
                markFitted(spec)
                if (!nearest) layouts = listOf(result.spec) + result.alternatives
                if (!quiet) Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                then?.invoke(result)
            }
            if (alignAgain) {
                alignAgain = false
                autoAlign(quiet = true, join = true, nearest = true)
            }
        }
    }

    /**
     * After a hand change of the tiling ([placed]: the block dropped, or whose split was turned or flipped): the card
     * as it ends up (FlexPainter.placedByHand: the nearest fit round what was done), worked out off the main thread and applied as part of the same undo step, if the card has not changed again.
     */
    fun tuneLayout(placed: Int?, before: FlexSpec?) {
        val sp = spec ?: return
        val ct = content ?: return
        val snapshot = assets
        scope.launch {
            val run = veil.Run(this)
            val done = withContext(Dispatchers.Default) { runCatching { FlexPainter.placedByHand(sp, ct, snapshot, placed, before) }.getOrNull() }
            run.finish(if (done != null && done != sp && spec == sp) ({ if (spec == sp) commit(done, join = true, force = true) }) else null)
        }
    }

    /** The fit that runs by itself, by the card's mode (see above). [join]: part of the undo step of the change before it. */
    fun fitByItself(join: Boolean) {
        val sp = spec ?: return
        val ct = content ?: return
        when (sp.style.layoutMode) {
            FlexLayoutMode.SPLIT, FlexLayoutMode.FLOW -> autoAlign(quiet = true, join = join, nearest = true)
            FlexLayoutMode.FREE -> {
                val placed = FlexPainter.placeBoxes(sp, ct, assets)
                if (placed != sp) commit(placed, join = join, force = true)
                markFitted(placed)
            }
        }
    }

    /**
     * Switches the card to [mode]: free boxes start where the tiles are, flow takes the tiling for the order the card
     * reads in, tiles get best fit. One undo step; worked out off the main thread.
     */
    fun setMode(mode: FlexLayoutMode) {
        val sp = spec ?: return
        val ct = content ?: return
        if (sp.style.layoutMode == mode) return
        val snapshot = assets
        scope.launch {
            val run = veil.Run(this)
            val next = withContext(Dispatchers.Default) {
                runCatching { FlexPainter.placeBoxes(FlexPainter.withMode(sp, ct, snapshot, mode), ct, snapshot) }.getOrNull()
            }
            if (next == null || spec != sp) {
                run.finish(null)
                return@launch
            }
            // Tiles go on to best fit: the veil stays up through it.
            run.finish {
                commit(next, force = true)
                markFitted(next)
                selectedBlock = null
                if (mode == FlexLayoutMode.SPLIT) autoAlign(quiet = true, join = true)
            }
        }
    }

    /** Flow mode: the card tiled in [order] (a block moved one place with the buttons), off the main thread, one undo step. */
    fun flowTo(order: List<Int>) {
        val sp = spec ?: return
        val ct = content ?: return
        val snapshot = assets
        scope.launch {
            val run = veil.Run(this)
            val next = withContext(Dispatchers.Default) { runCatching { FlexPainter.flowOrder(sp, ct, snapshot, order) }.getOrNull() }
            run.finish(if (next != null && spec == sp && next != sp) ({ if (spec == sp) commit(next) }) else null)
        }
    }

    /** Another good tiling of the same blocks: the next one best fit found, after the best (worked out first if need be). */
    fun anotherLayout() {
        val now = spec ?: return
        val at = layouts.indexOf(now)
        if (at >= 0 && layouts.size > 1) {
            val next = layouts[(at + 1) % layouts.size]
            scope.launch { veil.Run(this).finish { if (spec == now) commit(next, force = true) } }
            return
        }
        autoAlign(quiet = true) { result ->
            if (!result.changed) {
                if (result.alternatives.isEmpty()) Toast.makeText(context, "No other layout fits these blocks as well", Toast.LENGTH_SHORT).show()
                else {
                    val next = result.alternatives[0]
                    val base = spec
                    scope.launch { veil.Run(this).finish { if (spec == base) commit(next, force = true) } }
                }
            }
        }
    }

    // A share opens fitted to what it is showing this time, once its pictures are in (their sizes count): a card with
    // a layout of its own gets the nearest fit (its arrangement stays, new or missing blocks are fitted in), a card
    // without one gets best fit; a flow card its tiling for its order; free boxes stay as they are. The card as it was
    // left is one Undo away.
    var alignOnOpen by remember(preset) { mutableStateOf(true) }
    LaunchedEffect(spec != null, content, assets) {
        if (!alignOnOpen || spec == null || content == null || !assets.ready) return@LaunchedEffect
        alignOnOpen = false
        val sp = spec
        // Blocks the saved layout does not have yet count as changed.
        fittedKey = sp?.let { fitKeyOf(it) }
        val laid = FlexTree.leaves(sp?.layout).toSet()
        fittedBlocks = sp?.blocks?.filter { it.id in laid }?.associate { it.id to fitBlockOf(it) } ?: emptyMap()
        when (sp?.style?.layoutMode) {
            FlexLayoutMode.SPLIT -> autoAlign(quiet = true, nearest = sp.layout != null)
            FlexLayoutMode.FLOW, FlexLayoutMode.FREE -> fitByItself(join = false)
            null -> {}
        }
    }
    // Any change to what is on the card: the nearest fit, by itself, as part of the change's undo step.
    val fitKey = spec?.let { fitKeyOf(it) }
    LaunchedEffect(fitKey, assets.ready) {
        val key = fitKey ?: return@LaunchedEffect
        if (alignOnOpen || !assets.ready || content == null || key == fittedKey) return@LaunchedEffect
        // Wait for the change to settle (a slider being dragged, typing).
        delay(FIT_DELAY_MS)
        fittedKey = key
        fitByItself(join = true)
    }

    val picker = rememberImagePicker(aspect = null, outputWidth = 1600, outputHeight = 1600) { uri ->
        updateStyle { it.copy(photoUri = uri.toString(), bgMode = FlexBgMode.PHOTO) }
    }

    val pagerState = rememberPagerState(initialPage = PAGE_FLEX, pageCount = { FlexBarPages.size })
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

    val editSpec = spec
    val editContent = content
    val editBlock = editingId?.takeIf { id -> editSpec?.blocks?.any { it.id == id } == true }
    if (editBlock != null && editSpec != null && editContent != null) {
        // A block's own edit page: it works on a copy and hands it back on Done.
        key(editBlock) {
            FlexEditPage(
                blockId = editBlock,
                initial = editSpec,
                content = editContent,
                onDone = { edited ->
                    commit(edited)
                    if (edited.blocks.none { it.id == editBlock }) selectedBlock = null
                    editingId = null
                },
                onFinish = { editingId = null },
                onHome = onHome,
                onOpenSettings = onOpenSettings
            )
        }
    } else {
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
                            0 -> EditorPage(sp, ct, assets, pl, previewHeight, veil.up) {
                                LookTab(
                                    spec = sp,
                                    hasDefaultPhoto = ct.photoUri != null,
                                    bottom = bottom,
                                    style = { updateStyle(it) },
                                    onMode = { setMode(it) },
                                    onPickPhoto = {
                                        picker.launch()
                                    },
                                    onReset = {
                                        defaultSpec?.let { commit(it, force = true) }
                                        store.reset(preset)
                                    }
                                )
                            }
                            PAGE_FLEX -> FlexViewPage(
                                spec = sp,
                                content = ct,
                                assets = assets,
                                plan = pl,
                                bottom = bottom,
                                // A tile moved, or a split turned or flipped, by hand: photos re-crop to fill their new cells at
                                // once; then, on a fixed size, the cells get the sizes best fit would give that tiling.
                                onLayout = { tree, placed ->
                                    val before = spec
                                    update { FlexPainter.settle(it.copy(layout = tree), ct, assets) }
                                    tuneLayout(placed, before)
                                },
                                onCard = { card -> commit(card) },
                                selectedId = selectedBlock,
                                onSelect = { selectedBlock = it },
                                onEdit = { id ->
                                    selectedBlock = id
                                    editingId = id
                                },
                                update = { transform -> update(transform = transform) },
                                canUndo = history.canUndo,
                                canRedo = history.canRedo,
                                onUndo = { undo() },
                                onRedo = { redo() },
                                onAutoAlign = { autoAlign() },
                                onNearestFit = { autoAlign(nearest = true) },
                                onAnotherLayout = { anotherLayout() },
                                onResizeLayout = { tree -> update(continuous = true) { FlexPainter.settle(it.copy(layout = tree), ct, assets) } },
                                onFreeBox = { id, x, y, w, h, continuous ->
                                    update(continuous = continuous) { sp ->
                                        sp.copy(blocks = sp.blocks.map { if (it.id == id) it.copy(freeX = x, freeY = y, freeW = w, freeH = h) else it })
                                    }
                                },
                                // The list order is the stacking order: the last block is on top.
                                onLayer = { id, front ->
                                    update { sp ->
                                        val b = sp.blocks.first { it.id == id }
                                        val rest = sp.blocks.filter { it.id != id }
                                        sp.copy(blocks = if (front) rest + b else listOf(b) + rest)
                                    }
                                },
                                onFlowOrder = { order -> flowTo(order) },
                                onMode = { setMode(it) },
                                aligning = aligning,
                                veiled = veil.up
                            )
                            else -> FlexPresetsPage(bottom = bottom)
                        }
                    }
                }
            }
        }
    }
    if (full) FlexFullDialog { full = false }
}

/** A page with the live card on top and one tab's controls below it. A warning appears under it when the card is too full. */
@Composable
private fun EditorPage(
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    plan: FlexPlan,
    previewHeight: Dp,
    veiled: Boolean,
    controls: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        FlexPreview(
            spec = spec,
            content = content,
            assets = assets,
            plan = plan,
            boxHeight = previewHeight,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            veiled = veiled
        )
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
    poses: (() -> FlexPoses?)? = null,
    /**
     * The tiling is being worked out (see [FlexVeil]): the card blurs (and dims, which older Android versions without
     * blur still show), "aznding" pulses over it, and it snaps back sharp when the new tiling is in.
     */
    veiled: Boolean = false,
    /**
     * Something is being resized by hand: the card takes its new shape at once, as the tiles do (an eased shape would
     * trail behind the content and the finger). Otherwise it eases into it with the tiles' glide (see FlexViewPage).
     */
    instantShape: Boolean = false
) {
    // The height is what changes (Free size follows its content), so the height is eased, on the tiles' timing: the
    // card's edge then moves in step with the tiles gliding inside it (easing the width / height ratio would not).
    val tall by animateFloatAsState(
        targetValue = plan.height / plan.width,
        animationSpec = if (instantShape) snap() else tween(FLEX_GLIDE_MS),
        label = "flexTall"
    )
    val ratio = 1f / tall
    // Into the blur smoothly, out of it at a snap.
    val veil by animateFloatAsState(
        targetValue = if (veiled) 1f else 0f,
        animationSpec = tween(if (veiled) VEIL_IN_MS.toInt() else VEIL_OUT_MS),
        label = "flexVeil"
    )
    BoxWithConstraints(
        modifier = modifier.height(boxHeight),
        contentAlignment = Alignment.Center
    ) {
        val width = minOf(maxWidth, boxHeight * ratio)
        Box(
            modifier = Modifier
                .width(width)
                .aspectRatio(ratio),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (veil > 0.01f) Modifier.blur(VEIL_BLUR * veil) else Modifier)
                    .then(canvasModifier)
            ) {
                val k = size.width / plan.width
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.clipRect(0f, 0f, size.width, size.height)
                    FlexPainter.draw(native, size.width, spec, content, assets, plan, poses?.invoke())
                    native.restore()
                }
                overlay?.invoke(this, k)
                if (veil > 0.01f) drawRect(Color.Black.copy(alpha = 0.28f * veil))
            }
            if (veil > 0.01f) AzndingLabel(Modifier.alpha(veil))
        }
    }
}

/** "aznding": what the card says while its tiling is being worked out, pulsing gently. */
@Composable
private fun AzndingLabel(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "aznding")
    val glow by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "azndingGlow"
    )
    Text(
        "aznding",
        style = MaterialTheme.typography.titleLarge.copy(
            color = Color.White,
            letterSpacing = 1.5.sp,
            shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 12f)
        ),
        modifier = modifier.alpha(glow)
    )
}

/**
 * The veil over the card preview while a tiling change is worked out (a fit, a mode switch, a flow reorder): up while
 * any job holds it. A job that is quick never raises it until its change is ready; then it comes up, the change is
 * made under it, and it stays up at least [VEIL_MIN_MS] in all. A job that takes long raises it after [VEIL_SLOW_MS].
 */
@Stable
internal class FlexVeil {
    var up by mutableStateOf(false)
        private set
    private var holders = 0

    fun raise(): Long {
        holders++
        up = true
        return System.currentTimeMillis()
    }

    /** Lowers the veil raised at [since], once it has been up [VEIL_MIN_MS]. */
    suspend fun lower(since: Long) {
        val left = VEIL_MIN_MS - (System.currentTimeMillis() - since)
        try {
            if (left > 0) delay(left)
        } finally {
            holders = (holders - 1).coerceAtLeast(0)
            up = holders > 0
        }
    }

    /** One tiling job: started with the job, [finish]ed with its change (or none). Its coroutine scope is [scope]. */
    inner class Run(scope: CoroutineScope) {
        private var since = -1L
        private val early = scope.launch {
            delay(VEIL_SLOW_MS)
            since = raise()
        }

        /** The job is done. [change], if any, is made under the veil (brought up first, if it is not up yet). */
        suspend fun finish(change: (() -> Unit)?) {
            early.cancel()
            if (change != null) {
                if (since < 0) {
                    since = raise()
                    delay(VEIL_IN_MS)
                }
                change()
            }
            if (since >= 0) {
                val s = since
                since = -1
                lower(s)
            }
        }
    }
}

/** How long tiles glide to new places, and the card's shape with them. */
internal const val FLEX_GLIDE_MS = 260

/** The veil: how long it takes to come in, the least it stays up, how quickly it snaps away, and how blurred. */
private const val VEIL_IN_MS = 140L
private const val VEIL_MIN_MS = 500L
private const val VEIL_SLOW_MS = 120L
private const val VEIL_OUT_MS = 110
private val VEIL_BLUR = 14.dp

// ---------------------------------------------------------------------------------------------
// Look tab: colours, text, panels, card shape
// ---------------------------------------------------------------------------------------------

@Composable
private fun LookTab(
    spec: FlexSpec,
    hasDefaultPhoto: Boolean,
    bottom: Dp,
    style: ((FlexStyle) -> FlexStyle) -> Unit,
    /** Switches the card's layout mode (tiles, flow, free): it rearranges the card, so it is not a plain style change. */
    onMode: (FlexLayoutMode) -> Unit,
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
            FlexFoldSection("Starter looks", "Palettes and app themes") {
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
            val base = (st.moduleScale * 100).roundToInt()
            val shown = (st.moduleSize * 100).roundToInt()
            FlexFoldSection("Layout", "${st.layoutMode.label} · ${st.aspect.label} · modules $base%", initiallyOpen = true) {
                Text("Layout mode", style = MaterialTheme.typography.labelMedium)
                ChipChoice(FlexLayoutMode.entries, st.layoutMode, { it.label }) { picked -> onMode(picked) }
                Text(
                    st.layoutMode.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DropdownField("Card size", FlexAspect.entries, st.aspect, { it.label }) { picked ->
                    style { it.copy(aspect = picked) }
                }
                Text(
                    when {
                        st.aspect == FlexAspect.FREE -> "Grows or shrinks with the content, so nothing is squeezed."
                        st.layoutMode == FlexLayoutMode.FREE -> "Blocks off the card's edges are cut off."
                        else -> "Holds as much as fits without squeezing; the card tells you when it is full."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LabeledSlider(
                    "Module size",
                    if (shown != base) "$base% · shown at $shown%" else "$base%",
                    st.moduleScale,
                    FLEX_MODULE_MIN..FLEX_MODULE_MAX
                ) { v -> style { it.copy(moduleScale = (v * 20f).roundToInt() / 20f) } }
                Text(
                    "How big every block is drawn. Smaller fits more blocks before any has to shrink.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            FlexFoldSection("Colours", "Text, accent and panels") {
                ColorRow("Text", st.textColor) { c -> style { it.copy(textColor = c) } }
                ColorRow("Accent", st.accent) { c -> style { it.copy(accent = c) } }
                ColorRow("Panels", st.panelColor) { c -> style { it.copy(panelColor = c) } }
            }
        }
        item {
            FlexFoldSection("Text", "${st.font.label} · ${(st.textScale * 100).roundToInt()}%") {
                DropdownField("Font", FlexFont.entries, st.font, { it.label }) { picked -> style { it.copy(font = picked) } }
                LabeledSlider("Size", "${(st.textScale * 100).roundToInt()}%", st.textScale, 0.7f..1.5f) { v ->
                    style { it.copy(textScale = v) }
                }
            }
        }
        item {
            FlexFoldSection("Spacing", "Margins ${st.padding} · gap ${st.spacing}") {
                LabeledSlider("Margins", "${st.padding}", st.padding.toFloat(), 24f..160f) { v ->
                    style { it.copy(padding = v.roundToInt()) }
                }
                LabeledSlider("Gap between tiles", "${st.spacing}", st.spacing.toFloat(), 8f..72f) { v ->
                    style { it.copy(spacing = v.roundToInt()) }
                }
            }
        }
        item {
            FlexFoldSection("Panels", st.panel.label) {
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
            FlexFoldSection("Card", "Corners ${st.cardRadius} · watermark ${if (st.watermark) st.watermarkAlign.label.lowercase() else "off"}") {
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
                    Text("Watermark position", style = MaterialTheme.typography.labelMedium)
                    ChipChoice(FlexAlign.entries, st.watermarkAlign, { it.label }) { picked -> style { it.copy(watermarkAlign = picked) } }
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

/** The background section of the Look tab: solid / gradient / photo and the settings of the one picked. */
private fun LazyListScope.backgroundItems(
    st: FlexStyle,
    hasDefaultPhoto: Boolean,
    style: ((FlexStyle) -> FlexStyle) -> Unit,
    onPickPhoto: () -> Unit,
    onAdvanced: () -> Unit
) {
    item {
        FlexFoldSection("Background", st.bgMode.label) {
            ChipChoice(FlexBgMode.entries, st.bgMode, { it.label }) { picked -> style { it.copy(bgMode = picked) } }
            if (st.bgMode != FlexBgMode.PHOTO) {
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
            } else {
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
