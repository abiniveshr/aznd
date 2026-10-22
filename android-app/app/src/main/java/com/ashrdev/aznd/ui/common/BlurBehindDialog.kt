package com.ashrdev.aznd.ui.common

import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Blurs and dims whatever is behind the dialog this is placed in. The blur is done by the window
 * compositor (Android 12+, when the device allows it), so it costs nothing in Compose: no
 * re-drawing the page, no extra layers.
 *
 * [dim] is the darkness behind the dialog when the blur is on. Where blur isn't available the
 * normal dim is left exactly as it was, unless [dimWithoutBlur] is given: a blur hides the page's
 * text, a plain dim doesn't, so full-screen viewers pass a darker value to cover for it.
 */
@Composable
fun BlurBehindDialog(
    radius: Dp = 10.dp,
    dim: Float = 0.25f,
    dimWithoutBlur: Float? = null
) {
    val view = LocalView.current
    val radiusPx = with(LocalDensity.current) { radius.roundToPx() }
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        val blurAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            view.context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
        if (blurAvailable) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.also { it.blurBehindRadius = radiusPx }
            window.setDimAmount(dim)
        } else if (dimWithoutBlur != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(dimWithoutBlur)
        }
    }
}
