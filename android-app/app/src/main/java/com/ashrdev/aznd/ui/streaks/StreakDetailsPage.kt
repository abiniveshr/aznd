package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Upcoming milestone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (next != null && date != null) {
                Text(days(next), style = MaterialTheme.typography.titleLarge)
                Text(displayDate(date), style = MaterialTheme.typography.bodyMedium)
                MilestoneProgressBar(
                    previous = stats.previousMilestone,
                    next = next,
                    current = stats.current,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Text(
                    if (hasMilestones) "All milestones reached" else "No milestones set",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

private const val BAR_PARTS = 10

/**
 * The stretch from the previous milestone to the next, cut into [BAR_PARTS] equal parts. The day
 * count at each boundary sits above the bar (first = previous milestone, last = next milestone);
 * a solid circle marks today's position with "current/next" underneath.
 */
@Composable
private fun MilestoneProgressBar(
    previous: Int,
    next: Int,
    current: Int,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val divider = MaterialTheme.colorScheme.surface
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val markerStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    val span = (next - previous).coerceAtLeast(1)
    val fraction = ((current - previous).toFloat() / span).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val inset = 0.dp.toPx()
        val barLeft = inset
        val barRight = size.width - inset - 1
        val barW = barRight - barLeft
        val barTop = 20.dp.toPx()
        val barH = 8.dp.toPx()
        val barBottom = barTop + barH
        val corner = 4.dp.toPx()

        // Day count at every other boundary, above the bar.
        for (k in 0..BAR_PARTS step 2) {
                val x = barLeft + barW * k / BAR_PARTS
                val value = Math.round(previous + span * k / BAR_PARTS.toDouble()).toInt()
                val layout = measurer.measure(value.toString(), labelStyle)
                val labelX = (x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(labelX, barTop - 4.dp.toPx() - layout.size.height))
        }

        // The bar: track, progress, and the dividers between the parts.
        val shape = Path().apply {
            addRoundRect(RoundRect(barLeft, barTop, barRight, barBottom, corner, corner))
        }
        clipPath(shape) {
            drawRect(track, Offset(barLeft, barTop), Size(barW, barH))
            drawRect(primary, Offset(barLeft, barTop), Size(barW * fraction, barH))
            for (k in 1 until BAR_PARTS) {
                val x = barLeft + barW * k / BAR_PARTS
                drawLine(divider, Offset(x, barTop), Offset(x, barBottom), strokeWidth = 1.5.dp.toPx())
            }
        }
        val progressX = barLeft + barW * fraction
            for (k in 1 until BAR_PARTS + 1) {
            val x = barLeft + barW * k / BAR_PARTS
            // gap-coloured inside the filled part, bar-coloured in the empty part
            val markColor = if (x <= progressX) divider else primary
            drawLine(markColor, Offset(x, barTop), Offset(x, barBottom), strokeWidth = 1.5.dp.toPx())
        }
        // Today: a solid circle on the bar, current/next below it.
        val markerX = barLeft + barW * fraction
        val markerY = barTop + barH / 2f
        drawCircle(divider, radius = 9.dp.toPx(), center = Offset(markerX, markerY))
        drawCircle(primary, radius = 6.5.dp.toPx(), center = Offset(markerX, markerY))
        val markerLayout = measurer.measure("$current/$next", markerStyle)
        val markerTextX = (markerX - markerLayout.size.width / 2f)
            .coerceIn(0f, size.width - markerLayout.size.width)
        drawText(markerLayout, topLeft = Offset(markerTextX, markerY + 12.dp.toPx()))
    }
}
