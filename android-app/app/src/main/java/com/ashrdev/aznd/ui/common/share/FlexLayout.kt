package com.ashrdev.aznd.ui.common.share

import kotlin.math.abs
import kotlin.math.max

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

class DropSpot(val target: Int, val zone: DropZone)

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

private const val BIG = 1e9f

object FlexTree {

    fun leaves(node: FlexNode?): List<Int> = when (node) {
        null -> emptyList()
        is FlexLeaf -> listOf(node.id)
        is FlexGroup -> node.children.flatMap { leaves(it) }
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

    /** Drops [dragged] on [target]: beside it on the side [zone] says (sharing its space), or in its place. */
    fun moveTo(root: FlexNode?, dragged: Int, target: Int, zone: DropZone): FlexNode? {
        if (root == null || dragged == target) return null
        if (zone == DropZone.SWAP) return swap(root, dragged, target)
        val without = prune(root, leaves(root).toSet() - dragged) ?: return null
        val vertical = zone == DropZone.TOP || zone == DropZone.BOTTOM
        val before = zone == DropZone.LEFT || zone == DropZone.TOP
        return insertBeside(without, target, dragged, vertical, before)
    }

    private fun insertBeside(node: FlexNode, target: Int, id: Int, vertical: Boolean, before: Boolean): FlexNode {
        val leaf = FlexLeaf(id)
        return when (node) {
            is FlexLeaf -> if (node.id == target) {
                FlexGroup(vertical, if (before) listOf(leaf, node) else listOf(node, leaf), zeros(2))
            } else node
            is FlexGroup -> {
                val i = node.children.indexOfFirst { it is FlexLeaf && it.id == target }
                if (i >= 0 && node.vertical == vertical) {
                    val kids = node.children.toMutableList()
                    val ws = node.weights.toMutableList()
                    val at = if (before) i else i + 1
                    kids.add(at, leaf)
                    if (node.isAuto) {
                        ws.add(at, 0f)
                    } else {
                        val half = node.weights[i] / 2f
                        ws[i] = half
                        ws.add(at, half)
                    }
                    FlexGroup(node.vertical, kids, ws)
                } else {
                    FlexGroup(node.vertical, node.children.map { insertBeside(it, target, id, vertical, before) }, node.weights)
                }
            }
        }
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

    /** Moves the border [d] by [travelled] card pixels, never leaving a tile smaller than [MIN_TILE]. */
    fun resized(tree: FlexNode?, d: FlexDivider, travelled: Float): FlexNode? {
        if (d.index + 1 >= d.sizes.size) return tree
        val a = d.sizes[d.index]
        val b = d.sizes[d.index + 1]
        val low = -(a - MIN_TILE).coerceAtLeast(0f)
        val high = (b - MIN_TILE).coerceAtLeast(0f)
        val t = travelled.coerceIn(low, high)
        val sizes = d.sizes.toMutableList()
        sizes[d.index] = a + t
        sizes[d.index + 1] = b - t
        return setWeights(tree, d.path, sizes)
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
 * What dropping something held at card pixel [x], [y] would do: the cell under it (the space the tiling gave a
 * tile, which can be bigger than the tile itself) and which part of it.
 */
internal fun dropSpot(plan: FlexPlan, dragged: Int, x: Float, y: Float): DropSpot? {
    val t = plan.boxes.firstOrNull { it.id != dragged && it.cellContains(x, y) } ?: return null
    val dx = x - (t.cellLeft + t.cellWidth / 2f)
    val dy = y - (t.cellTop + t.cellHeight / 2f)
    if (abs(dx) < t.cellWidth * 0.2f && abs(dy) < t.cellHeight * 0.2f) return DropSpot(t.id, DropZone.SWAP)
    val zone = if (abs(dx) / t.cellWidth > abs(dy) / t.cellHeight) {
        if (dx < 0f) DropZone.LEFT else DropZone.RIGHT
    } else {
        if (dy < 0f) DropZone.TOP else DropZone.BOTTOM
    }
    return DropSpot(t.id, zone)
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

    /** The widest line of the least width a node can have without breaking a word or cutting text. */
    private fun floorOf(node: FlexNode, gap: Float, need: (Int) -> Float): Float = when (node) {
        is FlexLeaf -> need(node.id) * MIN_FLOOR_SCALE
        is FlexGroup -> if (node.vertical) {
            node.children.maxOf { floorOf(it, gap, need) }
        } else {
            node.children.sumOf { floorOf(it, gap, need).toDouble() }.toFloat() + gap * (node.children.size - 1)
        }
    }

    /** Side by side children share the width by how much each wants (or by their real shares). */
    private fun widthShares(g: FlexGroup, gap: Float, hug: (Int) -> Float): List<Float> {
        if (!g.isAuto) return fractionsOf(g)
        val wants = g.children.map { hugOf(it, gap, hug).coerceAtLeast(1f) }
        val total = wants.sum()
        return wants.map { it / total }
    }

    /**
     * The height [node] needs when it is [w] wide; [nat] says how tall a block's content is at a width.
     * A group with real shares needs enough for the tile that is the tightest fit for its share.
     */
    fun natural(node: FlexNode, w: Float, gap: Float, nat: (Int, Float) -> Float, hug: (Int) -> Float): Float = when (node) {
        is FlexLeaf -> nat(node.id, w)
        is FlexGroup -> {
            val gaps = gap * (node.children.size - 1)
            if (node.vertical) {
                if (node.isAuto) {
                    node.children.sumOf { natural(it, w, gap, nat, hug).toDouble() }.toFloat() + gaps
                } else {
                    val fr = fractionsOf(node)
                    var most = 0f
                    node.children.forEachIndexed { i, child ->
                        most = max(most, natural(child, w, gap, nat, hug) / fr[i].coerceAtLeast(0.02f))
                    }
                    most + gaps
                }
            } else {
                val fr = widthShares(node, gap, hug)
                var most = 0f
                node.children.forEachIndexed { i, child -> most = max(most, natural(child, (w - gaps) * fr[i], gap, nat, hug)) }
                most
            }
        }
    }

    /**
     * Lays [root] out in the box x, y, w, h into cells: the space each tile may use, which fills the box. [nat] is
     * a block's natural height at a width, [hug] the width it wants, [need] the least width it can have without
     * breaking a word or cutting text, and [cap] the most a block's cell may take along an axis (photos are
     * held to half the card), or a huge number for no limit.
     */
    fun solve(
        root: FlexNode?, x: Float, y: Float, w: Float, h: Float, gap: Float,
        nat: (Int, Float) -> Float,
        cap: (id: Int, vertical: Boolean) -> Float,
        hug: (Int) -> Float,
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
                    val needs = g.children.map { natural(it, pw, gap, nat, hug).coerceAtLeast(1f) }
                    val total = needs.sum()
                    needs.map { it / total * axis }
                }
                else -> widthShares(g, gap, hug).map { it * axis }
            }
            val floors = g.children.map { if (g.vertical) 0f else floorOf(it, gap, need) }
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

/** A tile may be given this much of the width its block needs to keep its words whole, since content can shrink to that scale. */
private const val MIN_FLOOR_SCALE = 0.6f
