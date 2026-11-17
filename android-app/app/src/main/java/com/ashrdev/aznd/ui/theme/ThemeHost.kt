package com.ashrdev.aznd.ui.theme

import android.graphics.Bitmap
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.settings.PatternMode
import com.ashrdev.aznd.ui.settings.PerformanceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val COLD_CARD_DELAY_MS = 300L
private const val FADE_OUT_MS = 200
private const val DIM_ALPHA = 0.35f
private const val CAPTURE_SCALE = 0.25f
private const val BLUR_RADIUS = 6
private const val BLUR_PASSES = 3

private class HostMemo {
    var lastSets: Set<PatternSet>? = null
    var settledMode: PatternMode? = null
    var seenMode: PatternMode? = null
}

private fun darkOf(settings: ThemeSettings, system: Boolean): Boolean = when (settings.mode) {
    ThemeMode.SYSTEM -> system
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun ThemeHost(content: @Composable () -> Unit) {
    val committed by ThemeStore.settings.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = darkOf(committed, systemDark)
    val view = LocalView.current
    val pixelDensity = LocalDensity.current.density
    val patternMode = PerformanceStore.patternMode

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val size = BakeSize(constraints.maxWidth, constraints.maxHeight, pixelDensity)
        SideEffect { if (ScreenBakes.size != size) ScreenBakes.size = size }

        var shown by remember { mutableStateOf(committed) }
        var shownDark by remember { mutableStateOf(dark) }
        val startMissing = remember { patternMode == PatternMode.BAKED && ScreenBakes.missing(ScreenBakes.themeSets(committed, dark), size).isNotEmpty() }
        var covering by remember { mutableStateOf(startMissing) }
        var cold by remember { mutableStateOf(startMissing) }
        var backdrop by remember { mutableStateOf<ImageBitmap?>(null) }
        var done by remember { mutableIntStateOf(0) }
        var total by remember { mutableIntStateOf(0) }
        val memo = remember { HostMemo() }

        val shownSets = remember(shown, shownDark) { ScreenBakes.themeSets(shown, shownDark) }
        val extraRequired = ScreenBakes.required - shownSets

        LaunchedEffect(committed, dark, size, extraRequired, patternMode) {
            val baked = patternMode == PatternMode.BAKED
            val previousMode = memo.seenMode
            memo.seenMode = patternMode
            val needed = if (baked) ScreenBakes.themeSets(committed, dark) + extraRequired else emptySet()

            fun switchToCommitted() {
                ScreenBakes.setThemeRequired(ScreenBakes.themeSets(committed, dark))
                applyUiDensity(committed.density)
                shown = committed
                shownDark = dark
            }

            fun settle() {
                if (memo.lastSets == needed && memo.settledMode == patternMode) return
                memo.lastSets = needed
                memo.settledMode = patternMode
                ScreenBakes.retainOnly(needed + ScreenBakes.required, size)
                if (committed.mode == ThemeMode.SYSTEM) {
                    val other = ScreenBakes.themeSets(committed, !dark)
                    if (other.isNotEmpty()) ScreenBakes.prebake(other, size)
                }
                ScreenBakes.sweepAsync(committed)
            }

            if (!baked) {
                switchToCommitted()
                covering = false
                memo.lastSets = null
                memo.settledMode = patternMode
                if (previousMode == PatternMode.BAKED || previousMode == null) ScreenBakes.clearAll()
                return@LaunchedEffect
            }

            val missing = ScreenBakes.missing(needed, size)
            if (missing.isEmpty()) {
                switchToCommitted()
                covering = false
                settle()
                return@LaunchedEffect
            }

            if (!covering) {
                cold = false
                backdrop = null
                val shot = captureView(view)
                covering = true
                if (shot != null) {
                    backdrop = withContext(Dispatchers.Default) { boxBlur(shot, BLUR_RADIUS, BLUR_PASSES) }?.asImageBitmap()
                }
            }
            done = 0
            total = missing.size
            try {
                ScreenBakes.ensure(needed, size) { d, t ->
                    done = d
                    total = t
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
            }
            switchToCommitted()
            memo.lastSets = null
            ScreenBakes.retainOnly(needed + ScreenBakes.required, size)
            covering = false
            settle()
        }

        val themed = remember(shown, shownDark) {
            if (shown.mode == ThemeMode.SYSTEM) shown.copy(mode = if (shownDark) ThemeMode.DARK else ThemeMode.LIGHT) else shown
        }

        CompositionLocalProvider(LocalFillField provides IntSize(constraints.maxWidth, constraints.maxHeight)) {
            AzndTheme(themed) {
                content()
                ApplyingScreen(
                    covering = covering,
                    cold = cold,
                    backdrop = backdrop,
                    progress = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
                )
            }
        }
    }
}

@Composable
private fun ApplyingScreen(covering: Boolean, cold: Boolean, backdrop: ImageBitmap?, progress: Float) {
    AnimatedVisibility(
        visible = covering,
        modifier = Modifier.fillMaxSize(),
        enter = EnterTransition.None,
        exit = fadeOut(tween(FADE_OUT_MS))
    ) {
        BackHandler(enabled = covering) {}
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(covering) {
                    if (covering) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (cold) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            } else {
                if (backdrop != null) {
                    Image(backdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = DIM_ALPHA)))
            }
            var cardVisible by remember { mutableStateOf(!cold) }
            LaunchedEffect(Unit) {
                if (cold) {
                    delay(COLD_CARD_DELAY_MS)
                    cardVisible = true
                }
            }
            AnimatedVisibility(visible = cardVisible, enter = fadeIn(), exit = fadeOut()) {
                Card(modifier = Modifier.width(260.dp)) {
                    Column(modifier = Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                        Text("Applying theme", style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private fun captureView(view: View): Bitmap? = try {
    val w = (view.width * CAPTURE_SCALE).toInt()
    val h = (view.height * CAPTURE_SCALE).toInt()
    if (w <= 0 || h <= 0) {
        null
    } else {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.scale(CAPTURE_SCALE, CAPTURE_SCALE)
        view.draw(canvas)
        bitmap
    }
} catch (e: Throwable) {
    null
}

private fun boxBlur(src: Bitmap, radius: Int, passes: Int): Bitmap? = try {
    val w = src.width
    val h = src.height
    var a = IntArray(w * h)
    var b = IntArray(w * h)
    src.getPixels(a, 0, w, 0, 0, w, h)
    repeat(passes) {
        blurPass(a, b, w, h, radius, true)
        blurPass(b, a, w, h, radius, false)
    }
    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(a, 0, w, 0, 0, w, h) }
} catch (e: Throwable) {
    null
}

private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val n = if (horizontal) w else h
    val lines = if (horizontal) h else w
    val step = if (horizontal) 1 else w
    val span = 2 * r + 1
    for (line in 0 until lines) {
        val base = if (horizontal) line * w else line
        var sa = 0
        var sr = 0
        var sg = 0
        var sb = 0
        for (k in -r..r) {
            val p = src[base + k.coerceIn(0, n - 1) * step]
            sa += p ushr 24
            sr += (p shr 16) and 0xFF
            sg += (p shr 8) and 0xFF
            sb += p and 0xFF
        }
        for (i in 0 until n) {
            dst[base + i * step] = ((sa / span) shl 24) or ((sr / span) shl 16) or ((sg / span) shl 8) or (sb / span)
            val out = src[base + (i - r).coerceAtLeast(0) * step]
            val inn = src[base + (i + r + 1).coerceAtMost(n - 1) * step]
            sa += (inn ushr 24) - (out ushr 24)
            sr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
            sg += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
            sb += (inn and 0xFF) - (out and 0xFF)
        }
    }
}
