package com.ashrdev.aznd.ui.common.share

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * The tile layout of a card, in the manner of a tiling window manager (Hyprland, i3).
 *
 * The card's area is split into tiles, one per block, and the tiles always fill the area: nothing floats,
 * nothing is left over, and a tile can sit next to or under any other. The layout is a tree: a group splits its
 * area across (side by side) or down (stacked) among its children, and a child is a block's tile or another
 * group, so tiles can be nested as deep as wanted (a tall tile on the left, two stacked on the right, ...).
 *
 * A group's [FlexGroup.weights] say how its area is shared. All zero means automatic: stacked children share
 * the height by how much room their content needs, side by side ones share the width equally. Once the person
 * resizes a tile, the weights of that group become real shares.
 */

/** A part of the layout. */
sealed interface FlexNode

/** The tile of block [id]. */
data class FlexLeaf(val id: Int) : FlexNode

/**
 * A split. [vertical] true stacks the [children] top to bottom, false puts them side by side. There is one
 * weight per child: all zero = automatic, otherwise all positive (relative shares).
 */
data class FlexGroup(val vertical: Boolean, val children: List<FlexNode>, val weights: List<Float>) : FlexNode {
    val isAuto: Boolean get() = weights.all { it <= 0f }
}

/** Where a dragged tile is dropped on another: beside it on that side, or in its place (swapping). */
enum class DropZone { LEFT, RIGHT, TOP, BOTTOM, SWAP }

/**
 * A drop: on [zone]'s side of [span] (the blocks of one part of the tiling: a single tile, or a whole row,
 * column or the card, which then make room together), or swapping places with [target]. [region] is that part's
 * area in card pixels (x, y, width, height), [tree] the tiling the drop makes.
 */
class DropSpot(
    val target: Int,
    val zone: DropZone,
    val span: Set<Int> = setOf(target),
    val region: FloatArray = FloatArray(4),
    val tree: FlexNode? = null
) {
    /** More than one tile makes room: the drop spans a row, a column or the card. */
    val spans: Boolean get() = span.size > 1
}

/**
 * The border between two neighbours of one group, for resizing. [cx], [cy] is its middle in card pixels,
 * [sizes] the current extent of every child of the group along the split (so dragging can move the border),
 * [adjacent] the blocks on either side of it.
 */
class FlexDivider(
    val path: List<Int>,
    val index: Int,
    val vertical: Boolean,
    val cx: Float,
    val cy: Float,
    val sizes: List<Float>,
    val adjacent: Set<Int>
)

/** Where block [id] sits in the tree: group [group] at [path], as its child number [index]. */
class FlexGroupRef(val path: List<Int>, val index: Int, val group: FlexGroup)

/** The tiles of a laid out tree, in card pixels: x, y, width, height. */
class FlexSolved(
    val rects: Map<Int, FloatArray>,
    val dividers: List<FlexDivider>,
    /** Extent of every child of every group, by the group's path. */
    val groupSizes: Map<List<Int>, List<Float>>
)

/** The smallest a tile can be dragged to, in card pixels. */
const val MIN_TILE = 140f

/** A border dragged this close (a share of the two tiles' total) to an even split snaps to it. */
private const val SNAP = 0.03f

/** An automatic pair side by side is split between these shares when it evens their heights out (see [FlexTiler]). */
private const val SPLIT_LOW = 0.25f
private const val SPLIT_HIGH = 0.75f
private const val SPLIT_STEPS = 16

private const val BIG = 1e9f

object FlexTree {

    fun leaves(node: FlexNode?): List<Int> = when (node) {
        null -> emptyList()
        is FlexLeaf -> listOf(node.id)
        is FlexGroup -> node.children.flatMap { leaves(it) }
    }

    /** The blocks whose tiles touch the top edge of [node]: the first of a stack, every one of a row. */
    fun topEdge(node: FlexNode?): Set<Int> = when (node) {
        null -> emptySet()
        is FlexLeaf -> setOf(node.id)
        is FlexGroup -> if (node.vertical) topEdge(node.children.firstOrNull()) else node.children.flatMapTo(HashSet()) { topEdge(it) }
    }

    /** [node] with only the blocks in [keep]: emptied groups vanish, a group of one is replaced by its child. */
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

    /** A group of [kids]: none is null, one is that child, a child group that splits the same way is merged in. */
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

    /** All zero or all positive: a mix gets its missing shares from the average of the others. */
    private fun evened(ws: List<Float>): List<Float> {
        val positive = ws.filter { it > 0f }
        if (positive.isEmpty() || positive.size == ws.size) return ws.map { max(it, 0f) }
        val avg = positive.sum() / positive.size
        return ws.map { if (it > 0f) it else avg }
    }

    private fun zeros(n: Int): List<Float> = List(n) { 0f }

    /** The layout when none was made: stacked, or on a wide card side by side (in two columns from four blocks). */
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

    /** The layout of exactly the [shown] blocks: the saved one without the missing, the new ones added at the bottom. */
    fun effective(layout: FlexNode?, shown: List<Int>, wide: Boolean): FlexNode? {
        if (shown.isEmpty()) return null
        var tree: FlexNode = prune(layout, shown.toSet()) ?: return autoTree(shown, wide)
        val have = leaves(tree).toSet()
        for (id in shown) if (id !in have) tree = append(tree, id)
        return tree
    }

    /** A new tile along the bottom of the whole card. */
    fun append(root: FlexNode, id: Int): FlexNode {
        val leaf = FlexLeaf(id)
        if (root is FlexGroup && root.vertical) {
            val w = if (root.isAuto) 0f else root.weights.sum() / root.weights.size
            return FlexGroup(true, root.children + leaf, root.weights + w)
        }
        return FlexGroup(true, listOf(root, leaf), zeros(2))
    }

    /**
     * Drops [dragged] on [target]: beside it on the side [zone] says, or in its place. Every group the move changes
     * (the ones that held the block, and the ones that hold it now) shares its space by content again: shares that
     * were set for the old contents (by a resize or by auto align) would leave cells far bigger than what is in them.
     * Groups the move does not touch keep their shares.
     */
    fun moveTo(root: FlexNode?, dragged: Int, target: Int, zone: DropZone): FlexNode? {
        if (root == null || dragged == target) return null
        if (zone == DropZone.SWAP) return byContent(byContent(swap(root, dragged, target), dragged), target)
        return moveBeside(root, dragged, setOf(target), zone)
    }

    /**
     * Drops [dragged] on the [zone] side of the part of the tiling that holds exactly the blocks [span] (a tile, a
     * row, a column, the whole card): the new tile runs along that whole side and every tile of the part makes room,
     * so a block dropped beside a column of two takes the full height and the two share what is left. As with
     * [moveTo], the groups the move touches share by content again. Null when there is nothing to drop beside.
     */
    fun moveBeside(root: FlexNode?, dragged: Int, span: Set<Int>, zone: DropZone): FlexNode? {
        if (root == null || zone == DropZone.SWAP) return null
        val rest = span - dragged
        if (rest.isEmpty()) return null
        val all = leaves(root).toSet()
        if (dragged !in all) return null
        val without = prune(byContent(root, dragged), all - dragged) ?: return null
        val vertical = zone == DropZone.TOP || zone == DropZone.BOTTOM
        val before = zone == DropZone.LEFT || zone == DropZone.TOP
        val placed = insertAt(without, rest, dragged, vertical, before)
        // Tidy: a group put inside one that splits the same way is merged into it.
        return prune(byContent(placed, dragged), all)
    }

    /** [node] with a tile for [id] on one side of the part that holds exactly [span]. */
    private fun insertAt(node: FlexNode, span: Set<Int>, id: Int, vertical: Boolean, before: Boolean): FlexNode {
        val leaf = FlexLeaf(id)
        fun wrap(n: FlexNode): FlexNode = FlexGroup(vertical, if (before) listOf(leaf, n) else listOf(n, leaf), zeros(2))
        if (leaves(node).toSet() == span) return wrap(node)
        if (node !is FlexGroup) return node
        // A run of this group's children that holds exactly the span (a part that tidying merged into its group).
        val sets = node.children.map { leaves(it).toSet() }
        for (a in sets.indices) {
            if (!span.containsAll(sets[a])) continue
            var union = HashSet<Int>()
            for (b in a until sets.size) {
                if (!span.containsAll(sets[b])) break
                union.addAll(sets[b])
                if (union.size == span.size && union == span) {
                    if (a == 0 && b == sets.lastIndex) return wrap(node)
                    val kids = node.children.toMutableList()
                    val ws = node.weights.toMutableList()
                    if (node.vertical == vertical) {
                        val at = if (before) a else b + 1
                        kids.add(at, leaf)
                        ws.add(at, if (node.isAuto) 0f else node.weights.slice(a..b).average().toFloat())
                    } else {
                        val run = FlexGroup(node.vertical, node.children.slice(a..b), node.weights.slice(a..b))
                        val share = node.weights.slice(a..b).sum()
                        for (k in b downTo a) {
                            kids.removeAt(k)
                            ws.removeAt(k)
                        }
                        kids.add(a, wrap(run))
                        ws.add(a, share)
                    }
                    return FlexGroup(node.vertical, kids, ws)
                }
            }
        }
        // Deeper: the child that holds the span.
        val i = sets.indexOfFirst { it.containsAll(span) }
        if (i < 0) return wrap(node)
        val kids = node.children.toMutableList()
        kids[i] = insertAt(kids[i], span, id, vertical, before)
        return FlexGroup(node.vertical, kids, node.weights)
    }

    /** [node] with every group that holds block [id] (at any depth) sharing its space by content. */
    private fun byContent(node: FlexNode, id: Int): FlexNode {
        if (node !is FlexGroup || id !in leaves(node)) return node
        return FlexGroup(node.vertical, node.children.map { byContent(it, id) }, zeros(node.children.size))
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

    /** Puts the tree's blocks in [order] (the blocks list): the tiles stay where they are, the blocks change places. */
    fun relabel(root: FlexNode, order: List<Int>): FlexNode {
        val sorted = leaves(root).sortedBy { id -> order.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it } }
        var next = 0
        return mapLeaves(root) { sorted[next++] }
    }

    /** Which group holds block [id] as a direct child. */
    fun refOf(root: FlexNode?, id: Int, path: List<Int> = emptyList()): FlexGroupRef? {
        if (root !is FlexGroup) return null
        root.children.forEachIndexed { i, child ->
            if (child is FlexLeaf && child.id == id) return FlexGroupRef(path, i, root)
            refOf(child, id, path + i)?.let { return it }
        }
        return null
    }

    /** The group at [path] with these weights. */
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

    /** The group at [path] sharing its space by content again. */
    fun clearWeights(node: FlexNode?, path: List<Int>): FlexNode? {
        val group = (path.fold(node) { n, i -> (n as? FlexGroup)?.children?.getOrNull(i) }) as? FlexGroup ?: return node
        return setWeights(node, path, zeros(group.children.size))
    }

    /**
     * Moves the border [d] by [travelled] card pixels, never leaving a tile smaller than [MIN_TILE]. Close to where
     * the two sides would be equal (within [SNAP] of their total) it snaps there.
     */
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

    /** The group that holds block [id] as a direct child, split the other way (a row becomes a column), sharing by content. */
    fun turned(root: FlexNode?, id: Int): FlexNode? = editParent(root, id) { g ->
        FlexGroup(!g.vertical, g.children, zeros(g.children.size))
    }

    /** The group that holds block [id] as a direct child, its parts in the opposite order (mirrored). */
    fun flipped(root: FlexNode?, id: Int): FlexNode? = editParent(root, id) { g ->
        FlexGroup(g.vertical, g.children.reversed(), g.weights.reversed())
    }

    /** Every group of the tiling sharing its space by content again: the cells fit what is in them (resizes and auto align's shares are cleared). */
    fun relaxed(root: FlexNode?): FlexNode? = when (root) {
        null -> null
        is FlexLeaf -> root
        is FlexGroup -> FlexGroup(root.vertical, root.children.map { relaxed(it)!! }, zeros(root.children.size))
    }

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
        // Tidy: a turned group that now splits the same way as its parent is merged into it.
        return prune(out, leaves(out).toSet())
    }

    /** Gives the child [index] of the group at [path] the share [fraction] (0 to 1) of the group, the others keeping their proportions. */
    fun withShare(tree: FlexNode?, path: List<Int>, sizes: List<Float>, index: Int, fraction: Float): FlexNode? {
        if (index !in sizes.indices) return tree
        val total = sizes.sum()
        val others = total - sizes[index]
        val f = if (sizes.size > 1) fraction.coerceIn(0.1f, 0.9f) else 1f
        val out = sizes.mapIndexed { j, s ->
            when {
                j == index -> f * total
                others > 0f -> s * ((1f - f) * total / others)
                else -> s
            }
        }
        return setWeights(tree, path, out)
    }
}

/**
 * What dropping something held at card pixel [x], [y] would do. Over another tile's cell (the space the tiling gave
 * it, which can be bigger than the tile itself): its middle swaps the two; a side puts the dropped tile beside it.
 * Near the side of a cell that is also the side of a bigger part (a row, a column, the whole card), the drop goes
 * beside that part instead, so several tiles make room together: the closer to the edge, the bigger the part. The
 * card's margins drop beside the whole card. Parts that would make the same tiling count once.
 */
internal fun dropSpot(plan: FlexPlan, dragged: Int, x: Float, y: Float): DropSpot? {
    val root = plan.tree ?: return null
    val cells = plan.boxes.associate { it.id to floatArrayOf(it.cellLeft, it.cellTop, it.cellWidth, it.cellHeight) }
    if (cells.size < 2) return null
    val cl = plan.contentLeft
    val ct = plan.contentTop
    val cr = cl + plan.contentWidth
    val cb = ct + plan.contentHeight

    fun regionOf(ids: Set<Int>): FloatArray {
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
    fun spot(target: Int, zone: DropZone, span: Set<Int>): DropSpot? {
        val tree = FlexTree.moveBeside(root, dragged, span, zone) ?: return null
        return DropSpot(target, zone, span, regionOf(span - dragged), tree)
    }

    // In the margin round the tiles: beside the whole card.
    if (x < cl || x > cr || y < ct || y > cb) {
        val zone = listOf(
            DropZone.LEFT to cl - x, DropZone.RIGHT to x - cr, DropZone.TOP to ct - y, DropZone.BOTTOM to y - cb
        ).maxByOrNull { it.second }!!.first
        val all = FlexTree.leaves(root).toSet()
        return spot(all.first { it != dragged }, zone, all)
    }

    // In a gap between tiles: between the two, beside the nearest one (only that tile's own part, not a bigger one).
    val under = plan.boxes.firstOrNull { it.id != dragged && it.cellContains(x, y) }
    if (under == null) {
        if (plan.boxes.any { it.id == dragged && it.cellContains(x, y) }) return null
        val near = plan.boxes.filter { it.id != dragged }.minByOrNull { b ->
            val ox = max(0f, max(b.cellLeft - x, x - (b.cellLeft + b.cellWidth)))
            val oy = max(0f, max(b.cellTop - y, y - (b.cellTop + b.cellHeight)))
            ox + oy
        } ?: return null
        val ox = max(near.cellLeft - x, x - (near.cellLeft + near.cellWidth))
        val oy = max(near.cellTop - y, y - (near.cellTop + near.cellHeight))
        if (max(ox, oy) > plan.gap + 12f) return null
        val zone = if (ox >= oy) {
            if (x < near.cellLeft) DropZone.LEFT else DropZone.RIGHT
        } else {
            if (y < near.cellTop) DropZone.TOP else DropZone.BOTTOM
        }
        return spot(near.id, zone, setOf(near.id))
    }
    val t = under
    val dx = x - (t.cellLeft + t.cellWidth / 2f)
    val dy = y - (t.cellTop + t.cellHeight / 2f)
    if (abs(dx) < t.cellWidth * 0.2f && abs(dy) < t.cellHeight * 0.2f) {
        val tree = FlexTree.moveTo(root, dragged, t.id, DropZone.SWAP)
        return DropSpot(t.id, DropZone.SWAP, setOf(t.id), floatArrayOf(t.cellLeft, t.cellTop, t.cellWidth, t.cellHeight), tree)
    }
    val horizontal = abs(dx) / t.cellWidth > abs(dy) / t.cellHeight
    val zone = if (horizontal) {
        if (dx < 0f) DropZone.LEFT else DropZone.RIGHT
    } else {
        if (dy < 0f) DropZone.TOP else DropZone.BOTTOM
    }
    val cell = cells.getValue(t.id)
    fun edgeOf(r: FloatArray): Float = when (zone) {
        DropZone.LEFT -> r[0]
        DropZone.RIGHT -> r[0] + r[2]
        DropZone.TOP -> r[1]
        else -> r[1] + r[3]
    }
    val edge = edgeOf(cell)

    // The parts that hold this tile and share its side, from the tile out to the whole card.
    val chain = ArrayList<Set<Int>>()
    fun walk(node: FlexNode): Boolean {
        val ids = FlexTree.leaves(node).toSet()
        if (t.id !in ids) return false
        if (node is FlexGroup) node.children.forEach { if (walk(it)) return@forEach }
        if (abs(edgeOf(regionOf(ids)) - edge) <= 2f) chain += ids
        return true
    }
    walk(root)
    val options = ArrayList<DropSpot>()
    for (ids in chain) {
        val s = spot(t.id, zone, ids) ?: continue
        if (options.none { it.tree == s.tree }) options += s
    }
    if (options.isEmpty()) return null
    // How near the edge the finger is: 0 at the edge, 1 where the middle (the swap) starts.
    val half = if (horizontal) t.cellWidth / 2f else t.cellHeight / 2f
    val dist = abs((if (horizontal) x else y) - edge)
    val u = (dist / (half * 0.6f).coerceAtLeast(1f)).coerceIn(0f, 0.999f)
    val level = ((1f - u) * options.size).toInt().coerceIn(0, options.size - 1)
    return options[level]
}

/**
 * Shares out [total] among tiles that want [desired], none getting more than its cap or less than its floor;
 * what a capped tile cannot use goes to the others, and what a floored one needs comes from the others. When
 * the floors alone are more than there is, they are shrunk together.
 */
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

internal object FlexTiler {

    /** The width a node wants when nothing limits it: a tile's own, side by side the sum, stacked the widest. */
    fun hugOf(node: FlexNode, gap: Float, hug: (Int) -> Float): Float = when (node) {
        is FlexLeaf -> hug(node.id)
        is FlexGroup -> if (node.vertical) {
            node.children.maxOf { hugOf(it, gap, hug) }
        } else {
            node.children.sumOf { hugOf(it, gap, hug).toDouble() }.toFloat() + gap * (node.children.size - 1)
        }
    }

    /** The least width a node can have, given a tile's own ([floor] or a need): side by side the sum, stacked the widest. */
    private fun floorOf(node: FlexNode, gap: Float, floor: (Int) -> Float): Float = when (node) {
        is FlexLeaf -> floor(node.id)
        is FlexGroup -> if (node.vertical) {
            node.children.maxOf { floorOf(it, gap, floor) }
        } else {
            node.children.sumOf { floorOf(it, gap, floor).toDouble() }.toFloat() + gap * (node.children.size - 1)
        }
    }

    /**
     * How side by side children share [axis] (the row's width without its gaps): by their real shares; else by how much each wants, except that a
     * pair is split where both come out equally tall (within a quarter to three quarters each), so neither leaves
     * empty space under it. Same idea as the auto aligner's even rows.
     */
    private fun widthShares(
        g: FlexGroup, axis: Float, gap: Float, nat: (Int, Float) -> Float, hug: (Int) -> Float, need: (Int) -> Float
    ): List<Float> {
        if (!g.isAuto) return fractionsOf(g)
        val wants = g.children.map { hugOf(it, gap, hug).coerceAtLeast(1f) }
        val total = wants.sum()
        val byWant = wants.map { it / total }
        if (g.children.size != 2 || axis <= 0f) return byWant
        val a = g.children[0]
        val b = g.children[1]
        fun uneven(f: Float): Float = abs(natural(a, axis * f, gap, nat, hug, need) - natural(b, axis * (1f - f), gap, nat, hug, need))
        // Evening out never takes a part below the width its content needs at full size (or below its share by want, if
        // that is less): a narrower part would shrink, which is worse than a gap under it.
        val low = max(SPLIT_LOW, min(byWant[0], floorOf(a, gap, need) / axis))
        val high = min(SPLIT_HIGH, 1f - min(byWant[1], floorOf(b, gap, need) / axis))
        if (high <= low) return byWant
        // The heights need not move one way as the split moves (a photo grows taller as it widens, text shorter), so look along it, then closer in.
        var best = byWant[0].coerceIn(low, high)
        var bestGap = uneven(best)
        val start = bestGap
        for (i in 0..SPLIT_STEPS) {
            val f = low + (high - low) * i / SPLIT_STEPS
            val u = uneven(f)
            if (u < bestGap - 0.5f) {
                best = f
                bestGap = u
            }
        }
        var step = (high - low) / SPLIT_STEPS
        for (i in 0 until 8) {
            step /= 2f
            for (f in floatArrayOf(best - step, best + step)) {
                if (f < low || f > high) continue
                val u = uneven(f)
                if (u < bestGap) {
                    best = f
                    bestGap = u
                }
            }
        }
        // Keep the split by what they want unless evening them out is worth it.
        return if (start - bestGap < 4f) byWant else listOf(best, 1f - best)
    }

    /**
     * The height [node] needs when it is [w] wide; [nat] says how tall a block's content is at a width.
     * A group with real shares needs enough for the tile that is the tightest fit for its share.
     */
    fun natural(node: FlexNode, w: Float, gap: Float, nat: (Int, Float) -> Float, hug: (Int) -> Float, need: (Int) -> Float): Float = when (node) {
        is FlexLeaf -> nat(node.id, w)
        is FlexGroup -> {
            val gaps = gap * (node.children.size - 1)
            if (node.vertical) {
                if (node.isAuto) {
                    node.children.sumOf { natural(it, w, gap, nat, hug, need).toDouble() }.toFloat() + gaps
                } else {
                    val fr = fractionsOf(node)
                    var most = 0f
                    node.children.forEachIndexed { i, child ->
                        most = max(most, natural(child, w, gap, nat, hug, need) / fr[i].coerceAtLeast(0.02f))
                    }
                    most + gaps
                }
            } else {
                val fr = widthShares(node, w - gaps, gap, nat, hug, need)
                var most = 0f
                node.children.forEachIndexed { i, child -> most = max(most, natural(child, (w - gaps) * fr[i], gap, nat, hug, need)) }
                most
            }
        }
    }

    /**
     * Lays [root] out in the box x, y, w, h into cells: the space each tile may use, which fills the box. [nat] is
     * a block's natural height at a width, [hug] the width it wants, [floor] the least width its cell can have
     * (what keeps its words whole at its class's smallest scale), [need] the least width at which it is not shrunk
     * at all, and [cap] the most a block's cell may take along an axis (drawables keep to their size caps, photos to
     * half the card), or a huge number for no limit.
     */
    fun solve(
        root: FlexNode?, x: Float, y: Float, w: Float, h: Float, gap: Float,
        nat: (Int, Float) -> Float,
        cap: (id: Int, vertical: Boolean) -> Float,
        hug: (Int) -> Float,
        floor: (Int) -> Float,
        need: (Int) -> Float
    ): FlexSolved {
        val rects = LinkedHashMap<Int, FloatArray>()
        val dividers = ArrayList<FlexDivider>()
        val groupSizes = HashMap<List<Int>, List<Float>>()

        // The most a node may take along an axis. Along its own split a group takes the sum of its children's;
        // across it, the tightest of them.
        fun capOf(node: FlexNode, vertical: Boolean): Float {
            if (node is FlexLeaf) return cap(node.id, vertical)
            val g = node as FlexGroup
            if (g.vertical == vertical) {
                var sum = gap * (g.children.size - 1)
                for (child in g.children) {
                    val c = capOf(child, vertical)
                    if (c >= BIG) return BIG
                    sum += c
                }
                return sum
            }
            var least = BIG
            for (child in g.children) least = minOf(least, capOf(child, vertical))
            return least
        }

        fun place(node: FlexNode, px: Float, py: Float, pw: Float, ph: Float, path: List<Int>) {
            if (node is FlexLeaf) {
                rects[node.id] = floatArrayOf(px, py, pw, ph)
                return
            }
            val g = node as FlexGroup
            val n = g.children.size
            val axis = ((if (g.vertical) ph else pw) - gap * (n - 1)).coerceAtLeast(0f)
            val desired: List<Float> = when {
                !g.isAuto -> fractionsOf(g).map { it * axis }
                g.vertical -> {
                    val needs = g.children.map { natural(it, pw, gap, nat, hug, need).coerceAtLeast(1f) }
                    val total = needs.sum()
                    needs.map { it / total * axis }
                }
                else -> widthShares(g, axis, gap, nat, hug, need).map { it * axis }
            }
            val floors = g.children.map { if (g.vertical) 0f else floorOf(it, gap, floor) }
            val sizes = distribute(axis, desired, g.children.map { capOf(it, g.vertical) }, floors)
            groupSizes[path] = sizes
            var off = 0f
            g.children.forEachIndexed { i, child ->
                if (g.vertical) place(child, px, py + off, pw, sizes[i], path + i)
                else place(child, px + off, py, sizes[i], ph, path + i)
                off += sizes[i]
                if (i < n - 1) {
                    val adjacent = (FlexTree.leaves(child) + FlexTree.leaves(g.children[i + 1])).toSet()
                    val mid = off + gap / 2f
                    dividers.add(
                        if (g.vertical) FlexDivider(path, i, true, px + pw / 2f, py + mid, sizes, adjacent)
                        else FlexDivider(path, i, false, px + mid, py + ph / 2f, sizes, adjacent)
                    )
                }
                off += gap
            }
        }

        if (root != null) place(root, x, y, w, h, emptyList())
        return FlexSolved(rects, dividers, groupSizes)
    }
}
