package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.streaks.StreakEntity
import com.ashrdev.aznd.ui.components.Card

private fun days(n: Int) = "$n day${if (n == 1) "" else "s"}"

/**
 * Centre page of the streak screen: banner photo, icon + name + day count, key numbers, the next
 * milestone, and Delete at the very bottom. (Save today / Log a slip / Saved days are on Calendar.)
 */
@Composable
fun StreakDetailsPage(
    streak: StreakEntity,
    stats: StreakStats,
    hasMilestones: Boolean,
    bottomPadding: Dp,
    onPickBanner: () -> Unit,
    onRemoveBanner: () -> Unit,
    onPickLogo: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StreakBannerCard(
            bannerUri = streak.bannerUri,
            onPick = onPickBanner,
            onRemove = onRemoveBanner
        )
        StreakIdentityCard(streak = streak, count = stats.current, onLogoClick = onPickLogo)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatTile(
                label = "Times broken",
                value = if (stats.timesBroken == 1) "1 time" else "${stats.timesBroken} times",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Longest streak",
                value = days(stats.longest),
                modifier = Modifier.weight(1f)
            )
        }

        MilestoneCard(stats = stats, hasMilestones = hasMilestones)

        Spacer(modifier = Modifier.height(4.dp))
        TextButton(
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete streak")
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun MilestoneCard(stats: StreakStats, hasMilestones: Boolean) {
    val next = stats.nextMilestone
    val date = stats.nextMilestoneDate
    val left = stats.daysLeft
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Upcoming milestone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (next != null && date != null && left != null) {
                Text(days(next), style = MaterialTheme.typography.titleLarge)
                Text(displayDate(date), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${days(left)} left",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val span = (next - stats.previousMilestone).coerceAtLeast(1)
                val fraction = ((stats.current - stats.previousMilestone).toFloat() / span).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            } else {
                Text(
                    if (hasMilestones) "All milestones reached" else "No milestones set",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
