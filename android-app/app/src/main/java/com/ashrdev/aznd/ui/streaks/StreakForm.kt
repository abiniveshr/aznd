package com.ashrdev.aznd.ui.streaks

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.streaks.DEFAULT_MILESTONES
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import java.util.Calendar

const val STREAK_QUOTE_LIMIT = 80

/** Everything the "new streak" form edits. Lives above the form so it survives page swipes. */
@Stable
class StreakDraft {
    var name by mutableStateOf("")
    var quote by mutableStateOf("")
    var startDate by mutableStateOf(todayKey())
    var photoUri by mutableStateOf<String?>(null)
    val milestones = mutableStateListOf<Int>().apply { addAll(DEFAULT_MILESTONES) }

    val canSave: Boolean get() = name.isNotBlank()

    fun addMilestone(days: Int) {
        if (days <= 0 || days in milestones) return
        milestones.add(days)
        milestones.sort()
    }

    fun resetMilestones() {
        milestones.clear()
        milestones.addAll(DEFAULT_MILESTONES)
    }

    fun reset() {
        name = ""
        quote = ""
        startDate = todayKey()
        photoUri = null
        resetMilestones()
    }
}

@Composable
fun rememberStreakDraft(): StreakDraft = remember { StreakDraft() }

fun StreakViewModel.createStreak(draft: StreakDraft) = createStreak(
    name = draft.name.trim(),
    startDate = draft.startDate,
    photoUri = draft.photoUri,
    quote = draft.quote,
    milestones = draft.milestones.toList()
)

/**
 * The create-streak fields, shared by the Streaks pager's last page and the standalone
 * StreakEditorScreen. [bottomPadding] keeps the last field clear of the bottom bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StreakForm(draft: StreakDraft, bottomPadding: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var milestoneInput by remember { mutableStateOf("") }

    val imagePicker = rememberImagePicker(
        aspect = STREAK_LOGO_ASPECT to 1f,
        outputWidth = STREAK_LOGO_PX,
        outputHeight = STREAK_LOGO_PX,
        onImagePicked = { uri -> draft.photoUri = uri.toString() }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Logo
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StreakLogo(
                photoUri = draft.photoUri,
                size = 80.dp,
                modifier = Modifier.clickable { imagePicker.launch() }
            )
            Spacer(Modifier.width(16.dp))
            Column {
                OutlinedButton(onClick = { imagePicker.launch() }) {
                    Text(if (draft.photoUri != null) "Replace logo" else "Add logo (optional)")
                }
                if (draft.photoUri != null) {
                    TextButton(onClick = { draft.photoUri = null }) { Text("Remove logo") }
                }
            }
        }

        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft.name = it },
            label = { Text("Streak name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = draft.quote,
            onValueChange = { if (it.length <= STREAK_QUOTE_LIMIT) draft.quote = it },
            label = { Text("Quote (optional, ${draft.quote.length}/$STREAK_QUOTE_LIMIT)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedButton(
            onClick = {
                val cal = Calendar.getInstance()
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth -> draft.startDate = dateKey(year, month, dayOfMonth) },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start date: ${displayDate(draft.startDate)}")
        }

        // Milestones
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Milestones (days)", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { draft.resetMilestones() }) { Text("Reset to default") }
        }
        if (draft.milestones.isEmpty()) {
            Text(
                "No milestones. Add one below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            draft.milestones.forEach { days ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.clickable { draft.milestones.remove(days) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        Text("$days", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove $days",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = milestoneInput,
                onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) milestoneInput = it },
                label = { Text("Add milestone") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    milestoneInput.toIntOrNull()?.let { draft.addMilestone(it) }
                    milestoneInput = ""
                },
                enabled = (milestoneInput.toIntOrNull() ?: 0) > 0
            ) {
                Text("Add")
            }
        }
    }
}
