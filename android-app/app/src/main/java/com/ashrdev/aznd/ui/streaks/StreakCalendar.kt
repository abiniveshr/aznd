package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Calendar

private val weekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S")

@Composable
fun StreakCalendar(
    startDate: String,
    breaks: Set<String>,
    onDayClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = todayKey()
    val availableMonths = remember(startDate, today) { monthsBetween(startDate, today) }
    var monthIndex by remember(startDate) { mutableIntStateOf(availableMonths.lastIndex) }
    var monthMenuExpanded by remember { mutableStateOf(false) }

    val (year, month) = availableMonths.getOrElse(monthIndex) { availableMonths.last() }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (monthIndex > 0) monthIndex-- },
                enabled = monthIndex > 0
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
            }
            Box {
                Text(
                    monthLabel(year, month),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .clickable { monthMenuExpanded = true }
                        .padding(8.dp)
                )
                DropdownMenu(
                    expanded = monthMenuExpanded,
                    onDismissRequest = { monthMenuExpanded = false }
                ) {
                    availableMonths.forEachIndexed { index, (y, m) ->
                        DropdownMenuItem(
                            text = { Text(monthLabel(y, m)) },
                            onClick = {
                                monthIndex = index
                                monthMenuExpanded = false
                            }
                        )
                    }
                }
            }
            IconButton(
                onClick = { if (monthIndex < availableMonths.lastIndex) monthIndex++ },
                enabled = monthIndex < availableMonths.lastIndex
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        val firstWeekday = cal.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val totalCells = firstWeekday + daysInMonth
        val rows = (totalCells + 6) / 7

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(monthIndex, availableMonths) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            if (totalDrag > 120 && monthIndex > 0) monthIndex--
                            else if (totalDrag < -120 && monthIndex < availableMonths.lastIndex) monthIndex++
                            totalDrag = 0f
                        },
                        onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount }
                    )
                }
        ) {
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayOfMonth = cellIndex - firstWeekday + 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        ) {
                            if (dayOfMonth in 1..daysInMonth) {
                                val dateStr = dateKey(year, month, dayOfMonth)
                                DayCell(
                                    day = dayOfMonth,
                                    status = dayStatus(dateStr, startDate, today, breaks),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { onDayClick(dateStr) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, status: DayStatus, modifier: Modifier = Modifier) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val onHighlight = MaterialTheme.colorScheme.onPrimary
    val textColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier.then(
            when (status) {
                DayStatus.ON_STREAK -> Modifier.background(highlightColor)
                DayStatus.BROKEN -> Modifier.border(2.dp, highlightColor)
                DayStatus.NONE -> Modifier
            }
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            day.toString(),
            color = if (status == DayStatus.ON_STREAK) onHighlight else textColor,
            style = MaterialTheme.typography.bodySmall
        )
    }
}