package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
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
import com.ashrdev.aznd.ui.common.PhotoStore

private val BannerHeight = 200.dp
private val PreviewHeight = 140.dp

/** Banner aspect for the image picker (the box above is a 16:9-ish wide rectangle). */
const val STREAK_BANNER_WIDTH_PX = 1200
const val STREAK_BANNER_HEIGHT_PX = 675

@Composable
fun StreakBannerImage(bannerUri: String?, modifier: Modifier = Modifier, height: Dp = BannerHeight, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val bitmap = remember(bannerUri) { bannerUri?.let { PhotoStore.loadBitmap(context, it.toUri()) } }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Icon(
                Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
fun StreakBannerCard(
    bannerUri: String?,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFull by remember { mutableStateOf(false) }
    StreakBannerImage(
        bannerUri = bannerUri,
        modifier = modifier,
        onClick = { if (bannerUri != null) showFull = true else onPick() }
    )
    if (showFull && bannerUri != null) {
        PhotoViewerDialog(uriString = bannerUri, onDismiss = { showFull = false })
    }
}

@Composable
fun StreakPreviewHeader(bannerUri: String?, photoUri: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        StreakBannerImage(bannerUri = bannerUri, height = PreviewHeight, modifier = Modifier.padding(bottom = 28.dp))
        StreakLogo(
            photoUri = photoUri,
            size = 64.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp)
                .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
        )
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
