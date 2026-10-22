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

/**
 * What best fit by itself looks at: what is on the card and how big it is drawn, not where tiles are, nor what best
 * fit sets itself (the order of the blocks, tiles filling their cells, photo crops, its growth) nor colours.
 */
private fun fitKeyOf(spec: FlexSpec): Any {
    val st = spec.style
    val blocks = spec.blocks.sortedBy { it.id }.map { it.copy(fillWidth = false, photoRatio = 0f, photoZoom = 1f, photoX = 0.5f, photoY = 0.5f) }
    return listOf(
        blocks, st.aspect, st.moduleScale, st.textScale, st.font, st.padding, st.spacing, st.panel,
        st.watermark && st.watermarkText.isNotBlank(), st.autoFit, st.growToFill
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
    /** [join]: the change belongs to the undo step before it (best fit following the change that set it off). */
    fun commit(next: FlexSpec, continuous: Boolean = false, join: Boolean = false) {
        val before = spec
        if (before != null && before != next && !(join && history.canUndo)) history.push(before, continuous)
        spec = next
    }
    /** [continuous]: changes that follow each other closely (a slider, a dragged handle) are one undo step. */
    fun update(continuous: Boolean = false, transform: (FlexSpec) -> FlexSpec) {
        spec?.let { commit(transform(it), continuous) }
    }
    fun updateStyle(transform: (FlexStyle) -> FlexStyle) = update(continuous = true) { it.copy(style = transform(it.style)) }

    // Best fit runs by itself (FlexStyle.autoFit) when what is on the card changes: blocks added, removed, edited or
    // resized, the card size, the module or text size, the spacing. Not when tiles are only moved, or colours change.
    var fittedKey by remember(preset) { mutableStateOf<Any?>(null) }
    fun undo() {
        val now = spec ?: return
        history.undo(now)?.let {
            spec = it
            fittedKey = fitKeyOf(it)   // an undone card is not fitted again
        }
    }
    fun redo() {
        val now = spec ?: return
        history.redo(now)?.let {
            spec = it
            fittedKey = fitKeyOf(it)
        }
    }

    var aligning by remember { mutableStateOf(false) }
    var alignAgain by remember { mutableStateOf(false) }
    // The tilings the last best fit found, best first: "Another layout" steps through them.
    var layouts by remember(preset) { mutableStateOf<List<FlexSpec>>(emptyList()) }
    /**
     * Finds the tiling that fits the blocks best, off the main thread, and applies it. [quiet]: no message (best fit
     * running by itself). [join]: one undo step with the change before it. [then]: runs with the result once applied.
     */
    fun autoAlign(quiet: Boolean = false, join: Boolean = false, then: ((FlexAutoResult) -> Unit)? = null) {
        val sp = spec ?: return
        val ct = content ?: return
        if (aligning) {
            // A change came in while best fit was busy: once it is done, it runs again on the card as it is then.
            if (quiet) alignAgain = true
            return
        }
        aligning = true
        val snapshot = assets
        scope.launch {
            val result = try {
                withContext(Dispatchers.Default) { FlexPainter.autoAlign(sp, ct, snapshot) }
            } catch (e: Exception) {
                null
            } finally {
                aligning = false
            }
            if (result == null) {
                if (!quiet) Toast.makeText(context, "Couldn't align the card", Toast.LENGTH_SHORT).show()
            } else if (spec == sp) {
                // Only if the card is still the one that was aligned.
                if (result.changed) commit(result.spec, join = join)
                layouts = listOf(result.spec) + result.alternatives
                if (!quiet) Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                then?.invoke(result)
            }
            if (alignAgain) {
                alignAgain = false
                fittedKey = spec?.let { fitKeyOf(it) }
                autoAlign(quiet = true, join = true)
            }
        }
    }

    /**
     * After a hand change of the tiling: best fit's sizes for the tiling as made (FlexPainter.tune), worked out off the
     * main thread and applied as part of the same undo step, if the card has not changed again meanwhile.
     */
    fun tuneLayout() {
        val sp = spec ?: return
        val ct = content ?: return
        val snapshot = assets
        scope.launch {
            val tuned = withContext(Dispatchers.Default) { runCatching { FlexPainter.tune(sp, ct, snapshot) }.getOrNull() }
            if (tuned != null && spec == sp) commit(tuned, join = true)
        }
    }

    /** Another good tiling of the same blocks: the next one best fit found, after the best (worked out first if need be). */
    fun anotherLayout() {
        val now = spec ?: return
        val at = layouts.indexOf(now)
        if (at >= 0 && layouts.size > 1) {
            commit(layouts[(at + 1) % layouts.size])
            return
        }
        autoAlign(quiet = true) { result ->
            if (!result.changed) {
                if (result.alternatives.isEmpty()) Toast.makeText(context, "No other layout fits these blocks as well", Toast.LENGTH_SHORT).show()
                else commit(result.alternatives[0])
            }
        }
    }

    // A share opens auto aligned to what it is showing this time, once its pictures are in (their sizes count).
    // The card as it was left is one Undo away. Not when best fit is off for this card: its layout is kept.
    var alignOnOpen by remember(preset) { mutableStateOf(true) }
    LaunchedEffect(spec != null, content, assets) {
        if (!alignOnOpen || spec == null || content == null || !assets.ready) return@LaunchedEffect
        alignOnOpen = false
        fittedKey = spec?.let { fitKeyOf(it) }
        if (spec?.style?.autoFit == true) autoAlign(quiet = true)
    }
    val fitKey = spec?.let { fitKeyOf(it) }
    LaunchedEffect(fitKey, assets.ready) {
        val key = fitKey ?: return@LaunchedEffect
        if (alignOnOpen || !assets.ready || content == null || key == fittedKey) return@LaunchedEffect
        // Wait for the change to settle (a slider being dragged, typing).
        delay(FIT_DELAY_MS)
        fittedKey = key
        if (spec?.style?.autoFit == true) autoAlign(quiet = true, join = true)
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
                            0 -> EditorPage(sp, ct, assets, pl, previewHeight) {
                                LookTab(
                                    spec = sp,
                                    hasDefaultPhoto = ct.photoUri != null,
                                    bottom = bottom,
                                    style = { updateStyle(it) },
                                    onPickPhoto = {
                                        picker.launch()
                                    },
                                    onReset = {
                                        defaultSpec?.let { commit(it) }
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
                                onLayout = { tree ->
                                    update { FlexPainter.settle(it.copy(layout = tree), ct, assets) }
                                    tuneLayout()
                                },
                                onSizeBlock = { id, size ->
                                    update(continuous = true) { sp -> sp.copy(blocks = sp.blocks.map { if (it.id == id) it.copy(size = size) else it }) }
                                },
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
                                onAnotherLayout = { anotherLayout() },
                                onResizeLayout = { tree -> update(continuous = true) { FlexPainter.settle(it.copy(layout = tree), ct, assets) } },
                                aligning = aligning
                            )
                            else -> FlexPresetsPage(bottom = bottom)
                        }
                    }
                }
            }
        }
    }
}

/** A page with the live card on top and one tab's controls below it. A warning appears under it when the card is too full. */
@Composable
private fun EditorPage(
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    plan: FlexPlan,
    previewHeight: Dp,
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
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
    poses: (() -> FlexPoses?)? = null
) {
    val ratio by animateFloatAsState(
        targetValue = plan.width / plan.height,
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
            val k = size.width / plan.width
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
            FlexFoldSection("Layout", "${st.aspect.label} · modules $base%", initiallyOpen = true) {
                DropdownField("Card size", FlexAspect.entries, st.aspect, { it.label }) { picked ->
                    style { it.copy(aspect = picked) }
                }
                Text(
                    if (st.aspect == FlexAspect.FREE) "Grows or shrinks with the content, so nothing is squeezed."
                    else "Fits up to ${st.aspect.maxBlocks} blocks, each in a tile that fills the card.",
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
                SwitchRow("Best fit by itself", st.autoFit) { on -> style { it.copy(autoFit = on) } }
                Text(
                    if (st.autoFit) "Re-tiles the card when it opens and whenever blocks are added, removed, edited or resized."
                    else "Your layout stays as you leave it. Best fit runs only from the card's menu.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (st.aspect != FlexAspect.FREE) {
                    SwitchRow("Grow modules to fill", st.growToFill) { on ->
                        style { it.copy(growToFill = on, fitBoost = if (on) it.fitBoost else 1f) }
                    }
                    Text(
                        "When there is room to spare, best fit draws the modules bigger to fill the card.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
