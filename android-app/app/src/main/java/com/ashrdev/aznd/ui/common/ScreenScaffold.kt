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
    val reserve = if (reservePrimarySpace) PrimaryButtonHeight + PrimaryButtonGap else 0.dp

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
                    if (subtitle == null) {
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
                actions = { topBarActions() }
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
                if (primaryAction != null && tray.value < 0.98f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 16.dp, end = 16.dp, bottom = barHeight + PrimaryButtonGap)
                            .offset { IntOffset(0, -(trayTravelPx * tray.value).roundToInt()) }
                            .graphicsLayer { alpha = (1f - tray.value).coerceIn(0f, 1f) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (action in listOfNotNull(primaryAction, secondaryAction)) {
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
