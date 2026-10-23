package com.ashrdev.aznd.ui.common.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * The Flex page, the middle one: the card, edited on the card itself, and under it the list of its blocks.
 * The card is tiled like a tiling window manager: every block has a cell and the cells fill the card. A tile is
 * only as big as its block's content needs and sits in its cell where the block's alignment says, so what can
 * be tapped and grabbed is the content, never empty space.
 *
 *  - Tap a tile (on the card or in the list) to select it. It gets resize bars on its sides that border other
 *    tiles, and an Edit button opens that block's edit page (where its content's size is set: by default content
 *    takes up its tile, as far as the module size lets it grow).
 *  - Drag a resize bar to make the tile bigger or smaller: the tiles on the other side of that border give or take the
 *    room (it snaps to an even split). A bar stops where a tile's content would no longer fit.
 *  - Hold a tile and drag it to move it: it lifts off the card and follows the finger at the size it was picked up,
 *    and the card shows itself as the drop would leave it: the other tiles glide to their new places and a faint copy
 *    of the moved tile sits, outlined, where it will land; let go and it flies there. Drop it on the left, right, top or bottom of another cell to sit
 *    beside that side of it, or on its middle to swap places. Near the edge of a cell that is also the edge of a
 *    row, a column or the card, it goes beside that whole part instead (the closer to the edge, the bigger the part,
 *    which is outlined), and all of the part's tiles make room; the card's margins drop beside the whole card.
 *  - The list switches blocks on and off, reorders them (the tiles keep their places; the blocks swap) and adds
 *    new ones. It can be folded away to give the card the whole page.
 *  - Turn and Flip next to a selected tile's Edit button turn the split it is part of (a row into a column) or
 *    mirror it.
 *  - A floating drop-down over the card has Undo, Redo, Best fit (finds the tiling that fits the blocks best),
 *    Nearest fit (the closest stable layout to the one the card has: what runs by itself on every change),
 *    Another layout (steps through the other good tilings best fit found) and Even out tiles (every cell sized by
 *    its content again).
 */
@Composable
internal fun FlexViewPage(
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    plan: FlexPlan,
    bottom: Dp,
    /** The tiling changed by hand; [placed]: the block that was dropped, or whose split was turned or flipped. */
    onLayout: (tree: FlexNode?, placed: Int?) -> Unit,
    /** A finished card from a drop whose preview was shown: applied as it is (one undo step), not fitted again. */
    onCard: (FlexSpec) -> Unit,
    /** The selected block; kept by the screen so the card and the list share it. */
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    onEdit: (Int) -> Unit,
    /** Changes the card's spec (switching, reordering, adding blocks from the list). */
    update: ((FlexSpec) -> FlexSpec) -> Unit,
    /** The floating drop-down over the card: undo and redo the changes made to the card, and auto align. */
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAutoAlign: () -> Unit,
    /** The nearest fit: the closest stable layout to the one the card has (what runs by itself on every change). */
    onNearestFit: () -> Unit,
    /** The next good tiling best fit found (a shuffle). */
    onAnotherLayout: () -> Unit,
    /** A border between tiles being dragged: one undo step per drag. */
    onResizeLayout: (FlexNode?) -> Unit,
    /** Free mode: block [id]'s box placed at [x], [y], [w] by [h] (card pixels); [continuous]: still being dragged. */
    onFreeBox: (id: Int, x: Float, y: Float, w: Float, h: Float, continuous: Boolean) -> Unit,
    /** Free mode: block [id] brought to the front ([front]) or sent to the back of the stack. */
    onLayer: (id: Int, front: Boolean) -> Unit,
    /** Flow mode: the card tiled in this order (worked out by the screen). */
    onFlowOrder: (List<Int>) -> Unit,
    /** Switches the layout mode (free mode's "Tile it"). */
    onMode: (FlexLayoutMode) -> Unit,
    aligning: Boolean,
    /** The tiling is being worked out: the card is blurred under "aznding" (see FlexPreview) and takes no new drags. */
    veiled: Boolean = false
) {
    val mode = spec.style.layoutMode
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf<Int?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var spot by remember { mutableStateOf<DropSpot?>(null) }
    val lift = remember { Animatable(0f) }
    /** The held tile as it was picked up: it floats at this size. */
    var heldBox by remember { mutableStateOf<FlexBlockBox?>(null) }
    // The card as the aimed drop would leave it, worked out off the main thread. While it is aimed, the card shows that
    // card: every tile where it will go, the held one faint in its landing place.
    var ghost by remember { mutableStateOf<DropGhost?>(null) }
    LaunchedEffect(spot) {
        val aimed = spot
        val tree = aimed?.tree
        if (aimed == null || tree == null) {
            ghost = null
            return@LaunchedEffect
        }
        val sp = spec
        val held = dragging
        val made = withContext(Dispatchers.Default) {
            runCatching {
                // As the drop will leave it (the same as the screen does after the drop, see FlexPainter.placedByHand).
                val next = FlexPainter.placedByHand(sp.copy(layout = tree), content, assets, held)
                Triple(next, FlexPainter.plan(next, content, assets), FlexPainter.holdsAsMuch(sp, next, content, assets))
            }.getOrNull()
        }
        if (made != null && spot === aimed) ghost = DropGhost(aimed, sp, made.first, made.second, made.third)
    }

    // Flow mode: when a block is picked up, the card with it at every place in the order is worked out (off the main
    // thread); while it is held, the card shows the one that puts it nearest the finger, so the others flow round it.
    var flowPick by remember { mutableStateOf<FlowPick?>(null) }
    var flowAt by remember { mutableStateOf<FlexFlowOption?>(null) }
    // The zone shown (its drop is what the card shows, or, when it fails the size check, the tile outlined red), and the
    // one the finger moved into, which takes over only after it has stayed there FLOW_HOVER_MS.
    var flowZoneShown by remember { mutableStateOf<FlowZone?>(null) }
    var flowPending by remember { mutableStateOf<List<List<Int>>?>(null) }
    var flowTimer by remember { mutableStateOf<Job?>(null) }
    // Free mode: the held box where it would go (snapped to guides), and the guides it snapped to.
    var freeAt by remember { mutableStateOf<FlexSnapped?>(null) }

    // Tiles glide to new places whenever the layout changes (a drop being aimed, a drop, a fit): each is drawn from
    // where it was shown and eases into its new place.
    val glide = remember { Animatable(1f) }
    var shown by remember { mutableStateOf<Map<Int, Offset>>(emptyMap()) }
    var glideFrom by remember { mutableStateOf<Map<Int, Offset>>(emptyMap()) }
    /**
     * Something is being resized by hand (a tile's size, a border, a free box): the layout changes on every move, and
     * the tiles and the card's shape follow at once, in step with the finger, instead of gliding behind it.
     */
    var resizing by remember { mutableStateOf(false) }
    /** A glide is set but its animation has not started yet: tiles are drawn where they were (no frame at the end first). */
    var gliding by remember { mutableStateOf(false) }
    fun glideAt(): Float = if (gliding) 0f else glide.value
    /** Where the dropped tile was let go (card pixels): it flies from there into its place. */
    var letGo by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    fun glideTo(target: FlexPlan) {
        if (resizing) {
            // Straight to the new places: no glide while resizing.
            letGo = null
            shown = target.boxes.associate { it.id to Offset(it.left, it.top) }
            glideFrom = emptyMap()
            gliding = false
            return
        }
        val t = glideAt()
        val from = HashMap<Int, Offset>()
        for (b in target.boxes) {
            val at = Offset(b.left, b.top)
            val was = shown[b.id]?.let { s -> s + (glideFrom[b.id] ?: Offset.Zero) * (1f - t) }
            val fly = letGo?.takeIf { it.first == b.id }?.second
            val start = fly ?: was ?: continue
            if ((start - at).getDistance() > 0.5f) from[b.id] = start - at
        }
        letGo = null
        shown = target.boxes.associate { it.id to Offset(it.left, it.top) }
        glideFrom = from
        gliding = from.isNotEmpty()
        if (from.isNotEmpty()) scope.launch {
            glide.snapTo(0f)
            gliding = false
            glide.animateTo(1f, tween(FLEX_GLIDE_MS))
        }
    }
    val showing = ghost?.takeIf { dragging != null && it.spot === spot && it.fits }
    // Tiles: a drop the card cannot hold (it would leave a module off): its part outlined in red, and letting go there does nothing.
    val refusedDrop = ghost?.takeIf { dragging != null && it.spot === spot && !it.fits }
    val flowing = flowAt?.takeIf { dragging != null && mode == FlexLayoutMode.FLOW }
    // Set off as soon as the plan drawn changes, before it is drawn.
    val glidedTo = remember { arrayOfNulls<FlexPlan>(1) }
    val drawnNow = flowing?.plan ?: showing?.plan ?: plan
    SideEffect {
        if (glidedTo[0] !== drawnNow) {
            glidedTo[0] = drawnNow
            glideTo(drawnNow)
        }
    }

    val planNow by rememberUpdatedState(plan)
    val layoutNow by rememberUpdatedState(onLayout)
    val cardNow by rememberUpdatedState(onCard)
    val resizeNow by rememberUpdatedState(onResizeLayout)
    val specNow by rememberUpdatedState(spec)
    val selectNow by rememberUpdatedState(onSelect)
    val modeNow by rememberUpdatedState(mode)
    val veiledNow by rememberUpdatedState(veiled)
    val freeBoxNow by rememberUpdatedState(onFreeBox)
    val contentNow by rememberUpdatedState(content)
    val assetsNow by rememberUpdatedState(assets)
    val primary = MaterialTheme.colorScheme.primary
    // A selection that is not on the card (turned off, hidden) has no handles; its list row is still selected.
    val picked = selectedId?.takeIf { id -> plan.boxes.any { it.id == id } }
    val pickedNow by rememberUpdatedState(picked)
    // The sides of the selected tile with a resize bar: those whose border can move (worked out off the main thread;
    // while a bar is dragged, the sides it had when the drag began).
    var barSides by remember { mutableStateOf(emptySet<FlexSide>()) }
    LaunchedEffect(spec, plan, picked, resizing) {
        if (resizing) return@LaunchedEffect
        val id = picked
        barSides = if (id == null || mode == FlexLayoutMode.FREE) emptySet()
        else withContext(Dispatchers.Default) { FlexPainter.resizeBars(spec, content, assets, plan, id).map { it.side }.toSet() }
    }

    /** Starts holding the tile [hit] at card pixel [at]. */
    fun startDrag(hit: FlexBlockBox, at: Offset) {
        if (veiledNow) return
        dragging = hit.id
        heldBox = hit
        selectNow(hit.id)
        finger = at
        grab = at - Offset(hit.left, hit.top)
        spot = null
        flowAt = null
        flowZoneShown = null
        flowPending = null
        flowTimer?.cancel()
        freeAt = null
        when (modeNow) {
            FlexLayoutMode.FLOW -> {
                val sp = specNow
                val id = hit.id
                flowPick = null
                scope.launch {
                    val targets = withContext(Dispatchers.Default) {
                        runCatching { FlexPainter.flowTargets(sp, contentNow, assetsNow, id) }.getOrNull()
                    }
                    if (targets != null && dragging == id) {
                        flowPick = FlowPick(sp, id, targets)
                        // Until the finger moves into another zone: home (the card as it is, the tile's own place outlined).
                        val home = FlexPainter.flowRows(planNow.tree)
                        if (flowZoneShown == null && flowPending == null) {
                            flowZoneShown = FlowZone(FlowZoneKind.HOME, home)
                            flowAt = targets[home]
                        }
                    }
                }
            }
            FlexLayoutMode.FREE -> freeAt = FlexSnapped(hit.cellLeft, hit.cellTop, hit.cellWidth, hit.cellHeight, emptyList(), emptyList())
            FlexLayoutMode.SPLIT -> {}
        }
        // A free box is moved, not lifted: only a slight lift there.
        scope.launch { lift.animateTo(if (modeNow == FlexLayoutMode.FREE) 0.3f else 1f, spring(stiffness = Spring.StiffnessMedium)) }
    }

    /** The finger, holding [id], is at card pixel [at]. */
    fun moveDrag(id: Int, at: Offset) {
        finger = at
        when (modeNow) {
            FlexLayoutMode.SPLIT -> {
                val next = dropSpot(planNow, id, finger.x, finger.y)
                // Only a different drop replaces the aimed one (so its preview is not worked out again on every move).
                val now = spot
                if (next == null || now == null || next.zone != now.zone || next.target != now.target || next.span != now.span) spot = next
            }
            FlexLayoutMode.FLOW -> {
                val pick = flowPick?.takeIf { it.id == id } ?: return
                // The zone under the finger, by the card as it is (so zones hold still while the preview flows): a gap
                // between rows or between two tiles (within the threshold), else the tile under it (a split of its row).
                val zone = flowZone(planNow, id, at.x, at.y) ?: return
                // A gap in a row that fails the size check: a row of its own after that row instead.
                val key = if (pick.targets[zone.rows] == null && zone.fallback != null) zone.fallback else zone.rows
                if (key == flowZoneShown?.rows) {
                    flowPending = null
                    flowTimer?.cancel()
                    return
                }
                if (key == flowPending) return
                // A new zone takes over only once the finger has stayed in it a moment, so crossing a threshold does not
                // snap the card back and forth.
                flowPending = key
                flowTimer?.cancel()
                val shown = if (key === zone.rows) zone else FlowZone(FlowZoneKind.GAP, key, region = zone.region)
                flowTimer = scope.launch {
                    delay(FLOW_HOVER_MS)
                    if (flowPending == key && dragging == id) {
                        flowZoneShown = shown
                        flowAt = pick.targets[key]
                        flowPending = null
                    }
                }
            }
            FlexLayoutMode.FREE -> {
                val box = heldBox ?: return
                freeAt = FlexSnap.move(planNow, id, at.x - grab.x, at.y - grab.y, box.cellWidth, box.cellHeight)
            }
        }
    }

    /** Ends a drag: drops the tile where it was aimed, or just puts it back. */
    fun endDrag(apply: Boolean) {
        val id = dragging ?: return
        val aimed = spot
        val preview = ghost
        // Flow: the zone under the finger when it lets go (the hover delay only steadies the preview, not the drop).
        val flowed = flowPick?.takeIf { it.base == specNow }?.let { pick ->
            val key = flowPending ?: flowZoneShown?.rows
            key?.let { pick.targets[it] }
        }
        val freed = freeAt
        // The tile flies from where it was let go into its place (or back to where it was).
        letGo = id to (if (modeNow == FlexLayoutMode.FREE && freed != null) Offset(freed.x, freed.y) else finger - grab)
        dragging = null
        spot = null
        ghost = null
        heldBox = null
        flowAt = null
        flowPick = null
        flowZoneShown = null
        flowPending = null
        flowTimer?.cancel()
        freeAt = null
        if (apply && modeNow == FlexLayoutMode.FLOW) {
            if (flowed != null) cardNow(flowed.spec) else glideTo(planNow)
        } else if (apply && modeNow == FlexLayoutMode.FREE) {
            if (freed != null) freeBoxNow(id, freed.x, freed.y, freed.w, freed.h, false)
        } else if (apply && aimed != null) {
            // The card as the preview showed it, when it was worked out for the card as it still is: it lands exactly
            // as shown. Else the drop's own tiling, which the screen then fits the same way.
            val worked = preview?.takeIf { it.spot === aimed && it.base == specNow }
            if (worked != null && !worked.fits) glideTo(planNow)
            else if (worked != null) cardNow(worked.card)
            else (aimed.tree ?: FlexTree.moveTo(planNow.tree, id, aimed.target, aimed.zone))?.let { layoutNow(it, id) }
        } else {
            glideTo(planNow)
        }
        scope.launch { lift.snapTo(0f) }
    }

    val gestures = Modifier
        // Tap: select the tile under the finger (or nothing).
        .pointerInput(Unit) {
            detectTapGestures { p ->
                val k = planNow.width / size.width.toFloat()
                selectNow(planNow.hit(p.x * k, p.y * k)?.id)
            }
        }
        // Hold, then drag: move a tile.
        .pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { p ->
                    val k = planNow.width / size.width.toFloat()
                    val at = Offset(p.x * k, p.y * k)
                    planNow.hit(at.x, at.y)?.let { startDrag(it, at) }
                },
                onDrag = { change, _ ->
                    val id = dragging
                    if (id != null) {
                        change.consume()
                        val k = planNow.width / size.width.toFloat()
                        moveDrag(id, Offset(change.position.x * k, change.position.y * k))
                    }
                },
                onDragEnd = { endDrag(true) },
                onDragCancel = { endDrag(false) }
            )
        }
        // The selected tile's resize bars, and in free mode its box's corner. Last in the chain, so it sees touches
        // first and leaves the others alone when the touch is on neither.
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val id = pickedNow ?: return@awaitEachGesture
                val k = size.width / planNow.width
                // A resize bar of the selected tile: drag it to move that border (the tiles on either side share the
                // space differently). Inside, the same as dragging the border itself.
                val reach = BAR_REACH.toPx() / k
                val edge = planNow.edgesOf(id).filter { it.side in barSides }.minByOrNull { it.distance(down.position.x / k, down.position.y / k) }
                    ?.takeIf { it.distance(down.position.x / k, down.position.y / k) <= reach }
                if (edge != null && modeNow != FlexLayoutMode.FREE) {
                    down.consume()
                    val border = edge.divider
                    val tree = planNow.tree
                    val start = specNow
                    val before = planNow
                    // The bar stops where it would leave a tile's content cut off or push a block off the card: the
                    // finger can go on, and the border waits there until the finger comes back.
                    fun fits(t: Float): Boolean {
                        val p = FlexPainter.plan(start.copy(layout = FlexTree.resized(tree, border, t)), contentNow, assetsNow)
                        return p.hidden.size <= before.hidden.size && (!p.clipped || before.clipped)
                    }
                    var finger = 0f
                    var placedAt = 0f
                    resizing = true
                    try {
                        drag(down.id) { change ->
                            change.consume()
                            val step = change.position - change.previousPosition
                            // A border between stacked tiles moves up and down; between tiles side by side, across.
                            finger += (if (border.vertical) step.y else step.x) / k
                            var to = finger
                            if (!fits(to)) {
                                // As close to the finger as it fits (a fast drag can jump past the limit in one step).
                                var good = placedAt
                                var bad = to
                                repeat(RESIZE_STEPS) {
                                    val mid = (good + bad) / 2f
                                    if (fits(mid)) good = mid else bad = mid
                                }
                                to = good
                            }
                            if (to != placedAt) {
                                placedAt = to
                                resizeNow(FlexTree.resized(tree, border, to))
                            }
                        }
                    } finally {
                        resizing = false
                    }
                    return@awaitEachGesture
                }
                val box = planNow.box(id) ?: return@awaitEachGesture
                val inset = HANDLE_INSET.toPx()
                val corner = Offset((box.left + box.width) * k - inset, (box.top + box.height) * k - inset)
                val onCorner = (down.position - corner).getDistance() <= HANDLE_REACH.toPx()
                if (modeNow == FlexLayoutMode.FREE) {
                    if (onCorner) {
                        // The corner of a free box: drag it to size the box (its edges snap to guides).
                        down.consume()
                        var w = box.cellWidth
                        var h = box.cellHeight
                        // Its size limits: never smaller than its content needs, never bigger than the card (a picture within its shape range).
                        val limits = FlexPainter.boxLimits(specNow, contentNow, assetsNow, id)
                        resizing = true
                        try {
                            drag(down.id) { change ->
                                change.consume()
                                val step = (change.position - change.previousPosition) / k
                                w += step.x
                                h += step.y
                                val s2 = FlexSnap.resize(planNow, id, box.cellLeft, box.cellTop, w, h, FlexPainter.FREE_MIN)
                                val (cw, ch) = limits?.clamp(s2.w, s2.h) ?: (s2.w to s2.h)
                                freeAt = FlexSnapped(s2.x, s2.y, cw, ch, if (cw == s2.w) s2.guidesX else emptyList(), if (ch == s2.h) s2.guidesY else emptyList())
                                freeBoxNow(id, s2.x, s2.y, cw, ch, true)
                            }
                        } finally {
                            resizing = false
                            freeAt = null
                        }
                        return@awaitEachGesture
                    }
                    // Anywhere else on the selected box: drag it straight away, as on a slide.
                    val at = Offset(down.position.x / k, down.position.y / k)
                    if (!box.cellContains(at.x, at.y)) return@awaitEachGesture
                    down.consume()
                    // It starts moving once the finger has moved past the touch slop (a tap only keeps it selected).
                    var started = false
                    var moved = Offset.Zero
                    drag(down.id) { change ->
                        change.consume()
                        moved += change.position - change.previousPosition
                        if (!started && moved.getDistance() > viewConfiguration.touchSlop) {
                            started = true
                            startDrag(box, at)
                        }
                        if (started) moveDrag(id, Offset(change.position.x / k, change.position.y / k))
                    }
                    if (started) endDrag(true)
                }
            }
        }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val pageHeight = maxHeight
        var listOpen by rememberSaveable { mutableStateOf(true) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            FlexWarning(spec, plan, Modifier.padding(top = 4.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // While a drop is aimed the card shows itself as that drop would leave it: the other tiles glide to
                // their new places, the held tile floats under the finger at the size it was picked up, and a faint copy
                // of it sits where it will land. Drop targets still come from the card as it is (so they hold still).
                // Flow mode the same with the place nearest the finger; free mode moves only the held box.
                val drawnSpec = flowing?.spec ?: showing?.card ?: spec
                val drawnPlan = flowing?.plan ?: showing?.plan ?: plan
                FlexPreview(
                    spec = drawnSpec,
                    content = content,
                    assets = assets,
                    plan = drawnPlan,
                    boxHeight = maxHeight,
                    modifier = Modifier.fillMaxWidth(),
                    canvasModifier = gestures,
                    veiled = veiled,
                    instantShape = resizing,
                    poses = {
                        val t = glideAt()
                        val offsets = if (t >= 1f) emptyMap() else glideFrom.mapValues { (_, d) -> FlexPose(d.x * (1f - t), d.y * (1f - t)) }
                        val id = dragging
                        val free = freeAt
                        // Flow: the held tile is pulled part of the way to its slot, so it is seen to snap there.
                        val slot = flowing?.plan?.box(id ?: -1)
                        val hx = when {
                            mode == FlexLayoutMode.FREE && free != null -> free.x
                            slot != null -> lerp(finger.x - grab.x, slot.left, SNAP_PULL)
                            else -> finger.x - grab.x
                        }
                        val hy = when {
                            mode == FlexLayoutMode.FREE && free != null -> free.y
                            slot != null -> lerp(finger.y - grab.y, slot.top, SNAP_PULL)
                            else -> finger.y - grab.y
                        }
                        when {
                            id != null -> FlexPoses(
                                offsets, id, hx, hy, lift.value,
                                heldBox = heldBox,
                                landing = if (showing != null || flowing != null) LANDING_ALPHA else 0f
                            )
                            offsets.isNotEmpty() -> FlexPoses(offsets)
                            else -> null
                        }
                    },
                    overlay = { k ->
                        val radius = CornerRadius(10.dp.toPx())
                        val held = dragging?.let { id -> plan.boxes.firstOrNull { it.id == id } }
                        val aimed = spot
                        val landing = (flowing?.plan ?: showing?.plan)?.boxes?.firstOrNull { it.id == dragging }
                        // Free mode: the guides the held box snapped to, across the card.
                        freeAt?.let { f ->
                            val guide = Color(0xFFFF4FA3)
                            for (gx in f.guidesX) drawLine(guide, Offset(gx * k, 0f), Offset(gx * k, size.height), strokeWidth = 1.5.dp.toPx())
                            for (gy in f.guidesY) drawLine(guide, Offset(0f, gy * k), Offset(size.width, gy * k), strokeWidth = 1.5.dp.toPx())
                        }
                        val refused = flowZoneShown?.takeIf { mode == FlexLayoutMode.FLOW && held != null && flowAt == null && it.target != null }?.region
                            ?: refusedDrop?.spot?.region?.takeIf { mode == FlexLayoutMode.SPLIT && held != null }
                        if (refused != null) {
                            // A drop the card cannot take (a split that fails the size check, or that would leave a module
                            // off): the part it would go beside, outlined in red (a drop here does nothing).
                            val r = refused
                            val red = Color(0xFFE5484D)
                            drawRoundRect(red.copy(alpha = 0.12f), topLeft = Offset(r[0] * k, r[1] * k), size = Size(r[2] * k, r[3] * k), cornerRadius = radius)
                            drawRoundRect(red, topLeft = Offset(r[0] * k, r[1] * k), size = Size(r[2] * k, r[3] * k), cornerRadius = radius, style = Stroke(width = 2.5.dp.toPx()))
                        }
                        if (mode == FlexLayoutMode.FLOW && held != null && landing != null) {
                            // Flow: its slot in the card as the others flowed round it.
                            val at = Offset(landing.cellLeft * k, landing.cellTop * k)
                            val extent = Size(landing.cellWidth * k, landing.cellHeight * k)
                            drawRoundRect(primary.copy(alpha = 0.14f), topLeft = at, size = extent, cornerRadius = radius)
                            drawRoundRect(primary, topLeft = at, size = extent, cornerRadius = radius, style = Stroke(width = 2.5.dp.toPx()))
                        }
                        if (held != null && landing == null && mode != FlexLayoutMode.FREE) {
                            // Nothing aimed yet: the gap the tile came from.
                            drawRoundRect(
                                primary.copy(alpha = 0.7f),
                                topLeft = Offset(held.left * k, held.top * k),
                                size = Size(held.width * k, held.height * k),
                                cornerRadius = radius,
                                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                            )
                        }
                        if (aimed != null && landing != null) {
                            // Where it lands: its cell in the card as the drop leaves it (drawn under the finger above).
                            val at = Offset(landing.cellLeft * k, landing.cellTop * k)
                            val extent = Size(landing.cellWidth * k, landing.cellHeight * k)
                            drawRoundRect(primary.copy(alpha = 0.14f), topLeft = at, size = extent, cornerRadius = radius)
                            drawRoundRect(primary, topLeft = at, size = extent, cornerRadius = radius, style = Stroke(width = 2.5.dp.toPx()))
                        } else if (aimed != null) {
                            // Until that is worked out: the side of the part the tile goes beside (a tile, or a whole row or column).
                            val (l, t, w, h) = aimed.region.let { listOf(it[0], it[1], it[2], it[3]) }
                            val part = when (aimed.zone) {
                                DropZone.LEFT -> floatArrayOf(l, t, w / 2f, h)
                                DropZone.RIGHT -> floatArrayOf(l + w / 2f, t, w / 2f, h)
                                DropZone.TOP -> floatArrayOf(l, t, w, h / 2f)
                                DropZone.BOTTOM -> floatArrayOf(l, t + h / 2f, w, h / 2f)
                                DropZone.SWAP -> floatArrayOf(l, t, w, h)
                            }
                            val at = Offset(part[0] * k, part[1] * k)
                            val extent = Size(part[2] * k, part[3] * k)
                            drawRoundRect(primary.copy(alpha = 0.28f), topLeft = at, size = extent, cornerRadius = radius)
                            drawRoundRect(primary, topLeft = at, size = extent, cornerRadius = radius, style = Stroke(width = 2.5.dp.toPx()))
                        }
                        if (aimed != null && aimed.spans) {
                            // A drop that spans several tiles: the whole part that makes room, outlined.
                            val r = aimed.region
                            drawRoundRect(
                                primary,
                                topLeft = Offset(r[0] * k, r[1] * k),
                                size = Size(r[2] * k, r[3] * k),
                                cornerRadius = radius,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                        val sel = if (held == null) picked?.let { plan.box(it) } else null
                        if (sel != null) {
                            drawRoundRect(
                                primary,
                                topLeft = Offset(sel.left * k, sel.top * k),
                                size = Size(sel.width * k, sel.height * k),
                                cornerRadius = radius,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            if (mode != FlexLayoutMode.FREE) {
                                // Its resize bars: one along each side that borders other tiles, as long as the side,
                                // with a grip in the middle (where it is easiest to take hold of).
                                val thin = 4.dp.toPx()
                                val grip = Size(7.dp.toPx(), 30.dp.toPx())
                                val inset = 10.dp.toPx()
                                for (e in plan.edgesOf(sel.id).filter { it.side in barSides }) {
                                    val across = e.side == FlexSide.TOP || e.side == FlexSide.BOTTOM
                                    val a = Offset(e.x0 * k, e.y0 * k)
                                    val b = Offset(e.x1 * k, e.y1 * k)
                                    // Clear of the cell's rounded corners.
                                    val s0 = if (across) a.copy(x = min(a.x + inset, (a.x + b.x) / 2f)) else a.copy(y = min(a.y + inset, (a.y + b.y) / 2f))
                                    val s1 = if (across) b.copy(x = max(b.x - inset, (a.x + b.x) / 2f)) else b.copy(y = max(b.y - inset, (a.y + b.y) / 2f))
                                    drawLine(primary.copy(alpha = 0.55f), s0, s1, strokeWidth = thin, cap = StrokeCap.Round)
                                    val g = if (across) Size(grip.height, grip.width) else grip
                                    val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                                    val at = Offset(mid.x - g.width / 2f, mid.y - g.height / 2f)
                                    val corner = CornerRadius(min(g.width, g.height) / 2f)
                                    drawRoundRect(primary, topLeft = at, size = g, cornerRadius = corner)
                                    drawRoundRect(Color.White, topLeft = at, size = g, cornerRadius = corner, style = Stroke(width = 1.5.dp.toPx()))
                                }
                            } else {
                                // Free mode: the corner handle, to size the box.
                                val corner = Offset((sel.left + sel.width) * k - HANDLE_INSET.toPx(), (sel.top + sel.height) * k - HANDLE_INSET.toPx())
                                drawCircle(primary, radius = 11.dp.toPx(), center = corner)
                                drawCircle(Color.White, radius = 4.dp.toPx(), center = corner)
                            }
                        }
                    }
                )
                // Undo, redo and auto align float over the card (out of the way while a tile is being moved).
                if (dragging == null) {
                    val history = listOf(
                        FlexMenuAction(Icons.AutoMirrored.Filled.Undo, "Undo", canUndo) { onUndo() },
                        FlexMenuAction(Icons.AutoMirrored.Filled.Redo, "Redo", canRedo) { onRedo() }
                    )
                    val tools = when (mode) {
                        FlexLayoutMode.SPLIT -> listOf(
                            FlexMenuAction(Icons.Default.AutoFixHigh, if (aligning) "Fitting…" else "Best fit", !aligning) { onAutoAlign() },
                            FlexMenuAction(Icons.Default.CenterFocusStrong, if (aligning) "Fitting…" else "Nearest fit", !aligning) { onNearestFit() },
                            FlexMenuAction(Icons.Default.Shuffle, "Another layout", !aligning && plan.boxes.size > 1) { onAnotherLayout() },
                            FlexMenuAction(Icons.AutoMirrored.Filled.ViewQuilt, "Even out tiles", plan.tree is FlexGroup) {
                                onLayout(FlexTree.relaxed(plan.tree), null)
                            }
                        )
                        // Flow: best fit, free to pick the order too (the order it finds is then the card's order).
                        FlexLayoutMode.FLOW -> listOf(
                            FlexMenuAction(Icons.Default.AutoFixHigh, if (aligning) "Fitting…" else "Best order", !aligning) { onAutoAlign() }
                        )
                        FlexLayoutMode.FREE -> listOf(
                            FlexMenuAction(Icons.AutoMirrored.Filled.ViewQuilt, "Tile it", true) { onMode(FlexLayoutMode.SPLIT) }
                        )
                    }
                    FlexFloatingMenu(
                        actions = history + tools,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp)
                    )
                }
            }

            // Tap a tile: its name, where it is, and the way to its edit page.
            val block = spec.blocks.firstOrNull { it.id == picked }
            AnimatedVisibility(visible = block != null) {
                if (block != null) {
                    val position = plan.boxes.indexOfFirst { it.id == block.id } + 1
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (mode == FlexLayoutMode.FREE) "${block.type.label} · layer $position of ${plan.boxes.size}"
                            else "${block.type.label} · $position of ${plan.boxes.size}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (mode == FlexLayoutMode.FREE) {
                            // Its place in the stack of boxes.
                            IconButton(onClick = { onLayer(block.id, false) }, enabled = position > 1) {
                                Icon(Icons.Default.FlipToBack, contentDescription = "Send to back")
                            }
                            IconButton(onClick = { onLayer(block.id, true) }, enabled = position < plan.boxes.size) {
                                Icon(Icons.Default.FlipToFront, contentDescription = "Bring to front")
                            }
                        }
                        if (mode == FlexLayoutMode.FLOW) {
                            // One place earlier or later in the order; the others flow round it.
                            val order = FlexTree.leaves(plan.tree)
                            val i = order.indexOf(block.id)
                            IconButton(onClick = { onFlowOrder(order.toMutableList().also { o -> o.removeAt(i); o.add(i - 1, block.id) }) }, enabled = i > 0) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Earlier")
                            }
                            IconButton(onClick = { onFlowOrder(order.toMutableList().also { o -> o.removeAt(i); o.add(i + 1, block.id) }) }, enabled = i in 0 until order.lastIndex) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Later")
                            }
                        }
                        // The split this tile is part of: turn it (a row becomes a column) or flip it (mirror its order).
                        if (mode == FlexLayoutMode.SPLIT && FlexTree.refOf(plan.tree, block.id) != null) {
                            IconButton(onClick = { FlexTree.turned(plan.tree, block.id)?.let { onLayout(it, block.id) } }) {
                                Icon(Icons.Default.Rotate90DegreesCw, contentDescription = "Turn the split")
                            }
                            IconButton(onClick = { FlexTree.flipped(plan.tree, block.id)?.let { onLayout(it, block.id) } }) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = "Flip the split")
                            }
                        }
                        Button(onClick = { onEdit(block.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit")
                        }
                    }
                }
            }

            // The blocks (the old Content page), foldable so the card can have the whole page.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { listOpen = !listOpen }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Blocks · ${spec.blocks.size}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (listOpen) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = if (listOpen) "Hide blocks" else "Show blocks"
                )
            }
            AnimatedVisibility(visible = listOpen) {
                BlockListPanel(
                    spec = spec,
                    content = content,
                    plan = plan,
                    selectedId = selectedId,
                    onSelect = onSelect,
                    onEdit = onEdit,
                    update = update,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pageHeight * 0.32f)
                )
            }
            Spacer(modifier = Modifier.height(bottom))
        }
    }
}

/** Flow mode: the places the held block [id] can go, worked out for [base] (the card when it was picked up). */
/** Flow mode: every card a drop of the held block [id] can make, by the rows it makes (null: fails the size check), for [base]. */
private class FlowPick(val base: FlexSpec, val id: Int, val targets: Map<List<List<Int>>, FlexFlowOption?>)

/** Flow mode: how long the finger must stay in a new zone before the card shows its drop. */
private const val FLOW_HOVER_MS = 150L

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Flow mode: how far towards its slot the held tile is drawn, so it is seen to snap into the place it will take. */
private const val SNAP_PULL = 0.35f

/** The card as an aimed drop would leave it: worked out for [base] (the card when it was aimed), shown while it is aimed. */
private class DropGhost(val spot: DropSpot, val base: FlexSpec, val card: FlexSpec, val plan: FlexPlan, val fits: Boolean = true)

/** How close a touch must be to free mode's corner handle to grab it, and how far inside the tile's corner it sits. */
private val HANDLE_REACH = 30.dp
private val HANDLE_INSET = 14.dp

/** How close a touch must be to a resize bar to take hold of it (it runs along the tile's side, so only a strip). */
private val BAR_REACH = 18.dp

/** How many halvings a resize bar takes to find where it has to stop. */
private const val RESIZE_STEPS = 5

/** How faint the held tile is where it will land. (Tiles glide for FLEX_GLIDE_MS.) */
private const val LANDING_ALPHA = 0.45f
