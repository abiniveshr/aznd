package com.ashrdev.aznd.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.ui.theme.Space

private val NoDecoration: (String) -> DayDecoration = { DayDecoration.None }

private fun typedDayKey(day: String, month: String, year: String): String? {
    if (year.length != 4) return null
    val y = year.toIntOrNull() ?: return null
    val m = month.toIntOrNull() ?: return null
    val d = day.toIntOrNull() ?: return null
    if (m !in 1..12) return null
    val key = MonthKey(y, m)
    if (d !in 1..key.daysInMonth()) return null
    return key.dayKey(d)
}

@Composable
fun CalendarDatePickerDialog(
    title: String,
    pickableDays: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    chosenDay: String? = null,
    initialMonth: MonthKey? = null,
    notPickableMessage: String = "Nothing on that day"
) {
    val pickable = remember(pickableDays) { { dayKey: String -> dayKey in pickableDays } }
    val validMonths = remember(pickableDays) { pickableDays.map { monthKeyOf(it) }.distinct() }
    val calendarState = rememberCalendarState(
        initialMonth = initialMonth ?: validMonths.maxOrNull() ?: currentMonth(),
        initialSelectedDay = null,
        initialZoom = CalendarZoom.MONTH
    )
    var chosen by remember { mutableStateOf(chosenDay) }
    var day by remember { mutableStateOf(chosenDay?.substring(8, 10) ?: "") }
    var month by remember { mutableStateOf(chosenDay?.substring(5, 7) ?: "") }
    var year by remember { mutableStateOf(chosenDay?.substring(0, 4) ?: "") }

    val typed = typedDayKey(day, month, year)
    val typedPickable = typed?.takeIf { it in pickableDays }
    val complete = day.isNotEmpty() && month.isNotEmpty() && year.length == 4
    val error = when {
        !complete -> null
        typed == null -> "Not a real date"
        typedPickable == null -> notPickableMessage
        else -> null
    }

    fun typedChanged(newDay: String, newMonth: String, newYear: String) {
        day = newDay
        month = newMonth
        year = newYear
        val key = typedDayKey(newDay, newMonth, newYear)
        if (key != null && key in pickableDays) {
            chosen = key
            calendarState.visibleMonth = monthKeyOf(key)
            calendarState.zoom = CalendarZoom.MONTH
        }
    }

    val number = KeyboardOptions(keyboardType = KeyboardType.Number)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                ZoomCalendar(
                    decorate = NoDecoration,
                    validMonths = validMonths,
                    modifier = Modifier.fillMaxWidth(),
                    state = calendarState,
                    selectable = false,
                    maxGridHeight = 280.dp,
                    pickableDays = pickable,
                    chosenDay = chosen,
                    onDayClick = { dayKey ->
                        chosen = dayKey
                        day = dayKey.substring(8, 10)
                        month = dayKey.substring(5, 7)
                        year = dayKey.substring(0, 4)
                        onPick(dayKey)
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    OutlinedTextField(
                        value = day,
                        onValueChange = { typedChanged(it.filter(Char::isDigit).take(2), month, year) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Day") },
                        singleLine = true,
                        keyboardOptions = number
                    )
                    OutlinedTextField(
                        value = month,
                        onValueChange = { typedChanged(day, it.filter(Char::isDigit).take(2), year) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Month") },
                        singleLine = true,
                        keyboardOptions = number
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { typedChanged(day, month, it.filter(Char::isDigit).take(4)) },
                        modifier = Modifier.weight(1.4f),
                        label = { Text("Year") },
                        singleLine = true,
                        keyboardOptions = number
                    )
                }
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { typedPickable?.let(onPick) }, enabled = typedPickable != null) { Text("Use date") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
