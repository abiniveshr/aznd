package com.ashrdev.aznd.ui.theme

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How tight or loose the app's spacing is. [scale] multiplies every [Space] token. */
enum class UiDensity(val scale: Float) {
    COMPACT(0.85f),
    REGULAR(1f),
    ROOMY(1.15f)
}

// Snapshot state, so a token read in composition, layout or draw is read again when the density changes.
private val densityScale = mutableFloatStateOf(1f)

/** Makes [density] the one every [Space] token uses. Call on the main thread, with the committed theme's density. */
internal fun applyUiDensity(density: UiDensity) {
    densityScale.floatValue = density.scale
}

/** The spacing scale: 4, 8, 12, 16, 24 and 32 dp at Regular density, scaled by the density setting. */
object Space {
    val xs: Dp get() = (4f * densityScale.floatValue).dp
    val sm: Dp get() = (8f * densityScale.floatValue).dp
    val md: Dp get() = (12f * densityScale.floatValue).dp
    val lg: Dp get() = (16f * densityScale.floatValue).dp
    val xl: Dp get() = (24f * densityScale.floatValue).dp
    val xxl: Dp get() = (32f * densityScale.floatValue).dp
}
