package com.ashrdev.aznd.ui.common.share

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Auto align: finds the tiling of a card that fits its blocks best.
 *
 * A tiling here is a slicing floorplan (see FlexLayout.kt: every group cuts its area across or down), so the
 * search is the classic one for those, in the line of Stockmeyer's shape-curve optimisation of slicing floorplans
 * (1983) and the squarified treemaps of Bruls, Huizing and van Wijk (2000): every block is a soft tile whose
 * height depends on the width it gets (its "shape curve", sampled in [AutoItem]); a group shares its length among
 * its children by what they ask for; and a dynamic programme over the reading order tries every way to cut the
 * run of blocks into rows and columns (rows inside columns inside rows, as deep as it pays), keeping the cheapest.
 *
 *  - Order is kept: only runs of neighbouring blocks are grouped, so what reads first still reads first.
 *  - Both directions are tried at every level, so horizontal and vertical stacks are weighed against each other.
 *  - Restrictions: no tile is made smaller than its floor (a word is never broken, content is never shrunk
 *    below [FlexFit.MIN_SCALE], a tile is never smaller than [MIN_TILE]); photos never exceed the card's
 *    half limit.
 *  - Full width: every text tile spans the whole width of its cell (see [FlexBlock.fillWidth]), and a card is
 *    stacked, one module under the other and edge to edge, unless splitting pays for itself. A split (side by
 *    side) costs [AutoCost.SPLIT]; it is kept centred ([AutoCost.OFF_CENTRE]) unless a part needs more room, and its parts
 *    are kept equally tall ([AutoCost.UNEVEN]), so the modules in a row shrink together.
 *  - Priorities: photos win spare room (their area is rewarded, and they are the ones to grow when there is
 *    slack and the last to shrink when there is none); titles are never shrunk, never wrapped when avoidable, and
 *    like the top edge.
 *
 * Nothing in here knows about Android: the painter measures the blocks and passes the numbers in, which is also
 * what lets the search be tested on its own.
 */

internal enum class AutoKind { TITLE, PHOTO, TEXT }

/** One block as the optimiser sees it. All sizes in card pixels, at content scale 1. */
internal class AutoItem(
    val id: Int,
    val kind: AutoKind,
    /** The least width the content can have without a broken word or cut text (0 for a photo). */
    val need: Float,
    /** The width its content takes when it has room to spare. */
    val hug: Float,
    /** Its height at the widths [sampleWidths] lists (empty for a photo). */
    private val heights: FloatArray = FloatArray(0),
    /** Photo only: false when there is no picture (the tile then fills its cell). */
    val hasImage: Boolean = true,
    /** Photo only: the tile's width / height. */
    val photoRatio: Float = 1f,
    /** Photo only: a circle, which is a square tile. */
    val circle: Boolean = false,
    /** Photo only: the crop shapes the tile may take. A photo sticks to the margins of its cell by taking whichever fits. */
    val ratioMin: Float = photoRatio,
    val ratioMax: Float = photoRatio
) {
    /** How much of a shortage a block is spared (the higher, the later it shrinks). */
    val priority: Float
        get() = when (kind) {
            AutoKind.PHOTO -> 3f
            AutoKind.TITLE -> 2f
            AutoKind.TEXT -> 1f
        }

    /** How much of the spare room a block takes: photos most, titles hardly any (a title with air round it looks lost). */
    val appetite: Float
        get() = when (kind) {
            AutoKind.PHOTO -> 3f
            AutoKind.TITLE -> 0.2f
            AutoKind.TEXT -> 1f
        }

    /** Its height at the width [w], between the sampled widths. */
    fun height(w: Float): Float {
        if (heights.isEmpty()) return 0f
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

/** The area the tiles fill (card pixels), the gap between tiles, and how big a photo may be along each axis. */
internal class AutoSpace(
    val width: Float,
    val height: Float,
    val gap: Float,
    val photoMaxW: Float,
    val photoMaxH: Float
)

/** What a block's tile came to inside a cell: its size, the scale its content was shrunk to, and whether it is cut off. */
internal class TileFit(val w: Float, val h: Float, val scale: Float, val cut: Boolean)

internal object FlexFit {
    /** A tile's content is never shrunk below this. Same as [FlexPainter.MIN_TILE_SCALE]. */
    const val MIN_SCALE = 0.6f

    /**
     * Fits text-like content into a cell [cw] by [ch]: it keeps its words whole by scaling down if the cell is
     * narrower than [need1], and shrinks (never below [MIN_SCALE]) until it fits the height. [height] says how
     * tall the content is at a width (scale 1), [hugAt] how wide it is at a scale and height.
     */
    inline fun text(
        cw: Float,
        ch: Float,
        need1: Float,
        height: (Float) -> Float,
        hugAt: (scale: Float, drawnHeight: Float) -> Float
    ): TileFit {
        val widest = if (need1 > cw) (cw / need1).coerceAtLeast(MIN_SCALE) else 1f
        var s = widest
        var dh = height(cw / s) * s
        if (dh > ch) {
            var tall = dh
            for (i in 0 until 6) {
                val next = (s * ch / tall).coerceIn(MIN_SCALE, widest)
                if (abs(next - s) < 0.0005f) {
                    s = next
                    break
                }
                s = next
                tall = height(cw / s) * s
            }
            // Wrapping can make the last step slightly too big; never end up over.
            var guard = 0
            while (height(cw / s) * s > ch + 0.5f && s > MIN_SCALE && guard++ < 40) {
                s = max(MIN_SCALE, s - 0.004f)
            }
            dh = height(cw / s) * s
        }
        val cut = dh > ch + 1f || need1 > cw / s + 1f
        val th = min(dh, ch)
        val tw = if (cut) cw else hugAt(s, dh)
        return TileFit(tw, th, s, cut)
    }

    /** A photo in a cell: its crop shape, as big as fits, never beyond the card's limit. */
    fun photo(item: AutoItem, space: AutoSpace, cw: Float, ch: Float): TileFit {
        if (!item.hasImage) return TileFit(cw, ch, 1f, false)
        if (item.circle) {
            val d = min(min(cw, ch), min(space.photoMaxW, space.photoMaxH))
            return TileFit(d, d, 1f, false)
        }
        // The crop is free within its range: the photo takes the shape that makes it span its cell edge to edge.
        var pw = min(cw, space.photoMaxW)
        val maxH = min(ch, space.photoMaxH)
        val ratio = (pw / maxH).coerceIn(item.ratioMin, item.ratioMax)
        var ph = pw / ratio
        if (ph > maxH) {
            ph = maxH
            pw = ph * ratio
        }
        return TileFit(pw, ph, 1f, false)
    }

    /** [text] on the optimiser's numbers: the width a tile takes is estimated, not searched for. */
    fun of(item: AutoItem, space: AutoSpace, cw: Float, ch: Float): TileFit =
        if (item.kind == AutoKind.PHOTO) photo(item, space, cw, ch)
        // Auto align sets every text block to fill its cell, so the tile is as wide as the cell.
        else text(cw, ch, item.need, { item.height(it) }, { _, _ -> cw })
}

/** The price of a tile: lower is a better fit. The same numbers judge the optimiser's guesses and the real layouts. */
internal object AutoCost {
    const val CUT = 60.0
    const val GROUP = 0.05
    const val HIDDEN = 25.0

    /** Putting modules side by side instead of one under the other (per extra part): a card stays stacked unless the height it saves pays for it. */
    const val SPLIT = 0.15

    /** Side by side, a part's share off the equal share (0 = all equal, 1 = the most it can be): centre is preferred. */
    const val OFF_CENTRE = 1.2

    /** Side by side, how unequal the parts' heights are (0 = equal, 1 = one is empty): the modules of a row are as tall as each other. */
    const val UNEVEN = 2.0

    /** Rows of columns whose edges do not line up (0 = all rows cut at the same places). */
    const val RAGGED = 1.0

    /**
     * What a group costs beyond its tiles. [sizes] is what each part got along the split and [used] how far each
     * reaches across it (for side by side parts, how tall the tile or stack in it is).
     */
    fun group(stacked: Boolean, sizes: FloatArray, used: FloatArray): Double {
        var c = GROUP
        if (stacked || sizes.size < 2) return c
        val m = sizes.size
        val total = sizes.sum().toDouble().coerceAtLeast(1.0)
        var off = 0.0
        for (s in sizes) off += abs(s / total - 1.0 / m)
        c += SPLIT * (m - 1) + OFF_CENTRE * off / 2.0
        val tall = used.maxOrNull() ?: 0f
        val short = used.minOrNull() ?: 0f
        if (tall > 0f) c += UNEVEN * (tall - short) / tall
        return c
    }

    fun tile(item: AutoItem, space: AutoSpace, cw: Float, ch: Float, fit: TileFit, top: Boolean): Double {
        val area = (space.width * space.height).toDouble().coerceAtLeast(1.0)
        val waste = (cw.toDouble() * ch - fit.w.toDouble() * fit.h) / area
        val s = fit.scale.toDouble()
        var c = if (fit.cut) CUT else 0.0
        when (item.kind) {
            AutoKind.TEXT -> {
                c += 5.0 * (1.0 - s) + 9.0 * max(0.0, 0.85 - s)
                c += 1.2 * waste
                // Not a thin sliver, and not text broken into many more lines than it needs.
                c += 4.0 * max(0.0, 0.30 - cw.toDouble() / space.width)
                val full = item.height(space.width).toDouble().coerceAtLeast(1.0)
                val wrapped = item.height(cw / fit.scale) * fit.scale / full
                c += 0.25 * min(max(0.0, wrapped - 1.6), 4.0)
            }
            AutoKind.TITLE -> {
                // A title is not shrunk, not broken into lines if it can help it, and sits at the top.
                c += 14.0 * (1.0 - s) + 20.0 * max(0.0, 0.92 - s)
                val full = item.height(space.width).toDouble().coerceAtLeast(1.0)
                val wrapped = item.height(cw / fit.scale) * fit.scale / full
                c += 1.2 * min(max(0.0, wrapped - 1.0), 3.0)
                c += 2.0 * waste
                if (!top) c += 0.5
            }
            AutoKind.PHOTO -> {
                // A photo is rewarded for every bit of the card it covers, up to about half of it.
                val frac = fit.w.toDouble() * fit.h / area
                c += 1.6 * waste
                c -= 2.0 * min(frac, 0.5)
                if (frac < 0.12) c += 3.0 * (0.12 - frac)
            }
        }
        return c
    }
}

/** A tiling the search found, and what it cost by the optimiser's own numbers. */
internal class AutoCandidate(val tree: FlexNode, val cost: Double)

/** [used]: how tall the tiling is (a tile's own height; stacked, the sum; side by side, the tallest). */
private class AutoSub(val cost: Double, val node: FlexNode?, val floorW: Float, val used: Float = 0f)

internal class FlexAutoAligner(private val items: List<AutoItem>, private val space: AutoSpace) {

    private val n = items.size
    private val gap = space.gap
    private val memo = HashMap<Long, AutoSub>()
    private val none = AutoSub(Double.POSITIVE_INFINITY, null, 0f)
    private var budget = 300_000
    private val segLimit = when {
        n <= 8 -> n
        n <= 10 -> 4
        else -> 3
    }
    private val photoMin = max(MIN_TILE * 1.5f, 0.2f * min(space.width, space.height))

    /** The best tilings: one that is cut into rows at the top, one cut into columns, whichever is cheaper in each case. */
    fun solve(): List<AutoCandidate> {
        if (n == 0 || n > MAX_BLOCKS) return emptyList()
        if (n == 1) return listOf(AutoCandidate(FlexLeaf(items[0].id), 0.0))
        val out = ArrayList<AutoCandidate>(2)
        for (avoid in intArrayOf(ROWS, COLUMNS)) {
            val sub = solveSub(0, n - 1, space.width, space.height, avoid, true)
            sub.node?.let { out += AutoCandidate(it, sub.cost) }
        }
        return out
    }

    private fun solveSub(i: Int, j: Int, w: Float, h: Float, avoid: Int, top: Boolean): AutoSub {
        val key = ((((((i * 16L + j) * 3 + avoid) * 2 + (if (top) 1 else 0)) * 2048L) + (w / 4f).roundToInt().coerceIn(0, 2047)) * 4096L) +
            (h / 4f).roundToInt().coerceIn(0, 4095)
        memo[key]?.let { return it }
        val out = if (i == j) leaf(i, w, h, top) else group(i, j, w, h, avoid, top)
        memo[key] = out
        return out
    }

    private fun leaf(i: Int, w: Float, h: Float, top: Boolean): AutoSub {
        val item = items[i]
        val fit = FlexFit.of(item, space, w, h)
        val cost = AutoCost.tile(item, space, w, h, fit, top)
        return AutoSub(cost, FlexLeaf(item.id), floorOf(item), fit.h)
    }

    /** The narrowest the solver lets a tile be: what its words need at the smallest scale. A photo has no floor there. */
    private fun floorOf(item: AutoItem): Float = if (item.kind == AutoKind.PHOTO) 0f else item.need * FlexFit.MIN_SCALE

    /** Cuts the run [i]..[j] (in the way [avoid] does not say) into neighbouring parts, each part a tile or a group cut the other way. */
    private fun group(i: Int, j: Int, w: Float, h: Float, avoid: Int, top: Boolean): AutoSub {
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

    private fun evaluate(stacked: Boolean, from: IntArray, to: IntArray, sizes: FloatArray, cross: Float, top: Boolean): AutoSub {
        val m = sizes.size
        var cost = 0.0
        val kids = ArrayList<FlexNode>(m)
        val used = FloatArray(m)
        var floor = 0f
        for (k in 0 until m) {
            val cw = if (stacked) cross else sizes[k]
            val ch = if (stacked) sizes[k] else cross
            val sub = solveSub(from[k], to[k], cw, ch, if (stacked) ROWS else COLUMNS, top && (!stacked || k == 0))
            val node = sub.node ?: return none
            // Side by side, the solver widens a tile to its own floor and takes it from the others: not a layout the search priced.
            if (!stacked && sub.floorW > sizes[k] + 0.5f) return none
            cost += sub.cost
            kids += node
            used[k] = sub.used
            floor = if (stacked) max(floor, sub.floorW) else floor + sub.floorW
        }
        if (!stacked) floor += gap * (m - 1)
        cost += AutoCost.group(stacked, sizes, used)
        val extent = if (stacked) used.sum() + gap * (m - 1) else (used.maxOrNull() ?: 0f)
        return AutoSub(cost, FlexGroup(stacked, kids, sizes.toList()), floor, extent)
    }

    // ---- what a part asks for ------------------------------------------------------------------------------

    private fun leafWantsHeight(item: AutoItem, w: Float): Float =
        if (item.kind == AutoKind.PHOTO) {
            // No picture: a placeholder that fills its cell, so it asks for a modest shape.
            if (item.hasImage) FlexFit.photo(item, space, w, BIG).h else min(w * 0.75f, space.photoMaxH)
        } else FlexFit.text(w, BIG, item.need, { item.height(it) }, { s, _ -> min(w, item.hug * s) }).h

    private fun leafHug(item: AutoItem): Float = when {
        item.kind != AutoKind.PHOTO -> item.hug.coerceAtLeast(1f)
        item.hasImage -> FlexFit.photo(item, space, BIG, BIG).w.coerceAtLeast(1f)
        else -> space.photoMaxW / 2f
    }

    /** The narrowest width at which the content is no taller than [h]; the width it wants when it cannot be that short. */
    private fun leafWantsWidth(item: AutoItem, h: Float, maxW: Float): Float {
        if (item.kind == AutoKind.PHOTO) {
            return if (item.hasImage) FlexFit.photo(item, space, BIG, h).w else min(h * 1.33f, space.photoMaxW)
        }
        val top = min(item.hug, maxW).coerceAtLeast(1f)
        val low = min(item.need, top)
        if (item.height(top) > h) return top
        if (item.height(low) <= h) return low
        var lo = low
        var hi = top
        for (i in 0 until 12) {
            val mid = (lo + hi) / 2f
            if (item.height(mid) <= h) hi = mid else lo = mid
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
            val f = if (item.kind == AutoKind.PHOTO) {
                photoMin
            } else if (from == to) {
                max(item.height(w / FlexFit.MIN_SCALE) * FlexFit.MIN_SCALE, min(MIN_TILE, leafWantsHeight(item, w)))
            } else {
                MIN_TILE * 0.7f
            }
            floor = max(floor, f)
        }
        return floor
    }

    private fun floorWidth(from: Int, to: Int): Float {
        var floor = 0f
        for (k in from..to) {
            val item = items[k]
            floor = max(floor, if (item.kind == AutoKind.PHOTO) photoMin else max(item.need * FlexFit.MIN_SCALE, min(MIN_TILE, item.hug)))
        }
        return floor
    }

    private fun capOf(from: Int, to: Int, stacked: Boolean): Float {
        var cap = BIG
        for (k in from..to) {
            if (items[k].kind == AutoKind.PHOTO) cap = min(cap, if (stacked) space.photoMaxH else space.photoMaxW)
        }
        return cap
    }

    /**
     * The ways to share [axis] among the parts. Each starts from what the parts ask for; spare room goes to the
     * photos first (by priority), a shortage is taken from the least important first; plus, for two parts, a few
     * fixed splits. Floors and photo caps hold in every one.
     */
    private fun allocations(stacked: Boolean, from: IntArray, to: IntArray, axis: Float, cross: Float): List<FloatArray> {
        val m = from.size
        val want = FloatArray(m)
        val floors = FloatArray(m)
        val caps = FloatArray(m)
        val prio = FloatArray(m)
        val hunger = FloatArray(m)
        for (k in 0 until m) {
            floors[k] = if (stacked) floorHeight(from[k], to[k], cross) else floorWidth(from[k], to[k])
            caps[k] = capOf(from[k], to[k], stacked)
            for (b in from[k]..to[k]) {
                prio[k] += items[b].priority
                hunger[k] += items[b].appetite
            }
            val ask = if (stacked) wantsHeight(from[k], to[k], cross) else wantsWidth(from[k], to[k], cross)
            want[k] = ask.coerceAtLeast(1f).coerceIn(floors[k], max(floors[k], caps[k]))
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
        private const val BIG = 1e9f
        private const val ROWS = 1      // a group that stacks its parts: one under the other
        private const val COLUMNS = 2   // a group that puts its parts side by side
        private val SPLITS = floatArrayOf(0.3f, 0.4f, 0.5f, 0.6f, 0.7f)

        /** The height all [items] need one under the other at the full [FlexAutoAligner] width: the card Free size would make without a layout. */
        fun stackedHeight(items: List<AutoItem>, space: AutoSpace): Float {
            var sum = space.gap * (items.size - 1).coerceAtLeast(0)
            for (item in items) {
                sum += if (item.kind == AutoKind.PHOTO) FlexFit.photo(item, space, space.width, BIG).h
                else FlexFit.text(space.width, BIG, item.need, { item.height(it) }, { s, _ -> min(space.width, item.hug * s) }).h
            }
            return sum
        }
    }
}

/**
 * Lines the tiles of a tiling up in a grid: where a group's parts are all groups cut the other way into the same
 * number of cells (rows of two, say), every one of them gets the average share for each cell, so the edges between
 * cells run straight through instead of zigzagging.
 */
internal fun alignGrid(node: FlexNode): FlexNode {
    if (node !is FlexGroup) return node
    var kids = node.children.map { alignGrid(it) }
    val groups = kids.filterIsInstance<FlexGroup>()
    if (groups.size == kids.size && groups.size >= 2 && groups.all { it.children.size == groups[0].children.size && it.vertical != node.vertical && !it.isAuto }) {
        val cells = groups[0].children.size
        val shares = FloatArray(cells) { c -> groups.map { g -> g.weights[c] / g.weights.sum() }.average().toFloat() }
        kids = groups.map { g -> FlexGroup(g.vertical, g.children, shares.map { it * 1000f }) }
    }
    return FlexGroup(node.vertical, kids, node.weights)
}

/** Puts every side by side group of a tiling on equal shares: all rows are cut in the middle, however many parts they have. */
internal fun centreColumns(node: FlexNode): FlexNode {
    if (node !is FlexGroup) return node
    val kids = node.children.map { centreColumns(it) }
    return if (node.vertical) FlexGroup(true, kids, node.weights) else FlexGroup(false, kids, List(kids.size) { 1000f })
}
