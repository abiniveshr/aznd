package com.ashrdev.aznd.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.domain.streaks.TrackMode
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.streaks.CheckInButton
import com.ashrdev.aznd.ui.streaks.DayAction
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.streaks.TrackedDetailScreen

/**
 * One routine, in the same shared view as a streak: Progress | Details | Calendar. A routine that is
 * tracked as a streak shows its streak; one that is not shows the run worked out from its schedule.
 * The only top-bar action is Edit.
 */
@Composable
fun RoutineDetailScreen(
    routineId: Long,
    viewModel: StreakViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onEdit: (Long) -> Unit,
    onSaveDay: (Long, String) -> Unit,
    onOpenSavedDays: (Long) -> Unit
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val snap = snapshot ?: return
    val routine = snap.routines.firstOrNull { it.id == routineId }
    if (routine == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val track = snap.trackOf(routine)
    val linked = snap.streakOf(routine)
    val control = snap.routineCheck(routine)
    val savedFlow = remember(linked?.id, viewModel) { linked?.let { viewModel.savedDaysForStreak(it.id) } ?: viewModel.noSavedDays }
    val savedDays by savedFlow.collectAsState(initial = emptyList())
    val today = snap.today

    fun toggle(date: String, done: Boolean) {
        val streakId = control?.streakId
        if (streakId != null) viewModel.setCheckIn(streakId, date, done)
        else viewModel.toggleRoutineDone(routine.id, date, done)
    }

    TrackedDetailScreen(
        title = "Routine View",
        streak = track.streak,
        track = track,
        savedDays = savedDays.map { it.date }.toSet(),
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        topBarActions = {
            IconButton(onClick = { onEdit(routine.id) }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit routine")
            }
        },
        onPickBanner = { onEdit(routine.id) },
        onPickLogo = { onEdit(routine.id) },
        dayAction = { date ->
            when {
                control != null -> {
                    val done = date in control.info.done
                    DayAction(if (done) "Undo check-in" else "Check in") { toggle(date, !done) }
                }
                linked != null && track.mode == TrackMode.AUTO -> {
                    val isBroken = date in track.manualBreaks
                    DayAction(if (isBroken) "Undo break" else "Break streak") {
                        if (isBroken) viewModel.unmarkBreak(linked.id, date) else viewModel.markBreak(linked.id, date)
                    }
                }
                else -> null
            }
        },
        calendarActions = {
            if (control != null || linked != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (linked != null) {
                        OutlinedButton(onClick = { onSaveDay(linked.id, today) }, modifier = Modifier.weight(1f)) { Text("Save today") }
                    }
                    if (control != null) {
                        CheckInButton(control.info, onChange = { toggle(today, it) }, modifier = Modifier.weight(1f), outlined = true)
                    }
                }
            }
            if (linked != null) {
                OutlinedButton(onClick = { onOpenSavedDays(linked.id) }, modifier = Modifier.fillMaxWidth()) { Text("Saved days") }
            }
        }
    )
}
