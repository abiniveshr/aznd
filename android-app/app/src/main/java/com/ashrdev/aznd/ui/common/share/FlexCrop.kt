package com.ashrdev.aznd.ui.common.share

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A rectangle: [l], [t], [r], [b] are the left, top, right and bottom sides. In [FlexCrop.normalise], [FlexCrop.drag]
 * and [FlexCrop.handleAt] the numbers are fractions of the photo (0 is its left / top, 1 its right / bottom); in
 * [FlexCrop.source] they are photo pixels.
 */
data class FlexCropBox(val l: Float, val t: Float, val r: Float, val b: Float) {
    val width: Float get() = r - l
    val height: Float get() = b - t
}

/** What a finger can grab on the crop frame: a corner, an edge, or the inside (moves the whole frame). */
enum class FlexCropHandle { TOP_LEFT, TOP, TOP_RIGHT, LEFT, RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT, MOVE }

/**
 * The maths of a free crop of a photo block: the person frames any rectangle of the photo, stored as four fractions
 * of the photo's width and height (see [FlexBlock.cropL] and its three neighbours). Pure, so it is tested by the
 * harness (harness/tiling/CropChecks.kt).
 */
object FlexCrop {
    /** The least a crop side may be, as a fraction of the photo's side, so the frame never collapses. */
    const val MIN_SIDE = 0.1f

    /** Float rounding slack when a side is compared with [MIN_SIDE]. */
    private const val SLACK = 1e-6f

    /** One side pair made valid: ordered, inside 0..1 and at least [MIN_SIDE] apart (grown around its middle). */
    private fun axis(a: Float, b: Float, fallbackLo: Float, fallbackHi: Float): FloatArray {
        if (a.isNaN() || b.isNaN()) return floatArrayOf(fallbackLo, fallbackHi)
        var lo = min(a, b).coerceIn(0f, 1f)
        var hi = max(a, b).coerceIn(0f, 1f)
        if (hi - lo < MIN_SIDE - SLACK) {
            val mid = (lo + hi) / 2f
            lo = mid - MIN_SIDE / 2f
            hi = lo + MIN_SIDE
            if (lo < 0f) { lo = 0f; hi = MIN_SIDE }
            if (hi > 1f) { hi = 1f; lo = 1f - MIN_SIDE }
        }
        return floatArrayOf(lo, hi)
    }

    /** [v] kept between [lo] and [hi]; when rounding leaves no room ([hi] below [lo]) it is [lo]. */
    private fun clamp(v: Float, lo: Float, hi: Float): Float = if (hi < lo) lo else v.coerceIn(lo, hi)

    /**
     * The crop made valid: the sides ordered (left before right, top before bottom), inside the photo (0 to 1) and at
     * least [MIN_SIDE] long, a too short side being grown around its middle. A number that is not a number gives the
     * whole photo for that axis. A valid crop comes back unchanged, so applying this twice gives the same as once.
     */
    fun normalise(l: Float, t: Float, r: Float, b: Float): FlexCropBox {
        val x = axis(l, r, 0f, 1f)
        val y = axis(t, b, 0f, 1f)
        return FlexCropBox(x[0], y[0], x[1], y[1])
    }

    /**
     * The crop's width divided by its height in photo pixels, for a photo of [photoW] by [photoH] pixels (a photo with
     * no size is taken as square, so the ratio is the fractions' own).
     */
    fun ratio(l: Float, t: Float, r: Float, b: Float, photoW: Float, photoH: Float): Float {
        val w = if (photoW > 0f) photoW else 1f
        val h = if (photoH > 0f) photoH else 1f
        val cw = (r - l) * w
        val ch = (b - t) * h
        return if (ch > 0f) cw / ch else 1f
    }

    /**
     * The part of the photo, in photo pixels, to draw into a tile whose width / height is [dstRatio]: the crop itself
     * when the proportions match (within a thousandth), otherwise the biggest rectangle of the tile's proportions
     * centred in the crop. So it is never outside the crop, and so never outside the photo. A tile ratio that is not
     * positive gives the crop itself.
     */
    fun source(l: Float, t: Float, r: Float, b: Float, photoW: Float, photoH: Float, dstRatio: Float): FlexCropBox {
        val x0 = l * photoW
        val x1 = r * photoW
        val y0 = t * photoH
        val y1 = b * photoH
        val cw = x1 - x0
        val ch = y1 - y0
        if (!(dstRatio > 0f) || !(cw > 0f) || !(ch > 0f)) return FlexCropBox(x0, y0, x1, y1)
        val own = cw / ch
        if (abs(own - dstRatio) <= 1e-3f * dstRatio) return FlexCropBox(x0, y0, x1, y1)
        val w = if (own > dstRatio) ch * dstRatio else cw
        val h = if (own > dstRatio) ch else cw / dstRatio
        val left = (x0 + x1) / 2f - w / 2f
        val top = (y0 + y1) / 2f - h / 2f
        return FlexCropBox(
            max(x0, left), max(y0, top),
            min(x1, left + w), min(y1, top + h)
        )
    }

    /**
     * The crop after a finger moved [handle] by [dx] across and [dy] down, both as fractions of the photo. A corner
     * moves its two sides and an edge one; the opposite sides stay where they are. [FlexCropHandle.MOVE] slides the
     * whole frame without changing its size. A side stops at the photo's border and at [MIN_SIDE] from the opposite
     * side. The crop given is made valid first (see [normalise]).
     */
    fun drag(l: Float, t: Float, r: Float, b: Float, handle: FlexCropHandle, dx: Float, dy: Float): FlexCropBox {
        val c = normalise(l, t, r, b)
        val mx = if (dx.isNaN()) 0f else dx
        val my = if (dy.isNaN()) 0f else dy
        if (handle == FlexCropHandle.MOVE) {
            val nl = (c.l + mx).coerceIn(0f, 1f - c.width)
            val nt = (c.t + my).coerceIn(0f, 1f - c.height)
            return FlexCropBox(nl, nt, nl + c.width, nt + c.height)
        }
        val moveL = handle == FlexCropHandle.TOP_LEFT || handle == FlexCropHandle.LEFT || handle == FlexCropHandle.BOTTOM_LEFT
        val moveR = handle == FlexCropHandle.TOP_RIGHT || handle == FlexCropHandle.RIGHT || handle == FlexCropHandle.BOTTOM_RIGHT
        val moveT = handle == FlexCropHandle.TOP_LEFT || handle == FlexCropHandle.TOP || handle == FlexCropHandle.TOP_RIGHT
        val moveB = handle == FlexCropHandle.BOTTOM_LEFT || handle == FlexCropHandle.BOTTOM || handle == FlexCropHandle.BOTTOM_RIGHT
        val nl = if (moveL) clamp(c.l + mx, 0f, c.r - MIN_SIDE) else c.l
        val nr = if (moveR) min(1f, clamp(c.r + mx, c.l + MIN_SIDE, 1f)) else c.r
        val nt = if (moveT) clamp(c.t + my, 0f, c.b - MIN_SIDE) else c.t
        val nb = if (moveB) min(1f, clamp(c.b + my, c.t + MIN_SIDE, 1f)) else c.b
        return FlexCropBox(nl, nt, nr, nb)
    }

    /**
     * What a touch at ([x], [y]) grabs on the crop frame, all as fractions of the photo; [slopX] and [slopY] are how far
     * from a corner or an edge still counts as on it (a finger's reach, as fractions). The nearest corner within reach
     * wins, then the nearest edge within reach, then the inside ([FlexCropHandle.MOVE]); null when the touch is
     * further out than that.
     */
    fun handleAt(l: Float, t: Float, r: Float, b: Float, x: Float, y: Float, slopX: Float, slopY: Float): FlexCropHandle? {
        val sx = max(slopX, 1e-6f)
        val sy = max(slopY, 1e-6f)
        // Distances in units of reach, so a corner and an edge compare fairly on a photo that is not square.
        val dl = abs(x - l) / sx
        val dr = abs(x - r) / sx
        val dt = abs(y - t) / sy
        val db = abs(y - b) / sy
        val corners = listOf(
            Triple(FlexCropHandle.TOP_LEFT, dl, dt),
            Triple(FlexCropHandle.TOP_RIGHT, dr, dt),
            Triple(FlexCropHandle.BOTTOM_LEFT, dl, db),
            Triple(FlexCropHandle.BOTTOM_RIGHT, dr, db)
        )
        val corner = corners.filter { it.second <= 1f && it.third <= 1f }.minByOrNull { it.second * it.second + it.third * it.third }
        if (corner != null) return corner.first
        val withinX = x >= l - slopX && x <= r + slopX
        val withinY = y >= t - slopY && y <= b + slopY
        val edges = ArrayList<Pair<FlexCropHandle, Float>>(4)
        if (withinY && dl <= 1f) edges += FlexCropHandle.LEFT to dl
        if (withinY && dr <= 1f) edges += FlexCropHandle.RIGHT to dr
        if (withinX && dt <= 1f) edges += FlexCropHandle.TOP to dt
        if (withinX && db <= 1f) edges += FlexCropHandle.BOTTOM to db
        val edge = edges.minByOrNull { it.second }
        if (edge != null) return edge.first
        return if (x in l..r && y in t..b) FlexCropHandle.MOVE else null
    }
}
