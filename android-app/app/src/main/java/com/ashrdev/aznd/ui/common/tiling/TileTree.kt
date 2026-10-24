package com.ashrdev.aznd.ui.common.tiling

import kotlin.math.abs
import kotlin.math.max

sealed interface TileNode

data class TileLeaf(val id: Int) : TileNode

data class TileGroup(val vertical: Boolean, val children: List<TileNode>, val weights: List<Float>) : TileNode {
    val isAuto: Boolean get() = weights.all { it <= 0f }
}

enum class DropZone { LEFT, RIGHT, TOP, BOTTOM, SWAP }

enum class DropKind { GAP, EDGE, SIDE, SWAP }

class DropSpot(
    val target: Int,
    val zone: DropZone,
    val span: Set<Int> = setOf(target),
    val region: FloatArray = FloatArray(4),
    val tree: TileNode? = null,
    val kind: DropKind = DropKind.SIDE,
    val bar: FloatArray = region
) {
    val spans: Boolean get() = span.size > 1

    fun sameAs(other: DropSpot?): Boolean =
        other != null && other.kind == kind && other.zone == zone && other.target == target && other.span == span && other.tree == tree
}

class TileDivider(
    val path: List<Int>,
    val index: Int,
    val vertical: Boolean,
    val cx: Float,
    val cy: Float,
    val sizes: List<Float>,
    val adjacent: Set<Int>,
    val span: Float = 0f
)

class TileGroupRef(val path: List<Int>, val index: Int, val group: TileGroup)

class TileSolved(
    val rects: Map<Int, FloatArray>,
    val dividers: List<TileDivider>,
    val groupSizes: Map<List<Int>, List<Float>>,
    val levels: Map<Int, Float> = emptyMap(),
    val zoom: Float = 1f
)

object TileTree {

    fun leaves(node: TileNode?): List<Int> = when (node) {
        null -> emptyList()
        is TileLeaf -> listOf(node.id)
        is TileGroup -> node.children.flatMap { leaves(it) }
    }

    fun banner(node: TileNode?): Int? = when (node) {
        null -> null
        is TileLeaf -> node.id
        is TileGroup -> if (node.vertical) banner(node.children.firstOrNull()) else null
    }

    fun prune(node: TileNode?, keep: Set<Int>): TileNode? = when (node) {
        null -> null
        is TileLeaf -> if (node.id in keep) node else null
        is TileGroup -> {
            val kids = ArrayList<TileNode>()
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

    private fun tidy(vertical: Boolean, kids: List<TileNode>, rawWeights: List<Float>): TileNode? {
        if (kids.isEmpty()) return null
        if (kids.size == 1) return kids[0]
        val ws = evened(rawWeights)
        val explicit = ws.any { it > 0f }
        val outKids = ArrayList<TileNode>()
        val outWs = ArrayList<Float>()
        kids.forEachIndexed { i, child ->
            if (child is TileGroup && child.vertical == vertical && (explicit || child.isAuto)) {
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
        return TileGroup(vertical, outKids, outWs)
    }

    private fun evened(ws: List<Float>): List<Float> {
        val positive = ws.filter { it > 0f }
        if (positive.isEmpty() || positive.size == ws.size) return ws.map { max(it, 0f) }
        val avg = positive.sum() / positive.size
        return ws.map { if (it > 0f) it else avg }
    }

    private fun zeros(n: Int): List<Float> = List(n) { 0f }

    fun autoTree(ids: List<Int>, wide: Boolean): TileNode? {
        val leaves = ids.map { TileLeaf(it) }
        return when {
            ids.isEmpty() -> null
            ids.size == 1 -> leaves[0]
            wide && ids.size <= 3 -> TileGroup(false, leaves, zeros(leaves.size))
            wide -> {
                val half = (leaves.size + 1) / 2
                TileGroup(false, listOf(column(leaves.take(half)), column(leaves.drop(half))), zeros(2))
            }
            else -> TileGroup(true, leaves, zeros(leaves.size))
        }
    }

    private fun column(l: List<TileNode>): TileNode = if (l.size == 1) l[0] else TileGroup(true, l, zeros(l.size))

    fun effective(layout: TileNode?, shown: List<Int>, wide: Boolean): TileNode? {
        if (shown.isEmpty()) return null
        var tree: TileNode = prune(layout, shown.toSet()) ?: return autoTree(shown, wide)
        val have = leaves(tree).toMutableSet()
        for ((i, id) in shown.withIndex()) {
            if (id in have) continue
            val before = shown.subList(0, i).lastOrNull { it in have }
            tree = insertRow(tree, id, before)
            have += id
        }
        return tree
    }

    fun append(root: TileNode, id: Int): TileNode = insertRow(root, id, leaves(root).lastOrNull())

    private fun insertRow(root: TileNode, id: Int, after: Int?): TileNode {
        val leaf = TileLeaf(id)
        if (root !is TileGroup || !root.vertical) {
            return if (after == null) TileGroup(true, listOf(leaf, root), zeros(2)) else TileGroup(true, listOf(root, leaf), zeros(2))
        }
        val at = if (after == null) 0 else root.children.indexOfFirst { after in leaves(it) }.let { if (it < 0) root.children.size else it + 1 }
        val kids = root.children.toMutableList().also { it.add(at, leaf) }
        val ws = if (root.isAuto) zeros(kids.size) else root.weights.toMutableList().also { it.add(at, root.weights.average().toFloat()) }
        return TileGroup(true, kids, ws)
    }

    fun moveTo(root: TileNode?, dragged: Int, target: Int, zone: DropZone): TileNode? {
        if (root == null || dragged == target) return null
        if (zone == DropZone.SWAP) return evenParent(evenParent(swap(root, dragged, target), dragged), target)
        return moveBeside(root, dragged, setOf(target), zone)
    }

    fun moveBeside(root: TileNode?, dragged: Int, span: Set<Int>, zone: DropZone): TileNode? {
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

    private fun insertAt(node: TileNode, span: Set<Int>, id: Int, vertical: Boolean, before: Boolean): TileNode {
        val leaf = TileLeaf(id)
        fun wrap(n: TileNode): TileNode = TileGroup(vertical, if (before) listOf(leaf, n) else listOf(n, leaf), zeros(2))
        if (leaves(node).toSet() == span) {
            if (node is TileGroup && node.vertical == vertical) {
                val kids = if (before) listOf(leaf) + node.children else node.children + leaf
                return TileGroup(vertical, kids, zeros(kids.size))
            }
            return wrap(node)
        }
        if (node !is TileGroup) return node
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
                        return TileGroup(node.vertical, kids, zeros(kids.size))
                    }
                    val run = TileGroup(node.vertical, node.children.slice(a..b), node.weights.slice(a..b))
                    val share = node.weights.slice(a..b).sum()
                    for (k in b downTo a) {
                        kids.removeAt(k)
                        ws.removeAt(k)
                    }
                    kids.add(a, if (a == b) wrap(node.children[a]) else wrap(run))
                    ws.add(a, share)
                    return TileGroup(node.vertical, kids, ws)
                }
            }
        }
        val i = sets.indexOfFirst { it.containsAll(span) }
        if (i < 0) return wrap(node)
        val kids = node.children.toMutableList()
        kids[i] = insertAt(kids[i], span, id, vertical, before)
        return TileGroup(node.vertical, kids, node.weights)
    }

    private fun evenParent(node: TileNode, id: Int): TileNode {
        if (node !is TileGroup) return node
        if (node.children.any { it is TileLeaf && it.id == id }) return TileGroup(node.vertical, node.children, zeros(node.children.size))
        return TileGroup(node.vertical, node.children.map { evenParent(it, id) }, node.weights)
    }

    fun swap(root: TileNode, a: Int, b: Int): TileNode = mapLeaves(root) { id ->
        when (id) {
            a -> b
            b -> a
            else -> id
        }
    }

    private fun mapLeaves(node: TileNode, f: (Int) -> Int): TileNode = when (node) {
        is TileLeaf -> TileLeaf(f(node.id))
        is TileGroup -> TileGroup(node.vertical, node.children.map { mapLeaves(it, f) }, node.weights)
    }

    fun relabel(root: TileNode, order: List<Int>): TileNode {
        val sorted = leaves(root).sortedBy { id -> order.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it } }
        var next = 0
        return mapLeaves(root) { sorted[next++] }
    }

    fun refOf(root: TileNode?, id: Int, path: List<Int> = emptyList()): TileGroupRef? {
        if (root !is TileGroup) return null
        root.children.forEachIndexed { i, child ->
            if (child is TileLeaf && child.id == id) return TileGroupRef(path, i, root)
            refOf(child, id, path + i)?.let { return it }
        }
        return null
    }

    fun setWeights(node: TileNode?, path: List<Int>, weights: List<Float>): TileNode? {
        if (node == null) return null
        val group = node as? TileGroup ?: return node
        if (path.isEmpty()) {
            if (group.children.size != weights.size) return group
            return TileGroup(group.vertical, group.children, weights.map { it.coerceAtLeast(0f) })
        }
        val i = path[0]
        if (i !in group.children.indices) return group
        val kids = group.children.toMutableList()
        kids[i] = setWeights(kids[i], path.drop(1), weights) ?: kids[i]
        return TileGroup(group.vertical, kids, group.weights)
    }

    fun resized(tree: TileNode?, d: TileDivider, travelled: Float, rules: TileRules): TileNode? {
        if (d.index + 1 >= d.sizes.size) return tree
        val a = d.sizes[d.index]
        val b = d.sizes[d.index + 1]
        val low = -(a - rules.minTile).coerceAtLeast(0f)
        val high = (b - rules.minTile).coerceAtLeast(0f)
        var t = travelled.coerceIn(low, high)
        val even = (b - a) / 2f
        if (abs(t - even) < rules.resizeSnap * (a + b) && even in low..high) t = even
        val sizes = d.sizes.toMutableList()
        sizes[d.index] = a + t
        sizes[d.index + 1] = b - t
        return setWeights(tree, d.path, sizes)
    }

    fun turned(root: TileNode?, id: Int): TileNode? = editParent(root, id) { g ->
        TileGroup(!g.vertical, g.children, zeros(g.children.size))
    }

    fun flipped(root: TileNode?, id: Int): TileNode? = editParent(root, id) { g ->
        TileGroup(g.vertical, g.children.reversed(), g.weights.reversed())
    }

    fun groupPaths(node: TileNode?, path: List<Int> = emptyList()): List<List<Int>> {
        if (node !is TileGroup) return emptyList()
        return listOf(path) + node.children.flatMapIndexed { i, c -> groupPaths(c, path + i) }
    }

    fun groupAt(node: TileNode?, path: List<Int>): TileGroup? =
        path.fold(node) { n, i -> (n as? TileGroup)?.children?.getOrNull(i) } as? TileGroup

    fun relaxed(root: TileNode?): TileNode? = when (root) {
        null -> null
        is TileLeaf -> root
        is TileGroup -> TileGroup(root.vertical, root.children.map { relaxed(it)!! }, zeros(root.children.size))
    }

    fun sameShape(a: TileNode?, b: TileNode?): Boolean = relaxed(a) == relaxed(b)

    private fun editParent(root: TileNode?, id: Int, edit: (TileGroup) -> TileGroup): TileNode? {
        val ref = refOf(root, id) ?: return null
        fun at(node: TileNode, path: List<Int>): TileNode {
            if (path.isEmpty()) return edit(node as TileGroup)
            val g = node as TileGroup
            val kids = g.children.toMutableList()
            kids[path[0]] = at(kids[path[0]], path.drop(1))
            return TileGroup(g.vertical, kids, g.weights)
        }
        val out = at(root!!, ref.path)
        return prune(out, leaves(out).toSet())
    }
}
