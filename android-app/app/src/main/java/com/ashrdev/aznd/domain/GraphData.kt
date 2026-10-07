package com.ashrdev.aznd.domain

import java.util.Calendar
import java.util.Locale

/**
 * How far back every graph looks. ONE setting for the whole app (kept by GraphRangeStore); each
 * page decides what its graph shows, this only decides the time window.
 * Asking for more than there is data for simply shows everything (same as MAX).
 */
enum class GraphRange(val label: String, val months: Int? = null, val recent: Int? = null) {
    RECENT_5("Recent 5", recent = 5),
    MONTHS_3("3 months", months = 3),
    MONTHS_6("6 months", months = 6),
    YEAR_1("1 year", months = 12),
    YEARS_2("2 years", months = 24),
    YEARS_3("3 years", months = 36),
    MAX("Max");

    companion object {
        fun fromName(name: String?): GraphRange = values().firstOrNull { it.name == name } ?: MONTHS_3
    }
}

/**
 * One dot / bar of a graph. [detail] is the small second line shown when the point is selected;
 * [valueText], when set, replaces the "value unit" text of the readout (e.g. "BW 76 + 19 = 95 kg").
 */
data class GraphPoint(
    val timeMs: Long,
    val value: Double,
    val detail: String? = null,
    val valueText: String? = null
)

enum class GraphStyle { LINE, BARS }

/**
 * One thing a page can graph (volume, calories, ...). The page builds these; the common graph
 * draws whichever one is selected. [startAtZero] false suits values like 1RM that hover in a band.
 */
data class GraphMode(
    val id: String,
    val label: String,
    val unit: String,
    val style: GraphStyle,
    val points: List<GraphPoint>,
    val startAtZero: Boolean = true
)

/** Picking the part of a series a [GraphRange] shows. Pure, so any page can reuse it. */
object GraphWindow {
    private const val WEEK_MS = 7 * 86_400_000L

    fun monthsBack(nowMs: Long, months: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = nowMs
            add(Calendar.MONTH, -months)
        }.timeInMillis

    /** The items inside [range], oldest first. */
    fun <T> select(items: List<T>, range: GraphRange, nowMs: Long, time: (T) -> Long): List<T> {
        val sorted = items.sortedBy(time)
        val recent = range.recent
        val months = range.months
        return when {
            recent != null -> sorted.takeLast(recent)
            months != null -> {
                val start = monthsBack(nowMs, months)
                sorted.filter { time(it) >= start }
            }
            else -> sorted
        }
    }

    fun selectPoints(points: List<GraphPoint>, range: GraphRange, nowMs: Long): List<GraphPoint> =
        select(points, range, nowMs) { it.timeMs }

    /** True when the range reaches back at least to the first item, i.e. everything is shown. */
    fun <T> coversAll(items: List<T>, range: GraphRange, nowMs: Long, time: (T) -> Long): Boolean {
        if (items.isEmpty()) return true
        val recent = range.recent
        val months = range.months
        return when {
            recent != null -> items.size <= recent
            months != null -> monthsBack(nowMs, months) <= items.minOf(time)
            else -> true
        }
    }

    /** Where the shown window effectively starts: never earlier than the first item. */
    fun <T> windowStartMs(items: List<T>, range: GraphRange, nowMs: Long, time: (T) -> Long): Long? {
        if (items.isEmpty()) return null
        val first = items.minOf(time)
        val months = range.months
        return when {
            range.recent != null -> select(items, range, nowMs, time).firstOrNull()?.let(time)
            months != null -> maxOf(monthsBack(nowMs, months), first)
            else -> first
        }
    }

    /** Length of the shown window in weeks, at least 1 (so "per week" never explodes on a new account). */
    fun <T> windowWeeks(items: List<T>, range: GraphRange, nowMs: Long, time: (T) -> Long): Double {
        val start = windowStartMs(items, range, nowMs, time) ?: return 1.0
        return ((nowMs - start) / WEEK_MS.toDouble()).coerceAtLeast(1.0)
    }

    /** 1,240 / 82 / 12.5 : whole numbers stay whole, small fractions get one decimal. */
    fun formatValue(v: Double): String {
        val locale = Locale.getDefault()
        return when {
            kotlin.math.abs(v) >= 1000 -> String.format(locale, "%,.0f", v)
            kotlin.math.abs(v - Math.rint(v)) < 0.05 -> String.format(locale, "%.0f", v)
            else -> String.format(locale, "%.1f", v)
        }
    }
}
