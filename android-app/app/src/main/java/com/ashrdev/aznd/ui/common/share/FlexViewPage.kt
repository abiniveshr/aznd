package com.ashrdev.aznd.ui.common.share

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Moves the block [fromId] to where block [toId] is, shifting the ones between. */
internal fun moveBlock(spec: FlexSpec, fromId: Int, toId: Int): FlexSpec {
    val from = spec.blocks.indexOfFirst { it.id == fromId }
    val to = spec.blocks.indexOfFirst { it.id == toId }
    if (from < 0 || to < 0 || from == to) return spec
    return spec.copy(blocks = spec.blocks.toMutableList().also { it.add(to, it.removeAt(from)) })
}

/**
 * The right-hand page: the card as large as the page allows, and a first piece of on-card editing. Tap a
 * block to select it; hold it and drag over another block to rearrange. The order is the Content list's
 * order, so both always agree. (This page is where the fuller canvas editor will grow.)
 */
@Composable
internal fun FlexViewPage(
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    plan: FlexPlan,
    bottom: Dp,
    onMoveBlock: (fromId: Int, toId: Int) -> Unit
) {
    var selected by remember { mutableStateOf<Int?>(null) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    val planNow by rememberUpdatedState(plan)
    val moveNow by rememberUpdatedState(onMoveBlock)
    val primary = MaterialTheme.colorScheme.primary
    // A selection that has left the card (turned off, hidden) is forgotten.
    val picked = selected?.takeIf { id -> plan.boxes.any { it.id == id } }

    val gestures = Modifier
        .pointerInput(Unit) {
            detectTapGestures { p ->
                val k = FLEX_CARD_WIDTH / size.width.toFloat()
                selected = planNow.hit(p.x * k, p.y * k)?.id
            }
        }
        .pointerInput(Unit) {
            // After each swap, wait for the card to be laid out again before looking for the next one.
            var waitingFor: FlexPlan? = null
            detectDragGesturesAfterLongPress(
                onDragStart = { p ->
                    val k = FLEX_CARD_WIDTH / size.width.toFloat()
                    val hit = planNow.hit(p.x * k, p.y * k)
                    dragging = hit?.id
                    if (hit != null) selected = hit.id
                    waitingFor = null
                },
                onDrag = { change, _ ->
                    change.consume()
                    val id = dragging
                    if (id != null && waitingFor !== planNow) {
                        val k = FLEX_CARD_WIDTH / size.width.toFloat()
                        val fx = change.position.x * k
                        val fy = change.position.y * k
                        val boxes = planNow.boxes
                        val mine = boxes.indexOfFirst { it.id == id }
                        val target = planNow.hit(fx, fy)
                        if (mine >= 0 && target != null && target.id != id) {
                            val theirs = boxes.indexOfFirst { it.id == target.id }
                            val middle = target.top + target.height / 2f
                            // Edge past the middle, so blocks of different heights never swap back and forth.
                            val passed = if (theirs > mine) fy > middle else fy < middle
                            if (passed) {
                                waitingFor = planNow
                                moveNow(id, target.id)
                            }
                        }
                    }
                },
                onDragEnd = {
                    dragging = null
                    waitingFor = null
                },
                onDragCancel = {
                    dragging = null
                    waitingFor = null
                }
            )
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
                overlay = { k ->
                    val id = dragging ?: picked
                    plan.boxes.firstOrNull { it.id == id }?.let { b ->
                        val topLeft = Offset(b.left * k, b.top * k)
                        val boxSize = Size(b.width * k, b.height * k)
                        val radius = CornerRadius(10.dp.toPx())
                        if (dragging != null) drawRoundRect(primary.copy(alpha = 0.22f), topLeft, boxSize, radius)
                        drawRoundRect(primary, topLeft, boxSize, radius, style = Stroke(width = 2.5.dp.toPx()))
                    }
                }
            )
        }
        val label = picked?.let { id -> spec.blocks.firstOrNull { it.id == id }?.type?.label }
        val position = plan.boxes.indexOfFirst { it.id == picked } + 1
        Text(
            text = if (label != null) {
                "$label · $position of ${plan.boxes.size}. Hold it and drag to move it."
            } else {
                "Tap a block to select it. Hold and drag to rearrange."
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(bottom))
    }
}
