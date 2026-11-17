package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.rememberPhotoBitmap

/** Streak logos are cropped square by the image picker and shown as a circle. */
const val STREAK_LOGO_ASPECT = 1f
const val STREAK_LOGO_PX = 800

@Composable
fun StreakLogo(photoUri: String?, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    // Sampling halves in steps, so twice the shown size keeps the picture at least 1:1.
    val bitmap = rememberPhotoBitmap(photoUri, (sizePx * 2).coerceIn(64, 400))
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Streak logo",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}
