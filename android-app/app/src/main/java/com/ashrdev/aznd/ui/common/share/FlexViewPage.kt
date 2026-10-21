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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Button
import kotlinx.coroutines.launch

/**
 * The Flex page, the middle one: the card, edited on the card itself, and under it the list of its blocks.
 * The card is tiled like a tiling window manager: every block has a tile and the tiles fill the card.
 *
 *  - Tap a tile (on the card or in the list) to select it. Its borders get handles and an Edit button opens
 *    that block's edit page.
 *  - Drag a handle on a selected tile's border to resize: the tile on the other side gives or takes the room.
 *  - Hold a tile and drag it to move it: it lifts off the card and follows the finger, and the part of the tile
 *    under the finger that it will take lights up. Drop it on the left, right, top or bottom of another tile to
 *    sit beside that side of it (sharing its space), or on its middle to swap places.
 *  - The list switches blocks on and off, reorders them (the tiles keep their places; the blocks swap) and adds
 *    new ones. It can be folded away to give the card the whole page.
 */
@Composable
internal fun FlexViewPage(
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    plan: FlexPlan,
    bottom: Dp,
    onLayout: (FlexNode?) -> Unit,
    /** The selected block; kept by the screen so the card and the list share it. */
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    onEdit: (Int) -> Unit,
    /** Changes the card's spec (switching, reordering, adding blocks from the list). */
    update: ((FlexSpec) -> FlexSpec) -> Unit
) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf<Int?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var spot by remember { mutableStateOf<DropSpot?>(null) }
    val lift = remember { Animatable(0f) }

    val planNow by rememberUpdatedState(plan)
    val layoutNow by rememberUpdatedState(onLayout)
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
        if (apply && aimed != null) {
            FlexTree.moveTo(planNow.tree, id, aimed.target, aimed.zone)?.let { layoutNow(it) }
        }
        scope.launch { lift.snapTo(0f) }
    }

    val gestures = Modifier
        // Tap: select the tile under the finger (or nothing).
        .pointerInput(Unit) {
            detectTapGestures { p ->
                val k = FLEX_CARD_WIDTH / size.width.toFloat()
                selectNow(planNow.hit(p.x * k, p.y * k)?.id)
            }
        }
        // Hold, then drag: move a tile.
        .pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { p ->
                    val k = FLEX_CARD_WIDTH / size.width.toFloat()
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
                        val k = FLEX_CARD_WIDTH / size.width.toFloat()
                        finger = Offset(change.position.x * k, change.position.y * k)
                        spot = dropSpot(planNow, id, finger.x, finger.y)
                    }
                },
                onDragEnd = { endDrag(true) },
                onDragCancel = { endDrag(false) }
            )
        }
        // Drag a handle on the selected tile's border: resize. Last in the chain, so it sees touches first and
        // leaves the others alone when the touch is not on a handle.
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val id = pickedNow ?: return@awaitEachGesture
                val k = size.width / FLEX_CARD_WIDTH.toFloat()
                val reach = HANDLE_REACH.toPx()
                val border = planNow.dividersOf(id).firstOrNull { d ->
                    (Offset(d.cx * k, d.cy * k) - down.position).getDistance() <= reach
                } ?: return@awaitEachGesture
                down.consume()
                var travelled = 0f
                drag(down.id) { change ->
                    change.consume()
                    val step = change.position - change.previousPosition
                    travelled += (if (border.vertical) step.y else step.x) / k
                    FlexTree.resized(planNow.tree, border, travelled)?.let { layoutNow(it) }
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
                        val target = aimed?.let { s -> plan.boxes.firstOrNull { it.id == s.target } }
                        if (aimed != null && target != null) {
                            // The part of the target the dropped tile will take.
                            val l = target.left
                            val t = target.top
                            val w = target.width
                            val h = target.height
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
                        val sel = if (held == null) picked?.let { plan.box(it) } else null
                        if (sel != null) {
                            drawRoundRect(
                                primary,
                                topLeft = Offset(sel.left * k, sel.top * k),
                                size = Size(sel.width * k, sel.height * k),
                                cornerRadius = radius,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            // Handles on the borders it touches: drag one to resize.
                            val longSide = 36.dp.toPx()
                            val shortSide = 10.dp.toPx()
                            for (d in plan.dividersOf(sel.id)) {
                                val w = if (d.vertical) longSide else shortSide
                                val h = if (d.vertical) shortSide else longSide
                                drawRoundRect(
                                    primary,
                                    topLeft = Offset(d.cx * k - w / 2f, d.cy * k - h / 2f),
                                    size = Size(w, h),
                                    cornerRadius = CornerRadius(shortSide / 2f)
                                )
                            }
                        }
                    }
                )
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
                if (spec.layout != null) {
                    TextButton(onClick = { update { it.copy(layout = null) } }) { Text("Auto layout") }
                }
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

/** How close a touch must be to a border handle to grab it. */
private val HANDLE_REACH = 30.dp
