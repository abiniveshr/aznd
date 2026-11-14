package com.ashrdev.aznd.ui.calendar

// The geometry of the calendar, with nothing from Android or Compose in it: which months a view holds, how the weeks
// are laid out, where each day sits, and the arithmetic for stepping between periods. The menu calendar (CalendarGrid,
// ZoomCalendar) and the Flex card's calendar block both draw from it, so they cannot disagree about a layout. It is
// compiled by the tiling harness, which checks it.

/** How much of the year a calendar shows: all of it, a third of it (four months), or one month. */
enum class CalendarZoom { YEAR, QUADRANT, MONTH }

internal val weekdayLetters = listOf("S", "M", "T", "W", "T", "F", "S")

internal class YearLayout(val year: Int, firstDayOfWeek: Int) {
    val dayKeys: List<String> = (1..12).flatMap { m ->
        val month = MonthKey(year, m)
        (1..month.daysInMonth()).map { month.dayKey(it) }
    }

    val leading: Int = (MonthKey(year, 1).firstWeekday() - firstDayOfWeek + 7) % 7

    val weeks: Int = (leading + dayKeys.size + 6) / 7

    val monthStartWeek: List<Int> = run {
        var ordinal = 0
        (1..12).map { m ->
            ((leading + ordinal) / 7).also { ordinal += MonthKey(year, m).daysInMonth() }
        }
    }

    fun dayKeyAt(week: Int, row: Int): String? = dayKeys.getOrNull(week * 7 + row - leading)

    fun weekOf(dayKey: String): Int? =
        dayKeys.indexOf(dayKey).takeIf { it >= 0 }?.let { (leading + it) / 7 }

    fun monthEndWeek(month: Int): Int =
        weekOf(MonthKey(year, month).let { it.dayKey(it.daysInMonth()) }) ?: (weeks - 1)
}

internal class YearBand(val months: IntRange, val startWeek: Int, val endWeek: Int) {
    val weekCount: Int get() = endWeek - startWeek + 1

    fun dayKeyAt(layout: YearLayout, week: Int, row: Int): String? =
        layout.dayKeyAt(week, row)?.takeIf { monthKeyOf(it).month in months }
}

internal data class CalendarView(val zoom: CalendarZoom, val year: Int, val index: Int) {
    val monthRanges: List<IntRange>
        get() = when (zoom) {
            CalendarZoom.YEAR -> listOf(1..4, 5..8, 9..12)
            CalendarZoom.QUADRANT -> {
                val start = index * 4 + 1
                listOf(start..start + 1, start + 2..start + 3)
            }
            CalendarZoom.MONTH -> listOf(index..index)
        }

    val columns: Int
        get() = when (zoom) {
            CalendarZoom.YEAR -> 19
            CalendarZoom.QUADRANT -> 10
            CalendarZoom.MONTH -> 6
        }

    val months: List<MonthKey>
        get() = monthRanges.flatMap { range -> range.map { MonthKey(year, it) } }

    companion object {
        fun of(zoom: CalendarZoom, month: MonthKey): CalendarView = CalendarView(
            zoom,
            month.year,
            when (zoom) {
                CalendarZoom.YEAR -> 0
                CalendarZoom.QUADRANT -> (month.month - 1) / 4
                CalendarZoom.MONTH -> month.month
            }
        )
    }
}

internal fun YearLayout.bandsFor(view: CalendarView): List<YearBand> =
    view.monthRanges.map { range ->
        YearBand(range, monthStartWeek[range.first - 1], monthEndWeek(range.last))
    }

internal fun CalendarView.firstMonth(): MonthKey = MonthKey(year, monthRanges.first().first)

internal fun CalendarView.lastMonth(): MonthKey = MonthKey(year, monthRanges.last().last)

internal fun CalendarView.title(): String = when (zoom) {
    CalendarZoom.YEAR -> year.toString()
    CalendarZoom.QUADRANT -> "${firstMonth().shortName()} – ${lastMonth().shortName()} $year"
    CalendarZoom.MONTH -> MonthKey(year, index).title()
}

internal fun CalendarView.step(direction: Int, first: MonthKey, last: MonthKey): MonthKey? {
    val target = when (zoom) {
        CalendarZoom.YEAR -> CalendarView(zoom, year + direction, 0)
        CalendarZoom.QUADRANT -> {
            val raw = index + direction
            CalendarView(zoom, year + Math.floorDiv(raw, 3), Math.floorMod(raw, 3))
        }
        CalendarZoom.MONTH -> CalendarView.of(zoom, MonthKey(year, index).let { if (direction < 0) it.previous() else it.next() })
    }
    val start = maxOf(target.firstMonth(), first)
    val end = minOf(target.lastMonth(), last)
    return if (start <= end) start else null
}

/** One day of a view placed in its grid: [band] counts from the top, [col] is the week within the band, [row] the weekday row. */
internal class CalendarSlot(val dayKey: String, val month: Int, val dayOfMonth: Int, val band: Int, val col: Int, val row: Int)

/** A month name that sits above [col] of [band]: the week the month's first day is in. */
internal class CalendarMonthLabel(val band: Int, val col: Int, val month: Int)

/**
 * A view laid out as the menu calendar draws it: weeks run across in columns, the weekdays down in seven rows, and the
 * view's months are stacked in [bands] (one band for a month, two for a quadrant, three for the year). Every day of the
 * view is in [slots] exactly once.
 */
internal class CalendarGridPlan(val view: CalendarView, val firstDayOfWeek: Int) {
    private val layout = YearLayout(view.year, firstDayOfWeek)
    val bands: List<YearBand> = layout.bandsFor(view)

    /** Weeks (columns) in each band. */
    val weekCounts: List<Int> = bands.map { it.weekCount }

    val slots: List<CalendarSlot> = buildList {
        bands.forEachIndexed { bandIndex, band ->
            for (week in band.startWeek..band.endWeek) {
                for (row in 0 until 7) {
                    val key = band.dayKeyAt(layout, week, row) ?: continue
                    add(CalendarSlot(key, monthKeyOf(key).month, dayOfMonthOf(key), bandIndex, week - band.startWeek, row))
                }
            }
        }
    }

    val labels: List<CalendarMonthLabel> = buildList {
        bands.forEachIndexed { bandIndex, band ->
            for (week in band.startWeek..band.endWeek) {
                val month = band.months.firstOrNull { layout.monthStartWeek[it - 1] == week } ?: continue
                add(CalendarMonthLabel(bandIndex, week - band.startWeek, month))
            }
        }
    }

    /** The weekday letter of [row], for the column down the left. */
    fun weekdayLetter(row: Int): String = weekdayLetters[(firstDayOfWeek - 1 + row) % 7]
}

/** The side of a day cell when the view's grid is fitted into [width] by [height] (the same rule as [cellSizeFor], in plain numbers). */
internal fun calendarCellSide(view: CalendarView, width: Float, height: Float, weekdayWidth: Float, labelHeight: Float, bandGap: Float): Float {
    val rows = view.monthRanges.size
    val byWidth = (width - weekdayWidth) / view.columns
    val byHeight = (height - labelHeight * rows - bandGap * (rows - 1)) / (7 * rows)
    return minOf(byWidth, byHeight).coerceAtLeast(4f)
}

/** The same view with its year and index made valid: a year 0, a quadrant 0 to 2, a month 1 to 12. */
internal fun CalendarView.valid(): CalendarView = CalendarView(
    zoom,
    year.coerceIn(1, 9999),
    when (zoom) {
        CalendarZoom.YEAR -> 0
        CalendarZoom.QUADRANT -> index.coerceIn(0, 2)
        CalendarZoom.MONTH -> index.coerceIn(1, 12)
    }
)

/** The view of [zoom] that holds this view's first month (a month's quadrant, a quadrant's first month, ...). */
internal fun CalendarView.inZoom(zoom: CalendarZoom): CalendarView = CalendarView.of(zoom, firstMonth())

/** The view after this one ([direction] 1) or before it (-1) at the same zoom, with no bounds. */
internal fun CalendarView.shifted(direction: Int): CalendarView = when (zoom) {
    CalendarZoom.YEAR -> CalendarView(zoom, year + direction, 0)
    CalendarZoom.QUADRANT -> {
        val raw = index + direction
        CalendarView(zoom, year + Math.floorDiv(raw, 3), Math.floorMod(raw, 3))
    }
    CalendarZoom.MONTH -> CalendarView.of(zoom, MonthKey(year, index).let { if (direction < 0) it.previous() else it.next() })
}

/** Every view of [zoom] that holds at least one month from [first] to [last], oldest first. */
internal fun calendarViews(zoom: CalendarZoom, first: MonthKey, last: MonthKey): List<CalendarView> {
    if (first > last) return emptyList()
    val out = ArrayList<CalendarView>()
    var cursor = CalendarView.of(zoom, first)
    while (cursor.firstMonth() <= last) {
        out += cursor
        cursor = cursor.shifted(1)
    }
    return out
}

/** The names of a year's three quadrants, as the menu calendar titles them ("Jan – Apr 2026"). */
internal fun quadrantTitles(year: Int): List<String> =
    (0..2).map { CalendarView(CalendarZoom.QUADRANT, year, it).title() }
