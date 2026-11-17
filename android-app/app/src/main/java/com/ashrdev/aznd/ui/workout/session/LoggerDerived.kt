package com.ashrdev.aznd.ui.workout.session

import com.ashrdev.aznd.domain.workout.BuilderState
import com.ashrdev.aznd.domain.workout.SetCard
import com.ashrdev.aznd.domain.workout.SetKind
import com.ashrdev.aznd.domain.workout.SetRow

/**
 * What the card list derives from one [BuilderState]. Parts that did not change keep the previous
 * instance (see [deriveLogger]), so a card whose rows are the same can skip recomposition.
 * [previousOf] maps a row id to the row it is compared with in the previous session.
 */
class LoggerDerived(
    val cards: List<SetCard>,
    val depth: Map<Long, Int>,
    val previousOf: Map<Long, SetRow>
)

/** Holds the last result so the next one can reuse what is equal. */
class LoggerDerivedCache {
    var last: LoggerDerived? = null
}

/**
 * Same pairing as previousRowFor: a row meets the row at the same position among this exercise's
 * rows of the same kind (DROP vs not DROP) in [previous], but computed for all rows in one pass.
 */
fun deriveLogger(state: BuilderState, previous: List<SetRow>, last: LoggerDerived?): LoggerDerived {
    val oldByFirst = last?.cards?.associateBy { it.rows.first().id }.orEmpty()
    val merged = state.cards.map { c -> oldByFirst[c.rows.first().id]?.takeIf { it == c } ?: c }
    val oldCards = last?.cards
    val cards = if (oldCards != null && oldCards.size == merged.size && merged.indices.all { merged[it] === oldCards[it] }) {
        oldCards
    } else {
        merged
    }

    val groups = previous.filter { it.exerciseId != null }
        .groupBy { Pair(it.exerciseId, it.kind == SetKind.DROP) }
    val seen = HashMap<Pair<Long?, Boolean>, Int>()
    val map = HashMap<Long, SetRow>()
    for (r in state.rows) {
        val key = Pair(r.exerciseId ?: continue, r.kind == SetKind.DROP)
        val index = seen[key] ?: 0
        seen[key] = index + 1
        groups[key]?.getOrNull(index)?.let { map[r.id] = it }
    }

    val newDepth = state.depthById
    val depth = if (last != null && newDepth == last.depth) last.depth else newDepth
    val previousOf = if (last != null && map == last.previousOf) last.previousOf else map
    return LoggerDerived(cards, depth, previousOf)
}
