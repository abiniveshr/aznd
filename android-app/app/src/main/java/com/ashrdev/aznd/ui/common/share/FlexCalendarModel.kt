package com.ashrdev.aznd.ui.common.share

import com.ashrdev.aznd.ui.calendar.CalendarView
import com.ashrdev.aznd.ui.calendar.CalendarZoom
import com.ashrdev.aznd.ui.calendar.MonthKey
import com.ashrdev.aznd.ui.calendar.calendarViews
import com.ashrdev.aznd.ui.calendar.currentMonth
import com.ashrdev.aznd.ui.calendar.monthKeyOf
import com.ashrdev.aznd.ui.calendar.valid
import java.util.Calendar

// What the Flex calendar block is made of and how its shape follows its view. Nothing here touches Android, so the
// tiling harness compiles and checks it; the painter (FlexPainter) and the edit page (FlexEditPage) use it.

/** How a highlighted day is marked: a filled shape (the menu calendar's highlight) or a ring around the day. */
enum class FlexDayMark { FILL, OUTLINE }

/**
 * One highlighted day of a calendar source. [alpha] (0 to 1) is how strongly the highlight is drawn, 1 for a plain
 * highlight; a score-based source sets it per day. [color] is an ARGB colour for the mark, 0 for the card's accent.
 */
data class FlexDay(
    val mark: FlexDayMark = FlexDayMark.FILL,
    val alpha: Float = 1f,
    val color: Int = 0
) {
    /** [alpha] as the painter uses it: from 0 to 1, a broken value counting as 1. */
    val strength: Float get() = if (alpha.isNaN()) 1f else alpha.coerceIn(0f, 1f)
}

/**
 * Which days a calendar highlights: what a screen offers the Calendar block to show (a streak's kept days, the days
 * trained). [key] identifies it in a saved card, [days] maps a day key ("yyyy-MM-dd") to how it is marked, [firstDay]
 * is the earliest day it has (the first month a person can pick), "" when it has none.
 */
data class FlexCalendarSource(
    val key: String,
    val label: String,
    val days: Map<String, FlexDay>,
    val firstDay: String = ""
)

/**
 * What a Calendar block shows. A block always shows one period: [zoom] says whether it is a month, a quadrant (four
 * months) or a year, [year] and [index] which one (index: 0 for a year, 0 to 2 for a quadrant, 1 to 12 for a month).
 * [year] 0 means "the screen's own month": the period at this zoom that holds it, so a card saved before periods could
 * be picked keeps showing the month it showed. [source] is a [FlexCalendarSource.key], "" for the first the screen
 * offers. [weekStart] is a java.util.Calendar weekday constant.
 */
data class FlexCalendarSpec(
    val zoom: CalendarZoom = CalendarZoom.MONTH,
    val year: Int = 0,
    val index: Int = 0,
    val source: String = "",
    val weekStart: Int = Calendar.SUNDAY
)

/** The period of this spec; [anchor] is the month the screen is about, used while [FlexCalendarSpec.year] is 0. */
internal fun FlexCalendarSpec.view(anchor: MonthKey): CalendarView =
    if (year <= 0) CalendarView.of(zoom, anchor) else CalendarView(zoom, year, index).valid()

/** The source [key] names, or the first when it names none (a streak that was deleted since the card was saved). */
fun List<FlexCalendarSource>.sourceFor(key: String): FlexCalendarSource? = firstOrNull { it.key == key } ?: firstOrNull()

/** The month a screen is about: that of [day] when it is a day key, else that of [today], else this month. */
fun flexCalendarAnchor(day: String, today: String): MonthKey =
    monthOfOrNull(day) ?: monthOfOrNull(today) ?: currentMonth()

private fun monthOfOrNull(dayKey: String): MonthKey? {
    if (dayKey.length < 10) return null
    val month = runCatching { monthKeyOf(dayKey) }.getOrNull() ?: return null
    return month.takeIf { it.month in 1..12 && it.year > 0 }
}

/** The first and last month a person can pick for [source]: from its first day (or [today]'s month) to [today]'s month. */
fun flexCalendarRange(source: FlexCalendarSource?, today: MonthKey): Pair<MonthKey, MonthKey> {
    val first = source?.firstDay?.let { monthOfOrNull(it) } ?: today
    return if (first <= today) first to today else today to today
}

/** The years offered in the year dropdown: [first] to [last] (at most the 30 latest), always including [shown], newest first. */
fun flexCalendarYears(first: MonthKey, last: MonthKey, shown: Int): List<Int> {
    val from = maxOf(first.year, last.year - 29)
    return ((from..last.year).toSet() + shown).sortedDescending()
}

/** The views of [zoom] a person can add for [first] to [last], newest first, without the [taken] ones, at most [max]. */
internal fun flexCalendarChoices(zoom: CalendarZoom, first: MonthKey, last: MonthKey, taken: Set<CalendarView>, max: Int = 36): List<CalendarView> =
    calendarViews(zoom, first, last).asReversed().filter { it !in taken }.take(max)

/**
 * The size a Calendar block asks for, from the view it shows. The numbers are in the block's own units (before its
 * size and the module size), the same way the chart's are. The day cells are square, so the shape follows the view's
 * columns (weeks) and rows (weekdays times bands): a month is nearly square, a quadrant a little taller, a year taller still.
 */
object FlexCalendarShape {
    /** The column of weekday letters, a month name's line, and the gap between two bands of months. */
    const val WEEKDAY_W = 24f
    const val LABEL_H = 28f
    const val BAND_GAP = 18f

    /** A day cell wider than this has its number written in it (below, it is a plain shape). */
    const val NUMBER_MIN = 32f

    /** A day cell at its natural size and the least it reads at, per zoom. */
    fun naturalCell(zoom: CalendarZoom): Float = when (zoom) {
        CalendarZoom.MONTH -> 64f
        CalendarZoom.QUADRANT -> 50f
        CalendarZoom.YEAR -> 40f
    }

    fun leastCell(zoom: CalendarZoom): Float = when (zoom) {
        CalendarZoom.MONTH -> 38f
        CalendarZoom.QUADRANT -> 30f
        CalendarZoom.YEAR -> 22f
    }

    class Size(
        val naturalW: Float,
        val naturalH: Float,
        val minW: Float,
        val minH: Float,
        val ratioMin: Float,
        val ratioMax: Float
    ) {
        val ratio: Float get() = naturalW / naturalH
    }

    /** Width and height of a grid of [cell] sided days for [zoom] (without the block's padding and head). */
    fun grid(zoom: CalendarZoom, cell: Float, weekdays: Boolean): Pair<Float, Float> {
        val view = CalendarView(zoom, 2000, if (zoom == CalendarZoom.MONTH) 1 else 0)
        val bands = view.monthRanges.size
        val w = (if (weekdays) WEEKDAY_W else 0f) + view.columns * cell
        val h = bands * (LABEL_H + 7 * cell) + (bands - 1) * BAND_GAP
        return w to h
    }

    /**
     * The size of a block that shows [zoom]: [pad] is the panel padding, [head] the height of the lines above the grid
     * (source name and title), [weekdays] whether the weekday letters are shown, [k] the scale the block is drawn at
     * (it multiplies the least size, as a chart's does).
     */
    fun of(zoom: CalendarZoom, pad: Float, head: Float, weekdays: Boolean, k: Float): Size {
        val (nw, nh) = grid(zoom, naturalCell(zoom), weekdays)
        val (lw, lh) = grid(zoom, leastCell(zoom), weekdays)
        val natW = nw + 2 * pad
        val natH = head + nh + 2 * pad
        val ratio = natW / natH
        return Size(natW, natH, (lw + 2 * pad) * k, (head + lh + 2 * pad) * k, ratio * 0.7f, ratio * 1.6f)
    }
}
