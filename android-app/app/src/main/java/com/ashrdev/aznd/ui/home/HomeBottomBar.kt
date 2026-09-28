package com.ashrdev.aznd.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.FilledTonalButton
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.theme.LocalAzndStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// Pages in swipe order, left to right.
enum class HomePageId(val title: String, val icon: ImageVector) {
    HISTORY("History", Icons.Default.History),
    HOME("Home", Icons.Default.Home),
    DASHBOARD("Dashboard", Icons.Default.Dashboard)
}

private val HandleAreaHeight = 20.dp
private val TabsHeight = 56.dp
private val BarBottomMargin = 8.dp
private const val FLING_VELOCITY = 800f

// How much space the pages must leave at the bottom so nothing hides behind the bar.
@Composable
fun bottomBarClearance(): Dp {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return HandleAreaHeight + TabsHeight + BarBottomMargin + navInset + 16.dp
}

@Composable
fun HomeBottomBar(
    pagerState: PagerState,
    tray: Animatable<Float, AnimationVector1D>,
    onSelectPage: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalAzndStyle.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var trayHeightPx by remember { mutableIntStateOf(0) }

    val dragState = rememberDraggableState { delta ->
        if (trayHeightPx > 0) {
            scope.launch {
                tray.snapTo((tray.value - delta / trayHeightPx).coerceIn(0f, 1f))
            }
        }
    }

    // Top corners follow the theme's corner setting; bottom stays square on the screen edge.
    val shape = MaterialTheme.shapes.large.copy(
        bottomStart = ZeroCornerSize,
        bottomEnd = ZeroCornerSize
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = style.card,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 12.dp,
        border = if (style.cardOutline) BorderStroke(1.dp, style.outline) else null
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = BarBottomMargin)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        val target = when {
                            velocity < -FLING_VELOCITY -> 1f
                            velocity > FLING_VELOCITY -> 0f
                            tray.value >= 0.5f -> 1f
                            else -> 0f
                        }
                        tray.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
                    }
                )
        ) {
            // Handle: tap to toggle the quick actions, or drag anywhere on the bar.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HandleAreaHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Show or hide quick actions"
                    ) {
                        scope.launch { tray.animateTo(if (tray.value > 0.5f) 0f else 1f) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                )
            }

            // Quick actions tray, revealed by pulling the bar up.
            val trayHeight = with(density) { (trayHeightPx * tray.value).toDp() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trayHeight)
                    .clipToBounds()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .onSizeChanged { trayHeightPx = it.height }
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Settings")
                    }
                    OutlinedButton(
                        onClick = { /* TODO: export / import data */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ImportExport, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export / import data")
                    }
                    OutlinedButton(
                        onClick = { /* TODO: delete data */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete data", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            PageTabs(pagerState = pagerState, onSelectPage = onSelectPage)
        }
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

        // The sliding highlight follows the pager, so it moves with swipes and taps alike.
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
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "tabTint"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(3.dp)
                        .clip(style.buttonShape)
                        .clickable { onSelectPage(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(page.icon, contentDescription = null, tint = tint)
                    Text(page.title, style = MaterialTheme.typography.labelSmall, color = tint)
                }
            }
        }
    }
}