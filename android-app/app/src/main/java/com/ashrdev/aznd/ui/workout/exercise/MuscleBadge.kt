package com.ashrdev.aznd.ui.workout.exercise

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashrdev.aznd.domain.workout.Muscle
import com.ashrdev.aznd.ui.common.rememberFileThumb

/**
 * Small rounded square showing the exercise photo if available, or falling back
 * to the 3-letter code of the primary muscle (CHE, LAT, UBK, etc.).
 */
@Composable
fun MuscleBadge(
    muscle: Muscle,
    photoPath: String?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val shape = RoundedCornerShape(8.dp)
    val targetPx = with(LocalDensity.current) { (size * 2).roundToPx() }
    val bitmap = rememberFileThumb(photoPath, targetPx)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = muscle.badgeCode,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
