package com.ashrdev.aznd.ui.streaks

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import java.util.Calendar

// Back in the bottom bar cancels. Save stays in the top bar so it's reachable with the keyboard up.
@Composable
fun StreakEditorScreen(
    viewModel: StreakViewModel,
    onDone: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(todayKey()) }
    var photoUri by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberImagePicker(
        aspect = 16f to 9f,
        outputWidth = 1200,
        outputHeight = 675,
        onImagePicked = { uri -> photoUri = uri.toString() }
    )

    ScreenScaffold(
        title = "New Streak",
        onBack = onDone,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        topBarActions = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    viewModel.createStreak(name.trim(), startDate, photoUri)
                    onDone()
                }
            }) {
                Text("Save")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            photoUri?.let { uri -> StreakBanner(uri) }
            OutlinedButton(onClick = { imagePicker.launch() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (photoUri != null) "Replace photo" else "Add photo (optional)")
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Streak name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth -> startDate = dateKey(year, month, dayOfMonth) },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start date: ${displayDate(startDate)}")
            }
        }
    }
}
