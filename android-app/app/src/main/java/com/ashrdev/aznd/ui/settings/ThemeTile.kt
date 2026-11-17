package com.ashrdev.aznd.ui.settings

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.theme.Palette
import com.ashrdev.aznd.ui.theme.ThemeColor
import com.ashrdev.aznd.ui.theme.ThemeDef
import com.ashrdev.aznd.ui.theme.ThemeKind
import com.ashrdev.aznd.ui.theme.ThemeLibrary
import com.ashrdev.aznd.ui.theme.ThemeStore
import com.ashrdev.aznd.ui.theme.azndFill
import com.ashrdev.aznd.ui.theme.resolved

private val TileWidth = 96.dp
private val TileHeight = 120.dp
private val PreviewHeight = 84.dp
private val TileFrame = RoundedCornerShape(12.dp)
private val HueColors = List(7) { Color.hsv((it * 60f) % 360f, 1f, 1f) }

@Composable
fun ThemeTile(
    def: ThemeDef,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onPickBase: (() -> Unit)?
) {
    val dark = appShowsDark()
    val context = LocalContext.current
    val draft by ThemeStore.draft.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val pal: Palette = if (def.kind == ThemeKind.WALLPAPER) {
        val s = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else scheme
        Palette(
            background = s.background,
            card = s.surfaceVariant,
            topBar = s.surface,
            text = s.onSurface,
            button = s.primary,
            highlight = s.primaryContainer,
            outline = s.outline
        )
    } else {
        remember(def, dark, draft.themeId, draft.baseColor, draft.shade) {
            ThemeLibrary.shown(def, ThemeLibrary.baseOf(def.id), ThemeLibrary.shadeOf(def.id), dark)
        }
    }
    val cardShape = RoundedCornerShape(6.dp)
    val pill = RoundedCornerShape(50)

    Column(
        modifier = Modifier
            .size(width = TileWidth, height = TileHeight)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PreviewHeight)
                .clip(TileFrame)
                .azndFill(pal.resolved(ThemeColor.BACKGROUND), TileFrame, preview = true)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) pal.button else pal.outline.copy(alpha = 0.4f),
                    TileFrame
                )
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(cardShape)
                        .azndFill(pal.resolved(ThemeColor.CARD), cardShape, preview = true)
                        .border(1.dp, pal.outline.copy(alpha = 0.5f), cardShape)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(pal.text)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(pal.text.copy(alpha = 0.7f))
                    )
                }
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(14.dp)
                        .clip(pill)
                        .azndFill(pal.resolved(ThemeColor.BUTTON), pill, preview = true)
                )
            }
            if (onPickBase != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(48.dp)
                        .clickable(onClick = onPickBase)
                        .semantics { contentDescription = "Base colour" },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawCircle(Brush.sweepGradient(HueColors))
                        drawCircle(
                            color = Color.White.copy(alpha = 0.8f),
                            radius = size.minDimension / 2f - 0.5.dp.toPx(),
                            style = Stroke(1.dp.toPx())
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp)) {
            if (!def.builtIn) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Custom theme",
                    modifier = Modifier.size(12.dp)
                )
                Box(modifier = Modifier.width(2.dp))
            }
            Text(
                def.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!enabled) {
            Text(
                "Needs Android 12",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
internal fun AddThemeTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(width = TileWidth, height = TileHeight)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PreviewHeight)
                .clip(TileFrame)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), TileFrame),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
        }
        Text(
            "Add custom theme",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
