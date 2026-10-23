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

    /**
     * The block at the top of [node] that runs across its whole width, if any: the first of a stack, as deep as
     * stacks go; none when the top is a row. Only such a title keeps air above it (see [FlexStyle.titleInset]): a title
     * in a row beside other tiles lines up with their tops.
     */
    fun banner(node: FlexNode?): Int? = when (node) {
        null -> null
        is FlexLeaf -> node.id
        is FlexGroup -> if (node.vertical) banner(node.children.firstOrNull()) else null
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

    /** The paths of every group in [node] (the root's is empty), parents before their children. */
    fun groupPaths(node: FlexNode?, path: List<Int> = emptyList()): List<List<Int>> {
        if (node !is FlexGroup) return emptyList()
        return listOf(path) + node.children.flatMapIndexed { i, c -> groupPaths(c, path + i) }
    }

    /** The group at [path], or null. */
    fun groupAt(node: FlexNode?, path: List<Int>): FlexGroup? =
        path.fold(node) { n, i -> (n as? FlexGroup)?.children?.getOrNull(i) } as? FlexGroup

    /** [root] with the group at [path] replaced by what [edit] makes of it, tidied (null when there is none). */
    fun editAt(root: FlexNode?, path: List<Int>, edit: (FlexGroup) -> FlexNode): FlexNode? {
        if (groupAt(root, path) == null) return null
        fun at(node: FlexNode, rest: List<Int>): FlexNode {
            val g = node as FlexGroup
            if (rest.isEmpty()) return edit(g)
            val kids = g.children.toMutableList()
            kids[rest[0]] = at(kids[rest[0]], rest.drop(1))
            return FlexGroup(g.vertical, kids, g.weights)
        }
        val out = at(root!!, path)
        return prune(out, leaves(out).toSet())
    }

    /** The group at [path] split the other way, sharing by content. */
    fun turnedAt(root: FlexNode?, path: List<Int>): FlexNode? = editAt(root, path) { g -> FlexGroup(!g.vertical, g.children, zeros(g.children.size)) }

    /** The group at [path] with its parts [i] and [i] + 1 changed places (sharing by content). */
    fun swappedAt(root: FlexNode?, path: List<Int>, i: Int): FlexNode? = editAt(root, path) { g ->
        if (i + 1 >= g.children.size) g
        else FlexGroup(g.vertical, g.children.toMutableList().also { val t = it[i]; it[i] = it[i + 1]; it[i + 1] = t }, zeros(g.children.size))
    }

    /** The group at [path] with its parts [i] and [i] + 1 put in a group of their own, cut the other way: one level more. */
    fun pairedAt(root: FlexNode?, path: List<Int>, i: Int): FlexNode? = editAt(root, path) { g ->
        if (g.children.size < 3 || i + 1 >= g.children.size) g
        else {
            val pair = FlexGroup(!g.vertical, g.children.subList(i, i + 2), zeros(2))
            FlexGroup(g.vertical, g.children.take(i) + pair + g.children.drop(i + 2), zeros(g.children.size - 1))
        }
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

/**
 * How a stack that shares by content shares [axis] (its height without the gaps). Every part first gets what its
 * content [needs] (within its [floors] and [caps]). Short of room, the parts shrink together, none below its floor
 * (while the floors fit). With room to spare, the spare goes by [appetite] (pictures first, a title hardly any) to
 * the parts that can still use it, each up to its cap: what a capped part cannot use goes on to the others by their
 * appetite, never to a part by its size. Room no part can use is left over (under the stack), except that parts that
 * can stretch ([stretchy]: panels in a stack beside a taller part) take it, so the edges line up.
 */
internal fun stackShares(
    axis: Float,
    needs: List<Float>,
    caps: List<Float>,
    floors: List<Float>,
    appetite: List<Float>,
    stretchy: List<Boolean>
): List<Float> {
    val n = needs.size
    if (n == 0) return emptyList()
    val base = FloatArray(n) { needs[it].coerceIn(floors[it], max(floors[it], caps[it])) }
    val total = base.sum()
    if (total > axis + 0.01f) return distribute(axis, base.toList(), base.toList(), floors)
    val out = base.copyOf()
    var spare = axis - total
    fun pour(open: (Int) -> Boolean, capped: Boolean) {
        for (round in 0..n) {
            if (spare < 0.5f) return
            val idx = (0 until n).filter { open(it) && (!capped || out[it] < caps[it] - 0.5f) }
            if (idx.isEmpty()) return
            val w = idx.sumOf { max(appetite[it], 0.01f).toDouble() }.toFloat()
            var used = 0f
            for (i in idx) {
                val add = spare * max(appetite[i], 0.01f) / w
                val give = if (capped) min(add, caps[i] - out[i]) else add
                out[i] += give
                used += give
            }
            spare -= used
            if (used < 0.25f) return
        }
    }
    pour({ appetite[it] > 0f }, capped = true)
    pour({ stretchy[it] }, capped = false)
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
        g: FlexGroup, axis: Float, gap: Float, nat: (Int, Float) -> Float, hug: (Int) -> Float, need: (Int) -> Float,
        byContentDown: Boolean = false
    ): List<Float> {
        if (!g.isAuto) return fractionsOf(g)
        val wants = g.children.map { hugOf(it, gap, hug).coerceAtLeast(1f) }
        val total = wants.sum()
        val byWant = wants.map { it / total }
        if (g.children.size != 2 || axis <= 0f) return byWant
        val a = g.children[0]
        val b = g.children[1]
        fun uneven(f: Float): Float =
            abs(natural(a, axis * f, gap, nat, hug, need, byContentDown) - natural(b, axis * (1f - f), gap, nat, hug, need, byContentDown))
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
    fun natural(
        node: FlexNode, w: Float, gap: Float, nat: (Int, Float) -> Float, hug: (Int) -> Float, need: (Int) -> Float,
        /** Free size: stacks always share by content (their height is what the content needs; shares would only add room). */
        byContentDown: Boolean = false
    ): Float = when (node) {
        is FlexLeaf -> nat(node.id, w)
        is FlexGroup -> {
            val gaps = gap * (node.children.size - 1)
            if (node.vertical) {
                if (node.isAuto || byContentDown) {
                    node.children.sumOf { natural(it, w, gap, nat, hug, need, byContentDown).toDouble() }.toFloat() + gaps
                } else {
                    val fr = fractionsOf(node)
                    var most = 0f
                    node.children.forEachIndexed { i, child ->
                        most = max(most, natural(child, w, gap, nat, hug, need, byContentDown) / fr[i].coerceAtLeast(0.02f))
                    }
                    most + gaps
                }
            } else {
                val fr = widthShares(node, w - gaps, gap, nat, hug, need, byContentDown)
                var most = 0f
                node.children.forEachIndexed { i, child -> most = max(most, natural(child, (w - gaps) * fr[i], gap, nat, hug, need, byContentDown)) }
                most
            }
        }
    }

    /**
     * The least height [node] may be given at the width [w] (see [solve]'s [least]): a stack the sum of its parts' and
     * the gaps, a row its tallest part's at the width the row gives it. A tiling whose least height is no more than the
     * card's content height shows every module at its least or better: that is a card's content budget.
     */
    fun leastHeight(
        node: FlexNode, w: Float, gap: Float, nat: (Int, Float) -> Float, hug: (Int) -> Float, need: (Int) -> Float,
        least: (id: Int, w: Float) -> Float, byContentDown: Boolean = false
    ): Float {
        if (node is FlexLeaf) return least(node.id, w)
        val g = node as FlexGroup
        val gaps = gap * (g.children.size - 1)
        if (g.vertical) return g.children.sumOf { leastHeight(it, w, gap, nat, hug, need, least, byContentDown).toDouble() }.toFloat() + gaps
        val room = (w - gaps).coerceAtLeast(1f)
        val shares = if (!g.isAuto) fractionsOf(g) else widthShares(g, room, gap, nat, hug, need, byContentDown)
        var most = 0f
        g.children.forEachIndexed { i, child -> most = max(most, leastHeight(child, room * shares[i], gap, nat, hug, need, least, byContentDown)) }
        return most
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
        /** [cross]: the cell's size across the split (a picture's height cap depends on how wide it is). */
        cap: (id: Int, vertical: Boolean, cross: Float) -> Float,
        hug: (Int) -> Float,
        floor: (Int) -> Float,
        need: (Int) -> Float,
        /**
         * How much of a stack's spare height a block takes (a stack that shares by content and is taller than its
         * content: a column beside a taller one, a fixed-size card): drawables most, a title hardly any, so the room
         * goes where it can be used (a photo grows, a panel stretches) instead of under every tile alike.
         */
        grow: (Int) -> Float = { 1f },
        /** Free size: stacks always share by content (see [natural]). */
        byContentDown: Boolean = false,
        /**
         * The least height a block's cell may have at a width: what its content needs at its least scale (a picture:
         * its least visible size). A stack short of room shrinks its parts, never one below this while the least
         * heights fit (see [FlexPainter.load]: a card within its budget always has room for them).
         */
        least: (id: Int, w: Float) -> Float = { _, _ -> 0f },
        /**
         * Blocks whose panel can stretch to a taller cell (statistics and panelled text that span their cell): in a
         * stack inside a row, the room their content cannot use goes to them, so the row's edges line up.
         */
        stretch: (Int) -> Boolean = { false },
        /**
         * The most height a block's cell can use at a width: its content at the most it grows (a picture: its cap). A
         * stack that shares by content gives no part more than this; the room goes to parts that can use it. (Parts
         * whose shares the person set keep them.)
         */
        most: (id: Int, w: Float) -> Float = { _, _ -> BIG }
    ): FlexSolved {
        val rects = LinkedHashMap<Int, FloatArray>()
        val dividers = ArrayList<FlexDivider>()
        val groupSizes = HashMap<List<Int>, List<Float>>()

        // The most a node may take along an axis. Along its own split a group takes the sum of its children's;
        // across it, the tightest of them.
        fun capOf(node: FlexNode, vertical: Boolean, cross: Float): Float {
            if (node is FlexLeaf) return cap(node.id, vertical, cross)
            val g = node as FlexGroup
            if (g.vertical == vertical) {
                var sum = gap * (g.children.size - 1)
                for (child in g.children) {
                    val c = capOf(child, vertical, cross)
                    if (c >= BIG) return BIG
                    sum += c
                }
                return sum
            }
            // Across the split the children share the cross size: each is capped at its own share of it (a photo in a
            // row is as tall as its own width lets it be, not the row's). A row is as tall, and a column as wide, as its
            // most able part can use (the others sit in bigger cells: a picture in the middle of its own).
            val gaps = gap * (g.children.size - 1)
            val room = (cross - gaps).coerceAtLeast(1f)
            val shares = when {
                !g.isAuto -> fractionsOf(g)
                !g.vertical -> widthShares(g, room, gap, nat, hug, need, byContentDown)
                else -> {
                    val needs = g.children.map { natural(it, room, gap, nat, hug, need, byContentDown).coerceAtLeast(1f) }
                    needs.map { it / needs.sum() }
                }
            }
            val caps = g.children.mapIndexed { i, child -> capOf(child, vertical, room * shares[i]) }
            return caps.max()
        }

        fun leastOf(node: FlexNode, w: Float): Float = leastHeight(node, w, gap, nat, hug, need, least, byContentDown)

        // The most height a node can use at width [w]: a stack the sum of its parts', a row its tallest part's.
        fun mostOf(node: FlexNode, w: Float): Float {
            if (node is FlexLeaf) return most(node.id, w)
            val g = node as FlexGroup
            val gaps = gap * (g.children.size - 1)
            if (g.vertical) {
                var sum = gaps
                for (child in g.children) {
                    val c = mostOf(child, w)
                    if (c >= BIG) return BIG
                    sum += c
                }
                return sum
            }
            val room = (w - gaps).coerceAtLeast(1f)
            val shares = if (!g.isAuto) fractionsOf(g) else widthShares(g, room, gap, nat, hug, need, byContentDown)
            var top = 0f
            g.children.forEachIndexed { i, child -> top = max(top, mostOf(child, room * shares[i])) }
            return top
        }

        fun place(node: FlexNode, px: Float, py: Float, pw: Float, ph: Float, path: List<Int>) {
            if (node is FlexLeaf) {
                rects[node.id] = floatArrayOf(px, py, pw, ph)
                return
            }
            val g = node as FlexGroup
            val n = g.children.size
            val axis = ((if (g.vertical) ph else pw) - gap * (n - 1)).coerceAtLeast(0f)
            val cross = if (g.vertical) pw else ph
            val caps = g.children.map { capOf(it, g.vertical, cross) }
            val sizes: List<Float> = if (g.vertical && (g.isAuto || byContentDown)) {
                val needs = g.children.map { natural(it, pw, gap, nat, hug, need, byContentDown).coerceAtLeast(1f) }
                val floors = g.children.map { leastOf(it, pw) }
                val appetite = g.children.map { child -> FlexTree.leaves(child).maxOfOrNull { grow(it) } ?: 1f }
                // A stack inside a row lines up with the row's other parts: what its content cannot use goes to its panels.
                val nested = path.isNotEmpty()
                val stretchy = g.children.map { child -> nested && FlexTree.leaves(child).any { stretch(it) } }
                val usable = g.children.mapIndexed { i, child -> min(caps[i], mostOf(child, pw)) }
                stackShares(axis, needs, usable, floors, appetite, stretchy)
            } else if (g.vertical) {
                // Shares set by hand or by best fit: each part gets its share, but never below its least height (while
                // those fit) nor more than its content can take up; what a part cannot use goes to the others as in a
                // stack that shares by content (pictures first), not into empty space under a title.
                val desired = fractionsOf(g).map { it * axis }
                val floors = g.children.map { leastOf(it, pw) }
                val usable = g.children.mapIndexed { i, child -> min(caps[i], mostOf(child, pw)) }
                val appetite = g.children.map { child -> FlexTree.leaves(child).maxOfOrNull { grow(it) } ?: 1f }
                val nested = path.isNotEmpty()
                val stretchy = g.children.map { child -> nested && FlexTree.leaves(child).any { stretch(it) } }
                stackShares(axis, desired, usable, floors, appetite, stretchy)
            } else {
                val desired = if (!g.isAuto) fractionsOf(g).map { it * axis } else widthShares(g, axis, gap, nat, hug, need, byContentDown).map { it * axis }
                distribute(axis, desired, caps, g.children.map { floorOf(it, gap, floor) })
            }
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

/**
 * Free mode: where a box being moved or resized snaps to, as smart guides in a slide editor do. A box's left, centre
 * and right snap to the left, centre and right of every other box and of the card's content area (its margins and
 * middle), and the same down; only within [SNAP_REACH] card pixels, so the box goes where it is put otherwise.
 * [guidesX] / [guidesY] are the lines it snapped to (to draw while it is held).
 */
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

/** Flow mode: what part of the card a held block is over (see [flowZone]). */
enum class FlowZoneKind {
    /** Its own place: nothing changes. */
    HOME,
    /** The gap between two rows (or above the first, below the last): a row of its own there. */
    GAP,
    /** The gap between two tiles of a row: between them, in that row. */
    IN_ROW,
    /** On a tile: beside it in its row, on the side the finger is on (a split of that row's room). */
    BESIDE
}

/**
 * Flow mode: a drop zone. [rows] are the rows the drop makes; [fallback], for a gap in a row that fails the size
 * check, the rows it makes instead (a row of its own after that row); [target] the tile a [FlowZoneKind.BESIDE] drop
 * goes beside; [region] the part of the card (x, y, width, height) the zone is, to show while it is aimed.
 */
class FlowZone(
    val kind: FlowZoneKind,
    val rows: List<List<Int>>,
    val fallback: List<List<Int>>? = null,
    val target: Int? = null,
    val region: FloatArray = FloatArray(4)
)

/** How far either side of a gap's middle (beyond half the gap) a held block counts as in the gap, in card pixels. */
const val FLOW_GAP_REACH = 42f

/**
 * Flow mode: the zone a block [dragged] held at card pixel [x], [y] is over, by the card as it is ([plan]). Near the
 * gap between two rows (within [FLOW_GAP_REACH] beyond half the gap, and above the first row or below the last) it is
 * that gap; near the gap between two tiles of a row, that gap; else on a tile, beside it (its left or right half);
 * on its own tile, home. Null outside every zone (the margins at the sides).
 */
internal fun flowZone(plan: FlexPlan, dragged: Int, x: Float, y: Float, reach: Float = FLOW_GAP_REACH): FlowZone? {
    val rows = FlexPainter.flowRows(plan.tree)
    if (rows.isEmpty()) return null
    val cells = plan.boxes.associate { it.id to floatArrayOf(it.cellLeft, it.cellTop, it.cellWidth, it.cellHeight) }
    fun rect(ids: List<Int>): FloatArray {
        val l = ids.minOf { cells.getValue(it)[0] }
        val t = ids.minOf { cells.getValue(it)[1] }
        val r = ids.maxOf { cells.getValue(it).let { c -> c[0] + c[2] } }
        val b = ids.maxOf { cells.getValue(it).let { c -> c[1] + c[3] } }
        return floatArrayOf(l, t, r - l, b - t)
    }
    if (rows.flatten().any { it !in cells }) return null
    val base = rows.map { r -> r.filter { it != dragged } }.filter { it.isNotEmpty() }
    // Where row [i] of the card as it is falls among the rows without the held block.
    fun baseIndex(i: Int): Int = rows.take(i).count { r -> r.any { it != dragged } }
    val half = plan.gap / 2f + reach
    val rects = rows.map { rect(it) }
    val left = plan.contentLeft
    val width = plan.contentWidth

    // Between rows, above the first and below the last.
    for (i in 0..rows.size) {
        val line = when (i) {
            0 -> rects[0][1]
            rows.size -> rects.last().let { it[1] + it[3] }
            else -> (rects[i - 1][1] + rects[i - 1][3] + rects[i][1]) / 2f
        }
        val near = when (i) {
            0 -> y <= line + half
            rows.size -> y >= line - half
            else -> abs(y - line) <= half
        }
        if (!near) continue
        val result = base.take(baseIndex(i)) + listOf(listOf(dragged)) + base.drop(baseIndex(i))
        val kind = if (result == rows) FlowZoneKind.HOME else FlowZoneKind.GAP
        return FlowZone(kind, result, region = floatArrayOf(left, line - plan.gap / 2f, width, plan.gap))
    }
    // Between two tiles of a row.
    for ((i, r) in rows.withIndex()) {
        val rr = rects[i]
        if (r.size < 2 || y < rr[1] || y > rr[1] + rr[3]) continue
        for (j in 0 until r.size - 1) {
            val a = cells.getValue(r[j])
            val b = cells.getValue(r[j + 1])
            val line = (a[0] + a[2] + b[0]) / 2f
            if (abs(x - line) > half) continue
            val row = r.filter { it != dragged }
            val at = r.take(j + 1).count { it != dragged }
            val inRow = row.take(at) + dragged + row.drop(at)
            val bi = baseIndex(i)
            // The row has two tiles or more, so without the held block it still has one: it is base row [bi].
            val result = base.take(bi) + listOf(inRow) + base.drop(bi + 1)
            val fallback = base.take(baseIndex(i + 1)) + listOf(listOf(dragged)) + base.drop(baseIndex(i + 1))
            val kind = if (result == rows) FlowZoneKind.HOME else FlowZoneKind.IN_ROW
            return FlowZone(kind, result, fallback, region = floatArrayOf(line - plan.gap / 2f, rr[1], plan.gap, rr[3]))
        }
    }
    // On a tile: beside it.
    val t = plan.boxes.firstOrNull { it.cellContains(x, y) } ?: return null
    if (t.id == dragged) return FlowZone(FlowZoneKind.HOME, rows, region = floatArrayOf(t.cellLeft, t.cellTop, t.cellWidth, t.cellHeight))
    val before = x < t.cellLeft + t.cellWidth / 2f
    val result = base.map { r ->
        val k = r.indexOf(t.id)
        if (k < 0) r else r.take(if (before) k else k + 1) + dragged + r.drop(if (before) k else k + 1)
    }
    val kind = if (result == rows) FlowZoneKind.HOME else FlowZoneKind.BESIDE
    return FlowZone(kind, result, target = t.id, region = floatArrayOf(t.cellLeft, t.cellTop, t.cellWidth, t.cellHeight))
}
