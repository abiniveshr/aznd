package com.ashrdev.aznd.ui.common.tiling

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * How a drawable takes a cell. It has no text scale: it takes a shape between [ratioMin] and [ratioMax] (width /
 * height) that lets it fill its cell edge to edge, never bigger than [maxW] by [maxH]. Below [minW] by [minH] it
 * no longer reads (a chart's labels run into each other), so a cell smaller than that cuts it.
 */
class DrawableShape(
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
    /** Keeps the shape it was fitted to as its crop, so auto align stores it. */
    val crops: Boolean
)

/**
 * A statistic's layouts, the one it is designed with first: layout k puts its cells in [columns]`[k]` columns, is
 * at least [widths]`[k]` wide and [heights]`[k]` tall, at scale 1. Nothing in a statistic wraps, so these are fixed
 * numbers; reflowing is picking a layout with fewer columns. [designed] is the column count it is meant to have
 * (fewer costs more, see [TileRules.tileCost]); 1 for a statistic that has no such count (a sentence of stats).
 * [reflowKeep] is the ruleset's [TileRules.reflowKeep].
 */
class StatShape(val columns: IntArray, val widths: FloatArray, val heights: FloatArray, val designed: Int, val reflowKeep: Float) {

    /** The least width it can have at scale 1: its narrowest layout. */
    val need: Float get() = widths[widths.size - 1]

    /** The width it takes as designed. */
    val hug: Float get() = widths[0]

    /**
     * The layout it takes when it is [w] wide and has height to spare: the most columns that fit, counting a layout
     * that only has to shrink a little (to [reflowKeep]) as fitting, else the narrowest.
     */
    fun layoutAt(w: Float): Int {
        for (k in widths.indices) if (widths[k] * reflowKeep <= w) return k
        return widths.size - 1
    }

    /**
     * Its height at scale 1 at the width [w], as a smooth curve for the search: just below the width where a
     * layout stops fitting, the height ramps up to the next layout's over [RAMP] instead of jumping, so it never
     * goes down as the width shrinks and has no steps for the search to trip on. (The tile itself is fitted
     * exactly, by [TileFitter.statistic].)
     */
    fun smoothHeight(w: Float): Float {
        val k = layoutAt(w)
        if (k == 0) return heights[0]
        // Where the layout with one more column starts to fit (see layoutAt).
        val edge = widths[k - 1] * reflowKeep
        val from = max(edge - RAMP, widths[k] * reflowKeep)
        if (w <= from || edge <= from) return heights[k]
        val t = (w - from) / (edge - from)
        return heights[k] + (heights[k - 1] - heights[k]) * t
    }

    companion object {
        const val RAMP = 64f
    }
}

/**
 * What a block's tile came to inside a cell: its size, the scale its content was shrunk to, and whether it is cut
 * off. [columns] is the layout a statistic took (0 for the others), [inset] the air a title kept above it (part
 * of [h]).
 */
class TileFit(
    val w: Float,
    val h: Float,
    val scale: Float,
    val cut: Boolean,
    val columns: Int = 0,
    val inset: Float = 0f
)

class TileAt(val scale: Float, val height: Float, val columns: Int = 0, val narrow: Boolean = false)

/** The one place a tile's size is decided, one function per tile kind, under one ruleset. The plan and the optimiser both call it. */
class TileFitter(val rules: TileRules) {

    fun minScale(kind: TileKind): Float = rules.minScale(kind)

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
        minScale: Float = rules.textMinScale,
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
     * shrinks before the title would be scaled below [TileRules.titleKeep] (or below what the cell's width already
     * forces) or cut, down to none.
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
        if (!full.cut && full.scale >= rules.titleKeep) return withInset(full, inset)
        val bare = text(cw, ch, need1, height, hugAt, maxScale = maxScale)
        if (bare.cut) return bare
        val keep = min(bare.scale, rules.titleKeep) - 0.001f
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
            val lo = shape.ratioMin / rules.hugSlack
            val hi = shape.ratioMax * rules.hugSlack
            val w2 = if (cw - pw > 0.5f && cw - pw <= max(rules.hugPx, rules.hugShare * cw)) cw else pw
            val h2 = if (ch - ph > 0.5f && ch - ph <= max(rules.hugPx, rules.hugShare * ch)) ch else ph
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
     * [TileRules.reflowKeep] of that; then it shrinks as one unit, never below [minScale]. [fill] makes the tile as wide
     * as the cell; else it is as wide as its layout.
     */
    fun statistic(shape: StatShape, cw: Float, ch: Float, fill: Boolean, minScale: Float = rules.statMinScale, maxScale: Float = 1f): TileFit {
        var best = 0
        var bestS = -1f
        for (k in shape.widths.indices) {
            val s = min(1f, min(cw / shape.widths[k], ch / shape.heights[k].coerceAtLeast(1f)))
            if (bestS < 0f || (s > bestS + 0.0005f && bestS < s * rules.reflowKeep)) {
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

    fun statAt(shape: StatShape, w: Float, h: Float, top: Float, floor: Float): TileAt {
        var best = -1
        var bestS = -1f
        for (k in shape.widths.indices) {
            val s = min(top, min(w / shape.widths[k].coerceAtLeast(1f), h / shape.heights[k].coerceAtLeast(1f)))
            if (best < 0 || (s > bestS + 0.0005f && bestS < s * rules.reflowKeep)) {
                best = k
                bestS = s
            }
        }
        val narrow = bestS < floor - 0.0005f
        val s = max(bestS, floor)
        return TileAt(s, shape.heights[best] * s, shape.columns[best], narrow)
    }

    /**
     * [TileFitter] on the optimiser's numbers. Auto align sets every non-drawable block to fill its cell, so the tile is
     * as wide as the cell. [banner]: the tile runs across the top of the area (see [TileTree.banner]), so a title keeps
     * its air.
     */
    fun of(item: AlignItem, cw: Float, ch: Float, banner: Boolean): TileFit = when (item.kind) {
        TileKind.DRAWABLE -> drawable(item.drawable!!, cw, ch)
        TileKind.STATISTIC -> statistic(item.stat!!, cw, ch, true, maxScale = rules.fillMax)
        TileKind.TITLE -> title(cw, ch, item.need, if (banner) item.inset else 0f, { item.height(it) }, { _, _ -> cw }, rules.fillTitle)
        TileKind.TEXT -> text(cw, ch, item.need, { item.height(it) }, { _, _ -> cw }, maxScale = rules.fillMax)
    }
}

/** The most a drawable's cell can use along a split ([stacked]: its height), [cross] the cell's size across it. */
fun drawableCap(shape: DrawableShape, stacked: Boolean, cross: Float): Float {
    if (!shape.hasContent) return if (stacked) shape.maxH else shape.maxW
    return if (stacked) min(shape.maxH, cross / shape.ratioMin) else min(shape.maxW, cross * shape.ratioMax)
}
