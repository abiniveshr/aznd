package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.GraphPoint
import com.ashrdev.aznd.domain.GraphStyle
import com.ashrdev.aznd.domain.GraphWindow
import com.ashrdev.aznd.domain.SessionStat
import com.ashrdev.aznd.ui.common.StatsGraph
import com.ashrdev.aznd.ui.components.Card

/** The graph at the top of a workout's stats screen. Modes are local to this page. */
@Composable
fun WorkoutGraphSection(viewModel: WorkoutGraphViewModel, modifier: Modifier = Modifier) {
    val sessions by viewModel.sessions.collectAsState()
    val list = sessions ?: return
    if (list.isEmpty()) {
        Card(modifier = modifier.fillMaxWidth()) {
            Text(
                "Graphs appear after you finish a session of this workout.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }
    val modes = remember(list) {
        fun mode(id: String, label: String, unit: String, style: GraphStyle, value: (SessionStat) -> Double) =
            GraphMode(
                id, label, unit, style,
                list.map { s ->
                    GraphPoint(
                        s.finishedAt, value(s),
                        "${s.sets} sets  ·  ${GraphWindow.formatValue(s.durationMin)} min"
                    )
                }
            )
        listOf(
            mode("volume", "Total volume", "kg", GraphStyle.BARS) { it.volumeKg },
            mode("moved", "Weight moved", "kg", GraphStyle.BARS) { it.weightMovedKg },
            mode("kcal", "Calories burnt", "kcal", GraphStyle.BARS) { it.kcal },
            mode("sets", "Sets", "sets", GraphStyle.BARS) { it.sets.toDouble() },
            mode("duration", "Duration", "min", GraphStyle.BARS) { it.durationMin }
        )
    }
    StatsGraph(
        modes = modes,
        modifier = modifier,
        title = "Progress per session",
        footnote = "Total volume = added weight × reps. Weight moved also counts the bodyweight you lift. " +
            "Calories are an estimate. Only sessions logged with the current logger are included."
    )
}
