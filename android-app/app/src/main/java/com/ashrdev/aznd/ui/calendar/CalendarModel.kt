package com.ashrdev.aznd.ui.calendar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import com.ashrdev.aznd.ui.theme.readableOn

/** What fills the day's square. */
sealed interface DayFill {
    /** Nothing. */
    object None : DayFill

    /** The theme's highlight colour (follows Appearance settings). */
    object Highlight : DayFill

    /** Any colour you like, e.g. a score colour on the Dashboard. Text colour adapts. */
    data class Custom(val color: Color) : DayFill

    /**
     * The theme's highlight colour at a strength from 0 to 1. The strength *is* the colour's
     * opacity: 1 is solid (the brightest) and smaller values fade toward the background.
     * Any non-zero [fraction] is kept at or above [minAlpha] so a tiny value never vanishes.
     * Build it from raw numbers with [intensityFill], which does the normalising.
     */
    data class Intensity(
        val fraction: Float,
        val minAlpha: Float = DEFAULT_MIN_INTENSITY_ALPHA
    ) : DayFill {
        val alpha: Float
            get() = if (fraction <= 0f) 0f else fraction.coerceIn(minAlpha, 1f)
    }
}

/** Faintest opacity an [DayFill.Intensity] day can have (unless its value is zero). */
const val DEFAULT_MIN_INTENSITY_ALPHA = 0.15f

enum class BadgePosition { TOP_START, TOP_END, BOTTOM_CENTER }

/** Small extra indicator drawn on top of a day. A day can carry any number of these. */
sealed interface DayBadge {
    val position: BadgePosition

    data class Dot(
        val color: Color,
        override val position: BadgePosition = BadgePosition.BOTTOM_CENTER
    ) : DayBadge

    data class Symbol(
        val icon: ImageVector,
        val tint: Color,
        override val position: BadgePosition = BadgePosition.TOP_END
    ) : DayBadge
}

/**
 * Everything the calendar needs to draw one day. Screens build these; the calendar never knows
 * what they mean. Outline (ring) and fill are independent, so a day can have either, both, or
 * neither, plus any badges.
 */
@Immutable
data class DayDecoration(
    val outline: Boolean = false,
    val outlineColor: Color? = null,
    val fill: DayFill = DayFill.None,
    val textColor: Color? = null,
    val badges: List<DayBadge> = emptyList()
) {
    companion object {
        val None = DayDecoration()
    }
}

/** Colour a day from a 0..1 score, for the future Dashboard heat-map. */
fun scoreFill(score: Float, low: Color, high: Color): DayFill =
    DayFill.Custom(lerp(low, high, score.coerceIn(0f, 1f)))

/**
 * Highlight a day in proportion to [value] / [max], as opacity of the highlight colour.
 * [max] is the value that should look brightest, e.g. the heaviest lift in the range being
 * shown, so every day is judged against the same ceiling:
 *
 *     val best = bestLiftByDay.values.max()
 *     decorate = { day -> DayDecoration(fill = intensityFill(bestLiftByDay[day] ?: 0f, best)) }
 *
 * Zero, negative or missing data (and a non-positive [max]) gives no fill at all.
 */
fun intensityFill(
    value: Float,
    max: Float,
    minAlpha: Float = DEFAULT_MIN_INTENSITY_ALPHA
): DayFill =
    if (value <= 0f || max <= 0f) DayFill.None
    else DayFill.Intensity(value / max, minAlpha)

/** The colour a fill paints (transparent for none). Intensity keeps its opacity here. */
@Composable
internal fun DayFill.resolveColor(): Color {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        DayFill.None -> Color.Transparent
        DayFill.Highlight -> colors.primaryContainer
        is DayFill.Custom -> color
        is DayFill.Intensity -> colors.primaryContainer.copy(alpha = alpha)
    }
}

/** Readable text colour for a day with this fill. Faded fills are judged as they look on screen. */
@Composable
internal fun DayFill.defaultTextColor(): Color {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        DayFill.None -> colors.onSurface
        DayFill.Highlight -> colors.onPrimaryContainer
        is DayFill.Custom -> readableOn(color)
        is DayFill.Intensity -> readableOn(resolveColor().compositeOver(colors.background))
    }
}

enum class CalendarZoom { YEAR, QUADRANT, MONTH }

@Stable
class CalendarState(
    initialMonth: MonthKey,
    initialSelectedDay: String?,
    initialZoom: CalendarZoom = CalendarZoom.YEAR
) {
    var visibleMonth by mutableStateOf(initialMonth)
    var selectedDay by mutableStateOf(initialSelectedDay)
    var zoom by mutableStateOf(initialZoom)

    companion object {
        val Saver: Saver<CalendarState, Any> = listSaver<CalendarState, Any>(
            save = {
                listOf(
                    it.visibleMonth.year,
                    it.visibleMonth.month,
                    it.selectedDay ?: "",
                    it.zoom.name
                )
            },
            restore = {
                CalendarState(
                    MonthKey(it[0] as Int, it[1] as Int),
                    (it[2] as String).ifEmpty { null },
                    (it.getOrNull(3) as? String)
                        ?.let { name -> CalendarZoom.values().firstOrNull { z -> z.name == name } }
                        ?: CalendarZoom.YEAR
                )
            }
        )
    }
}

@Composable
fun rememberCalendarState(
    initialMonth: MonthKey = currentMonth(),
    initialSelectedDay: String? = todayDayKey(),
    initialZoom: CalendarZoom = CalendarZoom.YEAR
): CalendarState = rememberSaveable(saver = CalendarState.Saver) {
    CalendarState(initialMonth, initialSelectedDay, initialZoom)
}
