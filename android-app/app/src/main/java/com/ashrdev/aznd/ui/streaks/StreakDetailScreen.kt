package com.ashrdev.aznd.ui.streaks

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.streaks.StreakEntity
import com.ashrdev.aznd.domain.streaks.TrackMode
import com.ashrdev.aznd.domain.streaks.dateKey
import com.ashrdev.aznd.domain.streaks.todayKey
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.components.OutlinedButton
import java.util.Calendar

private val breakPhrases = listOf(
    "Do not worry, we start again",
    "Get back up",
    "On your feet soldier"
)

/**
 * One streak, in the shared [TrackedDetailScreen]: Progress | Details | Calendar, opening on Details.
 * Top bar: Edit and Delete. The calendar has Save today, Break streak (automatic check-in) or
 * Check in (manual check-in), and Saved days.
 */
@Composable
fun StreakDetailScreen(
    streakId: Long,
    viewModel: StreakViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSavedDays: () -> Unit,
    onSaveDay: (Long, String) -> Unit,
    onEdit: (StreakEntity) -> Unit
) {
    val context = LocalContext.current
    var streak by remember { mutableStateOf<StreakEntity?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(streakId) {
        streak = viewModel.getStreak(streakId)
        loaded = true
    }

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

    val track by viewModel.trackFor(streakId).collectAsState(initial = null)
    val savedDaysList by viewModel.savedDaysForStreak(streakId).collectAsState(initial = emptyList())

    if (!loaded) return
    val loadedStreak = streak
    if (loadedStreak == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val t = track ?: return
    val current = t.streak.copy(photoUri = loadedStreak.photoUri, bannerUri = loadedStreak.bannerUri)
    val mode = t.mode
    val breaks = t.tracked.breaks
    val manualBreaks = t.manualBreaks
    val today = todayKey()

    fun breakOn(date: String) {
        Toast.makeText(context, breakPhrases[manualBreaks.size % breakPhrases.size], Toast.LENGTH_SHORT).show()
        viewModel.markBreak(streakId, date)
    }

    val breakStreak: () -> Unit = {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val picked = dateKey(year, month, dayOfMonth)
                if (picked in breaks) viewModel.unmarkBreak(streakId, picked)
                else breakOn(picked)
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

    TrackedDetailScreen(
        title = "Streak View",
        streak = current,
        track = t,
        savedDays = savedDaysList.map { it.date }.toSet(),
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        topBarActions = {
            IconButton(onClick = { onEdit(current) }) {
                Icon(Icons.Default.Edit, contentDescription = if (current.routineId != null) "Edit routine" else "Edit streak")
            }
            IconButton(onClick = { showDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete streak", tint = MaterialTheme.colorScheme.error)
            }
        },
        onPickBanner = { bannerPicker.launch() },
        onPickLogo = { logoPicker.launch() },
        dayAction = { date ->
            if (mode == TrackMode.AUTO) {
                val isBroken = date in manualBreaks
                DayAction(if (isBroken) "Undo break" else "Break streak") {
                    if (isBroken) viewModel.unmarkBreak(streakId, date) else breakOn(date)
                }
            } else {
                val checkedIn = date in t.checkIns
                DayAction(if (checkedIn) "Undo check-in" else "Check in") { viewModel.setCheckIn(streakId, date, !checkedIn) }
            }
        },
        calendarActions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = { onSaveDay(streakId, today) }, modifier = Modifier.weight(1f)) { Text("Save today") }
                val check = t.check
                if (mode == TrackMode.AUTO) {
                    OutlinedButton(onClick = breakStreak, modifier = Modifier.weight(1f)) { Text("Break streak") }
                } else if (check != null) {
                    CheckInButton(check, onChange = { viewModel.setCheckIn(streakId, today, it) }, modifier = Modifier.weight(1f), outlined = true)
                }
            }
            OutlinedButton(onClick = onOpenSavedDays, modifier = Modifier.fillMaxWidth()) { Text("Saved days") }
        }
    )
}
