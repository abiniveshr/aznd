package com.ashrdev.aznd.ui.dashboard

import com.ashrdev.aznd.ui.common.tiling.AlignItem
import com.ashrdev.aznd.ui.common.tiling.AlignSpace
import com.ashrdev.aznd.ui.common.tiling.TileFit
import com.ashrdev.aznd.ui.common.tiling.TileKind
import com.ashrdev.aznd.ui.common.tiling.TileRules
import com.ashrdev.aznd.ui.common.tiling.TileZoom
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object DashboardTileRules : TileRules {
    override val minTile = 64f
    override val resizeSnap = 0.03f
    override val dropGapReach = 16f
    override val swapShare = 0.18f
    override val snapReach = 8f

    override val splitLow = 0.25f
    override val splitHigh = 0.75f
    override val rowGrow = 1.25f
    override val zoom = TileZoom(min = 0.8f, limit = 0.9f, max = 1.5f)

    override val textMinScale = 0.8f
    override val statMinScale = 0.8f
    override val fillMax = 1.5f
    override val fillTitle = 1.2f

    override val hugShare = 0.12f
    override val hugPx = 16f
    override val hugSlack = 1.3f

    override val titleKeep = 0.92f
    override val reflowKeep = 0.92f

    override val drawableLeastTiles = 1.6f
    override val drawableLeastShare = 0.22f

    const val CUT = 60.0
    const val GROUP = 0.04
    const val SPLIT = 0.04
    const val OFF_CENTRE = 1.2
    const val UNEVEN = 1.2
    const val RAGGED = 0.6
    const val EDGES = 1.2
    const val REFLOW = 0.15
    const val PICTURE = 1.4
    const val LEFT_OVER = 1.2
    const val SHRINK = 6.0
    const val SMALL = 3.0
    const val GROWN = 0.4
    const val SPARSE = 1.5
    const val SPARSE_FREE = 0.4
    const val BAR = 0.12
    const val BAR_FREE = 3.2

    override val cutCost = CUT
    override val hiddenCost = 25.0
    override val raggedCost = RAGGED
    override val edgesCost = EDGES
    override val leftOverCost = LEFT_OVER

    override fun minScale(kind: TileKind): Float = when (kind) {
        TileKind.STATISTIC -> statMinScale
        TileKind.DRAWABLE -> 1f
        TileKind.TITLE, TileKind.TEXT -> textMinScale
    }

    override fun priority(kind: TileKind): Float = when (kind) {
        TileKind.DRAWABLE -> 2.5f
        TileKind.TITLE -> 2f
        TileKind.STATISTIC -> 2f
        TileKind.TEXT -> 1f
    }

    override fun appetite(kind: TileKind): Float = when (kind) {
        TileKind.DRAWABLE -> 2.5f
        TileKind.TITLE -> 0.2f
        TileKind.STATISTIC -> 1f
        TileKind.TEXT -> 1f
    }

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

    override fun tileCost(item: AlignItem, space: AlignSpace, cw: Float, ch: Float, fit: TileFit, top: Boolean, air: Boolean): Double {
        val area = (space.width * space.height).toDouble().coerceAtLeast(1.0)
        val waste = (cw.toDouble() * ch - fit.w.toDouble() * fit.h) / area
        val s = min(1.0, fit.scale.toDouble())
        var c = if (fit.cut) CUT else 0.0
        when (item.kind) {
            TileKind.STATISTIC, TileKind.TEXT, TileKind.TITLE -> {
                c += SHRINK * (1.0 - s) + 9.0 * max(0.0, 0.85 - s)
                c += 1.0 * waste
                c += GROWN * max(0.0, fit.scale - 1.0)
                val designed = item.stat?.designed ?: 1
                if (fit.columns in 1 until designed) c += REFLOW * (designed - fit.columns)
                val stat = item.stat
                if (stat != null) {
                    val k = stat.columns.indexOf(fit.columns).coerceAtLeast(0)
                    val empty = 1.0 - stat.widths[k] * fit.scale / cw.coerceAtLeast(1f)
                    c += SPARSE * max(0.0, empty - SPARSE_FREE)
                }
                val stretched = ch / fit.h.coerceAtLeast(1f)
                if (stretched > 2.2f) c += 0.3 * min(stretched - 2.2, 4.0)
                val flat = cw / ch.coerceAtLeast(1f)
                if (flat > BAR_FREE) c += BAR * min(flat - BAR_FREE, 6.0)
            }
            TileKind.DRAWABLE -> {
                val frac = fit.w.toDouble() * fit.h / area
                c += 1.4 * waste
                c -= PICTURE * min(frac, 0.35)
                if (frac < 0.1) c += SMALL * (0.1 - frac)
            }
        }
        return c
    }
}
