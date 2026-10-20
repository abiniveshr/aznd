package com.ashrdev.aznd.ui.common.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** How a dragged block lands relative to a block of the card. */
internal enum class DropKind { ROW_BEFORE, ROW_AFTER, LEFT_OF, RIGHT_OF }

private const val MIN_BLOCK_SIZE = 0.5f
private const val MAX_BLOCK_SIZE = 2f
/** The narrowest a block can be made, as a share of its row. */
internal const val MIN_BLOCK_WIDTH = 0.15f
/** Holding a dragged block over another this long splits the space between them. */
private const val SPLIT_HOLD_MS = 500f

/**
 * The rows after moving [dragged] next to [target]: into a row of its own above or below the target's row,
 * or into that row on the target's left or right. Null when it cannot be done (row already full).
 */
internal fun rowsAfterMove(rows: List<List<Int>>, dragged: Int, kind: DropKind, target: Int): List<List<Int>>? {
    val work = rows.map { it.toMutableList() }.toMutableList()
    work.forEach { it.remove(dragged) }
    work.removeAll { it.isEmpty() }
    val r = work.indexOfFirst { target in it }
    if (r < 0) return null
    when (kind) {
        DropKind.ROW_BEFORE -> work.add(r, mutableListOf(dragged))
        DropKind.ROW_AFTER -> work.add(r + 1, mutableListOf(dragged))
        DropKind.LEFT_OF, DropKind.RIGHT_OF -> {
            if (work[r].size >= FLEX_MAX_PER_ROW) return null
            val at = work[r].indexOf(target) + if (kind == DropKind.RIGHT_OF) 1 else 0
            work[r].add(at, dragged)
        }
    }
    return work.map { it.toList() }
}

/**
 * Writes an arrangement back into the card: the shown blocks take the new order (the blocks that are off
 * or hidden keep their places in the list) and every shown block gets an explicit place: first in its row
 * is [FlexPlace.BELOW], the others [FlexPlace.BESIDE]. Also how an automatic arrangement is frozen.
 *
 * Rows that changed get new widths so the result makes sense: a block alone in its row takes all of it;
 * a block that joined a row with free space takes that space; otherwise the row is split equally.
 * [oldRows] is the arrangement before, [moved] the block that was moved (if any).
 */
internal fun applyRows(
    spec: FlexSpec,
    shownIds: List<Int>,
    rows: List<List<Int>>,
    oldRows: List<List<Int>> = rows,
    moved: Int? = null
): FlexSpec {
    val order = rows.flatten()
    if (order.size != shownIds.size) return spec
    val placeOf = HashMap<Int, FlexPlace>()
    rows.forEach { row -> row.forEachIndexed { i, id -> placeOf[id] = if (i == 0) FlexPlace.BELOW else FlexPlace.BESIDE } }

    val byId = spec.blocks.associateBy { it.id }
    val widthOf = HashMap<Int, Float>()
    for (row in rows) {
        if (row in oldRows) continue
        if (row.size == 1) {
            widthOf[row[0]] = 1f
            continue
        }
        val rest = row.filter { it != moved }
        val joinedFreeSpace = moved != null && moved in row && rest in oldRows
        if (joinedFreeSpace) {
            var used = 0f
            rest.forEach { used += (byId[it]?.width ?: 1f).coerceAtMost(1f) }
            val free = 1f - used
            if (used < 1f && free >= 0.2f) {
                widthOf[moved!!] = free
                continue
            }
        }
        row.forEach { widthOf[it] = 1f / row.size }
    }

    val shown = shownIds.toSet()
    val result = spec.blocks.toMutableList()
    var next = 0
    for (slot in spec.blocks.indices) {
        if (spec.blocks[slot].id !in shown) continue
        val id = order[next++]
        val b = byId.getValue(id)
        result[slot] = b.copy(place = placeOf.getValue(id), width = widthOf[id] ?: b.width)
    }
    return spec.copy(blocks = result)
}

/** The share of its row that block [id] takes now (1 = all of it). */
internal fun widthFraction(plan: FlexPlan, id: Int): Float {
    val row = plan.rows.firstOrNull { id in it } ?: return 1f
    val inner = plan.contentWidth - plan.gap * (row.size - 1)
    val w = plan.box(id)?.width ?: return 1f
    return if (inner <= 0f) 1f else (w / inner).coerceIn(0f, 1f)
}

/**
 * New widths for the row of block [id] when it takes [wanted] of the row. Blocks beside it only give way if
 * there is not enough room left, and then in proportion. Empty when the block is not on the card.
 */
internal fun resizeWidth(plan: FlexPlan, id: Int, wanted: Float): Map<Int, Float> {
    val row = plan.rows.firstOrNull { id in it } ?: return emptyMap()
    val f = wanted.coerceIn(MIN_BLOCK_WIDTH, 1f - MIN_BLOCK_WIDTH * (row.size - 1))
    val out = HashMap<Int, Float>()
    out[id] = f
    val others = row.filter { it != id }
    if (others.isNotEmpty()) {
        val current = others.associateWith { widthFraction(plan, it) }
        val sum = current.values.sum()
        val room = 1f - f
        if (sum > room && sum > 0f) others.forEach { out[it] = (current.getValue(it) * room / sum).coerceAtLeast(MIN_BLOCK_WIDTH) }
    }
    return out
}

/**
 * What the arrangement should become while block [id] is held with its top-left at [topLeft] (card pixels),
 * or null to leave it. One change at a time; the caller waits for the card to be laid out again.
 *  - Into empty space beside a row that does not fill its width: into that row.
 *  - Its leading edge past the middle of the row above or below: into a row of its own there.
 *  - Pulled clear of its own row (above or below it): out of that row.
 * (Holding it over another block splits the space with it: see the dwell timer in [FlexViewPage].)
 */
internal fun rowsForDrag(plan: FlexPlan, id: Int, topLeft: Offset): List<List<Int>>? {
    val mine = plan.box(id) ?: return null
    val rows = plan.rows
    val myRow = rows.indexOfFirst { id in it }
    if (myRow < 0) return null
    val cx = topLeft.x + mine.width / 2f
    val cy = topLeft.y + mine.height / 2f

    fun span(row: Int): Pair<Float, Float> {
        var top = Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        rows[row].forEach { rid ->
            plan.box(rid)?.let {
                top = minOf(top, it.top)
                bottom = maxOf(bottom, it.top + it.height)
            }
        }
        return top to bottom
    }

    // Into the empty space beside a row that is not full width.
    val contentLeft = plan.originX
    val contentRight = plan.originX + plan.contentWidth
    for (ri in rows.indices) {
        if (ri == myRow || rows[ri].size >= FLEX_MAX_PER_ROW) continue
        val boxes = rows[ri].mapNotNull { plan.box(it) }
        if (boxes.isEmpty()) continue
        val (top, bottom) = span(ri)
        if (cy < top || cy > bottom) continue
        val right = boxes.maxOf { it.left + it.width }
        val left = boxes.minOf { it.left }
        val room = 0.2f * plan.contentWidth
        val result = when {
            cx > right && contentRight - right >= room ->
                rowsAfterMove(rows, id, DropKind.RIGHT_OF, boxes.maxByOrNull { it.left }!!.id)
            cx < left && left - contentLeft >= room ->
                rowsAfterMove(rows, id, DropKind.LEFT_OF, boxes.minByOrNull { it.left }!!.id)
            else -> null
        }
        if (result != null && result != rows) return result
    }

    // Above or below.
    val dTop = topLeft.y
    val dBottom = topLeft.y + mine.height
    if (myRow + 1 < rows.size) {
        val (t, b) = span(myRow + 1)
        if (dBottom > (t + b) / 2f) return rowsAfterMove(rows, id, DropKind.ROW_AFTER, rows[myRow + 1].first())
    }
    if (myRow > 0) {
        val (t, b) = span(myRow - 1)
        if (dTop < (t + b) / 2f) return rowsAfterMove(rows, id, DropKind.ROW_BEFORE, rows[myRow - 1].first())
    }
    if (rows[myRow].size > 1) {
        val (t, b) = span(myRow)
        val other = rows[myRow].first { it != id }
        if (cy > b) return rowsAfterMove(rows, id, DropKind.ROW_AFTER, other)
        if (cy < t) return rowsAfterMove(rows, id, DropKind.ROW_BEFORE, other)
    }
    return null
}

/**
 * The right-hand page: the card as large as the page allows, edited on the card itself.
 *
 *  - Tap a block to select it. Handles appear and a Size control under the card.
 *  - The corner handle scales its contents; the handle on its right edge sets how wide it is (any block, not
 *    only ones beside another). The sliders do the same.
 *  - Hold a block and drag to move it: it lifts off the card and follows the finger while the others glide
 *    out of the way and the gap shows where it will land. Pass it above or below another block to stack
 *    vertically; drop it in the empty space beside a narrow block to sit next to it; or hold it over another
 *    block for half a second to split the space between the two. The order is the Content list's order.
 */
@Composable
internal fun FlexViewPage(
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    plan: FlexPlan,
    bottom: Dp,
    onRowsChange: (shownIds: List<Int>, rows: List<List<Int>>, oldRows: List<List<Int>>, moved: Int?) -> Unit,
    onWidths: (Map<Int, Float>) -> Unit,
    onSizeBlock: (id: Int, size: Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Int?>(null) }
    /** Finger is down on this block (dragging it). */
    var dragging by remember { mutableStateOf<Int?>(null) }
    /** The floating block: while dragged, and while it settles into its place after the drop. */
    var heldId by remember { mutableStateOf<Int?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    /** A handle is being dragged: blocks follow the layout exactly, with no gliding to lag behind. */
    var resizing by remember { mutableStateOf(false) }
    /** The block the held one is hovering over, and how far along the hold-to-split timer is (0 to 1). */
    var hoverId by remember { mutableStateOf<Int?>(null) }
    var hoverProgress by remember { mutableStateOf(0f) }
    val lift = remember { Animatable(0f) }
    /** How far each block is from its place right now (card pixels); animated back to zero. */
    val offsets = remember { mutableStateMapOf<Int, Offset>() }
    val jobs = remember { HashMap<Int, Job>() }
    val settle = remember { arrayOfNulls<Job>(1) }
    val dwell = remember { arrayOfNulls<Job>(1) }
    /** The layout the last change was made from; changes wait until the card has been laid out again. */
    val waiting = remember { arrayOfNulls<FlexPlan>(1) }

    val planNow by rememberUpdatedState(plan)
    val specNow by rememberUpdatedState(spec)
    val rowsNow by rememberUpdatedState(onRowsChange)
    val widthsNow by rememberUpdatedState(onWidths)
    val sizeNow by rememberUpdatedState(onSizeBlock)
    val primary = MaterialTheme.colorScheme.primary
    // A selection that has left the card (turned off, hidden) is forgotten.
    val picked = selected?.takeIf { id -> plan.boxes.any { it.id == id } }
    val pickedNow by rememberUpdatedState(picked)

    /** Starts [id] [from] away from its place and glides it home. */
    fun glide(id: Int, from: Offset) {
        jobs[id]?.cancel()
        offsets[id] = from
        jobs[id] = scope.launch {
            animate(
                typeConverter = Offset.VectorConverter,
                initialValue = from,
                targetValue = Offset.Zero,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) { value, _ -> offsets[id] = value }
            offsets.remove(id)
        }
    }

    // Whenever the layout changes (a swap, a resize, a new card size), every block that moved glides from
    // where it was drawn to its new place instead of jumping. Runs in the same frame, before drawing.
    // While a handle is being dragged there is no gliding: the block, its outline and the handle all sit
    // exactly where the layout puts them.
    val lastBoxes = remember { arrayOf<Map<Int, FlexBlockBox>>(emptyMap()) }
    val lastPlan = remember { arrayOfNulls<FlexPlan>(1) }
    SideEffect {
        if (lastPlan[0] !== plan) {
            if (resizing) {
                jobs.values.forEach { it.cancel() }
                jobs.clear()
                offsets.clear()
            } else {
                val before = lastBoxes[0]
                for (b in plan.boxes) {
                    if (b.id == heldId) continue
                    val old = before[b.id] ?: continue
                    val current = offsets[b.id] ?: Offset.Zero
                    val dx = old.left + current.x - b.left
                    val dy = old.top + current.y - b.top
                    if (abs(dx) > 0.5f || abs(dy) > 0.5f) glide(b.id, Offset(dx, dy))
                }
            }
            lastBoxes[0] = plan.boxes.associateBy { it.id }
            lastPlan[0] = plan
        }
    }

    fun drop() {
        val id = dragging ?: return
        dragging = null
        dwell[0]?.cancel()
        hoverId = null
        hoverProgress = 0f
        val slot = planNow.boxes.firstOrNull { it.id == id }
        if (slot != null) glide(id, (finger - grab) - Offset(slot.left, slot.top))
        settle[0]?.cancel()
        settle[0] = scope.launch {
            lift.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            if (dragging == null) heldId = null
        }
    }

    /** While a block is held: watch what it hovers over; held there long enough, split the space with it. */
    fun startDwellWatch() {
        dwell[0]?.cancel()
        dwell[0] = scope.launch {
            var current: Int? = null
            var since = 0L
            while (isActive) {
                val id = dragging ?: break
                val plan = planNow
                val mine = plan.box(id)
                if (mine != null) {
                    val topLeft = finger - grab
                    val cx = topLeft.x + mine.width / 2f
                    val cy = topLeft.y + mine.height / 2f
                    val target = plan.boxes.firstOrNull { t -> t.id != id && t.contains(cx, cy) }
                    val now = System.nanoTime()
                    if (target?.id != current) {
                        current = target?.id
                        since = now
                        hoverId = current
                        hoverProgress = 0f
                    } else if (target != null) {
                        val progress = ((now - since) / 1_000_000f / SPLIT_HOLD_MS).coerceIn(0f, 1f)
                        hoverProgress = progress
                        if (progress >= 1f && waiting[0] !== plan) {
                            val kind = if (cx < target.left + target.width / 2f) DropKind.LEFT_OF else DropKind.RIGHT_OF
                            val rows = rowsAfterMove(plan.rows, id, kind, target.id)
                            if (rows != null && rows != plan.rows) {
                                waiting[0] = plan
                                rowsNow(plan.shownIds, rows, plan.rows, id)
                                current = null
                                hoverId = null
                                hoverProgress = 0f
                            } else {
                                since = now   // nothing to do here (row full); start over rather than retry every frame
                            }
                        }
                    }
                }
                delay(16)
            }
            hoverId = null
            hoverProgress = 0f
        }
    }

    val gestures = Modifier
        // Tap: select a block (or nothing).
        .pointerInput(Unit) {
            detectTapGestures { p ->
                val k = FLEX_CARD_WIDTH / size.width.toFloat()
                selected = planNow.hit(p.x * k, p.y * k)?.id
            }
        }
        // Hold, then drag: move a block.
        .pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { p ->
                    val k = FLEX_CARD_WIDTH / size.width.toFloat()
                    val hit = planNow.hit(p.x * k, p.y * k) ?: return@detectDragGesturesAfterLongPress
                    settle[0]?.cancel()
                    jobs[hit.id]?.cancel()
                    offsets.remove(hit.id)
                    dragging = hit.id
                    heldId = hit.id
                    selected = hit.id
                    finger = Offset(p.x * k, p.y * k)
                    grab = finger - Offset(hit.left, hit.top)
                    waiting[0] = null
                    scope.launch { lift.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
                    startDwellWatch()
                },
                onDrag = { change, _ ->
                    change.consume()
                    val id = dragging
                    if (id != null) {
                        val k = FLEX_CARD_WIDTH / size.width.toFloat()
                        finger = Offset(change.position.x * k, change.position.y * k)
                        val plan = planNow
                        if (waiting[0] !== plan) {
                            val moved = rowsForDrag(plan, id, finger - grab)
                            if (moved != null) {
                                waiting[0] = plan
                                rowsNow(plan.shownIds, moved, plan.rows, id)
                            }
                        }
                    }
                },
                onDragEnd = { drop() },
                onDragCancel = { drop() }
            )
        }
        // Drag one of the selected block's handles: the corner scales it, the right edge sets its width.
        // Last in the chain, so it sees touches first and leaves the others alone when the touch is on a handle.
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val id = pickedNow ?: return@awaitEachGesture
                val plan = planNow
                val box = plan.box(id) ?: return@awaitEachGesture
                val k = size.width / FLEX_CARD_WIDTH.toFloat()
                val inset = HANDLE_INSET.toPx()
                val reach = HANDLE_REACH.toPx()
                // Handles sit where the block is drawn, which includes any gliding still in progress.
                val shift = offsets[id] ?: Offset.Zero
                val corner = Offset((box.left + box.width + shift.x) * k - inset, (box.top + box.height + shift.y) * k - inset)
                val edge = Offset((box.left + box.width + shift.x) * k - inset, (box.top + box.height / 2f + shift.y) * k)
                val onCorner = (down.position - corner).getDistance() <= reach
                val onEdge = !onCorner && (down.position - edge).getDistance() <= reach
                if (!onCorner && !onEdge) return@awaitEachGesture
                down.consume()
                resizing = true
                try {
                    var travelled = 0f
                    if (onCorner) {
                        val start = specNow.blocks.firstOrNull { it.id == id }?.size ?: 1f
                        drag(down.id) { change ->
                            change.consume()
                            val step = change.position - change.previousPosition
                            // Drag right/down to grow, left/up to shrink; every 400 card px doubles or halves it.
                            travelled += (step.x + step.y) / 2f / k
                            sizeNow(id, (start * 2f.pow(travelled / 400f)).coerceIn(MIN_BLOCK_SIZE, MAX_BLOCK_SIZE))
                        }
                    } else {
                        val row = plan.rows.firstOrNull { id in it }
                        val inner = plan.contentWidth - plan.gap * ((row?.size ?: 1) - 1)
                        val start = widthFraction(plan, id)
                        drag(down.id) { change ->
                            change.consume()
                            travelled += (change.position.x - change.previousPosition.x) / k
                            if (inner > 0f) {
                                val widths = resizeWidth(plan, id, start + travelled / inner)
                                if (widths.isNotEmpty()) widthsNow(widths)
                            }
                        }
                    }
                } finally {
                    resizing = false
                }
            }
        }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)) {
        FlexWarning(spec, plan, Modifier.padding(top = 4.dp))
        BoxWithConstraints(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()) {
            FlexPreview(
                spec = spec,
                content = content,
                assets = assets,
                plan = plan,
                boxHeight = maxHeight,
                modifier = Modifier.fillMaxWidth(),
                canvasModifier = gestures,
                poses = {
                    val id = heldId
                    val slot = id?.let { held -> plan.boxes.firstOrNull { it.id == held } }
                    val moved = offsets.mapValues { FlexPose(it.value.x, it.value.y) }
                    if (id != null && slot != null) {
                        val off = offsets[id] ?: Offset.Zero
                        val topLeft = if (dragging != null) finger - grab else Offset(slot.left + off.x, slot.top + off.y)
                        FlexPoses(moved, id, topLeft.x, topLeft.y, lift.value)
                    } else {
                        FlexPoses(moved)
                    }
                },
                overlay = { k ->
                    val radius = CornerRadius(10.dp.toPx())
                    val held = heldId?.let { id -> plan.boxes.firstOrNull { it.id == id } }
                    if (held != null) {
                        // The gap the block will drop into.
                        drawRoundRect(
                            primary.copy(alpha = 0.7f),
                            Offset(held.left * k, held.top * k),
                            Size(held.width * k, held.height * k),
                            radius,
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                        )
                        // Hold-to-split: the block underneath fills in as the half second runs down.
                        val under = hoverId?.let { id -> plan.boxes.firstOrNull { it.id == id } }
                        if (under != null && dragging != null) {
                            val topLeft = finger - grab
                            val onLeft = topLeft.x + held.width / 2f < under.left + under.width / 2f
                            val half = under.width / 2f
                            val sideLeft = if (onLeft) under.left else under.left + half
                            drawRoundRect(
                                primary.copy(alpha = 0.10f + 0.30f * hoverProgress),
                                Offset(sideLeft * k, under.top * k),
                                Size(half * k, under.height * k),
                                radius
                            )
                            drawRoundRect(
                                primary.copy(alpha = 0.5f + 0.5f * hoverProgress),
                                Offset(under.left * k, under.top * k),
                                Size(under.width * k, under.height * k),
                                radius,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    } else {
                        plan.boxes.firstOrNull { it.id == picked }?.let { b ->
                            // Where the block is actually drawn: its place plus any glide still running.
                            val shift = offsets[b.id] ?: Offset.Zero
                            val left = (b.left + shift.x) * k
                            val top = (b.top + shift.y) * k
                            drawRoundRect(
                                primary,
                                Offset(left, top),
                                Size(b.width * k, b.height * k),
                                radius,
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                            val right = left + b.width * k
                            val bottom = top + b.height * k
                            val corner = Offset(right - HANDLE_INSET.toPx(), bottom - HANDLE_INSET.toPx())
                            drawCircle(primary, radius = 11.dp.toPx(), center = corner)
                            drawCircle(Color.White, radius = 4.dp.toPx(), center = corner)
                            val pill = Size(8.dp.toPx(), 30.dp.toPx())
                            val mid = Offset(right - HANDLE_INSET.toPx(), (top + bottom) / 2f)
                            drawRoundRect(primary, Offset(mid.x - pill.width / 2f, mid.y - pill.height / 2f), pill, CornerRadius(4.dp.toPx()))
                        }
                    }
                }
            )
        }

        // Tap a block to bring up its size.
        val block = spec.blocks.firstOrNull { it.id == picked }
        AnimatedVisibility(visible = block != null) {
            if (block != null) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${block.type.label} size",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                sizeNow(block.id, 1f)
                                widthsNow(mapOf(block.id to 1f))
                            },
                            enabled = block.size != 1f || widthFraction(plan, block.id) < 0.999f
                        ) { Text("Reset") }
                    }
                    LabeledSlider(
                        label = "Size (corner handle)",
                        valueText = "${(block.size * 100).roundToInt()}%",
                        value = block.size,
                        range = MIN_BLOCK_SIZE..MAX_BLOCK_SIZE
                    ) { sizeNow(block.id, it) }
                    val share = widthFraction(plan, block.id)
                    LabeledSlider(
                        label = "Width (edge handle)",
                        valueText = "${(share * 100).roundToInt()}% of the row",
                        value = share,
                        range = MIN_BLOCK_WIDTH..1f
                    ) { v ->
                        val widths = resizeWidth(plan, block.id, v)
                        if (widths.isNotEmpty()) widthsNow(widths)
                    }
                }
            }
        }
        val label = block?.type?.label
        val position = plan.boxes.indexOfFirst { it.id == picked } + 1
        Text(
            text = if (label != null) {
                "$label · $position of ${plan.boxes.size}. Hold it and drag to move it."
            } else {
                "Tap a block to size it. Hold and drag to rearrange; hold one over another for half a second to share the space."
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(bottom))
    }
}

private val HANDLE_INSET = 14.dp
private val HANDLE_REACH = 30.dp
