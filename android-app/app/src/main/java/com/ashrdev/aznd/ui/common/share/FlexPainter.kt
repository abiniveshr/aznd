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
 * Draws a Flex card with plain android.graphics, in a fixed 1080-wide design space that is scaled to
 * whatever width it is asked for. The editor preview (a Compose Canvas) and the exported PNG both
 * call [FlexPainter.draw], so what you see is what you share.
 *
 * Layout is a vertical stack of blocks. Every block function takes its box (x, y, width), draws itself
 * and returns its height; while [Ctx.dry] is set nothing is drawn, so the same code also measures.
 * That is what lets a card shrink its content to fit and lets panels wrap their content.
 */
object FlexPainter {

    fun draw(canvas: Canvas, targetWidthPx: Float, spec: FlexSpec, content: FlexContent, assets: FlexAssets) {
        val st = spec.style
        val w = FLEX_CARD_WIDTH.toFloat()
        val h = st.aspect.height.toFloat()
        val outer = canvas.save()
        val scale = targetWidthPx / w
        canvas.scale(scale, scale)

        // Rounded card corners are cut out of a layer so the edge is smooth and the corners are transparent.
        val rounded = st.cardRadius > 0
        val layer = if (rounded) canvas.saveLayer(0f, 0f, w, h, null) else -1

        val ctx = Ctx(canvas, st, assets)
        drawBackground(ctx, w, h)
        drawContent(ctx, spec, content, w, h)
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
        val center = st.align == FlexAlign.CENTER

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

        /** The largest paint up to [maxSize] with [s] fitting [width] on one line. */
        fun fit(s: String, bold: Boolean, color: Int, maxSize: Float, width: Float, alpha: Float = 1f): TextPaint {
            var size = maxSize
            var p = ink(size, bold, color, alpha)
            while (p.measureText(s) > width && size > 18f) {
                size *= 0.93f
                p = ink(size, bold, color, alpha)
            }
            return p
        }

        fun lineH(p: Paint): Float = p.descent() - p.ascent()

        fun layout(s: CharSequence, p: TextPaint, width: Float, maxLines: Int, centered: Boolean): StaticLayout =
            StaticLayout.Builder.obtain(s, 0, s.length, p, width.toInt().coerceAtLeast(1))
                .setAlignment(if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build()

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
                    fill.color = st.panelColor
                    fill.alpha = (st.panelAlpha * 255f).roundToInt().coerceIn(0, 255)
                    canvas.drawRoundRect(r, rad, rad, fill)
                }
                FlexPanel.OUTLINE -> {
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

        /** Centre-crops [bmp] into [dst], with rounded corners of [radius]. */
        fun image(bmp: Bitmap, dst: RectF, radius: Float) {
            if (dry || dst.width() <= 0f || dst.height() <= 0f) return
            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()
            val dstAspect = dst.width() / dst.height()
            val src = if (bw / bh > dstAspect) {
                val sw = bh * dstAspect
                val left = (bw - sw) / 2f
                Rect(left.toInt(), 0, (left + sw).toInt(), bh.toInt())
            } else {
                val sh = bw / dstAspect
                val top = (bh - sh) / 2f
                Rect(0, top.toInt(), bw.toInt(), (top + sh).toInt())
            }
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
            st.bgMode == FlexBgMode.SOLID -> {
                paint.color = st.bgA
                c.canvas.drawRect(0f, 0f, w, h, paint)
            }
            else -> {
                val rad = Math.toRadians(st.gradientAngle.toDouble())
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()
                val half = (abs(w * dx) + abs(h * dy)) / 2f
                val cx = w / 2f
                val cy = h / 2f
                paint.shader = LinearGradient(
                    cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half,
                    st.bgA, st.bgB, Shader.TileMode.CLAMP
                )
                c.canvas.drawRect(0f, 0f, w, h, paint)
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

    private fun drawContent(c: Ctx, spec: FlexSpec, content: FlexContent, w: Float, h: Float) {
        val st = spec.style
        val pad = st.padding.toFloat()
        val showMark = st.watermark && st.watermarkText.isNotBlank()
        val footer = if (showMark) 56f else 0f
        val availW = w - 2 * pad
        val availH = h - 2 * pad - footer

        // Shrink everything uniformly until the stack fits; the width the blocks lay out in grows to match.
        var s = 1f
        var contentH = 0f
        c.dry = true
        while (true) {
            contentH = drawBlocks(c, spec, content, availW / s)
            if (contentH * s <= availH || s <= 0.45f) break
            s -= 0.05f
        }
        c.dry = false

        val used = contentH * s
        val top = when (st.vAlign) {
            FlexVAlign.TOP -> 0f
            FlexVAlign.CENTER -> max(0f, (availH - used) / 2f)
            FlexVAlign.BOTTOM -> max(0f, availH - used)
        }
        c.canvas.save()
        c.canvas.translate(pad, pad + top)
        c.canvas.scale(s, s)
        drawBlocks(c, spec, content, availW / s)
        c.canvas.restore()

        if (showMark) {
            val p = c.ink(26f, true, st.textColor, 0.5f, 0.1f)
            val baseline = h - max(pad * 0.55f, 40f)
            p.textAlign = if (c.center) Paint.Align.CENTER else Paint.Align.LEFT
            c.canvas.drawText(st.watermarkText, if (c.center) w / 2f else pad, baseline, p)
            p.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawBlocks(c: Ctx, spec: FlexSpec, content: FlexContent, w: Float): Float {
        var y = 0f
        var first = true
        for (b in spec.blocks) {
            if (!b.enabled || !content.has(b.type)) continue
            val gap = if (first) 0f else spec.style.spacing.toFloat()
            val bh = block(c, b, content, 0f, y + gap, w)
            if (bh > 0f) {
                y += gap + bh
                first = false
            }
        }
        return y
    }

    private fun block(c: Ctx, b: FlexBlock, ct: FlexContent, x: Float, y: Float, w: Float): Float = when (b.type) {
        FlexBlockType.HEADER -> header(c, b, ct, x, y, w)
        FlexBlockType.STREAKS -> streaks(c, b, ct, x, y, w)
        FlexBlockType.TOP_SET -> topSet(c, b, ct, x, y, w)
        FlexBlockType.ONE_RM -> oneRm(c, b, ct, x, y, w)
        FlexBlockType.SESSION_STATS -> sessionStats(c, b, ct, x, y, w)
        FlexBlockType.EXERCISES -> exercises(c, b, ct, x, y, w)
        FlexBlockType.ROUTINE -> routine(c, b, ct, x, y, w)
        FlexBlockType.CHART -> chart(c, b, ct, x, y, w)
        FlexBlockType.DAY_NOTE -> dayNote(c, b, ct, x, y, w)
        FlexBlockType.PHOTO -> photo(c, b, ct, x, y, w)
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
    private fun liftMeta(l: FlexLiftItem): String =
        listOfNotNull(l.rpe?.let { "RPE ${formatWeight(it)}" }, fmtDate(l.dateMs)).joinToString(" · ")

    /** A big number with a small unit after it, sharing a baseline, left or centre aligned per the card. */
    private fun numberWithUnit(c: Ctx, x: Float, w: Float, y: Float, num: String, unit: String, maxSize: Float): Float {
        val st = c.st
        val unitP = c.ink(30f, false, st.textColor, 0.7f)
        val unitW = unitP.measureText(unit) + 12f
        val numP = c.fit(num, true, st.accent, maxSize, (w - unitW).coerceAtLeast(40f))
        val numW = numP.measureText(num)
        val startX = if (c.center) x + (w - numW - unitW) / 2f else x
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
            h += numberWithUnit(c, ix, iw, iy + h, s.days.toString(), if (s.days == 1) "day" else "days", 110f)
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
            val ax = if (c.center) ix + iw / 2f else ix
            val al = if (c.center) Paint.Align.CENTER else Paint.Align.LEFT
            val numP = c.fit(s.days.toString(), true, st.accent, 300f, iw)
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
                val ax = if (c.center) ix + iw / 2f else ix
                val al = if (c.center) Paint.Align.CENTER else Paint.Align.LEFT
                h += c.line(big, c.fit(big, true, st.accent, 150f, iw), ax, iy + h, al)
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
                    h += numberWithUnit(c, ix, iw, iy + h, one(l.e1rm), "kg", 260f)
                    h += 6f
                    h += c.para(l.name, c.ink(42f, true, st.textColor), ix, iy + h, iw, 2)
                    if (b.flag) {
                        h += 6f
                        h += c.para("from ${setText(l)}", c.ink(30f, false, st.textColor, 0.65f), ix, iy + h, iw, 1)
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
                        h += numberWithUnit(c, ix, iw, iy + h, one(l.e1rm), "kg", 100f)
                        if (b.flag) {
                            h += 6f
                            h += c.para(setText(l), c.ink(24f, false, st.textColor, 0.65f), ix, iy + h, iw, 1)
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
                    val metaL = if (b.flag) c.layout("from ${setText(l)}", c.ink(26f, false, st.textColor, 0.65f), leftW, 1, false) else null
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
            val valueP = c.fit(s.value, true, st.accent, 60f, iw)
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
            val ax = if (c.center) ix + iw / 2f else ix
            val al = if (c.center) Paint.Align.CENTER else Paint.Align.LEFT
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

    private fun photo(c: Ctx, b: FlexBlock, ct: FlexContent, x: Float, y: Float, w: Float): Float {
        val st = c.st
        val uri = b.keys?.firstOrNull() ?: ct.photoUri ?: return 0f
        val bmp = c.assets.images[uri] ?: return 0f
        val aspect = bmp.height.toFloat() / bmp.width
        return when (b.variant) {
            1 -> {
                val d = min(w, 440f)
                val dx = if (c.center) x + (w - d) / 2f else x
                c.image(bmp, RectF(dx, y, dx + d, y + d), d / 2f)
                d
            }
            2 -> {
                val ph = (w * aspect).coerceIn(220f, 760f)
                c.image(bmp, RectF(x, y, x + w, y + ph), 0f)
                ph
            }
            else -> {
                val ph = (w * aspect).coerceIn(220f, 620f)
                c.image(bmp, RectF(x, y, x + w, y + ph), max(st.panelRadius.toFloat(), 24f))
                ph
            }
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
