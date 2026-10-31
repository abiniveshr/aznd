package com.ashrdev.aznd.ui.home

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.share.FLEX_DROP_HOVER_MS
import com.ashrdev.aznd.ui.common.share.FLEX_GLIDE_MS
import com.ashrdev.aznd.ui.common.share.FLEX_LANDING_ALPHA
import com.ashrdev.aznd.ui.common.share.NO_ROOM_RED
import com.ashrdev.aznd.ui.common.share.dropIndicator
import com.ashrdev.aznd.ui.common.share.outlineArea
import com.ashrdev.aznd.ui.common.tiling.DropSpot
import com.ashrdev.aznd.ui.common.tiling.TileNode
import com.ashrdev.aznd.ui.common.tiling.dropSpot
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.dashboard.DashArranged
import com.ashrdev.aznd.ui.dashboard.DashCell
import com.ashrdev.aznd.ui.dashboard.DashMetrics
import com.ashrdev.aznd.ui.dashboard.DashTile
import com.ashrdev.aznd.ui.dashboard.DashWidget
import com.ashrdev.aznd.ui.dashboard.DashboardData
import com.ashrdev.aznd.ui.dashboard.DashboardLayout
import com.ashrdev.aznd.ui.dashboard.DashboardTargets
import com.ashrdev.aznd.ui.dashboard.DashboardText
import com.ashrdev.aznd.ui.dashboard.DashboardTileRules
import com.ashrdev.aznd.ui.dashboard.rememberDashMeasure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

private val Side = 16.dp
private val Top = 12.dp

// The held tile, as Flex draws its held block (FlexPainter.drawHeld): a touch bigger, on a soft shadow.
private const val LIFT_GROW = 0.04f
private val LiftShadow = 14.dp

// Holding a tile this close to the top or bottom of the page scrolls it, up to EDGE_SPEED dp a frame.
private val EdgeScroll = 64.dp
private const val EDGE_SPEED = 14f

private val OutlineRadius = 12.dp
private const val NO_ROOM = "Not enough room there: a tile would be cut off"

/**
 * The dashboard. Holding a tile puts it in edit mode ([editing]) and lifts the tile under the finger:
 * it then drops into gaps and beside other tiles the way Flex's flow mode does, with the same
 * timings. The drop is saved through [onLayout] as a hand-made tiling ([layout]).
 */
@Composable
fun DashboardPage(
    widgets: List<DashWidget>,
    layout: TileNode?,
    data: DashboardData,
    editing: Boolean,
    onStartEditing: () -> Unit,
    onLayout: (TileNode) -> Unit,
    onNavigate: (String) -> Unit,
    onOpenSettings: (Int) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    if (widgets.isEmpty()) {
        EmptyDashboard(onOpenSettings, bottomPadding, modifier)
        return
    }
    val measure = rememberDashMeasure()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = (maxWidth - Side * 2).value
        val viewport = (maxHeight - Top - bottomPadding).value.coerceAtLeast(DashMetrics.GAP * 10)
        val tiles = remember(widgets, data, measure) { widgets.map { it.id to DashboardText.shape(it, data, measure) } }
        val signature = remember(widgets, data) { widgets.joinToString("~") { DashboardText.signature(it, data) } }
        // A hand-made tiling doesn't depend on the list order, so neither does its key: the preview
        // of a drop and the page after it share one cached layout.
        val byIdSignature = remember(widgets, data) {
            widgets.sortedBy { it.id }.joinToString("~") { DashboardText.signature(it, data) }
        }
        val keyOf: (TileNode?) -> String = { tree ->
            if (tree == null) "auto|$signature@$width@$viewport" else "$tree|$byIdSignature@$width@$viewport"
        }
        val cacheKey = keyOf(layout)
        val produced by produceState(DashboardLayout.cached(cacheKey), cacheKey) {
            value = withContext(Dispatchers.Default) {
                DashboardLayout.arrangeCached(cacheKey, tiles, width, viewport, DashMetrics.GAP, layout)
            }
        }
        // Read the cache right away as well: after a drop its preview is already worked out, so the
        // tiles land without a frame back in their old places.
        val arranged = DashboardLayout.cached(cacheKey) ?: produced
        val preview: suspend (TileNode) -> DashArranged = { tree ->
            val key = keyOf(tree)
            withContext(Dispatchers.Default) { DashboardLayout.arrangeCached(key, tiles, width, viewport, DashMetrics.GAP, tree) }
        }
        arranged?.let { a ->
            DashboardGrid(a, widgets, data, viewport, bottomPadding, editing, preview, onStartEditing, onLayout, onNavigate, onOpenSettings)
        }
    }
}

/** The layout a drop would leave, worked out for [spot] on top of [base]. [fits]: nothing more is cut off. */
private class DashGhost(val spot: DropSpot, val base: DashArranged, val arranged: DashArranged, val fits: Boolean)

/** Tile [id] was let go at [from] (dp, in the grid): it flies from there to its place. */
private class DashLetGo(val id: Int, val from: Offset, val token: Int)

@Composable
private fun DashboardGrid(
    a: DashArranged,
    widgets: List<DashWidget>,
    data: DashboardData,
    viewport: Float,
    bottomPadding: Dp,
    editing: Boolean,
    preview: suspend (TileNode) -> DashArranged,
    onStartEditing: () -> Unit,
    onLayout: (TileNode) -> Unit,
    onNavigate: (String) -> Unit,
    onOpenSettings: (Int) -> Unit
) {
    val byId = widgets.associateBy { it.id }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dens = LocalDensity.current
    val scroll = rememberScrollState()
    val primary = MaterialTheme.colorScheme.primary
    val tileShape = CardDefaults.shape

    // Drag state, in dp within the grid. Mirrors FlexViewPage's flow mode.
    var dragging by remember { mutableStateOf<Int?>(null) }
    var heldCell by remember { mutableStateOf<DashCell?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var pointing by remember { mutableStateOf<DropSpot?>(null) }
    var spot by remember { mutableStateOf<DropSpot?>(null) }
    var settling by remember { mutableStateOf<Job?>(null) }
    var ghost by remember { mutableStateOf<DashGhost?>(null) }
    var dropped by remember { mutableStateOf<DashArranged?>(null) }
    var letGo by remember { mutableStateOf<DashLetGo?>(null) }
    val lift = remember { Animatable(0f) }

    val aNow by rememberUpdatedState(a)
    val byIdNow by rememberUpdatedState(byId)
    val dataNow by rememberUpdatedState(data)
    val editingNow by rememberUpdatedState(editing)
    val previewNow by rememberUpdatedState(preview)
    val startEditingNow by rememberUpdatedState(onStartEditing)
    val layoutNow by rememberUpdatedState(onLayout)
    val navigateNow by rememberUpdatedState(onNavigate)
    val settingsNow by rememberUpdatedState(onOpenSettings)

    // The zone held long enough: work out the layout it would leave, off the main thread.
    LaunchedEffect(spot) {
        val aimed = spot
        val tree = aimed?.tree
        if (aimed == null || tree == null) {
            ghost = null
            return@LaunchedEffect
        }
        val base = aNow
        val made = runCatching { previewNow(tree) }.getOrNull() ?: return@LaunchedEffect
        if (spot === aimed) ghost = DashGhost(aimed, base, made, made.cuts <= base.cuts)
    }
    // The saved layout caught up with the drop.
    LaunchedEffect(a) { dropped = null }

    val current = ghost?.takeIf { dragging != null && it.base === a && spot != null }
    val showing = current?.takeIf { it.fits }
    val refusedHere = current?.takeIf { !it.fits && it.spot.sameAs(pointing) } != null
    val drawn = showing?.arranged ?: dropped ?: a

    fun clearAim() {
        settling?.cancel()
        settling = null
        pointing = null
        spot = null
        ghost = null
    }

    fun startDrag(cell: DashCell, at: Offset) {
        if (!editingNow) startEditingNow()
        dragging = cell.id
        heldCell = cell
        finger = at
        grab = at - Offset(cell.x, cell.y)
        dropped = null
        clearAim()
        scope.launch { lift.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
    }

    fun moveDrag(id: Int, at: Offset) {
        finger = at
        val next = dropSpot(aNow, id, at.x, at.y, DashboardTileRules)
        val was = pointing
        if (if (next == null) was == null else next.sameAs(was)) return
        pointing = next
        settling?.cancel()
        val held = spot
        if (if (next == null) held == null else next.sameAs(held)) {
            settling = null
            return
        }
        // A new zone only takes over once it has been held a moment, so passing over tiles on
        // the way somewhere doesn't shuffle the whole dashboard.
        settling = scope.launch {
            delay(FLEX_DROP_HOVER_MS)
            if (dragging == id) spot = next
        }
    }

    fun endDrag(apply: Boolean) {
        val id = dragging ?: return
        val base = aNow
        val previewed = ghost?.takeIf { it.base === base && spot != null }
        val under = if (apply) dropSpot(base, id, finger.x, finger.y, DashboardTileRules) else null
        val aimed = when {
            under == null -> null
            previewed != null && previewed.fits -> previewed.spot
            else -> under
        }
        val tree = aimed?.tree
        letGo = DashLetGo(id, finger - grab, (letGo?.token ?: 0) + 1)
        dragging = null
        heldCell = null
        clearAim()
        scope.launch { lift.snapTo(0f) }
        if (aimed == null || tree == null) return
        val worked = previewed?.takeIf { it.spot.sameAs(aimed) }
        if (worked != null) {
            // Its refusal was already shown in red while it was held there.
            if (worked.fits) {
                dropped = worked.arranged
                layoutNow(tree)
            }
            return
        }
        // Let go before the zone was previewed: check it now.
        scope.launch {
            val made = runCatching { previewNow(tree) }.getOrNull() ?: return@launch
            if (made.cuts <= base.cuts) layoutNow(tree)
            else Toast.makeText(context, NO_ROOM, Toast.LENGTH_SHORT).show()
        }
    }

    // A tile held near the top or bottom edge scrolls a dashboard taller than the screen.
    LaunchedEffect(dragging) {
        val id = dragging ?: return@LaunchedEffect
        val edge = EdgeScroll.value
        while (dragging == id) {
            withFrameNanos { }
            val atY = finger.y + Top.value - scroll.value / dens.density
            val bottom = Top.value + viewport
            val depth = when {
                atY > bottom - edge -> (atY - (bottom - edge)) / edge
                atY < edge -> -(edge - atY) / edge
                else -> 0f
            }
            if (depth == 0f) continue
            val used = scroll.scrollBy(depth.coerceIn(-1f, 1f) * EDGE_SPEED * dens.density)
            if (used != 0f && dragging == id) moveDrag(id, finger + Offset(0f, used / dens.density))
        }
    }

    val content = max(drawn.height, 0f)
    // The page grows and shrinks on the tiles' glide, as Flex's Free-size card does.
    val shownHeight = animateFloatAsState(content, tween(FLEX_GLIDE_MS), label = "dashHeight")
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll, enabled = content > viewport + 1f && dragging == null)
            .padding(start = Side, end = Side, top = Top, bottom = bottomPadding)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .layout { m, c ->
                    val h = shownHeight.value.dp.roundToPx().coerceAtLeast(0)
                    val p = m.measure(c.copy(minHeight = h, maxHeight = h))
                    layout(p.width, h) { p.place(0, 0) }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        // Taken here only so a hold never ends as a tap: the drag below lifts the tile.
                        onLongPress = { },
                        onTap = { p ->
                            val cell = aNow.hit(p.x / density, p.y / density) ?: return@detectTapGestures
                            val w = byIdNow[cell.id] ?: return@detectTapGestures
                            if (editingNow) settingsNow(w.id) else navigateNow(DashboardTargets.route(w, dataNow))
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { p ->
                            val at = Offset(p.x / density, p.y / density)
                            aNow.hit(at.x, at.y)?.let { startDrag(it, at) }
                        },
                        onDrag = { change, _ ->
                            val id = dragging
                            if (id != null) {
                                change.consume()
                                moveDrag(id, Offset(change.position.x / density, change.position.y / density))
                            }
                        },
                        onDragEnd = { endDrag(true) },
                        onDragCancel = { endDrag(false) }
                    )
                }
        ) {
            val holding = dragging
            for (cell in drawn.cells) {
                val widget = byId[cell.id] ?: continue
                key(cell.id) {
                    PlacedTile(
                        widget = widget,
                        cell = cell,
                        data = data,
                        // The held tile's place: empty, or a faint copy where the previewed drop lands it.
                        opacity = if (cell.id == holding) (if (showing != null) FLEX_LANDING_ALPHA else 0f) else 1f,
                        letGo = letGo?.takeIf { it.id == cell.id }
                    )
                }
            }

            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val k = density
                        val radius = CornerRadius(OutlineRadius.toPx())
                        val held = heldCell
                        if (holding == null) {
                            // Edit mode: every tile outlined, so it reads as movable.
                            if (editing) for (c in drawn.cells) {
                                drawRoundRect(
                                    primary.copy(alpha = 0.35f),
                                    topLeft = Offset(c.x * k, c.y * k),
                                    size = Size(c.w * k, c.h * k),
                                    cornerRadius = radius,
                                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                                )
                            }
                            return@drawBehind
                        }
                        val landing = showing?.arranged?.cell(holding)
                        when {
                            landing != null -> outlineArea(
                                floatArrayOf(landing.x, landing.y, landing.w, landing.h), k, primary, 0.14f, radius
                            )
                            held != null -> {
                                drawRoundRect(
                                    primary.copy(alpha = 0.7f),
                                    topLeft = Offset(held.x * k, held.y * k),
                                    size = Size(held.w * k, held.h * k),
                                    cornerRadius = radius,
                                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                                )
                                pointing?.let { dropIndicator(it, k, if (refusedHere) NO_ROOM_RED else primary, radius) }
                            }
                        }
                    }
            )

            // The held tile floats under the finger, on top of everything, at the size it was picked up.
            val held = heldCell
            val heldWidget = holding?.let { byId[it] }
            if (held != null && heldWidget != null) {
                DashTile(
                    widget = heldWidget,
                    cell = held,
                    data = data,
                    onOpen = {},
                    onHold = {},
                    interactive = false,
                    modifier = Modifier
                        .offset { IntOffset((finger.x - grab.x).dp.roundToPx(), (finger.y - grab.y).dp.roundToPx()) }
                        .size(held.w.dp, held.h.dp)
                        .graphicsLayer {
                            val l = lift.value
                            scaleX = 1f + LIFT_GROW * l
                            scaleY = 1f + LIFT_GROW * l
                            shadowElevation = LiftShadow.toPx() * l
                            shape = tileShape
                            clip = false
                        }
                )
            }
        }
    }
}

/**
 * One tile, gliding to its cell on Flex's timing ([FLEX_GLIDE_MS]). Its position and size are
 * applied while laying out, so a glide redraws without recomposing the tile.
 */
@Composable
private fun PlacedTile(
    widget: DashWidget,
    cell: DashCell,
    data: DashboardData,
    opacity: Float,
    letGo: DashLetGo?
) {
    val x = remember { Animatable(cell.x) }
    val y = remember { Animatable(cell.y) }
    val w = remember { Animatable(cell.w) }
    val h = remember { Animatable(cell.h) }
    // A tile shown for the first time never flies in from an old drop.
    var flown by remember { mutableIntStateOf(letGo?.token ?: 0) }
    LaunchedEffect(cell.x, cell.y, letGo?.token) {
        val from = letGo
        if (from != null && from.token != flown) {
            flown = from.token
            x.snapTo(from.from.x)
            y.snapTo(from.from.y)
        }
        launch { x.animateTo(cell.x, tween(FLEX_GLIDE_MS)) }
        launch { y.animateTo(cell.y, tween(FLEX_GLIDE_MS)) }
    }
    LaunchedEffect(cell.w, cell.h) {
        launch { w.animateTo(cell.w, tween(FLEX_GLIDE_MS)) }
        launch { h.animateTo(cell.h, tween(FLEX_GLIDE_MS)) }
    }
    DashTile(
        widget = widget,
        cell = cell,
        data = data,
        onOpen = {},
        onHold = {},
        interactive = false,
        modifier = Modifier
            .offset { IntOffset(x.value.dp.roundToPx(), y.value.dp.roundToPx()) }
            .layout { m, c ->
                val pw = w.value.dp.roundToPx().coerceIn(c.minWidth, c.maxWidth)
                val ph = h.value.dp.roundToPx().coerceIn(c.minHeight, c.maxHeight)
                val p = m.measure(Constraints.fixed(pw, ph))
                layout(pw, ph) { p.place(0, 0) }
            }
            .graphicsLayer { alpha = opacity }
    )
}

@Composable
private fun EmptyDashboard(onOpenSettings: (Int) -> Unit, bottomPadding: Dp, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(bottom = bottomPadding), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Your dashboard is empty", style = MaterialTheme.typography.titleMedium)
            Text(
                "Add calories, streaks, graphs, shortcuts and more.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = { onOpenSettings(-1) }) { Text("Add widgets") }
        }
    }
}
