package com.ashrdev.aznd.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.share.FLEX_GLIDE_MS
import com.ashrdev.aznd.ui.common.share.FLEX_LANDING_ALPHA
import com.ashrdev.aznd.ui.common.share.NO_ROOM_RED
import com.ashrdev.aznd.ui.common.share.dropIndicator
import com.ashrdev.aznd.ui.common.share.outlineArea
import com.ashrdev.aznd.ui.common.tiling.DropSpot
import com.ashrdev.aznd.ui.common.tiling.TileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/** A page's tiles as laid out now, and how to lay them out for a dropped tiling (for previews). */
class DashArrangement(val arranged: DashArranged?, val preview: suspend (TileNode) -> DashArranged)

/**
 * Lays out one dashboard page's [widgets] in [width] x [viewport] dp, off the main thread and
 * cached, under the look in [LocalDashLook]. [layout]: a hand-made tiling, or null to auto-arrange.
 */
@Composable
fun rememberDashArrangement(
    widgets: List<DashWidget>,
    layout: TileNode?,
    data: DashboardData,
    width: Float,
    viewport: Float
): DashArrangement {
    val look = LocalDashLook.current
    val measure = rememberDashMeasure()
    val scale = look.moduleScale
    val gap = look.gap
    val tiles = remember(widgets, data, measure, scale) {
        widgets.map { it.id to DashboardText.shape(it, data, measure, scale) }
    }
    val signature = remember(widgets, data) { widgets.joinToString("~") { DashboardText.signature(it, data) } }
    // A hand-made tiling doesn't depend on the list order, so neither does its key: a drop's
    // preview and the page after the drop share one cached layout.
    val byIdSignature = remember(widgets, data) {
        widgets.sortedBy { it.id }.joinToString("~") { DashboardText.signature(it, data) }
    }
    val tail = "@$width@$viewport@${look.sizing}"
    val keyOf: (TileNode?) -> String = { tree -> if (tree == null) "auto|$signature$tail" else "$tree|$byIdSignature$tail" }
    val cacheKey = keyOf(layout)
    val produced by produceState(DashboardLayout.cached(cacheKey), cacheKey) {
        value = withContext(Dispatchers.Default) { DashboardLayout.arrangeCached(cacheKey, tiles, width, viewport, gap, layout) }
    }
    // Read the cache right away as well: after a drop its preview is already worked out, so the
    // tiles land without a frame back in their old places.
    val arranged = DashboardLayout.cached(cacheKey) ?: produced
    val preview: suspend (TileNode) -> DashArranged = { tree ->
        val key = keyOf(tree)
        withContext(Dispatchers.Default) { DashboardLayout.arrangeCached(key, tiles, width, viewport, gap, tree) }
    }
    return DashArrangement(arranged, preview)
}

/** The layout a drop would leave, worked out for [spot] on top of [base]. [fits]: nothing more is cut off. */
class DashGhost(val spot: DropSpot, val base: DashArranged, val arranged: DashArranged, val fits: Boolean)

/** Tile [id] of preset [presetId] was let go at [from] (dp, in its grid): it flies from there to its place. */
class DashLetGo(val presetId: Int, val id: Int, val from: Offset, val token: Int)

/**
 * The tile being dragged in edit mode, mirroring FlexViewPage's flow mode. It belongs to one page
 * ([presetId]) at a time; carrying it to another page moves it there under a new [id].
 */
@Stable
class DashDrag {
    var presetId by mutableIntStateOf(-1)
    var id by mutableStateOf<Int?>(null)
    var widget by mutableStateOf<DashWidget?>(null)
    /** The tile as it was picked up: the size it floats at. */
    var held by mutableStateOf<DashCell?>(null)
    /** The finger, in px within the edit host. */
    var finger by mutableStateOf(Offset.Zero)
    /** The finger in its page's grid (dp), and where it holds the tile (dp from the tile's top left). */
    var at by mutableStateOf(Offset.Zero)
    var grab by mutableStateOf(Offset.Zero)
    var pointing by mutableStateOf<DropSpot?>(null)
    var spot by mutableStateOf<DropSpot?>(null)
    var ghost by mutableStateOf<DashGhost?>(null)
    /** The layout a drop left, shown until the saved one catches up. */
    var dropped by mutableStateOf<Pair<Int, DashArranged>?>(null)
    var letGo by mutableStateOf<DashLetGo?>(null)
    val lift = Animatable(0f)

    val active: Boolean get() = id != null
}

private val OutlineRadius = 12.dp

/**
 * One dashboard page's tiles, gliding to their places on Flex's timing. Gestures belong to the
 * caller ([modifier] sits outside the scrolling). In edit mode ([editing]) every tile is outlined,
 * and while [drag] holds one of this page's tiles its drop is previewed as in Flex.
 */
@Composable
fun DashGrid(
    a: DashArranged,
    widgets: List<DashWidget>,
    data: DashboardData,
    viewport: Float,
    bottomPadding: Dp,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    drag: DashDrag? = null,
    presetId: Int = -1,
    onGridPlaced: ((LayoutCoordinates) -> Unit)? = null
) {
    val look = LocalDashLook.current
    val byId = widgets.associateBy { it.id }
    val primary = MaterialTheme.colorScheme.primary

    val mine = drag?.takeIf { it.presetId == presetId }
    val holding = mine?.id
    val current = mine?.ghost?.takeIf { holding != null && it.base === a && mine?.spot != null }
    val showing = current?.takeIf { it.fits }
    val refusedHere = mine != null && current != null && !current.fits && current.spot.sameAs(mine.pointing)
    val dropped = mine?.dropped?.takeIf { it.first == presetId }?.second
    val drawn = showing?.arranged ?: dropped ?: a
    // The saved layout caught up with the drop.
    LaunchedEffect(a) {
        val d = drag
        if (d != null && d.dropped?.first == presetId) d.dropped = null
    }

    val content = max(drawn.height, 0f)
    // The page grows and shrinks on the tiles' glide, as Flex's Free-size card does.
    val shownHeight = animateFloatAsState(content, tween(FLEX_GLIDE_MS), label = "dashHeight")
    Box(
        modifier
            .fillMaxSize()
            .verticalScroll(scroll, enabled = content > viewport + 1f && drag?.active != true)
            .padding(start = look.side.dp, end = look.side.dp, top = look.top.dp, bottom = bottomPadding)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .layout { m, c ->
                    val h = shownHeight.value.dp.roundToPx().coerceAtLeast(0)
                    val p = m.measure(c.copy(minHeight = h, maxHeight = h))
                    layout(p.width, h) { p.place(0, 0) }
                }
                .then(if (onGridPlaced != null) Modifier.onGloballyPositioned(onGridPlaced) else Modifier)
        ) {
            for (cell in drawn.cells) {
                val widget = byId[cell.id] ?: continue
                key(cell.id) {
                    PlacedTile(
                        widget = widget,
                        cell = cell,
                        data = data,
                        // The held tile's place: empty, or a faint copy where the previewed drop lands it.
                        opacity = if (cell.id == holding) (if (showing != null) FLEX_LANDING_ALPHA else 0f) else 1f,
                        letGo = drag?.letGo?.takeIf { it.presetId == presetId && it.id == cell.id }
                    )
                }
            }
            if (editing) {
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            val k = density
                            val radius = CornerRadius(OutlineRadius.toPx())
                            if (holding == null) {
                                // Every tile outlined, so it reads as movable.
                                for (c in drawn.cells) {
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
                            val held = a.cell(holding)
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
                                    mine?.pointing?.let { dropIndicator(it, k, if (refusedHere) NO_ROOM_RED else primary, radius) }
                                }
                            }
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

/** Which dashboard page is showing: a dot per page, the highlight sliding with the scroll. */
@Composable
fun DashPageDots(count: Int, position: () -> Float, modifier: Modifier = Modifier) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Canvas(modifier.size(DotSize, DotSize * count + DotGap * (count - 1).coerceAtLeast(0))) {
        val step = (DotSize + DotGap).toPx()
        val r = DotSize.toPx() / 2f
        for (i in 0 until count) drawCircle(off, r * 0.7f, Offset(r, r + i * step))
        val p = position().coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat())
        drawCircle(on, r, Offset(r, r + p * step))
    }
}

private val DotSize = 7.dp
private val DotGap = 6.dp
