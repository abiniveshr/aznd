package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.streaks.nextMilestone
import com.ashrdev.aznd.data.streaks.parseMilestones
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.PrimaryAction
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.components.Card
import kotlinx.coroutines.launch

private const val PAGE_DISCIPLINE = 0
private const val PAGE_STREAKS = 1
private const val PAGE_NEW = 2

private val StreakPages = listOf(
    BarPage("Discipline", Icons.Default.EmojiEvents),
    BarPage("Streaks", Icons.Default.LocalFireDepartment),
    BarPage("New", Icons.Default.Add)
)

/**
 * The Streaks module: three swipeable pages, opening on the middle one.
 *   Discipline | Streaks | New
 *  - Streaks: every streak with its logo, day count and optional quote; tap one to open it.
 *  - Discipline (swipe left): placeholder for the discipline level system.
 *  - New (swipe right): create a streak (logo, name, quote, start date, milestones).
 */
@Composable
fun StreaksScreen(
    viewModel: StreakViewModel,
    onOpenStreak: (Long) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val streaks by viewModel.streaksWithCounts.collectAsState()
    val draft = rememberStreakDraft()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = PAGE_STREAKS) { StreakPages.size }
    val subPages = rememberSubPageBar(pagerState, StreakPages)
    var menuExpanded by remember { mutableStateOf(false) }

    val create: () -> Unit = {
        if (draft.canSave) {
            viewModel.createStreak(draft)
            draft.reset()
            scope.launch { pagerState.animateScrollToPage(PAGE_STREAKS) }
        }
    }

    val page = pagerState.currentPage
    ScreenScaffold(
        title = when (page) {
            PAGE_DISCIPLINE -> "Discipline"
            PAGE_NEW -> "New streak"
            else -> "Streaks"
        },
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages,
        reservePrimarySpace = true,
        primaryAction = if (page == PAGE_NEW) PrimaryAction(
            icon = Icons.Default.Add,
            label = "Create streak",
            enabled = draft.canSave,
            onClick = create
        ) else null,
        topBarActions = when (page) {
            PAGE_STREAKS -> {
                {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(text = { Text("Delete data") }, enabled = false, onClick = {})
                            DropdownMenuItem(text = { Text("Export data") }, enabled = false, onClick = {})
                        }
                    }
                }
            }
            // Save stays reachable with the keyboard up (the bottom bar steps aside then).
            PAGE_NEW -> {
                {
                    TextButton(onClick = create, enabled = draft.canSave) { Text("Save") }
                }
            }
            else -> {
                {}
            }
        }
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            when (index) {
                PAGE_DISCIPLINE -> DisciplinePage(bottomPadding = bottom)
                PAGE_NEW -> StreakForm(draft = draft, bottomPadding = bottom)
                else -> StreakListPage(
                    streaks = streaks,
                    bottomPadding = bottom,
                    onOpenStreak = onOpenStreak
                )
            }
        }
    }
}

@Composable
private fun StreakListPage(
    streaks: List<StreakWithCount>,
    bottomPadding: Dp,
    onOpenStreak: (Long) -> Unit
) {
    if (streaks.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                "No streaks yet — swipe right to start one.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(streaks, key = { it.streak.id }) { item ->
            StreakCard(item = item, onClick = { onOpenStreak(item.streak.id) })
        }
    }
}

@Composable
private fun StreakCard(item: StreakWithCount, onClick: () -> Unit) {
    val streak = item.streak
    val days = item.currentStreak
    val next = remember(streak.milestones, days) { nextMilestone(days, parseMilestones(streak.milestones)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StreakLogo(photoUri = streak.photoUri, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    streak.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                streak.quote?.takeIf { it.isNotBlank() }?.let { quote ->
                    Text(
                        "\u201C$quote\u201D",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (next != null) {
                    Text(
                        "Next: $next days · ${next - days} to go",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$days", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (days == 1) "day" else "days",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
