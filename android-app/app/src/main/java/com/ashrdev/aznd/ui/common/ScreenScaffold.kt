package com.ashrdev.aznd.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.TopAppBar
import com.ashrdev.aznd.ui.home.TabsHeight
import com.ashrdev.aznd.ui.home.bottomBarClearance
import com.ashrdev.aznd.ui.home.bottomBarHeight
import com.ashrdev.aznd.ui.home.rememberTrayState
import com.ashrdev.aznd.ui.home.settleTo
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val PrimaryButtonHeight = 64.dp
private val PrimaryButtonGap = 12.dp

// Every screen except the Home pager sits inside this: top bar with a title, content, and the
// persistent bottom bar (Back / Home / optional third action, pull up for Settings etc.).
// `content` receives padding whose bottom value keeps your list clear of the bar (and of the
// big primary button when there is one).
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    third: ThirdAction? = null,
    primaryAction: PrimaryAction? = null,
    settingsEnabled: Boolean = true,
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val tray = rememberTrayState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.isImeVisible
    val clearance = bottomBarClearance()
    val barHeight = bottomBarHeight()
    val trayTravelPx = with(density) { TabsHeight.toPx() }
    val reserve = if (primaryAction != null) PrimaryButtonHeight + PrimaryButtonGap else 0.dp

    BackHandler(enabled = tray.value > 0f) { scope.launch { tray.settleTo(0f) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, actions = topBarActions) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .imePadding()
        ) {
            content(PaddingValues(bottom = if (imeVisible) 16.dp else clearance + reserve))

            // The bar steps aside while the keyboard is open so it never eats typing space.
            if (!imeVisible) {
                // Big action button: rides up with the bar as it's pulled, fading out as it goes.
                if (primaryAction != null && tray.value < 0.98f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 16.dp, end = 16.dp, bottom = barHeight + PrimaryButtonGap)
                            .offset { IntOffset(0, -(trayTravelPx * tray.value).roundToInt()) }
                            .graphicsLayer { alpha = (1f - tray.value).coerceIn(0f, 1f) }
                    ) {
                        Button(
                            onClick = primaryAction.onClick,
                            enabled = primaryAction.enabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(PrimaryButtonHeight)
                        ) {
                            Icon(primaryAction.icon, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(primaryAction.label, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                if (tray.value > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f * tray.value.coerceIn(0f, 1f)))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { scope.launch { tray.settleTo(0f) } }
                    )
                }
                ScreenBottomBar(
                    onBack = onBack,
                    onHome = onHome,
                    tray = tray,
                    onOpenSettings = onOpenSettings,
                    third = third,
                    settingsEnabled = settingsEnabled,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
