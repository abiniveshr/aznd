package com.ashrdev.aznd.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/*
 * Long-press-and-drag reordering for a LazyColumn.
 *
 *   val listState = rememberLazyListState()
 *   val reorder = rememberReorderState(listState, firstItemIndex = 1, itemCount = things.size) { from, to ->
 *       viewModel.move(from, to)          // indexes into `things`
 *   }
 *   LazyColumn(state = listState) {
 *       item(key = "header") { ... }      // anything before the reorderable items: counted by firstItemIndex
 *       items(things, key = { it.id }) { thing ->
 *           ReorderableItem(reorder, thing.id) {
 *               Card(Modifier.dragReorderOnLongPress(reorder, thing.id)) { ... }
 *           }
 *       }
 *   }
 *
 * The list keys must be stable ids (they are how the dragged item is found again after a swap), and
 * [onMove] has to apply the move to the list straight away: while dragging, the state asks for one
 * swap at a time and waits for the list to show it.
 */

@Stable
class ReorderState internal constructor(
    private val listState: LazyListState,
    private val scope: CoroutineScope
) {
    internal var onMove: (from: Int, to: Int) -> Unit = { _, _ -> }
    internal var canMove: ((from: Int, to: Int) -> Boolean)? = null
    /** Lazy-list index of the first reorderable item (items before it are not part of the order). */
    internal var firstIndex: Int = 0
    internal var count: Int = 0
    /** How close to the list's edge (px) a dragged item has to be before the list scrolls. */
    internal var edgePx: Float = 160f

    /** Key of the item being dragged, null when nothing is. */
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    /** Top edge of the dragged item, in list coordinates: it follows the finger, whatever the list does. */
    private var dragTop by mutableStateOf(0f)
    private var settleKey by mutableStateOf<Any?>(null)
    private val settle = Animatable(0f)
    private var staleIndex = -1
    private var loop: Job? = null

    private fun infoOf(key: Any): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    internal fun isHeld(key: Any): Boolean = draggingKey == key || settleKey == key

    /** How far the item is drawn from where the list laid it out. */
    internal fun translationOf(key: Any): Float = when (key) {
        draggingKey -> infoOf(key)?.let { dragTop - it.offset } ?: 0f
        settleKey -> settle.value
        else -> 0f
    }

    internal fun start(key: Any) {
        val info = infoOf(key) ?: return
        loop?.cancel()
        settleKey = null
        draggingKey = key
        dragTop = info.offset.toFloat()
        staleIndex = -1
        // While held, keep scrolling near the edges and keep checking for a swap, even if the finger rests.
        loop = scope.launch {
            while (isActive && draggingKey != null) {
                val step = edgeScroll()
                if (step != 0f) listState.scrollBy(step)
                trySwap()
                delay(16)
            }
        }
    }

    internal fun drag(dy: Float) {
        if (draggingKey == null) return
        dragTop += dy
        trySwap()
    }

    internal fun end() {
        val key = draggingKey ?: return
        val from = translationOf(key)
        loop?.cancel()
        draggingKey = null
        settleKey = key
        scope.launch {
            settle.snapTo(from)
            settle.animateTo(0f, tween(160))
            if (settleKey == key) settleKey = null
        }
    }

    private fun edgeScroll(): Float {
        val key = draggingKey ?: return 0f
        val info = infoOf(key) ?: return 0f
        val layout = listState.layoutInfo
        return when {
            dragTop < layout.viewportStartOffset + edgePx -> -EDGE_SPEED
            dragTop + info.size > layout.viewportEndOffset - edgePx -> EDGE_SPEED
            else -> 0f
        }
    }

    /**
     * Swaps the dragged item with a neighbour once its leading edge has passed that neighbour's middle.
     * (Edge against middle, not middle against bounds, so items of different heights never swap back and forth.)
     */
    private fun trySwap() {
        val key = draggingKey ?: return
        val info = infoOf(key) ?: return
        // The previous swap is not on screen yet; wait for it.
        if (info.index == staleIndex) return
        staleIndex = -1
        val range = firstIndex until firstIndex + count
        val others = listState.layoutInfo.visibleItemsInfo.filter { it.key != key && it.index in range }
        val bottom = dragTop + info.size
        val passed = others
            .filter { it.index > info.index && bottom > it.offset + it.size / 2f }
            .sortedBy { it.index }
            .ifEmpty {
                others
                    .filter { it.index < info.index && dragTop < it.offset + it.size / 2f }
                    .sortedByDescending { it.index }
            }
        val from = info.index - firstIndex
        val allowed = canMove
        val target = passed.firstOrNull { allowed == null || allowed(from, it.index - firstIndex) } ?: return
        staleIndex = info.index
        onMove(from, target.index - firstIndex)
    }

    private companion object {
        const val EDGE_SPEED = 18f
    }
}

/**
 * @param firstItemIndex lazy-list index of the first reorderable item (how many items come before it)
 * @param itemCount number of reorderable items
 * @param onMove called with data indexes (not lazy-list indexes): move the item at [from] to [to]
 */
@Composable
fun rememberReorderState(
    listState: LazyListState,
    firstItemIndex: Int,
    itemCount: Int,
    canMove: ((from: Int, to: Int) -> Boolean)? = null,
    onMove: (from: Int, to: Int) -> Unit
): ReorderState {
    val scope = rememberCoroutineScope()
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
    val state = remember(listState) { ReorderState(listState, scope) }
    SideEffect {
        state.onMove = onMove
        state.canMove = canMove
        state.firstIndex = firstItemIndex
        state.count = itemCount
        state.edgePx = edge
    }
    return state
}

/** Hold, then drag. Put it on the part of the item that should act as the handle (often the whole card). */
fun Modifier.dragReorderOnLongPress(state: ReorderState, key: Any): Modifier =
    pointerInput(state, key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.start(key) },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() }
        )
    }

/**
 * Drag straight away, no hold needed: put it on a hamburger (drag handle) icon. Works alongside
 * [dragReorderOnLongPress] on the item itself.
 */
fun Modifier.dragReorderHandle(state: ReorderState, key: Any): Modifier =
    pointerInput(state, key) {
        detectDragGestures(
            onDragStart = { state.start(key) },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() }
        )
    }

/**
 * Wraps one item of the list: draws it where the finger is while it is dragged, lets the others slide
 * out of the way. Needs Compose foundation 1.7+ for Modifier.animateItem(); on an older version, replace
 * that call with Modifier (items then jump instead of sliding).
 *
 * The modifier chain is the same whether or not the item is held. Swapping modifiers in and out around a
 * drag rebuilds the item's animation state, which is what used to break expand/collapse animations
 * afterwards; instead the held item just gets no placement animation (null spec) for as long as it is held.
 */
@Composable
fun LazyItemScope.ReorderableItem(
    state: ReorderState,
    key: Any,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val active = state.isHeld(key)
    // Lifts off the list while held (bigger, with a shadow) and settles back on drop.
    val lift by animateFloatAsState(if (state.draggingKey == key) 1f else 0f, label = "reorderLift")
    Box(
        modifier = modifier
            .animateItem(placementSpec = if (active) null else spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold))
            .zIndex(if (active) 1f else 0f)
            .graphicsLayer {
                translationY = state.translationOf(key)
                val grow = 1f + 0.03f * lift
                scaleX = grow
                scaleY = grow
                shadowElevation = 28f * lift
                shape = RoundedCornerShape(16.dp)
                clip = false
            }
    ) {
        content()
    }
}
