package com.ashrdev.aznd.ui.streaks

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocalFireDepartment
import com.ashrdev.aznd.ui.components.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.streaks.StreakEntity
import com.ashrdev.aznd.data.streaks.parseMilestones
import com.ashrdev.aznd.ui.calendar.CalendarState
import com.ashrdev.aznd.ui.calendar.rememberCalendarState
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.components.OutlinedButton
import java.util.Calendar

private const val PAGE_GRAPH = 0
private const val PAGE_DETAILS = 1
private const val PAGE_CALENDAR = 2

private val DetailPages = listOf(
    BarPage("Progress", Icons.Default.BarChart),
    BarPage("Details", Icons.Default.LocalFireDepartment),
    BarPage("Calendar", Icons.Default.DateRange)
)

private val slipPhrases = listOf(
    "Do not worry, we start again",
    "Get back up",
    "On your feet soldier"
)

/**
 * One streak: three swipeable pages, opening on the middle one.
 *   Progress | Details | Calendar
 *  - Details: banner photo (Add photo), icon + name + day count (tap the icon to change it), times
 *    broken, longest streak, upcoming milestone, Delete.
 *  - Progress (swipe left): the app's shared graph, fed with this streak's history.
 *  - Calendar (swipe right): the streak calendar with milestones marked, plus Save today,
 *    Log a slip and Saved days.
 */
@Composable
fun StreakDetailScreen(
    streakId: Long,
    viewModel: StreakViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSavedDays: () -> Unit,
    onSaveDay: (Long, String) -> Unit
) {
    val context = LocalContext.current
    var streak by remember { mutableStateOf<StreakEntity?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var tappedDate by remember { mutableStateOf<String?>(null) }
    val pagerState = rememberPagerState(initialPage = PAGE_DETAILS) { DetailPages.size }
    val subPages = rememberSubPageBar(pagerState, DetailPages)
    val calendarState = rememberCalendarState()

    LaunchedEffect(streakId) {
        streak = viewModel.getStreak(streakId)
        loaded = true
    }

    // The icon (square) and the banner photo (wide) each have their own picker and column.
    val logoPicker = rememberImagePicker(
        aspect = STREAK_LOGO_ASPECT to 1f,
        outputWidth = STREAK_LOGO_PX,
        outputHeight = STREAK_LOGO_PX,
        onImagePicked = { uri ->
            val newUri = uri.toString()
            viewModel.updateStreakPhoto(streakId, newUri)
            streak = streak?.copy(photoUri = newUri)
        }
    )
    val bannerPicker = rememberImagePicker(
        aspect = 16f to 9f,
        outputWidth = STREAK_BANNER_WIDTH_PX,
        outputHeight = STREAK_BANNER_HEIGHT_PX,
        onImagePicked = { uri ->
            val newUri = uri.toString()
            viewModel.updateStreakBanner(streakId, newUri)
            streak = streak?.copy(bannerUri = newUri)
        }
    )

    val breaksList by viewModel.breaksForStreak(streakId).collectAsState(initial = emptyList())
    val savedDaysList by viewModel.savedDaysForStreak(streakId).collectAsState(initial = emptyList())

    if (!loaded) return
    val current = streak
    if (current == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val breaks = breaksList.toSet()
    val savedDays = savedDaysList.map { it.date }.toSet()
    val today = todayKey()
    val milestones = remember(current.milestones) { parseMilestones(current.milestones) }
    val stats = remember(current.startDate, breaks, milestones, today) {
        computeStreakStats(current.startDate, breaks, milestones, today)
    }

    fun markSlip(date: String) {
        Toast.makeText(context, slipPhrases[breaksList.size % slipPhrases.size], Toast.LENGTH_SHORT).show()
        viewModel.markBreak(streakId, date)
    }

    val logSlip: () -> Unit = {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val picked = dateKey(year, month, dayOfMonth)
                if (picked in breaks) viewModel.unmarkBreak(streakId, picked)
                else markSlip(picked)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete streak?") },
            text = { Text("\"${current.name}\" and all its saved days will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.deleteStreak(streakId)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            }
        )
    }

    tappedDate?.let { date ->
        val inStreak = date >= current.startDate && date <= today
        val reached = stats.reachedMilestones[date]
        val upcoming = stats.upcomingMilestones[date]
        val isBroken = date in breaks
        val lines = buildList {
            if (inStreak) {
                val length = computeCurrentStreak(current.startDate, breaks, date)
                add("Streak length on this day: $length day${if (length == 1) "" else "s"}")
            }
            if (reached != null) add("Milestone reached: $reached days")
            if (upcoming != null) add("Milestone due: $upcoming days")
            if (isEmpty()) add(if (date > today) "Nothing on this day yet." else "Before this streak started.")
        }
        AlertDialog(
            onDismissRequest = { tappedDate = null },
            title = { Text(displayDate(date)) },
            text = { Column { lines.forEach { Text(it) } } },
            confirmButton = {
                if (inStreak) {
                    TextButton(onClick = {
                        if (isBroken) viewModel.unmarkBreak(streakId, date)
                        else markSlip(date)
                        tappedDate = null
                    }) {
                        Text(if (isBroken) "Undo slip" else "Mark as slip")
                    }
                } else {
                    TextButton(onClick = { tappedDate = null }) { Text("Close") }
                }
            },
            dismissButton = if (inStreak) {
                { TextButton(onClick = { tappedDate = null }) { Text("Close") } }
            } else null
        )
    }

    ScreenScaffold(
        title = "Streak View",
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            when (index) {
                PAGE_GRAPH -> StreakGraphPage(stats = stats, milestones = milestones, bottomPadding = bottom)
                PAGE_CALENDAR -> CalendarPage(
                    startDate = current.startDate,
                    breaks = breaks,
                    savedDays = savedDays,
                    stats = stats,
                    state = calendarState,
                    bottomPadding = bottom,
                    onDayClick = { tappedDate = it },
                    onSaveToday = { onSaveDay(streakId, today) },
                    onLogSlip = logSlip,
                    onOpenSavedDays = onOpenSavedDays
                )
                else -> StreakDetailsPage(
                    streak = current,
                    stats = stats,
                    hasMilestones = milestones.isNotEmpty(),
                    bottomPadding = bottom,
                    onPickBanner = { bannerPicker.launch() },
                    onRemoveBanner = {
                        viewModel.updateStreakBanner(streakId, null)
                        streak = current.copy(bannerUri = null)
                    },
                    onPickLogo = { logoPicker.launch() },
                    onDelete = { showDelete = true }
                )
            }
        }
    }
}

@Composable
private fun CalendarPage(
    startDate: String,
    breaks: Set<String>,
    savedDays: Set<String>,
    stats: StreakStats,
    state: CalendarState,
    bottomPadding: Dp,
    onDayClick: (String) -> Unit,
    onSaveToday: () -> Unit,
    onLogSlip: () -> Unit,
    onOpenSavedDays: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StreakCalendar(
            startDate = startDate,
            breaks = breaks,
            savedDays = savedDays,
            reachedMilestones = stats.reachedMilestones,
            upcomingMilestones = stats.upcomingMilestones,
            state = state,
            onDayClick = onDayClick,
            modifier = Modifier.fillMaxWidth()
        )
        StreakCalendarLegend()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onSaveToday, modifier = Modifier.weight(1f)) { Text("Save today") }
            OutlinedButton(onClick = onLogSlip, modifier = Modifier.weight(1f)) { Text("Log a slip") }
        }
        OutlinedButton(onClick = onOpenSavedDays, modifier = Modifier.fillMaxWidth()) { Text("Saved days") }
    }
}
