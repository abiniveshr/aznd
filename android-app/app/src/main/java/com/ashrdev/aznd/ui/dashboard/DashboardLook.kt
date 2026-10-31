package com.ashrdev.aznd.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.share.FlexBitmaps
import com.ashrdev.aznd.ui.common.share.FlexFont
import com.ashrdev.aznd.ui.common.share.FlexFonts
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.theme.BorderKind
import com.ashrdev.aznd.ui.theme.LocalAzndStyle
import com.ashrdev.aznd.ui.theme.ResolvedFill
import com.ashrdev.aznd.ui.theme.azndFill
import com.ashrdev.aznd.ui.theme.taperEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The look the dashboard's tiles are drawn with. Provided around every dashboard grid. */
val LocalDashLook = compositionLocalOf { DashLook() }

/** The font and text size every tile's text is drawn (and measured) with. */
class DashText(val family: FontFamily?, val scale: Float)

val LocalDashText = compositionLocalOf { DashText(null, 1f) }

/** Provides [look] (and its font) to the tiles inside [content]. */
@Composable
fun ProvideDashLook(look: DashLook, content: @Composable () -> Unit) {
    val family = rememberDashFont(look.font)
    val text = remember(family, look.textScale) { DashText(family, look.textScale) }
    CompositionLocalProvider(LocalDashLook provides look, LocalDashText provides text, content = content)
}

/** One of Flex's bundled fonts as a Compose font; null = the system font. */
@Composable
private fun rememberDashFont(font: FlexFont): FontFamily? {
    val context = LocalContext.current
    return remember(font) {
        FlexFonts.attach(context)
        FlexFonts.load(font, false)?.let { FontFamily(androidx.compose.ui.text.font.Typeface(it)) }
    }
}

/** The page behind the tiles. With the App theme background it draws nothing (the screen's fill shows). */
@Composable
fun DashBackground(look: DashLook, modifier: Modifier = Modifier) {
    when (look.bgMode) {
        DashBgMode.THEME -> Unit
        DashBgMode.SOLID, DashBgMode.GRADIENT ->
            Box(modifier.fillMaxSize().azndFill(ResolvedFill(Color(look.bgA), look.backgroundFill())))
        DashBgMode.PHOTO -> {
            val context = LocalContext.current
            val uri = look.photoUri
            val blur = look.photoBlur
            val photo by produceState<ImageBitmap?>(null, uri, blur) {
                value = if (uri == null) null else withContext(Dispatchers.IO) {
                    FlexBitmaps.load(context, uri, PHOTO_MAX)?.let { FlexBitmaps.blur(it, blur) }?.asImageBitmap()
                }
            }
            Box(modifier.fillMaxSize().background(Color.Black)) {
                photo?.let {
                    Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = look.photoDim)))
            }
        }
    }
}

/**
 * A tile's panel as [LocalDashLook] styles it, with the text colours inside it. App theme tiles are
 * the app's own cards. [content] fills the panel.
 */
@Composable
fun DashTileFrame(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val look = LocalDashLook.current
    val app = LocalAzndStyle.current
    val scheme = MaterialTheme.colorScheme
    val textColor = if (look.customColors) Color(look.textColor) else scheme.onSurface
    val themed = if (look.customColors) {
        scheme.copy(
            primary = Color(look.accent),
            onSurface = textColor,
            onSurfaceVariant = textColor.copy(alpha = MUTED),
            onPrimaryContainer = textColor
        )
    } else {
        scheme
    }
    val inner: @Composable () -> Unit = {
        MaterialTheme(colorScheme = themed, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
            CompositionLocalProvider(LocalContentColor provides textColor) {
                Box(Modifier.fillMaxSize(), content = content)
            }
        }
    }
    if (look.tiles == DashTileStyle.THEME) {
        Card(modifier = modifier.clip(CardDefaults.shape)) { inner() }
        return
    }
    val shape = RoundedCornerShape(look.tileRadius.dp)
    val panel = if (look.customColors) Color(look.tileColor) else app.card
    val line = look.tileBorderKind == BorderKind.LINE
    val edgeColor = if (look.tileBorder > 0f || line) Color(look.tileBorderColor) else textColor.copy(alpha = 0.45f)
    val edge = when {
        line -> null
        look.tileBorder > 0f && look.tiles != DashTileStyle.NONE -> look.tileBorder
        look.tiles == DashTileStyle.OUTLINE -> 1.5f
        look.tiles == DashTileStyle.GLASS -> 1f
        else -> null
    }
    val edgeTint = if (look.tiles == DashTileStyle.GLASS && look.tileBorder <= 0f) Color.White.copy(alpha = 0.28f) else edgeColor
    Box(
        modifier
            .clip(shape)
            .then(if (edge != null) Modifier.border(edge.dp, edgeTint, shape) else Modifier)
            .then(if (line && look.tileBorder > 0f) Modifier.taperEdge(look.lineTaper, edgeColor, look.tileBorder.dp, top = false) else Modifier)
    ) {
        when (look.tiles) {
            DashTileStyle.FILLED -> Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = look.tileAlpha }
                    .azndFill(ResolvedFill(panel, look.tileFill), shape)
            )
            DashTileStyle.GLASS -> Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.10f), shape))
            else -> Unit
        }
        inner()
    }
}

private const val MUTED = 0.72f
private const val PHOTO_MAX = 1600
