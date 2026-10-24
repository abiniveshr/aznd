package com.ashrdev.aznd.ui.common.share

import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun FlexViewPage(
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    plan: FlexPlan,
    bottom: Dp,
    onLayout: (FlexNode?) -> Unit,
    onCard: (FlexSpec) -> Unit,
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    onEdit: (Int) -> Unit,
    update: ((FlexSpec) -> FlexSpec) -> Unit,
    reorder: ((FlexSpec) -> FlexSpec) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAutoAlign: () -> Unit,
    onAnotherLayout: () -> Unit,
    onResizeLayout: (FlexNode?) -> Unit,
    onFreeBox: (id: Int, x: Float, y: Float, w: Float, h: Float, continuous: Boolean) -> Unit,
    onLayer: (id: Int, front: Boolean) -> Unit,
    onMode: (FlexLayoutMode) -> Unit,
    aligning: Boolean,
    veiled: Boolean = false
) {
    val mode = spec.style.layoutMode
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var dragging by remember { mutableStateOf<Int?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var spot by remember { mutableStateOf<DropSpot?>(null) }
    var pointing by remember { mutableStateOf<DropSpot?>(null) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val lift = remember { Animatable(0f) }
    var heldBox by remember { mutableStateOf<FlexBlockBox?>(null) }
    var freeAt by remember { mutableStateOf<FlexSnapped?>(null) }

    val planNow by rememberUpdatedState(plan)
    val specNow by rememberUpdatedState(spec)
    val contentNow by rememberUpdatedState(content)
    val assetsNow by rememberUpdatedState(assets)
    val cardNow by rememberUpdatedState(onCard)
    val resizeNow by rememberUpdatedState(onResizeLayout)
    val selectNow by rememberUpdatedState(onSelect)
    val modeNow by rememberUpdatedState(mode)
    val veiledNow by rememberUpdatedState(veiled)
    val freeBoxNow by rememberUpdatedState(onFreeBox)

    var ghost by remember { mutableStateOf<DropGhost?>(null) }
    LaunchedEffect(spot) {
        val aimed = spot
        val tree = aimed?.tree
        if (aimed == null || tree == null) {
            ghost = null
            return@LaunchedEffect
        }
        val sp = specNow
        val ct = contentNow
        val ad = assetsNow
        val made = withContext(Dispatchers.Default) {
            runCatching {
                val next = FlexPainter.withTree(sp, tree)
                val keep = FlexPainter.keeper(sp, ct, ad)
                val p = FlexPainter.plan(next, ct, ad)
                DropGhost(aimed, sp, next, p, keep.holds(next, p))
            }.getOrNull()
        }
        if (made != null && spot === aimed) ghost = made
    }

    val glide = remember { Animatable(1f) }
    var shown by remember { mutableStateOf<Map<Int, Offset>>(emptyMap()) }
    var glideFrom by remember { mutableStateOf<Map<Int, Offset>>(emptyMap()) }
    var resizing by remember { mutableStateOf(false) }
    var gliding by remember { mutableStateOf(false) }
    fun glideAt(): Float = if (gliding) 0f else glide.value
    var letGo by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    fun glideTo(target: FlexPlan) {
        if (resizing) {
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

    val current = ghost?.takeIf { dragging != null && it.base == spec && spot != null }
    val showing = current?.takeIf { it.fits }
    val refusedHere = current?.takeIf { !it.fits && it.spot.sameAs(pointing) } != null
    val glidedTo = remember { arrayOfNulls<FlexPlan>(1) }
    val drawnNow = showing?.plan ?: plan
    SideEffect {
        if (glidedTo[0] !== drawnNow) {
            glidedTo[0] = drawnNow
            glideTo(drawnNow)
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val picked = selectedId?.takeIf { id -> plan.boxes.any { it.id == id } }
    val pickedNow by rememberUpdatedState(picked)
    var barSides by remember { mutableStateOf(emptySet<FlexSide>()) }
    LaunchedEffect(spec, plan, picked, resizing) {
        if (resizing) return@LaunchedEffect
        val id = picked
        barSides = if (id == null || mode == FlexLayoutMode.FREE) emptySet()
        else withContext(Dispatchers.Default) {
            runCatching { FlexPainter.resizeBars(spec, content, assets, plan, id).map { it.side }.toSet() }.getOrDefault(emptySet())
        }
    }

    fun clearAim() {
        settling?.cancel()
        settling = null
        pointing = null
        spot = null
        ghost = null
    }

    fun startDrag(hit: FlexBlockBox, at: Offset) {
        if (veiledNow) return
        dragging = hit.id
        heldBox = hit
        selectNow(hit.id)
        finger = at
        grab = at - Offset(hit.left, hit.top)
        clearAim()
        freeAt = if (modeNow == FlexLayoutMode.FREE) FlexSnapped(hit.cellLeft, hit.cellTop, hit.cellWidth, hit.cellHeight, emptyList(), emptyList()) else null
        scope.launch { lift.animateTo(if (modeNow == FlexLayoutMode.FREE) 0.3f else 1f, spring(stiffness = Spring.StiffnessMedium)) }
    }

    fun moveDrag(id: Int, at: Offset) {
        finger = at
        when (modeNow) {
            FlexLayoutMode.SPLIT -> {
                val next = dropSpot(planNow, id, at.x, at.y)
                val was = pointing
                if (if (next == null) was == null else next.sameAs(was)) return
                pointing = next
                settling?.cancel()
                val held = spot
                if (if (next == null) held == null else next.sameAs(held)) {
                    settling = null
                    return
                }
                settling = scope.launch {
                    delay(DROP_HOVER_MS)
                    if (dragging == id) spot = next
                }
            }
            FlexLayoutMode.FREE -> {
                val box = heldBox ?: return
                freeAt = FlexSnap.move(planNow, id, at.x - grab.x, at.y - grab.y, box.cellWidth, box.cellHeight)
            }
        }
    }

    fun endDrag(apply: Boolean) {
        val id = dragging ?: return
        val base = specNow
        val preview = ghost?.takeIf { it.base == base && spot != null }
        val under = if (modeNow == FlexLayoutMode.SPLIT && apply) dropSpot(planNow, id, finger.x, finger.y) else null
        val aimed = when {
            under == null -> null
            preview != null && preview.fits -> preview.spot
            else -> under
        }
        val tree = aimed?.tree
        val freed = freeAt
        letGo = id to (if (modeNow == FlexLayoutMode.FREE && freed != null) Offset(freed.x, freed.y) else finger - grab)
        dragging = null
        heldBox = null
        freeAt = null
        clearAim()
        when {
            !apply -> glideTo(planNow)
            modeNow == FlexLayoutMode.FREE -> if (freed != null) freeBoxNow(id, freed.x, freed.y, freed.w, freed.h, false) else glideTo(planNow)
            aimed == null || tree == null -> glideTo(planNow)
            else -> {
                val worked = preview?.takeIf { it.spot.sameAs(aimed) }
                val card = worked?.card ?: FlexPainter.withTree(base, tree)
                val fits = worked?.fits ?: runCatching { FlexPainter.holdsAsMuch(base, card, contentNow, assetsNow) }.getOrDefault(false)
                if (fits) {
                    cardNow(card)
                } else {
                    glideTo(planNow)
                    if (worked == null) Toast.makeText(context, FLEX_NO_ROOM, Toast.LENGTH_SHORT).show()
                }
            }
        }
        scope.launch { lift.snapTo(0f) }
    }

    val gestures = Modifier
        .pointerInput(Unit) {
            detectTapGestures { p ->
                val k = planNow.width / size.width.toFloat()
                selectNow(planNow.hit(p.x * k, p.y * k)?.id)
            }
        }
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
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val id = pickedNow ?: return@awaitEachGesture
                if (veiledNow) return@awaitEachGesture
                val k = size.width / planNow.width
                val reach = BAR_REACH.toPx() / k
                val edge = planNow.edgesOf(id).filter { it.side in barSides }.minByOrNull { it.distance(down.position.x / k, down.position.y / k) }
                    ?.takeIf { it.distance(down.position.x / k, down.position.y / k) <= reach }
                if (edge != null && modeNow != FlexLayoutMode.FREE) {
                    down.consume()
                    val border = edge.divider
                    val tree = planNow.tree
                    val start = specNow
                    val keep = FlexPainter.keeper(start, contentNow, assetsNow)
                    val probes = HashMap<Int, Boolean>()
                    fun fits(t: Int): Boolean = probes.getOrPut(t) {
                        runCatching { keep.holds(start.copy(layout = FlexTree.resized(tree, border, t.toFloat()))) }.getOrDefault(false)
                    }
                    var travelled = 0f
                    var placedAt = 0
                    resizing = true
                    try {
                        drag(down.id) { change ->
                            change.consume()
                            val step = change.position - change.previousPosition
                            travelled += (if (border.vertical) step.y else step.x) / k
                            var to = travelled.roundToInt()
                            if (!fits(to)) {
                                var good = placedAt
                                var bad = to
                                while (abs(bad - good) > 1) {
                                    val mid = (good + bad) / 2
                                    if (fits(mid)) good = mid else bad = mid
                                }
                                to = good
                            }
                            if (to != placedAt) {
                                placedAt = to
                                resizeNow(FlexTree.resized(tree, border, to.toFloat()))
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
                        down.consume()
                        var w = box.cellWidth
                        var h = box.cellHeight
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
                    val at = Offset(down.position.x / k, down.position.y / k)
                    if (!box.cellContains(at.x, at.y)) return@awaitEachGesture
                    down.consume()
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
                FlexPreview(
                    spec = showing?.card ?: spec,
                    content = content,
                    assets = assets,
                    plan = drawnNow,
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
                        val hx = if (mode == FlexLayoutMode.FREE && free != null) free.x else finger.x - grab.x
                        val hy = if (mode == FlexLayoutMode.FREE && free != null) free.y else finger.y - grab.y
                        when {
                            id != null -> FlexPoses(
                                offsets, id, hx, hy, lift.value,
                                heldBox = heldBox,
                                landing = if (showing != null) LANDING_ALPHA else 0f
                            )
                            offsets.isNotEmpty() -> FlexPoses(offsets)
                            else -> null
                        }
                    },
                    overlay = { k ->
                        val radius = CornerRadius(10.dp.toPx())
                        val held = dragging?.let { id -> plan.box(id) }
                        val landing = showing?.plan?.box(dragging ?: -1)
                        freeAt?.let { f ->
                            val guide = Color(0xFFFF4FA3)
                            for (gx in f.guidesX) drawLine(guide, Offset(gx * k, 0f), Offset(gx * k, size.height), strokeWidth = 1.5.dp.toPx())
                            for (gy in f.guidesY) drawLine(guide, Offset(0f, gy * k), Offset(size.width, gy * k), strokeWidth = 1.5.dp.toPx())
                        }
                        if (held != null && mode == FlexLayoutMode.SPLIT) {
                            val aimed = pointing
                            when {
                                landing != null -> outlineArea(
                                    floatArrayOf(landing.cellLeft, landing.cellTop, landing.cellWidth, landing.cellHeight), k, primary, 0.14f, radius
                                )
                                else -> {
                                    drawRoundRect(
                                        primary.copy(alpha = 0.7f),
                                        topLeft = Offset(held.left * k, held.top * k),
                                        size = Size(held.width * k, held.height * k),
                                        cornerRadius = radius,
                                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                                    )
                                    if (aimed != null) dropIndicator(aimed, k, if (refusedHere) NO_ROOM_RED else primary, radius)
                                }
                            }
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
                                val thin = 4.dp.toPx()
                                val grip = Size(7.dp.toPx(), 30.dp.toPx())
                                val inset = 10.dp.toPx()
                                for (e in plan.edgesOf(sel.id).filter { it.side in barSides }) {
                                    val across = e.side == FlexSide.TOP || e.side == FlexSide.BOTTOM
                                    val a = Offset(e.x0 * k, e.y0 * k)
                                    val b = Offset(e.x1 * k, e.y1 * k)
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
                                val corner = Offset((sel.left + sel.width) * k - HANDLE_INSET.toPx(), (sel.top + sel.height) * k - HANDLE_INSET.toPx())
                                drawCircle(primary, radius = 11.dp.toPx(), center = corner)
                                drawCircle(Color.White, radius = 4.dp.toPx(), center = corner)
                            }
                        }
                    }
                )
                if (dragging == null) {
                    val history = listOf(
                        FlexMenuAction(Icons.AutoMirrored.Filled.Undo, "Undo", canUndo) { onUndo() },
                        FlexMenuAction(Icons.AutoMirrored.Filled.Redo, "Redo", canRedo) { onRedo() }
                    )
                    val tools = when (mode) {
                        FlexLayoutMode.SPLIT -> {
                            val even = FlexTree.relaxed(plan.tree)
                            listOf(
                                FlexMenuAction(Icons.Default.AutoFixHigh, if (aligning) "Fitting…" else "Best fit", !aligning) { onAutoAlign() },
                                FlexMenuAction(Icons.Default.Shuffle, "Another layout", !aligning && plan.boxes.size > 1) { onAnotherLayout() },
                                FlexMenuAction(Icons.AutoMirrored.Filled.ViewQuilt, "Even out tiles", !aligning && even != plan.tree) { onLayout(even) }
                            )
                        }
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
                            IconButton(onClick = { onLayer(block.id, false) }, enabled = position > 1) {
                                Icon(Icons.Default.FlipToBack, contentDescription = "Send to back")
                            }
                            IconButton(onClick = { onLayer(block.id, true) }, enabled = position < plan.boxes.size) {
                                Icon(Icons.Default.FlipToFront, contentDescription = "Bring to front")
                            }
                        }
                        if (mode == FlexLayoutMode.SPLIT && FlexTree.refOf(plan.tree, block.id) != null) {
                            IconButton(onClick = { FlexTree.turned(plan.tree, block.id)?.let { onLayout(it) } }, enabled = !veiled) {
                                Icon(Icons.Default.Rotate90DegreesCw, contentDescription = "Turn the split")
                            }
                            IconButton(onClick = { FlexTree.flipped(plan.tree, block.id)?.let { onLayout(it) } }, enabled = !veiled) {
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
                    assets = assets,
                    plan = plan,
                    selectedId = selectedId,
                    onSelect = onSelect,
                    onEdit = onEdit,
                    update = update,
                    reorder = reorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pageHeight * 0.32f)
                )
            }
            Spacer(modifier = Modifier.height(bottom))
        }
    }
}

private fun DrawScope.outlineArea(r: FloatArray, k: Float, color: Color, fill: Float, radius: CornerRadius, width: Dp = 2.5.dp) {
    val at = Offset(r[0] * k, r[1] * k)
    val extent = Size(r[2] * k, r[3] * k)
    if (fill > 0f) drawRoundRect(color.copy(alpha = fill), topLeft = at, size = extent, cornerRadius = radius)
    drawRoundRect(color, topLeft = at, size = extent, cornerRadius = radius, style = Stroke(width = width.toPx()))
}

private fun DrawScope.dropIndicator(s: DropSpot, k: Float, color: Color, radius: CornerRadius) {
    when (s.kind) {
        DropKind.GAP, DropKind.EDGE -> {
            if (s.spans) outlineArea(s.region, k, color, 0.08f, radius, 1.5.dp)
            val r = s.bar
            val thick = DROP_BAR.toPx()
            val across = r[2] >= r[3]
            val cx = (r[0] + r[2] / 2f) * k
            val cy = (r[1] + r[3] / 2f) * k
            val extent = if (across) Size(r[2] * k, thick) else Size(thick, r[3] * k)
            drawRoundRect(color, topLeft = Offset(cx - extent.width / 2f, cy - extent.height / 2f), size = extent, cornerRadius = CornerRadius(thick / 2f))
        }
        DropKind.SIDE -> outlineArea(s.bar, k, color, 0.28f, radius)
        DropKind.SWAP -> outlineArea(s.bar, k, color, 0.18f, radius)
    }
}

private class DropGhost(val spot: DropSpot, val base: FlexSpec, val card: FlexSpec, val plan: FlexPlan, val fits: Boolean)

private val NO_ROOM_RED = Color(0xFFE5484D)

private val HANDLE_REACH = 30.dp
private val HANDLE_INSET = 14.dp

private val BAR_REACH = 18.dp

private val DROP_BAR = 6.dp

private const val LANDING_ALPHA = 0.45f

private const val DROP_HOVER_MS = 150L
