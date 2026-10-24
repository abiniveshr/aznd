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
    val zoom: TileZoom

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
    fun groupCost(stacked: Boolean, sizes: FloatArray, used: FloatArray): Double
}
