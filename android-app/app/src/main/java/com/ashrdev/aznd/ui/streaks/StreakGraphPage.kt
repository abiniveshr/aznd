package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.GraphPoint
import com.ashrdev.aznd.domain.GraphStyle
import com.ashrdev.aznd.domain.PointTint
import com.ashrdev.aznd.domain.streaks.StreakStats
import com.ashrdev.aznd.domain.streaks.parseKey
import com.ashrdev.aznd.domain.streaks.shortDate
import com.ashrdev.aznd.ui.common.StatsGraph

private fun dayText(n: Int) = "$n day${if (n == 1) "" else "s"}"

/** Left page of the streak screen: the app's shared graph module fed with this streak's history. */
@Composable
fun StreakGraphPage(stats: StreakStats, milestones: List<Int>, bottomPadding: Dp) {
    val modes = remember(stats, milestones) { streakGraphModes(stats, milestones) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatsGraph(
            modes = modes,
            title = "Streak",
            footnote = "The line climbs a day at a time and drops to zero on a slip. " +
                "Brighter points are milestones, darker ones are slips."
        )
    }
}

/**
 *  - Progress: the streak length for every day, rising by one and falling to 0 on a slip day. A slip
 *    adds two points on the same day (the last value, then 0) so the drop is a vertical line.
 *  - Runs: one bar per run, placed on the run's last day (the current run on today).
 */
fun streakGraphModes(stats: StreakStats, milestones: List<Int>): List<GraphMode> {
    val milestoneSet = milestones.toSet()
    val times = stats.days.map { parseKey(it).timeInMillis }

    val progress = ArrayList<GraphPoint>()
    for (i in stats.days.indices) {
        val count = stats.counts[i]
        val t = times[i]
        if (count == 0) {
            val before = if (i > 0) stats.counts[i - 1] else 0
            if (before > 0) {
                progress.add(GraphPoint(t, before.toDouble(), detail = "Streak ended", valueText = dayText(before)))
            }
            progress.add(
                GraphPoint(t, 0.0, detail = "Slip: streak reset", valueText = dayText(0), tint = PointTint.BELOW)
            )
        } else if (count in milestoneSet) {
            progress.add(
                GraphPoint(t, count.toDouble(), detail = "Milestone reached", valueText = dayText(count), tint = PointTint.ABOVE)
            )
        } else {
            progress.add(GraphPoint(t, count.toDouble(), valueText = dayText(count)))
        }
    }

    val runs = ArrayList<GraphPoint>()
    for (i in stats.days.indices) {
        val count = stats.counts[i]
        val isLast = i == stats.days.lastIndex
        if (count > 0 && (isLast || stats.counts[i + 1] == 0)) {
            val start = stats.days[i - count + 1]
            runs.add(
                GraphPoint(
                    timeMs = times[i],
                    value = count.toDouble(),
                    detail = "${shortDate(start)} to ${shortDate(stats.days[i])}" + if (isLast) " (current)" else "",
                    valueText = dayText(count),
                    tint = if (isLast) PointTint.ABOVE else PointTint.NORMAL
                )
            )
        }
    }

    return listOf(
        GraphMode(id = "progress", label = "Progress", unit = "days", style = GraphStyle.LINE, points = progress),
        GraphMode(id = "runs", label = "Runs", unit = "days", style = GraphStyle.BARS, points = runs)
    )
}
