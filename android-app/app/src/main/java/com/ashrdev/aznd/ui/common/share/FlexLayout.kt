package com.ashrdev.aznd.ui.common.share

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

sealed interface FlexNode

data class FlexLeaf(val id: Int) : FlexNode

data class FlexGroup(val vertical: Boolean, val children: List<FlexNode>, val weights: List<Float>) : FlexNode {
    val isAuto: Boolean get() = weights.all { it <= 0f }
}

enum class DropZone { LEFT, RIGHT, TOP, BOTTOM, SWAP }

enum class DropKind { GAP, EDGE, SIDE, SWAP }

class DropSpot(
    val target: Int,
    val zone: DropZone,
    val span: Set<Int> = setOf(target),
    val region: FloatArray = FloatArray(4),
    val tree: FlexNode? = null,
    val kind: DropKind = DropKind.SIDE,
    val bar: FloatArray = region
) {
    val spans: Boolean get() = span.size > 1

    fun sameAs(other: DropSpot?): Boolean =
        other != null && other.kind == kind && other.zone == zone && other.target == target && other.span == span && other.tree == tree
}

class FlexDivider(
    val path: List<Int>,
    val index: Int,
    val vertical: Boolean,
    val cx: Float,
    val cy: Float,
    val sizes: List<Float>,
    val adjacent: Set<Int>
)

class FlexGroupRef(val path: List<Int>, val index: Int, val group: FlexGroup)

class FlexSolved(
    val rects: Map<Int, FloatArray>,
    val dividers: List<FlexDivider>,
    val groupSizes: Map<List<Int>, List<Float>>,
    val levels: Map<Int, Float> = emptyMap(),
    val zoom: Float = 1f
)

const val MIN_TILE = 140f

private const val SNAP = 0.03f
private const val SPLIT_LOW = 0.2f
private const val SPLIT_HIGH = 0.8f
private const val SPLIT_STEPS = 16
private const val BIG = 1e9f

const val DROP_GAP_REACH = 40f
private const val ROW_GROW = 1.3f
private const val SWAP_SHARE = 0.18f

object FlexTree {

    fun leaves(node: FlexNode?): List<Int> = when (node) {
        null -> emptyList()
        is FlexLeaf -> listOf(node.id)
        is FlexGroup -> node.children.flatMap { leaves(it) }
    }

    fun banner(node: FlexNode?): Int? = when (node) {
        null -> null
        is FlexLeaf -> node.id
        is FlexGroup -> if (node.vertical) banner(node.children.firstOrNull()) else null
    }

    fun prune(node: FlexNode?, keep: Set<Int>): FlexNode? = when (node) {
        null -> null
        is FlexLeaf -> if (node.id in keep) node else null
        is FlexGroup -> {
            val kids = ArrayList<FlexNode>()
            val ws = ArrayList<Float>()
            node.children.forEachIndexed { i, child ->
                prune(child, keep)?.let {
                    kids.add(it)
                    ws.add(node.weights.getOrElse(i) { 0f })
                }
            }
            tidy(node.vertical, kids, ws)
        }
    }

    private fun tidy(vertical: Boolean, kids: List<FlexNode>, rawWeights: List<Float>): FlexNode? {
        if (kids.isEmpty()) return null
        if (kids.size == 1) return kids[0]
        val ws = evened(rawWeights)
        val explicit = ws.any { it > 0f }
        val outKids = ArrayList<FlexNode>()
        val outWs = ArrayList<Float>()
        kids.forEachIndexed { i, child ->
            if (child is FlexGroup && child.vertical == vertical && (explicit || child.isAuto)) {
                if (explicit) {
                    val total = child.weights.sumOf { it.toDouble() }.toFloat()
                    child.children.forEachIndexed { j, grand ->
                        outKids.add(grand)
                        outWs.add(ws[i] * (if (child.isAuto || total <= 0f) 1f / child.children.size else child.weights[j] / total))
                    }
                } else {
                    child.children.forEach { grand ->
                        outKids.add(grand)
                        outWs.add(0f)
                    }
                }
            } else {
                outKids.add(child)
                outWs.add(ws[i])
            }
        }
        return FlexGroup(vertical, outKids, outWs)
    }

    private fun evened(ws: List<Float>): List<Float> {
        val positive = ws.filter { it > 0f }
        if (positive.isEmpty() || positive.size == ws.size) return ws.map { max(it, 0f) }
        val avg = positive.sum() / positive.size
        return ws.map { if (it > 0f) it else avg }
    }

    private fun zeros(n: Int): List<Float> = List(n) { 0f }

    fun autoTree(ids: List<Int>, wide: Boolean): FlexNode? {
        val leaves = ids.map { FlexLeaf(it) }
        return when {
            ids.isEmpty() -> null
            ids.size == 1 -> leaves[0]
            wide && ids.size <= 3 -> FlexGroup(false, leaves, zeros(leaves.size))
            wide -> {
                val half = (leaves.size + 1) / 2
                FlexGroup(false, listOf(column(leaves.take(half)), column(leaves.drop(half))), zeros(2))
            }
            else -> FlexGroup(true, leaves, zeros(leaves.size))
        }
    }

    private fun column(l: List<FlexNode>): FlexNode = if (l.size == 1) l[0] else FlexGroup(true, l, zeros(l.size))

    fun effective(layout: FlexNode?, shown: List<Int>, wide: Boolean): FlexNode? {
        if (shown.isEmpty()) return null
        var tree: FlexNode = prune(layout, shown.toSet()) ?: return autoTree(shown, wide)
        val have = leaves(tree).toMutableSet()
        for ((i, id) in shown.withIndex()) {
            if (id in have) continue
            val before = shown.subList(0, i).lastOrNull { it in have }
            tree = insertRow(tree, id, before)
            have += id
        }
        return tree
    }

    fun append(root: FlexNode, id: Int): FlexNode = insertRow(root, id, leaves(root).lastOrNull())

    private fun insertRow(root: FlexNode, id: Int, after: Int?): FlexNode {
        val leaf = FlexLeaf(id)
        if (root !is FlexGroup || !root.vertical) {
            return if (after == null) FlexGroup(true, listOf(leaf, root), zeros(2)) else FlexGroup(true, listOf(root, leaf), zeros(2))
        }
        val at = if (after == null) 0 else root.children.indexOfFirst { after in leaves(it) }.let { if (it < 0) root.children.size else it + 1 }
        val kids = root.children.toMutableList().also { it.add(at, leaf) }
        val ws = if (root.isAuto) zeros(kids.size) else root.weights.toMutableList().also { it.add(at, root.weights.average().toFloat()) }
        return FlexGroup(true, kids, ws)
    }

    fun moveTo(root: FlexNode?, dragged: Int, target: Int, zone: DropZone): FlexNode? {
        if (root == null || dragged == target) return null
        if (zone == DropZone.SWAP) return evenParent(evenParent(swap(root, dragged, target), dragged), target)
        return moveBeside(root, dragged, setOf(target), zone)
    }

    fun moveBeside(root: FlexNode?, dragged: Int, span: Set<Int>, zone: DropZone): FlexNode? {
        if (root == null || zone == DropZone.SWAP) return null
        val rest = span - dragged
        if (rest.isEmpty()) return null
        val all = leaves(root).toSet()
        if (dragged !in all || !all.containsAll(rest)) return null
        val without = prune(evenParent(root, dragged), all - dragged) ?: return null
        val vertical = zone == DropZone.TOP || zone == DropZone.BOTTOM
        val before = zone == DropZone.LEFT || zone == DropZone.TOP
        val placed = insertAt(without, rest, dragged, vertical, before)
        return prune(placed, all)
    }

    private fun insertAt(node: FlexNode, span: Set<Int>, id: Int, vertical: Boolean, before: Boolean): FlexNode {
        val leaf = FlexLeaf(id)
        fun wrap(n: FlexNode): FlexNode = FlexGroup(vertical, if (before) listOf(leaf, n) else listOf(n, leaf), zeros(2))
        if (leaves(node).toSet() == span) {
            if (node is FlexGroup && node.vertical == vertical) {
                val kids = if (before) listOf(leaf) + node.children else node.children + leaf
                return FlexGroup(vertical, kids, zeros(kids.size))
            }
            return wrap(node)
        }
        if (node !is FlexGroup) return node
        val sets = node.children.map { leaves(it).toSet() }
        for (a in sets.indices) {
            if (!span.containsAll(sets[a])) continue
            val union = HashSet<Int>()
            for (b in a until sets.size) {
                if (!span.containsAll(sets[b])) break
                union.addAll(sets[b])
                if (union == span) {
                    val kids = node.children.toMutableList()
                    val ws = node.weights.toMutableList()
                    if (node.vertical == vertical) {
                        kids.add(if (before) a else b + 1, leaf)
                        return FlexGroup(node.vertical, kids, zeros(kids.size))
                    }
                    val run = FlexGroup(node.vertical, node.children.slice(a..b), node.weights.slice(a..b))
                    val share = node.weights.slice(a..b).sum()
                    for (k in b downTo a) {
                        kids.removeAt(k)
                        ws.removeAt(k)
                    }
                    kids.add(a, if (a == b) wrap(node.children[a]) else wrap(run))
                    ws.add(a, share)
                    return FlexGroup(node.vertical, kids, ws)
                }
            }
        }
        val i = sets.indexOfFirst { it.containsAll(span) }
        if (i < 0) return wrap(node)
        val kids = node.children.toMutableList()
        kids[i] = insertAt(kids[i], span, id, vertical, before)
        return FlexGroup(node.vertical, kids, node.weights)
    }

    private fun evenParent(node: FlexNode, id: Int): FlexNode {
        if (node !is FlexGroup) return node
        if (node.children.any { it is FlexLeaf && it.id == id }) return FlexGroup(node.vertical, node.children, zeros(node.children.size))
        return FlexGroup(node.vertical, node.children.map { evenParent(it, id) }, node.weights)
    }

    fun swap(root: FlexNode, a: Int, b: Int): FlexNode = mapLeaves(root) { id ->
        when (id) {
            a -> b
            b -> a
            else -> id
        }
    }

    private fun mapLeaves(node: FlexNode, f: (Int) -> Int): FlexNode = when (node) {
        is FlexLeaf -> FlexLeaf(f(node.id))
        is FlexGroup -> FlexGroup(node.vertical, node.children.map { mapLeaves(it, f) }, node.weights)
    }

    fun relabel(root: FlexNode, order: List<Int>): FlexNode {
        val sorted = leaves(root).sortedBy { id -> order.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it } }
        var next = 0
        return mapLeaves(root) { sorted[next++] }
    }

    fun refOf(root: FlexNode?, id: Int, path: List<Int> = emptyList()): FlexGroupRef? {
        if (root !is FlexGroup) return null
        root.children.forEachIndexed { i, child ->
            if (child is FlexLeaf && child.id == id) return FlexGroupRef(path, i, root)
            refOf(child, id, path + i)?.let { return it }
        }
        return null
    }

    fun setWeights(node: FlexNode?, path: List<Int>, weights: List<Float>): FlexNode? {
        if (node == null) return null
        val group = node as? FlexGroup ?: return node
        if (path.isEmpty()) {
            if (group.children.size != weights.size) return group
            return FlexGroup(group.vertical, group.children, weights.map { it.coerceAtLeast(0f) })
        }
        val i = path[0]
        if (i !in group.children.indices) return group
        val kids = group.children.toMutableList()
        kids[i] = setWeights(kids[i], path.drop(1), weights) ?: kids[i]
        return FlexGroup(group.vertical, kids, group.weights)
    }

    fun resized(tree: FlexNode?, d: FlexDivider, travelled: Float): FlexNode? {
        if (d.index + 1 >= d.sizes.size) return tree
        val a = d.sizes[d.index]
        val b = d.sizes[d.index + 1]
        val low = -(a - MIN_TILE).coerceAtLeast(0f)
        val high = (b - MIN_TILE).coerceAtLeast(0f)
        var t = travelled.coerceIn(low, high)
        val even = (b - a) / 2f
        if (abs(t - even) < SNAP * (a + b) && even in low..high) t = even
        val sizes = d.sizes.toMutableList()
        sizes[d.index] = a + t
        sizes[d.index + 1] = b - t
        return setWeights(tree, d.path, sizes)
    }

    fun turned(root: FlexNode?, id: Int): FlexNode? = editParent(root, id) { g ->
        FlexGroup(!g.vertical, g.children, zeros(g.children.size))
    }

    fun flipped(root: FlexNode?, id: Int): FlexNode? = editParent(root, id) { g ->
        FlexGroup(g.vertical, g.children.reversed(), g.weights.reversed())
    }

    fun groupPaths(node: FlexNode?, path: List<Int> = emptyList()): List<List<Int>> {
        if (node !is FlexGroup) return emptyList()
        return listOf(path) + node.children.flatMapIndexed { i, c -> groupPaths(c, path + i) }
    }

    fun groupAt(node: FlexNode?, path: List<Int>): FlexGroup? =
        path.fold(node) { n, i -> (n as? FlexGroup)?.children?.getOrNull(i) } as? FlexGroup

    fun relaxed(root: FlexNode?): FlexNode? = when (root) {
        null -> null
        is FlexLeaf -> root
        is FlexGroup -> FlexGroup(root.vertical, root.children.map { relaxed(it)!! }, zeros(root.children.size))
    }

    fun sameShape(a: FlexNode?, b: FlexNode?): Boolean = relaxed(a) == relaxed(b)

    private fun editParent(root: FlexNode?, id: Int, edit: (FlexGroup) -> FlexGroup): FlexNode? {
        val ref = refOf(root, id) ?: return null
        fun at(node: FlexNode, path: List<Int>): FlexNode {
            if (path.isEmpty()) return edit(node as FlexGroup)
            val g = node as FlexGroup
            val kids = g.children.toMutableList()
            kids[path[0]] = at(kids[path[0]], path.drop(1))
            return FlexGroup(g.vertical, kids, g.weights)
        }
        val out = at(root!!, ref.path)
        return prune(out, leaves(out).toSet())
    }
}

internal fun dropSpot(plan: FlexPlan, dragged: Int, x: Float, y: Float): DropSpot? {
    val root = plan.tree ?: return null
    val cells = plan.boxes.associate { it.id to floatArrayOf(it.cellLeft, it.cellTop, it.cellWidth, it.cellHeight) }
    if (cells.size < 2 || dragged !in cells) return null
    val cl = plan.contentLeft
    val ct = plan.contentTop
    val cr = cl + plan.contentWidth
    val cb = ct + plan.contentHeight
    val used = cells.values.maxOf { it[1] + it[3] }

    fun regionOf(ids: Collection<Int>): FloatArray {
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (id in ids) {
            val c = cells[id] ?: continue
            l = min(l, c[0]); t = min(t, c[1]); r = max(r, c[0] + c[2]); b = max(b, c[1] + c[3])
        }
        return floatArrayOf(l, t, r - l, b - t)
    }
    fun regionOf(n: FlexNode): FloatArray = regionOf(FlexTree.leaves(n))

    fun spot(target: Int, zone: DropZone, span: Set<Int>, kind: DropKind, bar: FloatArray): DropSpot? {
        val tree = FlexTree.moveBeside(root, dragged, span, zone) ?: return null
        if (FlexTree.sameShape(tree, root)) return null
        return DropSpot(target, zone, span, regionOf(span - dragged), tree, kind, bar)
    }

    if (x < cl || x > cr || y < ct || y > max(cb, used)) {
        val zone = listOf(
            DropZone.LEFT to cl - x, DropZone.RIGHT to x - cr, DropZone.TOP to ct - y, DropZone.BOTTOM to y - max(cb, used)
        ).maxByOrNull { it.second }!!.first
        val all = FlexTree.leaves(root).toSet()
        val whole = regionOf(all)
        val strip = 8f
        val bar = when (zone) {
            DropZone.LEFT -> floatArrayOf(whole[0] - strip * 2f, whole[1], strip, whole[3])
            DropZone.RIGHT -> floatArrayOf(whole[0] + whole[2] + strip, whole[1], strip, whole[3])
            DropZone.TOP -> floatArrayOf(whole[0], whole[1] - strip * 2f, whole[2], strip)
            else -> floatArrayOf(whole[0], whole[1] + whole[3] + strip, whole[2], strip)
        }
        return spot(all.first { it != dragged }, zone, all, DropKind.EDGE, bar)
    }

    val reach = plan.gap / 2f + DROP_GAP_REACH
    var bestGap: DropSpot? = null
    var bestDist = Float.MAX_VALUE
    var homeGap = false
    fun consider(dist: Float, make: () -> DropSpot?, home: Boolean) {
        if (dist >= bestDist) return
        bestDist = dist
        homeGap = home
        bestGap = if (home) null else make()
    }
    fun walk(n: FlexNode) {
        if (n !is FlexGroup) return
        val rs = n.children.map { regionOf(it) }
        val whole = regionOf(n)
        for (k in 0 until n.children.size - 1) {
            val a = n.children[k]
            val b = n.children[k + 1]
            val line: Float
            val dist: Float
            val inside: Boolean
            val bar: FloatArray
            if (n.vertical) {
                line = (rs[k][1] + rs[k][3] + rs[k + 1][1]) / 2f
                dist = abs(y - line)
                inside = x >= whole[0] && x <= whole[0] + whole[2]
                bar = floatArrayOf(whole[0], line - plan.gap / 2f, whole[2], plan.gap)
            } else {
                line = (rs[k][0] + rs[k][2] + rs[k + 1][0]) / 2f
                dist = abs(x - line)
                inside = y >= whole[1] && y <= whole[1] + whole[3]
                bar = floatArrayOf(line - plan.gap / 2f, whole[1], plan.gap, whole[3])
            }
            if (!inside || dist > reach) continue
            val home = (a is FlexLeaf && a.id == dragged) || (b is FlexLeaf && b.id == dragged)
            val afterA = FlexTree.leaves(a).toSet()
            val beforeB = FlexTree.leaves(b).toSet()
            consider(dist, {
                val zoneAfter = if (n.vertical) DropZone.BOTTOM else DropZone.RIGHT
                val zoneBefore = if (n.vertical) DropZone.TOP else DropZone.LEFT
                if (afterA - dragged != afterA || afterA == setOf(dragged)) {
                    spot(FlexTree.leaves(b).first(), zoneBefore, beforeB, DropKind.GAP, bar)
                } else {
                    spot(FlexTree.leaves(a).last(), zoneAfter, afterA, DropKind.GAP, bar)
                }
            }, home)
        }
        n.children.forEach { walk(it) }
    }
    walk(root)
    val top = FlexTree.leaves(root).toSet()
    val whole = regionOf(top)
    if (root is FlexGroup && root.vertical) {
        val first = root.children.first()
        val last = root.children.last()
        if (y - whole[1] in -reach..reach) {
            consider(abs(y - whole[1]) + 0.01f, {
                spot(FlexTree.leaves(first).first(), DropZone.TOP, FlexTree.leaves(first).toSet(), DropKind.GAP, floatArrayOf(whole[0], whole[1] - plan.gap, whole[2], plan.gap))
            }, first is FlexLeaf && first.id == dragged)
        }
        val bottom = whole[1] + whole[3]
        if (y >= bottom - reach) {
            consider(abs(y - bottom) + 0.01f, {
                spot(FlexTree.leaves(last).last(), DropZone.BOTTOM, FlexTree.leaves(last).toSet(), DropKind.GAP, floatArrayOf(whole[0], bottom, whole[2], plan.gap))
            }, last is FlexLeaf && last.id == dragged)
        }
    }
    if (bestDist < Float.MAX_VALUE) return if (homeGap) null else bestGap

    val under = plan.boxes.firstOrNull { it.cellContains(x, y) } ?: return null
    if (under.id == dragged) return null
    val t = under
    val dx = x - (t.cellLeft + t.cellWidth / 2f)
    val dy = y - (t.cellTop + t.cellHeight / 2f)
    val cell = floatArrayOf(t.cellLeft, t.cellTop, t.cellWidth, t.cellHeight)
    if (abs(dx) < t.cellWidth * SWAP_SHARE && abs(dy) < t.cellHeight * SWAP_SHARE) {
        val tree = FlexTree.moveTo(root, dragged, t.id, DropZone.SWAP) ?: return null
        return DropSpot(t.id, DropZone.SWAP, setOf(t.id), cell, tree, DropKind.SWAP, cell)
    }
    val horizontal = abs(dx) / t.cellWidth > abs(dy) / t.cellHeight
    val zone = if (horizontal) {
        if (dx < 0f) DropZone.LEFT else DropZone.RIGHT
    } else {
        if (dy < 0f) DropZone.TOP else DropZone.BOTTOM
    }
    val half = when (zone) {
        DropZone.LEFT -> floatArrayOf(cell[0], cell[1], cell[2] / 2f, cell[3])
        DropZone.RIGHT -> floatArrayOf(cell[0] + cell[2] / 2f, cell[1], cell[2] / 2f, cell[3])
        DropZone.TOP -> floatArrayOf(cell[0], cell[1], cell[2], cell[3] / 2f)
        else -> floatArrayOf(cell[0], cell[1] + cell[3] / 2f, cell[2], cell[3] / 2f)
    }
    return spot(t.id, zone, setOf(t.id), DropKind.SIDE, half)
}

private fun distribute(total: Float, desired: List<Float>, caps: List<Float>, floors: List<Float>): List<Float> {
    val n = desired.size
    if (n == 0) return emptyList()
    val floorSum = floors.sum()
    if (floorSum > total && floorSum > 0f) return floors.map { it / floorSum * total }
    val out = FloatArray(n)
    val fixed = BooleanArray(n)
    fun clamp(i: Int, v: Float): Float = v.coerceIn(floors[i], max(caps[i], floors[i]))
    for (pass in 0..n) {
        var fixedSum = 0f
        var want = 0f
        for (i in 0 until n) if (fixed[i]) fixedSum += out[i] else want += desired[i]
        val k = if (want > 0f) (total - fixedSum) / want else 0f
        var violation = 0f
        for (i in 0 until n) if (!fixed[i]) violation += clamp(i, desired[i] * k) - desired[i] * k
        if (abs(violation) < 0.01f) break
        var any = false
        for (i in 0 until n) {
            if (fixed[i]) continue
            val v = desired[i] * k
            val c = clamp(i, v)
            if ((violation > 0f && c > v) || (violation < 0f && c < v)) {
                out[i] = c
                fixed[i] = true
                any = true
            }
        }
        if (!any) break
    }
    var fixedSum = 0f
    var want = 0f
    for (i in 0 until n) if (fixed[i]) fixedSum += out[i] else want += desired[i]
    val k = if (want > 0f) (total - fixedSum) / want else 0f
    for (i in 0 until n) if (!fixed[i]) out[i] = desired[i] * k
    return out.toList()
}

private fun fractionsOf(g: FlexGroup): List<Float> {
    val n = g.children.size
    if (g.isAuto) return List(n) { 1f / n }
    val sum = g.weights.sum()
    return g.weights.map { it / sum }
}

internal object FlexZoom {
    const val MIN = 0.45f
    const val LIMIT = 0.8f
    const val MAX = 1.8f
    const val TITLE_MAX = 1.4f
    const val Q_BUDGET = 1f
    const val Q_NATURAL = 3f
    const val Q_TOP = 5f

    fun zOf(q: Float): Float {
        val z = when {
            q <= 1f -> MIN + (LIMIT - MIN) * q.coerceAtLeast(0f)
            q <= 3f -> LIMIT + (1f - LIMIT) * (q - 1f) / 2f
            q <= 4f -> 1f
            else -> 1f + (MAX - 1f) * (q - 4f).coerceAtMost(1f)
        }
        return kotlin.math.floor(z * STEPS) / STEPS
    }

    private const val STEPS = 400f

    fun pOf(q: Float): Float = when {
        q <= 1f -> -1f
        q <= 3f -> (q - 1f) / 2f - 1f
        q <= 4f -> q - 3f
        else -> 1f
    }
}

internal class TileItem(
    val id: Int,
    val kind: FlexBlockClass,
    val need: Float,
    val floor: Float,
    val cap: Float,
    val text: ((w: Float, s: Float) -> Float)? = null,
    val stat: StatShape? = null,
    val pic: DrawableShape? = null,
    val least: ((w: Float) -> Float)? = null,
    val air: Float = 0f,
    val stretchy: Boolean = false,
    val picArea: Float = 0f,
    val hug: Float = 0f,
    val leastScale: Float = FlexZoom.LIMIT
)

internal class TileAt(val scale: Float, val height: Float, val columns: Int = 0, val narrow: Boolean = false)

internal fun statFit(shape: StatShape, w: Float, h: Float, top: Float, floor: Float): TileAt {
    var best = -1
    var bestS = -1f
    for (k in shape.widths.indices) {
        val s = min(top, min(w / shape.widths[k].coerceAtLeast(1f), h / shape.heights[k].coerceAtLeast(1f)))
        if (best < 0 || (s > bestS + 0.0005f && bestS < s * FlexFit.REFLOW_KEEP)) {
            best = k
            bestS = s
        }
    }
    val narrow = bestS < floor - 0.0005f
    val s = max(bestS, floor)
    return TileAt(s, shape.heights[best] * s, shape.columns[best], narrow)
}

internal class FlexTiler(
    private val items: Map<Int, TileItem>,
    private val gap: Float,
    private val byContent: Boolean = false,
    private val strict: Boolean = false
) {
    private val hasPictures = HashMap<FlexNode, Boolean>()
    private var frozenAt = -1f
    private val frozen = java.util.IdentityHashMap<FlexGroup, HashMap<Int, FloatArray>>()

    private fun split(g: FlexGroup, w: Float, q: Float): FloatArray {
        val at = frozenAt
        if (at < 0f) return widths(g, w, q)
        return frozen.getOrPut(g) { HashMap() }.getOrPut(w.toRawBits()) { widths(g, w, at) }
    }

    private inline fun <T> withSplitsAt(q: Float, block: () -> T): T {
        val was = frozenAt
        if (was >= 0f) return block()
        frozenAt = q
        frozen.clear()
        try {
            return block()
        } finally {
            frozenAt = was
            frozen.clear()
        }
    }

    fun leaf(id: Int, w: Float, q: Float): TileAt {
        val it = items[id] ?: return TileAt(1f, 0f)
        return when (it.kind) {
            FlexBlockClass.DRAWABLE -> picAt(it, w, FlexZoom.pOf(q))
            FlexBlockClass.STATISTIC -> {
                val shape = it.stat ?: return TileAt(1f, 0f)
                statFit(shape, w, BIG, FlexZoom.zOf(q).coerceIn(it.floor, it.cap), it.floor)
            }
            FlexBlockClass.TITLE, FlexBlockClass.TEXT -> textAt(it, w, FlexZoom.zOf(q))
        }
    }

    private fun textAt(it: TileItem, w: Float, z: Float): TileAt {
        val top = z.coerceIn(it.floor, it.cap)
        var s = top
        if (it.need > 0f && it.need * s > w) s = max(it.floor, w / it.need)
        val narrow = it.need * s > w + 0.5f
        val air = if (it.air > 0f) it.air * min(1f, z) else 0f
        val h = it.text?.invoke(w, s) ?: 0f
        return TileAt(s, h + air, 0, narrow)
    }

    private fun picAt(it: TileItem, w: Float, p: Float): TileAt {
        val sh = it.pic ?: return TileAt(1f, 0f)
        if (!sh.hasContent) return TileAt(1f, min(w * 0.75f, sh.maxH))
        val wm = min(w, sh.maxW).coerceAtLeast(1f)
        val most = min(sh.maxH, wm / sh.ratioMin)
        val leastRaw = it.least?.invoke(w) ?: 0f
        val reach = min(wm, most * sh.ratioMax) * most
        val narrow = w < sh.minW - 0.5f || leastRaw > most + 0.5f || reach < 0.9f * it.picArea
        val least = min(leastRaw, most)
        val own = (wm / sh.ratio).coerceIn(least, most)
        val h = if (p <= 0f) own + (least - own) * (-p) else own + (most - own) * p
        return TileAt(1f, h, 0, narrow)
    }

    private fun leafHeight(id: Int, w: Float, q: Float): Float {
        val at = leaf(id, w, q)
        val it = items[id]
        if (strict && (at.narrow || (it != null && it.kind != FlexBlockClass.DRAWABLE && at.scale < min(FlexZoom.zOf(q).coerceAtMost(FlexZoom.LIMIT), it.leastScale) - 0.005f))) return TOO_NARROW
        return at.height
    }

    fun height(n: FlexNode, w: Float, q: Float): Float = when (n) {
        is FlexLeaf -> leafHeight(n.id, w, q)
        is FlexGroup -> {
            val gaps = gap * (n.children.size - 1)
            if (n.vertical) {
                if (n.isAuto || byContent) {
                    var sum = gaps
                    for (c in n.children) sum += height(c, w, q)
                    sum
                } else {
                    val fr = fractionsOf(n)
                    var most = 0f
                    n.children.forEachIndexed { i, c -> most = max(most, height(c, w, q) / fr[i].coerceAtLeast(0.02f)) }
                    most + gaps
                }
            } else {
                val ws = split(n, w, q)
                var most = 0f
                n.children.forEachIndexed { i, c -> most = max(most, height(c, ws[i], q)) }
                most
            }
        }
    }

    private fun pictured(n: FlexNode): Boolean = hasPictures.getOrPut(n) {
        FlexTree.leaves(n).any { items[it]?.kind == FlexBlockClass.DRAWABLE }
    }

    private fun stretchy(n: FlexNode): Boolean = FlexTree.leaves(n).any { items[it]?.stretchy == true }

    fun needWidth(n: FlexNode, q: Float): Float = when (n) {
        is FlexLeaf -> {
            val it = items[n.id]
            when {
                it == null -> 0f
                it.kind == FlexBlockClass.DRAWABLE -> {
                    val sh = it.pic
                    if (sh == null || !sh.hasContent) 0f else max(sh.minW, kotlin.math.sqrt(it.picArea * sh.ratioMin))
                }
                it.kind == FlexBlockClass.STATISTIC -> (it.stat?.hug ?: 0f) * FlexZoom.zOf(q).coerceIn(it.floor, it.cap)
                else -> it.need * FlexZoom.zOf(q).coerceIn(it.floor, it.cap)
            }
        }
        is FlexGroup -> if (n.vertical) n.children.maxOf { needWidth(it, q) }
        else n.children.sumOf { needWidth(it, q).toDouble() }.toFloat() + gap * (n.children.size - 1)
    }

    fun floorWidth(n: FlexNode): Float = when (n) {
        is FlexLeaf -> {
            val it = items[n.id]
            when {
                it == null -> 0f
                it.kind == FlexBlockClass.DRAWABLE -> it.pic?.takeIf { p -> p.hasContent }?.minW ?: 0f
                it.kind == FlexBlockClass.STATISTIC -> (it.stat?.need ?: 0f) * it.floor
                else -> it.need * it.floor
            }
        }
        is FlexGroup -> if (n.vertical) n.children.maxOf { floorWidth(it) }
        else n.children.sumOf { floorWidth(it).toDouble() }.toFloat() + gap * (n.children.size - 1)
    }

    private fun leastWidth(n: FlexNode): Float = when (n) {
        is FlexLeaf -> {
            val it = items[n.id]
            when {
                it == null -> 0f
                it.kind == FlexBlockClass.DRAWABLE -> {
                    val sh = it.pic
                    if (sh == null || !sh.hasContent) 0f else min(sh.maxW, max(sh.minW, kotlin.math.sqrt(it.picArea * sh.ratioMin)))
                }
                it.kind == FlexBlockClass.STATISTIC -> (it.stat?.need ?: 0f) * max(it.floor, it.leastScale)
                else -> it.need * max(it.floor, it.leastScale)
            }
        }
        is FlexGroup -> if (n.vertical) n.children.maxOf { leastWidth(it) }
        else n.children.sumOf { leastWidth(it).toDouble() }.toFloat() + gap * (n.children.size - 1)
    }

    fun widths(g: FlexGroup, w: Float, q: Float): FloatArray {
        val n = g.children.size
        val room = (w - gap * (n - 1)).coerceAtLeast(1f)
        if (n == 1) return floatArrayOf(room)
        val soft = List(n) { leastWidth(g.children[it]) }
        val floors = if (soft.sum() <= room) soft else List(n) { min(floorWidth(g.children[it]), room) }
        if (!g.isAuto) {
            val fr = fractionsOf(g)
            return distribute(room, fr.map { it * room }, List(n) { BIG }, floors).toFloatArray()
        }
        val needs = List(n) { needWidth(g.children[it], q).coerceAtLeast(1f) }
        val base = distribute(room, needs.map { it / needs.sum() * room }, List(n) { BIG }, floors).toFloatArray()
        if (n != 2) return base
        val a = g.children[0]
        val b = g.children[1]
        var lo = max(room * SPLIT_LOW, floors[0])
        var hi = min(room * SPLIT_HIGH, room - floors[1])
        if (needs[0] + needs[1] <= room) {
            lo = max(lo, min(needs[0], room - needs[1]))
            hi = min(hi, max(room - needs[1], needs[0]))
        }
        if (hi <= lo + 0.5f) return base
        fun uneven(x: Float): Float = abs(height(a, x, q) - height(b, room - x, q))
        var best = base[0].coerceIn(lo, hi)
        var bestGap = uneven(best)
        val start = bestGap
        for (i in 0..SPLIT_STEPS) {
            val f = lo + (hi - lo) * i / SPLIT_STEPS
            val u = uneven(f)
            if (u < bestGap - 0.5f) {
                best = f
                bestGap = u
            }
        }
        var step = (hi - lo) / SPLIT_STEPS
        for (i in 0 until 8) {
            step /= 2f
            for (f in floatArrayOf(best - step, best + step)) {
                if (f < lo || f > hi) continue
                val u = uneven(f)
                if (u < bestGap) {
                    best = f
                    bestGap = u
                }
            }
        }
        if (start - bestGap < 4f) return floatArrayOf(base[0].coerceIn(lo, hi), room - base[0].coerceIn(lo, hi))
        return floatArrayOf(best, room - best)
    }

    fun zoom(n: FlexNode, w: Float, h: Float): Float {
        if (frozenAt >= 0f) return bisect(n, w, h)
        var q = withSplitsAt(FlexZoom.Q_BUDGET) { bisect(n, w, h) }
        for (round in 0 until ZOOM_ROUNDS) {
            val next = withSplitsAt(q) { bisect(n, w, h) }
            if (next <= q + 0.005f) {
                q = max(q, next)
                break
            }
            q = next
        }
        if (height(n, w, q) <= h + 0.5f) return q
        return bisect(n, w, h, q)
    }

    private fun bisect(n: FlexNode, w: Float, h: Float, top: Float = FlexZoom.Q_TOP): Float {
        if (height(n, w, top) <= h + 0.5f) return top
        if (height(n, w, 0f) > h + 0.5f) return 0f
        var lo = 0f
        var hi = top
        repeat(ZOOM_STEPS) {
            val mid = (lo + hi) / 2f
            if (height(n, w, mid) <= h + 0.5f) lo = mid else hi = mid
        }
        return lo
    }

    private fun keepWidth(n: FlexNode, q: Float): Float = when (n) {
        is FlexLeaf -> {
            val it = items[n.id]
            if (it != null && it.kind == FlexBlockClass.TITLE) max(needWidth(n, q), it.hug * FlexZoom.zOf(q).coerceIn(it.floor, it.cap))
            else needWidth(n, q)
        }
        is FlexGroup -> if (n.vertical) n.children.maxOf { keepWidth(it, q) }
        else n.children.sumOf { keepWidth(it, q).toDouble() }.toFloat() + gap * (n.children.size - 1)
    }

    private fun fillWidths(g: FlexGroup, w: Float, h: Float, q: Float): FloatArray? {
        val n = g.children.size
        val room = (w - gap * (n - 1)).coerceAtLeast(1f)
        val pics = BooleanArray(n) { val c = g.children[it]; c is FlexLeaf && items[c.id]?.kind == FlexBlockClass.DRAWABLE }
        val out = FloatArray(n)
        for (i in 0 until n) {
            val c = g.children[i]
            if (pics[i]) {
                val sh = items[(c as FlexLeaf).id]?.pic ?: return null
                var lo = floorWidth(c)
                var hi = min(room, sh.maxW)
                if (height(c, hi, q) <= h + 0.5f) {
                    out[i] = hi
                } else {
                    repeat(14) {
                        val mid = (lo + hi) / 2f
                        if (height(c, mid, q) <= h + 0.5f) lo = mid else hi = mid
                    }
                    out[i] = lo
                }
            } else {
                var lo = min(room, max(floorWidth(c), keepWidth(c, q)))
                var hi = room
                if (height(c, lo, q) <= h + 0.5f) {
                    out[i] = lo
                } else {
                    repeat(14) {
                        val mid = (lo + hi) / 2f
                        if (height(c, mid, q) <= h + 0.5f) hi = mid else lo = mid
                    }
                    out[i] = hi
                }
            }
        }
        val used = out.sum()
        if (used > room + 0.5f) return null
        val texts = (0 until n).filter { !pics[it] }
        val spare = room - used
        val base = texts.sumOf { out[it].toDouble() }.toFloat()
        if (base > 0f) for (i in texts) out[i] += spare * out[i] / base
        return out
    }

    private fun grow(n: FlexNode, w: Float, h: Float, from: Float, cap: Float): Float {
        val q = withSplitsAt(from) { growFrom(n, w, h, from, cap) }
        return if (q <= from || height(n, w, q) <= h + 0.5f) q else from
    }

    private fun growFrom(n: FlexNode, w: Float, h: Float, from: Float, cap: Float): Float {
        fun ok(q: Float): Boolean = FlexZoom.zOf(q) <= cap + 0.0001f && height(n, w, q) <= h + 0.5f
        if (ok(FlexZoom.Q_TOP)) return FlexZoom.Q_TOP
        if (!ok(from)) return from
        var lo = from
        var hi = FlexZoom.Q_TOP
        repeat(16) {
            val mid = (lo + hi) / 2f
            if (ok(mid)) lo = mid else hi = mid
        }
        return lo
    }

    fun solve(root: FlexNode?, x: Float, y: Float, w: Float, h: Float): FlexSolved {
        val rects = LinkedHashMap<Int, FloatArray>()
        val levels = HashMap<Int, Float>()
        val dividers = ArrayList<FlexDivider>()
        val groupSizes = HashMap<List<Int>, List<Float>>()
        if (root == null) return FlexSolved(rects, dividers, groupSizes, levels, 1f)
        val q = if (byContent) FlexZoom.Q_NATURAL else zoom(root, w, h)

        fun place(n: FlexNode, px: Float, py: Float, pw: Float, ph: Float, level: Float, nested: Boolean, path: List<Int>) {
            if (n is FlexLeaf) {
                rects[n.id] = floatArrayOf(px, py, pw, ph)
                levels[n.id] = level
                return
            }
            val g = n as FlexGroup
            val count = g.children.size
            val gaps = gap * (count - 1)
            var sizes: FloatArray
            val qs = FloatArray(count) { level }
            if (g.vertical) {
                val axis = (ph - gaps).coerceAtLeast(0f)
                if (!g.isAuto && !byContent) {
                    val fr = fractionsOf(g)
                    val floors = g.children.map { height(it, pw, 0f) }
                    val caps = g.children.map { height(it, pw, FlexZoom.Q_TOP) }
                    sizes = distribute(axis, fr.map { it * axis }, caps, floors).toFloatArray()
                    for (i in 0 until count) qs[i] = zoom(g.children[i], pw, sizes[i])
                } else {
                    sizes = FloatArray(count) { height(g.children[it], pw, level) }
                    val left = axis - sizes.sum()
                    if (left < -0.5f) {
                        val floors = g.children.map { height(it, pw, 0f) }
                        sizes = distribute(axis, sizes.toList(), sizes.toList(), floors).toFloatArray()
                    } else if (left > 0.5f && nested) {
                        val pull = FloatArray(count) { if (stretchy(g.children[it])) sizes[it] else 0f }
                        if (pull.sum() <= 0f) for (i in 0 until count) if (pictured(g.children[i])) pull[i] = sizes[i]
                        val total = pull.sum()
                        if (total > 0f) for (i in 0 until count) sizes[i] += left * pull[i] / total
                    }
                }
            } else {
                sizes = widths(g, pw, level)
                if (g.children.any { it is FlexLeaf && items[it.id]?.kind == FlexBlockClass.DRAWABLE } &&
                    g.children.any { !pictured(it) }
                ) {
                    val natural = g.children.indices.maxOf { height(g.children[it], sizes[it], level) }
                    if (natural < ph - 0.5f) withSplitsAt(level) { fillWidths(g, pw, ph, level) }?.let { sizes = it }
                }
                val cap = FlexZoom.zOf(level) * ROW_GROW
                for (i in 0 until count) {
                    val child = g.children[i]
                    if (height(child, sizes[i], level) < ph - 0.5f) qs[i] = grow(child, sizes[i], ph, level, cap)
                }
            }
            groupSizes[path] = sizes.toList()
            var off = 0f
            g.children.forEachIndexed { i, child ->
                if (g.vertical) place(child, px, py + off, pw, sizes[i], qs[i], nested, path + i)
                else place(child, px + off, py, sizes[i], ph, qs[i], true, path + i)
                off += sizes[i]
                if (i < count - 1) {
                    val adjacent = (FlexTree.leaves(child) + FlexTree.leaves(g.children[i + 1])).toSet()
                    val mid = off + gap / 2f
                    dividers.add(
                        if (g.vertical) FlexDivider(path, i, true, px + pw / 2f, py + mid, sizes.toList(), adjacent)
                        else FlexDivider(path, i, false, px + mid, py + ph / 2f, sizes.toList(), adjacent)
                    )
                }
                off += gap
            }
        }

        val height = if (byContent) height(root, w, q) else h
        place(root, x, y, w, height, q, false, emptyList())
        return FlexSolved(rects, dividers, groupSizes, levels, FlexZoom.zOf(q))
    }

    companion object {
        const val TOO_NARROW = 1e6f
        private const val ZOOM_STEPS = 16
        private const val ZOOM_ROUNDS = 3
    }
}

class FlexSnapped(val x: Float, val y: Float, val w: Float, val h: Float, val guidesX: List<Float>, val guidesY: List<Float>)

object FlexSnap {
    const val SNAP_REACH = 14f

    private fun lines(plan: FlexPlan, id: Int, vertical: Boolean): List<Float> {
        val out = ArrayList<Float>()
        if (vertical) {
            out += plan.contentTop; out += plan.contentTop + plan.contentHeight / 2f; out += plan.contentTop + plan.contentHeight
        } else {
            out += plan.contentLeft; out += plan.contentLeft + plan.contentWidth / 2f; out += plan.contentLeft + plan.contentWidth
        }
        for (b in plan.boxes) {
            if (b.id == id) continue
            if (vertical) { out += b.cellTop; out += b.cellTop + b.cellHeight / 2f; out += b.cellTop + b.cellHeight }
            else { out += b.cellLeft; out += b.cellLeft + b.cellWidth / 2f; out += b.cellLeft + b.cellWidth }
        }
        return out
    }

    /** The nearest line to any of [edges] (the box's own), within reach: how far to shift, and the line. */
    private fun nearest(edges: List<Float>, lines: List<Float>): Pair<Float, Float>? {
        var best: Pair<Float, Float>? = null
        for (e in edges) for (l in lines) {
            val d = l - e
            if (abs(d) <= SNAP_REACH && (best == null || abs(d) < abs(best.first))) best = d to l
        }
        return best
    }

    /** Box [id] moved to [x], [y] (its size [w] by [h]): where it snaps. */
    fun move(plan: FlexPlan, id: Int, x: Float, y: Float, w: Float, h: Float): FlexSnapped {
        val sx = nearest(listOf(x, x + w / 2f, x + w), lines(plan, id, false))
        val sy = nearest(listOf(y, y + h / 2f, y + h), lines(plan, id, true))
        return FlexSnapped(x + (sx?.first ?: 0f), y + (sy?.first ?: 0f), w, h, listOfNotNull(sx?.second), listOfNotNull(sy?.second))
    }

    /** Box [id] at [x], [y] resized from its bottom right corner to [w] by [h]: where its right and bottom edges snap. */
    fun resize(plan: FlexPlan, id: Int, x: Float, y: Float, w: Float, h: Float, least: Float): FlexSnapped {
        val sx = nearest(listOf(x + w), lines(plan, id, false))
        val sy = nearest(listOf(y + h), lines(plan, id, true))
        val nw = (w + (sx?.first ?: 0f)).coerceAtLeast(least)
        val nh = (h + (sy?.first ?: 0f)).coerceAtLeast(least)
        return FlexSnapped(x, y, nw, nh, listOfNotNull(sx?.second), listOfNotNull(sy?.second))
    }
}

