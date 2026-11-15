@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ashrdev.aznd.ui.common.share

import com.ashrdev.aznd.ui.common.TaperLineControls
import com.ashrdev.aznd.ui.theme.BorderKind
import android.net.Uri
import androidx.activity.compose.BackHandler
import com.ashrdev.aznd.ui.components.AlertDialog
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
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.FillCopySource
import com.ashrdev.aznd.ui.common.FillEditor
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


/** Left to right: how the card looks, the card with its blocks, adding a block. Opens on the middle one. */
private val FlexEditorPages = listOf(
    BarPage("Look", Icons.Default.Palette),
    BarPage("Card", Icons.Default.Dashboard),
    BarPage("Add", Icons.Default.AddBox)
)
private const val PAGE_CARD = 1

private const val FIT_DELAY_MS = 600L

/**
 * The block as far as the layout fit cares: zoom, move and the box in free mode do not change a tile's shape, so they
 * are left out. A free crop does change it (the tile takes the crop's shape), so its crop counts while the block is in
 * free crop mode and is left out (reset to the whole photo) in every other mode, where it has no effect.
 */
private fun fitBlockOf(b: FlexBlock): FlexBlock {
    val plain = b.copy(photoZoom = 1f, photoX = 0.5f, photoY = 0.5f, freeX = 0f, freeY = 0f, freeW = 0f, freeH = 0f)
    return if (b.photoRatio == FLEX_PHOTO_FREE) plain else plain.copy(cropL = 0f, cropT = 0f, cropR = 1f, cropB = 1f)
}

private fun fitKeyOf(spec: FlexSpec): Any {
    val st = spec.style
    val blocks = spec.blocks.sortedBy { it.id }.map { fitBlockOf(it) }
    return listOf(
        blocks, st.aspect, st.moduleScale, st.textScale, st.font, st.padding, st.paddingV, st.spacing, st.panel,
        st.watermark && st.watermarkText.isNotBlank()
    )
}

/**
 * The preset editor: the card maker for one [FlexCardPreset]. Three swipeable pages, also tabs in the bottom bar:
 * Look (colours, text, background), Card (the card, large, edited right on the card, with its list of blocks under it;
 * tap a block for its Edit button) and Add (tiles to add a block). It works on sample content, so every block shows
 * something. A block's edit page replaces the screen while it is open. Save sits in the top bar and stores the preset
 * (a new one asks for its name first); leaving with unsaved changes asks first.
 * [initial] is null for a new preset (it starts from the blank card), else the preset to edit; one with an id of 0 or
 * less is not stored yet.
 */
@Composable
internal fun FlexPresetEditor(
    initial: FlexCardPreset?,
    onSaved: (FlexCardPreset) -> Unit,
    onClose: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presetStore = remember { FlexPresetStore(context) }

    val content by produceState<FlexContent?>(null) {
        value = FlexSampleContent.load(context)
    }

    var spec by remember { mutableStateOf<FlexSpec?>(null) }
    /** The card the editor started from: what "reset" goes back to and what "unsaved changes" is measured against. */
    var startSpec by remember { mutableStateOf<FlexSpec?>(null) }
    LaunchedEffect(content) {
        val c = content ?: return@LaunchedEffect
        if (spec == null) {
            val first = initial?.spec?.reconciled() ?: FlexSampleContent.blankSpec(c)
            startSpec = first
            spec = first
        }
    }
    val startName = initial?.name.orEmpty()
    var name by remember { mutableStateOf(startName) }
    /** True from opening until the card's first automatic fit is done: that fit is part of the starting card, not an edit. */
    var settling by remember { mutableStateOf(true) }

    val assets = rememberFlexAssets(spec, content)
    // The layout of the card as it is right now: its height, shrink, block positions, what is hidden or cut off.
    val plan = remember(spec, content, assets) {
        val sp = spec
        val ct = content
        if (sp != null && ct != null) FlexPainter.plan(sp, ct, assets) else null
    }
    /** The block selected on the Flex page (card and list share it), and the one whose edit page is open. */
    var selectedBlock by remember { mutableStateOf<Int?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) }

    // Everything done to the card on this screen can be undone and redone (from the drop-down over the card).
    val history = remember { UndoStack<FlexSpec>() }
    // The card's content budget: a change that would take the card past what it can hold (a block added or turned on,
    // more items, a bigger size, a smaller card ...) is not made, and the person is told the card cannot fit any more.
    var full by remember { mutableStateOf(false) }
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
        if (settling && force) {
            startSpec = next
            spec = next
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

    var fittedKey by remember { mutableStateOf<Any?>(null) }
    fun markFitted(sp: FlexSpec?) {
        if (sp != null) fittedKey = fitKeyOf(sp)
    }
    fun undo() {
        val now = spec ?: return
        history.undo(now)?.let {
            spec = it
            markFitted(it)
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
    var fitAgain by remember { mutableStateOf(false) }
    val veil = remember { FlexVeil() }

    fun rescue(join: Boolean) {
        val sp = spec ?: return
        val ct = content ?: return
        if (sp.style.layoutMode != FlexLayoutMode.SPLIT) return
        if (aligning) {
            fitAgain = true
            return
        }
        aligning = true
        val snapshot = assets
        scope.launch {
            val run = veil.Run(this)
            val fixed = try {
                withContext(Dispatchers.Default) { runCatching { FlexPainter.rescue(sp, ct, snapshot) }.getOrNull() }
            } finally {
                aligning = false
            }
            val stale = spec != sp
            run.finish(if (fixed != null && !stale) ({ if (spec == sp) commit(fixed, join = join, force = true) }) else null)
            if (fitAgain || (fixed != null && stale)) {
                fitAgain = false
                rescue(join = true)
            }
        }
    }

    fun fitByItself(join: Boolean) {
        val sp = spec ?: return
        val ct = content ?: return
        when (sp.style.layoutMode) {
            FlexLayoutMode.SPLIT -> rescue(join)
            FlexLayoutMode.FREE -> {
                val placed = FlexPainter.placeBoxes(sp, ct, assets)
                if (placed != sp) commit(placed, join = join, force = true)
                markFitted(placed)
            }
        }
    }

    fun bestFit(quiet: Boolean = false, join: Boolean = false, then: ((FlexAutoResult) -> Unit)? = null) {
        val sp = spec ?: return
        val ct = content ?: return
        if (aligning) {
            if (quiet) fitAgain = true
            return
        }
        aligning = true
        val snapshot = assets
        scope.launch {
            val run = veil.Run(this)
            val result = try {
                withContext(Dispatchers.Default) { runCatching { FlexPainter.autoAlign(sp, ct, snapshot, rescale = !quiet) }.getOrNull() }
            } finally {
                aligning = false
            }
            run.finish(if (result != null && result.changed && spec == sp) ({ if (spec == sp) commit(result.spec, join = join, force = true) }) else null)
            if (result == null) {
                if (!quiet) Toast.makeText(context, "Couldn't align the card", Toast.LENGTH_SHORT).show()
            } else if (spec == sp || spec == result.spec) {
                if (!quiet) Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                then?.invoke(result)
            }
            if (fitAgain) {
                fitAgain = false
                rescue(join = true)
            }
        }
    }

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
            run.finish {
                commit(next, force = true)
                markFitted(next)
                selectedBlock = null
                if (mode == FlexLayoutMode.SPLIT) {
                    if (next.layout == null) bestFit(quiet = true, join = true) else rescue(join = true)
                }
            }
        }
    }

    var alignOnOpen by remember { mutableStateOf(true) }
    LaunchedEffect(spec != null, content, assets) {
        if (!alignOnOpen || spec == null || content == null || !assets.ready) return@LaunchedEffect
        alignOnOpen = false
        val sp = spec ?: return@LaunchedEffect
        markFitted(sp)
        if (sp.style.layoutMode == FlexLayoutMode.SPLIT && sp.layout == null) bestFit(quiet = true) else fitByItself(join = false)
    }
    LaunchedEffect(alignOnOpen, aligning, veil.up) {
        if (settling && !alignOnOpen && !aligning && !veil.up) settling = false
    }
    val fitKey = spec?.let { fitKeyOf(it) }
    LaunchedEffect(fitKey, assets.ready) {
        val key = fitKey ?: return@LaunchedEffect
        if (alignOnOpen || !assets.ready || content == null || key == fittedKey) return@LaunchedEffect
        delay(FIT_DELAY_MS)
        fittedKey = key
        fitByItself(join = true)
    }

    val picker = rememberImagePicker(aspect = null, outputWidth = 1600, outputHeight = 1600) { uri ->
        updateStyle { it.copy(photoUri = uri.toString(), bgMode = FlexBgMode.PHOTO) }
    }

    val pagerState = rememberPagerState(initialPage = PAGE_CARD, pageCount = { FlexEditorPages.size })
    val bar = rememberSubPageBar(pagerState, FlexEditorPages)
    val ready = spec != null && content != null

    val stored = initial != null && initial.id > 0
    val dirty = spec != startSpec || name != startName
    /** 0: none, 1: asking the name to save a new preset, 2: renaming. */
    var nameDialog by remember { mutableStateOf(0) }
    var nameDraft by remember { mutableStateOf("") }
    var askDiscard by remember { mutableStateOf(false) }

    fun save(withName: String) {
        val sp = spec ?: return
        val saved = presetStore.save(
            FlexCardPreset(id = initial?.id ?: 0L, name = withName, spec = sp, updatedAt = 0L)
        )
        onSaved(saved)
    }
    fun askName(mode: Int) {
        nameDraft = name.ifBlank { initial?.name?.takeIf { it.isNotBlank() } ?: presetStore.nextDefaultName() }
        nameDialog = mode
    }
    fun requestClose() {
        if (dirty) askDiscard = true else onClose()
    }

    val editSpec = spec
    val editContent = content
    val editBlock = editingId?.takeIf { id -> editSpec?.blocks?.any { it.id == id } == true }
    BackHandler(enabled = editBlock == null) { requestClose() }
    if (editBlock != null && editSpec != null && editContent != null) {
        // A block's own edit page: it works on a copy and hands it back on Done.
        key(editBlock) {
            FlexEditPage(
                startId = editBlock,
                initial = editSpec,
                content = editContent,
                onDone = { edited, last ->
                    commit(edited)
                    selectedBlock = last.takeIf { id -> edited.blocks.any { it.id == id } }
                    editingId = null
                },
                onFinish = { editingId = null },
                onHome = onHome,
                onOpenSettings = onOpenSettings
            )
        }
    } else {
        ScreenScaffold(
            title = name.ifBlank { "New preset" },
            subtitle = "Preset",
            onBack = { requestClose() },
            onHome = onHome,
            onOpenSettings = onOpenSettings,
            subPages = bar,
            topBarActions = {
                IconButton(enabled = ready, onClick = { askName(2) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename")
                }
                IconButton(
                    enabled = ready,
                    onClick = {
                        if (stored) save(name.ifBlank { initial?.name.orEmpty() }) else askName(1)
                    }
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save")
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
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> EditorPage(sp, ct, assets, pl, veil.up, bottom) {
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
                                        startSpec?.let { commit(it, force = true) }
                                    }
                                )
                            }
                            PAGE_CARD -> FlexViewPage(
                                spec = sp,
                                content = ct,
                                assets = assets,
                                plan = pl,
                                bottom = bottom,
                                onCard = { card -> commit(card) },
                                selectedId = selectedBlock,
                                onSelect = { selectedBlock = it },
                                onEdit = { id ->
                                    selectedBlock = id
                                    editingId = id
                                },
                                update = { transform -> update(transform = transform) },
                                reorder = { transform -> update(continuous = true, transform = transform) },
                                canUndo = history.canUndo,
                                canRedo = history.canRedo,
                                onUndo = { undo() },
                                onRedo = { redo() },
                                onAutoAlign = { bestFit() },
                                onResizeLayout = { tree -> update(continuous = true) { it.copy(layout = tree) } },
                                onFreeBox = { id, x, y, w, h, continuous ->
                                    update(continuous = continuous) { sp ->
                                        sp.copy(blocks = sp.blocks.map { if (it.id == id) it.copy(freeX = x, freeY = y, freeW = w, freeH = h) else it })
                                    }
                                },
                                onLayer = { id, front ->
                                    update { sp ->
                                        val b = sp.blocks.first { it.id == id }
                                        val rest = sp.blocks.filter { it.id != id }
                                        sp.copy(blocks = if (front) rest + b else listOf(b) + rest)
                                    }
                                },
                                onMode = { setMode(it) },
                                aligning = aligning,
                                veiled = veil.up
                            )
                            else -> FlexAddPage(
                                spec = sp,
                                content = ct,
                                bottom = bottom,
                                onAdd = { next, newId ->
                                    commit(next)
                                    // A refused add leaves the card as it was (the full dialog tells why).
                                    if (spec == next) {
                                        selectedBlock = newId
                                        scope.launch { pagerState.animateScrollToPage(PAGE_CARD) }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    if (full) FlexFullDialog { full = false }
    if (nameDialog != 0) {
        val saving = nameDialog == 1
        AlertDialog(
            onDismissRequest = { nameDialog = 0 },
            title = { Text(if (saving) "Name this preset" else "Rename preset") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = nameDraft.isNotBlank(),
                    onClick = {
                        val chosen = nameDraft.trim()
                        nameDialog = 0
                        name = chosen
                        if (saving) save(chosen)
                    }
                ) { Text(if (saving) "Save" else "OK") }
            },
            dismissButton = { TextButton(onClick = { nameDialog = 0 }) { Text("Cancel") } }
        )
    }
    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your changes to this preset have not been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    askDiscard = false
                    onClose()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { askDiscard = false }) { Text("Keep editing") } }
        )
    }
}
