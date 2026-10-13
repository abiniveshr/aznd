package com.ashrdev.aznd.ui.workout

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.data.workout.ExerciseTutorial
import com.ashrdev.aznd.data.workout.TutorialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val FRAME_MS = 900L

private class TutorialLoad(val tutorial: ExerciseTutorial?, val frames: List<ImageBitmap>)

@Composable
fun ExerciseTutorialPage(exercise: Exercise, bottomPadding: Dp) {
    val context = LocalContext.current.applicationContext
    val load by produceState<TutorialLoad?>(null, exercise.id) {
        value = withContext(Dispatchers.IO) {
            val tutorial = TutorialStore.forExercise(context, exercise.seedKey)
            val frames = tutorial?.frames.orEmpty().mapNotNull { path ->
                runCatching {
                    context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                }.getOrNull()
            }
            TutorialLoad(tutorial, frames)
        }
    }

    val loaded = load ?: return
    val tutorial = loaded.tutorial
    if (tutorial == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "No tutorial for this exercise yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (loaded.frames.isNotEmpty()) {
            item(key = "frames") { FrameLoop(loaded.frames) }
        }
        if (tutorial.steps.isNotEmpty()) {
            item(key = "steps-title") { Text("Steps", style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(tutorial.steps) { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
        tutorial.credit?.let { credit ->
            item(key = "credit") {
                Text(
                    credit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FrameLoop(frames: List<ImageBitmap>) {
    var index by remember(frames) { mutableStateOf(0) }
    if (frames.size > 1) {
        LaunchedEffect(frames) {
            while (true) {
                delay(FRAME_MS)
                index = (index + 1) % frames.size
            }
        }
    }
    val first = frames.first()
    Crossfade(
        targetState = index,
        animationSpec = tween(400),
        label = "tutorialFrame",
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(first.width.toFloat() / first.height.toFloat())
    ) { i ->
        frames.getOrNull(i)?.let { frame ->
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
