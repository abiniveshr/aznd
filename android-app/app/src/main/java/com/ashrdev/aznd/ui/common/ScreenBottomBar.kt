package com.ashrdev.aznd.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.home.ActionTile
import com.ashrdev.aznd.ui.home.BottomBarShell
import com.ashrdev.aznd.ui.home.TabsHeight

data class ThirdAction(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

// A big full-width button that floats just above the bottom bar (e.g. Start on a workout).
// It slides up and fades out while the bar is pulled up.
data class PrimaryAction(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * The bar every screen except the Home pager sits above. Back lives in the top bar.
 *
 *  - With [subPages]: the closed bar shows the subpage tabs.
 *  - Without: the closed bar shows the screen's actions ([third] plus [extraActions]), or just
 *    the pull handle when there are none.
 *  - Pulling up reveals Home / Settings / Manage data.
 */
@Composable
fun ScreenBottomBar(
    onHome: () -> Unit,
    tray: Animatable<Float, AnimationVector1D>,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    third: ThirdAction? = null,
    extraActions: List<ThirdAction> = emptyList(),
    settingsEnabled: Boolean = true,
    manageDataEnabled: Boolean = true,
    subPages: SubPageBar? = null
) {
    val actions = listOfNotNull(third) + extraActions
    val mainRow: (@Composable () -> Unit)? = when {
        subPages != null -> {
            { SubPageTabs(subPages) }
        }
        actions.isNotEmpty() -> {
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TabsHeight)
                        .padding(horizontal = 12.dp)
                ) {
                    actions.forEach { action ->
                        ActionTile(
                            icon = action.icon,
                            label = action.label,
                            tint = if (action.enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            onClick = if (action.enabled) action.onClick else null
                        )
                    }
                }
            }
        }
        else -> null
    }

    BottomBarShell(
        tray = tray,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        settingsEnabled = settingsEnabled,
        manageDataEnabled = manageDataEnabled,
        onHome = onHome,
        row1 = mainRow
    )
}
