package com.ashrdev.aznd.ui.common.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.ashrdev.aznd.domain.workout.formatWeight
import com.ashrdev.aznd.ui.theme.BorderKind
import com.ashrdev.aznd.ui.theme.FillRender
import com.ashrdev.aznd.ui.common.tiling.AlignItem
import com.ashrdev.aznd.ui.common.tiling.AlignSpace
import com.ashrdev.aznd.ui.common.tiling.DrawableShape
import com.ashrdev.aznd.ui.common.tiling.DropZone
import com.ashrdev.aznd.ui.common.tiling.StatShape
import com.ashrdev.aznd.ui.common.tiling.TileAligner
import com.ashrdev.aznd.ui.common.tiling.TileArea
import com.ashrdev.aznd.ui.common.tiling.TileCell
import com.ashrdev.aznd.ui.common.tiling.TileDivider
import com.ashrdev.aznd.ui.common.tiling.TileFit
import com.ashrdev.aznd.ui.common.tiling.TileGroup
import com.ashrdev.aznd.ui.common.tiling.TileItem
import com.ashrdev.aznd.ui.common.tiling.TileKind
import com.ashrdev.aznd.ui.common.tiling.TileLeaf
import com.ashrdev.aznd.ui.common.tiling.TileNode
import com.ashrdev.aznd.ui.common.tiling.TileSolved
import com.ashrdev.aznd.ui.common.tiling.TileSolver
import com.ashrdev.aznd.ui.common.tiling.TileTree
import com.ashrdev.aznd.ui.common.tiling.TileZoom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.stream.Collectors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A block's tile on the card, in card pixels (see [FLEX_CARD_SHORT]): only as big as its content needs, so this is also
 * what can be tapped and grabbed. [scale] is how far its content had to shrink to fit (1 = natural size) and
 * [drawn] the height that content takes at that scale. The tile sits inside its cell, the space the tiling
 * gave it, which can be bigger.
 */
class FlexBlockBox(
    override val id: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val scale: Float = 1f,
    val drawn: Float = height,
    override val cellLeft: Float = left,
    override val cellTop: Float = top,
    override val cellWidth: Float = width,
    override val cellHeight: Float = height,
    /** A statistic: the number of columns its layout took (0 for the other classes). */
    val columns: Int = 0,
    /** A title at the top edge: the air above its content, part of [height] (and not of [drawn]). */
    val inset: Float = 0f,
    /** The tile was stretched to its cell's height (its panel fills the cell); its content takes [contentHeight]. */
    val stretched: Boolean = false
) : TileCell {
    /** How tall the tile's content is, its air included: [height] unless the tile was stretched. */
    val contentHeight: Float get() = if (stretched) drawn + inset else height

    fun contains(x: Float, y: Float) = x >= left && x <= left + width && y >= top && y <= top + height
}

/** A block drawn this far (card pixels) from where the layout put it: used to glide blocks to new places. */
class FlexPose(val dx: Float, val dy: Float)

/**
 * Transient state of the View page while blocks are being moved: how far each block currently is from its
 * place, and the block that is held (floating under the finger, drawn on top, lifted by [lift] 0..1) at
 * ([heldLeft], [heldTop]) in card pixels.
 */
class FlexPoses(
    val offsets: Map<Int, FlexPose> = emptyMap(),
    val heldId: Int? = null,
    val heldLeft: Float = 0f,
    val heldTop: Float = 0f,
    val lift: Float = 0f,
    /** The size the held block floats at (its tile where it was picked up); null: its tile in the plan drawn. */
    val heldBox: FlexBlockBox? = null,
    /**
     * Above 0: the held block is also drawn in its own place in the plan, this opaque (0..1): where it will land, when
     * the plan drawn is the card as the drop would leave it. 0: its place is left empty.
     */
    val landing: Float = 0f
)

/**
 * The result of laying a card out, without drawing it: the card's height (fixed by the size, or taken from
 * the content for Free size), the tiles, how far the content in them had to shrink, and what did not fit.
 * Compute it once per change ([FlexPainter.plan]) and hand it to [FlexPainter.draw] and to the UI.
 */
class FlexPlan(
    /** The card's size in card pixels: 1080 along its shorter side (see [FLEX_CARD_SHORT]). */
    val width: Float,
    val height: Float,
    val boxes: List<FlexBlockBox>,
    /** The tiling actually drawn: the saved layout with the blocks that are not on the card left out and new ones added. */
    override val tree: TileNode?,
    /** The borders between neighbouring tiles, for resizing. */
    val dividers: List<TileDivider>,
    /** How much room each child of each group has along the split, by the group's path in [tree]. */
    val groupSizes: Map<List<Int>, List<Float>>,
    /** The area the tiles fill, in card pixels, and the gap between tiles. */
    override val contentLeft: Float,
    override val contentTop: Float,
    override val contentWidth: Float,
    override val contentHeight: Float,
    override val gap: Float,
    /** The blocks that are on the card (enabled, have something to show, within the size's limit), in order. */
    val shownIds: List<Int>,
    /** Enabled blocks left out because this size only fits [limit] of them. */
    val hidden: List<Int>,
    /** Some content is still too big for its tile at the smallest scale, so it is cut off at the tile's edge. */
    val clipped: Boolean,
    /** The smallest scale any tile's content was shrunk to (1 = none shrunk). */
    val minScale: Float,
    val limit: Int,
    /** Free size only: a card taller than most apps handle well. */
    val tooTall: Boolean,
    /** How the blocks were placed. In [FlexLayoutMode.FREE] every box is where it was put, the later on top; [tree] is null. */
    val mode: FlexLayoutMode = FlexLayoutMode.SPLIT,
    val zoom: Float = 1f,
    val crowded: Boolean = false,
    val rules: List<TileDivider> = dividers
) : TileArea {
    override val cells: List<TileCell> get() = boxes

    /** The block under a point: its tile; in free mode its whole box, the topmost one. */
    fun hit(x: Float, y: Float): FlexBlockBox? =
        if (mode == FlexLayoutMode.FREE) boxes.lastOrNull { it.cellContains(x, y) } else boxes.firstOrNull { it.contains(x, y) }
    fun box(id: Int): FlexBlockBox? = boxes.firstOrNull { it.id == id }
    /** The borders the tile of block [id] touches. */
    fun dividersOf(id: Int): List<TileDivider> = dividers.filter { id in it.adjacent }

    /**
     * The resize bars of block [id]'s tile: for each side of its cell that is a border with other tiles, the border
     * ([TileDivider], what a drag moves) and the bar along that side, as long as the cell's side (not the whole
     * border, which can run past it beside other tiles). At most one per side: the border that lies on it.
     */
    fun edgesOf(id: Int): List<FlexEdge> {
        val b = box(id) ?: return emptyList()
        val out = ArrayList<FlexEdge>(4)
        for (side in FlexSide.entries) {
            // Where the border along that side runs: in the middle of the gap next to the cell.
            val at = when (side) {
                FlexSide.LEFT -> b.cellLeft - gap / 2f
                FlexSide.RIGHT -> b.cellLeft + b.cellWidth + gap / 2f
                FlexSide.TOP -> b.cellTop - gap / 2f
                FlexSide.BOTTOM -> b.cellTop + b.cellHeight + gap / 2f
            }
            val across = side == FlexSide.TOP || side == FlexSide.BOTTOM
            val d = dividersOf(id)
                // A border between stacked tiles is a line across: the top or bottom side.
                .filter { it.vertical == across }
                .minByOrNull { abs((if (across) it.cy else it.cx) - at) }
                ?.takeIf { abs((if (across) it.cy else it.cx) - at) <= gap / 2f + 2f }
                ?: continue
            out += if (across) FlexEdge(d, side, b.cellLeft, at, b.cellLeft + b.cellWidth, at)
            else FlexEdge(d, side, at, b.cellTop, at, b.cellTop + b.cellHeight)
        }
        return out
    }
}

/** A side of a tile's cell. */
enum class FlexSide { LEFT, TOP, RIGHT, BOTTOM }

/**
 * A resize bar on side [side] of a selected tile: dragging it moves [divider] (as [TileTree.resized] does), so the tile
 * and the tiles on the other side of it share the space differently. [x0], [y0] to [x1], [y1]: the bar (card pixels),
 * along the middle of the gap.
 */
class FlexEdge(val divider: TileDivider, val side: FlexSide, val x0: Float, val y0: Float, val x1: Float, val y1: Float) {
    /** How far point [x], [y] is from the bar (0 on it). */
    fun distance(x: Float, y: Float): Float {
        val dx = max(0f, max(min(x0, x1) - x, x - max(x0, x1)))
        val dy = max(0f, max(min(y0, y1) - y, y - max(y0, y1)))
        return sqrt(dx * dx + dy * dy)
    }
}

/**
 * What best fit came to: the card ([changed] false when it already was the best fit) and a few other good tilings of
 * the same blocks, best first ([alternatives], for "Another layout").
 */
class FlexAutoResult(val spec: FlexSpec, val changed: Boolean, val message: String, val alternatives: List<FlexSpec> = emptyList())

/**
 * The size limits of one block's box (card pixels), from what its content needs: never so small that the content
 * would have to shrink below [FlexPainter.LIMIT_SCALE] (text keeps its longest word, a statistic a readable layout,
 * a chart room for its labels), never bigger than the card's content area, and a picture within its shape range.
 * Free mode holds every box to them.
 */
class FlexBoxLimits(
    val minW: Float,
    val maxW: Float,
    val maxH: Float,
    /** Pictures: the box's width / height stays within these (0 and infinity for the others). */
    val ratioMin: Float,
    val ratioMax: Float,
    private val least: (Float) -> Float
) {
    /** The least height at the width [w]. */
    fun minH(w: Float): Float = least(w)

    /** [w] by [h] brought within the limits: the width first, then the height for that width. */
    fun clamp(w: Float, h: Float): Pair<Float, Float> {
        val cw = w.coerceIn(minW, max(minW, maxW))
        val lo = max(minH(cw), if (ratioMax.isFinite()) cw / ratioMax else 0f)
        val hi = max(lo, min(maxH, if (ratioMin > 0f) cw / ratioMin else Float.MAX_VALUE))
        return cw to h.coerceIn(lo, hi)
    }
}

/**
 * Draws a Flex card with plain android.graphics, in a design space 1080 along its shorter side that is scaled to
 * whatever width it is asked for. The editor preview (a Compose Canvas) and the exported PNG both
 * call [FlexPainter.draw], so what you see is what you share.
 *
 * Layout is a tiling (see ui/common/tiling, under [FlexTileRules]): every block gets a tile, and how it takes that tile
 * depends on its class ([FlexBlockClass], fitted by [FlexFit], the same code the auto aligner prices with): titles and text are drawn at
 * their natural size, or smaller when the tile is too small, wrapping on whole words; statistics are single lines in
 * a grid that reflows before it shrinks; drawables (photos, charts) take the shape that fills their cell. The tile
 * is placed in its cell by the block's own alignment. Every text-like block function takes its box (x, y, width),
 * draws itself and returns its height; while [Ctx.dry] is set nothing is drawn, so the same code also measures.
 * That is what lets [plan] fit the content, find what is hidden or clipped, and what lets panels wrap their content.
 */
object FlexPainter {

    /** A tile's content is never shrunk below this (text would turn tiny): past it blocks are left off the card and the editor warns. */
    const val MIN_TILE_SCALE = 0.6f

    private const val MIN_FREE_HEIGHT = 480f
    private const val NO_CAP = 1e9f
    private const val TOO_TALL = 4000f

    /** Pixels per dp the patterns are drawn at: the card is 1080 wide, about a phone screen's width in pixels. */
    private const val PATTERN_DENSITY = 2.7f

    private class Arranged(
        val tree: TileNode?,
        val boxes: List<FlexBlockBox>,
        val solved: TileSolved,
        val height: Float,
        val availH: Float,
        val cuts: Int,
        val minScale: Float
    ) {
        val clipped: Boolean get() = cuts > 0
    }

    private fun footerOf(st: FlexStyle): Float = if (st.watermark && st.watermarkText.isNotBlank()) FLEX_FOOTER else 0f

    fun plan(spec: FlexSpec, content: FlexContent, assets: FlexAssets): FlexPlan {
        if (spec.style.layoutMode == FlexLayoutMode.FREE) return planFree(spec, content, assets)
        val st = spec.style
        val c = Ctx(Canvas(), st, assets)
        c.dry = true
        val pad = st.padding.toFloat()
        val padV = st.paddingV.toFloat()
        val footer = footerOf(st)
        val availW = st.aspect.width - 2 * pad
        val gap = st.spacing.toFloat()
        val free = st.aspect == FlexAspect.FREE
        val m = Metrics(c, content, availW)

        val drawable = spec.blocks
            .filter { it.enabled && content.has(it.type) }
            .filter { m.heightOf(it, availW) > 0f }
        val admitted = admit(m, st, drawable, spec.layout)
        val limit = admitted.size
        val hidden = ArrayList(drawable.filter { it !in admitted }.map { it.id })
        var live = admitted

        fun arrange(shown: List<FlexBlock>): Arranged {
            val byId = shown.associateBy { it.id }
            val tree = TileTree.effective(spec.layout, shown.map { it.id }, st.aspect == FlexAspect.WIDE)
            val banner = TileTree.banner(tree)
            val tiler = m.tiler(shown, banner, byContent = free)
            val height: Float = if (free) {
                val wanted = if (tree != null) tiler.height(tree, availW, TileZoom.Q_NATURAL) else 0f
                (wanted + 2 * padV + footer).coerceAtLeast(MIN_FREE_HEIGHT)
            } else {
                st.aspect.height.toFloat()
            }
            val availH = height - 2 * padV - footer
            val solved = tiler.solve(tree, pad, padV, availW, availH)
            var cuts = 0
            var minScale = 1f
            val boxes = ArrayList<FlexBlockBox>()
            for ((id, r) in solved.rects) {
                val b = byId[id] ?: continue
                val cx = r[0]
                val cy = r[1]
                val cw = r[2]
                val ch = r[3]
                val level = solved.levels[id] ?: TileZoom.Q_NATURAL
                val air = if (b.type.blockClass == FlexBlockClass.TITLE && id == banner) st.titleInset else 0f
                val fit = m.fitAt(b, cw, ch, air, tiler.leaf(id, cw, level).scale)
                if (fit.cut) cuts++
                minScale = min(minScale, fit.scale)
                val tw = fit.w
                val th = if (stretches(st, b) && !fit.cut && ch > fit.h + 0.5f) ch else fit.h
                val picture = b.type.blockClass == FlexBlockClass.DRAWABLE
                val hAlign = if (picture && b.hAlign == FlexAlign.START) FlexAlign.CENTER else b.hAlign
                val vAlign = if (picture && b.vAlign == FlexVAlign.TOP) FlexVAlign.CENTER else b.vAlign
                val tx = cx + when (hAlign) {
                    FlexAlign.START -> 0f
                    FlexAlign.CENTER -> (cw - tw) / 2f
                    FlexAlign.END -> cw - tw
                }
                val ty = cy + when (vAlign) {
                    FlexVAlign.TOP -> 0f
                    FlexVAlign.CENTER -> (ch - th) / 2f
                    FlexVAlign.BOTTOM -> ch - th
                }
                boxes += FlexBlockBox(id, tx, ty, tw, th, fit.scale, fit.h - fit.inset, cx, cy, cw, ch, fit.columns, fit.inset, stretched = th > fit.h + 0.5f)
            }
            return Arranged(tree, boxes, solved, height, availH, cuts, minScale)
        }

        var arranged = arrange(live)
        while (arranged.clipped && !free && live.size > 1) {
            val last = TileTree.leaves(arranged.tree).lastOrNull() ?: break
            val fewer = live.filter { it.id != last }
            val next = arrange(fewer)
            if (next.cuts >= arranged.cuts) break
            live = fewer
            hidden.add(last)
            arranged = next
        }
        return FlexPlan(
            width = st.aspect.width.toFloat(),
            height = arranged.height,
            boxes = arranged.boxes,
            tree = arranged.tree,
            dividers = if (free) arranged.solved.dividers.filter { !it.vertical } else arranged.solved.dividers,
            rules = arranged.solved.dividers,
            groupSizes = arranged.solved.groupSizes,
            contentLeft = pad,
            contentTop = padV,
            contentWidth = availW,
            contentHeight = arranged.availH,
            gap = gap,
            shownIds = live.map { it.id },
            hidden = hidden,
            clipped = arranged.clipped,
            minScale = arranged.minScale,
            limit = limit,
            tooTall = free && arranged.height > TOO_TALL,
            zoom = arranged.solved.zoom,
            crowded = arranged.boxes.any { box -> drawable.firstOrNull { it.id == box.id }?.let { m.short(it, box) } == true }
        )
    }

    // -----------------------------------------------------------------------------------------
    // Free mode: boxes placed by hand
    // -----------------------------------------------------------------------------------------

    /** Free mode: the least a box can be sized to, in card pixels. */
    const val FREE_MIN = 60f

    /** Content is never made to shrink below this to fit its tile or box while the card is within its budget. */
    const val LIMIT_SCALE = 0.8f

    /**
     * A picture is never shown smaller than this share of the card's content area (times its block's size): a photo or
     * a 3D snapshot. Charts and calendars, whose labels set their own least size, [VISIBLE_AREA_PLOT].
     */
    const val VISIBLE_AREA = 0.12f
    const val VISIBLE_AREA_PLOT = 0.10f

    internal fun visibleArea(type: FlexBlockType): Float = when (type) {
        FlexBlockType.CHART, FlexBlockType.CALENDAR -> VISIBLE_AREA_PLOT
        else -> VISIBLE_AREA
    }

    /**
     * The resize bars to show on block [id]'s tile ([FlexPlan.edgesOf] of [now], the plan of [spec]): those whose
     * border can move at least one way without cutting content off or pushing a block off the card. A border between
     * tiles that are all at their least (or a picture at its cap) cannot move, and a bar that does nothing would only
     * confuse.
     */
    fun resizeBars(spec: FlexSpec, content: FlexContent, assets: FlexAssets, now: FlexPlan, id: Int): List<FlexEdge> {
        if (spec.style.layoutMode == FlexLayoutMode.FREE) return emptyList()
        val b = now.box(id) ?: return emptyList()
        val keep = keeper(spec, content, assets)
        return now.edgesOf(id).filter { e ->
            val wide = e.side == FlexSide.LEFT || e.side == FlexSide.RIGHT
            listOf(BAR_PROBE, -BAR_PROBE).any { t ->
                val next = spec.copy(layout = TileTree.resized(now.tree, e.divider, t, FlexTileRules))
                val q = plan(next, content, assets)
                val box = q.box(id)
                box != null && (if (wide) abs(box.cellWidth - b.cellWidth) else abs(box.cellHeight - b.cellHeight)) > 2f && keep.holds(next, q)
            }
        }
    }

    private const val BAR_PROBE = 24f

    class FlexLoad(
        val costs: Map<Int, Float>,
        val used: Float,
        val budget: Float,
        val admitted: List<Int>
    ) {
        val over: Boolean get() = admitted.size < costs.size
    }

    fun load(spec: FlexSpec, content: FlexContent, assets: FlexAssets): FlexLoad {
        val st = spec.style
        val c = Ctx(Canvas(), st, assets).also { it.dry = true }
        val availW = st.aspect.width - 2f * st.padding
        val m = Metrics(c, content, availW)
        val shown = spec.blocks.filter { it.enabled && content.has(it.type) && m.heightOf(it, availW) > 0f }
        val tree = budgetLayout(spec, m, shown)
        val held = admitOn(m, st, shown, tree, strict = true)
        return FlexLoad(shown.associate { it.id to m.cost(it) }, m.leastOf(shown, tree) + st.spacing, roomOf(st) * (1f - BUDGET_SLACK) + st.spacing, held.map { it.id })
    }

    internal fun costOf(spec: FlexSpec, content: FlexContent, assets: FlexAssets, b: FlexBlock): Float {
        val st = spec.style
        val c = Ctx(Canvas(), st, assets).also { it.dry = true }
        return Metrics(c, content, st.aspect.width - 2f * st.padding).cost(b.copy(enabled = true))
    }

    internal fun visibleAreaOf(spec: FlexSpec, content: FlexContent, assets: FlexAssets, b: FlexBlock): Float {
        val st = spec.style
        val c = Ctx(Canvas(), st, assets).also { it.dry = true }
        return Metrics(c, content, st.aspect.width - 2f * st.padding).visibleArea(b)
    }

    private const val BUDGET_SLACK = 0.03f

    private fun roomOf(st: FlexStyle): Float {
        val height = if (st.aspect == FlexAspect.FREE) TOO_TALL else st.aspect.height.toFloat()
        return height - 2f * st.paddingV - footerOf(st)
    }

    private fun budgetLayout(spec: FlexSpec, m: Metrics, shown: List<FlexBlock>): TileNode? {
        val st = spec.style
        val safe = budgetTree(m, st, shown)
        if (st.layoutMode == FlexLayoutMode.FREE) return safe
        val now = TileTree.effective(spec.layout, shown.map { it.id }, st.aspect == FlexAspect.WIDE)
        return if (m.leastOf(shown, safe) < m.leastOf(shown, now)) safe else now
    }

    private fun admitOn(m: Metrics, st: FlexStyle, shown: List<FlexBlock>, tree: TileNode?, strict: Boolean = false): List<FlexBlock> {
        if (shown.isEmpty()) return shown
        val room = roomOf(st) * (if (strict) 1f - BUDGET_SLACK else 1f) + 0.5f
        val full = tree ?: budgetTree(m, st, shown)
        fun fits(keep: Collection<Int>): Boolean {
            val t = TileTree.prune(full, keep.toSet()) ?: return true
            return m.leastOf(shown.filter { it.id in keep }, t, strict) <= room
        }
        if (fits(shown.map { it.id })) return shown
        val held = ArrayList<FlexBlock>()
        for (b in shown) if (fits(held.map { it.id } + b.id)) held += b
        return held
    }

    private fun admit(m: Metrics, st: FlexStyle, shown: List<FlexBlock>, layout: TileNode?): List<FlexBlock> =
        admitOn(m, st, shown, TileTree.effective(layout, shown.map { it.id }, st.aspect == FlexAspect.WIDE))

    private fun admitAny(m: Metrics, st: FlexStyle, shown: List<FlexBlock>, layout: TileNode?): List<FlexBlock> {
        val now = admit(m, st, shown, layout)
        if (now.size == shown.size) return now
        val safe = admitOn(m, st, shown, budgetTree(m, st, shown))
        return if (safe.size > now.size) safe else now
    }

    private fun budgetTree(m: Metrics, st: FlexStyle, shown: List<FlexBlock>): TileNode? {
        fun stack(l: List<FlexBlock>): TileNode? = when (l.size) {
            0 -> null
            1 -> TileLeaf(l[0].id)
            else -> TileGroup(true, l.map { TileLeaf(it.id) }, List(l.size) { 0f })
        }
        if (st.aspect != FlexAspect.WIDE || shown.size < 3) return stack(shown)
        val banner = shown.first().takeIf { it.type.blockClass == FlexBlockClass.TITLE }
        val rest = if (banner != null) shown.drop(1) else shown
        val colW = (m.availW - st.spacing) / 2f
        val costs = rest.map { m.cost(it, colW) }
        val half = (1 until rest.size).minByOrNull { k -> max(costs.take(k).sum(), costs.drop(k).sum()) } ?: 1
        val cols = listOfNotNull(stack(rest.take(half)), stack(rest.drop(half)))
        val body: TileNode = if (cols.size == 1) cols[0] else TileGroup(false, cols, List(cols.size) { 0f })
        return if (banner == null) body else TileGroup(true, listOf(TileLeaf(banner.id), body), listOf(0f, 0f))
    }

    fun refuses(before: FlexSpec?, next: FlexSpec, content: FlexContent, assets: FlexAssets): Boolean {
        if (before == null) return false
        val after = load(next, content, assets)
        if (!after.over) return false
        val was = load(before, content, assets)
        if (after.costs.size - after.admitted.size > was.costs.size - was.admitted.size) return true
        return after.used / after.budget.coerceAtLeast(1f) > was.used / was.budget.coerceAtLeast(1f) + 0.001f
    }

    fun boxLimits(spec: FlexSpec, content: FlexContent, assets: FlexAssets, id: Int): FlexBoxLimits? {
        val st = spec.style
        val c = Ctx(Canvas(), st, assets)
        c.dry = true
        val pad = st.padding.toFloat()
        val availW = st.aspect.width - 2 * pad
        val b = spec.blocks.firstOrNull { it.id == id } ?: return null
        return Metrics(c, content, availW).limits(b, maxBoxH(st, st.paddingV.toFloat(), footerOf(st)))
    }

    private fun maxBoxH(st: FlexStyle, padV: Float, footer: Float): Float =
        if (st.aspect == FlexAspect.FREE) Float.MAX_VALUE else st.aspect.height - 2 * padV - footer

    /**
     * Free mode ([FlexLayoutMode.FREE]): every block is drawn in its own box ([FlexBlock.freeX] ...), in list order, so a
     * later block covers an earlier one. A block without a box yet gets one below the lowest box, as wide as the
     * card's content and as tall as its content there (see [placeBoxes], which keeps those). The content fits its box
     * by its class's rules, as a tile fits its cell: text takes the box's width and shrinks to keep its words whole,
     * a statistic reflows, a panel fills the box; a picture is cropped to the box. Free size is as tall as the lowest
     * box (plus the margin and the watermark); a fixed size keeps its height, and what is off the card is cut.
     */
    private fun planFree(spec: FlexSpec, content: FlexContent, assets: FlexAssets): FlexPlan {
        val st = spec.style
        val c = Ctx(Canvas(), st, assets)
        c.dry = true
        val pad = st.padding.toFloat()
        val padV = st.paddingV.toFloat()
        val footer = footerOf(st)
        val availW = st.aspect.width - 2 * pad
        val gap = st.spacing.toFloat()
        val m = Metrics(c, content, availW)
        val shown = spec.blocks.filter { it.enabled && content.has(it.type) }.filter { m.heightOf(it, availW) > 0f }
        val maxH = maxBoxH(st, padV, footer)
        val rects = freeRects(shown, m, pad, padV, availW, gap, maxH)
        val bottom = rects.values.maxOfOrNull { it[1] + it[3] } ?: padV
        val height = if (st.aspect == FlexAspect.FREE) max(bottom + padV + footer, MIN_FREE_HEIGHT) else st.aspect.height.toFloat()
        var clipped = false
        var minScale = 1f
        val boxes = ArrayList<FlexBlockBox>()
        for (b in shown) {
            val r = rects.getValue(b.id)
            val (x, y, w, h) = listOf(r[0], r[1], r[2], r[3])
            val fit = if (b.type.blockClass == FlexBlockClass.DRAWABLE) TileFit(w, h, 1f, false)
            else m.fitAt(b, w, h, 0f, 1f)
            if (fit.cut) clipped = true
            minScale = min(minScale, fit.scale)
            // The box is the tile: a panel fills it, bare text sits in it by the block's alignment.
            val stretch = st.panel != FlexPanel.NONE && !fit.cut &&
                (b.type.blockClass == FlexBlockClass.STATISTIC || b.type.blockClass == FlexBlockClass.TEXT && panelled(b))
            boxes += FlexBlockBox(b.id, x, y, w, h, fit.scale, fit.h - fit.inset, x, y, w, h, fit.columns, fit.inset, stretched = stretch)
        }
        return FlexPlan(
            width = st.aspect.width.toFloat(),
            height = height,
            boxes = boxes,
            tree = null,
            dividers = emptyList(),
            groupSizes = emptyMap(),
            contentLeft = pad,
            contentTop = padV,
            contentWidth = availW,
            contentHeight = height - 2 * padV - footer,
            gap = gap,
            shownIds = shown.map { it.id },
            hidden = emptyList(),
            clipped = clipped,
            minScale = minScale,
            limit = Int.MAX_VALUE,
            tooTall = st.aspect == FlexAspect.FREE && height > TOO_TALL,
            mode = FlexLayoutMode.FREE
        )
    }

    /**
     * The free boxes of [shown]: their own, held to the block's size limits (a box made when its content was smaller
     * grows to fit it), and for the ones without, one each below the lowest box (x, y, w, h).
     */
    private fun freeRects(shown: List<FlexBlock>, m: Metrics, pad: Float, padV: Float, availW: Float, gap: Float, maxH: Float): Map<Int, FloatArray> {
        val out = LinkedHashMap<Int, FloatArray>()
        var below = padV
        for (b in shown) {
            if (!b.placed) continue
            val (w, h) = m.limits(b, maxH).clamp(b.freeW, b.freeH)
            out[b.id] = floatArrayOf(b.freeX, b.freeY, w, h)
            below = max(below, b.freeY + h + gap)
        }
        for (b in shown) {
            if (b.placed) continue
            val (w, h) = m.limits(b, maxH).clamp(availW, max(FREE_MIN, m.heightOf(b, availW)))
            out[b.id] = floatArrayOf(pad, below, w, h)
            below += h + gap
        }
        return shown.associate { it.id to out.getValue(it.id) }
    }

    /** Free mode: [spec] with a box for every block on the card that has none yet (where [plan] puts it), so it stays there. */
    fun placeBoxes(spec: FlexSpec, content: FlexContent, assets: FlexAssets): FlexSpec {
        if (spec.style.layoutMode != FlexLayoutMode.FREE) return spec
        val p = planFree(spec, content, assets)
        if (spec.blocks.all { b -> b.placed || p.box(b.id) == null }) return spec
        return spec.copy(blocks = spec.blocks.map { b ->
            val box = p.box(b.id)
            if (b.placed || box == null) b else b.copy(freeX = box.cellLeft, freeY = box.cellTop, freeW = box.cellWidth, freeH = box.cellHeight)
        })
    }

    fun withMode(spec: FlexSpec, content: FlexContent, assets: FlexAssets, mode: FlexLayoutMode): FlexSpec {
        if (spec.style.layoutMode == mode) return spec
        val before = plan(spec, content, assets)
        val switched = spec.copy(style = spec.style.copy(layoutMode = mode))
        return when (mode) {
            FlexLayoutMode.FREE -> {
                val cells = before.boxes.associateBy { it.id }
                val order = before.boxes.map { it.id }
                val blocks = switched.blocks.sortedBy { b -> order.indexOf(b.id).let { if (it < 0) Int.MAX_VALUE else it } }.map { b ->
                    val box = cells[b.id] ?: return@map b
                    b.copy(freeX = box.cellLeft, freeY = box.cellTop, freeW = box.cellWidth, freeH = box.cellHeight)
                }
                switched.copy(blocks = blocks)
            }
            FlexLayoutMode.SPLIT -> switched
        }
    }

    private class Measured(val height: Float, val overflow: Float)

    private data class MeasureKey(
        val type: FlexBlockType,
        val variant: Int,
        val keys: List<String>?,
        val limit: Int,
        val text: String,
        val flag: Boolean,
        val size: Float,
        val font: FlexFont,
        val textScale: Float,
        val moduleScale: Float,
        val panel: Boolean,
        val content: Int,
        val width: Long
    )

    private val measures = java.util.concurrent.ConcurrentHashMap<MeasureKey, Measured>()

    private class Metrics(val c: Ctx, val ct: FlexContent, val availW: Float) {
        private val needs = HashMap<Int, Float>()
        private val stats = HashMap<Int, StatShape?>()
        private val shapes = HashMap<Int, DrawableShape>()
        private val contentId = System.identityHashCode(ct)

        private fun laidOut(b: FlexBlock, w: Float): Measured {
            val st = c.st
            val key = MeasureKey(
                b.type, b.variant, b.keys, b.limit, b.text, b.flag, b.size, st.font, st.textScale, st.moduleScale,
                st.panel != FlexPanel.NONE, contentId, (w * 100).roundToLong()
            )
            measures[key]?.let { return it }
            if (measures.size > MEASURE_CACHE) measures.clear()
            c.overflow = 0f
            val h = block(c, b, ct, 0f, 0f, w)
            val out = Measured(h, c.overflow)
            measures[key] = out
            return out
        }

        fun heightOf(b: FlexBlock, w: Float): Float = when (b.type.blockClass) {
            FlexBlockClass.DRAWABLE -> {
                val shape = shape(b)
                if (shape.hasContent) FlexFit.drawableAsk(shape, w, NO_CAP).h else 0f
            }
            FlexBlockClass.STATISTIC -> stat(b)?.let { FlexFit.statistic(it, w, NO_CAP, true).h } ?: 0f
            FlexBlockClass.TITLE, FlexBlockClass.TEXT -> laidOut(b, w).height
        }

        fun tileHeight(b: FlexBlock, w: Float, s: Float): Float = heightOf(b, w / s) * s

        fun needOf(b: FlexBlock): Float = needs.getOrPut(b.id) {
            when (b.type.blockClass) {
                FlexBlockClass.DRAWABLE -> 0f
                FlexBlockClass.STATISTIC -> stat(b)?.need ?: 0f
                FlexBlockClass.TITLE, FlexBlockClass.TEXT -> {
                    val k = scaleOf(c, b)
                    val most = availW * NEED_REACH
                    var w = 60f
                    for (i in 0 until 24) {
                        val o = laidOut(b, w).overflow
                        if (o <= 0.5f) break
                        w += o * k + 1f
                        if (w >= most) {
                            w = most
                            break
                        }
                    }
                    w.coerceAtMost(most)
                }
            }
        }

        fun leastScale(b: FlexBlock): Float {
            val cls = b.type.blockClass
            if (cls == FlexBlockClass.DRAWABLE) return 1f
            val need = needOf(b)
            val fits = if (need > 0f) availW / need else LIMIT_SCALE
            return min(LIMIT_SCALE, fits).coerceAtLeast(FlexFit.minScale(cls.kind))
        }

        fun short(b: FlexBlock, box: FlexBlockBox): Boolean =
            if (b.type.blockClass == FlexBlockClass.DRAWABLE) box.width * box.height < 0.9f * visibleArea(b)
            else box.scale < leastScale(b) - 0.012f

        fun hugOf(b: FlexBlock): Float = when (b.type.blockClass) {
            FlexBlockClass.DRAWABLE -> FlexFit.drawableAsk(shape(b), availW, NO_CAP).w
            FlexBlockClass.STATISTIC -> min(stat(b)?.hug ?: availW, availW)
            FlexBlockClass.TITLE, FlexBlockClass.TEXT -> hugWidth(b, availW, 1f, heightOf(b, availW))
        }

        fun item(b: FlexBlock, banner: Int?): TileItem = when (b.type.blockClass) {
            FlexBlockClass.DRAWABLE -> TileItem(
                b.id, TileKind.DRAWABLE, 0f, 1f, 1f,
                pic = shape(b), least = { w -> leastH(b, w) }, picArea = visibleArea(b)
            )
            FlexBlockClass.STATISTIC -> {
                val sh = stat(b)
                TileItem(b.id, TileKind.STATISTIC, sh?.need ?: 0f, FlexTileRules.statMinScale, FlexTileRules.zoom.max, stat = sh, stretchy = stretches(c.st, b), leastScale = leastScale(b))
            }
            FlexBlockClass.TITLE -> TileItem(
                b.id, TileKind.TITLE, needOf(b), FlexTileRules.textMinScale, FlexTileRules.TITLE_ZOOM_MAX,
                text = { w, s -> tileHeight(b, w, s) }, air = if (b.id == banner) c.st.titleInset else 0f, hug = hugOf(b), leastScale = leastScale(b)
            )
            FlexBlockClass.TEXT -> TileItem(
                b.id, TileKind.TEXT, needOf(b), FlexTileRules.textMinScale, FlexTileRules.zoom.max,
                text = { w, s -> tileHeight(b, w, s) }, stretchy = stretches(c.st, b), leastScale = leastScale(b)
            )
        }

        fun tiler(shown: List<FlexBlock>, banner: Int?, byContent: Boolean = false, strict: Boolean = false): TileSolver =
            TileSolver(shown.associate { it.id to item(it, banner) }, c.st.spacing.toFloat(), FlexTileRules, byContent, strict)

        fun leastOf(shown: List<FlexBlock>, tree: TileNode?, strict: Boolean = true): Float {
            if (tree == null) return 0f
            return tiler(shown, TileTree.banner(tree), strict = strict).height(tree, availW, TileZoom.Q_BUDGET)
        }

        fun fitAt(b: FlexBlock, cw: Float, ch: Float, air: Float, top: Float): TileFit {
            val hugAt: (Float, Float) -> Float = { _, _ -> cw }
            return when (b.type.blockClass) {
                FlexBlockClass.DRAWABLE -> FlexFit.drawable(shape(b), cw, ch)
                FlexBlockClass.STATISTIC -> {
                    val shape = stat(b) ?: return TileFit(cw, 0f, 1f, false)
                    val f = FlexFit.statAt(shape, cw, ch, top, FlexTileRules.statMinScale)
                    TileFit(cw, min(f.height, ch), f.scale, f.narrow || f.height > ch + 1f, f.columns)
                }
                FlexBlockClass.TITLE -> snap(b, cw, ch, FlexFit.title(cw, ch, needOf(b), air, { w -> heightOf(b, w) }, hugAt, top))
                FlexBlockClass.TEXT -> snap(b, cw, ch, FlexFit.text(cw, ch, needOf(b), { w -> heightOf(b, w) }, hugAt, maxScale = top))
            }
        }

        private fun snap(b: FlexBlock, cw: Float, ch: Float, f: TileFit): TileFit {
            if (f.cut) return f
            val least = FlexFit.minScale(b.type.blockClass.kind)
            var s = f.scale
            for (i in 0 until 4) {
                val o = laidOut(b, cw / s).overflow
                if (o <= 0.5f) break
                val next = cw / (cw / s + o + 1f)
                if (next < least) return f
                s = next
            }
            if (s >= f.scale) return f
            val room = ch - f.inset
            val dh = heightOf(b, cw / s) * s
            val cut = dh > room + 1f || laidOut(b, cw / s).overflow > 0.5f
            return TileFit(cw, min(dh, room) + f.inset, s, cut, 0, f.inset)
        }

        fun hugWidth(b: FlexBlock, cw: Float, s: Float, dh: Float): Float {
            var lo = min(cw, needOf(b) * s)
            var hi = cw
            var i = 0
            while (hi - lo > 3f && i++ < 8) {
                val mid = (lo + hi) / 2f
                val at = laidOut(b, mid / s)
                if (at.overflow <= 0.5f && abs(at.height * s - dh) <= 1.5f) hi = mid else lo = mid
            }
            return min(cw, hi + 2f)
        }

        fun cost(b: FlexBlock, w: Float = availW): Float = leastH(b, w) + c.st.spacing

        val availH: Float = run {
            val st = c.st
            (if (st.aspect.height > 0) st.aspect.height else FlexAspect.PORTRAIT.height) - 2f * st.paddingV - footerOf(st)
        }

        fun leastH(b: FlexBlock, w: Float): Float {
            val s = LIMIT_SCALE
            return when (b.type.blockClass) {
                FlexBlockClass.DRAWABLE -> {
                    val sh = shape(b)
                    max(visibleH(b), visibleArea(b) / min(w, sh.maxW).coerceAtLeast(1f)).coerceAtMost(max(visibleH(b), min(sh.maxH, availH)))
                }
                FlexBlockClass.STATISTIC -> {
                    val st = stat(b) ?: return 0f
                    var best = Float.MAX_VALUE
                    for (j in st.widths.indices) if (st.widths[j] * s <= w + 0.5f) best = min(best, st.heights[j] * s)
                    if (best == Float.MAX_VALUE) st.heights.last() * s else best
                }
                FlexBlockClass.TITLE, FlexBlockClass.TEXT -> heightOf(b, w / s) * s
            }
        }

        fun visibleArea(b: FlexBlock): Float = visibleArea(b.type) * b.size * availW * availH

        fun visibleH(b: FlexBlock): Float {
            val sh = shape(b)
            val area = visibleArea(b)
            val ratio = sh.ratioMax.coerceIn(0.4f, 4f)
            var h = sqrt(area / ratio)
            if (h * ratio > availW) h = availW / ratio
            return h.coerceAtLeast(max(sh.minH, FREE_MIN)).coerceAtMost(min(availH, max(sh.maxH, sh.minH)))
        }

        fun shape(b: FlexBlock): DrawableShape = shapes.getOrPut(b.id) { drawableShape(c, b, ct) }

        fun stat(b: FlexBlock): StatShape? = stats.getOrPut(b.id) { statShape(c, b, ct) }

        fun limits(b: FlexBlock, maxH: Float): FlexBoxLimits {
            val s = LIMIT_SCALE
            return when (b.type.blockClass) {
                FlexBlockClass.DRAWABLE -> {
                    val sh = shape(b)
                    val least = max(FREE_MIN, sh.minH)
                    FlexBoxLimits(
                        minW = min(availW, max(sh.minW, FREE_MIN)), maxW = availW, maxH = maxH,
                        ratioMin = if (sh.hasContent) sh.ratioMin else 0f,
                        ratioMax = if (sh.hasContent) sh.ratioMax else Float.POSITIVE_INFINITY
                    ) { least }
                }
                FlexBlockClass.STATISTIC -> {
                    val st = stat(b)
                    if (st == null) FlexBoxLimits(FREE_MIN, availW, maxH, 0f, Float.POSITIVE_INFINITY) { FREE_MIN }
                    else {
                        val k = st.widths.size - 1
                        FlexBoxLimits(min(availW, st.widths[k] * s), availW, maxH, 0f, Float.POSITIVE_INFINITY) { w ->
                            var best = Float.MAX_VALUE
                            for (j in st.widths.indices) if (st.widths[j] * s <= w + 0.5f) best = min(best, st.heights[j] * min(1f, w / st.widths[j]))
                            if (best == Float.MAX_VALUE) st.heights.last() * s else best
                        }
                    }
                }
                FlexBlockClass.TITLE, FlexBlockClass.TEXT -> {
                    val need = needOf(b)
                    FlexBoxLimits(min(availW, need * s), availW, maxH, 0f, Float.POSITIVE_INFINITY) { w ->
                        max(24f, heightOf(b, w / s) * s)
                    }
                }
            }
        }
    }

    private const val NEED_REACH = 4f

    private const val MEASURE_CACHE = 200_000

    /** A block as the auto aligner sees it: its class, its least and its hugging width, and how its height goes with its width. */
    private fun autoItem(c: Ctx, m: Metrics, b: FlexBlock, availW: Float): AlignItem = when (b.type.blockClass) {
        FlexBlockClass.DRAWABLE -> AlignItem(b.id, TileKind.DRAWABLE, 0f, availW, drawable = m.shape(b))
        FlexBlockClass.STATISTIC -> {
            val shape = m.stat(b) ?: StatShape(intArrayOf(1), floatArrayOf(1f), floatArrayOf(0f), 1, FlexTileRules.reflowKeep)
            AlignItem(b.id, TileKind.STATISTIC, shape.need, min(shape.hug, availW), stat = shape)
        }
        FlexBlockClass.TITLE, FlexBlockClass.TEXT -> {
            val minScale = FlexFit.minScale(b.type.blockClass.kind)
            val widths = AlignItem.sampleWidths(availW / minScale)
            val heights = FloatArray(widths.size) { m.heightOf(b, widths[it]) }
            val inset = if (b.type.blockClass == FlexBlockClass.TITLE) c.st.titleInset else 0f
            val full = m.heightOf(b, availW)
            AlignItem(b.id, b.type.blockClass.kind, m.needOf(b), m.hugWidth(b, availW, 1f, full), heights, inset = inset, exactW = availW, exactH = full)
        }
    }

    private fun withLayout(spec: FlexSpec, drawable: List<FlexBlock>, live: List<FlexBlock>, tree: TileNode): FlexSpec {
        val tiles = TileTree.leaves(tree).mapNotNull { id -> live.firstOrNull { it.id == id } }
        val rest = drawable.filter { d -> tiles.none { it.id == d.id } }
        val order = tiles + rest
        val blocks = spec.blocks.toMutableList()
        var next = 0
        spec.blocks.forEachIndexed { i, b -> if (drawable.any { it.id == b.id }) blocks[i] = order[next++] }
        return spec.copy(blocks = blocks, layout = tree)
    }

    fun withTree(spec: FlexSpec, tree: TileNode?): FlexSpec {
        if (tree == null) return spec.copy(layout = null)
        val tiles = TileTree.leaves(tree).distinct()
        val byId = spec.blocks.associateBy { it.id }
        val ordered = tiles.mapNotNull { byId[it] }
        val blocks = spec.blocks.toMutableList()
        var next = 0
        spec.blocks.forEachIndexed { i, b -> if (b.id in tiles) blocks[i] = ordered[next++] }
        return spec.copy(blocks = blocks, layout = tree)
    }

    class Keeper internal constructor(private val content: FlexContent, private val assets: FlexAssets, private val was: FlexPlan, private val least: Int) {
        private val shown = was.shownIds.toSet()

        fun holds(after: FlexSpec, now: FlexPlan = plan(after, content, assets)): Boolean {
            if (!now.shownIds.toSet().containsAll(shown) || (now.clipped && !was.clipped)) return false
            return belowLeast(after, content, assets, now) <= least
        }
    }

    fun keeper(before: FlexSpec, content: FlexContent, assets: FlexAssets): Keeper {
        val was = plan(before, content, assets)
        return Keeper(content, assets, was, belowLeast(before, content, assets, was))
    }

    fun holdsAsMuch(before: FlexSpec, after: FlexSpec, content: FlexContent, assets: FlexAssets): Boolean =
        keeper(before, content, assets).holds(after)

    internal fun belowLeast(spec: FlexSpec, content: FlexContent, assets: FlexAssets, p: FlexPlan = plan(spec, content, assets)): Int {
        val st = spec.style
        val m = Metrics(Ctx(Canvas(), st, assets).also { it.dry = true }, content, st.aspect.width - 2f * st.padding)
        val byId = spec.blocks.associateBy { it.id }
        return p.boxes.count { box ->
            val b = byId[box.id] ?: return@count false
            m.short(b, box)
        }
    }

    private fun guarded(spec: FlexSpec, fitted: FlexSpec, content: FlexContent, assets: FlexAssets): FlexSpec {
        val held = load(spec, content, assets).admitted
        fun bad(s: FlexSpec): Boolean {
            val p = plan(s, content, assets)
            return !p.shownIds.containsAll(held) || belowLeast(s, content, assets, p) > 0
        }
        if (!bad(fitted)) return fitted
        val search = Search(spec, content, assets, emptyList())
        val live = held.mapNotNull { search.byId[it] }
        val safe = budgetTree(search.m, spec.style, live) ?: return fitted
        val card = withLayout(spec, search.drawable, live, safe)
        return if (!bad(card)) card else fitted
    }

    fun rescue(spec: FlexSpec, content: FlexContent, assets: FlexAssets): FlexSpec? {
        if (spec.style.layoutMode != FlexLayoutMode.SPLIT) return null
        val p = plan(spec, content, assets)
        val held = load(spec, content, assets).admitted
        val m = Metrics(Ctx(Canvas(), spec.style, assets).also { it.dry = true }, content, spec.style.aspect.width - 2f * spec.style.padding)
        val byId = spec.blocks.associateBy { it.id }
        fun small(q: FlexPlan): List<Int> = q.boxes.filter { box ->
            val b = byId[box.id] ?: return@filter false
            m.short(b, box)
        }.map { it.id }
        fun broken(q: FlexPlan): List<Int> = q.hidden.filter { it in held } + small(q) + (if (q.clipped) listOf(CLIPPED) else emptyList())
        val bad = broken(p)
        if (bad.isEmpty()) return null
        var best: FlexSpec? = null
        var least = bad.size
        fun better(s: FlexSpec?): Boolean {
            if (s == null || s == spec) return false
            val n = broken(plan(s, content, assets)).size
            if (n < least) {
                best = s
                least = n
            }
            return n == 0
        }
        var tree = p.tree
        for (id in bad) {
            val root = tree ?: break
            if (id !in TileTree.leaves(root)) continue
            val top = (root as? TileGroup)?.takeIf { it.vertical }?.children?.firstOrNull { id in TileTree.leaves(it) }
            if (top == null || top is TileLeaf) continue
            val anchor = TileTree.leaves(top).toSet()
            tree = TileTree.moveBeside(root, id, anchor, DropZone.BOTTOM) ?: tree
        }
        if (tree != p.tree && better(withTree(spec, tree))) return best
        val probe = Search(spec, content, assets, emptyList())
        val seen = TileTree.leaves(p.tree).filter { it in probe.byId }
        val reading = seen + probe.drawable.map { it.id }.filter { it !in seen }
        val quick = Search(spec, content, assets, reading, fixedOrder = true, titleFirst = false, effort = TileAligner.QUICK_EFFORT)
        quick.run(drop = false)
        for (found in quick.tried.sortedBy { it.score }) if (better(found.spec)) return best
        val live = held.mapNotNull { probe.byId[it] }
        budgetTree(probe.m, spec.style, live)?.let { better(withLayout(spec, probe.drawable, live, it)) }
        if (best == null && better(autoAlign(spec, content, assets).spec)) return best
        return best
    }

    private const val CLIPPED = -1

    private const val ORDER_SWAP = 0.2

    private const val ZOOM_GAIN = 1.2

    private const val BELOW_LEAST = 15.0

    private fun inversions(order: List<Int>, reading: List<Int>): Int {
        val rank = reading.withIndex().associate { it.value to it.index }
        val r = order.mapNotNull { rank[it] }
        var n = 0
        for (i in r.indices) for (j in i + 1 until r.size) if (r[i] > r[j]) n++
        return n
    }

    /** A card laid out by best fit and what it scored. */
    private class Found(val spec: FlexSpec, val plan: FlexPlan, val score: Double)

    /**
     * One search of best fit, for the card as [spec] has it (its module size, best fit's growth included): measures
     * the blocks for the optimiser, finds tilings, lays them out for real and scores them. [reading] is the order the
     * blocks read in now: tilings that read in another order pay [ORDER_SWAP] per swapped pair.
     */
    private class Search(
        val spec: FlexSpec,
        val content: FlexContent,
        val assets: FlexAssets,
        val reading: List<Int>,
        val fixedOrder: Boolean = false,
        val titleFirst: Boolean = true,
        val effort: Int = TileAligner.FULL_EFFORT
    ) {
        val st = spec.style
        val c = Ctx(Canvas(), st, assets).also { it.dry = true }
        val pad = st.padding.toFloat()
        val footer = if (st.watermark && st.watermarkText.isNotBlank()) FLEX_FOOTER else 0f
        val availW = st.aspect.width - 2 * pad
        val gap = st.spacing.toFloat()
        val free = st.aspect == FlexAspect.FREE
        val m = Metrics(c, content, availW)

        /** The blocks that can be on the card. */
        val drawable = spec.blocks.filter { it.enabled && content.has(it.type) }.filter { m.heightOf(it, availW) > 0f }
        val byId = drawable.associateBy { it.id }
        val items: Map<Int, AlignItem> by lazy { HashMap<Int, AlignItem>().also { map -> for (b in drawable) map[b.id] = autoItem(c, m, b, availW) } }
        private val stackSpace = AlignSpace(availW, 1f, gap)
        val isTitle: (FlexBlock) -> Boolean = { it.type.blockClass == FlexBlockClass.TITLE }

        /**
         * How many modules of [p] are below their least: content drawn smaller than [LIMIT_SCALE] of the person's size,
         * a picture smaller than its least visible area. A card within its budget never needs one (its budget's tiling
         * has none), so a fit that comes to one is not taken ([guarded]).
         */
        fun belowLeast(p: FlexPlan): Int = p.boxes.count { box ->
            val b = byId[box.id] ?: return@count false
            m.short(b, box)
        }

        /** The blocks of [list] (in that order) the card can hold, by its content budget. */
        fun admitted(list: List<FlexBlock>): List<FlexBlock> = admitAny(m, st, list, spec.layout)

        /** The blocks in reading order, the title first. */
        val ordered: List<FlexBlock> = reading.mapNotNull { byId[it] }.let { r -> if (titleFirst) r.filter(isTitle) + r.filterNot(isTitle) else r }
        private val stacked = TileAligner.stackedHeight(ordered.map { items.getValue(it.id) }, stackSpace, FlexTileRules).coerceAtLeast(1f)

        /** What a laid out card costs: the same prices the search uses, on the real tiles. */
        fun score(p: FlexPlan): Double {
            val space = AlignSpace(p.contentWidth, p.contentHeight, gap)
            var total = 0.0
            val boxes = p.boxes.associateBy { it.id }
            val banner = TileTree.banner(p.tree)
            for (box in p.boxes) {
                val item = items[box.id] ?: continue
                val fit = TileFit(box.width, box.contentHeight, box.scale, false, box.columns, box.inset)
                total += FlexTileRules.tileCost(item, space, box.cellWidth, box.cellHeight, fit, box.cellTop <= p.contentTop + 1f, box.id == banner)
            }
            // The structure: splits, how far they are from the middle, how unequal the modules of a row are, and rows whose columns do not line up.
            // Where a part visibly ends: its last tile's bottom (a stretched panel ends at its cell's bottom).
            fun bottom(node: TileNode): Float = when (node) {
                is TileLeaf -> boxes[node.id]?.let { it.top + it.height } ?: 0f
                is TileGroup -> if (node.vertical) bottom(node.children.last()) else node.children.maxOf { bottom(it) }
            }
            fun walk(node: TileNode, path: List<Int>): Float {
                if (node is TileLeaf) return boxes[node.id]?.contentHeight ?: 0f
                val g = node as TileGroup
                val used = FloatArray(g.children.size) { walk(g.children[it], path + it) }
                val sizes = p.groupSizes[path]?.toFloatArray() ?: FloatArray(used.size) { 1f }
                total += FlexTileRules.groupCost(g.vertical, sizes, used)
                if (!g.vertical) {
                    val ends = g.children.map { bottom(it) }
                    val ids = TileTree.leaves(g)
                    val rowTop = ids.minOf { boxes[it]?.cellTop ?: Float.MAX_VALUE }
                    val rowH = (ends.max() - rowTop).coerceAtLeast(1f)
                    total += FlexTileRules.EDGES * (ends.max() - ends.min()) / rowH
                }
                if (g.vertical) {
                    var before: FloatArray? = null
                    g.children.forEachIndexed { i, kid ->
                        if (kid is TileGroup && !kid.vertical) {
                            val row = p.groupSizes[path + i]?.toFloatArray()
                            if (row != null && row.sum() > 0f) {
                                val shares = FloatArray(row.size) { row[it] / row.sum() }
                                val prev = before
                                if (prev != null && prev.size == shares.size) {
                                    var off = 0f
                                    for (k in shares.indices) off += abs(shares[k] - prev[k])
                                    total += FlexTileRules.RAGGED * off / 2f
                                }
                                before = shares
                            }
                        }
                    }
                }
                return if (g.vertical) used.sum() + gap * (used.size - 1) else (used.maxOrNull() ?: 0f)
            }
            p.tree?.let { walk(it, emptyList()) }
            // Room no cell was given (under a stack whose parts could not use it) is as wasted as room inside a cell.
            if (!free) {
                val bottom = p.boxes.maxOfOrNull { it.cellTop + it.cellHeight } ?: p.contentTop
                total += FlexTileRules.LEFT_OVER * (p.contentTop + p.contentHeight - bottom).coerceAtLeast(0f) / p.contentHeight.coerceAtLeast(1f)
            }
            if (p.clipped) total += FlexTileRules.CUT
            total += FlexTileRules.HIDDEN * p.hidden.size
            total += BELOW_LEAST * belowLeast(p)
            total += ORDER_SWAP * inversions(TileTree.leaves(p.tree), reading)
            if (free) total += 1.4 * (p.contentHeight / stacked)
            else total -= ZOOM_GAIN * ln(p.zoom.coerceIn(0.4f, FlexTileRules.zoom.max).toDouble())
            return total
        }

        /**
         * The orders worth trying for [live] (title first, then the rest): the reading order, and others that put blocks
         * that pair well next to each other. Up to four after the title: every order; up to six: neighbours swapped,
         * and each class kept together; more: the reading order only (each order is a whole search).
         */
        fun ordersFor(live: List<FlexBlock>): List<List<FlexBlock>> = orders(live)

        private fun orders(live: List<FlexBlock>): List<List<FlexBlock>> {
            val head = live.filter(isTitle)
            val rest = live.filterNot(isTitle)
            val out = LinkedHashSet<List<FlexBlock>>()
            out += live
            if (fixedOrder || rest.size > 6) return out.toList()
            fun add(r: List<FlexBlock>) {
                out += head + r
            }
            // Same classes side by side, each class where it first appears.
            val firstAt = HashMap<FlexBlockClass, Int>()
            rest.forEachIndexed { i, b -> firstAt.getOrPut(b.type.blockClass) { i } }
            add(rest.sortedBy { firstAt.getValue(it.type.blockClass) })
            when {
                rest.size <= 4 -> permutations(rest).forEach { add(it) }
                else -> {
                    for (i in 0 until rest.size - 1) {
                        if (rest[i].type.blockClass == rest[i + 1].type.blockClass) continue
                        val r = rest.toMutableList()
                        r[i] = rest[i + 1]
                        r[i + 1] = rest[i]
                        add(r)
                    }
                }
            }
            return out.toList()
        }

        private fun <T> permutations(list: List<T>): List<List<T>> {
            if (list.size <= 1) return listOf(list)
            val out = ArrayList<List<T>>()
            for (i in list.indices) {
                val rest = list.toMutableList()
                val first = rest.removeAt(i)
                for (p in permutations(rest)) out += listOf(first) + p
            }
            return out
        }

        /**
         * Tilings worth laying out for [live]: the optimiser runs on every order from [orders] (and, for Free size, at a
         * few card heights), all side by side; the best few by its own price and the reading order's own pay come back,
         * each also with its rows' columns lined up and with every row cut in the middle.
         */
        private fun trees(live: List<FlexBlock>, explore: Boolean): List<TileNode> {
            val heights = if (free) {
                val base = TileAligner.stackedHeight(live.map { items.getValue(it.id) }, stackSpace, FlexTileRules)
                val least = max(300f, MIN_FREE_HEIGHT - 2 * st.paddingV - footer)
                listOf(1f, 0.82f, 0.68f, 0.56f).map { max(least, it * base) }.distinct()
            } else {
                listOf(st.aspect.height - 2f * st.paddingV - footer)
            }
            val ids = live.map { it.id }
            val jobs = ArrayList<Pair<List<FlexBlock>, Float>>()
            for (order in if (explore) orders(live) else listOf(live)) for (h in heights) jobs += order to h
            class Hit(val tree: TileNode, val price: Double, val reading: Boolean)
            val found = jobs.parallelStream()
                .map { (order, h) ->
                    val list = order.map { items.getValue(it.id) }
                    val swaps = inversions(order.map { it.id }, ids)
                    val tall = if (free) 1.4 * h / stacked else 0.0
                    TileAligner(list, AlignSpace(availW, h, gap), FlexTileRules, effort).solve()
                        .map { Hit(it.tree, it.cost + ORDER_SWAP * swaps + tall, swaps == 0) }
                }
                .collect(Collectors.toList())
                .flatten()
            val keep = (found.filter { it.reading } + found.sortedBy { it.price }.take(TOP_ORDERS)).distinctBy { it.tree }
            val out = ArrayList<TileNode>()
            for (hit in keep) {
                TileTree.relaxed(hit.tree)?.let { tree -> if (tree !in out) out += tree }
            }
            return out
        }

        /** Every card the last [run] laid out. */
        val tried = ArrayList<Found>()

        /** Other good cards of the last [run] than [best], best first: different tilings that show as much, scoring within [ALT_RANGE]. */
        fun alternatives(best: Found): List<FlexSpec> = tried
            .filter { it.plan.shownIds.size >= best.plan.shownIds.size && it.score <= best.score + ALT_RANGE }
            .sortedBy { it.score }
            .distinctBy { it.spec.layout }
            .filter { it.spec.layout != best.spec.layout }
            .take(ALT_COUNT)
            .map { it.spec }

        /** The best card this search finds; [drop]: when the best cannot show every block, leave off the least important and try again. */
        fun run(drop: Boolean): Found? {
            tried.clear()
            var live = admitted(ordered)
            var best: Found? = null
            var first = true
            while (live.isNotEmpty()) {
                var round: Found? = null
                // Other orders are tried with every block; once blocks have to be left off, only the reading order.
                val candidates = (trees(live, explore = first) + listOfNotNull(budgetTree(m, st, live)))
                    .mapNotNull { TileTree.relaxed(it) }.distinct()
                for (tree in candidates) {
                    val candidate = withLayout(spec, drawable, live, tree)
                    val p = plan(candidate, content, assets)
                    val f = Found(candidate, p, score(p))
                    tried += f
                    if (round == null || f.score < round.score) round = f
                }
                first = false
                val found = round ?: break
                if (best == null || found.score < best.score) best = found
                if (!drop || free || live.size == 1 || found.plan.shownIds.size >= live.size) break
                // The least important block goes: the last of the lowest priority (text, then statistics, then drawables), never the title.
                val gone = live.indices.filter { !isTitle(live[it]) }
                    .minWithOrNull(compareBy<Int> { FlexTileRules.priority(live[it].type.blockClass.kind) }.thenByDescending { it })
                    ?: live.lastIndex
                live = live.filterIndexed { i, _ -> i != gone }
            }
            return best
        }
    }

    private const val TOP_ORDERS = 4

    private const val ALT_COUNT = 6
    private const val ALT_RANGE = 2.5

    internal fun scoreOf(spec: FlexSpec, content: FlexContent, assets: FlexAssets): Double {
        val p = plan(spec, content, assets)
        val reading = TileTree.leaves(p.tree).let { t -> t + spec.blocks.map { it.id }.filter { it !in t } }
        return Search(spec, content, assets, reading).score(p)
    }

    fun autoAlign(spec: FlexSpec, content: FlexContent, assets: FlexAssets): FlexAutoResult {
        if (spec.style.layoutMode == FlexLayoutMode.FREE) return FlexAutoResult(spec, false, "Free mode is placed by hand")
        val before = plan(spec, content, assets)
        val probe = Search(spec, content, assets, emptyList())
        if (probe.drawable.isEmpty()) return FlexAutoResult(spec, false, "Nothing to align")
        val seen = TileTree.leaves(before.tree).filter { it in probe.byId }
        val reading = seen + probe.drawable.map { it.id }.filter { it !in seen }
        val search = Search(spec, content, assets, reading)
        var chosen = search.run(drop = true) ?: return FlexAutoResult(spec, false, "Too many blocks to align")
        val others = search.alternatives(chosen)
        guarded(spec, chosen.spec, content, assets).let { kept ->
            if (kept != chosen.spec) {
                val p = plan(kept, content, assets)
                chosen = Found(kept, p, search.score(p))
            }
        }
        if (chosen.spec == spec) return FlexAutoResult(spec, false, "Already the best fit", others)
        if (before.hidden.isEmpty() && !holdsAsMuch(spec, chosen.spec, content, assets)) return FlexAutoResult(spec, false, "Already the best fit", others)
        val now = search.score(before)
        if (now - chosen.score < 0.005 && before.hidden.size <= chosen.plan.hidden.size) return FlexAutoResult(spec, false, "Already the best fit", others)
        val message = when {
            chosen.plan.hidden.size > before.hidden.size -> "Aligned; ${chosen.plan.hidden.size} block(s) don't fit this size"
            chosen.plan.minScale < LIMIT_SCALE + 0.05f -> "Aligned; it's a tight fit"
            else -> "Aligned"
        }
        return FlexAutoResult(chosen.spec, true, message, others)
    }

    // -----------------------------------------------------------------------------------------
    // Drawables: one shape model for photos, charts and whatever picture comes next
    // -----------------------------------------------------------------------------------------

    /** A chart is not held to the half-card limit, but never taller than this (a stretched chart reads badly). */
    private const val CHART_MAX_H = 720f

    /** The shape a chart has when nothing limits it: about what it used to be drawn at across a card. */
    private const val CHART_RATIO = 1.85f

    /** The least room a chart's plot needs between its labels. */
    private const val CHART_PLOT_W = 160f
    private const val CHART_PLOT_H = 120f
    private const val CHART_AXIS_W = 84f
    private const val CHART_DATES_H = 40f

    private fun photoBitmap(c: Ctx, b: FlexBlock, ct: FlexContent): Bitmap? = when (b.type) {
        FlexBlockType.MODEL -> ct.modelUri?.let { c.assets.images[it] }
        else -> (b.keys?.firstOrNull() ?: ct.photoUri)?.let { c.assets.images[it] }
    }

    /** A calendar's day cell at its natural size, and the least it reads at (the block's own units, before its size). */
    private const val CAL_CELL = 66f
    private const val CAL_CELL_MIN = 40f
    private const val CAL_GAP = 6f

    /**
     * How a drawable block takes its cell. Adding a kind of drawable (a 3D snapshot, say) is a branch here and one
     * in [drawDrawable]. [free]: a cropping drawable (a photo) may take any crop within its range, else it keeps
     * the crop it has. Sizes are in the block's tile, so a block drawn bigger ([FlexBlock.size]) needs more room.
     */
    private fun drawableShape(c: Ctx, b: FlexBlock, ct: FlexContent): DrawableShape = when (b.type) {
        FlexBlockType.PHOTO -> {
            val bmp = photoBitmap(c, b, ct)
            val lim = c.limits
            val own = (if (b.photoRatio > 0f) b.photoRatio else bmp?.let { it.width.toFloat() / it.height } ?: 1f)
                .coerceIn(lim.range.start, lim.range.endInclusive)
            val circle = b.variant == 1
            val fill = b.photoRatio == 0f && !circle
            val lo = if (circle) 1f else if (fill) lim.range.start else own
            val hi = if (circle) 1f else if (fill) lim.range.endInclusive else own
            DrawableShape(bmp != null, if (circle) 1f else own, lo, hi, 0f, 0f, lim.maxWidth, lim.maxHeight, greedy = true, crops = fill)
        }
        FlexBlockType.MODEL -> {
            val bmp = photoBitmap(c, b, ct)
            val lim = c.limits
            val own = (if (b.photoRatio > 0f) b.photoRatio else bmp?.let { it.width.toFloat() / it.height } ?: 0.75f)
                .coerceIn(lim.range.start, lim.range.endInclusive)
            val panel = b.variant == 1
            val fill = b.photoRatio == 0f || panel
            val lo = if (fill) lim.range.start else own
            val hi = if (fill) lim.range.endInclusive else own
            DrawableShape(bmp != null, own, lo, hi, 0f, 0f, lim.maxWidth, lim.maxHeight, greedy = true, crops = fill && !panel)
        }
        FlexBlockType.CALENDAR -> {
            // A month: seven columns of day cells under its title; the cells stretch to the tile (within a sensible range
            // of shapes), the numbers keep their size, so it has a least size at which they still read.
            val cal = ct.calendar
            val k = scaleOf(c, b)
            val pad = c.panelPad()
            val weeks = cal?.weeks ?: 5
            val head = calendarHead(c, b, ct)
            val minW = (7 * CAL_CELL_MIN + 6 * CAL_GAP + 2 * pad) * k
            val minH = (head + weeks * CAL_CELL_MIN + (weeks - 1) * CAL_GAP + 2 * pad) * k
            val natW = 7 * CAL_CELL + 6 * CAL_GAP + 2 * pad
            val natH = head + weeks * CAL_CELL + (weeks - 1) * CAL_GAP + 2 * pad
            DrawableShape(cal != null && cal.days > 0, natW / natH, 0.7f, 2.2f, minW, minH, NO_CAP, max(CHART_MAX_H * 1.2f, minH), greedy = false, crops = false)
        }
        FlexBlockType.CHART -> {
            // A chart stretches to its cell (within a sensible range of shapes) instead of being fitted by text scaling;
            // its labels keep their size, so it has a least size at which they still read.
            val k = scaleOf(c, b)
            val pad = c.panelPad()
            val kicker = if (ct.chartLabel.isNotBlank()) c.lineH(c.kicker()) + 14f else 0f
            val labelW = if (ct.chartLabel.isNotBlank()) c.kicker().measureText(ct.chartLabel.uppercase()) else 0f
            val axisW = if (b.flag) CHART_AXIS_W else 0f
            val datesH = if (b.flag) CHART_DATES_H else 0f
            val minW = (max(axisW + CHART_PLOT_W, labelW) + 2 * pad) * k
            val minH = (kicker + CHART_PLOT_H + datesH + 2 * pad) * k
            DrawableShape(ct.chart.isNotEmpty(), CHART_RATIO, 0.9f, 3.5f, minW, minH, NO_CAP, max(CHART_MAX_H, minH), greedy = false, crops = false)
        }
        // Not a drawable: nothing to draw as one.
        else -> DrawableShape(false, 1f, 1f, 1f, 0f, 0f, NO_CAP, NO_CAP, greedy = false, crops = false)
    }

    /** A drawable in its tile, [w] by [h] in the block's own units (its [FlexBlock.size] is applied around this). */
    private fun drawDrawable(c: Ctx, b: FlexBlock, ct: FlexContent, w: Float, h: Float) {
        when (b.type) {
            FlexBlockType.PHOTO -> drawPhoto(c, b, ct, w, h)
            FlexBlockType.CHART -> chart(c, b, ct, w, h)
            FlexBlockType.CALENDAR -> calendar(c, b, ct, w, h)
            FlexBlockType.MODEL -> drawModel(c, b, ct, w, h)
            else -> Unit
        }
    }

    fun draw(
        canvas: Canvas,
        targetWidthPx: Float,
        spec: FlexSpec,
        content: FlexContent,
        assets: FlexAssets,
        plan: FlexPlan = plan(spec, content, assets),
        poses: FlexPoses? = null
    ) {
        val st = spec.style
        val w = plan.width
        val h = plan.height
        val outer = canvas.save()
        val scale = targetWidthPx / w
        canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, w, h)

        // Rounded card corners are cut out of a layer so the edge is smooth and the corners are transparent.
        val rounded = st.cardRadius > 0
        val layer = if (rounded) canvas.saveLayer(0f, 0f, w, h, null) else -1

        val ctx = Ctx(canvas, st, assets)
        drawBackground(ctx, w, h)
        drawContent(ctx, spec, content, plan, poses, w, h)
        drawBorder(ctx, w, h, plan)

        if (rounded) {
            val r = st.cardRadius.toFloat()
            val outside = Path().apply {
                addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW)
                fillType = Path.FillType.INVERSE_EVEN_ODD
            }
            val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
            canvas.drawPath(outside, clear)
            canvas.restoreToCount(layer)
        }
        canvas.restoreToCount(outer)
    }

    // -----------------------------------------------------------------------------------------
    // Drawing context
    // -----------------------------------------------------------------------------------------

    /** Wrapped lines, and how far left of its box they are drawn (when a word made them wider than the box). */
    private class Lines(val layout: StaticLayout, val shift: Float) {
        val height: Int get() = layout.height
    }

    private class Ctx(val canvas: Canvas, val st: FlexStyle, val assets: FlexAssets) {
        /** While true, blocks only measure. */
        var dry = false

        /**
         * Dry runs only: how much wider (in the units of the box being measured) the content must be for no word
         * to be broken and nothing to run into what sits beside it. 0 = it is fine. The caller resets it, measures,
         * and reads it.
         */
        var overflow = 0f

        /** How the block being drawn aligns its content across its tile. Set per block. */
        var align: FlexAlign = FlexAlign.START

        /** The number of columns the statistic being drawn takes (0 = the most that fit its width). Set per tile. */
        var columns: Int = 0
        /** While drawing a stretched tile: how tall it is, in the units of [block] (its panel is drawn that tall). */
        var fillTo: Float = 0f
        /** The same in the block's own content units; taken by the first panel drawn. */
        var target: Float = 0f
        val center: Boolean get() = align == FlexAlign.CENTER
        val right: Boolean get() = align == FlexAlign.END
        val limits: FlexPhotoLimit = st.photoLimit()

        /** The x a line of text is anchored at inside [x], [w]: left, middle or right edge, to go with [paintAlign]. */
        fun alignX(x: Float, w: Float): Float = when (align) {
            FlexAlign.START -> x
            FlexAlign.CENTER -> x + w / 2f
            FlexAlign.END -> x + w
        }

        fun paintAlign(): Paint.Align = when (align) {
            FlexAlign.START -> Paint.Align.LEFT
            FlexAlign.CENTER -> Paint.Align.CENTER
            FlexAlign.END -> Paint.Align.RIGHT
        }

        /** The left edge of something [d] wide placed in [x], [w]. */
        fun alignBox(x: Float, w: Float, d: Float): Float = when (align) {
            FlexAlign.START -> x
            FlexAlign.CENTER -> x + (w - d) / 2f
            FlexAlign.END -> x + w - d
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val paints = HashMap<String, TextPaint>()

        /** A cached text paint. [size] is in card pixels before the card's text scale is applied. */
        fun ink(size: Float, bold: Boolean, color: Int, alpha: Float = 1f, letter: Float = 0f): TextPaint {
            val key = "$size|$bold|$color|$alpha|$letter"
            return paints.getOrPut(key) {
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = size * st.textScale
                    typeface = st.font.face(bold)
                    this.color = color
                    this.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
                    letterSpacing = letter
                }
            }
        }

        fun kicker(): TextPaint = ink(28f, true, st.accent, 1f, 0.12f)

        fun lineH(p: Paint): Float = p.descent() - p.ascent()

        /**
         * Text wrapped in a box [width] wide, on whole words only: no hyphens, no ellipsis, no line limit (a tile that
         * is too small shrinks its content or is cut, it never drops words). A word is never broken: when the box is
         * narrower than the longest word, the text is laid out as wide as that word (and cut at the tile's edge),
         * and a dry run notes how much wider the box must be.
         */
        fun layout(s: CharSequence, p: TextPaint, width: Float, centered: Boolean, rightAligned: Boolean = right): Lines {
            var widest = 0f
            for (word in s.split(' ', '\n', '\t')) if (word.isNotEmpty()) widest = max(widest, p.measureText(word))
            if (dry && widest > width) overflow = max(overflow, widest - width)
            val lw = if (widest > width + 1f) width else max(width, widest + 0.5f)
            val l = StaticLayout.Builder.obtain(s, 0, s.length, p, ceil(lw).toInt().coerceAtLeast(1))
                .setAlignment(
                    when {
                        centered -> Layout.Alignment.ALIGN_CENTER
                        rightAligned -> Layout.Alignment.ALIGN_OPPOSITE
                        else -> Layout.Alignment.ALIGN_NORMAL
                    }
                )
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setIncludePad(false)
                .build()
            // A layout wider than its box overflows on the side its alignment points away from.
            val extra = ceil(lw) - width
            val shift = when {
                extra <= 0.5f -> 0f
                centered -> -extra / 2f
                rightAligned -> -extra
                else -> 0f
            }
            return Lines(l, shift)
        }

        fun drawLayout(l: Lines, x: Float, y: Float) {
            if (dry) return
            canvas.save()
            canvas.translate(x + l.shift, y)
            l.layout.draw(canvas)
            canvas.restore()
        }

        /** Wrapped text in a box; returns its height. */
        fun para(s: String, p: TextPaint, x: Float, y: Float, width: Float, centered: Boolean = center): Float {
            if (s.isBlank()) return 0f
            val l = layout(s, p, width, centered)
            drawLayout(l, x, y)
            return l.height.toFloat()
        }

        /** Content [w] wide that must sit beside something: notes, in a dry run, how much wider the box must be for it. */
        fun needs(w: Float, have: Float) {
            if (dry && w > have) overflow = max(overflow, w - have)
        }

        /** One line of text whose top edge is at [top]; [x] is its left, centre or right per [align]. */
        fun line(s: String, p: TextPaint, x: Float, top: Float, align: Paint.Align): Float {
            if (!dry) {
                p.textAlign = align
                canvas.drawText(s, x, top - p.ascent(), p)
                p.textAlign = Paint.Align.LEFT
            }
            return lineH(p)
        }

        fun rule(x: Float, y: Float, w: Float) {
            if (dry) return
            stroke.color = st.textColor
            stroke.alpha = 40
            stroke.strokeWidth = 2f
            canvas.drawLine(x, y, x + w, y, stroke)
        }

        fun measure(block: () -> Float): Float {
            val was = dry
            dry = true
            val h = block()
            dry = was
            return h
        }

        fun panelPad(): Float = if (st.panel == FlexPanel.NONE) 0f else 30f

        fun panelRect(x: Float, y: Float, w: Float, h: Float) {
            if (dry || st.panel == FlexPanel.NONE) return
            val r = RectF(x, y, x + w, y + h)
            val rad = min(st.panelRadius.toFloat(), min(w, h) / 2f)
            when (st.panel) {
                FlexPanel.FILLED -> {
                    if (st.panelFill.isPlain) {
                        fill.color = st.panelColor
                        fill.alpha = (st.panelAlpha * 255f).roundToInt().coerceIn(0, 255)
                        canvas.drawRoundRect(r, rad, rad, fill)
                    } else {
                        FillRender.drawRoundRect(canvas, st.panelFill, st.panelColor, r, rad, PATTERN_DENSITY, st.panelAlpha)
                    }
                }
                FlexPanel.OUTLINE -> if (st.panelBorderWidth <= 0 && st.panelBorderKind == BorderKind.OUTLINE) {
                    stroke.color = st.panelColor
                    stroke.alpha = 150
                    stroke.strokeWidth = 3f
                    canvas.drawRoundRect(r, rad, rad, stroke)
                }
                FlexPanel.GLASS -> {
                    fill.color = Color.WHITE
                    fill.alpha = 36
                    canvas.drawRoundRect(r, rad, rad, fill)
                    if (st.panelBorderKind == BorderKind.OUTLINE) {
                        stroke.color = Color.WHITE
                        stroke.alpha = 70
                        stroke.strokeWidth = 2f
                        canvas.drawRoundRect(r, rad, rad, stroke)
                    }
                }
                FlexPanel.NONE -> Unit
            }
            if (st.panelBorderWidth > 0 && st.panelBorderKind == BorderKind.OUTLINE) {
                val half = st.panelBorderWidth / 2f
                stroke.color = st.panelBorderColor
                stroke.alpha = 255
                stroke.strokeWidth = st.panelBorderWidth.toFloat()
                canvas.drawRoundRect(
                    RectF(r.left + half, r.top + half, r.right - half, r.bottom - half),
                    max(0f, rad - half), max(0f, rad - half), stroke
                )
            }
        }

        /**
         * Draws a panel around [inner] (which gets the padded box and returns its height) and returns the
         * panel's height, at least [minH]. With panels off it just calls [inner] without padding.
         */
        fun panel(x: Float, y: Float, w: Float, minH: Float = 0f, inner: (Float, Float, Float) -> Float): Float {
            val pad = panelPad()
            // A stretched tile's panel (the outermost one) is as tall as the tile.
            val stretchTo = target
            target = 0f
            val innerH = measure { inner(x + pad, y + pad, w - 2 * pad) }
            val total = max(max(innerH + 2 * pad, minH), stretchTo)
            if (!dry) {
                panelRect(x, y, w, total)
                inner(x + pad, y + pad, w - 2 * pad)
            }
            return total
        }

        /**
         * Crops [bmp] to fill [dst], with rounded corners of [radius]. [zoom] 1 to 3 crops in further; [fx] and
         * [fy] (0 to 1, 0.5 = centre) say where in the picture the crop sits.
         */
        fun image(bmp: Bitmap, dst: RectF, radius: Float, zoom: Float = 1f, fx: Float = 0.5f, fy: Float = 0.5f) {
            if (dry || dst.width() <= 0f || dst.height() <= 0f) return
            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()
            val dstAspect = dst.width() / dst.height()
            // The biggest crop of the picture with the shape of [dst], then zoomed in, then placed.
            val fullW = if (bw / bh > dstAspect) bh * dstAspect else bw
            val fullH = if (bw / bh > dstAspect) bh else bw / dstAspect
            val z = zoom.coerceIn(1f, 3f)
            val sw = fullW / z
            val sh = fullH / z
            val left = (bw - sw) * fx.coerceIn(0f, 1f)
            val top = (bh - sh) * fy.coerceIn(0f, 1f)
            val src = Rect(left.toInt(), top.toInt(), (left + sw).toInt(), (top + sh).toInt())
            val save = canvas.save()
            if (radius > 0f) {
                val clip = Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) }
                canvas.clipPath(clip)
            }
            canvas.drawBitmap(bmp, src, dst, bitmapPaint)
            canvas.restoreToCount(save)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Background, border, overall layout
    // -----------------------------------------------------------------------------------------

    private fun drawBackground(c: Ctx, w: Float, h: Float) {
        val st = c.st
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val photo = c.assets.background
        when {
            st.bgMode == FlexBgMode.PHOTO && photo != null -> {
                val scale = max(w / photo.width, h / photo.height)
                val dw = photo.width * scale
                val dh = photo.height * scale
                val left = (w - dw) / 2f
                val top = (h - dh) / 2f
                paint.isFilterBitmap = true
                c.canvas.drawBitmap(photo, null, RectF(left, top, left + dw, top + dh), paint)
                paint.color = Color.BLACK
                paint.alpha = (st.photoDim * 255f).roundToInt().coerceIn(0, 255)
                c.canvas.drawRect(0f, 0f, w, h, paint)
            }
            else -> {
                // Solid or gradient (two colours or advanced), with the pattern over it.
                FillRender.drawRoundRect(c.canvas, st.backgroundFill(), st.bgA, RectF(0f, 0f, w, h), 0f, PATTERN_DENSITY)
            }
        }
    }

    private fun drawRule(c: Ctx, x: Float, y: Float, length: Float, thickness: Float, color: Int, vertical: Boolean) {
        if (length <= 0f || thickness <= 0f) return
        val pts = c.st.lineTaper.outline(x, y, length, thickness, vertical)
        val path = Path()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        path.close()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        c.canvas.drawPath(path, paint)
    }

    private const val SEPARATOR_DEFAULT = 3f

    private fun drawSeparators(c: Ctx, plan: FlexPlan) {
        val st = c.st
        if (st.panelBorderKind != BorderKind.LINE) return
        val t = if (st.panelBorderWidth > 0) st.panelBorderWidth.toFloat() else SEPARATOR_DEFAULT
        val color = st.panelBorderColor
        if (plan.mode == FlexLayoutMode.FREE) {
            val lowest = plan.boxes.maxOfOrNull { it.cellTop + it.cellHeight } ?: return
            for (b in plan.boxes) {
                val bottom = b.cellTop + b.cellHeight
                if (bottom >= lowest - 0.5f) continue
                drawRule(c, b.cellLeft, bottom + plan.gap / 2f - t / 2f, b.cellWidth, t, color, false)
            }
            return
        }
        for (d in plan.rules) {
            if (d.span <= 0f) continue
            if (d.vertical) drawRule(c, d.cx - d.span / 2f, d.cy - t / 2f, d.span, t, color, false)
            else drawRule(c, d.cx - t / 2f, d.cy - d.span / 2f, d.span, t, color, true)
        }
    }

    private fun drawBorder(c: Ctx, w: Float, h: Float, plan: FlexPlan) {
        val st = c.st
        if (st.borderWidth <= 0) return
        if (st.borderKind == BorderKind.LINE) {
            val t = st.borderWidth.toFloat()
            val padV = st.paddingV.toFloat()
            val bottom = plan.contentTop + plan.contentHeight
            drawRule(c, plan.contentLeft, padV / 2f - t / 2f, plan.contentWidth, t, st.borderColor, false)
            drawRule(c, plan.contentLeft, bottom + padV / 2f - t / 2f, plan.contentWidth, t, st.borderColor, false)
            return
        }
        val half = st.borderWidth / 2f
        c.stroke.color = st.borderColor
        c.stroke.alpha = 255
        c.stroke.strokeWidth = st.borderWidth.toFloat()
        val r = max(0f, st.cardRadius - half)
        c.canvas.drawRoundRect(RectF(half, half, w - half, h - half), r, r, c.stroke)
    }

    private fun drawContent(c: Ctx, spec: FlexSpec, content: FlexContent, plan: FlexPlan, poses: FlexPoses?, w: Float, h: Float) {
        val st = spec.style
        val byId = spec.blocks.associateBy { it.id }
        val heldId = poses?.heldId
        c.dry = false
        drawSeparators(c, plan)
        for (box in plan.boxes) {
            val b = byId[box.id] ?: continue
            // Gliding to a new place: drawn that far from it.
            val o = poses?.offsets?.get(box.id)
            val left = box.left + (o?.dx ?: 0f)
            val top = box.top + (o?.dy ?: 0f)
            if (box.id == heldId) {
                // Floats above, drawn last. Here: where it will land, faint, or only the gap it leaves.
                val landing = (poses?.landing ?: 0f).coerceIn(0f, 1f)
                if (landing > 0.01f) {
                    val layer = c.canvas.saveLayerAlpha(left, top, left + box.width, top + box.height, (landing * 255).roundToInt())
                    drawTile(c, b, content, box, left, top)
                    c.canvas.restoreToCount(layer)
                }
                continue
            }
            drawTile(c, b, content, box, left, top)
        }
        if (poses != null && heldId != null) drawHeld(c, content, plan, poses, byId[heldId])

        if (st.watermark && st.watermarkText.isNotBlank()) {
            val pad = st.padding.toFloat()
            val p = c.ink(26f, true, st.textColor, 0.5f, 0.1f)
            val baseline = h - max(st.paddingV * 0.55f, 40f)
            p.textAlign = when (st.watermarkAlign) {
                FlexAlign.START -> Paint.Align.LEFT
                FlexAlign.CENTER -> Paint.Align.CENTER
                FlexAlign.END -> Paint.Align.RIGHT
            }
            val x = when (st.watermarkAlign) {
                FlexAlign.START -> pad
                FlexAlign.CENTER -> w / 2f
                FlexAlign.END -> w - pad
            }
            c.canvas.drawText(st.watermarkText, x, baseline, p)
            p.textAlign = Paint.Align.LEFT
        }
    }

    /**
     * One block in its tile, by its class. Text-like content is drawn at the tile's scale ([FlexBlockBox.scale],
     * below 1 only when it would not fit), under a title's air, placed in the tile by the block's own alignment; a
     * drawable takes the whole tile. Everything is cut off at the tile's edge, so nothing ever spills out of it.
     */
    private fun drawTile(c: Ctx, b: FlexBlock, ct: FlexContent, box: FlexBlockBox, left: Float, top: Float, grow: Float = 1f) {
        c.align = b.hAlign
        val canvas = c.canvas
        canvas.save()
        canvas.translate(left, top)
        if (grow != 1f) canvas.scale(grow, grow)
        canvas.clipRect(0f, 0f, box.width, box.height)
        if (b.type.blockClass == FlexBlockClass.DRAWABLE) {
            // The block's size scales what is drawn in the tile (a chart's labels), not the tile.
            val k = scaleOf(c, b)
            if (k != 1f) canvas.scale(k, k)
            drawDrawable(c, b, ct, box.width / k, box.height / k)
        } else {
            val room = box.height - box.inset
            val oy = box.inset + if (box.stretched) 0f else when (b.vAlign) {
                FlexVAlign.TOP -> 0f
                FlexVAlign.CENTER -> (room - box.drawn) / 2f
                FlexVAlign.BOTTOM -> room - box.drawn
            }.coerceAtLeast(0f)
            canvas.translate(0f, oy)
            canvas.scale(box.scale, box.scale)
            c.columns = box.columns
            // A stretched tile: its panel is drawn as tall as the tile (see FlexBlockBox.stretched).
            c.fillTo = if (box.stretched) room / box.scale else 0f
            block(c, b, ct, 0f, 0f, box.width / box.scale)
            c.fillTo = 0f
            c.target = 0f
            c.columns = 0
        }
        canvas.restore()
    }

    /** A photo is its own tile: the image fills it. */
    private fun drawPhoto(c: Ctx, b: FlexBlock, ct: FlexContent, w: Float, h: Float) {
        val bmp = photoBitmap(c, b, ct) ?: return
        val radius = when (b.variant) {
            1 -> min(w, h) / 2f
            2 -> 0f
            else -> max(c.st.panelRadius.toFloat(), 24f)
        }
        c.image(bmp, RectF(0f, 0f, w, h), radius, b.photoZoom, b.photoX, b.photoY)
    }

    /** The 3D model's snapshot: cropped to fill its tile like a photo, or shown whole on a panel ("On a panel"). */
    private fun drawModel(c: Ctx, b: FlexBlock, ct: FlexContent, w: Float, h: Float) {
        val bmp = photoBitmap(c, b, ct) ?: return
        val radius = max(c.st.panelRadius.toFloat(), 24f)
        if (b.variant != 1) {
            c.image(bmp, RectF(0f, 0f, w, h), radius, b.photoZoom, b.photoX, b.photoY)
            return
        }
        c.panelRect(0f, 0f, w, h)
        val pad = max(c.panelPad(), 12f)
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val k = min((w - 2 * pad) / bw, (h - 2 * pad) / bh).coerceAtLeast(0.01f)
        val dw = bw * k
        val dh = bh * k
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        c.image(bmp, RectF(left, top, left + dw, top + dh), 0f)
    }

    // ---- Calendar ----

    /** How tall a calendar's lines above its days are (label, month, weekdays), in the block's own units. */
    private fun calendarHead(c: Ctx, b: FlexBlock, ct: FlexContent): Float {
        val cal = ct.calendar ?: return 0f
        var h = 0f
        if (cal.label.isNotBlank()) h += c.lineH(c.kicker()) + 10f
        h += c.lineH(c.ink(44f, true, c.st.textColor)) + 14f
        if (b.flag) h += c.lineH(c.ink(22f, true, c.st.textColor, 0.6f)) + 8f
        return h
    }

    /**
     * A month in its whole tile, [w] by [h] (block units): the label and month above, the weekdays, then the days in seven
     * columns. "Month": every day numbered, the marked ones on an accent disc, today ringed. "Dots": a dot per day, the
     * marked ones in the accent colour, like a habit heatmap.
     */
    private fun calendar(c: Ctx, b: FlexBlock, ct: FlexContent, w: Float, h: Float) {
        val cal = ct.calendar ?: return
        if (c.dry) return
        // A picture sits in the middle of its tile unless set to the right (as its tile does in its cell).
        if (c.align == FlexAlign.START) c.align = FlexAlign.CENTER
        val st = c.st
        val pad = c.panelPad()
        c.panelRect(0f, 0f, w, h)
        val ix = pad
        val iw = w - 2 * pad
        var top = pad
        if (cal.label.isNotBlank()) top += aligned(c, cal.label.uppercase(), c.kicker(), ix, iw, top) + 10f
        top += aligned(c, cal.title, c.ink(44f, true, st.textColor), ix, iw, top) + 14f
        val gx = CAL_GAP
        val weeks = cal.weeks
        val weekdays = if (b.flag) c.ink(22f, true, st.textColor, 0.6f) else null
        val gridTop = top + (weekdays?.let { c.lineH(it) + 8f } ?: 0f)
        val cellH = ((h - pad - gridTop - (weeks - 1) * gx) / weeks).coerceAtLeast(1f)
        // Day cells no wider than they are tall by much: a wide tile puts the month in its middle instead of
        // stretching the days apart.
        val cellW = min(((iw - 6 * gx) / 7f).coerceAtLeast(1f), cellH * 1.5f)
        val gridX = c.alignBox(ix, iw, 7 * cellW + 6 * gx)
        if (weekdays != null) {
            val names = listOf("M", "T", "W", "T", "F", "S", "S")
            for (i in 0 until 7) c.line(names[i], weekdays, gridX + i * (cellW + gx) + cellW / 2f, top, Paint.Align.CENTER)
        }
        top = gridTop
        val dot = min(cellW, cellH)
        val numP = c.ink((dot * 0.42f).coerceIn(14f, 40f), true, st.textColor)
        val onP = c.ink((dot * 0.42f).coerceIn(14f, 40f), true, st.bgA)
        for (d in 1..cal.days) {
            val slot = cal.firstWeekday + d - 1
            val col = slot % 7
            val row = slot / 7
            val cx = gridX + col * (cellW + gx) + cellW / 2f
            val cy = top + row * (cellH + gx) + cellH / 2f
            val marked = d in cal.marked
            if (b.variant == 1) {
                val r = dot * 0.34f
                c.fill.color = if (marked) st.accent else st.textColor
                c.fill.alpha = if (marked) 255 else 40
                c.canvas.drawCircle(cx, cy, r, c.fill)
            } else {
                val r = dot * 0.46f
                if (marked) {
                    c.fill.color = st.accent
                    c.fill.alpha = 255
                    c.canvas.drawCircle(cx, cy, r, c.fill)
                }
                val p = if (marked) onP else numP
                c.line(d.toString(), p, cx, cy - c.lineH(p) / 2f, Paint.Align.CENTER)
            }
            if (d == cal.today) {
                c.stroke.color = st.textColor
                c.stroke.alpha = 200
                c.stroke.strokeWidth = 3f
                c.canvas.drawCircle(cx, cy, dot * 0.5f - 1.5f, c.stroke)
            }
        }
    }

    /** The block being dragged, drawn on top where the finger has it: slightly bigger, with a soft shadow. */
    private fun drawHeld(c: Ctx, content: FlexContent, plan: FlexPlan, poses: FlexPoses, b: FlexBlock?) {
        if (b == null) return
        val box = poses.heldBox ?: plan.boxes.firstOrNull { it.id == b.id } ?: return
        val lift = poses.lift.coerceIn(0f, 1f)
        val grow = 1f + 0.04f * lift
        val left = poses.heldLeft - box.width * (grow - 1f) / 2f
        val top = poses.heldTop - box.height * (grow - 1f) / 2f
        if (lift > 0.01f) {
            val radius = c.st.panelRadius.toFloat()
            for (i in 4 downTo 1) {
                val spread = i * 8f * lift
                val drop = 14f * lift
                c.fill.color = Color.BLACK
                c.fill.alpha = (16f * lift).roundToInt()
                c.canvas.drawRoundRect(
                    RectF(left - spread, top - spread + drop, left + box.width * grow + spread, top + box.height * grow + spread + drop),
                    radius + spread, radius + spread, c.fill
                )
            }
        }
        drawTile(c, b, content, box, left, top, grow)
    }

    private fun stretches(st: FlexStyle, b: FlexBlock): Boolean = st.panel != FlexPanel.NONE &&
        (b.type.blockClass == FlexBlockClass.STATISTIC || b.type.blockClass == FlexBlockClass.TEXT && panelled(b))

    /** Text blocks drawn on a panel (whose panel can stretch to its tile): the others are bare text. */
    private fun panelled(b: FlexBlock): Boolean = when (b.type) {
        FlexBlockType.EXERCISES, FlexBlockType.ROUTINE -> true
        FlexBlockType.DAY_NOTE -> b.variant != 1
        else -> false
    }

    /** How big a block's content is drawn before any fitting: its own size on top of the standard module scale. */
    private fun scaleOf(c: Ctx, b: FlexBlock): Float = b.size * FLEX_MODULE_SCALE * c.st.moduleScale

    /**
     * A block, at its own size ([scaleOf]): everything inside scales together, so the box it takes
     * grows or shrinks with it and the card lays out around it.
     */
    private fun block(c: Ctx, b: FlexBlock, ct: FlexContent, x: Float, y: Float, w: Float): Float {
        val k = scaleOf(c, b)
        c.target = if (c.fillTo > 0f) c.fillTo / k else 0f
        if (k == 1f) return blockContent(c, b, ct, x, y, w)
        if (c.dry) return blockContent(c, b, ct, 0f, 0f, w / k) * k
        c.canvas.save()
        c.canvas.translate(x, y)
        c.canvas.scale(k, k)
        val h = blockContent(c, b, ct, 0f, 0f, w / k)
        c.canvas.restore()
        return h * k
    }

    private fun blockContent(c: Ctx, b: FlexBlock, ct: FlexContent, x: Float, y: Float, w: Float): Float = when (b.type.blockClass) {
        FlexBlockClass.STATISTIC -> statBlock(c, b, ct)?.let { statDraw(c, it, x, y, w) } ?: 0f
        FlexBlockClass.DRAWABLE -> 0f   // drawn by drawDrawable, into its tile
        FlexBlockClass.TITLE, FlexBlockClass.TEXT -> when (b.type) {
            FlexBlockType.HEADER -> header(c, b, ct, x, y, w)
            FlexBlockType.EXERCISES -> exercises(c, b, ct, x, y, w)
            FlexBlockType.ROUTINE -> routine(c, b, ct, x, y, w)
            FlexBlockType.DAY_NOTE -> dayNote(c, b, ct, x, y, w)
            FlexBlockType.TEXT -> textBlock(c, b, x, y, w)
            else -> 0f
        }
    }

    // -----------------------------------------------------------------------------------------
    // Small helpers
    // -----------------------------------------------------------------------------------------

    private fun <T> pick(all: List<T>, keys: List<String>?, limit: Int, keyOf: (T) -> String): List<T> {
        if (keys == null) return all.take(limit.coerceAtLeast(1))
        val chosen = all.filter { keyOf(it) in keys }
        return chosen.ifEmpty { all.take(limit.coerceAtLeast(1)) }
    }

    private fun fmtDate(ms: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))
    private fun fmtDay(ms: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))
    private fun one(v: Double): String = formatWeight((v * 10.0).roundToLong() / 10.0)
    private fun setText(l: FlexLiftItem): String = "${formatWeight(l.weightKg)} kg × ${l.reps}"
    /** What an estimated 1RM was worked out from, e.g. "Calculated from 100 kg × 5 @ RPE 9". */
    private fun calcFrom(l: FlexLiftItem): String = "Calculated from ${calcSet(l)}"
    private fun calcSet(l: FlexLiftItem): String = setText(l) + (l.rpe?.let { " @ RPE ${formatWeight(it)}" } ?: "")
    private fun liftMeta(l: FlexLiftItem): String =
        listOfNotNull(l.rpe?.let { "RPE ${formatWeight(it)}" }, fmtDate(l.dateMs)).joinToString(" · ")

    /** One line [s] in [p] at [top], placed in [x], [w] by the block's alignment; returns its height. */
    private fun aligned(c: Ctx, s: String, p: TextPaint, x: Float, w: Float, top: Float): Float =
        c.line(s, p, c.alignX(x, w), top, c.paintAlign())

    /**
     * How wide a big number with a small unit after it is at least. The number gets the room of [reference] (the
     * widest value that kind of number normally takes), so every value of one kind is given the same room however
     * many digits it has; with the block scaled as one unit, they are all drawn at the same size.
     */
    private fun numberUnitWidth(c: Ctx, num: String, unit: String, size: Float, reference: String): Float {
        val numP = c.ink(size, true, c.st.accent)
        val unitP = c.ink(30f, false, c.st.textColor, 0.7f)
        return max(numP.measureText(num), numP.measureText(reference)) + unitP.measureText(unit) + 12f
    }

    /** A big number with a small unit after it, sharing a baseline, placed per the block's alignment; returns its height. */
    private fun numberWithUnit(c: Ctx, x: Float, w: Float, y: Float, num: String, unit: String, size: Float): Float {
        val st = c.st
        val unitP = c.ink(30f, false, st.textColor, 0.7f)
        val numP = c.ink(size, true, st.accent)
        val numW = numP.measureText(num)
        val unitW = unitP.measureText(unit) + 12f
        val startX = c.alignBox(x, w, numW + unitW)
        if (!c.dry) {
            val baseline = y - numP.ascent()
            c.canvas.drawText(num, startX, baseline, numP)
            c.canvas.drawText(unit, startX + numW + 12f, baseline, unitP)
        }
        return c.lineH(numP)
    }

    // -----------------------------------------------------------------------------------------
    // Statistics: single lines in a grid that reflows, scaled as one unit
    // -----------------------------------------------------------------------------------------

    /** One cell of a statistic: single lines only, so how wide it must be and how tall it is do not depend on where it goes. */
    private class StatCell(val minW: Float, val h: Float, val draw: (x: Float, y: Float, w: Float, h: Float) -> Unit)

    /**
     * A statistic block: an optional [heading] line, then [cells] in rows of up to [designed] columns.
     * Grid: the columns share the width equally and a row is as tall as its tallest cell; each cell has its own panel
     * ([cellPanels]), or the whole block shares one ([outerPanel]) with a rule between rows ([rules]).
     * Flow ([separator] set): cells keep their own widths and the cells of a row are joined by the separator, like a
     * sentence that may break only between items.
     * Nothing in it ever wraps; fewer columns (a reflow) is how it gets narrower, then it shrinks as a whole.
     */
    private class StatBlock(
        val cells: List<StatCell>,
        val designed: Int,
        val heading: StatCell? = null,
        val headGap: Float = 14f,
        val gapX: Float = 16f,
        val gapY: Float = 16f,
        val cellPanels: Boolean = true,
        val outerPanel: Boolean = false,
        val rules: Boolean = false,
        val separator: String? = null,
        val sepPaint: TextPaint? = null
    )

    /** The column counts a statistic can take, the one it is designed with first. */
    private fun statColumns(sb: StatBlock): IntArray {
        val most = sb.designed.coerceIn(1, max(1, sb.cells.size))
        return IntArray(most) { most - it }
    }

    /** How wide [sb] must be at least and how tall it is, with [cols] columns, at scale 1. */
    private fun statSize(c: Ctx, sb: StatBlock, cols: Int): FloatArray {
        val pad = c.panelPad()
        val cellPad = if (sb.cellPanels) pad else 0f
        val outer = if (sb.outerPanel) pad else 0f
        val rows = sb.cells.chunked(cols)
        var inner = 0f
        val sep = sb.separator
        if (sep != null) {
            val sepW = sb.sepPaint?.measureText(sep) ?: 0f
            for (row in rows) inner = max(inner, row.sumOf { it.minW.toDouble() }.toFloat() + sepW * (row.size - 1))
        } else {
            val cellW = (sb.cells.maxOfOrNull { it.minW } ?: 0f) + 2 * cellPad
            inner = cellW * cols + sb.gapX * (cols - 1)
        }
        var h = 0f
        sb.heading?.let {
            inner = max(inner, it.minW)
            h += it.h + sb.headGap
        }
        rows.forEachIndexed { r, row ->
            if (r > 0) h += sb.gapY
            h += row.maxOf { it.h } + 2 * cellPad
        }
        return floatArrayOf(inner + 2 * outer, h + 2 * outer)
    }

    /** A statistic's layouts as the fits see them (see [StatShape]), at the block's own size. Null when it has nothing to show. */
    private fun statShape(c: Ctx, b: FlexBlock, ct: FlexContent): StatShape? {
        val sb = statBlock(c, b, ct) ?: return null
        val cols = statColumns(sb)
        val k = scaleOf(c, b)
        val sizes = cols.map { statSize(c, sb, it) }
        // A flow has no designed column count to keep: it is a sentence that breaks where it must.
        val designed = if (sb.separator != null) 1 else cols[0]
        return StatShape(cols, FloatArray(cols.size) { sizes[it][0] * k }, FloatArray(cols.size) { sizes[it][1] * k }, designed, FlexTileRules.reflowKeep)
    }

    /**
     * Draws [sb] [w] wide in the columns the tile took ([Ctx.columns]), or, when measuring, the most columns that fit
     * [w]; returns its height.
     */
    private fun statDraw(c: Ctx, sb: StatBlock, x: Float, y: Float, w: Float): Float {
        val options = statColumns(sb)
        val cols = if (c.columns > 0) c.columns.coerceIn(1, options[0])
        else options.firstOrNull { statSize(c, sb, it)[0] <= w + 0.5f } ?: 1
        val size = statSize(c, sb, cols)
        if (c.dry) return size[1]
        // A stretched tile: the outer panel is as tall as the tile, or each row of cell panels gets its share of the room.
        val tall = max(size[1], c.target)
        c.target = 0f
        val rows = (sb.cells.size + cols - 1) / cols
        val extraRow = if (!sb.outerPanel && sb.cellPanels && rows > 0) (tall - size[1]) / rows else 0f
        val pad = c.panelPad()
        val cellPad = if (sb.cellPanels) pad else 0f
        val outer = if (sb.outerPanel) pad else 0f
        if (sb.outerPanel) c.panelRect(x, y, w, tall)
        val ix = x + outer
        val iw = w - 2 * outer
        var cy = y + outer
        sb.heading?.let {
            it.draw(ix, cy, iw, it.h)
            cy += it.h + sb.headGap
        }
        val sep = sb.separator
        val sepP = sb.sepPaint
        val sepW = if (sep != null && sepP != null) sepP.measureText(sep) else 0f
        sb.cells.chunked(cols).forEachIndexed { r, row ->
            if (r > 0) {
                if (sb.rules) c.rule(ix, cy + sb.gapY / 2f, iw)
                cy += sb.gapY
            }
            val rowH = row.maxOf { it.h } + 2 * cellPad + extraRow
            if (sep != null && sepP != null) {
                val rowW = row.sumOf { it.minW.toDouble() }.toFloat() + sepW * (row.size - 1)
                var cx = c.alignBox(ix, iw, rowW)
                row.forEachIndexed { i, cell ->
                    if (i > 0) {
                        c.line(sep, sepP, cx, cy + (rowH - c.lineH(sepP)) / 2f, Paint.Align.LEFT)
                        cx += sepW
                    }
                    cell.draw(cx, cy + (rowH - cell.h) / 2f, cell.minW, cell.h)
                    cx += cell.minW
                }
            } else {
                val cw = (iw - sb.gapX * (cols - 1)) / cols
                row.forEachIndexed { i, cell ->
                    val cx = ix + i * (cw + sb.gapX)
                    if (sb.cellPanels) c.panelRect(cx, cy, cw, rowH)
                    cell.draw(cx + cellPad, cy + cellPad, cw - 2 * cellPad, rowH - 2 * cellPad)
                }
            }
            cy += rowH
        }
        return size[1]
    }

    /** What a statistic block shows, as cells. Null when there is nothing to show. */
    private fun statBlock(c: Ctx, b: FlexBlock, ct: FlexContent): StatBlock? = when (b.type) {
        FlexBlockType.STREAKS -> streaks(c, b, ct)
        FlexBlockType.TOP_SET -> topSet(c, b, ct)
        FlexBlockType.ONE_RM -> oneRm(c, b, ct)
        FlexBlockType.SESSION_STATS -> sessionStats(c, b, ct)
        else -> null
    }

    /** A kicker line above the cells, e.g. "TOP SETS". */
    private fun headingCell(c: Ctx, text: String): StatCell {
        val p = c.kicker()
        return StatCell(p.measureText(text), c.lineH(p)) { x, y, w, _ -> aligned(c, text, p, x, w, y) }
    }

    /** A row of a list: a name (and a detail under it) on the left, a value on the right. */
    private fun liftRow(c: Ctx, name: String, meta: String?, right: String, rightSize: Float): StatCell {
        val st = c.st
        val nameP = c.ink(36f, true, st.textColor)
        val metaP = c.ink(26f, false, st.textColor, 0.65f)
        val rightP = c.ink(rightSize, true, st.accent)
        val textW = max(nameP.measureText(name), meta?.let { metaP.measureText(it) } ?: 0f)
        val textH = c.lineH(nameP) + (if (meta != null) c.lineH(metaP) + 4f else 0f)
        val rowH = max(textH, c.lineH(rightP))
        return StatCell(textW + 24f + rightP.measureText(right), rowH) { x, y, w, h ->
            val ty = y + (h - textH) / 2f
            c.line(name, nameP, x, ty, Paint.Align.LEFT)
            if (meta != null) c.line(meta, metaP, x, ty + c.lineH(nameP) + 4f, Paint.Align.LEFT)
            c.line(right, rightP, x + w, y + (h - c.lineH(rightP)) / 2f, Paint.Align.RIGHT)
        }
    }

    // ---- Streaks ----

    private fun streaks(c: Ctx, b: FlexBlock, ct: FlexContent): StatBlock? {
        val items = pick(ct.streaks, b.keys, b.limit) { FlexContent.streakKey(it) }
        if (items.isEmpty()) return null
        return when (b.variant) {
            2 -> StatBlock(listOf(streakHero(c, b, items.first())), 1)
            1 -> StatBlock(items.map { streakTile(c, b, it) }, 2)
            else -> StatBlock(items.map { streakCard(c, b, it) }, 1)
        }
    }

    private fun streakCard(c: Ctx, b: FlexBlock, s: FlexStreakItem): StatCell {
        val st = c.st
        val logo = s.logoUri?.let { c.assets.images[it] }
        val avatar = if (logo != null) 96f else 0f
        val gapX = if (logo != null) 24f else 0f
        val numP = c.ink(80f, true, st.accent)
        val unitP = c.ink(28f, false, st.textColor, 0.7f)
        val nameP = c.ink(40f, true, st.textColor)
        val subP = c.ink(26f, false, st.textColor, 0.65f)
        val num = s.days.toString()
        val unit = if (s.days == 1) "day" else "days"
        val sub = if (b.flag && s.longest > 0) "Longest run: ${s.longest} days" else null
        val rightW = max(max(numP.measureText(num), numP.measureText("000")), unitP.measureText(unit))
        val textW = max(nameP.measureText(s.name), sub?.let { subP.measureText(it) } ?: 0f)
        val textH = c.lineH(nameP) + (if (sub != null) c.lineH(subP) + 6f else 0f)
        val rightH = c.lineH(numP) + c.lineH(unitP) - 10f
        return StatCell(avatar + gapX + textW + 28f + rightW, max(max(textH, avatar), rightH)) { x, y, w, h ->
            val tx = x + avatar + gapX
            val ty = y + (h - textH) / 2f
            c.line(s.name, nameP, tx, ty, Paint.Align.LEFT)
            if (sub != null) c.line(sub, subP, tx, ty + c.lineH(nameP) + 6f, Paint.Align.LEFT)
            if (logo != null) {
                val top = y + (h - avatar) / 2f
                c.image(logo, RectF(x, top, x + avatar, top + avatar), avatar / 2f)
            }
            val rTop = y + (h - rightH) / 2f
            c.line(num, numP, x + w, rTop, Paint.Align.RIGHT)
            c.line(unit, unitP, x + w, rTop + c.lineH(numP) - 10f, Paint.Align.RIGHT)
        }
    }

    private fun streakTile(c: Ctx, b: FlexBlock, s: FlexStreakItem): StatCell {
        val st = c.st
        val nameP = c.ink(32f, true, st.textColor, 0.85f)
        val numP = c.ink(110f, true, st.accent)
        val longP = c.ink(24f, false, st.textColor, 0.65f)
        val num = s.days.toString()
        val unit = if (s.days == 1) "day" else "days"
        val longest = if (b.flag && s.longest > 0) "Longest ${s.longest}" else null
        val minW = maxOf(nameP.measureText(s.name), numberUnitWidth(c, num, unit, 110f, "000"), longest?.let { longP.measureText(it) } ?: 0f)
        val h = c.lineH(nameP) + 8f + c.lineH(numP) + (if (longest != null) 6f + c.lineH(longP) else 0f)
        return StatCell(minW, h) { x, y, w, _ ->
            var cy = y
            cy += aligned(c, s.name, nameP, x, w, cy) + 8f
            cy += numberWithUnit(c, x, w, cy, num, unit, 110f)
            if (longest != null) aligned(c, longest, longP, x, w, cy + 6f)
        }
    }

    private fun streakHero(c: Ctx, b: FlexBlock, s: FlexStreakItem): StatCell {
        val st = c.st
        val name = s.name.uppercase()
        val nameP = c.ink(34f, true, st.accent, 1f, 0.08f)
        val num = s.days.toString()
        val numP = c.ink(300f, true, st.accent)
        val days = if (s.days == 1) "DAY" else "DAYS"
        val daysP = c.ink(44f, true, st.textColor, 0.8f, 0.2f)
        val longest = if (b.flag && s.longest > 0) "Longest run: ${s.longest} days" else null
        val longP = c.ink(30f, false, st.textColor, 0.65f)
        val minW = maxOf(
            nameP.measureText(name), max(numP.measureText(num), numP.measureText("000")),
            daysP.measureText(days), longest?.let { longP.measureText(it) } ?: 0f
        )
        val h = c.lineH(nameP) + 4f + c.lineH(numP) + c.lineH(daysP) + (if (longest != null) 10f + c.lineH(longP) else 0f)
        return StatCell(minW, h) { x, y, w, _ ->
            var cy = y
            cy += aligned(c, name, nameP, x, w, cy) + 4f
            cy += aligned(c, num, numP, x, w, cy)
            cy += aligned(c, days, daysP, x, w, cy)
            if (longest != null) aligned(c, longest, longP, x, w, cy + 10f)
        }
    }

    // ---- Top set ----

    private fun topSet(c: Ctx, b: FlexBlock, ct: FlexContent): StatBlock? {
        val items = pick(ct.lifts, b.keys, b.limit) { it.name }
        if (items.isEmpty()) return null
        if (b.variant == 0) return StatBlock(listOf(topSetHero(c, b, items.first())), 1)
        return StatBlock(
            items.map { liftRow(c, it.name, if (b.flag) liftMeta(it) else null, setText(it), 42f) }, 1,
            heading = headingCell(c, "TOP SETS"), gapY = 28f, cellPanels = false, outerPanel = true, rules = true
        )
    }

    private fun topSetHero(c: Ctx, b: FlexBlock, l: FlexLiftItem): StatCell {
        val st = c.st
        val kickerP = c.kicker()
        val nameP = c.ink(40f, true, st.textColor, 0.9f)
        val big = setText(l)
        val bigP = c.ink(130f, true, st.accent)
        val meta = if (b.flag) liftMeta(l) else null
        val metaP = c.ink(30f, false, st.textColor, 0.7f)
        val minW = maxOf(
            kickerP.measureText("TOP SET"), nameP.measureText(l.name),
            max(bigP.measureText(big), bigP.measureText("000.0 kg × 00")), meta?.let { metaP.measureText(it) } ?: 0f
        )
        val h = c.lineH(kickerP) + 8f + c.lineH(nameP) + 6f + c.lineH(bigP) + (if (meta != null) 8f + c.lineH(metaP) else 0f)
        return StatCell(minW, h) { x, y, w, _ ->
            var cy = y
            cy += aligned(c, "TOP SET", kickerP, x, w, cy) + 8f
            cy += aligned(c, l.name, nameP, x, w, cy) + 6f
            cy += aligned(c, big, bigP, x, w, cy)
            if (meta != null) aligned(c, meta, metaP, x, w, cy + 8f)
        }
    }

    // ---- 1 rep max ----

    private fun oneRm(c: Ctx, b: FlexBlock, ct: FlexContent): StatBlock? {
        val items = pick(ct.lifts, b.keys, b.limit) { it.name }
        if (items.isEmpty()) return null
        return when (b.variant) {
            0 -> StatBlock(listOf(oneRmHero(c, b, items.first())), 1)
            2 -> StatBlock(items.map { oneRmTile(c, b, it) }, 2)
            else -> StatBlock(
                items.map { liftRow(c, it.name, if (b.flag) calcFrom(it) else null, "${one(it.e1rm)} kg", 52f) }, 1,
                heading = headingCell(c, "ESTIMATED 1RM"), gapY = 28f, cellPanels = false, outerPanel = true, rules = true
            )
        }
    }

    private fun oneRmHero(c: Ctx, b: FlexBlock, l: FlexLiftItem): StatCell {
        val st = c.st
        val kickerP = c.kicker()
        val num = one(l.e1rm)
        val nameP = c.ink(42f, true, st.textColor)
        val calc = if (b.flag) calcFrom(l) else null
        val calcP = c.ink(30f, false, st.textColor, 0.65f)
        val minW = maxOf(
            kickerP.measureText("ESTIMATED 1RM"), numberUnitWidth(c, num, "kg", 260f, "000.0"),
            nameP.measureText(l.name), calc?.let { calcP.measureText(it) } ?: 0f
        )
        val h = c.lineH(kickerP) + 6f + c.lineH(c.ink(260f, true, st.accent)) + 6f + c.lineH(nameP) +
            (if (calc != null) 6f + c.lineH(calcP) else 0f)
        return StatCell(minW, h) { x, y, w, _ ->
            var cy = y
            cy += aligned(c, "ESTIMATED 1RM", kickerP, x, w, cy) + 6f
            cy += numberWithUnit(c, x, w, cy, num, "kg", 260f) + 6f
            cy += aligned(c, l.name, nameP, x, w, cy)
            if (calc != null) aligned(c, calc, calcP, x, w, cy + 6f)
        }
    }

    private fun oneRmTile(c: Ctx, b: FlexBlock, l: FlexLiftItem): StatCell {
        val st = c.st
        val nameP = c.ink(30f, true, st.textColor, 0.85f)
        val num = one(l.e1rm)
        val calcP = c.ink(24f, false, st.textColor, 0.65f)
        // "Calculated from" and the set it came from are two labels, each on its own line: neither ever wraps.
        val calc = if (b.flag) listOf("Calculated from", calcSet(l)) else emptyList()
        val minW = maxOf(nameP.measureText(l.name), numberUnitWidth(c, num, "kg", 100f, "000.0"), calc.maxOfOrNull { calcP.measureText(it) } ?: 0f)
        val h = c.lineH(nameP) + 8f + c.lineH(c.ink(100f, true, st.accent)) + (if (calc.isNotEmpty()) 6f + calc.size * c.lineH(calcP) else 0f)
        return StatCell(minW, h) { x, y, w, _ ->
            var cy = y
            cy += aligned(c, l.name, nameP, x, w, cy) + 8f
            cy += numberWithUnit(c, x, w, cy, num, "kg", 100f)
            if (calc.isNotEmpty()) cy += 6f
            for (line in calc) cy += aligned(c, line, calcP, x, w, cy)
        }
    }

    // ---- Workout stats ----

    private fun sessionStats(c: Ctx, b: FlexBlock, ct: FlexContent): StatBlock? {
        val stats = ct.stats
        if (stats.isEmpty()) return null
        val st = c.st
        if (b.variant == 1) {
            // One line: the stats as one sentence, which breaks only between stats when it is too long.
            val p = c.ink(44f, true, st.textColor)
            val cells = stats.map { s ->
                val text = "${s.value} ${s.label.lowercase()}"
                StatCell(p.measureText(text), c.lineH(p)) { x, y, _, _ -> c.line(text, p, x, y, Paint.Align.LEFT) }
            }
            return StatBlock(cells, stats.size, gapY = 0f, cellPanels = false, separator = "  ·  ", sepPaint = p)
        }
        val n = stats.size
        val cols = when {
            n <= 2 -> n
            n == 4 -> 2
            else -> 3
        }
        val valueP = c.ink(50f, true, st.accent)
        val labelP = c.ink(24f, false, st.textColor, 0.65f, 0.08f)
        val cells = stats.map { s ->
            val label = s.label.uppercase()
            val minW = max(max(valueP.measureText(s.value), valueP.measureText("00,000 kg")), labelP.measureText(label))
            StatCell(minW, c.lineH(valueP) + 4f + c.lineH(labelP)) { x, y, w, _ ->
                c.line(s.value, valueP, x + w / 2f, y, Paint.Align.CENTER)
                c.line(label, labelP, x + w / 2f, y + c.lineH(valueP) + 4f, Paint.Align.CENTER)
            }
        }
        return StatBlock(cells, cols)
    }

    // -----------------------------------------------------------------------------------------
    // Titles and text
    // -----------------------------------------------------------------------------------------

    private fun header(c: Ctx, b: FlexBlock, ct: FlexContent, x: Float, y: Float, w: Float): Float {
        val st = c.st
        val title = b.text.ifBlank { ct.title }
        var h = 0f
        if (ct.kicker.isNotBlank()) {
            h += c.para(ct.kicker.uppercase(), c.ink(30f, true, st.accent, 1f, 0.12f), x, y + h, w)
            h += 14f
        }
        if (title.isNotBlank()) {
            val size = when (b.variant) {
                1 -> 124f
                2 -> 56f
                else -> 84f
            }
            h += c.para(title, c.ink(size, true, st.textColor), x, y + h, w)
        }
        val sub = listOfNotNull(
            ct.subtitle.takeIf { it.isNotBlank() },
            ct.dateText.takeIf { b.flag && it.isNotBlank() }
        ).joinToString("  ·  ")
        if (sub.isNotBlank()) {
            h += 12f
            h += c.para(sub, c.ink(34f, false, st.textColor, 0.7f), x, y + h, w)
        }
        return h
    }

    // ---- Exercises (a finished workout) ----

    private fun exercises(c: Ctx, b: FlexBlock, ct: FlexContent, x: Float, y: Float, w: Float): Float {
        val items = pick(ct.exercises, b.keys, b.limit) { it.name }
        if (items.isEmpty()) return 0f
        val st = c.st
        return c.panel(x, y, w) { ix, iy, iw ->
            var h = 0f
            items.forEachIndexed { i, ex ->
                if (i > 0) h += 22f
                val best = ex.sets.getOrNull(ex.bestIndex)
                when (b.variant) {
                    1 -> {
                        val right = buildString {
                            append(ex.sets.size).append(if (ex.sets.size == 1) " set" else " sets")
                            if (best != null) append(" · ").append(best.text(false))
                        }
                        val rightP = c.ink(28f, false, st.textColor, 0.75f)
                        val rightW = min(rightP.measureText(right), iw * 0.5f)
                        // The name keeps at least 100 px beside the sets.
                        c.needs(rightW + 20f + 100f, iw)
                        val nameL = c.layout(ex.name, c.ink(34f, true, st.textColor), (iw - rightW - 20f).coerceAtLeast(100f), false)
                        val rightL = c.layout(right, rightP, rightW + 2f, false)
                        val rowH = max(nameL.height, rightL.height).toFloat()
                        c.drawLayout(nameL, ix, iy + h + (rowH - nameL.height) / 2f)
                        c.drawLayout(rightL, ix + iw - rightW - 2f, iy + h + (rowH - rightL.height) / 2f)
                        h += rowH
                    }
                    2 -> {
                        h += c.para(ex.name, c.ink(38f, true, st.textColor), ix, iy + h, iw)
                        if (best != null) {
                            h += 4f
                            h += c.para("Best: ${best.text(b.flag)}", c.ink(32f, true, st.accent), ix, iy + h, iw)
                        }
                    }
                    else -> {
                        h += c.para(ex.name, c.ink(40f, true, st.textColor), ix, iy + h, iw)
                        h += 6f
                        val shown = ex.sets.take(8)
                        val setP = c.ink(30f, false, st.textColor, 0.8f)
                        shown.forEachIndexed { n, set ->
                            h += c.para("${n + 1}.  ${set.text(b.flag)}", setP, ix, iy + h, iw, false) + 4f
                        }
                        if (ex.sets.size > shown.size) {
                            h += c.para("+ ${ex.sets.size - shown.size} more", c.ink(26f, false, st.textColor, 0.55f), ix, iy + h, iw, false)
                        }
                    }
                }
            }
            h
        }
    }

    // ---- Routine (a workout template) ----

    private fun routine(c: Ctx, b: FlexBlock, ct: FlexContent, x: Float, y: Float, w: Float): Float {
        val items = ct.routine.take(b.limit.coerceAtLeast(1))
        if (items.isEmpty()) return 0f
        val st = c.st
        val compact = b.variant == 1
        return c.panel(x, y, w) { ix, iy, iw ->
            var h = 0f
            items.forEachIndexed { i, r ->
                if (i > 0) h += if (compact) 12f else 20f
                val right = if (b.flag) (r.target ?: "${r.sets} sets") else "${r.sets} sets"
                val rightP = c.ink(if (compact) 30f else 36f, true, st.accent)
                // The target is one line on the right, given at most 45% of the row; the name keeps at least 100 px.
                val rightFull = rightP.measureText(right)
                c.needs(rightFull / 0.45f, iw)
                val rightW = min(rightFull, iw * 0.45f)
                c.needs(rightW + 20f + 100f, iw)
                val nameL = c.layout(r.name, c.ink(if (compact) 32f else 38f, true, st.textColor), (iw - rightW - 20f).coerceAtLeast(100f), false)
                val tagL = if (!compact && !r.tag.isNullOrBlank()) c.layout(r.tag, c.ink(26f, false, st.textColor, 0.6f), iw, false) else null
                val textH = nameL.height + (tagL?.let { it.height + 2f } ?: 0f)
                val rowH = max(textH, c.lineH(rightP))
                val ty = iy + h + (rowH - textH) / 2f
                c.drawLayout(nameL, ix, ty)
                if (tagL != null) c.drawLayout(tagL, ix, ty + nameL.height + 2f)
                c.line(right, rightP, ix + iw, iy + h + (rowH - c.lineH(rightP)) / 2f, Paint.Align.RIGHT)
                h += rowH
            }
            h
        }
    }

    // ---- Chart ----

    /** The chart drawn into its whole tile, [w] by [h]: the plot stretches to the room left by its labels, which keep their size. */
    private fun chart(c: Ctx, b: FlexBlock, ct: FlexContent, w: Float, h: Float) {
        val pts = ct.chart.sortedBy { it.timeMs }
        if (pts.isEmpty() || c.dry) return
        val pad = c.panelPad()
        c.panelRect(0f, 0f, w, h)
        val ix = pad
        val iw = w - 2 * pad
        var top = pad
        if (ct.chartLabel.isNotBlank()) top += aligned(c, ct.chartLabel.uppercase(), c.kicker(), ix, iw, top) + 14f
        val axisW = if (b.flag) CHART_AXIS_W else 0f
        val datesH = if (b.flag) CHART_DATES_H else 0f
        val plotH = (h - pad - datesH - top).coerceAtLeast(1f)
        drawChart(c, pts, ix + axisW, top, (iw - axisW).coerceAtLeast(1f), plotH, b.variant == 1, b.flag)
    }

    private fun drawChart(
        c: Ctx, pts: List<FlexChartPoint>, left: Float, top: Float, width: Float, height: Float,
        bars: Boolean, labels: Boolean
    ) {
        val st = c.st
        val canvas = c.canvas
        val n = pts.size
        val minV = pts.minOf { it.value }
        val maxV = pts.maxOf { it.value }
        val lo: Double
        val hi: Double
        if (bars) {
            lo = 0.0
            hi = maxV * 1.1
        } else {
            val span = (maxV - minV).takeIf { it > 0.0 } ?: max(maxV * 0.1, 1.0)
            lo = minV - span * 0.15
            hi = maxV + span * 0.15
        }
        val range = (hi - lo).takeIf { it > 0.0 } ?: 1.0
        fun yOf(v: Double): Float = top + height - (((v - lo) / range) * height).toFloat()
        fun xOf(i: Int): Float = if (n == 1) left + width / 2f else left + width * i / (n - 1).toFloat()

        // Grid
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = st.textColor
            alpha = 36
            strokeWidth = 2f
        }
        for (g in 0..3) {
            val gy = top + height * g / 3f
            canvas.drawLine(left, gy, left + width, gy, grid)
        }

        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = st.accent }
        if (bars) {
            val slot = width / n
            val bw = slot * 0.6f
            pts.forEachIndexed { i, p ->
                val cx = left + slot * (i + 0.5f)
                val r = RectF(cx - bw / 2f, yOf(p.value), cx + bw / 2f, top + height)
                canvas.drawRoundRect(r, 6f, 6f, accent)
            }
        } else {
            val line = Path()
            pts.forEachIndexed { i, p -> if (i == 0) line.moveTo(xOf(i), yOf(p.value)) else line.lineTo(xOf(i), yOf(p.value)) }
            if (n > 1) {
                val area = Path(line).apply {
                    lineTo(xOf(n - 1), top + height)
                    lineTo(xOf(0), top + height)
                    close()
                }
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = st.accent
                    alpha = 40
                }
                canvas.drawPath(area, fill)
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = st.accent
                    style = Paint.Style.STROKE
                    strokeWidth = 7f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                canvas.drawPath(line, stroke)
            }
            pts.forEachIndexed { i, p -> canvas.drawCircle(xOf(i), yOf(p.value), if (n > 24) 5f else 9f, accent) }
        }

        if (labels) {
            val p = c.ink(24f, false, st.textColor, 0.65f)
            c.line(one(hi), p, left - 12f, top - 4f, Paint.Align.RIGHT)
            c.line(one(lo + range / 2.0), p, left - 12f, top + height / 2f - c.lineH(p) / 2f, Paint.Align.RIGHT)
            c.line(one(max(lo, 0.0)), p, left - 12f, top + height - c.lineH(p) + 4f, Paint.Align.RIGHT)
            val dateTop = top + height + 10f
            c.line(fmtDay(pts.first().timeMs), p, left, dateTop, Paint.Align.LEFT)
            if (n > 1) c.line(fmtDay(pts.last().timeMs), p, left + width, dateTop, Paint.Align.RIGHT)
        }
    }

    // ---- Day note, photo, free text ----

    private fun dayNote(c: Ctx, b: FlexBlock, ct: FlexContent, x: Float, y: Float, w: Float): Float {
        val st = c.st
        val note = ct.dayNote
        if (note.isBlank()) return 0f
        val date = if (b.flag) ct.dateText else ""
        if (b.variant == 1) {
            var h = c.para(note, c.ink(46f, false, st.textColor, 0.95f), x, y, w)
            if (date.isNotBlank()) {
                h += 12f
                h += c.para(date, c.ink(28f, false, st.textColor, 0.6f), x, y + h, w)
            }
            return h
        }
        return c.panel(x, y, w) { ix, iy, iw ->
            val markP = c.ink(130f, true, st.accent)
            var h = 0f
            val ax = c.alignX(ix, iw)
            val al = c.paintAlign()
            c.line("“", markP, ax, iy, al)
            h += c.lineH(markP) * 0.55f
            h += c.para(note, c.ink(46f, false, st.textColor), ix, iy + h, iw)
            if (date.isNotBlank()) {
                h += 14f
                h += c.para("— $date", c.ink(28f, false, st.textColor, 0.6f), ix, iy + h, iw)
            }
            h
        }
    }

    private fun textBlock(c: Ctx, b: FlexBlock, x: Float, y: Float, w: Float): Float {
        val st = c.st
        val s = b.text
        if (s.isBlank()) return 0f
        return when (b.variant) {
            1 -> {
                val bar = 8f
                val gap = 26f
                val l = c.layout(s, c.ink(48f, false, st.textColor), w - bar - gap, false)
                if (!c.dry) {
                    c.fill.color = st.accent
                    c.fill.alpha = 255
                    c.canvas.drawRoundRect(RectF(x, y, x + bar, y + l.height), 4f, 4f, c.fill)
                }
                c.drawLayout(l, x + bar + gap, y)
                l.height.toFloat()
            }
            2 -> c.para(s, c.ink(104f, true, st.textColor), x, y, w)
            else -> c.para(s, c.ink(46f, false, st.textColor, 0.92f), x, y, w)
        }
    }
}
