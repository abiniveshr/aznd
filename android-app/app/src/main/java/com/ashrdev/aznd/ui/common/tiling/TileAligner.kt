package com.ashrdev.aznd.ui.common.tiling

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Auto align: finds the tiling of an area that fits its blocks best.
 *
 * A tiling here is a slicing floorplan (see TileTree.kt: every group cuts its area across or down), so the
 * search is the classic one for those, in the line of Stockmeyer's shape-curve optimisation of slicing floorplans
 * (1983) and the squarified treemaps of Bruls, Huizing and van Wijk (2000): every block is a soft tile whose
 * height depends on the width it gets (its "shape curve", sampled in [AlignItem]); a group shares its length among
 * its children by what they ask for; and a dynamic programme over the reading order tries every way to cut the
 * run of blocks into rows and columns (rows inside columns inside rows, as deep as it pays), keeping the cheapest.
 *
 *  - Order is kept: only runs of neighbouring blocks are grouped, so what reads first still reads first.
 *  - Both directions are tried at every level, so horizontal and vertical stacks are weighed against each other.
 *  - Every block has a kind ([TileKind]) and each kind has its own fit in [TileFitter] and its own price in the
 *    ruleset ([TileRules.tileCost]); the plan uses the same fits, so the search and the real layout agree.
 *  - Restrictions: no tile is made smaller than its floor (a word is never broken, content is never shrunk
 *    below its kind's least scale, a tile is never smaller than [TileRules.minTile]); drawables keep within their
 *    shape and size caps.
 *  - Full width: every text-like tile spans the whole width of its cell, and an area is stacked, one module under
 *    the other and edge to edge, unless splitting pays for itself ([TileRules.groupCost]).
 *  - Priorities: drawables win spare room by their [TileRules.appetite] and are the last to shrink by their
 *    [TileRules.priority]; titles like the top edge.
 *
 * Nothing in here knows about Android: the caller measures the blocks and passes the numbers in, which is also
 * what lets the search be tested on its own.
 */

/** One block as the optimiser sees it. All sizes in area pixels, at content scale 1. */
class AlignItem(
    val id: Int,
    val kind: TileKind,
    /** The least width the content can have without a broken word or cut text (0 for a drawable). */
    val need: Float,
    /** The width its content takes when it has room to spare. */
    val hug: Float,
    /** Titles and text: its height at the widths [sampleWidths] lists. */
    private val heights: FloatArray = FloatArray(0),
    /** Drawables: how it takes a cell. */
    val drawable: DrawableShape? = null,
    /** Statistics: its layouts. */
    val stat: StatShape? = null,
    /** Titles: the air it keeps above it at the top edge. */
    val inset: Float = 0f,
    /**
     * Titles and text: one width measured exactly, the area's full width (most tiles are as wide as the area), with
     * its height. Text height steps with the width, so between samples it is only approximate.
     */
    private val exactW: Float = -1f,
    private val exactH: Float = 0f
) {
    /** Its height at the width [w], between the sampled widths (a statistic's comes from its layouts). */
    fun height(w: Float): Float {
        stat?.let { return it.smoothHeight(w) }
        if (heights.isEmpty()) return 0f
        if (abs(w - exactW) < 0.5f) return exactH
        val pos = (w - SAMPLE_FROM) / SAMPLE_STEP
        if (pos <= 0f) return heights[0]
        val last = heights.size - 1
        if (pos >= last) return heights[last]
        val k = pos.toInt()
        return heights[k] + (heights[k + 1] - heights[k]) * (pos - k)
    }

    companion object {
        const val SAMPLE_FROM = 48f
        const val SAMPLE_STEP = 32f

        /** The widths whose heights an item holds: up to [maxWidth] (content that shrinks is measured wider than its tile). */
        fun sampleWidths(maxWidth: Float): FloatArray {
            val count = ((maxWidth - SAMPLE_FROM) / SAMPLE_STEP).toInt().coerceAtLeast(1) + 2
            return FloatArray(count) { SAMPLE_FROM + it * SAMPLE_STEP }
        }
    }
}

/** The area the tiles fill (area pixels) and the gap between tiles. */
class AlignSpace(
    val width: Float,
    val height: Float,
    val gap: Float
)

/** A tiling the search found, and what it cost by the optimiser's own numbers. */
class AlignCandidate(val tree: TileNode, val cost: Double)

/** [used]: how tall the tiling is (a tile's own height; stacked, the sum; side by side, the tallest). */
private class AlignSub(val cost: Double, val node: TileNode?, val floorW: Float, val used: Float = 0f)

class TileAligner(
    private val items: List<AlignItem>,
    private val space: AlignSpace,
    private val rules: TileRules,
    effort: Int = FULL_EFFORT
) {

    private val n = items.size
    private val gap = space.gap
    private val fit = TileFitter(rules)
    private val memo = HashMap<Long, AlignSub>()
    private val none = AlignSub(Double.POSITIVE_INFINITY, null, 0f)
    private var budget = effort
    private val segLimit = when {
        n <= 8 -> n
        n <= 10 -> 4
        else -> 3
    }
    /** The least a drawable is planned at along either side: a picture smaller than this hardly reads. */
    private val drawableMin = max(rules.minTile * rules.drawableLeastTiles, rules.drawableLeastShare * min(space.width, space.height))

    private fun minScale(item: AlignItem): Float = rules.minScale(item.kind)

    /**
     * The least scale the search plans it at: never below the text scale. A statistic can go lower ([minScale]) when
     * a hand-made layout leaves it no more room, but a search that planned for that would weigh many more splits
     * (slow) to find layouts its prices reject anyway.
     */
    private fun planScale(item: AlignItem): Float = max(minScale(item), rules.textMinScale)

    /** The best tilings: one that is cut into rows at the top, one cut into columns, whichever is cheaper in each case. */
    fun solve(): List<AlignCandidate> {
        if (n == 0 || n > MAX_BLOCKS) return emptyList()
        if (n == 1) return listOf(AlignCandidate(TileLeaf(items[0].id), 0.0))
        val out = ArrayList<AlignCandidate>(2)
        for (avoid in intArrayOf(ROWS, COLUMNS)) {
            val sub = solveSub(0, n - 1, space.width, space.height, avoid, BANNER)
            sub.node?.let { out += AlignCandidate(it, sub.cost) }
        }
        return out
    }

    /**
     * The best sizes for a tiling made by hand, [tree] (its tiles in the order of the items): the structure is kept,
     * and every group that shares by content has its shares picked the way the search picks them; groups with shares
     * of their own (a border the person dragged) keep them. Null when the tree does not hold exactly the items.
     */
    fun solveFixed(tree: TileNode): AlignCandidate? {
        if (n == 0 || TileTree.leaves(tree) != items.map { it.id }) return null
        val sub = fixed(tree, 0, n - 1, space.width, space.height, BANNER)
        return sub.node?.let { AlignCandidate(it, sub.cost) }
    }

    private val fixedMemo = HashMap<Long, AlignSub>()

    private fun fixed(node: TileNode, i: Int, j: Int, w: Float, h: Float, top: Int): AlignSub {
        if (node is TileLeaf) return leaf(i, w, h, top)
        val key = ((((i * 16L + j) * 3 + top) * 4096L + (w / 2f).roundToInt().coerceIn(0, 4095)) * 4096L) +
            (h / 2f).roundToInt().coerceIn(0, 4095)
        fixedMemo[key]?.let { return it }
        val g = node as TileGroup
        val m = g.children.size
        val from = IntArray(m)
        val to = IntArray(m)
        var at = i
        for (k in 0 until m) {
            from[k] = at
            at += TileTree.leaves(g.children[k]).size
            to[k] = at - 1
        }
        val stacked = g.vertical
        val cross = if (stacked) w else h
        val axis = (if (stacked) h else w) - gap * (m - 1)
        var best = none
        if (axis > 0f) {
            val ways = if (g.isAuto) allocations(stacked, from, to, axis, cross)
            else listOf(g.weights.sum().let { total -> FloatArray(m) { g.weights[it] / total * axis } })
            for (sizes in ways) {
                var cost = 0.0
                val kids = ArrayList<TileNode>(m)
                val used = FloatArray(m)
                var ok = true
                for (k in 0 until m) {
                    val cw = if (stacked) cross else sizes[k]
                    val ch = if (stacked) sizes[k] else cross
                    val sub = fixed(g.children[k], from[k], to[k], cw, ch, childTop(top, stacked, k))
                    val kid = sub.node
                    if (kid == null || (!stacked && sub.floorW > sizes[k] + 0.5f)) {
                        ok = false
                        break
                    }
                    cost += sub.cost
                    kids += kid
                    used[k] = sub.used
                }
                if (!ok) continue
                cost += rules.groupCost(stacked, sizes, used)
                if (cost < best.cost) {
                    val extent = if (stacked) used.sum() + gap * (m - 1) else (used.maxOrNull() ?: 0f)
                    best = AlignSub(cost, TileGroup(stacked, kids, if (g.isAuto) sizes.toList() else g.weights), 0f, extent)
                }
            }
        }
        fixedMemo[key] = best
        return best
    }

    /**
     * The best tiling of the run [i]..[j] in [w] by [h]. Remembered by size to within [MEMO_STEP]: parts of nearly the
     * same size tile the same way, and not solving them again leaves the search's budget for different structures,
     * which finds better layouts as well as faster ones.
     */
    private fun solveSub(i: Int, j: Int, w: Float, h: Float, avoid: Int, top: Int): AlignSub {
        val key = ((((((i * 16L + j) * 3 + avoid) * 3 + top) * 2048L) + (w / MEMO_STEP).roundToInt().coerceIn(0, 2047)) * 4096L) +
            (h / MEMO_STEP).roundToInt().coerceIn(0, 4095)
        memo[key]?.let { return it }
        val out = if (i == j) leaf(i, w, h, top) else group(i, j, w, h, avoid, top)
        memo[key] = out
        return out
    }

    private fun leaf(i: Int, w: Float, h: Float, top: Int): AlignSub {
        val item = items[i]
        val f = fit.of(item, w, h, top == BANNER)
        val cost = rules.tileCost(item, space, w, h, f, top != NOT_TOP, top == BANNER)
        return AlignSub(cost, TileLeaf(item.id), floorOf(item), f.h)
    }

    /** The narrowest the solver lets a tile be: what its content needs at the least scale it plans for. A drawable has no floor there. */
    private fun floorOf(item: AlignItem): Float = if (item.kind == TileKind.DRAWABLE) 0f else item.need * planScale(item)

    /** Cuts the run [i]..[j] (in the way [avoid] does not say) into neighbouring parts, each part a tile or a group cut the other way. */
    private fun group(i: Int, j: Int, w: Float, h: Float, avoid: Int, top: Int): AlignSub {
        var best = none
        val cuts = j - i
        for (o in intArrayOf(ROWS, COLUMNS)) {
            if (o == avoid) continue
            val stacked = o == ROWS
            val total = if (stacked) h else w
            val cross = if (stacked) w else h
            for (mask in 1 until (1 shl cuts)) {
                val m = Integer.bitCount(mask) + 1
                if (m > segLimit || (budget < 0 && m > 2)) continue
                val from = IntArray(m)
                val to = IntArray(m)
                var k = 0
                var start = i
                for (b in 0 until cuts) {
                    if (mask and (1 shl b) != 0) {
                        from[k] = start
                        to[k] = i + b
                        k++
                        start = i + b + 1
                    }
                }
                from[k] = start
                to[k] = j
                val axis = total - gap * (m - 1)
                if (axis <= 0f) continue
                for (sizes in allocations(stacked, from, to, axis, cross)) {
                    budget--
                    val sub = evaluate(stacked, from, to, sizes, cross, top)
                    if (sub.cost < best.cost) best = sub
                }
            }
        }
        return best
    }

    private fun evaluate(stacked: Boolean, from: IntArray, to: IntArray, sizes: FloatArray, cross: Float, top: Int): AlignSub {
        val m = sizes.size
        var cost = 0.0
        val kids = ArrayList<TileNode>(m)
        val used = FloatArray(m)
        var floor = 0f
        for (k in 0 until m) {
            val cw = if (stacked) cross else sizes[k]
            val ch = if (stacked) sizes[k] else cross
            val sub = solveSub(from[k], to[k], cw, ch, if (stacked) ROWS else COLUMNS, childTop(top, stacked, k))
            val node = sub.node ?: return none
            // Side by side, the solver widens a tile to its own floor and takes it from the others: not a layout the search priced.
            if (!stacked && sub.floorW > sizes[k] + 0.5f) return none
            cost += sub.cost
            kids += node
            used[k] = sub.used
            floor = if (stacked) max(floor, sub.floorW) else floor + sub.floorW
        }
        if (!stacked) floor += gap * (m - 1)
        cost += rules.groupCost(stacked, sizes, used)
        val extent = if (stacked) used.sum() + gap * (m - 1) else (used.maxOrNull() ?: 0f)
        return AlignSub(cost, TileGroup(stacked, kids, sizes.toList()), floor, extent)
    }

    // ---- what a part asks for ------------------------------------------------------------------------------

    private fun leafWantsHeight(item: AlignItem, w: Float): Float = when (item.kind) {
        TileKind.DRAWABLE -> fit.drawableAsk(item.drawable!!, w, BIG).h
        // A title at the top keeps its air above it (it is read first, so in a search it is always at the top).
        TileKind.TITLE -> textAsk(item, w) + item.inset
        TileKind.STATISTIC, TileKind.TEXT -> textAsk(item, w)
    }

    /** How tall text-like content is at the width [w] with height to spare: scaled down only to keep its words whole (a statistic: its reflowed height). */
    private fun textAsk(item: AlignItem, w: Float): Float =
        fit.text(w, BIG, item.need, { item.height(it) }, { s, _ -> min(w, item.hug * s) }, minScale(item)).h

    private fun leafHug(item: AlignItem): Float =
        if (item.kind == TileKind.DRAWABLE) fit.drawableAsk(item.drawable!!, BIG, BIG).w.coerceIn(1f, space.width)
        else item.hug.coerceAtLeast(1f)

    /** The narrowest width at which the content is no taller than [h]; the width it wants when it cannot be that short. */
    fun leafWantsWidth(item: AlignItem, h: Float, maxW: Float): Float {
        if (item.kind == TileKind.DRAWABLE) return fit.drawableAsk(item.drawable!!, BIG, h).w
        val room = if (item.kind == TileKind.TITLE) (h - item.inset).coerceAtLeast(1f) else h
        val top = min(item.hug, maxW).coerceAtLeast(1f)
        val low = min(item.need, top)
        if (item.height(top) > room) {
            // Too tall for [h] even as wide as it likes: it will be shrunk to fit the height, and then needs only that
            // shrunk width (more would sit empty beside it, width a picture next to it could use).
            val stat = item.stat
            if (stat != null) {
                val f = fit.statistic(stat, top, room, false)
                return (stat.widths[stat.columns.indexOf(f.columns).coerceAtLeast(0)] * f.scale).coerceIn(min(low * f.scale, top), top)
            }
            val f = fit.text(top, room, item.need, { item.height(it) }, { s, _ -> min(top, item.hug * s) }, planScale(item))
            return (item.hug * f.scale).coerceIn(min(item.need * f.scale, top), top)
        }
        if (item.height(low) <= room) return low
        var lo = low
        var hi = top
        for (i in 0 until 12) {
            val mid = (lo + hi) / 2f
            if (item.height(mid) <= room) hi = mid else lo = mid
        }
        return min(hi + 4f, top)
    }

    /** The height a part asks for when it is [w] wide and stacked: one block, or a row of blocks sharing the width by what they hug. */
    private fun wantsHeight(from: Int, to: Int, w: Float): Float {
        if (from == to) return leafWantsHeight(items[from], w)
        val gaps = gap * (to - from)
        var hugs = 0f
        for (k in from..to) hugs += leafHug(items[k])
        var tallest = 0f
        for (k in from..to) {
            val wk = (w - gaps).coerceAtLeast(1f) * leafHug(items[k]) / hugs
            tallest = max(tallest, leafWantsHeight(items[k], wk))
        }
        return tallest
    }

    /** The width a part asks for when it is [h] tall and in a row: one block, or blocks stacked in it sharing the height by their size. */
    private fun wantsWidth(from: Int, to: Int, h: Float): Float {
        if (from == to) return leafWantsWidth(items[from], h, space.width)
        val gaps = gap * (to - from)
        var nat = 0f
        for (k in from..to) nat += leafWantsHeight(items[k], min(items[k].hug, space.width)).coerceAtLeast(1f)
        var widest = 0f
        for (k in from..to) {
            val hk = (h - gaps).coerceAtLeast(1f) * leafWantsHeight(items[k], min(items[k].hug, space.width)).coerceAtLeast(1f) / nat
            widest = max(widest, leafWantsWidth(items[k], hk, space.width))
        }
        return widest
    }

    private fun floorHeight(from: Int, to: Int, w: Float): Float {
        var floor = 0f
        for (k in from..to) {
            val item = items[k]
            val f = if (item.kind == TileKind.DRAWABLE) {
                max(drawableMin, item.drawable!!.minH)
            } else if (from == to) {
                val least = planScale(item)
                max(item.height(w / least) * least, min(rules.minTile, leafWantsHeight(item, w)))
            } else {
                rules.minTile * 0.7f
            }
            floor = max(floor, f)
        }
        return floor
    }

    private fun floorWidth(from: Int, to: Int): Float {
        var floor = 0f
        for (k in from..to) {
            val item = items[k]
            floor = max(
                floor,
                if (item.kind == TileKind.DRAWABLE) max(drawableMin, item.drawable!!.minW)
                else max(item.need * planScale(item), min(rules.minTile, item.hug))
            )
        }
        return floor
    }

    /**
     * The most the run may take along the split, [cross] across it: a drawable's size cap, and also what its shape range
     * allows at that cross size (a picture [cross] wide is never taller than its narrowest crop makes it), so a stack
     * does not give a picture height it cannot fill.
     */
    private fun capOf(from: Int, to: Int, stacked: Boolean, cross: Float): Float {
        var cap = BIG
        for (k in from..to) {
            val shape = items[k].drawable ?: continue
            cap = min(cap, drawableCap(shape, stacked, cross))
        }
        return cap
    }

    private val parts = HashMap<Long, FloatArray>()

    /**
     * What the run [from]..[to] asks for as one part of a group that splits [stacked] (one under the other) or side by
     * side, [cross] across it: its floor, its cap and what it wants along the split, and its priority and appetite.
     * Every way of cutting a run asks this of the same parts again, so it is remembered.
     */
    private fun part(from: Int, to: Int, stacked: Boolean, cross: Float): FloatArray {
        val key = (((from * 16L + to) * 2 + (if (stacked) 1 else 0)) shl 32) or (cross.toRawBits().toLong() and 0xFFFFFFFFL)
        return parts.getOrPut(key) {
            val floor = if (stacked) floorHeight(from, to, cross) else floorWidth(from, to)
            val cap = capOf(from, to, stacked, cross)
            var prio = 0f
            var hunger = 0f
            for (b in from..to) {
                prio += rules.priority(items[b].kind)
                hunger += rules.appetite(items[b].kind)
            }
            val ask = if (stacked) wantsHeight(from, to, cross) else wantsWidth(from, to, cross)
            floatArrayOf(floor, cap, ask.coerceAtLeast(1f).coerceIn(floor, max(floor, cap)), prio, hunger)
        }
    }

    /**
     * The ways to share [axis] among the parts. Each starts from what the parts ask for; spare room goes to the
     * hungriest first (by appetite), a shortage is taken from the least important first (by priority); plus, for two
     * parts, a few fixed splits. Floors and drawable caps hold in every one.
     */
    private fun allocations(stacked: Boolean, from: IntArray, to: IntArray, axis: Float, cross: Float): List<FloatArray> {
        val m = from.size
        val want = FloatArray(m)
        val floors = FloatArray(m)
        val caps = FloatArray(m)
        val prio = FloatArray(m)
        val hunger = FloatArray(m)
        for (k in 0 until m) {
            val part = part(from[k], to[k], stacked, cross)
            floors[k] = part[0]
            caps[k] = part[1]
            want[k] = part[2]
            prio[k] = part[3]
            hunger[k] = part[4]
        }
        val out = ArrayList<FloatArray>(8)
        fun add(v: FloatArray?) {
            if (v == null) return
            if (out.none { o -> o.indices.all { abs(o[it] - v[it]) < 3f } }) out += v
        }
        val shrink = FloatArray(m) { want[it] / prio[it] }
        add(fill(axis, want, floors, caps, FloatArray(m) { hunger[it] * hunger[it] }, shrink))
        if (budget < 0) return out
        add(fill(axis, want, floors, caps, hunger, shrink))
        add(fill(axis, want, floors, caps, want, want))
        if (m == 2) {
            val ones = FloatArray(2) { 1f }
            for (q in SPLITS) add(fill(axis, floatArrayOf(q * axis, (1f - q) * axis), floors, caps, ones, ones))
            // Side by side: the split that makes the two parts equally tall, so the modules of a row shrink together.
            if (!stacked) evenHeights(from, to, axis, floors, caps)?.let { x -> add(fill(axis, floatArrayOf(x, axis - x), floors, caps, ones, ones)) }
        }
        return out
    }

    /** The width of the first of two parts side by side that makes both equally tall (null when no split is allowed). */
    private fun evenHeights(from: IntArray, to: IntArray, axis: Float, floors: FloatArray, caps: FloatArray): Float? {
        var lo = max(floors[0], axis - caps[1])
        var hi = min(caps[0], axis - floors[1])
        if (hi <= lo) return null
        fun heightGap(x: Float): Float = wantsHeight(from[0], to[0], x) - wantsHeight(from[1], to[1], axis - x)
        // The first part gets taller as it narrows, the second as it widens: find where they meet, or the closest end.
        if (heightGap(lo) <= 0f) return lo
        if (heightGap(hi) >= 0f) return hi
        for (i in 0 until 14) {
            val mid = (lo + hi) / 2f
            if (heightGap(mid) > 0f) lo = mid else hi = mid
        }
        return (lo + hi) / 2f
    }

    /**
     * Sizes that add up to [total], each within its floor and cap, starting from [base]: what is missing is added in
     * proportion to [grow], what is over is taken in proportion to [shrink]; a size that reaches its limit stays there
     * and the others take the rest (the way a flexbox resolves flexible lengths). Null when the limits make it impossible.
     */
    private fun fill(total: Float, base: FloatArray, floors: FloatArray, caps: FloatArray, grow: FloatArray, shrink: FloatArray): FloatArray? {
        val m = base.size
        if (floors.sum() > total + 0.5f || caps.sum() < total - 0.5f) return null
        val x = FloatArray(m) { base[it].coerceIn(floors[it], max(floors[it], caps[it])) }
        val frozen = BooleanArray(m)
        for (pass in 0..m + 1) {
            val delta = total - x.sum()
            if (abs(delta) < 0.25f) return x
            var weights = 0f
            val w = FloatArray(m)
            for (k in 0 until m) {
                if (frozen[k]) continue
                val moves = if (delta > 0f) x[k] < caps[k] - 0.01f else x[k] > floors[k] + 0.01f
                if (!moves) {
                    frozen[k] = true
                    continue
                }
                w[k] = if (delta > 0f) grow[k] else shrink[k]
                weights += w[k]
            }
            if (weights <= 0f) break
            for (k in 0 until m) if (!frozen[k]) x[k] = (x[k] + delta * w[k] / weights).coerceIn(floors[k], caps[k])
        }
        return if (abs(total - x.sum()) < 1f) x else null
    }

    companion object {
        const val MAX_BLOCKS = 12
        const val FULL_EFFORT = 300_000
        const val QUICK_EFFORT = 40_000
        private const val BIG = 1e9f
        private const val MEMO_STEP = 12f
        private const val ROWS = 1      // a group that stacks its parts: one under the other
        private const val COLUMNS = 2   // a group that puts its parts side by side

        // Where a part is: not at the top, along the top edge (in a row there), or across the whole top (a banner).
        private const val NOT_TOP = 0
        private const val TOP_EDGE = 1
        private const val BANNER = 2

        /** Where part [k] of a group at [top] is: a stack's first part is where the stack is; a row's parts touch the top, none across it. */
        private fun childTop(top: Int, stacked: Boolean, k: Int): Int =
            if (stacked) (if (k == 0) top else NOT_TOP) else min(top, TOP_EDGE)
        private val SPLITS = floatArrayOf(0.3f, 0.4f, 0.5f, 0.6f, 0.7f)

        /** The height all [items] need one under the other at the full [space] width: the area Free size would make without a layout. */
        fun stackedHeight(items: List<AlignItem>, space: AlignSpace, rules: TileRules): Float {
            val fit = TileFitter(rules)
            var sum = space.gap * (items.size - 1).coerceAtLeast(0)
            items.forEachIndexed { i, item ->
                sum += when (item.kind) {
                    TileKind.DRAWABLE -> fit.drawableAsk(item.drawable!!, space.width, BIG).h
                    else -> {
                        val h = fit.text(space.width, BIG, item.need, { item.height(it) }, { s, _ -> min(space.width, item.hug * s) }, rules.minScale(item.kind)).h
                        if (i == 0 && item.kind == TileKind.TITLE) h + item.inset else h
                    }
                }
            }
            return sum
        }
    }
}
