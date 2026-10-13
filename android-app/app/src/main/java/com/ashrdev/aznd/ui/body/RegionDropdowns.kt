package com.ashrdev.aznd.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.OutlinedButton

data class ModelEntry(val label: String, val onPick: () -> Unit)

data class ModelRegion(val label: String, val entries: List<ModelEntry>)

private const val REGIONS_PER_ROW = 3

@Composable
fun RegionDropdownGrid(
    regions: List<ModelRegion>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        regions.chunked(REGIONS_PER_ROW).forEach { rowRegions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowRegions.forEach { region ->
                    RegionDropdown(region, Modifier.weight(1f))
                }
                repeat(REGIONS_PER_ROW - rowRegions.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RegionDropdown(region: ModelRegion, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(region.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            region.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label) },
                    onClick = {
                        expanded = false
                        entry.onPick()
                    }
                )
            }
        }
    }
}
