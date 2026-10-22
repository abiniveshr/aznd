package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.ashrdev.aznd.data.streaks.StreakEntity
import com.ashrdev.aznd.ui.common.PhotoViewerDialog
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.FilledTonalButton
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.common.PhotoStore

private val BannerHeight = 200.dp

/** Banner aspect for the image picker (the box above is a 16:9-ish wide rectangle). */
const val STREAK_BANNER_WIDTH_PX = 1200
const val STREAK_BANNER_HEIGHT_PX = 675

/**
 * The wide rectangle at the top of the Details page. Empty: an Add photo button. With a photo: the
 * photo (tap to view full screen) with Replace / Remove over its lower edge.
 */
@Composable
fun StreakBannerCard(
    bannerUri: String?,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(bannerUri) { bannerUri?.let { PhotoStore.loadBitmap(context, it.toUri()) } }
    var showFull by remember { mutableStateOf(false) }

    if (bitmap != null && bannerUri != null) {
        Card(modifier = modifier.fillMaxWidth()) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(BannerHeight)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Streak banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showFull = true }
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = onPick) { Text("Replace") }
                    FilledTonalButton(onClick = onRemove) { Text("Remove") }
                }
            }
        }
        if (showFull) {
            PhotoViewerDialog(uriString = bannerUri, onDismiss = { showFull = false })
        }
    } else {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            modifier = modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BannerHeight)
            ) {
                OutlinedButton(onClick = onPick) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add photo")
                }
            }
        }
    }
}

/** Icon and name, with the day count under the name (and the quote under that, if there is one). */
@Composable
fun StreakIdentityCard(
    streak: StreakEntity,
    count: Int,
    onLogoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quote = streak.quote?.takeIf { it.isNotBlank() }
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StreakLogo(
                photoUri = streak.photoUri,
                size = 64.dp,
                modifier = Modifier.clickable(onClick = onLogoClick)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(streak.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    "$count day${if (count == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (quote != null) {
                    Text(
                        "\u201C$quote\u201D",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
