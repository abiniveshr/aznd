package com.ashrdev.aznd.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.BottomBarShell
import com.ashrdev.aznd.ui.common.SubPageBar
import com.ashrdev.aznd.ui.common.SubPageTabs

// Pages in swipe order, left to right. The app opens on DASHBOARD.
enum class HomePageId(val title: String, val icon: ImageVector) {
    HISTORY("History", Icons.Default.History),
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    APPS("Apps", Icons.Default.Apps)
}

private val HomeBarPages = HomePageId.entries.map { BarPage(it.title, it.icon) }

@Composable
fun HomeBottomBar(
    pagerState: PagerState,
    tray: Animatable<Float, AnimationVector1D>,
    onSelectPage: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bar = SubPageBar(
        pages = HomeBarPages,
        position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
        onSelect = onSelectPage
    )
    BottomBarShell(
        tray = tray,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        onShare = onShare,
        row1 = { SubPageTabs(bar) }
    )
}
