package com.ashrdev.aznd.ui.dashboard

import com.ashrdev.aznd.ui.common.tiling.AlignCandidate
import com.ashrdev.aznd.ui.common.tiling.AlignItem
import com.ashrdev.aznd.ui.common.tiling.AlignShared
import com.ashrdev.aznd.ui.common.tiling.AlignSpace
import com.ashrdev.aznd.ui.common.tiling.DrawableShape
import com.ashrdev.aznd.ui.common.tiling.StatShape
import com.ashrdev.aznd.ui.common.tiling.TileAligner
import com.ashrdev.aznd.ui.common.tiling.TileArea
import com.ashrdev.aznd.ui.common.tiling.TileCell
import com.ashrdev.aznd.ui.common.tiling.TileFitter
import com.ashrdev.aznd.ui.common.tiling.TileGroup
import com.ashrdev.aznd.ui.common.tiling.TileItem
import com.ashrdev.aznd.ui.common.tiling.TileKind
import com.ashrdev.aznd.ui.common.tiling.TileLeaf
import com.ashrdev.aznd.ui.common.tiling.TileNode
import com.ashrdev.aznd.ui.common.tiling.TileScale
import com.ashrdev.aznd.ui.common.tiling.TileScaleResult
import com.ashrdev.aznd.ui.common.tiling.TileSolver
import com.ashrdev.aznd.ui.common.tiling.TileTree
import com.ashrdev.aznd.ui.common.tiling.TileZoom
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

sealed class DashShape {
    class Stat(val shape: StatShape) : DashShape()
    class Picture(val shape: DrawableShape) : DashShape()
}

class DashCell(
    override val id: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val scale: Float,
    val columns: Int,
    val cut: Boolean
) : TileCell {
    override val cellLeft: Float get() = x
    override val cellTop: Float get() = y
    override val cellWidth: Float get() = w
    override val cellHeight: Float get() = h
}

// A TileArea so the edit mode can aim drops with the same dropSpot as Flex.
class DashArranged(
    val width: Float,
    val height: Float,
    override val cells: List<DashCell>,
    override val tree: TileNode?,
    override val gap: Float = DashMetrics.GAP,
    /** Room beside tiles that were shrunk to their content and centred in their slot (left, top, width, height): margin, not a hole. */
    val spread: List<FloatArray> = emptyList()
) : TileArea {
    val cuts: Int get() = cells.count { it.cut }
    fun cell(id: Int): DashCell? = cells.firstOrNull { it.id == id }
    fun hit(x: Float, y: Float): DashCell? = cells.firstOrNull { it.cellContains(x, y) }

    override val contentLeft: Float get() = 0f
    override val contentTop: Float get() = 0f
    override val contentWidth: Float get() = width
    override val contentHeight: Float get() = height
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
    const val HARMONY = 2.5f
    private const val PLAIN_SLACK = 1.02f
    private const val STICK_TALL = 1.03f
    internal var stickAbs = 0.5
    internal var stickRel = 0.0
    private const val REFINE_STEPS = 1
    /** Each refinement is a whole search, which costs most on many tiles and gains least there. */
    private const val REFINE_TILES = 8
    private const val REFINE_MIN = 1.05f
    private val COMPACT = floatArrayOf(1f, 0.86f, 0.74f, 0.62f)
    const val STAT_TALL = 1.15f
    /** A tile whose content is narrower than this share of its cell is shrunk to the content and centred. */
    const val HUG = 0.72f
    /** A page shorter than this share of the viewport lets its content grow by [SPARSE_BOOST] first (up to the viewport). */
    const val SPARSE_PAGE = 0.9f
    const val SPARSE_BOOST = 2.5f

    /** How many growth factors [grownIfSparse] tries between 1 and [SPARSE_BOOST]. */
    const val GROW_TRIES = 5
    const val PICTURE_TALL = 1.15f

    private val rules = DashboardTileRules
    private val fitter = TileFitter(rules)

    private const val CACHE = 8
    private val cache = object : LinkedHashMap<String, DashArranged>(CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DashArranged>?): Boolean = size > CACHE
    }

    fun cached(key: String): DashArranged? = synchronized(cache) { cache[key] }

    private const val LAST = 8
    /** The tiling last arranged automatically for a page signature (tile ids in order, width, gap): what the next arrange of those tiles prefers. */
    private val last = object : LinkedHashMap<String, TileNode>(LAST, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TileNode>?): Boolean = size > LAST
    }

    /**
     * [layout]: a tiling arranged by hand in edit mode; null lets the aligner pick the best one, preferring the tiling
     * last shown for the same tiles (see [arrange]) so a small change in a widget's text does not reshuffle the page.
     *
     * The first result stored under a key is the one every caller gets while it is cached, so the edit preview and the
     * page agree; a caller that lost a race returns the stored one. After the entry is evicted the same key is arranged
     * again, now preferring whatever was shown last for those tiles, which can differ from what that key gave before.
     */
    fun arrangeCached(
        key: String,
        tiles: List<Pair<Int, DashShape>>,
        width: Float,
        viewport: Float,
        gap: Float,
        layout: TileNode? = null
    ): DashArranged {
        cached(key)?.let { return it }
        val signature = tiles.joinToString(",") { it.first.toString() } + "@$width@$gap"
        val a = if (layout != null) {
            arrangeTree(layout, tiles, width, viewport, gap)
        } else {
            arrange(tiles, width, viewport, gap, prefer = synchronized(last) { last[signature] })
        }
        synchronized(cache) {
            cache[key]?.let { return it }
            cache[key] = a
        }
        if (layout == null) a.tree?.let { tree -> synchronized(last) { last[signature] = tree } }
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

    /**
     * The best page for [tiles]. [prefer] is the tiling shown before: when it holds exactly these tiles it is placed
     * at the heights the search tries, and kept over the freshly found best when nothing is cut in it, its page is
     * at most [STICK_TALL] taller and its [judge] score is within [stickMargin] of the best's. A small change in a
     * widget's text then leaves the page as it was instead of reshuffling it. With no [prefer] this is the plain search.
     */
    fun arrange(
        tiles: List<Pair<Int, DashShape>>,
        width: Float,
        viewport: Float,
        gap: Float,
        effort: Int = TileAligner.FULL_EFFORT,
        prefer: TileNode? = null
    ): DashArranged = grownIfSparse(arrangeBase(tiles, width, viewport, gap, effort, prefer), tiles, width, viewport, gap)

    /**
     * A page far shorter than the screen has room its widgets can use: the same tiling placed again with statistics
     * allowed to grow up to [SPARSE_BOOST] times as far as usual, as long as the page stays within the viewport and
     * nothing is cut. [TileScale] tries a few such growth factors and keeps the one that leaves the least of the page
     * empty (the factor 1 stays unless another gains [TileScale.MARGIN]). This is the page's content scale: the
     * person's own module size is already in the widgets' shapes and is not touched.
     */
    private fun grownIfSparse(a: DashArranged, tiles: List<Pair<Int, DashShape>>, width: Float, viewport: Float, gap: Float): DashArranged {
        val tree = a.tree ?: return a
        if (a.cuts > 0 || a.height >= viewport * SPARSE_PAGE || a.cells.isEmpty()) return a
        val shown = tiles.take(TileAligner.MAX_BLOCKS)
        val items = shown.associate { it.first to tileItem(it.first, it.second) }
        val shapes = shown.toMap()
        val placed = HashMap<Float, DashArranged>()
        val pick = TileScale.choose(TileScale.candidates(1f, 1f, SPARSE_BOOST, GROW_TRIES), 1f) { boost ->
            val arranged = if (boost == 1f) a else place(tree, items, shapes, width, viewport, gap, boost)
            placed[boost] = arranged
            TileScaleResult(1.0 - contentShare(arranged, shapes), arranged.cuts + if (arranged.height > viewport + 0.5f) 1 else 0)
        }
        return pick?.let { placed[it.scale] } ?: a
    }

    private fun contentOf(c: DashCell, shape: DashShape?): FloatArray = when (shape) {
        is DashShape.Stat -> {
            val k = shape.shape.columns.indexOf(c.columns).coerceAtLeast(0)
            floatArrayOf(min(c.w, shape.shape.widths[k] * c.scale), min(c.h, shape.shape.heights[k] * c.scale))
        }
        is DashShape.Picture -> fitter.drawable(shape.shape, c.w, c.h).let { floatArrayOf(min(c.w, it.w), min(c.h, it.h)) }
        null -> floatArrayOf(c.w, c.h)
    }

    /** The share of the page (width by its height) that is content. */
    fun contentShare(a: DashArranged, shapes: Map<Int, DashShape>): Double =
        a.cells.sumOf { c -> contentOf(c, shapes[c.id]).let { it[0].toDouble() * it[1] } } / (a.width.toDouble() * max(a.height, 1f))

    private fun arrangeBase(
        tiles: List<Pair<Int, DashShape>>,
        width: Float,
        viewport: Float,
        gap: Float,
        effort: Int,
        prefer: TileNode?
    ): DashArranged {
        val shown = tiles.take(TileAligner.MAX_BLOCKS)
        if (shown.isEmpty() || width <= 0f || viewport <= 0f) return DashArranged(width, 0f, emptyList(), null)
        val align = shown.map { alignItem(it.first, it.second, width) }
        val items = shown.associate { it.first to tileItem(it.first, it.second) }
        val shapes = shown.toMap()
        val byId = align.associateBy { it.id }
        val visited = ArrayList<Float>()
        val fresh = search(shown, align, items, shapes, byId, width, viewport, gap, effort, visited)
        val ids = shown.map { it.first }
        if (prefer == null || fresh.tree == null || prefer === fresh.tree || !holdsExactly(prefer, ids)) return fresh
        if (TileTree.sameShape(prefer, fresh.tree)) return fresh
        var kept: DashArranged? = null
        for (h in visited.distinct()) {
            val a = place(prefer, items, shapes, width, h, gap)
            if (a.cuts == 0 && (kept == null || a.height < kept.height)) kept = a
        }
        if (kept == null || fresh.cuts > 0 || kept.height > fresh.height * STICK_TALL) return fresh
        val freshScore = judge(fresh, byId, viewport, gap)
        val keptScore = judge(kept, byId, viewport, gap)
        return if (keptScore <= freshScore + max(stickAbs, stickRel * freshScore)) kept else fresh
    }

    private fun holdsExactly(tree: TileNode, ids: List<Int>): Boolean {
        val leaves = TileTree.leaves(tree)
        return leaves.size == ids.size && leaves.toSet() == ids.toSet()
    }

    private fun search(
        shown: List<Pair<Int, DashShape>>,
        align: List<AlignItem>,
        items: Map<Int, TileItem>,
        shapes: Map<Int, DashShape>,
        byId: Map<Int, AlignItem>,
        width: Float,
        viewport: Float,
        gap: Float,
        effort: Int,
        visited: MutableList<Float>
    ): DashArranged {
        val stacked = TileAligner.stackedHeight(align, AlignSpace(width, viewport, gap), rules)
        val shared = AlignShared()
        // The grow loop starts at the viewport, which is the compact pass's first problem: what was found is kept by height.
        val solved = HashMap<Float, List<AlignCandidate>>()
        fun solveAt(h: Float): List<AlignCandidate> =
            solved.getOrPut(h) { TileAligner(align, AlignSpace(width, h, gap), rules, effort, shared).solve() }
        if (stacked <= viewport * 1.02f) {
            var best: DashArranged? = null
            var bestScore = Double.POSITIVE_INFINITY
            val seen = HashSet<TileNode>()
            for (k in COMPACT) {
                visited += viewport
                for (found in solveAt(viewport * k)) {
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
        var grown: DashArranged? = null
        var failed = 0f
        var worked = 0f
        for (h in heights(viewport, stacked).distinct()) {
            visited += h
            val arranged = solveAt(h).minByOrNull { it.cost }?.let { place(it.tree, items, shapes, width, h, gap) }
            if (arranged != null && arranged.cuts == 0) {
                grown = arranged
                worked = h
                break
            }
            failed = h
            if (arranged != null && (fallback == null || arranged.cuts < fallback.cuts)) fallback = arranged
        }
        // The steps are 30 % apart, so the first height that works can leave the page up to 30 % taller than it
        // needs to be: look between it and the last one that failed.
        var tried = 0
        while (grown != null && failed > 0f && tried < REFINE_STEPS && shown.size <= REFINE_TILES && worked > failed * REFINE_MIN) {
            tried++
            val mid = sqrt(failed * worked)
            visited += mid
            val arranged = solveAt(mid).minByOrNull { it.cost }?.let { place(it.tree, items, shapes, width, mid, gap) }
            if (arranged != null && arranged.cuts == 0) {
                worked = mid
                if (arranged.height < grown.height) grown = arranged
            } else {
                failed = mid
            }
        }
        val best = grown ?: fallback
        return notWorseThanStacking(best, shown, width, viewport, gap)
    }

    /**
     * The plain page, every tile under the next, is the floor for the automatic one: when [best] is cut, or its page
     * is more than 2 % taller than the plain page, the plain page is worked out too and wins with fewer cut tiles,
     * or the same number and a shorter page. A search that finds nothing at all gets the plain page.
     */
    private fun notWorseThanStacking(
        best: DashArranged?,
        shown: List<Pair<Int, DashShape>>,
        width: Float,
        viewport: Float,
        gap: Float
    ): DashArranged {
        val plain = arrangeTree(stackedTree(shown.map { it.first }), shown, width, viewport, gap)
        if (best == null) return plain
        if (best.cuts == 0 && best.height <= plain.height * PLAIN_SLACK) return best
        return if (plain.cuts < best.cuts || (plain.cuts == best.cuts && plain.height < best.height)) plain else best
    }

    // A hand-made tiling keeps its shape: only the tiles' sizes are solved, growing the page (it
    // scrolls) until nothing is cut off, as the automatic layout does.
    fun arrangeTree(
        layout: TileNode,
        tiles: List<Pair<Int, DashShape>>,
        width: Float,
        viewport: Float,
        gap: Float
    ): DashArranged {
        val shown = tiles.take(TileAligner.MAX_BLOCKS)
        if (shown.isEmpty() || width <= 0f || viewport <= 0f) return DashArranged(width, 0f, emptyList(), null)
        val tree = TileTree.effective(layout, shown.map { it.first }, false)
            ?: return DashArranged(width, 0f, emptyList(), null)
        val align = shown.map { alignItem(it.first, it.second, width) }
        val items = shown.associate { it.first to tileItem(it.first, it.second) }
        val shapes = shown.toMap()
        val stacked = TileAligner.stackedHeight(align, AlignSpace(width, viewport, gap), rules)
        var fallback: DashArranged? = null
        for (h in heights(viewport, stacked)) {
            val arranged = place(tree, items, shapes, width, h, gap)
            if (arranged.cuts == 0) return arranged
            if (fallback == null || arranged.cuts < fallback.cuts) fallback = arranged
        }
        return fallback ?: place(tree, items, shapes, width, viewport, gap)
    }

    fun judge(a: DashArranged, items: Map<Int, AlignItem>, viewport: Float, gap: Float): Double {
        val space = AlignSpace(a.width, viewport, gap)
        var score = 0.0
        for (c in a.cells) {
            val item = items[c.id] ?: continue
            val fit = fitter.of(item, c.w, c.h, false, c.w < a.width - 1f)
            score += rules.tileCost(item, space, c.w, c.h, fit, c.y < 1f, false)
        }
        val covered = a.cells.sumOf { it.w.toDouble() * it.h } / (a.width.toDouble() * viewport).coerceAtLeast(1.0)
        score += rules.leftOverCost * (1.0 - covered).coerceAtLeast(0.0)
        run {
            var content = 0.0
            for (c in a.cells) {
                val item = items[c.id] ?: continue
                content += item.stat?.let { st ->
                    val k = st.columns.indexOf(c.columns).coerceAtLeast(0)
                    min(c.w, st.widths[k] * c.scale).toDouble() * min(c.h, st.heights[k] * c.scale)
                } ?: item.drawable?.let { d -> fitter.drawable(d, c.w, c.h).let { min(c.w, it.w).toDouble() * min(c.h, it.h) } } ?: (c.w.toDouble() * c.h)
            }
            score += DEAD_JUDGE * (1.0 - content / (a.width.toDouble() * max(a.height, 1f)))
        }
        return score
    }

    /** What each share of dead room (inside cells and outside them) adds to a page's [judge] score. */
    private const val DEAD_JUDGE = 12.0

    private fun stackedTree(ids: List<Int>): TileNode =
        if (ids.size == 1) TileLeaf(ids[0]) else TileGroup(true, ids.map { TileLeaf(it) }, List(ids.size) { 0f })

    fun place(tree: TileNode, items: Map<Int, TileItem>, shapes: Map<Int, DashShape>, width: Float, height: Float, gap: Float, boost: Float = 1f): DashArranged {
        val solved = TileSolver(items, gap, rules).solve(tree, 0f, 0f, width, height)
        class Raw(val id: Int, val r: FloatArray, val scale: Float, val columns: Int, val cut: Boolean, val stat: StatShape?)
        val raw = ArrayList<Raw>()
        for ((id, r) in solved.rects) {
            val shape = shapes[id] ?: continue
            val level = solved.levels[id] ?: TileZoom.Q_NATURAL
            when (shape) {
                is DashShape.Stat -> {
                    val cap = rules.fillMax * boost
                    val f = fitter.statistic(shape.shape, r[2], r[3], true, maxScale = cap, beside = r[2] < width - 1f)
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
        // A tile whose content is much smaller than its cell shrinks to the content and is centred in its slot (a lone
        // tile also loses the height it does not use); the room it frees is margin. Tiles in a full row are left alone.
        val inRow = HashSet<Int>()
        fun rows(n: TileNode, inside: Boolean) {
            if (n is TileLeaf) { if (inside) inRow += n.id; return }
            val g = n as TileGroup
            g.children.forEach { rows(it, inside || (!g.vertical && g.children.size > 1)) }
        }
        rows(tree, false)
        val slots = HashMap<Int, FloatArray>()
        for ((id, b) in boxes) {
            val c = cells[id] ?: continue
            if (c.cut) continue
            val box = DashCell(id, b[0], b[1], b[2], b[3], c.scale, c.columns, false)
            val content = contentOf(box, shapes[id])
            if (content[0] < HUG * b[2]) {
                val dx = (b[2] - content[0]) / 2f
                slots[id] = floatArrayOf(dx)
                b[0] += dx
                b[2] = content[0]
                if (id !in inRow) b[3] = min(b[3], max(content[1], rules.minTile))
            }
        }
        pack(tree, boxes, gap)
        val spread = ArrayList<FloatArray>()
        val out = solved.rects.keys.mapNotNull { id ->
            val b = boxes[id] ?: return@mapNotNull null
            val c = cells[id] ?: return@mapNotNull null
            slots[id]?.let { s ->
                spread.add(floatArrayOf(b[0] - s[0], b[1], s[0], b[3]))
                spread.add(floatArrayOf(b[0] + b[2], b[1], s[0], b[3]))
            }
            DashCell(id, b[0], b[1], b[2], b[3], c.scale, c.columns, c.cut)
        }
        val used = out.maxOfOrNull { it.y + it.h } ?: 0f
        return DashArranged(width, max(used, 0f), out, tree, gap, spread)
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
