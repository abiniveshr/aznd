package com.ashrdev.aznd.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Chrome that belongs to one page of a swipeable screen (its title, its top-bar buttons, its big
 * bottom buttons) has to travel with that page instead of snapping when the pager passes 50 %.
 *
 * Everything here reads the pager position inside graphicsLayer, so a swipe only redraws: nothing
 * recomposes per frame. The only recompositions happen when the position crosses a whole page
 * (to swap which two pages are on stage).
 *
 *  - Top bar (title, subtitle, actions): a short parallax drift (TopBarDrift) in the swipe direction
 *    plus a cross-fade tied to the finger. Text never flies across the bar, so it stays calm.
 *  - Everything else ([PageSlider]): moves 1:1 with the page, exactly like the page content.
 */

/** How far top-bar chrome drifts while its page is swiped one full page away. */
private val TopBarDrift = 36.dp

/** Outgoing chrome is gone at this fraction of a swipe, so two titles never sit on each other. */
private const val FADE_SPAN = 0.6f

/**
 * Signed distance of [page] from the scroll [position] for chrome purposes, or null if the page is
 * not on stage. Neighbouring pages that show identical chrome ([same]) count as one, so nothing
 * moves between them.
 */
private fun stageOffset(page: Int, position: Float, same: ((Int, Int) -> Boolean)?): Float? {
    val lo = floor(position).toInt()
    val f = position - lo
    val merged = same != null && same(lo, lo + 1)
    return when (page) {
        lo -> if (merged) 0f else -f
        lo + 1 -> if (merged) null else 1f - f
        else -> null
    }
}

private fun chromeAlpha(d: Float) = (1f - abs(d) / FADE_SPAN).coerceIn(0f, 1f)

/** Keeps the position inside the real pages (and safe when there are none). */
private fun clampPosition(position: Float, pageCount: Int): Float =
    position.coerceIn(0f, (pageCount - 1).coerceAtLeast(0).toFloat())

/**
 * The page at or left of the position (whose chrome, with the next page's, is composed) and the
 * page nearest to it (the one that takes taps). Both only change when a page boundary is crossed.
 */
@Composable
private fun rememberStage(pageCount: Int, position: () -> Float): Pair<State<Int>, State<Int>> {
    val pos by rememberUpdatedState(position)
    val count by rememberUpdatedState(pageCount)
    val lo = remember { derivedStateOf { floor(clampPosition(pos(), count)).toInt() } }
    val nearest = remember { derivedStateOf { clampPosition(pos(), count).roundToInt() } }
    return lo to nearest
}

/** Pages to compose right now: the one at or left of the position, and the one after it. */
private fun stagePages(lo: Int, pageCount: Int): List<Int> =
    listOf(lo, lo + 1).filter { it in 0 until pageCount }

/** Blocks taps and hides from accessibility: for the page that is fading out. */
private fun Modifier.inert(): Modifier = clearAndSetSemantics { }

@Composable
private fun BoxScope.InputShield() {
    Box(
        Modifier
            .matchParentSize()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
    )
}

/**
 * Top-bar title for a swipeable screen: each page's title (and optional subtitle) drifts and
 * cross-fades with the swipe. Pages with the same title/subtitle as their neighbour stay still.
 * Also fine for small per-page labels elsewhere (e.g. a "2 / 5" counter): style it with
 * ProvideTextStyle, and use [endAligned] when the label sits against the right edge.
 */
@Composable
fun SlidingPageTitle(
    pageCount: Int,
    position: () -> Float,
    title: (Int) -> String,
    subtitle: ((Int) -> String?)? = null,
    modifier: Modifier = Modifier,
    endAligned: Boolean = false
) {
    val pos by rememberUpdatedState(position)
    val (lo, _) = rememberStage(pageCount, position)
    val driftPx = with(LocalDensity.current) { TopBarDrift.toPx() }
    val same: (Int, Int) -> Boolean = { a, b ->
        b < pageCount && title(a) == title(b) && subtitle?.invoke(a) == subtitle?.invoke(b)
    }

    Box(modifier, contentAlignment = if (endAligned) Alignment.CenterEnd else Alignment.CenterStart) {
        for (page in stagePages(lo.value, pageCount)) {
            key(page) {
                val sub = subtitle?.invoke(page)
                Column(
                    horizontalAlignment = if (endAligned) Alignment.End else Alignment.Start,
                    modifier = Modifier.graphicsLayer {
                        val d = stageOffset(page, clampPosition(pos(), pageCount), same)
                        if (d == null) {
                            alpha = 0f
                        } else {
                            alpha = chromeAlpha(d)
                            translationX = d * driftPx
                        }
                    }
                ) {
                    Text(title(page), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (sub != null) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top-bar action buttons that belong to individual pages. Each page's row drifts and fades with
 * the swipe; only the page you are nearest to takes taps. A page with no actions emits nothing.
 */
@Composable
fun SlidingPageActions(
    pageCount: Int,
    position: () -> Float,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.(page: Int) -> Unit
) {
    val pos by rememberUpdatedState(position)
    val (lo, nearest) = rememberStage(pageCount, position)
    val driftPx = with(LocalDensity.current) { TopBarDrift.toPx() }

    Box(modifier, contentAlignment = Alignment.CenterEnd) {
        // The nearest page goes last so it sits on top where the two rows overlap.
        val pages = stagePages(lo.value, pageCount).sortedBy { if (it == nearest.value) 1 else 0 }
        for (page in pages) {
            key(page) {
                val active = page == nearest.value
                Box(if (active) Modifier else Modifier.inert()) {
                    Row(
                        Modifier.graphicsLayer {
                            val d = stageOffset(page, clampPosition(pos(), pageCount), null)
                            if (d == null) {
                                alpha = 0f
                            } else {
                                alpha = chromeAlpha(d)
                                translationX = d * driftPx
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) { actions(page) }
                    if (!active) InputShield()
                }
            }
        }
    }
}

/**
 * Hosts per-page UI that lives outside the pager (bottom buttons, floating buttons, page
 * indicators...) and moves it 1:1 with its page, as if it were part of the page. Give it the same
 * width as the pager (normally full width); put any side padding inside [content].
 */
@Composable
fun PageSlider(
    pageCount: Int,
    position: () -> Float,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.BottomCenter,
    content: @Composable BoxScope.(page: Int) -> Unit
) {
    val pos by rememberUpdatedState(position)
    val (lo, _) = rememberStage(pageCount, position)

    Box(modifier) {
        for (page in stagePages(lo.value, pageCount)) {
            key(page) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(contentAlignment)
                        .graphicsLayer {
                            val p = clampPosition(pos(), pageCount)
                            translationX = (page - p) * size.width
                        },
                    contentAlignment = contentAlignment
                ) { content(page) }
            }
        }
    }
}
