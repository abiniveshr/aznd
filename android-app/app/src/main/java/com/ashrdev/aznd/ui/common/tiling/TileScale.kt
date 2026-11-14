package com.ashrdev.aznd.ui.common.tiling

import kotlin.math.abs

/** How a tiling came out at one overall content scale: [waste] the share of the area its content leaves empty, [clipped] cut or hidden blocks, [short] blocks drawn below their least. */
class TileScaleResult(val waste: Double, val clipped: Int = 0, val short: Int = 0) {
    val clean: Boolean get() = clipped == 0 && short == 0
}

/** The scale [TileScale.choose] picked and how the tiling came out there. */
class TileScalePick(val scale: Float, val result: TileScaleResult)

/**
 * Chooses a card's or page's overall content scale (the zoom its content starts from) as part of a fit: the tiling is
 * tried at several scales and the scale kept is the one that leaves the least room empty while nothing is clipped. The
 * Android side only supplies what a tiling comes to at a scale.
 */
object TileScale {

    /** A scale must leave this much less room empty than the current one to replace it, so a fit does not drift for nothing. */
    const val MARGIN = 0.01

    /** How far the Module size slider may be from its default and still count as untouched (its steps are 0.05). */
    const val DEFAULT_TOLERANCE = 0.01f

    /**
     * Whether a card's Module size [current] is still its [default]: only then may best fit choose a size of its own.
     * A person who moved the slider has said what size they want, and the wand only rearranges.
     */
    fun mayRescale(current: Float, default: Float): Boolean = abs(current - default) < DEFAULT_TOLERANCE

    /**
     * The scales worth trying between [min] and [max]: [steps] spread evenly on a log scale (a scale of 2 is as far
     * from 1 as 0.5 is), each rounded to [step], plus [current]. Ascending, without repeats.
     */
    fun candidates(current: Float, min: Float, max: Float, steps: Int, step: Float = 0.05f): List<Float> {
        val out = java.util.TreeSet<Float>()
        out += current
        if (steps > 1 && min > 0f && max > min) {
            val ratio = Math.pow((max / min).toDouble(), 1.0 / (steps - 1))
            for (i in 0 until steps) {
                val v = Math.round(min * Math.pow(ratio, i.toDouble()) / step) * step
                out += v.toFloat().coerceIn(min, max)
            }
        }
        return out.toList()
    }

    /**
     * The best of [scales] by [eval] (null: that scale cannot be tried). A scale with nothing clipped and no block
     * below its least beats one with; among those, the least waste wins, and [current] stays unless another leaves
     * [MARGIN] less. When none is clean, the one with the fewest clipped blocks (then fewest short, then least waste).
     * [eval] runs once per scale. Null when no scale could be tried.
     */
    fun choose(scales: List<Float>, current: Float, eval: (Float) -> TileScaleResult?): TileScalePick? {
        var best: TileScalePick? = null
        var held: TileScalePick? = null
        // The current scale first, then the others from the nearest to it: a tie keeps the nearest.
        for (s in scales.distinct().sortedBy { abs(it - current) }) {
            val r = eval(s) ?: continue
            val pick = TileScalePick(s, r)
            if (s == current) held = pick
            if (best == null || beats(r, best.result)) best = pick
        }
        val top = best ?: return null
        val now = held ?: return top
        if (top.scale == now.scale) return now
        val gain = now.result.waste - top.result.waste
        return if (!now.result.clean || gain > MARGIN || top.result.clipped < now.result.clipped) top else now
    }

    private fun beats(a: TileScaleResult, b: TileScaleResult): Boolean = when {
        a.clipped != b.clipped -> a.clipped < b.clipped
        a.short != b.short -> a.short < b.short
        else -> a.waste < b.waste - 1e-9
    }
}
