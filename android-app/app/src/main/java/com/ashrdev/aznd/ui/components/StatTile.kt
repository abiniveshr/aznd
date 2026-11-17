package com.ashrdev.aznd.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ashrdev.aznd.ui.theme.Space

/**
 * A small label + value tile, shared by Streaks, Body, Macros and the workout stats / history screens.
 * Label is labelMedium in onSurfaceVariant, value is titleLarge, both on one line. The optional
 * [supporting] line sits under the value. Content is centred vertically so a caller that fixes the
 * tile height (history grid) keeps it balanced. With [container] off only the text column is drawn,
 * for figures that already sit inside another card. Callers add clicks and heights through [modifier].
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
    supporting: String? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    container: Boolean = true
) {
    // One item for screen readers: "label, value, supporting".
    val merged = modifier.semantics(mergeDescendants = true) {}
    val align = if (horizontalAlignment == Alignment.CenterHorizontally) TextAlign.Center else TextAlign.Start
    val content: @Composable (Modifier) -> Unit = { inner ->
        Column(
            modifier = inner,
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = align,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = valueColor,
                textAlign = align,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = align,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (container) {
        Card(modifier = merged) {
            content(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = Space.lg, vertical = Space.md))
        }
    } else {
        content(merged)
    }
}
