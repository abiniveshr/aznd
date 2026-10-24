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
 *  - Every block has a class ([FlexBlockClass]) and each class has its own fit in [FlexFit] and its own price in
 *    [AutoCost]; the painter's plan uses the same fits, so the search and the real layout agree.
 *  - Restrictions: no tile is made smaller than its floor (a word is never broken, content is never shrunk
 *    below its class's least scale, a tile is never smaller than [MIN_TILE]); drawables keep within their
 *    shape and size caps (photos within the card's half limit).
 *  - Full width: every text-like tile spans the whole width of its cell, and a card
 *    is stacked, one module under the other and edge to edge, unless splitting pays for itself. A split (side by
 *    side) costs [AutoCost.SPLIT]; it is kept centred ([AutoCost.OFF_CENTRE]) unless a part needs more room, and its parts
 *    are kept equally tall ([AutoCost.UNEVEN]), so the modules in a row shrink together. Statistics are the best
 *    partners there: their height at a width is their reflowed height, so a row can even them out.
 *  - Priorities: drawables win spare room (their area is rewarded, and they are the ones to grow when there is
 *    slack and the last to shrink when there is none); titles are never shrunk, never wrapped when avoidable, and
 *    like the top edge.
 *
 * Nothing in here knows about Android: the painter measures the blocks and passes the numbers in, which is also
 * what lets the search be tested on its own.
 */

/** How much of a shortage a block of this class is spared (the higher, the later it shrinks). */
internal val FlexBlockClass.priority: Float
    get() = when (this) {
        FlexBlockClass.DRAWABLE -> 3f
        FlexBlockClass.TITLE -> 2f
        FlexBlockClass.STATISTIC -> 1.5f
        FlexBlockClass.TEXT -> 1f
    }

/** How much of the spare room a block of this class takes: drawables most, titles hardly any (a title with air round it looks lost). */
internal val FlexBlockClass.appetite: Float
    get() = when (this) {
        FlexBlockClass.DRAWABLE -> 3f
        FlexBlockClass.TITLE -> 0.2f
        FlexBlockClass.STATISTIC -> 1f
        FlexBlockClass.TEXT -> 1f
    }

/**
 * How a drawable takes a cell. It has no text scale: it takes a shape between [ratioMin] and [ratioMax] (width /
 * height) that lets it fill its cell edge to edge, never bigger than [maxW] by [maxH]. Below [minW] by [minH] it
 * no longer reads (a chart's labels run into each other), so a cell smaller than that cuts it.
 */
internal class DrawableShape(
    /** False when there is nothing to draw (no picture, no data): the tile is a placeholder that fills its cell. */
    val hasContent: Boolean,
    /** The shape it asks for when nothing limits it. */
    val ratio: Float,
    val ratioMin: Float,
    val ratioMax: Float,
    val minW: Float,
    val minH: Float,
    val maxW: Float,
    val maxH: Float,
    /** Asks for as much as its caps allow (a photo), instead of only its own shape (a chart). */
    val greedy: Boolean,
    /** Keeps the shape it was fitted to as its crop ([FlexBlock.photoRatio]), so auto align stores it. */
    val crops: Boolean
)

/**
 * A statistic's layouts, the one it is designed with first: layout k puts its cells in [columns]`[k]` columns, is
 * at least [widths]`[k]` wide and [heights]`[k]` tall, at scale 1. Nothing in a statistic wraps, so these are fixed
 * numbers; reflowing is picking a layout with fewer columns. [designed] is the column count it is meant to have
 * (fewer costs [AutoCost.REFLOW] each); 1 for a statistic that has no such count (a sentence of stats).
 */
internal class StatShape(val columns: IntArray, val widths: FloatArray, val heights: FloatArray, val designed: Int) {

    /** The least width it can have at scale 1: its narrowest layout. */
    val need: Float get() = widths[widths.size - 1]

    /** The width it takes as designed. */
    val hug: Float get() = widths[0]

    /**
     * The layout it takes when it is [w] wide and has height to spare: the most columns that fit, counting a layout
     * that only has to shrink a little (to [FlexFit.REFLOW_KEEP]) as fitting, else the narrowest.
     */
    fun layoutAt(w: Float): Int {
        for (k in widths.indices) if (widths[k] * FlexFit.REFLOW_KEEP <= w) return k
        return widths.size - 1
    }

    /**
     * Its height at scale 1 at the width [w], as a smooth curve for the search: just below the width where a
     * layout stops fitting, the height ramps up to the next layout's over [RAMP] instead of jumping, so it never
     * goes down as the width shrinks and has no steps for the search to trip on. (The tile itself is fitted
     * exactly, by [FlexFit.statistic].)
     */
    fun smoothHeight(w: Float): Float {
        val k = layoutAt(w)
        if (k == 0) return heights[0]
        // Where the layout with one more column starts to fit (see layoutAt).
        val edge = widths[k - 1] * FlexFit.REFLOW_KEEP
        val from = max(edge - RAMP, widths[k] * FlexFit.REFLOW_KEEP)
        if (w <= from || edge <= from) return heights[k]
        val t = (w - from) / (edge - from)
        return heights[k] + (heights[k - 1] - heights[k]) * t
    }

    companion object {
        const val RAMP = 64f
    }
}

/** One block as the optimiser sees it. All sizes in card pixels, at content scale 1. */
internal class AutoItem(
    val id: Int,
    val kind: FlexBlockClass,
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
    /** Titles: the air it keeps above it at the top edge (see [FlexStyle.titleInset]). */
    val inset: Float = 0f,
    /**
     * Titles and text: one width measured exactly, the card's full width (most tiles are as wide as the card), with
     * its height. Text height steps with the width, so between samples it is only approximate.
     */
    private val exactW: Float = -1f,
    private val exactH: Float = 0f
) {
    /** How much of a shortage a block is spared (the higher, the later it shrinks). */
    val priority: Float get() = kind.priority

    /** How much of the spare room a block takes. */
    val appetite: Float get() = kind.appetite

    /** The least scale its content is shrunk to. */
    val minScale: Float get() = FlexFit.minScale(kind)

    /**
     * The least scale the search plans it at: never below the text scale. A statistic can go lower ([minScale]) when
     * a hand-made layout leaves it no more room, but a search that planned for that would weigh many more splits
     * (slow) to find layouts its prices reject anyway.
     */
    val planScale: Float get() = max(minScale, FlexFit.MIN_SCALE)

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

/** The area the tiles fill (card pixels) and the gap between tiles. */
internal class AutoSpace(
    val width: Float,
    val height: Float,
    val gap: Float
)

/**
 * What a block's tile came to inside a cell: its size, the scale its content was shrunk to, and whether it is cut
 * off. [columns] is the layout a statistic took (0 for the others), [inset] the air a title kept above it (part
 * of [h]).
 */
internal class TileFit(
    val w: Float,
    val h: Float,
    val scale: Float,
    val cut: Boolean,
    val columns: Int = 0,
    val inset: Float = 0f
)

/** The one place a tile's size is decided, one function per block class. The painter's plan and the optimiser both call it. */
internal object FlexFit {
    /** Titles and text are never shrunk below this. Same as [FlexPainter.MIN_TILE_SCALE]. */
    const val MIN_SCALE = 0.6f

    /** Statistics have no wrapping to fall back on, so they may shrink further. */
    const val STAT_MIN_SCALE = 0.45f

    /**
     * A tile's content takes up its cell: it is drawn bigger than its size (the block's own and the module size) when
     * the cell has room, up to this. The size control of the block's menu sets where it starts.
     */
    const val FILL_MAX = 1.5f

    /** A title grows less: past this it shouts. Its cell is never given more height than it takes at this. */
    const val FILL_TITLE = 1.25f

    /**
     * A picture hugs a side of its cell it nearly reaches: when the room left beside it (or above and below it) is at
     * most this share of the cell's side (and at least [HUG_PX]), it stretches to fill it, its shape going up to
     * [HUG_SLACK] past its range (a photo is re-cropped, a chart or calendar stretched). Further away, it sits in the
     * middle of the cell.
     */
    const val HUG_SHARE = 0.12f
    const val HUG_PX = 40f
    const val HUG_SLACK = 1.3f

    /** A title gives up the air above it before it would be shrunk below this. */
    const val TITLE_KEEP = 0.92f

    /**
     * A statistic keeps the columns it has while it only has to shrink to this; past it, fewer columns (when they let it
     * be bigger) come first. A grid that would shrink by a few percent does not reshuffle into more rows.
     */
    const val REFLOW_KEEP = 0.92f

    fun minScale(kind: FlexBlockClass): Float = when (kind) {
        FlexBlockClass.STATISTIC -> STAT_MIN_SCALE
        FlexBlockClass.DRAWABLE -> 1f
        FlexBlockClass.TITLE, FlexBlockClass.TEXT -> MIN_SCALE
    }

    /**
     * Text: fits content into a cell [cw] by [ch]. It keeps its words whole by scaling down if the cell is narrower
     * than [need1], and shrinks (never below [minScale]) until it fits the height. [height] says how tall the
     * content is at a width (scale 1), [hugAt] how wide it is at a scale and height.
     */
    inline fun text(
        cw: Float,
        ch: Float,
        need1: Float,
        height: (Float) -> Float,
        hugAt: (scale: Float, drawnHeight: Float) -> Float,
        minScale: Float = MIN_SCALE,
        /** A tile's content grows to take up its cell, up to this (measuring passes 1: content as it is). */
        maxScale: Float = 1f
    ): TileFit {
        val widest = (if (need1 > 0f) min(maxScale, cw / need1) else maxScale).coerceAtLeast(minScale)
        var s = widest
        var dh = height(cw / s) * s
        if (dh > ch) {
            var tall = dh
            for (i in 0 until 6) {
                val next = (s * ch / tall).coerceIn(minScale, widest)
                if (abs(next - s) < 0.0005f) {
                    s = next
                    break
                }
                s = next
                tall = height(cw / s) * s
            }
            // Wrapping can make the last step slightly too big; never end up over.
            var guard = 0
            while (height(cw / s) * s > ch + 0.5f && s > minScale && guard++ < 40) {
                s = max(minScale, s - 0.004f)
            }
            // Still over (wrapping makes the height jump about as the scale moves, and growth can start far above): a
            // scale between the least and this one at which it fits, found by halving.
            if (height(cw / s) * s > ch + 0.5f && s > minScale && height(cw / minScale) * minScale <= ch + 0.5f) {
                var lo = minScale
                var hi = s
                for (i in 0 until 12) {
                    val mid = (lo + hi) / 2f
                    if (height(cw / mid) * mid <= ch + 0.5f) lo = mid else hi = mid
                }
                s = lo
            }
            dh = height(cw / s) * s
        }
        val cut = dh > ch + 1f || need1 > cw / s + 1f
        val th = min(dh, ch)
        val tw = if (cut) cw else hugAt(s, dh)
        return TileFit(tw, th, s, cut)
    }

    /**
     * A title: [text], with [inset] of air above it (pass 0 when it is not at the top edge). The air is soft: it
     * shrinks before the title would be scaled below [TITLE_KEEP] (or below what the cell's width already forces)
     * or cut, down to none.
     */
    fun title(
        cw: Float,
        ch: Float,
        need1: Float,
        inset: Float,
        height: (Float) -> Float,
        hugAt: (scale: Float, drawnHeight: Float) -> Float,
        maxScale: Float = 1f
    ): TileFit {
        if (inset <= 0f) return text(cw, ch, need1, height, hugAt, maxScale = maxScale)
        // Most cells have room for the air: one fit settles it.
        val full = text(cw, ch - inset, need1, height, hugAt, maxScale = maxScale)
        if (!full.cut && full.scale >= TITLE_KEEP) return withInset(full, inset)
        val bare = text(cw, ch, need1, height, hugAt, maxScale = maxScale)
        if (bare.cut) return bare
        val keep = min(bare.scale, TITLE_KEEP) - 0.001f
        if (!full.cut && full.scale >= keep) return withInset(full, inset)
        var lo = 0f
        var hi = inset
        var best = bare
        for (i in 0 until 7) {
            val mid = (lo + hi) / 2f
            val f = text(cw, ch - mid, need1, height, hugAt, maxScale = maxScale)
            if (!f.cut && f.scale >= keep) {
                lo = mid
                best = f
            } else {
                hi = mid
            }
        }
        return if (lo < 1f) bare else withInset(best, lo)
    }

    fun withInset(f: TileFit, inset: Float): TileFit = TileFit(f.w, f.h + inset, f.scale, f.cut, f.columns, inset)

    /** A drawable in a cell: the shape within its range that spans the cell, as big as fits, never beyond its caps. */
    fun drawable(shape: DrawableShape, cw: Float, ch: Float): TileFit {
        if (!shape.hasContent) return TileFit(cw, ch, 1f, false)
        var pw = min(cw, shape.maxW)
        val maxH = min(ch, shape.maxH)
        val ratio = (pw / maxH.coerceAtLeast(1f)).coerceIn(shape.ratioMin, shape.ratioMax)
        var ph = pw / ratio
        if (ph > maxH) {
            ph = maxH
            pw = ph * ratio
        }
        val cut = cw < shape.minW - 0.5f || ch < shape.minH - 0.5f
        // Nearly reaching a side: reach it. A shape that cannot change (a circle) keeps its size.
        if (shape.crops || shape.ratioMax > shape.ratioMin + 0.001f) {
            val lo = shape.ratioMin / HUG_SLACK
            val hi = shape.ratioMax * HUG_SLACK
            val w2 = if (cw - pw > 0.5f && cw - pw <= max(HUG_PX, HUG_SHARE * cw)) cw else pw
            val h2 = if (ch - ph > 0.5f && ch - ph <= max(HUG_PX, HUG_SHARE * ch)) ch else ph
            if (w2 / h2 in lo..hi) return TileFit(w2, h2, 1f, cut)
            if (w2 / ph in lo..hi) return TileFit(w2, ph, 1f, cut)
            if (pw / h2 in lo..hi) return TileFit(pw, h2, 1f, cut)
        }
        return TileFit(pw, ph, 1f, cut)
    }

    /** What a drawable asks for when it may have up to [w] by [h]: all that fits when it is greedy, else its own shape. */
    fun drawableAsk(shape: DrawableShape, w: Float, h: Float): TileFit {
        // Nothing to draw: a placeholder that fills its cell, so it asks for a modest shape.
        if (!shape.hasContent) return TileFit(min(min(w, h * 1.33f), shape.maxW), min(min(h, w * 0.75f), shape.maxH), 1f, false)
        if (shape.greedy) return drawable(shape, w, h)
        val aw = min(w, max(h * shape.ratio, shape.minW))
        val ah = min(h, max(w / shape.ratio, shape.minH))
        return drawable(shape, aw, ah)
    }

    /**
     * A statistic in a cell: it reflows before it shrinks. It keeps the most columns it can (the layout it is designed
     * with first) and takes fewer only when that lets it be bigger and the layout with more would have to shrink below
     * [REFLOW_KEEP] of that; then it shrinks as one unit, never below [minScale]. [fill] makes the tile as wide as
     * the cell; else it is as wide as its layout.
     */
    fun statistic(shape: StatShape, cw: Float, ch: Float, fill: Boolean, minScale: Float = STAT_MIN_SCALE, maxScale: Float = 1f): TileFit {
        var best = 0
        var bestS = -1f
        for (k in shape.widths.indices) {
            val s = min(1f, min(cw / shape.widths[k], ch / shape.heights[k].coerceAtLeast(1f)))
            if (bestS < 0f || (s > bestS + 0.0005f && bestS < s * REFLOW_KEEP)) {
                best = k
                bestS = s
            }
        }
        // The layout is picked as for content at its size (reflowing only to keep from shrinking); then a layout with
        // room to spare grows into its cell, up to [maxScale].
        if (maxScale > 1f && bestS >= 1f) bestS = min(maxScale, min(cw / shape.widths[best], ch / shape.heights[best].coerceAtLeast(1f)))
        val cut = bestS < minScale - 0.0005f
        val s = max(bestS, minScale)
        val th = min(shape.heights[best] * s, ch)
        val tw = if (fill || cut) cw else min(cw, shape.widths[best] * s)
        return TileFit(tw, th, s, cut, shape.columns[best])
    }

    /**
     * [FlexFit] on the optimiser's numbers. Auto align sets every non-drawable block to fill its cell, so the tile is as
     * wide as the cell. [banner]: the tile runs across the top of the card (see [FlexTree.banner]), so a title keeps its air.
     */
    fun of(item: AutoItem, cw: Float, ch: Float, banner: Boolean): TileFit = when (item.kind) {
        FlexBlockClass.DRAWABLE -> drawable(item.drawable!!, cw, ch)
        FlexBlockClass.STATISTIC -> statistic(item.stat!!, cw, ch, true, maxScale = FILL_MAX)
        FlexBlockClass.TITLE -> title(cw, ch, item.need, if (banner) item.inset else 0f, { item.height(it) }, { _, _ -> cw }, FILL_TITLE)
        FlexBlockClass.TEXT -> text(cw, ch, item.need, { item.height(it) }, { _, _ -> cw }, maxScale = FILL_MAX)
    }
}

/** The most a drawable's cell can use along a split ([stacked]: its height), [cross] the cell's size across it. */
internal fun drawableCap(shape: DrawableShape, stacked: Boolean, cross: Float): Float {
    if (!shape.hasContent) return if (stacked) shape.maxH else shape.maxW
    return if (stacked) min(shape.maxH, cross / shape.ratioMin) else min(shape.maxW, cross * shape.ratioMax)
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
     * Side by side, how far apart the visible bottoms of the parts are, as a share of the row's height: a picture that
     * cannot be cropped tall enough to fill its cell next to a panel that stretches to the bottom of its own.
     */
    const val EDGES = 2.0

    /** A statistic reflowed into fewer columns than it is designed with, per column. */
    const val REFLOW = 0.2

    /** What a picture earns per share of the card it covers (up to half the card). */
    const val PICTURE = 3.5

    /** Card height no cell was given (a share of the content height): wasted like room inside a cell. */
    const val LEFT_OVER = 1.2

    /** A title at the top that had to give up the air above it (all of it). */
    const val NO_AIR = 0.6

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

    /** [top]: the tile touches the top edge; [air]: it runs across the top (a title there is owed its air). */
    fun tile(item: AutoItem, space: AutoSpace, cw: Float, ch: Float, fit: TileFit, top: Boolean, air: Boolean = top): Double {
        val area = (space.width * space.height).toDouble().coerceAtLeast(1.0)
        val waste = (cw.toDouble() * ch - fit.w.toDouble() * fit.h) / area
        // Content grown into its cell is not rewarded or charged for its scale (only shrinking is), and its wrapping
        // is judged at its own size.
        val s = min(1.0, fit.scale.toDouble())
        var c = if (fit.cut) CUT else 0.0
        when (item.kind) {
            FlexBlockClass.TEXT -> {
                c += 5.0 * (1.0 - s) + 9.0 * max(0.0, 0.85 - s)
                c += 1.2 * waste
                c += 4.0 * max(0.0, 0.30 - cw.toDouble() / space.width)
                val at = fit.scale.coerceAtLeast(0.3f)
                val full = item.height(space.width / at).toDouble().coerceAtLeast(1.0)
                val wrapped = item.height(cw / at) / full
                c += 0.8 * min(max(0.0, wrapped - 1.3), 5.0)
            }
            FlexBlockClass.STATISTIC -> {
                // Shrunk as one unit: the same price as text, but nothing wraps, so fewer columns is the price instead.
                c += 5.0 * (1.0 - s) + 9.0 * max(0.0, 0.85 - s)
                c += 1.2 * waste
                c += 4.0 * max(0.0, 0.30 - cw.toDouble() / space.width)
                val designed = item.stat?.designed ?: 1
                if (fit.columns in 1 until designed) c += REFLOW * (designed - fit.columns)
            }
            FlexBlockClass.TITLE -> {
                c += 6.0 * (1.0 - s) + 9.0 * max(0.0, 0.85 - s)
                val at = fit.scale.coerceAtLeast(0.3f)
                val full = item.height(space.width / at).toDouble().coerceAtLeast(1.0)
                val wrapped = item.height(cw / at) / full
                c += 4.0 * min(max(0.0, wrapped - 1.0), 3.0)
                c += 2.0 * waste
                if (!top) c += 0.5
                else if (air && item.inset > 0f) c += NO_AIR * (1.0 - fit.inset / item.inset).coerceIn(0.0, 1.0)
            }
            FlexBlockClass.DRAWABLE -> {
                // A drawable is rewarded for every bit of the card it covers, up to about half of it: pictures are what a
                // card is for, so a tiling that shows one bigger is worth a little shrinking elsewhere.
                val frac = fit.w.toDouble() * fit.h / area
                c += 1.6 * waste
                c -= PICTURE * min(frac, 0.5)
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

internal const val FULL_EFFORT = 300_000

internal const val QUICK_EFFORT = 40_000

internal class FlexAutoAligner(private val items: List<AutoItem>, private val space: AutoSpace, effort: Int = FULL_EFFORT) {

    private val n = items.size
    private val gap = space.gap
    private val memo = HashMap<Long, AutoSub>()
    private val none = AutoSub(Double.POSITIVE_INFINITY, null, 0f)
    private var budget = effort
    private val segLimit = when {
        n <= 8 -> n
        n <= 10 -> 4
        else -> 3
    }
    /** The least a drawable is planned at along either side: a picture smaller than this hardly reads. */
    private val drawableMin = max(MIN_TILE * 1.5f, 0.2f * min(space.width, space.height))

    /** The best tilings: one that is cut into rows at the top, one cut into columns, whichever is cheaper in each case. */
    fun solve(): List<AutoCandidate> {
        if (n == 0 || n > MAX_BLOCKS) return emptyList()
        if (n == 1) return listOf(AutoCandidate(FlexLeaf(items[0].id), 0.0))
        val out = ArrayList<AutoCandidate>(2)
        for (avoid in intArrayOf(ROWS, COLUMNS)) {
            val sub = solveSub(0, n - 1, space.width, space.height, avoid, BANNER)
            sub.node?.let { out += AutoCandidate(it, sub.cost) }
        }
        return out
    }

    /**
     * The best sizes for a tiling made by hand, [tree] (its tiles in the order of the items): the structure is kept,
     * and every group that shares by content has its shares picked the way the search picks them; groups with shares
     * of their own (a border the person dragged) keep them. Null when the tree does not hold exactly the items.
     */
    fun solveFixed(tree: FlexNode): AutoCandidate? {
        if (n == 0 || FlexTree.leaves(tree) != items.map { it.id }) return null
        val sub = fixed(tree, 0, n - 1, space.width, space.height, BANNER)
        return sub.node?.let { AutoCandidate(it, sub.cost) }
    }

    private val fixedMemo = HashMap<Long, AutoSub>()

    private fun fixed(node: FlexNode, i: Int, j: Int, w: Float, h: Float, top: Int): AutoSub {
        if (node is FlexLeaf) return leaf(i, w, h, top)
        val key = ((((i * 16L + j) * 3 + top) * 4096L + (w / 2f).roundToInt().coerceIn(0, 4095)) * 4096L) +
            (h / 2f).roundToInt().coerceIn(0, 4095)
        fixedMemo[key]?.let { return it }
        val g = node as FlexGroup
        val m = g.children.size
        val from = IntArray(m)
        val to = IntArray(m)
        var at = i
        for (k in 0 until m) {
            from[k] = at
            at += FlexTree.leaves(g.children[k]).size
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
                val kids = ArrayList<FlexNode>(m)
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
                cost += AutoCost.group(stacked, sizes, used)
                if (cost < best.cost) {
                    val extent = if (stacked) used.sum() + gap * (m - 1) else (used.maxOrNull() ?: 0f)
                    best = AutoSub(cost, FlexGroup(stacked, kids, if (g.isAuto) sizes.toList() else g.weights), 0f, extent)
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
    private fun solveSub(i: Int, j: Int, w: Float, h: Float, avoid: Int, top: Int): AutoSub {
        val key = ((((((i * 16L + j) * 3 + avoid) * 3 + top) * 2048L) + (w / MEMO_STEP).roundToInt().coerceIn(0, 2047)) * 4096L) +
            (h / MEMO_STEP).roundToInt().coerceIn(0, 4095)
        memo[key]?.let { return it }
        val out = if (i == j) leaf(i, w, h, top) else group(i, j, w, h, avoid, top)
        memo[key] = out
        return out
    }

    private fun leaf(i: Int, w: Float, h: Float, top: Int): AutoSub {
        val item = items[i]
        val fit = FlexFit.of(item, w, h, top == BANNER)
        val cost = AutoCost.tile(item, space, w, h, fit, top != NOT_TOP, top == BANNER)
        return AutoSub(cost, FlexLeaf(item.id), floorOf(item), fit.h)
    }

    /** The narrowest the solver lets a tile be: what its content needs at the least scale it plans for. A drawable has no floor there. */
    private fun floorOf(item: AutoItem): Float = if (item.kind == FlexBlockClass.DRAWABLE) 0f else item.need * item.planScale

    /** Cuts the run [i]..[j] (in the way [avoid] does not say) into neighbouring parts, each part a tile or a group cut the other way. */
    private fun group(i: Int, j: Int, w: Float, h: Float, avoid: Int, top: Int): AutoSub {
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

    private fun evaluate(stacked: Boolean, from: IntArray, to: IntArray, sizes: FloatArray, cross: Float, top: Int): AutoSub {
        val m = sizes.size
        var cost = 0.0
        val kids = ArrayList<FlexNode>(m)
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
        cost += AutoCost.group(stacked, sizes, used)
        val extent = if (stacked) used.sum() + gap * (m - 1) else (used.maxOrNull() ?: 0f)
        return AutoSub(cost, FlexGroup(stacked, kids, sizes.toList()), floor, extent)
    }

    // ---- what a part asks for ------------------------------------------------------------------------------

    private fun leafWantsHeight(item: AutoItem, w: Float): Float = when (item.kind) {
        FlexBlockClass.DRAWABLE -> FlexFit.drawableAsk(item.drawable!!, w, BIG).h
        // A title at the top keeps its air above it (it is read first, so in a search it is always at the top).
        FlexBlockClass.TITLE -> textAsk(item, w) + item.inset
        FlexBlockClass.STATISTIC, FlexBlockClass.TEXT -> textAsk(item, w)
    }

    /** How tall text-like content is at the width [w] with height to spare: scaled down only to keep its words whole (a statistic: its reflowed height). */
    private fun textAsk(item: AutoItem, w: Float): Float =
        FlexFit.text(w, BIG, item.need, { item.height(it) }, { s, _ -> min(w, item.hug * s) }, item.minScale).h

    private fun leafHug(item: AutoItem): Float =
        if (item.kind == FlexBlockClass.DRAWABLE) FlexFit.drawableAsk(item.drawable!!, BIG, BIG).w.coerceIn(1f, space.width)
        else item.hug.coerceAtLeast(1f)

    /** The narrowest width at which the content is no taller than [h]; the width it wants when it cannot be that short. */
    internal fun leafWantsWidth(item: AutoItem, h: Float, maxW: Float): Float {
        if (item.kind == FlexBlockClass.DRAWABLE) return FlexFit.drawableAsk(item.drawable!!, BIG, h).w
        val room = if (item.kind == FlexBlockClass.TITLE) (h - item.inset).coerceAtLeast(1f) else h
        val top = min(item.hug, maxW).coerceAtLeast(1f)
        val low = min(item.need, top)
        if (item.height(top) > room) {
            // Too tall for [h] even as wide as it likes: it will be shrunk to fit the height, and then needs only that
            // shrunk width (more would sit empty beside it, width a picture next to it could use).
            val stat = item.stat
            if (stat != null) {
                val f = FlexFit.statistic(stat, top, room, false)
                return (stat.widths[stat.columns.indexOf(f.columns).coerceAtLeast(0)] * f.scale).coerceIn(min(low * f.scale, top), top)
            }
            val f = FlexFit.text(top, room, item.need, { item.height(it) }, { s, _ -> min(top, item.hug * s) }, item.planScale)
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
            val f = if (item.kind == FlexBlockClass.DRAWABLE) {
                max(drawableMin, item.drawable!!.minH)
            } else if (from == to) {
                val least = item.planScale
                max(item.height(w / least) * least, min(MIN_TILE, leafWantsHeight(item, w)))
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
            floor = max(
                floor,
                if (item.kind == FlexBlockClass.DRAWABLE) max(drawableMin, item.drawable!!.minW)
                else max(item.need * item.planScale, min(MIN_TILE, item.hug))
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
                prio += items[b].priority
                hunger += items[b].appetite
            }
            val ask = if (stacked) wantsHeight(from, to, cross) else wantsWidth(from, to, cross)
            floatArrayOf(floor, cap, ask.coerceAtLeast(1f).coerceIn(floor, max(floor, cap)), prio, hunger)
        }
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

        /** The height all [items] need one under the other at the full [FlexAutoAligner] width: the card Free size would make without a layout. */
        fun stackedHeight(items: List<AutoItem>, space: AutoSpace): Float {
            var sum = space.gap * (items.size - 1).coerceAtLeast(0)
            items.forEachIndexed { i, item ->
                sum += when (item.kind) {
                    FlexBlockClass.DRAWABLE -> FlexFit.drawableAsk(item.drawable!!, space.width, BIG).h
                    else -> {
                        val h = FlexFit.text(space.width, BIG, item.need, { item.height(it) }, { s, _ -> min(space.width, item.hug * s) }, item.minScale).h
                        if (i == 0 && item.kind == FlexBlockClass.TITLE) h + item.inset else h
                    }
                }
            }
            return sum
        }
    }
}
