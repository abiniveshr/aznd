package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.ui.components.OutlinedTextField as FieldBox

/**
 * Opens the "new custom exercise" form. Provided by MainActivity around the screens that can pick
 * or browse exercises; when it is null there is no "Add custom exercise" button.
 */
val LocalOpenCustomExercise = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Exercise field. Looks like a text field showing the selected name, but tapping it opens the
 * full-screen exercise browser (ExerciseBrowser.kt; no dropdown, so the keyboard stays up while typing).
 */
@Composable
fun ExercisePicker(
    selected: Exercise?,
    exercises: List<Exercise>,
    onPick: (Exercise) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Exercise",
    // false for pickers that only look things up (stats): no "Add custom exercise" button
    allowAddCustom: Boolean = true
) {
    var open by remember { mutableStateOf(false) }
    val openCustom = if (allowAddCustom) LocalOpenCustomExercise.current else null

    Box(modifier) {
        FieldBox(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        // transparent overlay: swallows the tap so the field never takes focus / opens the keyboard
        Box(Modifier.matchParentSize().clickable { open = true })
    }

    if (open) {
        ExerciseSearchScreen(
            selected = selected,
            exercises = exercises,
            onDismiss = { open = false },
            onPick = {
                open = false
                onPick(it)
            },
            onAddCustom = openCustom?.let { go ->
                {
                    open = false
                    go()
                }
            }
        )
    }
}

@Composable
fun ExercisePickerDialog(
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onPick: (Exercise) -> Unit
) {
    val openCustom = LocalOpenCustomExercise.current
    ExerciseSearchScreen(
        selected = null,
        exercises = exercises,
        onDismiss = onDismiss,
        onPick = onPick,
        onAddCustom = openCustom?.let { go ->
            {
                onDismiss()
                go()
            }
        }
    )
}
