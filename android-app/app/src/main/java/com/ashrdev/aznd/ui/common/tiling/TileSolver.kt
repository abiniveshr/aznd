package com.ashrdev.aznd.ui.common.tiling

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val SPLIT_STEPS = 16
private const val BIG = 1e9f

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

private fun fractionsOf(g: TileGroup): List<Float> {
    val n = g.children.size
    if (g.isAuto) return List(n) { 1f / n }
    val sum = g.weights.sum()
    return g.weights.map { it / sum }
}

class TileItem(
    val id: Int,
    val kind: TileKind,
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
    val leastScale: Float = 1f
)

class TileSolver(
    private val items: Map<Int, TileItem>,
    private val gap: Float,
    private val rules: TileRules,
    private val byContent: Boolean = false,
    private val strict: Boolean = false
) {
    private val zoomScale = rules.zoom
    private val fit = TileFitter(rules)
    private val hasPictures = HashMap<TileNode, Boolean>()
    private var frozenAt = -1f
    private val frozen = java.util.IdentityHashMap<TileGroup, HashMap<Int, FloatArray>>()

    private fun split(g: TileGroup, w: Float, q: Float): FloatArray {
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
            TileKind.DRAWABLE -> picAt(it, w, zoomScale.pOf(q))
            TileKind.STATISTIC -> {
                val shape = it.stat ?: return TileAt(1f, 0f)
                fit.statAt(shape, w, BIG, zoomScale.zOf(q).coerceIn(it.floor, it.cap), it.floor)
            }
            TileKind.TITLE, TileKind.TEXT -> textAt(it, w, zoomScale.zOf(q))
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
        if (strict && (at.narrow || (it != null && it.kind != TileKind.DRAWABLE && at.scale < min(zoomScale.zOf(q).coerceAtMost(zoomScale.limit), it.leastScale) - 0.005f))) return TOO_NARROW
        return at.height
    }

    fun height(n: TileNode, w: Float, q: Float): Float = when (n) {
        is TileLeaf -> leafHeight(n.id, w, q)
        is TileGroup -> {
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

    private fun pictured(n: TileNode): Boolean = hasPictures.getOrPut(n) {
        TileTree.leaves(n).any { items[it]?.kind == TileKind.DRAWABLE }
    }

    private fun stretchy(n: TileNode): Boolean = TileTree.leaves(n).any { items[it]?.stretchy == true }

    fun needWidth(n: TileNode, q: Float): Float = when (n) {
        is TileLeaf -> {
            val it = items[n.id]
            when {
                it == null -> 0f
                it.kind == TileKind.DRAWABLE -> {
                    val sh = it.pic
                    if (sh == null || !sh.hasContent) 0f else max(sh.minW, sqrt(it.picArea * sh.ratioMin))
                }
                it.kind == TileKind.STATISTIC -> (it.stat?.hug ?: 0f) * zoomScale.zOf(q).coerceIn(it.floor, it.cap)
                else -> it.need * zoomScale.zOf(q).coerceIn(it.floor, it.cap)
            }
        }
        is TileGroup -> if (n.vertical) n.children.maxOf { needWidth(it, q) }
        else n.children.sumOf { needWidth(it, q).toDouble() }.toFloat() + gap * (n.children.size - 1)
    }

    fun floorWidth(n: TileNode): Float = when (n) {
        is TileLeaf -> {
            val it = items[n.id]
            when {
                it == null -> 0f
                it.kind == TileKind.DRAWABLE -> it.pic?.takeIf { p -> p.hasContent }?.minW ?: 0f
                it.kind == TileKind.STATISTIC -> (it.stat?.need ?: 0f) * it.floor
                else -> it.need * it.floor
            }
        }
        is TileGroup -> if (n.vertical) n.children.maxOf { floorWidth(it) }
        else n.children.sumOf { floorWidth(it).toDouble() }.toFloat() + gap * (n.children.size - 1)
    }

    private fun leastWidth(n: TileNode): Float = when (n) {
        is TileLeaf -> {
            val it = items[n.id]
            when {
                it == null -> 0f
                it.kind == TileKind.DRAWABLE -> {
                    val sh = it.pic
                    if (sh == null || !sh.hasContent) 0f else min(sh.maxW, max(sh.minW, sqrt(it.picArea * sh.ratioMin)))
                }
                it.kind == TileKind.STATISTIC -> (it.stat?.need ?: 0f) * max(it.floor, it.leastScale)
                else -> it.need * max(it.floor, it.leastScale)
            }
        }
        is TileGroup -> if (n.vertical) n.children.maxOf { leastWidth(it) }
        else n.children.sumOf { leastWidth(it).toDouble() }.toFloat() + gap * (n.children.size - 1)
    }

    fun widths(g: TileGroup, w: Float, q: Float): FloatArray {
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
        var lo = max(room * rules.splitLow, floors[0])
        var hi = min(room * rules.splitHigh, room - floors[1])
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

    fun zoom(n: TileNode, w: Float, h: Float): Float {
        if (frozenAt >= 0f) return bisect(n, w, h)
        var q = withSplitsAt(TileZoom.Q_BUDGET) { bisect(n, w, h) }
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

    private fun bisect(n: TileNode, w: Float, h: Float, top: Float = TileZoom.Q_TOP): Float {
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

    private fun keepWidth(n: TileNode, q: Float): Float = when (n) {
        is TileLeaf -> {
            val it = items[n.id]
            if (it != null && it.kind == TileKind.TITLE) max(needWidth(n, q), it.hug * zoomScale.zOf(q).coerceIn(it.floor, it.cap))
            else needWidth(n, q)
        }
        is TileGroup -> if (n.vertical) n.children.maxOf { keepWidth(it, q) }
        else n.children.sumOf { keepWidth(it, q).toDouble() }.toFloat() + gap * (n.children.size - 1)
    }

    private fun fillWidths(g: TileGroup, w: Float, h: Float, q: Float): FloatArray? {
        val n = g.children.size
        val room = (w - gap * (n - 1)).coerceAtLeast(1f)
        val pics = BooleanArray(n) { val c = g.children[it]; c is TileLeaf && items[c.id]?.kind == TileKind.DRAWABLE }
        val out = FloatArray(n)
        for (i in 0 until n) {
            val c = g.children[i]
            if (pics[i]) {
                val sh = items[(c as TileLeaf).id]?.pic ?: return null
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

    private fun grow(n: TileNode, w: Float, h: Float, from: Float, cap: Float): Float {
        val q = withSplitsAt(from) { growFrom(n, w, h, from, cap) }
        return if (q <= from || height(n, w, q) <= h + 0.5f) q else from
    }

    private fun growFrom(n: TileNode, w: Float, h: Float, from: Float, cap: Float): Float {
        fun ok(q: Float): Boolean = zoomScale.zOf(q) <= cap + 0.0001f && height(n, w, q) <= h + 0.5f
        if (ok(TileZoom.Q_TOP)) return TileZoom.Q_TOP
        if (!ok(from)) return from
        var lo = from
        var hi = TileZoom.Q_TOP
        repeat(16) {
            val mid = (lo + hi) / 2f
            if (ok(mid)) lo = mid else hi = mid
        }
        return lo
    }

    fun solve(root: TileNode?, x: Float, y: Float, w: Float, h: Float): TileSolved {
        val rects = LinkedHashMap<Int, FloatArray>()
        val levels = HashMap<Int, Float>()
        val dividers = ArrayList<TileDivider>()
        val groupSizes = HashMap<List<Int>, List<Float>>()
        if (root == null) return TileSolved(rects, dividers, groupSizes, levels, 1f)
        val q = if (byContent) TileZoom.Q_NATURAL else zoom(root, w, h)

        fun place(n: TileNode, px: Float, py: Float, pw: Float, ph: Float, level: Float, nested: Boolean, path: List<Int>) {
            if (n is TileLeaf) {
                rects[n.id] = floatArrayOf(px, py, pw, ph)
                levels[n.id] = level
                return
            }
            val g = n as TileGroup
            val count = g.children.size
            val gaps = gap * (count - 1)
            var sizes: FloatArray
            val qs = FloatArray(count) { level }
            if (g.vertical) {
                val axis = (ph - gaps).coerceAtLeast(0f)
                if (!g.isAuto && !byContent) {
                    val fr = fractionsOf(g)
                    val floors = g.children.map { height(it, pw, 0f) }
                    val caps = g.children.map { height(it, pw, TileZoom.Q_TOP) }
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
                if (g.children.any { it is TileLeaf && items[it.id]?.kind == TileKind.DRAWABLE } &&
                    g.children.any { !pictured(it) }
                ) {
                    val natural = g.children.indices.maxOf { height(g.children[it], sizes[it], level) }
                    if (natural < ph - 0.5f) withSplitsAt(level) { fillWidths(g, pw, ph, level) }?.let { sizes = it }
                }
                val cap = zoomScale.zOf(level) * rules.rowGrow
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
                    val adjacent = (TileTree.leaves(child) + TileTree.leaves(g.children[i + 1])).toSet()
                    val mid = off + gap / 2f
                    dividers.add(
                        if (g.vertical) TileDivider(path, i, true, px + pw / 2f, py + mid, sizes.toList(), adjacent, pw)
                        else TileDivider(path, i, false, px + mid, py + ph / 2f, sizes.toList(), adjacent, ph)
                    )
                }
                off += gap
            }
        }

        val height = if (byContent) height(root, w, q) else h
        place(root, x, y, w, height, q, false, emptyList())
        return TileSolved(rects, dividers, groupSizes, levels, zoomScale.zOf(q))
    }

    companion object {
        const val TOO_NARROW = 1e6f
        private const val ZOOM_STEPS = 16
        private const val ZOOM_ROUNDS = 3
    }
}
