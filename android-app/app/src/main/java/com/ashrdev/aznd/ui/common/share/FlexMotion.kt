package com.ashrdev.aznd.ui.common.share

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

/**
 * The spring every glide in Flex runs on, and on the Home dashboard's edit mode: tiles, the card's height and the
 * dashboard page's height. Critically damped (no overshoot) and stiff enough to settle in about [FLEX_GLIDE_MS], so
 * it keeps its speed when the target changes mid-glide, where a fixed tween would stop and start again.
 * [threshold] is how close to the target counts as arrived, in the unit of the value that moves (card pixels,
 * dp, or the card's height over its width): it has to be far below a pixel, as a spring stops where it is inside it.
 */
internal fun flexGlide(threshold: Float): SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = FLEX_GLIDE_STIFFNESS,
    visibilityThreshold = threshold
)

/** A critically damped spring settles within 0.5 % of its distance in 7.4 / sqrt(stiffness) seconds: 0.26 s here. */
internal const val FLEX_GLIDE_STIFFNESS = 800f

/** Arrived, for a tile's position in card pixels (Flex) or dp (the dashboard). */
internal const val FLEX_GLIDE_PIXEL = 0.1f

/** Arrived, for the card's height over its width. */
internal const val FLEX_GLIDE_RATIO = 0.0004f

/** How much a held tile grows at full lift (4 %), and how deep its shadow is then. */
internal const val FLEX_LIFT_GROW = 0.04f
internal val FLEX_LIFT_SHADOW = 14.dp
