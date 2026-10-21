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
import android.text.TextUtils
import com.ashrdev.aznd.domain.formatWeight
import com.ashrdev.aznd.ui.theme.FillRender
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * A block's tile on the card, in card pixels (1080 wide): only as big as its content needs, so this is also
 * what can be tapped and grabbed. [scale] is how far its content had to shrink to fit (1 = natural size) and
 * [drawn] the height that content takes at that scale. The tile sits inside its cell, the space the tiling
 * gave it, which can be bigger.
 */
class FlexBlockBox(
    val id: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val scale: Float = 1f,
    val drawn: Float = height,
    val cellLeft: Float = left,
    val cellTop: Float = top,
    val cellWidth: Float = width,
    val cellHeight: Float = height
) {
    fun contains(x: Float, y: Float) = x >= left && x <= left + width && y >= top && y <= top + height
    fun cellContains(x: Float, y: Float) =
        x >= cellLeft && x <= cellLeft + cellWidth && y >= cellTop && y <= cellTop + cellHeight
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
    val lift: Float = 0f
)

/**
 * The result of laying a card out, without drawing it: the card's height (fixed by the size, or taken from
 * the content for Free size), the tiles, how far the content in them had to shrink, and what did not fit.
 * Compute it once per change ([FlexPainter.plan]) and hand it to [FlexPainter.draw] and to the UI.
 */
class FlexPlan(
    val height: Float,
    val boxes: List<FlexBlockBox>,
    /** The tiling actually drawn: the saved layout with the blocks that are not on the card left out and new ones added. */
    val tree: FlexNode?,
    /** The borders between neighbouring tiles, for resizing. */
    val dividers: List<FlexDivider>,
    /** How much room each child of each group has along the split, by the group's path in [tree]. */
    val groupSizes: Map<List<Int>, List<Float>>,
    /** The area the tiles fill, in card pixels, and the gap between tiles. */
    val contentLeft: Float,
    val contentTop: Float,
    val contentWidth: Float,
    val contentHeight: Float,
    val gap: Float,
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
    val tooTall: Boolean
) {
    fun hit(x: Float, y: Float): FlexBlockBox? = boxes.firstOrNull { it.contains(x, y) }
    fun box(id: Int): FlexBlockBox? = boxes.firstOrNull { it.id == id }
    /** The borders the tile of block [id] touches. */
    fun dividersOf(id: Int): List<FlexDivider> = dividers.filter { id in it.adjacent }
}

/**
 * Draws a Flex card with plain android.graphics, in a fixed 1080-wide design space that is scaled to
 * whatever width it is asked for. The editor preview (a Compose Canvas) and the exported PNG both
 * call [FlexPainter.draw], so what you see is what you share.
 *
 * Layout is a tiling (see FlexLayout.kt): every block gets a tile, and its content is drawn at its natural size, or
 * smaller when the tile is too small, and placed in the tile by the block's own alignment. Every block function
 * takes its box (x, y, width), draws itself and returns its height; while [Ctx.dry] is set nothing is drawn, so the same
 * code also measures. That is what lets [plan] fit the content, find what is hidden or clipped, and what
 * lets panels wrap their content.
 */
object FlexPainter {

    /** A tile's content is never shrunk below this (text would turn tiny): past it blocks are left off the card and the editor warns. */
    const val MIN_TILE_SCALE = 0.6f

    /** Below this the editor says some tiles are crowded. */
    const val CROWDED_SCALE = 0.85f
    private const val MIN_FREE_HEIGHT = 480f
    private const val NO_CAP = 1e9f
    private const val TOO_TALL = 4000f

    /** Pixels per dp the patterns are drawn at: the card is 1080 wide, about a phone screen's width in pixels. */
    private const val PATTERN_DENSITY = 2.7f

    /** What one arrangement of the shown blocks came to. */
    private class Arranged(
        val tree: FlexNode?,
        val boxes: List<FlexBlockBox>,
        val solved: FlexSolved,
        val height: Float,
        val availH: Float,
        val clipped: Boolean,
        val minScale: Float
    )

    /** Lays [spec] out for [content]. Cheap enough to run on every edit. */
    fun plan(spec: FlexSpec, content: FlexContent, assets: FlexAssets): FlexPlan {
        val st = spec.style
        val c = Ctx(Canvas(), st, assets)
        c.dry = true

        val pad = st.padding.toFloat()
        val footer = if (st.watermark && st.watermarkText.isNotBlank()) FLEX_FOOTER else 0f
        val availW = FLEX_CARD_WIDTH - 2 * pad
        val gap = st.spacing.toFloat()
        val limits = c.limits

        // Heights are measured a lot (every tile, every fit step), so remember them.
        val heights = HashMap<String, Float>()
        fun heightOf(b: FlexBlock, w: Float): Float =
            heights.getOrPut("${b.id}@${(w * 100).roundToInt()}") { measure(c, b, content, w) }

        // The least width a block can have without a broken word, a text cut short or text shrunk to a fraction.
        val needs = HashMap<Int, Float>()
        fun needOf(b: FlexBlock): Float = needs.getOrPut(b.id) {
            if (b.type == FlexBlockType.PHOTO) {
                0f
            } else {
                var w = 90f
                for (i in 0 until 14) {
                    c.overflow = 0f
                    measure(c, b, content, w)
                    if (c.overflow <= 0.5f) break
                    w += c.overflow * 2f * max(1f, b.size)
                    if (w >= availW) {
                        w = availW
                        break
                    }
                }
                w.coerceAtMost(availW)
            }
        }

        // The narrowest a block can be in a cell [cw] wide, at content scale [s], and still be [dh] tall with nothing broken or cut:
        // a block takes only what it needs.
        fun hugWidth(b: FlexBlock, cw: Float, s: Float, dh: Float): Float {
            var lo = min(cw, needOf(b) * s)
            var hi = cw
            var i = 0
            while (hi - lo > 3f && i++ < 8) {
                val mid = (lo + hi) / 2f
                c.overflow = 0f
                val h = measure(c, b, content, mid / s) * s
                if (c.overflow <= 0.5f && abs(h - dh) <= 1.5f) hi = mid else lo = mid
            }
            return min(cw, hi + 2f)
        }

        // 1. Which blocks draw anything. A size only fits so many.
        val drawable = spec.blocks
            .filter { it.enabled && content.has(it.type) }
            .filter { heightOf(it, availW) > 0f }
        val limit = st.aspect.maxBlocks
        val hidden = ArrayList(drawable.drop(limit).map { it.id })
        var live = drawable.take(limit)

        val hugs = HashMap<Int, Float>()

        fun arrange(shown: List<FlexBlock>): Arranged {
            val byId = shown.associateBy { it.id }
            // 2. The tiling of exactly those blocks.
            val tree = FlexTree.effective(spec.layout, shown.map { it.id }, st.aspect == FlexAspect.WIDE)
            val nat: (Int, Float) -> Float = { id, w -> byId[id]?.let { heightOf(it, w) } ?: 0f }
            val cap: (Int, Boolean) -> Float = { id, vertical ->
                if (byId[id]?.type == FlexBlockType.PHOTO) (if (vertical) limits.maxHeight else limits.maxWidth) else NO_CAP
            }
            val hug: (Int) -> Float = { id ->
                val b = byId[id]
                if (b == null) {
                    availW
                } else {
                    hugs.getOrPut(id) {
                        if (b.type == FlexBlockType.PHOTO) {
                            photoSize(c, b, content, availW, Float.MAX_VALUE)?.first ?: availW
                        } else {
                            hugWidth(b, availW, 1f, heightOf(b, availW))
                        }
                    }
                }
            }
            val need: (Int) -> Float = { id -> byId[id]?.let { needOf(it) } ?: 0f }

            // 3. The card's height: fixed by the size, or whatever the content needs for Free size.
            val height: Float = if (st.aspect == FlexAspect.FREE) {
                val wanted = if (tree != null) FlexTiler.natural(tree, availW, gap, nat, hug) else 0f
                (wanted + 2 * pad + footer).coerceAtLeast(MIN_FREE_HEIGHT)
            } else {
                st.aspect.height.toFloat()
            }
            val availH = height - 2 * pad - footer
            val solved = FlexTiler.solve(tree, pad, pad, availW, availH, gap, nat, cap, hug, need)

            // 4. Tiles. Each block is only as big as its content needs, inside the cell the tiling gave it, placed
            // there by the block's own alignment. Content that is too big for its cell shrinks (never below
            // MIN_TILE_SCALE: past that the block cannot be shown); a photo is its own shape, as big as fits.
            var clipped = false
            var minScale = 1f
            val boxes = ArrayList<FlexBlockBox>()
            for ((id, r) in solved.rects) {
                val b = byId[id] ?: continue
                val cx = r[0]
                val cy = r[1]
                val cw = r[2]
                val ch = r[3]
                var tw = cw
                var th = ch
                var s = 1f
                if (b.type == FlexBlockType.PHOTO) {
                    val size = photoSize(c, b, content, cw, ch)
                    if (size != null) {
                        tw = size.first
                        th = size.second
                    }
                } else {
                    val need1 = needOf(b)
                    // Words must stay whole: scale far enough for the box to be as wide as they need.
                    val widest = if (need1 > cw) (cw / need1).coerceAtLeast(MIN_TILE_SCALE) else 1f
                    s = widest
                    var dh = heightOf(b, cw / s) * s
                    if (dh > ch) {
                        var tall = dh
                        for (i in 0 until 6) {
                            val next = (s * ch / tall).coerceIn(MIN_TILE_SCALE, widest)
                            if (abs(next - s) < 0.0005f) {
                                s = next
                                break
                            }
                            s = next
                            tall = heightOf(b, cw / s) * s
                        }
                        // Wrapping can make the last step slightly too big; never end up over.
                        var guard = 0
                        while (heightOf(b, cw / s) * s > ch + 0.5f && s > MIN_TILE_SCALE && guard++ < 40) {
                            s = max(MIN_TILE_SCALE, s - 0.004f)
                        }
                        dh = heightOf(b, cw / s) * s
                    }
                    val cut = dh > ch + 1f || need1 > cw / s + 1f
                    if (cut) clipped = true
                    th = min(dh, ch)
                    tw = if (cut) cw else hugWidth(b, cw, s, dh)
                }
                minScale = min(minScale, s)
                val tx = cx + when (b.hAlign) {
                    FlexAlign.START -> 0f
                    FlexAlign.CENTER -> (cw - tw) / 2f
                    FlexAlign.END -> cw - tw
                }
                val ty = cy + when (b.vAlign) {
                    FlexVAlign.TOP -> 0f
                    FlexVAlign.CENTER -> (ch - th) / 2f
                    FlexVAlign.BOTTOM -> ch - th
                }
                boxes += FlexBlockBox(id, tx, ty, tw, th, s, th, cx, cy, cw, ch)
            }
            return Arranged(tree, boxes, solved, height, availH, clipped, minScale)
        }

        // 5. When something cannot be shown whole even at the smallest scale (a fixed-size card that is too full), the
        // block at the end of the layout is left off, over and over, until what is left fits. Free size just grows.
        var arranged = arrange(live)
        while (arranged.clipped && st.aspect != FlexAspect.FREE && live.size > 1) {
            val last = FlexTree.leaves(arranged.tree).lastOrNull() ?: break
            live = live.filter { it.id != last }
            hidden.add(last)
            arranged = arrange(live)
        }
        return FlexPlan(
            height = arranged.height,
            boxes = arranged.boxes,
            tree = arranged.tree,
            dividers = arranged.solved.dividers,
            groupSizes = arranged.solved.groupSizes,
            contentLeft = pad,
            contentTop = pad,
            contentWidth = availW,
            contentHeight = arranged.availH,
            gap = gap,
            shownIds = live.map { it.id },
            hidden = hidden,
            clipped = arranged.clipped,
            minScale = arranged.minScale,
            limit = limit,
            tooTall = st.aspect == FlexAspect.FREE && arranged.height > TOO_TALL
        )
    }

    /** How tall a block's content is at the width [w]. A photo takes the biggest size the half-card limit allows. */
    private fun measure(c: Ctx, b: FlexBlock, ct: FlexContent, w: Float): Float {
        if (b.type == FlexBlockType.PHOTO) return photoSize(c, b, ct, w, Float.MAX_VALUE)?.second ?: 0f
        return block(c, b, ct, 0f, 0f, w)
    }

    /**
     * The width and height a photo is drawn at inside a tile [w] by [h]: its crop shape, as big as fits, never
     * more than the half of the card the limit allows. Null when there is no picture.
     */
    private fun photoSize(c: Ctx, b: FlexBlock, ct: FlexContent, w: Float, h: Float): Pair<Float, Float>? {
        val uri = b.keys?.firstOrNull() ?: ct.photoUri ?: return null
        val bmp = c.assets.images[uri] ?: return null
        val lim = c.limits
        if (b.variant == 1) {
            val d = min(min(w, h), min(lim.maxWidth, lim.maxHeight))
            return d to d
        }
        val ratio = (if (b.photoRatio > 0f) b.photoRatio else bmp.width.toFloat() / bmp.height)
            .coerceIn(lim.range.start, lim.range.endInclusive)
        var pw = min(w, lim.maxWidth)
        var ph = pw / ratio
        val maxH = min(h, lim.maxHeight)
        if (ph > maxH) {
            ph = maxH
            pw = ph * ratio
        }
        return pw to ph
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
        val w = FLEX_CARD_WIDTH.toFloat()
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
        drawBorder(ctx, w, h)

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

    private class Ctx(val canvas: Canvas, val st: FlexStyle, val assets: FlexAssets) {
        /** While true, blocks only measure. */
        var dry = false

        /**
         * Dry runs only: how much wider (in the units of the box being measured) the content must be for no word
         * to be broken, no text cut with an ellipsis and no text shrunk to a fraction of its size. 0 = it is fine.
         * The caller resets it, measures, and reads it.
         */
        var overflow = 0f

        /** How the block being drawn aligns its content across its tile. Set per block. */
        var align: FlexAlign = FlexAlign.START
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

        /**
         * The largest paint up to [maxSize] that fits [width] on one line. The size is worked out for
         * [reference] (the widest value that kind of text normally takes), not for [s] itself, so every value
         * of one kind is drawn at the same size however many digits it has. Only a value longer than the
         * reference shrinks further.
         */
        fun fit(s: String, bold: Boolean, color: Int, maxSize: Float, width: Float, alpha: Float = 1f, reference: String = s): TextPaint {
            var size = maxSize
            var p = ink(size, bold, color, alpha)
            while (max(p.measureText(reference), p.measureText(s)) > width && size > 18f) {
                size *= 0.93f
                p = ink(size, bold, color, alpha)
            }
            if (dry) {
                // Text this much smaller than asked for looks wrong: the box has to be wide enough to avoid it.
                val least = ink(maxSize * 0.75f, bold, color, alpha)
                val lack = max(least.measureText(reference), least.measureText(s)) - width
                if (lack > 0f) overflow = max(overflow, lack)
            }
            return p
        }

        fun lineH(p: Paint): Float = p.descent() - p.ascent()

        fun layout(s: CharSequence, p: TextPaint, width: Float, maxLines: Int, centered: Boolean, rightAligned: Boolean = right): StaticLayout {
            val l = StaticLayout.Builder.obtain(s, 0, s.length, p, width.toInt().coerceAtLeast(1))
                .setAlignment(
                    when {
                        centered -> Layout.Alignment.ALIGN_CENTER
                        rightAligned -> Layout.Alignment.ALIGN_OPPOSITE
                        else -> Layout.Alignment.ALIGN_NORMAL
                    }
                )
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build()
            if (dry) noteOverflow(s, p, width, maxLines, l)
            return l
        }

        /** A word wider than its box gets broken mid-word, and text that does not fit its lines ends in an ellipsis: both want a wider box. */
        private fun noteOverflow(s: CharSequence, p: TextPaint, width: Float, maxLines: Int, l: StaticLayout) {
            var widest = 0f
            for (word in s.split(' ', '\n', '\t')) if (word.isNotEmpty()) widest = max(widest, p.measureText(word))
            if (widest > width) overflow = max(overflow, widest - width)
            for (i in 0 until l.lineCount) {
                if (l.getEllipsisCount(i) > 0) {
                    val need = p.measureText(s.toString()) / maxLines * (if (maxLines > 1) 1.1f else 1f) - width
                    overflow = max(overflow, max(need, width * 0.1f))
                    break
                }
            }
        }

        fun drawLayout(l: StaticLayout, x: Float, y: Float) {
            if (dry) return
            canvas.save()
            canvas.translate(x, y)
            l.draw(canvas)
            canvas.restore()
        }

        /** Wrapped text in a box; returns its height. */
        fun para(s: String, p: TextPaint, x: Float, y: Float, width: Float, maxLines: Int = 3, centered: Boolean = center): Float {
            if (s.isBlank()) return 0f
            val l = layout(s, p, width, maxLines, centered)
            drawLayout(l, x, y)
            return l.height.toFloat()
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
                FlexPanel.OUTLINE -> if (st.panelBorderWidth <= 0) {
                    stroke.color = st.panelColor
                    stroke.alpha = 150
                    stroke.strokeWidth = 3f
                    canvas.drawRoundRect(r, rad, rad, stroke)
                }
                FlexPanel.GLASS -> {
                    fill.color = Color.WHITE
                    fill.alpha = 36
                    canvas.drawRoundRect(r, rad, rad, fill)
                    stroke.color = Color.WHITE
                    stroke.alpha = 70
                    stroke.strokeWidth = 2f
                    canvas.drawRoundRect(r, rad, rad, stroke)
                }
                FlexPanel.NONE -> Unit
            }
            if (st.panelBorderWidth > 0) {
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
            val innerH = measure { inner(x + pad, y + pad, w - 2 * pad) }
            val total = max(innerH + 2 * pad, minH)
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

    private fun drawBorder(c: Ctx, w: Float, h: Float) {
        val st = c.st
        if (st.borderWidth <= 0) return
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
        for (box in plan.boxes) {
            val b = byId[box.id] ?: continue
            if (box.id == heldId) continue   // floats above, drawn last; here it only leaves its gap
            drawTile(c, b, content, box, box.left, box.top)
        }
        if (poses != null && heldId != null) drawHeld(c, content, plan, poses, byId[heldId])

        if (st.watermark && st.watermarkText.isNotBlank()) {
            val pad = st.padding.toFloat()
            val p = c.ink(26f, true, st.textColor, 0.5f, 0.1f)
            val baseline = h - max(pad * 0.55f, 40f)
            c.canvas.drawText(st.watermarkText, pad, baseline, p)
        }
    }

    /**
     * One block in its tile. The content is drawn at the tile's scale ([FlexBlockBox.scale], below 1 only when
     * it would not fit), placed in the tile by the block's own alignment, and cut off at the tile's edge so
     * nothing ever spills out of it. A photo is drawn at its own shape, as big as fits, and aligned the same way.
     */
    private fun drawTile(c: Ctx, b: FlexBlock, ct: FlexContent, box: FlexBlockBox, left: Float, top: Float, grow: Float = 1f) {
        c.align = b.hAlign
        val canvas = c.canvas
        canvas.save()
        canvas.translate(left, top)
        if (grow != 1f) canvas.scale(grow, grow)
        canvas.clipRect(0f, 0f, box.width, box.height)
        if (b.type == FlexBlockType.PHOTO) {
            drawPhoto(c, b, ct, box.width, box.height)
        } else {
            val oy = when (b.vAlign) {
                FlexVAlign.TOP -> 0f
                FlexVAlign.CENTER -> (box.height - box.drawn) / 2f
                FlexVAlign.BOTTOM -> box.height - box.drawn
            }.coerceAtLeast(0f)
            canvas.translate(0f, oy)
            canvas.scale(box.scale, box.scale)
            block(c, b, ct, 0f, 0f, box.width / box.scale)
        }
        canvas.restore()
    }

    /** A photo is its own tile: the image fills it. */
    private fun drawPhoto(c: Ctx, b: FlexBlock, ct: FlexContent, w: Float, h: Float) {
        val uri = b.keys?.firstOrNull() ?: ct.photoUri ?: return
        val bmp = c.assets.images[uri] ?: return
        val radius = when (b.variant) {
            1 -> min(w, h) / 2f
            2 -> 0f
            else -> max(c.st.panelRadius.toFloat(), 24f)
        }
        c.image(bmp, RectF(0f, 0f, w, h), radius, b.photoZoom, b.photoX, b.photoY)
    }

    /** The block being dragged, drawn on top where the finger has it: slightly bigger, with a soft shadow. */
    private fun drawHeld(c: Ctx, content: FlexContent, plan: FlexPlan, poses: FlexPoses, b: FlexBlock?) {
        if (b == null) return
        val box = plan.boxes.firstOrNull { it.id == b.id } ?: return
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

    /**
     * A block, at its own size ([FlexBlock.size]): everything inside scales together, so the box it takes
     * grows or shrinks with it and the card lays out around it.
     */
    private fun block(c: Ctx, b: FlexBlock, ct: FlexContent, x: Float, y: Float, w: Float): Float {
        val k = b.size
        if (k == 1f) return blockContent(c, b, ct, x, y, w)
        if (c.dry) return blockContent(c, b, ct, 0f, 0f, w / k) * k
        c.canvas.save()
        c.canvas.translate(x, y)
        c.canvas.scale(k, k)
        val h = blockContent(c, b, ct, 0f, 0f, w / k)
        c.canvas.restore()
        return h * k
    }

    private fun blockContent(c: Ctx, b: FlexBlock, ct: FlexContent, x: Float, y: Float, w: Float): Float = when (b.type) {
        FlexBlockType.HEADER -> header(c, b, ct, x, y, w)
        FlexBlockType.STREAKS -> streaks(c, b, ct, x, y, w)
        FlexBlockType.TOP_SET -> topSet(c, b, ct, x, y, w)
        FlexBlockType.ONE_RM -> oneRm(c, b, ct, x, y, w)
        FlexBlockType.SESSION_STATS -> sessionStats(c, b, ct, x, y, w)
        FlexBlockType.EXERCISES -> exercises(c, b, ct, x, y, w)
        FlexBlockType.ROUTINE -> routine(c, b, ct, x, y, w)
        FlexBlockType.CHART -> chart(c, b, ct, x, y, w)
        FlexBlockType.DAY_NOTE -> dayNote(c, b, ct, x, y, w)
        FlexBlockType.PHOTO -> 0f   // photos are drawn by drawPhoto, in their tile
        FlexBlockType.TEXT -> textBlock(c, b, x, y, w)
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
    private fun calcFrom(l: FlexLiftItem): String =
        "Calculated from ${setText(l)}" + (l.rpe?.let { " @ RPE ${formatWeight(it)}" } ?: "")
    private fun liftMeta(l: FlexLiftItem): String =
        listOfNotNull(l.rpe?.let { "RPE ${formatWeight(it)}" }, fmtDate(l.dateMs)).joinToString(" · ")

    /** A big number with a small unit after it, sharing a baseline, left or centre aligned per the card. */
    private fun numberWithUnit(c: Ctx, x: Float, w: Float, y: Float, num: String, unit: String, maxSize: Float, reference: String): Float {
        val st = c.st
        val unitP = c.ink(30f, false, st.textColor, 0.7f)
        val unitW = unitP.measureText(unit) + 12f
        val numP = c.fit(num, true, st.accent, maxSize, (w - unitW).coerceAtLeast(40f), reference = reference)
        val numW = numP.measureText(num)
        val startX = c.alignBox(x, w, numW + unitW)
        if (!c.dry) {
            val baseline = y - numP.ascent()
            c.canvas.drawText(num, startX, baseline, numP)
            c.canvas.drawText(unit, startX + numW + 12f, baseline, unitP)
        }
        return c.lineH(numP)
    }

    // -----------------------------------------------------------------------------------------
    // Blocks
    // -----------------------------------------------------------------------------------------

    private fun header(c: Ctx, b: FlexBlock, ct: FlexContent, x: Float, y: Float, w: Float): Float {
        val st = c.st
        val title = b.text.ifBlank { ct.title }
        var h = 0f
        if (ct.kicker.isNotBlank()) {
            h += c.para(ct.kicker.uppercase(), c.ink(30f, true, st.accent, 1f, 0.12f), x, y + h, w, 1)
            h += 14f
        }
        if (title.isNotBlank()) {
            val size = when (b.variant) {
                1 -> 124f
                2 -> 56f
                else -> 84f
            }
            h += c.para(title, c.ink(size, true, st.textColor), x, y + h, w, if (b.variant == 1) 3 else 2)
        }
        val sub = listOfNotNull(
            ct.subtitle.takeIf { it.isNotBlank() },
            ct.dateText.takeIf { b.flag && it.isNotBlank() }
        ).joinToString("  ·  ")
        if (sub.isNotBlank()) {
            h += 12f
            h += c.para(sub, c.ink(34f, false, st.textColor, 0.7f), x, y + h, w, 2)
        }
        return h
    }

    // ---- Streaks ----

    private fun streaks(c: Ctx, b: FlexBlock, ct: FlexContent, x: Float, y: Float, w: Float): Float {
        val items = pick(ct.streaks, b.keys, b.limit) { FlexContent.streakKey(it) }
        if (items.isEmpty()) return 0f
        return when (b.variant) {
            2 -> streakHero(c, b, items.first(), x, y, w)
            1 -> streakGrid(c, b, items, x, y, w)
            else -> streakCards(c, b, items, x, y, w)
        }
    }

    private fun streakCards(c: Ctx, b: FlexBlock, items: List<FlexStreakItem>, x: Float, y: Float, w: Float): Float {
        val st = c.st
        var cy = y
        items.forEachIndexed { i, s ->
            if (i > 0) cy += 16f
            cy += c.panel(x, cy, w) { ix, iy, iw ->
                val logo = s.logoUri?.let { c.assets.images[it] }
                val avatar = if (logo != null) 96f else 0f
                val gapX = if (logo != null) 24f else 0f
                val numP = c.ink(80f, true, st.accent)
                val unitP = c.ink(28f, false, st.textColor, 0.7f)
                val num = s.days.toString()
                val unit = if (s.days == 1) "day" else "days"
                val rightW = max(numP.measureText(num), unitP.measureText(unit))
                val leftW = (iw - avatar - gapX - rightW - 28f).coerceAtLeast(80f)
                val nameL = c.layout(s.name, c.ink(40f, true, st.textColor), leftW, 2, false)
                val subL = if (b.flag && s.longest > 0) {
                    c.layout("Longest run: ${s.longest} days", c.ink(26f, false, st.textColor, 0.65f), leftW, 1, false)
                } else null
                val textH = nameL.height + (subL?.let { it.height + 6f } ?: 0f)
                val rightH = c.lineH(numP) + c.lineH(unitP) - 10f
                val rowH = max(max(textH, avatar), rightH)
                if (!c.dry) {
                    val tx = ix + avatar + gapX
                    val ty = iy + (rowH - textH) / 2f
                    c.drawLayout(nameL, tx, ty)
                    if (subL != null) c.drawLayout(subL, tx, ty + nameL.height + 6f)
                    if (logo != null) {
                        val top = iy + (rowH - avatar) / 2f
                        c.image(logo, RectF(ix, top, ix + avatar, top + avatar), avatar / 2f)
                    }
                    val rTop = iy + (rowH - rightH) / 2f
                    c.line(num, numP, ix + iw, rTop, Paint.Align.RIGHT)
                    c.line(unit, unitP, ix + iw, rTop + c.lineH(numP) - 10f, Paint.Align.RIGHT)
                }
                rowH
            }
        }
        return cy - y
    }

    private fun streakGrid(c: Ctx, b: FlexBlock, items: List<FlexStreakItem>, x: Float, y: Float, w: Float): Float {
        val st = c.st
        val gap = 16f
        val tw = (w - gap) / 2f
        var cy = y

        fun tile(s: FlexStreakItem, tx: Float, ty: Float, minH: Float): Float = c.panel(tx, ty, tw, minH) { ix, iy, iw ->
            val nameL = c.layout(s.name, c.ink(32f, true, st.textColor, 0.85f), iw, 2, c.center)
            c.drawLayout(nameL, ix, iy)
            var h = nameL.height + 8f
            h += numberWithUnit(c, ix, iw, iy + h, s.days.toString(), if (s.days == 1) "day" else "days", 110f, "000")
            if (b.flag && s.longest > 0) {
                h += 6f
                h += c.para("Longest ${s.longest}", c.ink(24f, false, st.textColor, 0.65f), ix, iy + h, iw, 1)
            }
            h
        }

        items.chunked(2).forEachIndexed { r, row ->
            if (r > 0) cy += gap
            val rowTop = cy
            val rowH = row.mapIndexed { i, s -> c.measure { tile(s, x + i * (tw + gap), rowTop, 0f) } }
                .maxOrNull() ?: 0f
            row.forEachIndexed { i, s -> tile(s, x + i * (tw + gap), rowTop, rowH) }
            cy += rowH
        }
        return cy - y
    }

    private fun streakHero(c: Ctx, b: FlexBlock, s: FlexStreakItem, x: Float, y: Float, w: Float): Float =
        c.panel(x, y, w) { ix, iy, iw ->
            val st = c.st
            var h = 0f
            val nameL = c.layout(s.name.uppercase(), c.ink(34f, true, st.accent, 1f, 0.08f), iw, 2, c.center)
            c.drawLayout(nameL, ix, iy)
            h += nameL.height + 4f
            val ax = c.alignX(ix, iw)
            val al = c.paintAlign()
            val numP = c.fit(s.days.toString(), true, st.accent, 300f, iw, reference = "000")
            h += c.line(s.days.toString(), numP, ax, iy + h, al)
            h += c.line(if (s.days == 1) "DAY" else "DAYS", c.ink(44f, true, st.textColor, 0.8f, 0.2f), ax, iy + h, al)
            if (b.flag && s.longest > 0) {
                h += 10f
                h += c.para("Longest run: ${s.longest} days", c.ink(30f, false, st.textColor, 0.65f), ix, iy + h, iw, 1)
            }
            h
        }

    // ---- Top set ----

    private fun topSet(c: Ctx, b: FlexBlock, ct: FlexContent, x: Float, y: Float, w: Float): Float {
        val items = pick(ct.lifts, b.keys, b.limit) { it.name }
        if (items.isEmpty()) return 0f
        val st = c.st
        if (b.variant == 0) {
            val l = items.first()
            return c.panel(x, y, w) { ix, iy, iw ->
                var h = 0f
                h += c.para("TOP SET", c.kicker(), ix, iy + h, iw, 1) + 8f
                h += c.para(l.name, c.ink(40f, true, st.textColor, 0.9f), ix, iy + h, iw, 2) + 6f
                val big = setText(l)
                val ax = c.alignX(ix, iw)
                val al = c.paintAlign()
                h += c.line(big, c.fit(big, true, st.accent, 150f, iw, reference = "000.0 kg × 00"), ax, iy + h, al)
                if (b.flag) {
                    h += 8f
                    h += c.para(liftMeta(l), c.ink(30f, false, st.textColor, 0.7f), ix, iy + h, iw, 1)
                }
                h
            }
        }
        return c.panel(x, y, w) { ix, iy, iw ->
            var h = c.para("TOP SETS", c.kicker(), ix, iy, iw, 1) + 14f
            items.forEachIndexed { i, l ->
                if (i > 0) {
                    h += 14f
                    c.rule(ix, iy + h, iw)
                    h += 14f
                }
                val right = setText(l)
                val rightP = c.ink(42f, true, st.accent)
                val leftW = (iw - rightP.measureText(right) - 24f).coerceAtLeast(120f)
                val nameL = c.layout(l.name, c.ink(36f, true, st.textColor), leftW, 2, false)
                val metaL = if (b.flag) c.layout(liftMeta(l), c.ink(26f, false, st.textColor, 0.65f), leftW, 1, false) else null
                val textH = nameL.height + (metaL?.let { it.height + 4f } ?: 0f)
                val rowH = max(textH, c.lineH(rightP))
                val ty = iy + h + (rowH - textH) / 2f
                c.drawLayout(nameL, ix, ty)
                if (metaL != null) c.drawLayout(metaL, ix, ty + nameL.height + 4f)
                c.line(right, rightP, ix + iw, iy + h + (rowH - c.lineH(rightP)) / 2f, Paint.Align.RIGHT)
                h += rowH
            }
            h
        }
    }

    // ---- 1 rep max ----

    private fun oneRm(c: Ctx, b: FlexBlock, ct: FlexContent, x: Float, y: Float, w: Float): Float {
        val items = pick(ct.lifts, b.keys, b.limit) { it.name }
        if (items.isEmpty()) return 0f
        val st = c.st
        return when (b.variant) {
            0 -> {
                val l = items.first()
                c.panel(x, y, w) { ix, iy, iw ->
                    var h = 0f
                    h += c.para("ESTIMATED 1RM", c.kicker(), ix, iy + h, iw, 1) + 6f
                    h += numberWithUnit(c, ix, iw, iy + h, one(l.e1rm), "kg", 260f, "000.0")
                    h += 6f
                    h += c.para(l.name, c.ink(42f, true, st.textColor), ix, iy + h, iw, 2)
                    if (b.flag) {
                        h += 6f
                        h += c.para(calcFrom(l), c.ink(30f, false, st.textColor, 0.65f), ix, iy + h, iw, 2)
                    }
                    h
                }
            }
            2 -> {
                val gap = 16f
                val tw = (w - gap) / 2f
                var cy = y
                fun tile(l: FlexLiftItem, tx: Float, ty: Float, minH: Float): Float =
                    c.panel(tx, ty, tw, minH) { ix, iy, iw ->
                        val nameL = c.layout(l.name, c.ink(30f, true, st.textColor, 0.85f), iw, 2, c.center)
                        c.drawLayout(nameL, ix, iy)
                        var h = nameL.height + 8f
                        h += numberWithUnit(c, ix, iw, iy + h, one(l.e1rm), "kg", 100f, "000.0")
                        if (b.flag) {
                            h += 6f
                            h += c.para(calcFrom(l), c.ink(24f, false, st.textColor, 0.65f), ix, iy + h, iw, 2)
                        }
                        h
                    }
                items.chunked(2).forEachIndexed { r, row ->
                    if (r > 0) cy += gap
                    val rowTop = cy
                    val rowH = row.mapIndexed { i, l -> c.measure { tile(l, x + i * (tw + gap), rowTop, 0f) } }
                        .maxOrNull() ?: 0f
                    row.forEachIndexed { i, l -> tile(l, x + i * (tw + gap), rowTop, rowH) }
                    cy += rowH
                }
                cy - y
            }
            else -> c.panel(x, y, w) { ix, iy, iw ->
                var h = c.para("ESTIMATED 1RM", c.kicker(), ix, iy, iw, 1) + 14f
                items.forEachIndexed { i, l ->
                    if (i > 0) {
                        h += 14f
                        c.rule(ix, iy + h, iw)
                        h += 14f
                    }
                    val right = "${one(l.e1rm)} kg"
                    val rightP = c.ink(52f, true, st.accent)
                    val leftW = (iw - rightP.measureText(right) - 24f).coerceAtLeast(120f)
                    val nameL = c.layout(l.name, c.ink(36f, true, st.textColor), leftW, 2, false)
                    val metaL = if (b.flag) c.layout(calcFrom(l), c.ink(26f, false, st.textColor, 0.65f), leftW, 2, false) else null
                    val textH = nameL.height + (metaL?.let { it.height + 4f } ?: 0f)
                    val rowH = max(textH, c.lineH(rightP))
                    val ty = iy + h + (rowH - textH) / 2f
                    c.drawLayout(nameL, ix, ty)
                    if (metaL != null) c.drawLayout(metaL, ix, ty + nameL.height + 4f)
                    c.line(right, rightP, ix + iw, iy + h + (rowH - c.lineH(rightP)) / 2f, Paint.Align.RIGHT)
                    h += rowH
                }
                h
            }
        }
    }

    // ---- Workout stats ----

    private fun sessionStats(c: Ctx, b: FlexBlock, ct: FlexContent, x: Float, y: Float, w: Float): Float {
        val stats = ct.stats
        if (stats.isEmpty()) return 0f
        val st = c.st
        if (b.variant == 1) {
            val s = stats.joinToString("  ·  ") { "${it.value} ${it.label.lowercase()}" }
            return c.para(s, c.ink(44f, true, st.textColor), x, y, w, 4)
        }
        val n = stats.size
        val cols = when {
            n <= 2 -> n
            n == 4 -> 2
            else -> 3
        }
        val gap = 16f
        val tw = (w - gap * (cols - 1)) / cols

        fun tile(s: FlexStat, tx: Float, ty: Float, minH: Float): Float = c.panel(tx, ty, tw, minH) { ix, iy, iw ->
            var h = 0f
            val valueP = c.fit(s.value, true, st.accent, 60f, iw, reference = "00,000 kg")
            h += c.line(s.value, valueP, ix + iw / 2f, iy, Paint.Align.CENTER)
            h += 4f
            h += c.para(s.label.uppercase(), c.ink(24f, false, st.textColor, 0.65f, 0.08f), ix, iy + h, iw, 1, true)
            h
        }

        var cy = y
        stats.chunked(cols).forEachIndexed { r, row ->
            if (r > 0) cy += gap
            val rowTop = cy
            val rowH = row.mapIndexed { i, s -> c.measure { tile(s, x + i * (tw + gap), rowTop, 0f) } }
                .maxOrNull() ?: 0f
            row.forEachIndexed { i, s -> tile(s, x + i * (tw + gap), rowTop, rowH) }
            cy += rowH
        }
        return cy - y
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
                        val nameL = c.layout(ex.name, c.ink(34f, true, st.textColor), (iw - rightW - 20f).coerceAtLeast(100f), 2, false)
                        val rightL = c.layout(right, rightP, rightW + 2f, 2, false)
                        val rowH = max(nameL.height, rightL.height).toFloat()
                        c.drawLayout(nameL, ix, iy + h + (rowH - nameL.height) / 2f)
                        c.drawLayout(rightL, ix + iw - rightW - 2f, iy + h + (rowH - rightL.height) / 2f)
                        h += rowH
                    }
                    2 -> {
                        h += c.para(ex.name, c.ink(38f, true, st.textColor), ix, iy + h, iw, 2)
                        if (best != null) {
                            h += 4f
                            h += c.para("Best: ${best.text(b.flag)}", c.ink(32f, true, st.accent), ix, iy + h, iw, 1)
                        }
                    }
                    else -> {
                        h += c.para(ex.name, c.ink(40f, true, st.textColor), ix, iy + h, iw, 2)
                        h += 6f
                        val shown = ex.sets.take(8)
                        val setP = c.ink(30f, false, st.textColor, 0.8f)
                        shown.forEachIndexed { n, set ->
                            h += c.para("${n + 1}.  ${set.text(b.flag)}", setP, ix, iy + h, iw, 1, false) + 4f
                        }
                        if (ex.sets.size > shown.size) {
                            h += c.para("+ ${ex.sets.size - shown.size} more", c.ink(26f, false, st.textColor, 0.55f), ix, iy + h, iw, 1, false)
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
                val rightW = min(rightP.measureText(right), iw * 0.45f)
                val nameL = c.layout(r.name, c.ink(if (compact) 32f else 38f, true, st.textColor), (iw - rightW - 20f).coerceAtLeast(100f), 2, false)
                val tagL = if (!compact && !r.tag.isNullOrBlank()) c.layout(r.tag, c.ink(26f, false, st.textColor, 0.6f), iw, 1, false) else null
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

    private fun chart(c: Ctx, b: FlexBlock, ct: FlexContent, x: Float, y: Float, w: Float): Float {
        val pts = ct.chart.sortedBy { it.timeMs }
        if (pts.isEmpty()) return 0f
        return c.panel(x, y, w) { ix, iy, iw ->
            var h = 0f
            if (ct.chartLabel.isNotBlank()) h += c.para(ct.chartLabel.uppercase(), c.kicker(), ix, iy, iw, 1) + 14f
            val chartH = 360f
            val axisW = if (b.flag) 84f else 0f
            val labelH = if (b.flag) 40f else 0f
            if (!c.dry) drawChart(c, pts, ix + axisW, iy + h, iw - axisW, chartH, b.variant == 1, b.flag)
            h + chartH + labelH
        }
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
            var h = c.para(note, c.ink(46f, false, st.textColor, 0.95f), x, y, w, 10)
            if (date.isNotBlank()) {
                h += 12f
                h += c.para(date, c.ink(28f, false, st.textColor, 0.6f), x, y + h, w, 1)
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
            h += c.para(note, c.ink(46f, false, st.textColor), ix, iy + h, iw, 8)
            if (date.isNotBlank()) {
                h += 14f
                h += c.para("— $date", c.ink(28f, false, st.textColor, 0.6f), ix, iy + h, iw, 1)
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
                val l = c.layout(s, c.ink(48f, false, st.textColor), w - bar - gap, 8, false)
                if (!c.dry) {
                    c.fill.color = st.accent
                    c.fill.alpha = 255
                    c.canvas.drawRoundRect(RectF(x, y, x + bar, y + l.height), 4f, 4f, c.fill)
                }
                c.drawLayout(l, x + bar + gap, y)
                l.height.toFloat()
            }
            2 -> c.para(s, c.ink(104f, true, st.textColor), x, y, w, 6)
            else -> c.para(s, c.ink(46f, false, st.textColor, 0.92f), x, y, w, 10)
        }
    }
}
