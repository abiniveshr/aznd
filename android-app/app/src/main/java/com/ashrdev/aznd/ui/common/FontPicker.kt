package com.ashrdev.aznd.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.share.FlexFont
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.ui.theme.AppFonts
import com.ashrdev.aznd.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FontPicker(
    label: String,
    selected: FlexFont?,
    onSelect: (FlexFont?) -> Unit,
    modifier: Modifier = Modifier,
    systemDefaultLabel: String? = null
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var families by remember { mutableStateOf(emptyMap<FlexFont, FontFamily>()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(selected, open) {
        val wanted = if (open) FlexFont.entries else listOfNotNull(selected)
        val missing = wanted.filter { it !in families }
        if (missing.isNotEmpty()) {
            val built = withContext(Dispatchers.IO) { missing.associateWith { AppFonts.familyOf(it, context) } }
            families = families + built
        }
    }

    val matches = remember(query) {
        val q = query.trim()
        FlexFont.entries.filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) }
    }
    val showSystem = systemDefaultLabel != null &&
        (query.isBlank() || systemDefaultLabel.contains(query.trim(), ignoreCase = true))

    fun close() {
        open = false
        query = ""
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            val shape = MaterialTheme.shapes.small
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .clickable { open = true }
                    .padding(horizontal = Space.lg, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selected?.label ?: systemDefaultLabel.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = selected?.let { families[it] } ?: FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { close() }) {
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                Column(modifier = Modifier.widthIn(min = 240.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = Space.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.padding(end = Space.xs))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f).focusRequester(focus),
                            singleLine = true
                        )
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    }
                    Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        if (showSystem) {
                            DropdownMenuItem(
                                text = { Text(systemDefaultLabel.orEmpty()) },
                                trailingIcon = if (selected == null) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null,
                                onClick = {
                                    close()
                                    onSelect(null)
                                }
                            )
                        }
                        matches.forEach { font ->
                            DropdownMenuItem(
                                text = { Text(font.label, fontFamily = families[font] ?: FontFamily.Default) },
                                trailingIcon = if (font == selected) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null,
                                onClick = {
                                    close()
                                    onSelect(font)
                                }
                            )
                        }
                        if (matches.isEmpty() && !showSystem) {
                            DropdownMenuItem(text = { Text("No fonts match") }, enabled = false, onClick = {})
                        }
                    }
                }
            }
        }
    }
}
