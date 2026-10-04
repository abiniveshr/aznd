package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.data.workout.formLabel
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.ThirdAction
import com.ashrdev.aznd.ui.components.Card

/**
 * Lists the user's custom exercises. Tap a card or the pencil to edit, the bin to delete (with a
 * confirmation), the + at the bottom to add a new one. Catalog exercises are not listed.
 */
@Composable
fun CustomExercisesScreen(
    viewModel: CustomExercisesViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val items by viewModel.exercises.collectAsState()
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<Exercise?>(null) }

    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete exercise?") },
            text = {
                Text(
                    "\"${target.name}\" will be removed from every workout, and every set logged with it " +
                        "will be deleted from your history and stats, as if it never existed. " +
                        "This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target.id) { deleted ->
                        deleted.photoPath?.let { deletePrivatePhoto(context, it) }
                    }
                    toDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }

    ScreenScaffold(
        title = "Custom exercises",
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        third = ThirdAction(icon = Icons.Default.Add, label = "Add exercise", onClick = onAdd)
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    "No custom exercises yet — tap Add exercise to create one.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { e ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onEdit(e.id) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                        ) {
                            MuscleBadge(e.primaryMuscle, e.photoPath, size = 48.dp)
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val muscles = (listOf(e.primaryMuscle) + e.secondaryMuscles)
                                    .joinToString(", ") { it.name.lowercase().replace('_', ' ') }
                                Text(
                                    "$muscles  •  ${e.type.formLabel()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onEdit(e.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit ${e.name}")
                            }
                            IconButton(onClick = { toDelete = e }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete ${e.name}", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
