package com.ashrdev.aznd.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Two-part page: [top] hugs the top, [bottom] (your calendar) is pinned to the bottom edge.
 * On short screens the whole page scrolls instead of squashing.
 */
@Composable
fun BottomAnchoredPage(
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    top: @Composable ColumnScope.() -> Unit,
    bottom: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewport)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = top)
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = bottom)
        }
    }
}

/**
 * Heading for the tapped day, with the favourite toggle on the right. Pass
 * [onToggleFavorite] = null on days with nothing to favourite and the heart disappears.
 */
@Composable
fun SelectedDayHeader(
    dayKey: String,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val suffix = if (dayKey == todayDayKey()) " · Today" else ""
        Text(
            formatDayLong(dayKey) + suffix,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        if (onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove favourite" else "Mark as favourite",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
