package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.ThirdAction
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.theme.Space

@Composable
fun SavedDaysScreen(
    viewModel: StreakViewModel,
    onOpenStreak: (Long) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val streaks by viewModel.streaksWithSavedDays.collectAsState()

    ScreenScaffold(
        title = "Saved Streaks",
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        third = ThirdAction(icon = Icons.Default.Share, label = "Share", enabled = false, onClick = {})
    ) { padding ->
        if (streaks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Space.xl)
            ) {
                Text("No saved days yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.lg, end = Space.lg, top = Space.lg, bottom = padding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                items(streaks, key = { it.id }) { streak ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenStreak(streak.id) }
                    ) {
                        Text(
                            streak.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(Space.lg)
                        )
                    }
                }
            }
        }
    }
}
