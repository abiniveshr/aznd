package com.ashrdev.aznd.ui.dashboard

import com.ashrdev.aznd.ui.common.tiling.AlignItem
import com.ashrdev.aznd.ui.common.tiling.AlignSpace
import com.ashrdev.aznd.ui.common.tiling.DrawableShape
import com.ashrdev.aznd.ui.common.tiling.StatShape
import com.ashrdev.aznd.ui.common.tiling.TileAligner
import com.ashrdev.aznd.ui.common.tiling.TileFitter
import com.ashrdev.aznd.ui.common.tiling.TileGroup
import com.ashrdev.aznd.ui.common.tiling.TileItem
import com.ashrdev.aznd.ui.common.tiling.TileKind
import com.ashrdev.aznd.ui.common.tiling.TileLeaf
import com.ashrdev.aznd.ui.common.tiling.TileNode
import com.ashrdev.aznd.ui.common.tiling.TileSolver
import com.ashrdev.aznd.ui.common.tiling.TileTree
import com.ashrdev.aznd.ui.common.tiling.TileZoom
import kotlin.math.max
import kotlin.math.min

sealed class DashShape {
    class Stat(val shape: StatShape) : DashShape()
    class Picture(val shape: DrawableShape) : DashShape()
}

class DashCell(
    val id: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val scale: Float,
    val columns: Int,
    val cut: Boolean
)

class DashArranged(val width: Float, val height: Float, val cells: List<DashCell>, val tree: TileNode?) {
    val cuts: Int get() = cells.count { it.cut }
    fun cell(id: Int): DashCell? = cells.firstOrNull { it.id == id }
}

class DashStatBuilder(private val reflowKeep: Float) {
    private val columns = ArrayList<Int>()
    private val widths = ArrayList<Float>()
    private val heights = ArrayList<Float>()

    fun layout(cols: Int, w: Float, h: Float): DashStatBuilder {
        columns += cols
        widths += w
        heights += h
        return this
    }

    fun build(designed: Int = columns.firstOrNull() ?: 1): StatShape {
        val order = widths.indices.sortedByDescending { widths[it] }
        val keptCols = ArrayList<Int>()
        val keptW = ArrayList<Float>()
        val keptH = ArrayList<Float>()
        for (i in order) {
            if (keptW.isNotEmpty() && widths[i] >= keptW.last() - 0.5f) continue
            keptCols += columns[i]
            keptW += widths[i]
            keptH += heights[i]
        }
        return StatShape(keptCols.toIntArray(), keptW.toFloatArray(), keptH.toFloatArray(), designed, reflowKeep)
    }
}

object DashboardLayout {
    const val GROW_STEPS = 6
    const val HARMONY = 1.2f
    private val COMPACT = floatArrayOf(1f, 0.86f, 0.74f, 0.62f)
    const val STAT_TALL = 2f
    const val PICTURE_TALL = 1.15f

    private val rules = DashboardTileRules
    private val fitter = TileFitter(rules)

    private const val CACHE = 8
    private val cache = object : LinkedHashMap<String, DashArranged>(CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DashArranged>?): Boolean = size > CACHE
    }

    fun cached(key: String): DashArranged? = synchronized(cache) { cache[key] }

    fun arrangeCached(key: String, tiles: List<Pair<Int, DashShape>>, width: Float, viewport: Float, gap: Float): DashArranged {
        cached(key)?.let { return it }
        val a = arrange(tiles, width, viewport, gap)
        synchronized(cache) { cache[key] = a }
        return a
    }

    fun stat(): DashStatBuilder = DashStatBuilder(rules.reflowKeep)

    fun picture(
        hasContent: Boolean,
        ratio: Float,
        ratioMin: Float,
        ratioMax: Float,
        minW: Float,
        minH: Float,
        maxW: Float,
        maxH: Float
    ): DrawableShape = DrawableShape(hasContent, ratio, ratioMin, ratioMax, minW, minH, maxW, maxH, greedy = false, crops = false)

    private fun alignItem(id: Int, shape: DashShape, width: Float): AlignItem = when (shape) {
        is DashShape.Stat -> AlignItem(id, TileKind.STATISTIC, shape.shape.need, min(shape.shape.hug, width), stat = shape.shape)
        is DashShape.Picture -> AlignItem(id, TileKind.DRAWABLE, 0f, width, drawable = shape.shape)
    }

    private fun tileItem(id: Int, shape: DashShape): TileItem = when (shape) {
        is DashShape.Stat -> TileItem(
            id, TileKind.STATISTIC, shape.shape.need, rules.statMinScale, rules.zoom.max,
            stat = shape.shape, leastScale = rules.statMinScale
        )
        is DashShape.Picture -> {
            val p = shape.shape
            TileItem(
                id, TileKind.DRAWABLE, 0f, 1f, 1f,
                pic = p,
                least = { w -> max(p.minH, min(w, p.maxW) / p.ratioMax) },
                picArea = p.minW * p.minH
            )
        }
    }

    fun heights(viewport: Float, stacked: Float): List<Float> {
        val out = arrayListOf(viewport)
        if (stacked <= viewport * 1.02f) return out
        var h = viewport
        for (i in 0 until GROW_STEPS) {
            h *= 1.3f
            if (h >= stacked) break
            out += h
        }
        out += stacked
        out += stacked * 1.15f
        return out
    }

    fun arrange(
        tiles: List<Pair<Int, DashShape>>,
        width: Float,
        viewport: Float,
        gap: Float,
        effort: Int = TileAligner.FULL_EFFORT
    ): DashArranged {
        val shown = tiles.take(TileAligner.MAX_BLOCKS)
        if (shown.isEmpty() || width <= 0f || viewport <= 0f) return DashArranged(width, 0f, emptyList(), null)
        val align = shown.map { alignItem(it.first, it.second, width) }
        val items = shown.associate { it.first to tileItem(it.first, it.second) }
        val shapes = shown.toMap()
        val stacked = TileAligner.stackedHeight(align, AlignSpace(width, viewport, gap), rules)
        if (stacked <= viewport * 1.02f) {
            val byId = align.associateBy { it.id }
            var best: DashArranged? = null
            var bestScore = Double.POSITIVE_INFINITY
            val seen = HashSet<TileNode>()
            for (k in COMPACT) {
                for (found in TileAligner(align, AlignSpace(width, viewport * k, gap), rules, effort).solve()) {
                    if (!seen.add(found.tree)) continue
                    val arranged = place(found.tree, items, shapes, width, viewport, gap)
                    val score = judge(arranged, byId, viewport, gap)
                    if (score < bestScore) {
                        best = arranged
                        bestScore = score
                    }
                }
            }
            best?.let { if (it.cuts == 0) return it }
        }
        var fallback: DashArranged? = null
        for (h in heights(viewport, stacked)) {
            val found = TileAligner(align, AlignSpace(width, h, gap), rules, effort).solve().minByOrNull { it.cost } ?: continue
            val arranged = place(found.tree, items, shapes, width, h, gap)
            if (arranged.cuts == 0) return arranged
            if (fallback == null || arranged.cuts < fallback.cuts) fallback = arranged
        }
        return fallback ?: place(stackedTree(shown.map { it.first }), items, shapes, width, max(viewport, stacked), gap)
    }

    fun judge(a: DashArranged, items: Map<Int, AlignItem>, viewport: Float, gap: Float): Double {
        val space = AlignSpace(a.width, viewport, gap)
        var score = 0.0
        for (c in a.cells) {
            val item = items[c.id] ?: continue
            val fit = fitter.of(item, c.w, c.h, false)
            score += rules.tileCost(item, space, c.w, c.h, fit, c.y < 1f, false)
        }
        val covered = a.cells.sumOf { it.w.toDouble() * it.h } / (a.width.toDouble() * viewport).coerceAtLeast(1.0)
        score += rules.leftOverCost * (1.0 - covered).coerceAtLeast(0.0)
        return score
    }

    private fun stackedTree(ids: List<Int>): TileNode =
        if (ids.size == 1) TileLeaf(ids[0]) else TileGroup(true, ids.map { TileLeaf(it) }, List(ids.size) { 0f })

    fun place(tree: TileNode, items: Map<Int, TileItem>, shapes: Map<Int, DashShape>, width: Float, height: Float, gap: Float): DashArranged {
        val solved = TileSolver(items, gap, rules).solve(tree, 0f, 0f, width, height)
        class Raw(val id: Int, val r: FloatArray, val scale: Float, val columns: Int, val cut: Boolean, val stat: StatShape?)
        val raw = ArrayList<Raw>()
        for ((id, r) in solved.rects) {
            val shape = shapes[id] ?: continue
            val level = solved.levels[id] ?: TileZoom.Q_NATURAL
            when (shape) {
                is DashShape.Stat -> {
                    val cap = min(rules.fillMax, max(1f, rules.zoom.zOf(level) * rules.rowGrow))
                    val f = fitter.statistic(shape.shape, r[2], r[3], true, maxScale = cap)
                    raw += Raw(id, r, f.scale, f.columns, f.cut, shape.shape)
                }
                is DashShape.Picture -> {
                    val f = fitter.drawable(shape.shape, r[2], r[3])
                    raw += Raw(id, r, 1f, 0, f.cut, null)
                }
            }
        }
        val stats = raw.filter { it.stat != null && !it.cut }
        val common = stats.minOfOrNull { it.scale } ?: 1f
        val boxes = HashMap<Int, FloatArray>()
        val cells = HashMap<Int, DashCell>()
        for (it in raw) {
            val stat = it.stat
            val s = if (stat != null && !it.cut) min(it.scale, max(1f, common) * HARMONY).coerceAtLeast(common) else it.scale
            val content = when {
                it.cut -> it.r[3]
                stat != null -> stat.heights[stat.columns.indexOf(it.columns).coerceAtLeast(0)] * s * STAT_TALL
                else -> (shapes[it.id] as? DashShape.Picture)?.let { p -> fitter.drawable(p.shape, it.r[2], it.r[3]).h * PICTURE_TALL } ?: it.r[3]
            }
            boxes[it.id] = floatArrayOf(it.r[0], it.r[1], it.r[2], min(it.r[3], max(content, rules.minTile)))
            cells[it.id] = DashCell(it.id, 0f, 0f, 0f, 0f, s, it.columns, it.cut)
        }
        pack(tree, boxes, gap)
        val out = solved.rects.keys.mapNotNull { id ->
            val b = boxes[id] ?: return@mapNotNull null
            val c = cells[id] ?: return@mapNotNull null
            DashCell(id, b[0], b[1], b[2], b[3], c.scale, c.columns, c.cut)
        }
        val used = out.maxOfOrNull { it.y + it.h } ?: 0f
        return DashArranged(width, max(used, 0f), out, tree)
    }

    private fun shift(n: TileNode, boxes: Map<Int, FloatArray>, dy: Float) {
        for (id in TileTree.leaves(n)) boxes[id]?.let { it[1] += dy }
    }

    private fun pack(n: TileNode, boxes: Map<Int, FloatArray>, gap: Float): FloatArray = when (n) {
        is TileLeaf -> boxes[n.id]?.let { floatArrayOf(it[1], it[1] + it[3]) } ?: floatArrayOf(0f, 0f)
        is TileGroup -> {
            val spans = n.children.map { pack(it, boxes, gap) }
            if (n.vertical) {
                val top = spans.firstOrNull()?.get(0) ?: 0f
                var cursor = top
                n.children.forEachIndexed { i, child ->
                    val dy = cursor - spans[i][0]
                    if (dy != 0f) shift(child, boxes, dy)
                    cursor = spans[i][1] + dy + gap
                }
                floatArrayOf(top, cursor - gap)
            } else {
                floatArrayOf(spans.minOf { it[0] }, spans.maxOf { it[1] })
            }
        }
    }
}
