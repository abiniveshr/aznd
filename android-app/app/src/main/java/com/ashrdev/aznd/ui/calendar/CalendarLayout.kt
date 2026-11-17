package com.ashrdev.aznd.ui.calendar

// The geometry of the calendar, with nothing from Android or Compose in it: which months a view holds, how the weeks
// are laid out, where each day sits, and the arithmetic for stepping between periods. The menu calendar (CalendarGrid,
// ZoomCalendar) and the Flex card's calendar block both draw from it, so they cannot disagree about a layout. It is
// compiled by the tiling harness, which checks it.

/** How much of the year a calendar shows: all of it, a third of it (four months), or one month. */
enum class CalendarZoom { YEAR, QUADRANT, MONTH }

internal val weekdayLetters = listOf("S", "M", "T", "W", "T", "F", "S")

internal class YearLayout(val year: Int, firstDayOfWeek: Int) {
    // Ordinal (0-based day of the year) of each month's first day; the last entry is the day count.
    private val monthFirstOrdinal = IntArray(13).also { first ->
        for (m in 1..12) first[m] = first[m - 1] + MonthKey(year, m).daysInMonth()
    }

    private val dayCount = monthFirstOrdinal[12]

    private val monthOf = IntArray(dayCount).also { months ->
        for (m in 1..12) for (i in monthFirstOrdinal[m - 1] until monthFirstOrdinal[m]) months[i] = m
    }

    val dayKeys: List<String> = run {
        val out = ArrayList<String>(dayCount)
        val key = StringBuilder(10)
        for (m in 1..12) for (d in 1..(monthFirstOrdinal[m] - monthFirstOrdinal[m - 1])) {
            key.setLength(0)
            appendPadded(key, year, 4).append('-')
            appendPadded(key, m, 2).append('-')
            appendPadded(key, d, 2)
            out.add(key.toString())
        }
        out
    }

    val leading: Int = (MonthKey(year, 1).firstWeekday() - firstDayOfWeek + 7) % 7

    val weeks: Int = (leading + dayCount + 6) / 7

    val monthStartWeek: List<Int> = (1..12).map { m -> (leading + monthFirstOrdinal[m - 1]) / 7 }

    fun dayKeyAt(week: Int, row: Int): String? = dayKeys.getOrNull(week * 7 + row - leading)

    /** The month (1..12) of the day at [week], [row], or 0 where the grid has no day. */
    fun monthAt(week: Int, row: Int): Int {
        val i = week * 7 + row - leading
        return if (i in 0 until dayCount) monthOf[i] else 0
    }

    /** The day of the month of the day at [week], [row], or 0 where the grid has no day. */
    fun dayOfMonthAt(week: Int, row: Int): Int {
        val i = week * 7 + row - leading
        return if (i in 0 until dayCount) i - monthFirstOrdinal[monthOf[i] - 1] + 1 else 0
    }

    fun weekOf(dayKey: String): Int? {
        if (dayKey.length != 10) return null
        val y = digits(dayKey, 0, 4)
        val m = digits(dayKey, 5, 2)
        val d = digits(dayKey, 8, 2)
        if (y != year || m !in 1..12 || d < 1 || d > monthFirstOrdinal[m] - monthFirstOrdinal[m - 1]) return null
        return (leading + monthFirstOrdinal[m - 1] + d - 1) / 7
    }

    fun monthEndWeek(month: Int): Int =
        if (month in 1..12) (leading + monthFirstOrdinal[month] - 1) / 7 else weeks - 1

    companion object {
        private val cache = object : LinkedHashMap<Long, YearLayout>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, YearLayout>?): Boolean = size > 8
        }

        /** The layout of [year] for a week starting on [firstDayOfWeek], built once and shared. */
        fun of(year: Int, firstDayOfWeek: Int): YearLayout = synchronized(cache) {
            cache.getOrPut(year * 8L + firstDayOfWeek) { YearLayout(year, firstDayOfWeek) }
        }

        private fun appendPadded(out: StringBuilder, value: Int, width: Int): StringBuilder {
            val text = value.toString()
            for (i in text.length until width) out.append('0')
            return out.append(text)
        }

        private fun digits(text: String, start: Int, count: Int): Int {
            var n = 0
            for (i in start until start + count) {
                val c = text[i]
                if (c !in '0'..'9') return -1
                n = n * 10 + (c - '0')
            }
            return n
        }
    }
}

internal class YearBand(val months: IntRange, val startWeek: Int, val endWeek: Int) {
    val weekCount: Int get() = endWeek - startWeek + 1

    fun dayKeyAt(layout: YearLayout, week: Int, row: Int): String? =
        layout.dayKeyAt(week, row)?.takeIf { layout.monthAt(week, row) in months }
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
    private val layout = YearLayout.of(view.year, firstDayOfWeek)
    val bands: List<YearBand> = layout.bandsFor(view)

    /** Weeks (columns) in each band. */
    val weekCounts: List<Int> = bands.map { it.weekCount }

    val slots: List<CalendarSlot> = buildList {
        bands.forEachIndexed { bandIndex, band ->
            for (week in band.startWeek..band.endWeek) {
                for (row in 0 until 7) {
                    val key = band.dayKeyAt(layout, week, row) ?: continue
                    add(CalendarSlot(key, layout.monthAt(week, row), layout.dayOfMonthAt(week, row), bandIndex, week - band.startWeek, row))
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
