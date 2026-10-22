package com.ashrdev.aznd.ui.common.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * The Flex page, the middle one: the card, edited on the card itself, and under it the list of its blocks.
 * The card is tiled like a tiling window manager: every block has a cell and the cells fill the card. A tile is
 * only as big as its block's content needs and sits in its cell where the block's alignment says, so what can
 * be tapped and grabbed is the content, never empty space.
 *
 *  - Tap a tile (on the card or in the list) to select it. It gets a handle in its corner, and an Edit button
 *    opens that block's edit page.
 *  - Drag the corner handle to scale the tile's content: the tile grows or shrinks with it and the other tiles
 *    make room.
 *  - Hold a tile and drag it to move it: it lifts off the card and follows the finger, and the card shows where
 *    every tile would go, the moved one lit up. Drop it on the left, right, top or bottom of another cell to sit
 *    beside that side of it, or on its middle to swap places. Near the edge of a cell that is also the edge of a
 *    row, a column or the card, it goes beside that whole part instead (the closer to the edge, the bigger the part,
 *    which is outlined), and all of the part's tiles make room; the card's margins drop beside the whole card.
 *  - The list switches blocks on and off, reorders them (the tiles keep their places; the blocks swap) and adds
 *    new ones. It can be folded away to give the card the whole page.
 *  - A selected tile shows pills on its borders with its neighbours: drag one to share the space between them (it
 *    snaps to an even split). Turn and Flip next to its Edit button turn the split it is part of (a row into a
 *    column) or mirror it.
 *  - A floating drop-down over the card has Undo, Redo, Best fit (finds the tiling that fits the blocks best),
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
    onLayout: (FlexNode?) -> Unit,
    /** Scales the content of block [id]: 1 is natural size. */
    onSizeBlock: (id: Int, size: Float) -> Unit,
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
    /** The next good tiling best fit found (a shuffle). */
    onAnotherLayout: () -> Unit,
    /** A border between tiles being dragged: one undo step per drag. */
    onResizeLayout: (FlexNode?) -> Unit,
    aligning: Boolean
) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf<Int?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var spot by remember { mutableStateOf<DropSpot?>(null) }
    val lift = remember { Animatable(0f) }
    // The card as the aimed drop would leave it, worked out off the main thread: where every tile would go.
    var ghost by remember { mutableStateOf<Pair<DropSpot, FlexPlan>?>(null) }
    LaunchedEffect(spot) {
        val aimed = spot ?: return@LaunchedEffect
        val tree = aimed.tree ?: return@LaunchedEffect
        val sp = spec
        val p = withContext(Dispatchers.Default) {
            runCatching { FlexPainter.plan(sp.copy(layout = tree), content, assets) }.getOrNull()
        }
        if (p != null && spot === aimed) ghost = aimed to p
    }

    val planNow by rememberUpdatedState(plan)
    val layoutNow by rememberUpdatedState(onLayout)
    val sizeNow by rememberUpdatedState(onSizeBlock)
    val resizeNow by rememberUpdatedState(onResizeLayout)
    val specNow by rememberUpdatedState(spec)
    val selectNow by rememberUpdatedState(onSelect)
    val primary = MaterialTheme.colorScheme.primary
    // A selection that is not on the card (turned off, hidden) has no handles; its list row is still selected.
    val picked = selectedId?.takeIf { id -> plan.boxes.any { it.id == id } }
    val pickedNow by rememberUpdatedState(picked)

    /** Ends a drag: drops the tile where it was aimed, or just puts it back. */
    fun endDrag(apply: Boolean) {
        val id = dragging ?: return
        val aimed = spot
        dragging = null
        spot = null
        ghost = null
        if (apply && aimed != null) {
            (aimed.tree ?: FlexTree.moveTo(planNow.tree, id, aimed.target, aimed.zone))?.let { layoutNow(it) }
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
                    val hit = planNow.hit(at.x, at.y)
                    if (hit != null) {
                        dragging = hit.id
                        selectNow(hit.id)
                        finger = at
                        grab = at - Offset(hit.left, hit.top)
                        spot = null
                        scope.launch { lift.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
                    }
                },
                onDrag = { change, _ ->
                    val id = dragging
                    if (id != null) {
                        change.consume()
                        val k = planNow.width / size.width.toFloat()
                        finger = Offset(change.position.x * k, change.position.y * k)
                        val next = dropSpot(planNow, id, finger.x, finger.y)
                        // Only a different drop replaces the aimed one (so its preview is not worked out again on every move).
                        val now = spot
                        if (next == null || now == null || next.zone != now.zone || next.target != now.target || next.span != now.span) spot = next
                    }
                },
                onDragEnd = { endDrag(true) },
                onDragCancel = { endDrag(false) }
            )
        }
        // Drag the corner handle of the selected tile: scale its content. Last in the chain, so it sees touches
        // first and leaves the others alone when the touch is not on the handle.
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val id = pickedNow ?: return@awaitEachGesture
                val k = size.width / planNow.width
                // A border of the selected tile: drag it to share the space between the tiles on either side.
                val border = planNow.dividersOf(id).minByOrNull { (down.position - Offset(it.cx * k, it.cy * k)).getDistance() }
                if (border != null && (down.position - Offset(border.cx * k, border.cy * k)).getDistance() <= HANDLE_REACH.toPx()) {
                    down.consume()
                    val tree = planNow.tree
                    var travelled = 0f
                    drag(down.id) { change ->
                        change.consume()
                        val step = change.position - change.previousPosition
                        // A border between stacked tiles moves up and down; between tiles side by side, across.
                        travelled += (if (border.vertical) step.y else step.x) / k
                        resizeNow(FlexTree.resized(tree, border, travelled))
                    }
                    return@awaitEachGesture
                }
                val block = specNow.blocks.firstOrNull { it.id == id } ?: return@awaitEachGesture
                if (block.type == FlexBlockType.PHOTO) return@awaitEachGesture   // a photo's size is its crop shape
                val box = planNow.box(id) ?: return@awaitEachGesture
                val inset = HANDLE_INSET.toPx()
                val corner = Offset((box.left + box.width) * k - inset, (box.top + box.height) * k - inset)
                if ((down.position - corner).getDistance() > HANDLE_REACH.toPx()) return@awaitEachGesture
                down.consume()
                val start = block.size
                var travelled = 0f
                drag(down.id) { change ->
                    change.consume()
                    val step = change.position - change.previousPosition
                    // Drag right/down to grow, left/up to shrink; every 400 card px doubles or halves it.
                    travelled += (step.x + step.y) / 2f / k
                    sizeNow(id, (start * 2f.pow(travelled / 400f)).coerceIn(MIN_BLOCK_SIZE, MAX_BLOCK_SIZE))
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
                FlexPreview(
                    spec = spec,
                    content = content,
                    assets = assets,
                    plan = plan,
                    boxHeight = maxHeight,
                    modifier = Modifier.fillMaxWidth(),
                    canvasModifier = gestures,
                    poses = {
                        val id = dragging
                        if (id != null) FlexPoses(emptyMap(), id, finger.x - grab.x, finger.y - grab.y, lift.value) else null
                    },
                    overlay = { k ->
                        val radius = CornerRadius(10.dp.toPx())
                        val held = dragging?.let { id -> plan.boxes.firstOrNull { it.id == id } }
                        if (held != null) {
                            // The gap the tile came from.
                            drawRoundRect(
                                primary.copy(alpha = 0.7f),
                                topLeft = Offset(held.left * k, held.top * k),
                                size = Size(held.width * k, held.height * k),
                                cornerRadius = radius,
                                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                            )
                        }
                        val aimed = spot
                        val future = ghost?.takeIf { it.first === aimed }?.second
                        val landing = future?.boxes?.firstOrNull { it.id == dragging }
                        if (aimed != null && future != null && landing != null) {
                            // Where every tile goes if it is dropped here: the others outlined, the dropped one lit up.
                            val k2 = k * plan.width / future.width
                            for (b in future.boxes) {
                                if (b.id == landing.id) continue
                                drawRoundRect(
                                    primary.copy(alpha = 0.45f),
                                    topLeft = Offset(b.cellLeft * k2, b.cellTop * k2),
                                    size = Size(b.cellWidth * k2, b.cellHeight * k2),
                                    cornerRadius = radius,
                                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                                )
                            }
                            val at = Offset(landing.cellLeft * k2, landing.cellTop * k2)
                            val extent = Size(landing.cellWidth * k2, landing.cellHeight * k2)
                            drawRoundRect(primary.copy(alpha = 0.28f), topLeft = at, size = extent, cornerRadius = radius)
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
                            // Its borders with its neighbours: pills to drag.
                            for (d in plan.dividersOf(sel.id)) {
                                val long = 26.dp.toPx()
                                val thin = 7.dp.toPx()
                                val pill = if (d.vertical) Size(long, thin) else Size(thin, long)
                                val at = Offset(d.cx * k - pill.width / 2f, d.cy * k - pill.height / 2f)
                                drawRoundRect(primary, topLeft = at, size = pill, cornerRadius = CornerRadius(thin / 2f))
                                drawRoundRect(Color.White, topLeft = at, size = pill, cornerRadius = CornerRadius(thin / 2f), style = Stroke(width = 1.5.dp.toPx()))
                            }
                            // The corner handle: drag it to scale the content.
                            if (spec.blocks.firstOrNull { it.id == sel.id }?.type != FlexBlockType.PHOTO) {
                                val corner = Offset((sel.left + sel.width) * k - HANDLE_INSET.toPx(), (sel.top + sel.height) * k - HANDLE_INSET.toPx())
                                drawCircle(primary, radius = 11.dp.toPx(), center = corner)
                                drawCircle(Color.White, radius = 4.dp.toPx(), center = corner)
                            }
                        }
                    }
                )
                // Undo, redo and auto align float over the card (out of the way while a tile is being moved).
                if (dragging == null) {
                    FlexFloatingMenu(
                        actions = listOf(
                            FlexMenuAction(Icons.AutoMirrored.Filled.Undo, "Undo", canUndo) { onUndo() },
                            FlexMenuAction(Icons.AutoMirrored.Filled.Redo, "Redo", canRedo) { onRedo() },
                            FlexMenuAction(
                                Icons.Default.AutoFixHigh,
                                if (aligning) "Fitting…" else "Best fit",
                                !aligning
                            ) { onAutoAlign() },
                            FlexMenuAction(Icons.Default.Shuffle, "Another layout", !aligning && plan.boxes.size > 1) { onAnotherLayout() },
                            FlexMenuAction(Icons.AutoMirrored.Filled.ViewQuilt, "Even out tiles", plan.tree is FlexGroup) {
                                onLayout(FlexTree.relaxed(plan.tree))
                            }
                        ),
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
                            "${block.type.label} · $position of ${plan.boxes.size}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        // The split this tile is part of: turn it (a row becomes a column) or flip it (mirror its order).
                        if (FlexTree.refOf(plan.tree, block.id) != null) {
                            IconButton(onClick = { FlexTree.turned(plan.tree, block.id)?.let { onLayout(it) } }) {
                                Icon(Icons.Default.Rotate90DegreesCw, contentDescription = "Turn the split")
                            }
                            IconButton(onClick = { FlexTree.flipped(plan.tree, block.id)?.let { onLayout(it) } }) {
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

/** How close a touch must be to the corner handle to grab it, and how far inside the tile's corner it sits. */
private val HANDLE_REACH = 30.dp
private val HANDLE_INSET = 14.dp
private const val MIN_BLOCK_SIZE = 0.5f
private const val MAX_BLOCK_SIZE = 2f
