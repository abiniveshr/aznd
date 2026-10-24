package com.ashrdev.aznd.ui.common.tiling

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

interface TileCell {
    val id: Int
    val cellLeft: Float
    val cellTop: Float
    val cellWidth: Float
    val cellHeight: Float

    fun cellContains(x: Float, y: Float): Boolean =
        x >= cellLeft && x <= cellLeft + cellWidth && y >= cellTop && y <= cellTop + cellHeight
}

interface TileArea {
    val tree: TileNode?
    val cells: List<TileCell>
    val contentLeft: Float
    val contentTop: Float
    val contentWidth: Float
    val contentHeight: Float
    val gap: Float
}

fun dropSpot(area: TileArea, dragged: Int, x: Float, y: Float, rules: TileRules): DropSpot? {
    val root = area.tree ?: return null
    val cells = area.cells.associate { it.id to floatArrayOf(it.cellLeft, it.cellTop, it.cellWidth, it.cellHeight) }
    if (cells.size < 2 || dragged !in cells) return null
    val cl = area.contentLeft
    val ct = area.contentTop
    val cr = cl + area.contentWidth
    val cb = ct + area.contentHeight
    val used = cells.values.maxOf { it[1] + it[3] }

    fun regionOf(ids: Collection<Int>): FloatArray {
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (id in ids) {
            val c = cells[id] ?: continue
            l = min(l, c[0]); t = min(t, c[1]); r = max(r, c[0] + c[2]); b = max(b, c[1] + c[3])
        }
        return floatArrayOf(l, t, r - l, b - t)
    }
    fun regionOf(n: TileNode): FloatArray = regionOf(TileTree.leaves(n))

    fun spot(target: Int, zone: DropZone, span: Set<Int>, kind: DropKind, bar: FloatArray): DropSpot? {
        val tree = TileTree.moveBeside(root, dragged, span, zone) ?: return null
        if (TileTree.sameShape(tree, root)) return null
        return DropSpot(target, zone, span, regionOf(span - dragged), tree, kind, bar)
    }

    if (x < cl || x > cr || y < ct || y > max(cb, used)) {
        val zone = listOf(
            DropZone.LEFT to cl - x, DropZone.RIGHT to x - cr, DropZone.TOP to ct - y, DropZone.BOTTOM to y - max(cb, used)
        ).maxByOrNull { it.second }!!.first
        val all = TileTree.leaves(root).toSet()
        val whole = regionOf(all)
        val strip = 8f
        val bar = when (zone) {
            DropZone.LEFT -> floatArrayOf(whole[0] - strip * 2f, whole[1], strip, whole[3])
            DropZone.RIGHT -> floatArrayOf(whole[0] + whole[2] + strip, whole[1], strip, whole[3])
            DropZone.TOP -> floatArrayOf(whole[0], whole[1] - strip * 2f, whole[2], strip)
            else -> floatArrayOf(whole[0], whole[1] + whole[3] + strip, whole[2], strip)
        }
        return spot(all.first { it != dragged }, zone, all, DropKind.EDGE, bar)
    }

    val reach = area.gap / 2f + rules.dropGapReach
    var bestGap: DropSpot? = null
    var bestDist = Float.MAX_VALUE
    var homeGap = false
    fun consider(dist: Float, make: () -> DropSpot?, home: Boolean) {
        if (dist >= bestDist) return
        bestDist = dist
        homeGap = home
        bestGap = if (home) null else make()
    }
    fun walk(n: TileNode) {
        if (n !is TileGroup) return
        val rs = n.children.map { regionOf(it) }
        val whole = regionOf(n)
        for (k in 0 until n.children.size - 1) {
            val a = n.children[k]
            val b = n.children[k + 1]
            val line: Float
            val dist: Float
            val inside: Boolean
            val bar: FloatArray
            if (n.vertical) {
                line = (rs[k][1] + rs[k][3] + rs[k + 1][1]) / 2f
                dist = abs(y - line)
                inside = x >= whole[0] && x <= whole[0] + whole[2]
                bar = floatArrayOf(whole[0], line - area.gap / 2f, whole[2], area.gap)
            } else {
                line = (rs[k][0] + rs[k][2] + rs[k + 1][0]) / 2f
                dist = abs(x - line)
                inside = y >= whole[1] && y <= whole[1] + whole[3]
                bar = floatArrayOf(line - area.gap / 2f, whole[1], area.gap, whole[3])
            }
            if (!inside || dist > reach) continue
            val home = (a is TileLeaf && a.id == dragged) || (b is TileLeaf && b.id == dragged)
            val afterA = TileTree.leaves(a).toSet()
            val beforeB = TileTree.leaves(b).toSet()
            consider(dist, {
                val zoneAfter = if (n.vertical) DropZone.BOTTOM else DropZone.RIGHT
                val zoneBefore = if (n.vertical) DropZone.TOP else DropZone.LEFT
                if (afterA - dragged != afterA || afterA == setOf(dragged)) {
                    spot(TileTree.leaves(b).first(), zoneBefore, beforeB, DropKind.GAP, bar)
                } else {
                    spot(TileTree.leaves(a).last(), zoneAfter, afterA, DropKind.GAP, bar)
                }
            }, home)
        }
        n.children.forEach { walk(it) }
    }
    walk(root)
    val top = TileTree.leaves(root).toSet()
    val whole = regionOf(top)
    if (root is TileGroup && root.vertical) {
        val first = root.children.first()
        val last = root.children.last()
        if (y - whole[1] in -reach..reach) {
            consider(abs(y - whole[1]) + 0.01f, {
                spot(TileTree.leaves(first).first(), DropZone.TOP, TileTree.leaves(first).toSet(), DropKind.GAP, floatArrayOf(whole[0], whole[1] - area.gap, whole[2], area.gap))
            }, first is TileLeaf && first.id == dragged)
        }
        val bottom = whole[1] + whole[3]
        if (y >= bottom - reach) {
            consider(abs(y - bottom) + 0.01f, {
                spot(TileTree.leaves(last).last(), DropZone.BOTTOM, TileTree.leaves(last).toSet(), DropKind.GAP, floatArrayOf(whole[0], bottom, whole[2], area.gap))
            }, last is TileLeaf && last.id == dragged)
        }
    }
    if (bestDist < Float.MAX_VALUE) return if (homeGap) null else bestGap

    val under = area.cells.firstOrNull { it.cellContains(x, y) } ?: return null
    if (under.id == dragged) return null
    val t = under
    val dx = x - (t.cellLeft + t.cellWidth / 2f)
    val dy = y - (t.cellTop + t.cellHeight / 2f)
    val cell = floatArrayOf(t.cellLeft, t.cellTop, t.cellWidth, t.cellHeight)
    if (abs(dx) < t.cellWidth * rules.swapShare && abs(dy) < t.cellHeight * rules.swapShare) {
        val tree = TileTree.moveTo(root, dragged, t.id, DropZone.SWAP) ?: return null
        return DropSpot(t.id, DropZone.SWAP, setOf(t.id), cell, tree, DropKind.SWAP, cell)
    }
    val horizontal = abs(dx) / t.cellWidth > abs(dy) / t.cellHeight
    val zone = if (horizontal) {
        if (dx < 0f) DropZone.LEFT else DropZone.RIGHT
    } else {
        if (dy < 0f) DropZone.TOP else DropZone.BOTTOM
    }
    val half = when (zone) {
        DropZone.LEFT -> floatArrayOf(cell[0], cell[1], cell[2] / 2f, cell[3])
        DropZone.RIGHT -> floatArrayOf(cell[0] + cell[2] / 2f, cell[1], cell[2] / 2f, cell[3])
        DropZone.TOP -> floatArrayOf(cell[0], cell[1], cell[2], cell[3] / 2f)
        else -> floatArrayOf(cell[0], cell[1] + cell[3] / 2f, cell[2], cell[3] / 2f)
    }
    return spot(t.id, zone, setOf(t.id), DropKind.SIDE, half)
}

class TileSnapped(val x: Float, val y: Float, val w: Float, val h: Float, val guidesX: List<Float>, val guidesY: List<Float>)

object TileSnap {

    private fun lines(area: TileArea, id: Int, vertical: Boolean): List<Float> {
        val out = ArrayList<Float>()
        if (vertical) {
            out += area.contentTop; out += area.contentTop + area.contentHeight / 2f; out += area.contentTop + area.contentHeight
        } else {
            out += area.contentLeft; out += area.contentLeft + area.contentWidth / 2f; out += area.contentLeft + area.contentWidth
        }
        for (b in area.cells) {
            if (b.id == id) continue
            if (vertical) { out += b.cellTop; out += b.cellTop + b.cellHeight / 2f; out += b.cellTop + b.cellHeight }
            else { out += b.cellLeft; out += b.cellLeft + b.cellWidth / 2f; out += b.cellLeft + b.cellWidth }
        }
        return out
    }

    /** The nearest line to any of [edges] (the box's own), within reach: how far to shift, and the line. */
    private fun nearest(edges: List<Float>, lines: List<Float>, reach: Float): Pair<Float, Float>? {
        var best: Pair<Float, Float>? = null
        for (e in edges) for (l in lines) {
            val d = l - e
            if (abs(d) <= reach && (best == null || abs(d) < abs(best.first))) best = d to l
        }
        return best
    }

    /** Box [id] moved to [x], [y] (its size [w] by [h]): where it snaps. */
    fun move(area: TileArea, id: Int, x: Float, y: Float, w: Float, h: Float, rules: TileRules): TileSnapped {
        val sx = nearest(listOf(x, x + w / 2f, x + w), lines(area, id, false), rules.snapReach)
        val sy = nearest(listOf(y, y + h / 2f, y + h), lines(area, id, true), rules.snapReach)
        return TileSnapped(x + (sx?.first ?: 0f), y + (sy?.first ?: 0f), w, h, listOfNotNull(sx?.second), listOfNotNull(sy?.second))
    }

    /** Box [id] at [x], [y] resized from its bottom right corner to [w] by [h]: where its right and bottom edges snap. */
    fun resize(area: TileArea, id: Int, x: Float, y: Float, w: Float, h: Float, least: Float, rules: TileRules): TileSnapped {
        val sx = nearest(listOf(x + w), lines(area, id, false), rules.snapReach)
        val sy = nearest(listOf(y + h), lines(area, id, true), rules.snapReach)
        val nw = (w + (sx?.first ?: 0f)).coerceAtLeast(least)
        val nh = (h + (sy?.first ?: 0f)).coerceAtLeast(least)
        return TileSnapped(x, y, nw, nh, listOfNotNull(sx?.second), listOfNotNull(sy?.second))
    }
}
