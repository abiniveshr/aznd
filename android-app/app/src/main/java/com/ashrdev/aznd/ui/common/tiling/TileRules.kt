package com.ashrdev.aznd.ui.common.tiling

import kotlin.math.floor

enum class TileKind { TITLE, DRAWABLE, STATISTIC, TEXT }

class TileZoom(val min: Float, val limit: Float, val max: Float) {

    fun zOf(q: Float): Float {
        val z = when {
            q <= 1f -> min + (limit - min) * q.coerceAtLeast(0f)
            q <= 3f -> limit + (1f - limit) * (q - 1f) / 2f
            q <= 4f -> 1f
            else -> 1f + (max - 1f) * (q - 4f).coerceAtMost(1f)
        }
        return floor(z * STEPS) / STEPS
    }

    fun pOf(q: Float): Float = when {
        q <= 1f -> -1f
        q <= 3f -> (q - 1f) / 2f - 1f
        q <= 4f -> q - 3f
        else -> 1f
    }

    companion object {
        const val Q_BUDGET = 1f
        const val Q_NATURAL = 3f
        const val Q_TOP = 5f
        private const val STEPS = 400f
    }
}

interface TileRules {
    val minTile: Float
    val resizeSnap: Float
    val dropGapReach: Float
    val swapShare: Float
    val snapReach: Float

    val splitLow: Float
    val splitHigh: Float
    val rowGrow: Float

    /**
     * Side by side, a picture takes the width its row leaves after the other parts have what they need at natural size
     * (within its own cap and least width), instead of the width that makes the parts equally tall. Off keeps the
     * equal-height split.
     */
    val pictureFirstRows: Boolean get() = false

    /**
     * How far past its own height cap a picture may be drawn when its cell has room nobody else can use: the cap
     * keeps a picture from growing while other content shrinks, but room left over at the end is better spent on it
     * than left empty. 1 keeps the cap hard.
     */
    val pictureOverflow: Float get() = 1f

    /** Whether the automatic stack may pair a small block beside its neighbour (see [TileFlow]). Off leaves it a plain stack. */
    val pairsSmall: Boolean get() = false

    /**
     * When a stack ends above the bottom of its room and nothing can grow into what is left, that room is spread: half
     * of it widens the gaps (each by at most this share of the stack's room), the rest is an equal margin above and
     * below, so the content sits centred instead of at the top over a hole. 0 keeps the stack at the top.
     */
    val spreadGap: Float get() = 0f

    /**
     * A card that would leave more than [sparseMargin] of its room as spread margin lets titles, text and statistics
     * grow past their caps by this factor (together, so their size relation stays), as far as the width allows, before
     * the margin is spread. 1 keeps the caps.
     */
    val sparseBoost: Float get() = 1f
    val sparseMargin: Float get() = 0.25f
    val zoom: TileZoom

    /**
     * A statistic whose layout would cover less than this share of its cell, grown as far as [fillMax] lets it, takes
     * the layout that covers the most when that is clearly more (see [TileFitter.statistic]): beside a tall picture a
     * statistic reflows into fewer columns and grows to the picture's height instead of sitting in a hole. 0 keeps
     * the layout the reflow rule picked.
     */
    val statFillShare: Float get() = 0f

    val textMinScale: Float
    val statMinScale: Float
    val fillMax: Float
    val fillTitle: Float
    val hugShare: Float
    val hugPx: Float
    val hugSlack: Float
    val titleKeep: Float
    val reflowKeep: Float

    val drawableLeastTiles: Float
    val drawableLeastShare: Float

    val cutCost: Double
    val hiddenCost: Double
    val raggedCost: Double
    val edgesCost: Double
    val leftOverCost: Double

    fun minScale(kind: TileKind): Float
    fun priority(kind: TileKind): Float
    fun appetite(kind: TileKind): Float

    fun tileCost(item: AlignItem, space: AlignSpace, cw: Float, ch: Float, fit: TileFit, top: Boolean, air: Boolean = top): Double

    /**
     * The least [tileCost] can ever be for a tile of [kind], whatever its cell (0 unless the kind earns a reward, as
     * pictures do for the area they cover). [groupCost] is never negative. The search uses this to drop a split as soon
     * as the parts it has priced, plus the least the rest could cost, cannot beat the best split it already has.
     */
    fun tileCostFloor(kind: TileKind): Double
    fun groupCost(stacked: Boolean, sizes: FloatArray, used: FloatArray): Double
}
