package com.ashrdev.aznd.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
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

@Composable
fun ScreenBottomBar(
    onBack: () -> Unit,
    onHome: () -> Unit,
    tray: Animatable<Float, AnimationVector1D>,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    third: ThirdAction? = null,
    settingsEnabled: Boolean = true
) {
    BottomBarShell(
        tray = tray,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        settingsEnabled = settingsEnabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TabsHeight)
                .padding(horizontal = 12.dp)
        ) {
            ActionTile(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "Back",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onBack
            )
            ActionTile(
                icon = Icons.Default.Home,
                label = "Home",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onHome
            )
            if (third != null) {
                ActionTile(
                    icon = third.icon,
                    label = third.label,
                    tint = if (third.enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    onClick = if (third.enabled) third.onClick else null
                )
            }
        }
    }
}
