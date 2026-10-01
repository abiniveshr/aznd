package com.ashrdev.aznd.ui.calendar

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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector

/** What fills the day's square. */
sealed interface DayFill {
    /** Nothing. */
    object None : DayFill

    /** The theme's highlight colour (follows Appearance settings). */
    object Highlight : DayFill

    /** Any colour you like, e.g. a score colour on the Dashboard. Text colour adapts. */
    data class Custom(val color: Color) : DayFill
}

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

/** What a custom `cellContent` receives for each day. */
@Immutable
data class DayCellInfo(
    val dayKey: String,
    val dayOfMonth: Int,
    val decoration: DayDecoration,
    val isToday: Boolean,
    val isSelected: Boolean
)

/** Colour a day from a 0..1 score, for the future Dashboard heat-map. */
fun scoreFill(score: Float, low: Color, high: Color): DayFill =
    DayFill.Custom(lerp(low, high, score.coerceIn(0f, 1f)))

/** Which month is showing and which day is selected. Survives navigating away and back. */
@Stable
class CalendarState(initialMonth: MonthKey, initialSelectedDay: String?) {
    var visibleMonth by mutableStateOf(initialMonth)
    var selectedDay by mutableStateOf(initialSelectedDay)

    companion object {
        val Saver: Saver<CalendarState, Any> = listSaver<CalendarState, Any>(
            save = { listOf(it.visibleMonth.year, it.visibleMonth.month, it.selectedDay ?: "") },
            restore = {
                CalendarState(
                    MonthKey(it[0] as Int, it[1] as Int),
                    (it[2] as String).ifEmpty { null }
                )
            }
        )
    }
}

@Composable
fun rememberCalendarState(
    initialMonth: MonthKey = currentMonth(),
    initialSelectedDay: String? = todayDayKey()
): CalendarState = rememberSaveable(saver = CalendarState.Saver) {
    CalendarState(initialMonth, initialSelectedDay)
}
