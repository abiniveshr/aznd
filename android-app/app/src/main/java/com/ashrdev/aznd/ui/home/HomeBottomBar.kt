package com.ashrdev.aznd.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.theme.LocalAzndStyle
import kotlin.math.roundToInt

// Pages in swipe order, left to right.
enum class HomePageId(val title: String, val icon: ImageVector) {
    HISTORY("History", Icons.Default.History),
    HOME("Home", Icons.Default.Home),
    DASHBOARD("Dashboard", Icons.Default.Dashboard)
}

@Composable
fun HomeBottomBar(
    pagerState: PagerState,
    tray: Animatable<Float, AnimationVector1D>,
    onSelectPage: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomBarShell(tray = tray, onOpenSettings = onOpenSettings, modifier = modifier) {
        PageTabs(pagerState = pagerState, onSelectPage = onSelectPage)
    }
}

@Composable
private fun PageTabs(
    pagerState: PagerState,
    onSelectPage: (Int) -> Unit
) {
    val style = LocalAzndStyle.current
    val pages = HomePageId.entries

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(TabsHeight)
    ) {
        val tabWidth = maxWidth / pages.size

        Box(
            modifier = Modifier
                .offset {
                    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                    IntOffset((position * tabWidth.toPx()).roundToInt(), 0)
                }
                .width(tabWidth)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(style.buttonShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            pages.forEachIndexed { index, page ->
                val selected = pagerState.currentPage == index
                val tint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tabTint"
                )
                ActionTile(icon = page.icon, label = page.title, tint = tint) {
                    onSelectPage(index)
                }
            }
        }
    }
}
