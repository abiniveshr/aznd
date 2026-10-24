package com.ashrdev.aznd.ui.dashboard

import kotlin.math.max
import kotlin.math.min

interface DashMeasure {
    fun width(text: String, sp: Float, bold: Boolean): Float
    fun line(sp: Float): Float
}

object DashMetrics {
    const val PAD = 14f
    const val GAP = 12f
    const val ICON = 22f
    const val SHORTCUT_ICON = 26f
    const val LABEL = 12f
    const val NAME = 15f
    const val BIG = 30f
    const val VALUE = 18f
    const val SMALL = 11f
    const val ROW = 15f
    const val NAME_MAX = 170f
    const val WORKOUT_NAME_MAX = 210f
    const val FIGURE_GAP = 14f
    const val SIDE_GAP = 16f

    const val STREAK_ROW = 2
    const val STREAK_STACK = 1
    const val WORKOUT_ROW = 4
    const val WORKOUT_SPLIT = 3
    const val WORKOUT_LIST = 1
    const val SHORTCUT_ROW = 2
    const val SHORTCUT_STACK = 1
    const val LIFT_ROW = 2
    const val LIFT_STACK = 1
}

class StreakContent(val name: String, val days: String, val unit: String)

class WorkoutFigure(val label: String, val value: String)

class WorkoutContent(val heading: String, val name: String, val whenText: String, val figures: List<WorkoutFigure>)

class ShortcutContent(val label: String)

class LiftContent(val heading: String, val name: String, val e1rm: String, val topSet: String)

object DashboardShapes {
    private val M = DashMetrics

    fun streak(c: StreakContent, m: DashMeasure, f: Float): DashShape {
        val hw = M.ICON + 6f + min(m.width(c.name, M.NAME, true), M.NAME_MAX)
        val hh = max(M.ICON, m.line(M.NAME))
        val cw = m.width(c.days, M.BIG, true) + 6f + m.width(c.unit, M.LABEL, false)
        val ch = m.line(M.BIG)
        return DashShape.Stat(
            DashboardLayout.stat()
                .layout(M.STREAK_ROW, (2 * M.PAD + hw + M.SIDE_GAP + cw) * f, (2 * M.PAD + max(hh, ch)) * f)
                .layout(M.STREAK_STACK, (2 * M.PAD + max(hw, cw)) * f, (2 * M.PAD + hh + 4f + ch) * f)
                .build(1)
        )
    }

    fun workout(c: WorkoutContent, m: DashMeasure, f: Float): DashShape {
        val hw = maxOf(
            m.width(c.heading, M.LABEL, false),
            min(m.width(c.name, M.NAME, true), M.WORKOUT_NAME_MAX),
            m.width(c.whenText, M.LABEL, false)
        )
        val hh = m.line(M.LABEL) * 2 + m.line(M.NAME) + 4f
        val b = DashboardLayout.stat()
        if (c.figures.isEmpty()) {
            return DashShape.Stat(b.layout(M.WORKOUT_LIST, (2 * M.PAD + hw) * f, (2 * M.PAD + hh) * f).build(M.WORKOUT_LIST))
        }
        val n = c.figures.size
        val cellW = c.figures.map { max(m.width(it.value, M.VALUE, true), m.width(it.label, M.SMALL, false)) }
        val cellH = m.line(M.VALUE) + m.line(M.SMALL)
        val rowW = cellW.sum() + (n - 1) * M.FIGURE_GAP
        val listW = (c.figures.maxOfOrNull { m.width(it.label, M.LABEL, false) } ?: 0f) + 12f +
            (c.figures.maxOfOrNull { m.width(it.value, M.ROW, true) } ?: 0f)
        val listH = n * m.line(M.ROW) + (n - 1) * 2f
        return DashShape.Stat(
            b.layout(M.WORKOUT_ROW, (2 * M.PAD + hw + M.SIDE_GAP + rowW) * f, (2 * M.PAD + max(hh, cellH)) * f)
                .layout(M.WORKOUT_SPLIT, (2 * M.PAD + max(hw, rowW)) * f, (2 * M.PAD + hh + 10f + cellH) * f)
                .layout(M.WORKOUT_LIST, (2 * M.PAD + max(hw, listW)) * f, (2 * M.PAD + hh + 8f + listH) * f)
                .build(M.WORKOUT_ROW)
        )
    }

    fun shortcut(c: ShortcutContent, m: DashMeasure, f: Float): DashShape {
        val lw = min(m.width(c.label, M.NAME, true), M.NAME_MAX)
        val lh = m.line(M.NAME)
        return DashShape.Stat(
            DashboardLayout.stat()
                .layout(M.SHORTCUT_ROW, (2 * M.PAD + M.SHORTCUT_ICON + 10f + lw) * f, (2 * M.PAD + max(M.SHORTCUT_ICON, lh)) * f)
                .layout(M.SHORTCUT_STACK, (2 * M.PAD + max(M.SHORTCUT_ICON, lw)) * f, (2 * M.PAD + M.SHORTCUT_ICON + 6f + lh) * f)
                .build(1)
        )
    }

    fun lift(c: LiftContent, m: DashMeasure, f: Float): DashShape {
        val head = min(m.width(c.heading, M.LABEL, false), M.NAME_MAX)
        val name = min(m.width(c.name, M.NAME, true), M.NAME_MAX)
        val top = min(m.width(c.topSet, M.LABEL, false), M.NAME_MAX)
        val e1 = m.width(c.e1rm, M.VALUE, true)
        val leftH = m.line(M.LABEL) * 2 + m.line(M.NAME)
        return DashShape.Stat(
            DashboardLayout.stat()
                .layout(M.LIFT_ROW, (2 * M.PAD + maxOf(head, name, top) + M.SIDE_GAP + e1) * f, (2 * M.PAD + max(leftH, m.line(M.VALUE))) * f)
                .layout(M.LIFT_STACK, (2 * M.PAD + maxOf(head, name, top, e1)) * f, (2 * M.PAD + leftH + m.line(M.VALUE)) * f)
                .build(1)
        )
    }

    fun calories(f: Float): DashShape =
        DashShape.Picture(DashboardLayout.picture(true, 1.2f, 0.9f, 2.2f, 140f * f, 130f * f, 520f * f, 360f * f))

    fun graph(f: Float): DashShape =
        DashShape.Picture(DashboardLayout.picture(true, 2f, 1.25f, 3.8f, 170f * f, 120f * f, 2000f, 320f * f))

    fun model(f: Float): DashShape =
        DashShape.Picture(DashboardLayout.picture(true, 0.85f, 0.55f, 1.7f, 110f * f, 150f * f, 360f * f, 440f * f))
}
