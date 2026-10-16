package com.ashrdev.aznd.ui.streaks

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.ashrdev.aznd.ui.common.ScreenScaffold

// Standalone create screen (opened from the Dashboard's "add streak" button). The same form lives on
// the last page of the Streaks pager. Back in the bottom bar cancels; Save stays in the top bar so
// it's reachable with the keyboard up.
@Composable
fun StreakEditorScreen(
    viewModel: StreakViewModel,
    onDone: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val draft = rememberStreakDraft()

    ScreenScaffold(
        title = "New Streak",
        onBack = onDone,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        topBarActions = {
            TextButton(
                enabled = draft.canSave,
                onClick = {
                    viewModel.createStreak(draft)
                    onDone()
                }
            ) {
                Text("Save")
            }
        }
    ) { padding ->
        StreakForm(draft = draft, bottomPadding = padding.calculateBottomPadding())
    }
}
