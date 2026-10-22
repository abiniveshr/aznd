package com.ashrdev.aznd.ui.workout

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.domain.workout.SetRow

/**
 * How one nested row (drop / superset) is wired into the guide lines of its card.
 *  [isLast]     no later sibling under the same parent: the line ends at this row's elbow.
 *  [continuing] for each ancestor level (outermost first, this row's own level excluded): that
 *               ancestor has later siblings, so its line keeps running past this row.
 */
class ConnectorInfo(val isLast: Boolean, val continuing: List<Boolean>)

/** [rows] = one card's rows, depth-first. Top-level rows have no connector and are not in the map. */
fun connectorInfo(rows: List<SetRow>): Map<Long, ConnectorInfo> {
    val byId = rows.associateBy { it.id }
    val lastIds = HashSet<Long>()
    val seenParents = HashSet<Long>()
    for (r in rows.asReversed()) {
        val p = r.parentId ?: continue
        if (p in byId && seenParents.add(p)) lastIds += r.id
    }
    val result = HashMap<Long, ConnectorInfo>()
    for (r in rows) {
        if (r.parentId == null || r.parentId !in byId) continue
        val chain = ArrayList<SetRow>() // this row, then its nested ancestors
        var cur: SetRow? = r
        while (cur != null && cur.parentId != null && cur.parentId in byId && chain.size < 32) {
            chain += cur
            cur = byId[cur.parentId]
        }
        val levels = chain.asReversed() // level 1 .. depth
        result[r.id] = ConnectorInfo(
            isLast = r.id in lastIds,
            continuing = levels.dropLast(1).map { it.id !in lastIds }
        )
    }
    return result
}

/**
 * Guide lines in the left gutter of a nested row. A row that is not the last of its group keeps
 * the line running down to the next one; the LAST row ends it at its own elbow, so the line stops
 * where the drops stop. [gap] is the spacing between rows, bridged so the line is unbroken.
 */
fun Modifier.treeConnector(color: Color, indent: Dp, info: ConnectorInfo, gap: Dp, elbowY: Dp = 24.dp): Modifier =
    drawBehind {
        val stroke = 2.dp.toPx()
        val gapPx = gap.toPx()
        val elbow = minOf(elbowY.toPx(), size.height / 2f)
        // lines of outer levels that carry on past this row
        info.continuing.forEachIndexed { i, goesOn ->
            if (!goesOn) return@forEachIndexed
            val x = -(indent / 2).toPx() - indent.toPx() * (info.continuing.size - i)
            drawLine(color, Offset(x, -gapPx), Offset(x, size.height + gapPx), strokeWidth = stroke)
        }
        val x = -(indent / 2).toPx()
        val bottom = if (info.isLast) elbow else size.height + gapPx
        drawLine(color, Offset(x, -gapPx), Offset(x, bottom), strokeWidth = stroke)
        drawLine(color, Offset(x, elbow), Offset(0f, elbow), strokeWidth = stroke)
    }
