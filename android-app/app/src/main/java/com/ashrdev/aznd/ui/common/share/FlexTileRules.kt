package com.ashrdev.aznd.ui.common.share

import com.ashrdev.aznd.ui.common.tiling.AlignItem
import com.ashrdev.aznd.ui.common.tiling.AlignSpace
import com.ashrdev.aznd.ui.common.tiling.TileFit
import com.ashrdev.aznd.ui.common.tiling.TileFitter
import com.ashrdev.aznd.ui.common.tiling.TileKind
import com.ashrdev.aznd.ui.common.tiling.TileRules
import com.ashrdev.aznd.ui.common.tiling.TileZoom
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * Flex's ruleset for the common tiling engine (ui/common/tiling).
 *
 *  - Full width: a card is stacked, one module under the other and edge to edge, unless splitting pays for itself.
 *    A split (side by side) costs [SPLIT]; it is kept centred ([OFF_CENTRE]) unless a part needs more room, and its
 *    parts are kept equally tall ([UNEVEN]), so the modules in a row shrink together. Statistics are the best
 *    partners there: their height at a width is their reflowed height, so a row can even them out.
 *  - Priorities: drawables win spare room (their area is rewarded, and they are the ones to grow when there is
 *    slack and the last to shrink when there is none); titles are never shrunk, never wrapped when avoidable, and
 *    like the top edge.
 */
object FlexTileRules : TileRules {
    override val minTile = 140f
    override val resizeSnap = 0.03f
    override val dropGapReach = 40f
    override val swapShare = 0.18f
    override val snapReach = 14f

    override val splitLow = 0.2f
    override val splitHigh = 0.8f
    override val rowGrow = 1.3f
    override val zoom = TileZoom(min = 0.45f, limit = 0.8f, max = 1.8f)
    const val TITLE_ZOOM_MAX = 1.4f

    /** Titles and text are never shrunk below this. Same as [FlexPainter.MIN_TILE_SCALE]. */
    override val textMinScale = 0.6f

    /** Statistics have no wrapping to fall back on, so they may shrink further. */
    override val statMinScale = 0.45f

    /**
     * A tile's content takes up its cell: it is drawn bigger than its size (the block's own and the module size) when
     * the cell has room, up to this. The size control of the block's menu sets where it starts.
     */
    override val fillMax = 1.5f

    /** A title grows less: past this it shouts. Its cell is never given more height than it takes at this. */
    override val fillTitle = 1.25f

    /**
     * A picture hugs a side of its cell it nearly reaches: when the room left beside it (or above and below it) is at
     * most this share of the cell's side (and at least [hugPx]), it stretches to fill it, its shape going up to
     * [hugSlack] past its range (a photo is re-cropped, a chart or calendar stretched). Further away, it sits in the
     * middle of the cell.
     */
    override val hugShare = 0.12f
    override val hugPx = 40f
    override val hugSlack = 1.3f

    /** A title gives up the air above it before it would be shrunk below this. */
    override val titleKeep = 0.92f

    /**
     * A statistic keeps the columns it has while it only has to shrink to this; past it, fewer columns (when they let it
     * be bigger) come first. A grid that would shrink by a few percent does not reshuffle into more rows.
     */
    override val reflowKeep = 0.92f

    override val drawableLeastTiles = 1.5f
    override val drawableLeastShare = 0.2f

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

    override val cutCost = CUT
    override val hiddenCost = HIDDEN
    override val raggedCost = RAGGED
    override val edgesCost = EDGES
    override val leftOverCost = LEFT_OVER

    override fun minScale(kind: TileKind): Float = when (kind) {
        TileKind.STATISTIC -> statMinScale
        TileKind.DRAWABLE -> 1f
        TileKind.TITLE, TileKind.TEXT -> textMinScale
    }

    /** How much of a shortage a block of this kind is spared (the higher, the later it shrinks). */
    override fun priority(kind: TileKind): Float = when (kind) {
        TileKind.DRAWABLE -> 3f
        TileKind.TITLE -> 2f
        TileKind.STATISTIC -> 1.5f
        TileKind.TEXT -> 1f
    }

    /** How much of the spare room a block of this kind takes: drawables most, titles hardly any (a title with air round it looks lost). */
    override fun appetite(kind: TileKind): Float = when (kind) {
        TileKind.DRAWABLE -> 3f
        TileKind.TITLE -> 0.2f
        TileKind.STATISTIC -> 1f
        TileKind.TEXT -> 1f
    }

    /**
     * What a group costs beyond its tiles. [sizes] is what each part got along the split and [used] how far each
     * reaches across it (for side by side parts, how tall the tile or stack in it is).
     */
    override fun groupCost(stacked: Boolean, sizes: FloatArray, used: FloatArray): Double {
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
    override fun tileCost(item: AlignItem, space: AlignSpace, cw: Float, ch: Float, fit: TileFit, top: Boolean, air: Boolean): Double {
        val area = (space.width * space.height).toDouble().coerceAtLeast(1.0)
        val waste = (cw.toDouble() * ch - fit.w.toDouble() * fit.h) / area
        // Content grown into its cell is not rewarded or charged for its scale (only shrinking is), and its wrapping
        // is judged at its own size.
        val s = min(1.0, fit.scale.toDouble())
        var c = if (fit.cut) CUT else 0.0
        when (item.kind) {
            TileKind.TEXT -> {
                c += 5.0 * (1.0 - s) + 9.0 * max(0.0, 0.85 - s)
                c += 1.2 * waste
                c += 4.0 * max(0.0, 0.30 - cw.toDouble() / space.width)
                val at = fit.scale.coerceAtLeast(0.3f)
                val full = item.height(space.width / at).toDouble().coerceAtLeast(1.0)
                val wrapped = item.height(cw / at) / full
                c += 0.8 * min(max(0.0, wrapped - 1.3), 5.0)
            }
            TileKind.STATISTIC -> {
                // Shrunk as one unit: the same price as text, but nothing wraps, so fewer columns is the price instead.
                c += 5.0 * (1.0 - s) + 9.0 * max(0.0, 0.85 - s)
                c += 1.2 * waste
                c += 4.0 * max(0.0, 0.30 - cw.toDouble() / space.width)
                val designed = item.stat?.designed ?: 1
                if (fit.columns in 1 until designed) c += REFLOW * (designed - fit.columns)
            }
            TileKind.TITLE -> {
                c += 6.0 * (1.0 - s) + 9.0 * max(0.0, 0.85 - s)
                val at = fit.scale.coerceAtLeast(0.3f)
                val full = item.height(space.width / at).toDouble().coerceAtLeast(1.0)
                val wrapped = item.height(cw / at) / full
                c += 4.0 * min(max(0.0, wrapped - 1.0), 3.0)
                c += 2.0 * waste
                if (!top) c += 0.5
                else if (air && item.inset > 0f) c += NO_AIR * (1.0 - fit.inset / item.inset).coerceIn(0.0, 1.0)
            }
            TileKind.DRAWABLE -> {
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

internal val FlexFit = TileFitter(FlexTileRules)
