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
import androidx.compose.material.icons.filled.MoreVert
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
import com.ashrdev.aznd.ui.theme.Space
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


/** Left to right: how the poster looks, the poster itself, its list of cards. Opens on the middle one. */
private val FlexEditorPages = listOf(
    BarPage("Look", Icons.Default.Palette),
    BarPage("Poster", Icons.Default.Dashboard),
    BarPage("Cards", Icons.Default.Widgets)
)
private const val PAGE_POSTER = 1

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
 * [sp] with the fields that only colour or fade the poster set to their defaults: two specs with the same result lay out
 * the same, so the plan of one is the plan of the other. Everything that can change a size, a wrap or a position stays.
 */
private fun layoutShapeOf(sp: FlexSpec): FlexSpec {
    val d = FlexStyle()
    return sp.copy(
        style = sp.style.copy(
            bgA = d.bgA, bgB = d.bgB, gradientAngle = d.gradientAngle, bgGradient = d.bgGradient, bgPatterns = d.bgPatterns,
            photoBlur = d.photoBlur, photoDim = d.photoDim, textColor = d.textColor, accent = d.accent,
            panelColor = d.panelColor, panelAlpha = d.panelAlpha, panelFill = d.panelFill,
            panelBorderColor = d.panelBorderColor, borderColor = d.borderColor, watermarkAlign = d.watermarkAlign
        )
    )
}

/** What the editor is for: [EDIT] changes a preset on sample data; [USE] shows a preset with the person's real data. */
internal enum class FlexEditorMode { EDIT, USE }

/** What decides the data the poster needs: the cards, their kind, count and choices. Layout and look changes are not in it. */
private fun contentKeyOf(spec: FlexSpec): Any =
    spec.blocks.map { listOf(it.id, it.type, it.enabled, it.limit, it.stat, it.keys, it.graph, it.cal) }

/**
 * [next] (made from the poster as it is drawn, [shown]: the preset's spec with the person's data choices applied by
 * [bound]) put back on the preset's own spec [base]: the fields the choices decide (text, items, count, graph,
 * calendar) keep the preset's values unless [next] changed them, so a choice never leaks into the preset.
 */
private fun rebaseSpec(next: FlexSpec, shown: FlexSpec, base: FlexSpec): FlexSpec {
    if (shown == base) return next
    val baseBy = base.blocks.associateBy { it.id }
    val shownBy = shown.blocks.associateBy { it.id }
    return next.copy(blocks = next.blocks.map { b ->
        val o = baseBy[b.id]
        val s = shownBy[b.id]
        if (o == null || s == null) b else b.copy(
            keys = if (b.keys == s.keys) o.keys else b.keys,
            limit = if (b.limit == s.limit) o.limit else b.limit,
            text = if (b.text == s.text) o.text else b.text,
            graph = if (b.graph == s.graph) o.graph else b.graph,
            cal = if (b.cal == s.cal) o.cal else b.cal
        )
    })
}

/**
 * The preset editor: the poster maker for one [FlexCardPreset]. Three swipeable pages, also tabs in the bottom bar:
 * Look (colours, text, background), Poster (the live poster; tap a card on it and its settings open under it) and
 * Cards (the list of cards to reorder and switch on or off, with the Add button that opens the Add screen). It works
 * on sample content, so every card shows something. Save sits in the top bar and stores the preset
 * (a new one asks for its name first); leaving with unsaved changes asks first.
 * [initial] is null for a new preset (it starts from the blank card), else the preset to edit; one with an id of 0 or
 * less is not stored yet.
 *
 * In [FlexEditorMode.USE] (the Use screen) the same editor shows the real data: [initialBindings] are the person's
 * choices (see [FlexBindings]) and the poster's content comes from [loadBoundContent]. The editor holds two specs:
 * the preset's own (layout edits apply to it, it is what "Update preset to current" stores) and the one drawn, which
 * is the first with the choices applied ([bound]). Share opens the Export screen; Update preset to current stores the
 * layout, look and card options (not the choices) as the preset's new spec ([onSaved] gets it, the screen stays);
 * Configure again reopens the choices. Leaving with layout changes asks first. [onKept] says an export was added to
 * the Saved page.
 */
@Composable
internal fun FlexPresetEditor(
    initial: FlexCardPreset?,
    onSaved: (FlexCardPreset) -> Unit,
    onClose: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    mode: FlexEditorMode = FlexEditorMode.EDIT,
    library: FlexLibrary? = null,
    initialBindings: FlexBindings = emptyMap(),
    workoutViewModel: WorkoutViewModel? = null,
    streakViewModel: StreakViewModel? = null,
    onKept: () -> Unit = {},
    /**
     * The screen whose blueprint ("<Name> default", see FlexDefaults.kt) this poster is: with [FlexEditorMode.EDIT] the
     * Defaults page's editor (Save writes the blueprint, no name), with [FlexEditorMode.USE] a share started on that
     * screen (the same Use screen as for a preset). Only that screen's card types and stats are offered either way.
     */
    allowed: FlexPreset? = null,
    /** Screen share only: the blueprint as stored (before the cards the item has no data for were turned off). */
    blueprint: FlexSpec? = null,
    /** Screen share only: where the share started, for the defaults of cards turned on or added here. */
    share: FlexShareContext? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presetStore = remember { FlexPresetStore(context) }
    val use = mode == FlexEditorMode.USE && initial != null && library != null &&
        workoutViewModel != null && streakViewModel != null
    /** The Use screen of a share started on a screen: no remembered choices, no Configure, "Save layout to ... default". */
    val screenFlow = use && allowed != null
    /** The Defaults page's editor: Save writes the blueprint of [allowed]. */
    val blueprintEdit = !use && allowed != null

    // On the Use screen the preset's spec is there from the start (the data is real, there is no sample to wait for).
    var spec by remember { mutableStateOf<FlexSpec?>(if (use) initial?.spec?.reconciled() else null) }
    /** The card the editor started from: what "reset" goes back to and what "unsaved changes" is measured against. */
    var startSpec by remember { mutableStateOf<FlexSpec?>(spec) }
    /** The person's data choices (Use screen only). */
    var bindings by remember { mutableStateOf(initialBindings) }
    val contentKey = spec?.let { contentKeyOf(it) }
    val content by produceState<FlexContent?>(null, use, contentKey, bindings) {
        value = if (use) {
            val sp = spec
            if (sp == null) null else runCatching {
                loadBoundContent(context, sp, bindings, library!!, workoutViewModel!!, streakViewModel!!)
            }.getOrNull() ?: value
        } else {
            // The sample's title matches the screen the blueprint is for (a preset has no subject: blank kicker).
            FlexSampleContent.forSubject(FlexSampleContent.load(context), allowed)
        }
    }
    /** The poster as drawn: the preset's spec with the data choices applied. Equal to [spec] in the editor. */
    val shown = remember(spec, bindings, use) { spec?.let { if (use) it.bound(bindings) else it } }
    fun view(sp: FlexSpec): FlexSpec = if (use) sp.bound(bindings) else sp
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

    val assets = rememberFlexAssets(shown, content)
    // The layout of the card as it is right now: its height, shrink, block positions, what is hidden or cut off.
    val layoutShape = remember(shown) { shown?.let { layoutShapeOf(it) } }
    val plan = remember(layoutShape, content, assets) {
        val sp = shown
        val ct = content
        if (sp != null && ct != null) FlexPainter.plan(sp, ct, assets) else null
    }
    /** The card selected on the poster (its settings are open under the poster while one is). */
    var selectedBlock by remember { mutableStateOf<Int?>(null) }
    /** The Add screen is open (it replaces the pages). */
    var adding by remember { mutableStateOf(false) }
    /** The Export screen is open (Use screen; it replaces the pages). */
    var exporting by remember { mutableStateOf(false) }
    /** Share was tapped while a photo card on the poster has no picture: the dialog that says so is open. */
    var askPictures by remember { mutableStateOf(false) }
    /** The Configure popup is open (Use screen). */
    var configuring by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    /** Set after a card was added: once the pages are back, go to the Poster page. */
    var goPoster by remember { mutableStateOf(false) }

    // Everything done to the card on this screen can be undone and redone (from the drop-down over the card).
    val history = remember { UndoStack<FlexSpec>() }
    // The poster's content budget: a change that would take the poster past what it can hold (a card added or turned on,
    // more items, a bigger size, a smaller poster ...) is not made, and the person is told it cannot fit any more.
    var full by remember { mutableStateOf(false) }
    /**
     * [join]: the change belongs to the undo step before it (best fit following the change that set it off). [force]:
     * not held to the content budget (fits, which never add content, and putting the preset's card back).
     */
    // The fit key of the last spec checked, so a slider tick works out one key (the one before is the last tick's).
    val lastKey = remember { arrayOfNulls<Any>(3) }
    /** True when [after] (the drawn form of [next]) has a different fit key from the drawn form of the spec [before]. */
    fun keyChanged(before: FlexSpec, next: FlexSpec, after: FlexSpec): Boolean {
        val was = if (lastKey[0] === before && lastKey[1] === bindings && lastKey[2] != null) lastKey[2] else fitKeyOf(view(before))
        val now = fitKeyOf(after)
        lastKey[0] = next
        lastKey[1] = bindings
        lastKey[2] = now
        return was != now
    }
    fun tryCommit(next: FlexSpec, continuous: Boolean = false, join: Boolean = false, force: Boolean = false): Boolean {
        val before = spec
        val ct = content
        var next = next
        val viewNext = if (!force && before != null && ct != null) view(next) else null
        if (viewNext != null && before != null && ct != null && keyChanged(before, next, viewNext) &&
            FlexPainter.refuses(view(before), viewNext, ct, assets)
        ) {
            // A card that is added (a new block id: Add screen, Duplicate, "Add another") and does not fit is added turned
            // off instead: a card that is off costs the poster nothing. Any other change that does not fit is refused.
            val known = before.blocks.map { it.id }.toSet()
            val added = next.blocks.filter { it.id !in known && it.enabled }.map { it.id }.toSet()
            val off = if (added.isEmpty()) null else next.copy(blocks = next.blocks.map { if (it.id in added) it.copy(enabled = false) else it })
            if (off == null || FlexPainter.refuses(view(before), view(off), ct, assets)) {
                full = true
                return false
            }
            next = off
            Toast.makeText(context, "The poster is full, so the card was added turned off", Toast.LENGTH_LONG).show()
        }
        if (settling && force) {
            startSpec = next
            spec = next
            return true
        }
        // [continuous] changes close together (see UndoStack) make one undo step.
        if (before != null && before != next && !(join && history.canUndo)) history.push(before, continuous)
        spec = next
        return true
    }
    fun commit(next: FlexSpec, continuous: Boolean = false, join: Boolean = false, force: Boolean = false) {
        tryCommit(next, continuous, join, force)
    }
    /** [continuous]: changes that follow each other closely (a slider, a dragged handle) are one undo step. */
    fun update(continuous: Boolean = false, transform: (FlexSpec) -> FlexSpec) {
        spec?.let { commit(transform(it), continuous) }
    }
    fun updateStyle(transform: (FlexStyle) -> FlexStyle) = update(continuous = true) { it.copy(style = transform(it.style)) }

    var fittedKey by remember { mutableStateOf<Any?>(null) }
    fun markFitted(sp: FlexSpec?) {
        if (sp != null) fittedKey = fitKeyOf(view(sp))
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
        val drawn = view(sp)
        scope.launch {
            val run = veil.Run(this)
            val fixed = try {
                withContext(Dispatchers.Default) {
                    runCatching { FlexPainter.rescue(drawn, ct, snapshot) }.getOrNull()?.let { rebaseSpec(it, drawn, sp) }
                }
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
                val drawn = view(sp)
                val placed = rebaseSpec(FlexPainter.placeBoxes(drawn, ct, assets), drawn, sp)
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
        val drawn = view(sp)
        scope.launch {
            val run = veil.Run(this)
            val aligned = try {
                withContext(Dispatchers.Default) { runCatching { FlexPainter.autoAlign(drawn, ct, snapshot, rescale = !quiet) }.getOrNull() }
            } finally {
                aligning = false
            }
            // The result is made on the poster as drawn; what goes into the editor is the preset's own spec with its layout.
            val resultSpec = aligned?.let { rebaseSpec(it.spec, drawn, sp) }
            val result = aligned
            run.finish(if (result != null && resultSpec != null && result.changed && spec == sp) ({ if (spec == sp) commit(resultSpec, join = join, force = true) }) else null)
            if (result == null) {
                if (!quiet) Toast.makeText(context, "Couldn't align the poster", Toast.LENGTH_SHORT).show()
            } else if (spec == sp || spec == resultSpec) {
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
        val drawn = view(sp)
        scope.launch {
            val run = veil.Run(this)
            val next = withContext(Dispatchers.Default) {
                runCatching { FlexPainter.placeBoxes(FlexPainter.withMode(drawn, ct, snapshot, mode), ct, snapshot) }.getOrNull()
                    ?.let { rebaseSpec(it, drawn, sp) }
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
    LaunchedEffect(spec != null, content, assets, alignOnOpen) {
        if (!alignOnOpen || spec == null || content == null || !assets.ready) return@LaunchedEffect
        alignOnOpen = false
        val sp = spec ?: return@LaunchedEffect
        markFitted(sp)
        if (sp.style.layoutMode == FlexLayoutMode.SPLIT && sp.layout == null) bestFit(quiet = true) else fitByItself(join = false)
    }
    LaunchedEffect(alignOnOpen, aligning, veil.up) {
        if (settling && !alignOnOpen && !aligning && !veil.up) settling = false
    }
    // Use screen: new data (a choice changed, Configure again) can need another fit. While nothing was changed by hand
    // that fit belongs to the starting card (like the first one), after hand changes it is a change of its own.
    var lastContent by remember { mutableStateOf<FlexContent?>(null) }
    LaunchedEffect(content) {
        val c = content ?: return@LaunchedEffect
        val first = lastContent == null
        lastContent = c
        if (first || !use) return@LaunchedEffect
        if (spec == startSpec) {
            settling = true
            alignOnOpen = true
        } else {
            delay(FIT_DELAY_MS)
            fitByItself(join = true)
        }
    }
    // Use screen: a card turned on or added gets its default choices.
    val activeCards = spec?.blocks?.filter { it.enabled }?.map { it.id to it.type }
    LaunchedEffect(use, activeCards, screenFlow) {
        val sp = spec
        if (!use || sp == null || library == null) return@LaunchedEffect
        // Only cards without choices yet get their defaults; the choices already made stay.
        // A photo card added empty stays empty (no picture in its binding): the person picks one with the "+" on the poster.
        val empty = sp.blocks.filter { it.enabled && it.isEmptyPhoto() }.map { it.id }.toSet()
        val missing = defaultBindings(sp, library, bindings, if (screenFlow) share else null).filterKeys { it !in bindings }
            .mapValues { (id, b) -> if (id in empty) FlexBinding() else b }
        if (missing.isNotEmpty()) bindings = bindings + missing
    }
    val fitKey = shown?.let { fitKeyOf(it) }
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

    /** The empty photo card the "+" on the poster was tapped for (the picker answers for it). */
    var photoFor by remember { mutableStateOf<Int?>(null) }
    val cardPicker = rememberImagePicker(aspect = null, outputWidth = 1600, outputHeight = 1600) { uri ->
        val id = photoFor ?: return@rememberImagePicker
        photoFor = null
        val picked = uri.toString()
        if (use) {
            // Use screen: the picture is a data choice of that card.
            bindings = bindings + (id to (bindings[id] ?: FlexBinding()).copy(photoUri = picked))
        } else {
            update { sp -> sp.copy(blocks = sp.blocks.map { if (it.id == id) it.copy(keys = listOf(picked), text = "") else it }) }
        }
    }

    val pagerState = rememberPagerState(initialPage = PAGE_POSTER, pageCount = { FlexEditorPages.size })
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
    /** Use screen: stores the layout, look and card options as the preset's new spec (not the data choices). */
    fun updatePreset() {
        val sp = spec ?: return
        val base = initial ?: return
        val saved = presetStore.save(base.copy(spec = sp))
        startSpec = sp
        Toast.makeText(context, "Preset updated", Toast.LENGTH_SHORT).show()
        onSaved(saved)
    }
    /** Screen share: the Use screen as opened or as last saved; what "the person turned a card on or off here" is measured against. */
    var enabledRef by remember { mutableStateOf(initial?.spec) }
    /** Screen share: the blueprint as it is stored now (what a card the person did not touch goes back to). */
    var blueprintNow by remember { mutableStateOf(blueprint) }
    /**
     * Screen share: "Save layout to <Name> default". Writes the base spec (layout, look, card options; never the data
     * choices). Cards the share turned off for lack of data ([prepareScreenShare]) keep the enabled state of the stored
     * blueprint, unless the person turned that card on or off on this screen since the screen opened (or was last saved).
     */
    fun saveToDefault() {
        val source = allowed ?: return
        val sp = spec ?: return
        val ref = enabledRef?.blocks.orEmpty().associateBy { it.id }
        val kept = blueprintNow?.blocks.orEmpty().associateBy { it.id }
        val base = blueprintNow
        val out = if (base == null) sp else sp.copy(blocks = sp.blocks.map { c ->
            val r = ref[c.id]
            val o = kept[c.id]
            if (o == null) {
                // A card added on this screen: saved in blueprint form (data is the share's, not this card's).
                when {
                    c.type == FlexBlockType.PHOTO -> c.copy(keys = null, text = "")
                    c.type == FlexBlockType.GRAPH && allowed == FlexPreset.EXERCISE ->
                        c.copy(graph = c.graph.copy(variables = listOf(FLEX_SCREEN_EXERCISE_1RM)))
                    else -> c
                }
            } else {
                // The stored blueprint's card with this screen's layout/look and card options; what the share filled in
                // (photo pictures, a graph's resolved variables) and the card's own text stay the blueprint's.
                c.copy(
                    keys = o.keys,
                    text = o.text,
                    graph = c.graph.copy(variables = o.graph.variables),
                    enabled = if (r == null || c.enabled != r.enabled) c.enabled else o.enabled
                )
            }
        }).reconciled()
        FlexDefaultsStore.save(context, source, out)
        enabledRef = sp
        blueprintNow = out
        startSpec = sp
        Toast.makeText(context, "Saved to ${source.defaultsName()} default", Toast.LENGTH_SHORT).show()
    }
    /** Defaults page's editor: stores the blueprint (no name to ask). */
    fun saveBlueprint() {
        val source = allowed ?: return
        val sp = spec ?: return
        FlexDefaultsStore.save(context, source, sp)
        val base = initial ?: return
        onSaved(base.copy(spec = sp))
    }
    fun askName(mode: Int) {
        nameDraft = name.ifBlank { initial?.name?.takeIf { it.isNotBlank() } ?: presetStore.nextDefaultName() }
        nameDialog = mode
    }
    fun requestClose() {
        if (dirty) askDiscard = true else onClose()
    }

    // After a card was added the Add screen is gone and the pages are back: show the poster with the new card selected.
    LaunchedEffect(adding, goPoster) {
        if (goPoster && !adding) {
            goPoster = false
            pagerState.scrollToPage(PAGE_POSTER)
        }
    }

    BackHandler(enabled = !adding && !exporting) { requestClose() }
    val addSpec = shown
    val addContent = content
    if (exporting && addSpec != null && addContent != null && initial != null) {
        FlexExportScreen(
            spec = addSpec,
            content = addContent,
            presetId = initial.id,
            presetName = initial.name,
            bindings = bindings,
            onKept = onKept,
            onBack = { exporting = false },
            onHome = onHome,
            onOpenSettings = onOpenSettings,
            rememberChoices = !screenFlow
        )
    } else if (adding && addSpec != null && addContent != null) {
        FlexAddPage(
            spec = addSpec,
            content = addContent,
            onAdd = { draft, newId ->
                // A refused add leaves the poster as it was (the full dialog tells why) and the popup open.
                val done = tryCommit(rebaseSpec(draft, addSpec, spec ?: addSpec))
                if (done) {
                    selectedBlock = newId
                    adding = false
                    goPoster = true
                }
                done
            },
            onBack = { adding = false },
            onHome = onHome,
            onOpenSettings = onOpenSettings,
            library = if (use) library else null,
            allowed = allowed
        )
    } else {
        ScreenScaffold(
            title = name.ifBlank { "New preset" },
            subtitle = if (use) "Use preset" else if (blueprintEdit) "Default" else "Preset",
            onBack = { requestClose() },
            onHome = onHome,
            onOpenSettings = onOpenSettings,
            subPages = bar,
            topBarActions = {
                if (use) {
                    IconButton(enabled = ready && !aligning && !veil.up, onClick = {
                        // A poster with an empty photo card is not shared (see FLEX_EMPTY_PHOTO_MESSAGE).
                        if (shown?.blocks?.any { it.enabled && it.isEmptyPhoto() } == true) askPictures = true else exporting = true
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            if (screenFlow) {
                                DropdownMenuItem(
                                    text = { Text("Save layout to ${allowed?.defaultsName().orEmpty()} default") },
                                    enabled = ready,
                                    onClick = {
                                        moreMenu = false
                                        saveToDefault()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Update preset to current") },
                                    enabled = ready,
                                    onClick = {
                                        moreMenu = false
                                        updatePreset()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Configure again") },
                                    enabled = ready,
                                    onClick = {
                                        moreMenu = false
                                        configuring = true
                                    }
                                )
                            }
                        }
                    }
                } else {
                    if (!blueprintEdit) {
                        IconButton(enabled = ready, onClick = { askName(2) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename")
                        }
                    }
                    IconButton(
                        enabled = ready,
                        onClick = {
                            if (blueprintEdit) saveBlueprint()
                            else if (stored) save(name.ifBlank { initial?.name.orEmpty() }) else askName(1)
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            }
        ) { padding ->
            val sp = shown
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
                            PAGE_POSTER -> FlexViewPage(
                                spec = sp,
                                content = ct,
                                assets = assets,
                                plan = pl,
                                bottom = bottom,
                                onCard = { card -> spec?.let { base -> commit(rebaseSpec(card, sp, base)) } },
                                selectedId = selectedBlock,
                                onSelect = { selectedBlock = it },
                                panel = { id ->
                                    val own = spec?.blocks?.firstOrNull { it.id == id }
                                    // A share from a screen: the screen fixes the session, lifts, routine, streaks and day note, so
                                    // only the title, text, photo and a graph's range are chosen here (a screen's own photo cards are
                                    // shown as a photo choice too: the item's pictures and the gallery).
                                    val choiceBlock = when {
                                        own == null -> null
                                        !screenFlow -> own
                                        own.type == FlexBlockType.PHOTO -> own.copy(text = "")
                                        own.type == FlexBlockType.HEADER || own.type == FlexBlockType.TEXT ||
                                            own.type == FlexBlockType.GRAPH -> own
                                        else -> null
                                    }
                                    // The data choice is the panel's first (open) fold; the card's own folds below start closed.
                                    val dataFold = use && library != null && choiceBlock != null && choiceBlock.dataNeed() != null
                                    if (dataFold && library != null && choiceBlock != null) {
                                        FlexFoldSection("Data", "What this card shows", initiallyOpen = true) {
                                            FlexDataChoice(
                                                block = choiceBlock,
                                                binding = bindings[id] ?: FlexBinding(),
                                                library = library,
                                                onChange = { chosen -> bindings = bindings + (id to chosen) },
                                                graphRangeOnly = screenFlow
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(Space.md))
                                    }
                                    FlexCardConfig(
                                        blockId = id,
                                        spec = sp,
                                        content = ct,
                                        assets = assets,
                                        mode = FlexConfigMode.PANEL,
                                        onChange = { continuous, transform ->
                                            update(continuous = continuous, transform = transform)
                                        },
                                        onSelect = { selectedBlock = it },
                                        dataBound = use,
                                        allowedStats = allowed?.allowedStats()?.takeIf { it.isNotEmpty() },
                                        fixedGraphVariable = allowed == FlexPreset.EXERCISE,
                                        photoSlot = blueprintEdit,
                                        firstOpen = !dataFold
                                    )
                                },
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
                                veiled = veil.up,
                                // Blueprint editor: photo cards are slots (the shared item's photos), there is nothing to pick.
                                onPickPhoto = if (blueprintEdit) null else ({ id: Int ->
                                    photoFor = id
                                    cardPicker.launch()
                                })
                            )
                            else -> FlexCardsPage(
                                spec = sp,
                                content = ct,
                                assets = assets,
                                plan = pl,
                                selectedId = selectedBlock,
                                bottom = bottom,
                                onPick = { id ->
                                    selectedBlock = id
                                    scope.launch { pagerState.animateScrollToPage(PAGE_POSTER) }
                                },
                                onAdd = { adding = true },
                                update = { transform -> update(transform = transform) },
                                reorder = { transform -> update(continuous = true, transform = transform) }
                            )
                        }
                    }
                }
            }
        }
    }
    if (full) FlexFullDialog { full = false }
    if (askPictures) {
        // The first empty photo card in poster order (cards the poster cannot show come last).
        val empty = shown?.blocks.orEmpty().filter { it.enabled && it.isEmptyPhoto() }.map { it.id }
        val first = plan?.boxes?.map { it.id }?.firstOrNull { it in empty } ?: empty.firstOrNull()
        AlertDialog(
            onDismissRequest = { askPictures = false },
            text = { Text(FLEX_EMPTY_PHOTO_MESSAGE) },
            confirmButton = {
                TextButton(onClick = {
                    askPictures = false
                    if (first != null) {
                        selectedBlock = first
                        scope.launch { pagerState.animateScrollToPage(PAGE_POSTER) }
                    }
                }) { Text("Show the card") }
            },
            dismissButton = { TextButton(onClick = { askPictures = false }) { Text("Cancel") } }
        )
    }
    val configSpec = spec
    if (configuring && use && configSpec != null && library != null) {
        FlexConfigureDialog(
            spec = configSpec,
            library = library,
            initial = bindings,
            previewContent = { b -> loadBoundContent(context, configSpec, b, library, workoutViewModel!!, streakViewModel!!) },
            onDone = { chosen ->
                configuring = false
                bindings = chosen
            },
            onCancel = { configuring = false }
        )
    }
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
            text = { Text(if (use) "Your layout changes will not be kept." else "Your changes to this preset have not been saved.") },
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
