package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.streaks.StreakSavedDayEntity
import com.ashrdev.aznd.ui.calendar.BottomAnchoredPage
import com.ashrdev.aznd.ui.calendar.DayDecoration
import com.ashrdev.aznd.ui.calendar.DayFill
import com.ashrdev.aznd.ui.calendar.MonthCalendar
import com.ashrdev.aznd.ui.calendar.SelectedDayHeader
import com.ashrdev.aznd.ui.calendar.monthKeyOf
import com.ashrdev.aznd.ui.calendar.rememberCalendarState
import com.ashrdev.aznd.ui.calendar.rememberFavoriteDays
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.ThirdAction
import com.ashrdev.aznd.ui.components.Card

@Composable
fun SavedDaysForStreakScreen(
    streakId: Long,
    viewModel: StreakViewModel,
    onOpenDay: (Long, String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var streakName by remember { mutableStateOf("") }

    LaunchedEffect(streakId) {
        streakName = viewModel.getStreak(streakId)?.name ?: ""
    }

    val savedDays by viewModel.savedDaysForStreak(streakId).collectAsState(initial = emptyList())
    val favorites = rememberFavoriteDays("streak:$streakId")
    val calendarState = rememberCalendarState()
    val savedByDay = remember(savedDays) { savedDays.associateBy { it.date } }
    val validMonths = remember(savedByDay) { savedByDay.keys.map { monthKeyOf(it) }.distinct() }

    ScreenScaffold(
        title = if (streakName.isEmpty()) "Saved Streaks" else "$streakName • Saved Streaks",
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        third = ThirdAction(icon = Icons.Default.Share, label = "Share", enabled = false, onClick = {})
    ) { padding ->
        BottomAnchoredPage(
            bottomPadding = padding.calculateBottomPadding(),
            top = {
                if (savedDays.isEmpty()) {
                    Text(
                        "No saved days for this streak yet.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    savedDays.take(3).forEach { day ->
                        key(day.date) {
                            SavedDayCard(day = day, onClick = { onOpenDay(streakId, day.date) })
                        }
                    }
                }
            },
            bottom = {
                val selected = calendarState.selectedDay
                if (selected != null) {
                    val saved = savedByDay[selected]
                    SelectedDayHeader(
                        dayKey = selected,
                        isFavorite = favorites.isFavorite(selected),
                        onToggleFavorite = if (saved != null) {
                            { favorites.toggle(selected) }
                        } else null
                    )
                    if (saved == null) {
                        Text(
                            "Nothing saved on this day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        SavedDayCard(
                            day = saved,
                            onClick = { onOpenDay(streakId, saved.date) },
                            showRemark = true
                        )
                    }
                }
                MonthCalendar(
                    decorate = { dayKey ->
                        when {
                            dayKey !in savedByDay -> DayDecoration.None
                            favorites.isFavorite(dayKey) -> DayDecoration(outline = true, fill = DayFill.Highlight)
                            else -> DayDecoration(outline = true)
                        }
                    },
                    validMonths = validMonths,
                    state = calendarState
                )
            }
        )
    }
}

@Composable
private fun SavedDayCard(
    day: StreakSavedDayEntity,
    onClick: () -> Unit,
    showRemark: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(displayDate(day.date), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${day.streakCountAtSave} day${if (day.streakCountAtSave == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (showRemark && day.remark.isNotBlank()) {
                    Text(
                        day.remark,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            if (day.photoUri != null) {
                Icon(Icons.Default.Photo, contentDescription = "Has photo")
            }
        }
    }
}
