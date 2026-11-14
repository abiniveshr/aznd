package com.ashrdev.aznd.ui.common.tiling

import kotlin.math.min

/**
 * Flow's tiling of a card is one stack, one tile under the next, and it never puts tiles side by side by itself. When
 * the rules allow it ([TileRules.pairsSmall]) this offers one exception: a small block beside the one before or after
 * it, where that leaves a card with clearly less room that shows nothing.
 *
 * The rule: only neighbours in reading order are paired, each tile in at most one pair, and a pair is
 *  - a picture with a statistic, or with a short text (its height across the whole card at natural size is at most
 *    [SHORT] of the card's height), or
 *  - two statistics.
 * Never a title, never two pictures. A pairing is taken only when, solved the way the card is drawn, nothing is
 * clipped, no text or statistic is drawn smaller than it is in the plain stack (or than natural size, whichever is
 * less) and the room that shows nothing falls by at least [CLEARLY]; then the cheapest such pairing wins. A tiling
 * the person made is not offered here at all: the caller passes only the automatic stack.
 */
object TileFlow {
    const val SHORT = 0.3f
    const val CLEARLY = 0.02

    private class Measured(val dead: Double, val cut: Boolean, val scales: Map<Int, Float>)

    fun paired(stack: TileNode?, items: Map<Int, TileItem>, gap: Float, rules: TileRules, x: Float, y: Float, w: Float, h: Float): TileNode? {
        if (!rules.pairsSmall || stack !is TileGroup || !stack.vertical || h <= 0f) return stack
        if (stack.children.any { it !is TileLeaf }) return stack
        val ids = stack.children.map { (it as TileLeaf).id }
        if (ids.any { items[it] == null }) return stack
        val base = measure(stack, items, gap, rules, x, y, w, h) ?: return stack
        var best: TileNode? = null
        var bestDead = base.dead - CLEARLY
        for (shape in shapes(ids, items, w, h, gap, rules)) {
            val pairs = shape.count { it.size == 2 }
            if (pairs == 0) continue
            val tree = build(shape)
            val m = measure(tree, items, gap, rules, x, y, w, h) ?: continue
            if (m.cut) continue
            var worse = false
            for ((id, s) in m.scales) {
                val was = base.scales[id] ?: continue
                if (s < min(1f, was) - 0.005f) worse = true
            }
            if (worse) continue
            if (m.dead < bestDead - 1e-9) {
                best = tree
                bestDead = m.dead
            }
        }
        return best ?: stack
    }

    private fun build(shape: List<List<Int>>): TileNode {
        val nodes = shape.map { p ->
            if (p.size == 1) TileLeaf(p[0]) else TileGroup(false, p.map { TileLeaf(it) }, listOf(0f, 0f))
        }
        return if (nodes.size == 1) nodes[0] else TileGroup(true, nodes, List(nodes.size) { 0f })
    }

    /** Every way to leave tiles single or pair neighbours that may be paired, in reading order. */
    private fun shapes(ids: List<Int>, items: Map<Int, TileItem>, w: Float, h: Float, gap: Float, rules: TileRules): List<List<List<Int>>> {
        val out = ArrayList<List<List<Int>>>()
        fun walk(i: Int, acc: List<List<Int>>) {
            if (out.size >= LIMIT) return
            if (i >= ids.size) { out += acc; return }
            if (i + 1 < ids.size && canPair(items.getValue(ids[i]), items.getValue(ids[i + 1]), w, h)) walk(i + 2, acc + listOf(listOf(ids[i], ids[i + 1])))
            walk(i + 1, acc + listOf(listOf(ids[i])))
        }
        walk(0, emptyList())
        return out
    }

    private const val LIMIT = 64

    private fun small(it: TileItem, w: Float, h: Float): Boolean = when (it.kind) {
        TileKind.STATISTIC -> true
        TileKind.TEXT -> (it.text?.invoke(w, 1f) ?: h) <= SHORT * h
        else -> false
    }

    private fun canPair(a: TileItem, b: TileItem, w: Float, h: Float): Boolean {
        if (a.kind == TileKind.TITLE || b.kind == TileKind.TITLE) return false
        if (a.kind == TileKind.DRAWABLE && b.kind == TileKind.DRAWABLE) return false
        if (a.kind == TileKind.DRAWABLE) return b.pic != null && a.pic?.hasContent == true && small(b, w, h)
        if (b.kind == TileKind.DRAWABLE) return a.pic != null && b.pic?.hasContent == true && small(a, w, h)
        return a.kind == TileKind.STATISTIC && b.kind == TileKind.STATISTIC
    }

    private fun measure(tree: TileNode, items: Map<Int, TileItem>, gap: Float, rules: TileRules, x: Float, y: Float, w: Float, h: Float): Measured? {
        val solver = TileSolver(items, gap, rules)
        val fitter = TileFitter(rules)
        val solved = solver.solve(tree, x, y, w, h)
        val banner = TileTree.banner(tree)
        var covered = 0.0
        var cut = false
        val scales = HashMap<Int, Float>()
        for ((id, item) in items) {
            val r = solved.rects[id] ?: return null
            val top = solver.leaf(id, r[2], solved.levels[id] ?: TileZoom.Q_NATURAL).scale
            val cw = r[2]
            val ch = r[3]
            val f: TileFit = when (item.kind) {
                TileKind.DRAWABLE -> fitter.drawable(item.pic ?: return null, cw, ch)
                TileKind.STATISTIC -> {
                    val shape = item.stat ?: return null
                    val a = fitter.statAt(shape, cw, ch, top, rules.statMinScale)
                    TileFit(cw, min(a.height, ch), a.scale, a.narrow || a.height > ch + 1f, a.columns)
                }
                TileKind.TITLE -> fitter.title(cw, ch, item.need, if (id == banner) item.air else 0f, { t -> item.text!!(t, 1f) }, { _, _ -> cw }, top)
                TileKind.TEXT -> fitter.text(cw, ch, item.need, { t -> item.text!!(t, 1f) }, { _, _ -> cw }, maxScale = top)
            }
            if (f.cut) cut = true
            covered += f.w.toDouble() * f.h
            if (item.kind != TileKind.DRAWABLE) scales[id] = f.scale
        }
        return Measured(1.0 - covered / (w.toDouble() * h), cut, scales)
    }
}
