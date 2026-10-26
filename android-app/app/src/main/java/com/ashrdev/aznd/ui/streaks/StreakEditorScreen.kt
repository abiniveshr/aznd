package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.components.OutlinedTextField
import kotlinx.coroutines.launch

private const val PAGE_IMAGES = 0
private const val PAGE_STREAK = 1
private const val PAGE_GOALS = 2

private val EditorPages = listOf(
    BarPage("Images", Icons.Default.Image),
    BarPage("Streak", Icons.Default.LocalFireDepartment),
    BarPage("Goals", Icons.Default.Flag)
)

@Composable
fun StreakEditorScreen(
    streakId: Long?,
    viewModel: StreakViewModel,
    onDone: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val draft = rememberStreakDraft(streakId)
    var loaded by remember(streakId) { mutableStateOf(streakId == null) }
    LaunchedEffect(streakId) {
        if (streakId != null) {
            viewModel.loadStreak(streakId)?.let { (streak, reminders) -> draft.load(streak, reminders) }
            loaded = true
        }
    }
    val pagerState = rememberPagerState(initialPage = PAGE_STREAK) { EditorPages.size }
    val subPages = rememberSubPageBar(pagerState, EditorPages)
    val scope = rememberCoroutineScope()
    val save: () -> Unit = {
        if (draft.canSave) {
            viewModel.saveStreak(draft.toEntity(), draft.reminders.map { it.toEntity() })
            onDone()
        }
    }

    ScreenScaffold(
        title = if (streakId == null) "New streak" else "Edit streak",
        onBack = onDone,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages,
        topBarActions = { TextButton(onClick = save, enabled = loaded && draft.canSave) { Text("Save") } }
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        if (loaded) HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                PAGE_IMAGES -> ImagesPage(
                    bannerUri = draft.bannerUri,
                    photoUri = draft.photoUri,
                    onBanner = { draft.bannerUri = it },
                    onLogo = { draft.photoUri = it },
                    bottomPadding = bottom
                )
                PAGE_GOALS -> StreakGoalsPage(draft, bottom)
                else -> StreakDetailsForm(draft, bottom, onPickImages = { scope.launch { pagerState.animateScrollToPage(PAGE_IMAGES) } })
            }
        }
    }
}

@Composable
private fun StreakDetailsForm(draft: StreakDraft, bottomPadding: Dp, onPickImages: () -> Unit) {
    EditorColumn(bottomPadding) {
        StreakPreviewHeader(bannerUri = draft.bannerUri, photoUri = draft.photoUri, onClick = onPickImages)
        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft.name = it },
            label = { Text("Streak name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.quote,
            onValueChange = { if (it.length <= STREAK_QUOTE_LIMIT) draft.quote = it },
            label = { Text("Quote (optional, ${draft.quote.length}/$STREAK_QUOTE_LIMIT)") },
            modifier = Modifier.fillMaxWidth()
        )
        DateButton("Start date", draft.startDate, onDate = { draft.startDate = it })
        SectionTitle("Tracking")
        TrackModeOptions(selected = draft.trackMode, onSelect = { draft.trackMode = it })
    }
}

@Composable
private fun StreakGoalsPage(draft: StreakDraft, bottomPadding: Dp) {
    EditorColumn(bottomPadding) {
        MilestonesEditor(draft.milestones)
        NotificationsEditor(
            milestoneAlerts = draft.milestoneAlerts,
            onMilestoneAlerts = { draft.milestoneAlerts = it },
            alertsAvailable = true,
            reminders = draft.reminders,
            reminderHint = "Reminders repeat at their time on the chosen days. A check-in reminder is skipped once you have checked in."
        )
    }
}

