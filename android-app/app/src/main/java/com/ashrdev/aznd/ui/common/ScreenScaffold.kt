package com.ashrdev.aznd.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextOverflow
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
import com.ashrdev.aznd.ui.theme.ThemeColor
import com.ashrdev.aznd.ui.theme.themedFill
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val PrimaryButtonHeight = 64.dp
private val PrimaryButtonGap = 12.dp

// Every screen except the Home pager sits inside this: top bar with a title and a Back button on
// its left, content, and the persistent bottom bar (optional actions, pull up for Home /
// Settings / Manage data). Pass [subPages] on screens with swipeable subpages: the bar then shows
// their tabs instead of [third] / [extraActions].
// `content` receives padding whose bottom value keeps your list clear of the bar (and of the
// big primary button when there is one).
//
// Swipeable screens: chrome that belongs to ONE page must not be picked with
// `pagerState.currentPage` (it would snap at the halfway point). Use the page* parameters instead,
// which follow the swipe (see PagedChrome.kt):
//  - [pageTitle] / [pageSubtitle]: per-page title, drifts and cross-fades with the finger.
//  - [pageTopBarActions]: per-page top bar buttons, same motion; only the nearest page takes taps.
//    [topBarActions] stays for buttons every page shares (they don't move).
//  - [pagePrimaryActions]: per-page big bottom buttons (0-2 per page); they slide 1:1 with the page.
// A pager without bottom-bar tabs (e.g. the session's exercise cards) can drive the same page*
// parameters by passing [pageCount] + [pagePosition] instead of [subPages].
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenScaffold(
    title: String,
    // Small second line under the title (e.g. the workout name under the exercise name).
    subtitle: String? = null,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    third: ThirdAction? = null,
    extraActions: List<ThirdAction> = emptyList(),
    primaryAction: PrimaryAction? = null,
    // Shown next to [primaryAction], splitting the big button in two (e.g. Add workout | Empty workout).
    secondaryAction: PrimaryAction? = null,
    // Keep the space for the big button even when it is not shown (pager pages that don't have it).
    reservePrimarySpace: Boolean = primaryAction != null,
    settingsEnabled: Boolean = true,
    manageDataEnabled: Boolean = true,
    subPages: SubPageBar? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    pageTitle: ((page: Int) -> String)? = null,
    pageSubtitle: ((page: Int) -> String?)? = null,
    pageTopBarActions: (@Composable RowScope.(page: Int) -> Unit)? = null,
    pagePrimaryActions: ((page: Int) -> List<PrimaryAction>)? = null,
    // Only for pagers without tabs; ignored when [subPages] is given (it carries both).
    pageCount: Int = 0,
    pagePosition: (() -> Float)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val tray = rememberTrayState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.isImeVisible
    val hasMainRow = subPages != null || third != null || extraActions.isNotEmpty()
    val clearance = bottomBarClearance(hasMainRow)
    val barHeight = bottomBarHeight(hasMainRow)
    val trayTravelPx = with(density) { TabsHeight.toPx() }
    // Where per-page chrome takes its swipe position from: the tabs, or a bare pager.
    val chromeCount = subPages?.pages?.size ?: pageCount
    val chromePosition = subPages?.position ?: pagePosition?.takeIf { pageCount > 0 }
    val pagedPrimary = if (chromePosition != null) pagePrimaryActions else null
    val reserve = if (reservePrimarySpace || pagedPrimary != null) PrimaryButtonHeight + PrimaryButtonGap else 0.dp

    BackHandler(enabled = tray.value > 0f) { scope.launch { tray.settleTo(0f) } }

    Scaffold(
        // The screen's fill (gradient / pattern included) is painted by the modifier; Material's own
        // container is see-through, so its content colour is set by hand.
        modifier = Modifier.themedFill(ThemeColor.BACKGROUND),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    if (chromePosition != null && pageTitle != null) {
                        SlidingPageTitle(
                            pageCount = chromeCount,
                            position = chromePosition,
                            title = pageTitle,
                            subtitle = pageSubtitle
                        )
                    } else if (subtitle == null) {
                        Text(title)
                    } else {
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (chromePosition != null && pageTopBarActions != null) {
                        SlidingPageActions(
                            pageCount = chromeCount,
                            position = chromePosition,
                            actions = pageTopBarActions
                        )
                    }
                    topBarActions()
                }
            )
        }
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
                // Per-page buttons also slide sideways 1:1 with their page.
                val hasPrimary = pagedPrimary != null || primaryAction != null
                if (hasPrimary && tray.value < 0.98f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = barHeight + PrimaryButtonGap)
                            .offset { IntOffset(0, -(trayTravelPx * tray.value).roundToInt()) }
                            .graphicsLayer { alpha = (1f - tray.value).coerceIn(0f, 1f) }
                    ) {
                        if (pagedPrimary != null && chromePosition != null) {
                            PageSlider(
                                pageCount = chromeCount,
                                position = chromePosition,
                                modifier = Modifier.fillMaxWidth()
                            ) { page ->
                                PrimaryButtonRow(pagedPrimary(page))
                            }
                        } else {
                            PrimaryButtonRow(listOfNotNull(primaryAction, secondaryAction))
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
                    onHome = onHome,
                    tray = tray,
                    onOpenSettings = onOpenSettings,
                    third = third,
                    extraActions = extraActions,
                    settingsEnabled = settingsEnabled,
                    manageDataEnabled = manageDataEnabled,
                    subPages = subPages,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

/** One or two big buttons side by side, with the screen's side margins. Empty list = nothing. */
@Composable
private fun PrimaryButtonRow(actions: List<PrimaryAction>) {
    if (actions.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (action in actions) {
            Button(
                onClick = action.onClick,
                enabled = action.enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(PrimaryButtonHeight)
            ) {
                Icon(action.icon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(action.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        }
    }
}
