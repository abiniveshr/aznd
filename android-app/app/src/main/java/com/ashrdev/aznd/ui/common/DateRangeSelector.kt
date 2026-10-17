package com.ashrdev.aznd.ui.common

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.calendar.MonthKey
import com.ashrdev.aznd.ui.calendar.MonthYearPickerDialog
import com.ashrdev.aznd.ui.calendar.currentMonth
import com.ashrdev.aznd.ui.calendar.dayOfMonthOf
import com.ashrdev.aznd.ui.calendar.monthKeyOf
import com.ashrdev.aznd.ui.components.OutlinedButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * The app's one date-range picker (state lives in [DateRangeState], see [rememberDateRangeState]).
 *
 *  - a chip row picks the kind: presets, one Month, one Year, or a Custom start and end
 *  - Month / Year / Custom reveal their own controls underneath
 *  - a caption spells out the exact span that is active
 *
 * [availableMonths] are the months that have data: they are what the month picker lists and how far
 * the Month / Year steppers go (the current month is always allowed). Pass [kinds] to offer fewer
 * levels, e.g. only the presets.
 */
@Composable
fun DateRangeSelector(
    state: DateRangeState,
    modifier: Modifier = Modifier,
    availableMonths: Collection<MonthKey> = emptyList(),
    kinds: List<DateRangeKind> = DateRangeKind.entries
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(kinds, key = { it.name }) { kind ->
                FilterChip(
                    selected = state.kind == kind,
                    onClick = { state.kind = kind },
                    label = { Text(kind.label) }
                )
            }
        }

        when (state.kind) {
            DateRangeKind.MONTH -> MonthStepper(state, availableMonths)
            DateRangeKind.YEAR -> YearStepper(state, availableMonths)
            DateRangeKind.CUSTOM -> CustomRangeRow(state)
            else -> Unit
        }

        Text(
            state.describe(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MonthStepper(state: DateRangeState, availableMonths: Collection<MonthKey>) {
    val allowed = remember(availableMonths) { (availableMonths + currentMonth()).toSortedSet().toList() }
    val first = allowed.first()
    val last = allowed.last()
    val month = state.month
    var pickerOpen by remember { mutableStateOf(false) }

    if (pickerOpen) {
        MonthYearPickerDialog(
            months = allowed,
            selected = month,
            onPick = {
                state.month = it
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { state.month = month.previous() }, enabled = month > first) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
        }
        Text(
            month.title(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { pickerOpen = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
        IconButton(onClick = { state.month = month.next() }, enabled = month < last) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun YearStepper(state: DateRangeState, availableMonths: Collection<MonthKey>) {
    val years = remember(availableMonths) { (availableMonths.map { it.year } + currentMonth().year).distinct().sorted() }
    val year = state.year

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { state.year = year - 1 }, enabled = year > years.first()) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous year")
        }
        Text(
            year.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        IconButton(onClick = { state.year = year + 1 }, enabled = year < years.last()) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next year")
        }
    }
}

/**
 * The From / To buttons (each opens the calendar) for a [DateRangeState] in CUSTOM kind. Side by
 * side by default; [stacked] puts them on two full-width lines for narrow spaces such as dialogs.
 * [buttonModifier] sizes each button (e.g. a fixed height).
 */
@Composable
fun CustomRangeRow(
    state: DateRangeState,
    buttonModifier: Modifier = Modifier,
    stacked: Boolean = false
) {
    val context = LocalContext.current
    val fmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }

    fun label(dayKey: String): String {
        val cal = Calendar.getInstance().apply {
            clear()
            val m = monthKeyOf(dayKey)
            set(m.year, m.month - 1, dayOfMonthOf(dayKey))
        }
        return fmt.format(cal.time)
    }

    fun pick(current: String, onPicked: (String) -> Unit) {
        val m = monthKeyOf(current)
        DatePickerDialog(
            context,
            { _, y, mo, d -> onPicked(MonthKey(y, mo + 1).dayKey(d)) },
            m.year, m.month - 1, dayOfMonthOf(current)
        ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }

    val fromButton: @Composable (Modifier) -> Unit = { layout ->
        OutlinedButton(
            onClick = {
                pick(state.customStart) { picked ->
                    state.customStart = picked
                    if (picked > state.customEnd) state.customEnd = picked
                }
            },
            modifier = layout.then(buttonModifier)
        ) { Text("From ${label(state.customStart)}", maxLines = 1) }
    }
    val toButton: @Composable (Modifier) -> Unit = { layout ->
        OutlinedButton(
            onClick = {
                pick(state.customEnd) { picked ->
                    state.customEnd = picked
                    if (picked < state.customStart) state.customStart = picked
                }
            },
            modifier = layout.then(buttonModifier)
        ) { Text("To ${label(state.customEnd)}", maxLines = 1) }
    }

    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            fromButton(Modifier.fillMaxWidth())
            toButton(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            fromButton(Modifier.weight(1f))
            toButton(Modifier.weight(1f))
        }
    }
}
