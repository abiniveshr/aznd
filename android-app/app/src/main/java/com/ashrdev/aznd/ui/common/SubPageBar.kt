package com.ashrdev.aznd.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import com.ashrdev.aznd.ui.theme.LocalAzndStyle
import com.ashrdev.aznd.ui.theme.Space
import com.ashrdev.aznd.ui.theme.ThemeColor
import com.ashrdev.aznd.ui.theme.themedFill
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One swipeable subpage of a screen, as shown in the bottom bar. */
data class BarPage(val title: String, val icon: ImageVector)

/**
 * What the bottom bar needs to draw the subpage tabs: the pages, the current (fractional) position
 * so the highlight slides while swiping, and what to do when a tab is tapped.
 */
class SubPageBar(
    val pages: List<BarPage>,
    val position: () -> Float,
    val onSelect: (Int) -> Unit
)

/** Builds a [SubPageBar] that follows [pagerState]. Keep [pages] a stable (top-level) list. */
@Composable
fun rememberSubPageBar(pagerState: PagerState, pages: List<BarPage>): SubPageBar {
    val scope = rememberCoroutineScope()
    return remember(pagerState, pages) {
        SubPageBar(
            pages = pages,
            position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } }
        )
    }
}

/** The tab row: icon + label per page, with a highlight that slides between them. */
@Composable
fun SubPageTabs(bar: SubPageBar, modifier: Modifier = Modifier) {
    val style = LocalAzndStyle.current
    val count = bar.pages.size.coerceAtLeast(1)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md)
            .height(TabsHeight)
    ) {
        val tabWidth = maxWidth / count

        Box(
            modifier = Modifier
                .offset {
                    IntOffset((bar.position() * tabWidth.toPx()).roundToInt(), 0)
                }
                .width(tabWidth)
                .fillMaxHeight()
                .padding(Space.xs)
                .clip(style.buttonShape)
                .themedFill(ThemeColor.HIGHLIGHT)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            bar.pages.forEachIndexed { index, page ->
                SubPageTab(bar, index, page)
            }
        }
    }
}

// One tab. "Selected" is derived, so a swipe recomposes only the two tabs whose state flips.
@Composable
private fun RowScope.SubPageTab(bar: SubPageBar, index: Int, page: BarPage) {
    val selected by remember(bar, index) { derivedStateOf { bar.position().roundToInt() == index } }
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "subPageTint"
    )
    ActionTile(icon = page.icon, label = page.title, tint = tint) {
        bar.onSelect(index)
    }
}
