package com.ashrdev.aznd.ui.dashboard

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.WorkoutWithExercises
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.PrimaryAction
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.common.share.FLEX_DROP_HOVER_MS
import com.ashrdev.aznd.ui.common.tiling.TileNode
import com.ashrdev.aznd.ui.common.tiling.dropSpot
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.navigation.Destination
import com.ashrdev.aznd.ui.streaks.StreakWithCount
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PAGE_TUNING = 0
private const val PAGE_TILING = 1
private const val PAGE_MORE = 2

private val EditPages = listOf(
    BarPage("Tuning", Icons.Default.Tune),
    BarPage("Tiling", Icons.AutoMirrored.Filled.ViewQuilt),
    BarPage("More", Icons.Default.MoreHoriz)
)

// The held tile, as Flex draws its held block (FlexPainter.drawHeld): a touch bigger, on a soft shadow.
private const val LIFT_GROW = 0.04f
private val LiftShadow = 14.dp

// Holding a tile this close to the top or bottom scrolls a tall page, up to EDGE_SPEED dp a frame;
// at the page's end, holding it there FLIP_MS carries the tile to the page above or below.
private val EdgeZone = 64.dp
private const val EDGE_SPEED = 14f
private const val FLIP_MS = 450L

private const val NO_ROOM = "Not enough room there: a tile would be cut off"
private const val PAGE_FULL = "That page is full"

/**
 * Edit mode for the dashboard, opened on page [presetId]. Three swipeable pages, opening on the
 * middle one:   Tuning | Tiling | More
 *  - Tiling: the live tiling. Hold a tile to drag it into a gap or beside another, as in Flex.
 *    Scroll up and down to edit the other pages; hold a tile at the top or bottom edge to carry it
 *    to the page above or below. Tap a tile for its options.
 *  - Tuning (swipe left): the page's widgets and their options.
 *  - More (swipe right): a placeholder for tools to come.
 * A preset that isn't a dashboard page is edited on its own.
 * [focusWidget]: open that widget's options on Tuning ([Destination.DashboardEdit.ADD_WIDGET]: the add picker).
 */
@Composable
fun DashboardEditScreen(
    store: DashboardStore,
    data: DashboardData,
    streaks: List<StreakWithCount>,
    workouts: List<WorkoutWithExercises>,
    modes: List<GraphMode>,
    presetId: Int,
    focusWidget: Int,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    // Opened on a preset that isn't a page: it is edited alone, without the others above and below.
    val standalone = rememberSaveable { presetId !in store.active }
    val pages = if (standalone) listOf(presetId).filter { store.preset(it) != null } else store.active
    if (pages.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val vPager = rememberPagerState(initialPage = pages.indexOf(presetId).coerceAtLeast(0)) { pages.size }
    val hPager = rememberPagerState(initialPage = if (focusWidget == -1) PAGE_TILING else PAGE_TUNING) { EditPages.size }
    val subPages = rememberSubPageBar(hPager, EditPages)
    val scope = rememberCoroutineScope()
    val drag = remember { DashDrag() }

    val currentId = pages.getOrNull(vPager.currentPage) ?: pages.first()
    val current = store.preset(currentId)
    // Which widget's options are open on Tuning (and on which page).
    var expandedPreset by rememberSaveable { mutableIntStateOf(if (focusWidget >= 0) presetId else -1) }
    var expandedWidget by rememberSaveable { mutableIntStateOf(focusWidget.coerceAtLeast(-1)) }
    var adding by rememberSaveable { mutableStateOf(focusWidget == Destination.DashboardEdit.ADD_WIDGET) }
    var resetting by remember { mutableStateOf(false) }
    val full = (current?.widgets?.size ?: 0) >= DashDefaults.MAX_WIDGETS

    BackHandler(enabled = hPager.currentPage != PAGE_TILING) { scope.launch { hPager.animateScrollToPage(PAGE_TILING) } }

    if (adding && current != null) {
        AddWidgetDialog(
            onPick = { type ->
                adding = false
                store.addWidget(current.id, type)?.let { id ->
                    expandedPreset = current.id
                    expandedWidget = id
                }
            },
            onDismiss = { adding = false }
        )
    }
    if (resetting && current != null) {
        AlertDialog(
            onDismissRequest = { resetting = false },
            title = { Text("Reset ${current.name}?") },
            text = { Text("Back to today's calories, your top 2 streaks and your latest workout, arranged automatically.") },
            confirmButton = {
                TextButton(onClick = {
                    resetting = false
                    expandedWidget = -1
                    store.resetPreset(current.id)
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { resetting = false }) { Text("Cancel") } }
        )
    }

    val name = current?.name.orEmpty()
    val where = if (standalone) "not on the dashboard" else "page ${vPager.currentPage + 1} of ${pages.size}"
    ScreenScaffold(
        title = "Edit dashboard",
        pageTitle = { page ->
            when (page) {
                PAGE_TUNING -> "Tuning · $name"
                PAGE_MORE -> "More"
                else -> name
            }
        },
        pageSubtitle = { page -> if (page == PAGE_MORE) null else "Editing · $where" },
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages,
        pageTopBarActions = { page ->
            when (page) {
                PAGE_TUNING -> IconButton(onClick = { resetting = true }) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Reset this page")
                }
                // Back to the tiles arranging themselves (like Flex's Best fit).
                PAGE_TILING -> IconButton(onClick = { store.autoArrange(currentId) }, enabled = current?.layout != null) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = "Auto arrange")
                }
                else -> Unit
            }
        },
        pagePrimaryActions = { page ->
            if (page == PAGE_TUNING) {
                listOf(PrimaryAction(Icons.Default.Add, if (full) "Page is full" else "Add widget", enabled = !full) { adding = true })
            } else {
                emptyList()
            }
        }
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        HorizontalPager(
            state = hPager,
            modifier = Modifier.fillMaxSize(),
            // A held tile owns the finger.
            userScrollEnabled = !drag.active
        ) { page ->
            when (page) {
                PAGE_TUNING -> current?.let { current ->
                    WidgetTuningList(
                        preset = current,
                        streaks = streaks,
                        workouts = workouts,
                        modes = modes,
                        expandedId = if (expandedPreset == current.id) expandedWidget else -1,
                        onExpand = { id ->
                            expandedPreset = current.id
                            expandedWidget = id
                        },
                        onChange = { changed -> store.editWidget(current.id, changed.id) { changed } },
                        onRemove = { id -> store.removeWidget(current.id, id) },
                        onMove = { from, to -> store.moveWidget(current.id, from, to) },
                        bottom = bottom
                    )
                }
                PAGE_MORE -> ComingSoon(bottom)
                else -> TilingPage(
                    store = store,
                    pages = pages,
                    vPager = vPager,
                    data = data,
                    drag = drag,
                    bottom = bottom,
                    onTapWidget = { pid, wid ->
                        expandedPreset = pid
                        expandedWidget = wid
                        scope.launch { hPager.animateScrollToPage(PAGE_TUNING) }
                    }
                )
            }
        }
    }
}

/**
 * The live tiling of every page, stacked vertically. The hold-and-drag is handled here, above the
 * pages, so a held tile can travel from one page to the next. Mirrors FlexViewPage's flow mode:
 * the same drop zones, hover delay, lift, glide and landing preview.
 */
@Composable
private fun TilingPage(
    store: DashboardStore,
    pages: List<Int>,
    vPager: PagerState,
    data: DashboardData,
    drag: DashDrag,
    bottom: Dp,
    onTapWidget: (presetId: Int, widgetId: Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dens = LocalDensity.current
    val look = store.look

    // Where each page's grid is, how it is laid out, and how it scrolls: read by the gestures.
    val grids = remember { HashMap<Int, LayoutCoordinates>() }
    val arranged = remember { HashMap<Int, DashArranged>() }
    val previews = remember { HashMap<Int, suspend (TileNode) -> DashArranged>() }
    val scrolls = remember { HashMap<Int, ScrollState>() }
    val host = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val settling = remember { arrayOfNulls<Job>(1) }

    val pagesNow by rememberUpdatedState(pages)
    val tapNow by rememberUpdatedState(onTapWidget)

    fun currentPreset(): Int? = pagesNow.getOrNull(vPager.currentPage)

    /** A point in the host (px) as a point in page [presetId]'s grid (dp). */
    fun gridPoint(presetId: Int, p: Offset): Offset? {
        val g = grids[presetId]?.takeIf { it.isAttached } ?: return null
        val h = host[0]?.takeIf { it.isAttached } ?: return null
        val local = g.localPositionOf(h, p)
        return Offset(local.x / dens.density, local.y / dens.density)
    }

    fun clearAim() {
        settling[0]?.cancel()
        settling[0] = null
        drag.pointing = null
        drag.spot = null
        drag.ghost = null
    }

    fun startDrag(presetId: Int, widget: DashWidget, cell: DashCell, finger: Offset, at: Offset) {
        drag.presetId = presetId
        drag.id = cell.id
        drag.widget = widget
        drag.held = cell
        drag.finger = finger
        drag.at = at
        drag.grab = at - Offset(cell.x, cell.y)
        drag.dropped = null
        clearAim()
        scope.launch { drag.lift.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
    }

    fun moveDrag(finger: Offset) {
        drag.finger = finger
        val pid = drag.presetId
        val id = drag.id ?: return
        val at = gridPoint(pid, finger) ?: return
        drag.at = at
        val a = arranged[pid] ?: return
        val next = dropSpot(a, id, at.x, at.y, DashboardTileRules)
        val was = drag.pointing
        if (if (next == null) was == null else next.sameAs(was)) return
        drag.pointing = next
        settling[0]?.cancel()
        val held = drag.spot
        if (if (next == null) held == null else next.sameAs(held)) {
            settling[0] = null
            return
        }
        // A new zone only takes over once it has been held a moment, so passing over tiles on the
        // way somewhere doesn't shuffle the whole page.
        settling[0] = scope.launch {
            delay(FLEX_DROP_HOVER_MS)
            if (drag.id == id && drag.presetId == pid) drag.spot = next
        }
    }

    fun endDrag(apply: Boolean) {
        val id = drag.id ?: return
        val pid = drag.presetId
        val base = arranged[pid]
        val previewed = drag.ghost?.takeIf { it.base === base && drag.spot != null }
        val under = if (apply && base != null) dropSpot(base, id, drag.at.x, drag.at.y, DashboardTileRules) else null
        val aimed = when {
            under == null -> null
            previewed != null && previewed.fits -> previewed.spot
            else -> under
        }
        val tree = aimed?.tree
        drag.letGo = DashLetGo(pid, id, drag.at - drag.grab, (drag.letGo?.token ?: 0) + 1)
        drag.id = null
        drag.held = null
        drag.widget = null
        clearAim()
        scope.launch { drag.lift.snapTo(0f) }
        if (aimed == null || tree == null || base == null) return
        val worked = previewed?.takeIf { it.spot.sameAs(aimed) }
        if (worked != null) {
            // Its refusal was already shown in red while it was held there.
            if (worked.fits) {
                drag.dropped = pid to worked.arranged
                store.arrange(pid, tree)
            }
            return
        }
        // Let go before the zone was previewed: check it now.
        val preview = previews[pid] ?: return
        scope.launch {
            val made = runCatching { preview(tree) }.getOrNull() ?: return@launch
            if (made.cuts <= base.cuts) store.arrange(pid, tree)
            else Toast.makeText(context, NO_ROOM, Toast.LENGTH_SHORT).show()
        }
    }

    // The zone held long enough: work out the layout it would leave, off the main thread.
    LaunchedEffect(drag.spot) {
        val aimed = drag.spot
        val tree = aimed?.tree
        if (aimed == null || tree == null) {
            drag.ghost = null
            return@LaunchedEffect
        }
        val pid = drag.presetId
        val base = arranged[pid] ?: return@LaunchedEffect
        val preview = previews[pid] ?: return@LaunchedEffect
        val made = runCatching { preview(tree) }.getOrNull() ?: return@LaunchedEffect
        if (drag.spot === aimed) drag.ghost = DashGhost(aimed, base, made, made.cuts <= base.cuts)
    }

    // Near the top or bottom edge: scroll a tall page, then carry the tile to the next page.
    val holding = drag.active
    LaunchedEffect(holding) {
        if (!holding) return@LaunchedEffect
        val zone = with(dens) { EdgeZone.toPx() }
        val bottomPx = with(dens) { bottom.toPx() }
        var edgeSince = 0L
        var edgeDir = 0
        var warned = false
        while (drag.active) {
            val now = withFrameNanos { it }
            val h = host[0]?.takeIf { it.isAttached } ?: continue
            val y = drag.finger.y
            val height = h.size.height.toFloat()
            val dir = when {
                y > height - bottomPx - zone -> 1
                y < zone -> -1
                else -> 0
            }
            if (dir == 0) {
                edgeDir = 0
                warned = false
                continue
            }
            val pid = drag.presetId
            val sc = scrolls[pid]
            val canScroll = sc != null && (if (dir > 0) sc.canScrollForward else sc.canScrollBackward)
            if (sc != null && canScroll) {
                val depth = if (dir > 0) (y - (height - bottomPx - zone)) / zone else (zone - y) / zone
                sc.scrollBy(dir * depth.coerceIn(0f, 1f) * EDGE_SPEED * dens.density)
                moveDrag(drag.finger)
                edgeDir = 0
                continue
            }
            // At the page's end: hold there a moment to go to the next page.
            if (edgeDir != dir) {
                edgeDir = dir
                edgeSince = now
                continue
            }
            if ((now - edgeSince) / 1_000_000L < FLIP_MS) continue
            val target = vPager.currentPage + dir
            val id = drag.id ?: break
            val toId = pagesNow.getOrNull(target)
            if (toId == null) continue
            val newId = store.carry(pid, id, toId, first = dir > 0)
            if (newId == null) {
                if (!warned) Toast.makeText(context, PAGE_FULL, Toast.LENGTH_SHORT).show()
                warned = true
                continue
            }
            // The tile now belongs to the next page (as its first row going down, its last going up).
            clearAim()
            drag.dropped = null
            drag.presetId = toId
            drag.id = newId
            drag.widget = drag.widget?.copy(id = newId)
            vPager.animateScrollToPage(target)
            edgeSince = withFrameNanos { it }
            moveDrag(drag.finger)
        }
    }

    ProvideDashLook(look) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { host[0] = it }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { p ->
                        val pid = currentPreset() ?: return@detectTapGestures
                        val at = gridPoint(pid, p) ?: return@detectTapGestures
                        arranged[pid]?.hit(at.x, at.y)?.let { tapNow(pid, it.id) }
                    })
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val long = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        val pid = currentPreset() ?: return@awaitEachGesture
                        val at = gridPoint(pid, long.position) ?: return@awaitEachGesture
                        val cell = arranged[pid]?.hit(at.x, at.y) ?: return@awaitEachGesture
                        val widget = store.preset(pid)?.widgets?.firstOrNull { it.id == cell.id } ?: return@awaitEachGesture
                        long.consume()
                        startDrag(pid, widget, cell, long.position, at)
                        try {
                            // Taken before the pages and their scrolling see it (the Initial pass),
                            // so nothing under the finger scrolls while a tile is held.
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == long.id }
                                if (change == null) {
                                    endDrag(false)
                                    break
                                }
                                change.consume()
                                if (!change.pressed) {
                                    endDrag(true)
                                    break
                                }
                                moveDrag(change.position)
                            }
                        } finally {
                            endDrag(false)
                        }
                    }
                }
        ) {
            DashBackground(look)
            VerticalPager(
                state = vPager,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !drag.active,
                beyondViewportPageCount = 1,
                key = { pages.getOrNull(it) ?: (-it - 1) }
            ) { index ->
                val pid = pages.getOrNull(index) ?: return@VerticalPager
                val preset = store.preset(pid) ?: return@VerticalPager
                EditPage(
                    preset = preset,
                    data = data,
                    drag = drag,
                    bottom = bottom,
                    onPlaced = { grids[pid] = it },
                    onArranged = { a, preview ->
                        arranged[pid] = a
                        previews[pid] = preview
                    },
                    onScroll = { scrolls[pid] = it },
                    onGone = {
                        grids.remove(pid)
                        arranged.remove(pid)
                        previews.remove(pid)
                        scrolls.remove(pid)
                    }
                )
            }
            if (pages.size > 1) {
                DashPageDots(
                    count = pages.size,
                    position = { vPager.currentPage + vPager.currentPageOffsetFraction },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp, bottom = bottom)
                )
            }
            // The held tile floats under the finger, over every page, at the size it was picked up.
            val held = drag.held
            val widget = drag.widget
            if (drag.active && held != null && widget != null) {
                DashTile(
                    widget = widget,
                    cell = held,
                    data = data,
                    onOpen = {},
                    onHold = {},
                    interactive = false,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (drag.finger.x - drag.grab.x * density).roundToInt(),
                                (drag.finger.y - drag.grab.y * density).roundToInt()
                            )
                        }
                        .size(held.w.dp, held.h.dp)
                        .graphicsLayer {
                            val l = drag.lift.value
                            scaleX = 1f + LIFT_GROW * l
                            scaleY = 1f + LIFT_GROW * l
                            shadowElevation = LiftShadow.toPx() * l
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(look.tileRadius.dp)
                            clip = false
                        }
                )
            }
        }
    }
}

/** One page of the live tiling. Reports its grid, layout and scroll to [TilingPage]'s gestures. */
@Composable
private fun EditPage(
    preset: DashPreset,
    data: DashboardData,
    drag: DashDrag,
    bottom: Dp,
    onPlaced: (LayoutCoordinates) -> Unit,
    onArranged: (DashArranged, suspend (TileNode) -> DashArranged) -> Unit,
    onScroll: (ScrollState) -> Unit,
    onGone: () -> Unit
) {
    val look = LocalDashLook.current
    val scroll = rememberScrollState()
    DisposableEffect(Unit) { onDispose(onGone) }
    SideEffect { onScroll(scroll) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = (maxWidth - look.side.dp * 2).value
        val viewport = (maxHeight - look.top.dp - bottom).value.coerceAtLeast(DashMetrics.GAP * 10)
        val arrangement = rememberDashArrangement(preset.widgets, preset.layout, data, width, viewport)
        val a = arrangement.arranged ?: return@BoxWithConstraints
        SideEffect { onArranged(a, arrangement.preview) }
        DashGrid(
            a = a,
            widgets = preset.widgets,
            data = data,
            viewport = viewport,
            bottomPadding = bottom,
            scroll = scroll,
            editing = true,
            drag = drag,
            presetId = preset.id,
            onGridPlaced = onPlaced
        )
        if (preset.widgets.isEmpty()) {
            Text(
                "${preset.name} is empty. Add widgets on Tuning, or carry a tile here from the page above or below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp)
            )
        }
    }
}

@Composable
private fun ComingSoon(bottom: Dp) {
    Box(Modifier.fillMaxSize().padding(bottom = bottom), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("More editing tools are coming here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
