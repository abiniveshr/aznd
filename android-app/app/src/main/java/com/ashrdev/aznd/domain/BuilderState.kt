package com.ashrdev.aznd.domain

/**
 * Pure, immutable state of the workout builder: the flat set list plus a counter for temporary
 * ids. Every action returns a NEW state, so it is trivially testable and fits a StateFlow.
 *
 * Temporary ids are NEGATIVE (Room's real ids are positive); Room assigns real ids on
 * TrainingRepository.saveTemplate. Rows with no exercise are dropped by [rowsForSave].
 * Template rows never hold logged values, so changing an exercise never needs to clear them.
 */
data class BuilderState(
    val rows: List<SetRow> = emptyList(),
    val nextTempId: Long = -1L
) {
    val cards: List<SetCard> get() = SetTree.cards(rows)
    val depthById: Map<Long, Int> get() = SetTree.depthById(rows)

    /** Rows to hand to TrainingRepository.saveTemplate. */
    fun rowsForSave(): List<SetRow> = SetTree.dropUnsetRows(rows)

    /** True if at least one row would survive a save. */
    val hasContent: Boolean get() = rows.any { it.exerciseId != null }

    fun addCard(): BuilderState = mutate { SetTree.addEmptyCard(rows, it) }

    /** Card header dropdown. */
    fun pickCardExercise(anyRowIdInCard: Long, exerciseId: Long, clearValues: Boolean = false): BuilderState =
        mutate { SetTree.setCardExercise(rows, anyRowIdInCard, exerciseId, clearValues, it) }

    /** A superset row's own dropdown. */
    fun pickRowExercise(rowId: Long, exerciseId: Long, clearValues: Boolean = false): BuilderState =
        mutate { SetTree.setRowExercise(rows, rowId, exerciseId, clearValues) }

    fun addDrop(rowId: Long): BuilderState = mutate { SetTree.addDrop(rows, rowId, it) }
    fun addSuperset(rowId: Long): BuilderState = mutate { SetTree.addSuperset(rows, rowId, it) }
    fun duplicate(rowId: Long): BuilderState = mutate { SetTree.duplicate(rows, rowId, it) }
    fun delete(rowId: Long): BuilderState = mutate { SetTree.delete(rows, rowId) }

    /** Store a parsed target. DROP rows never get targets. */
    fun setTarget(rowId: Long, min: Int?, max: Int?): BuilderState {
        val row = rows.firstOrNull { it.id == rowId } ?: return this
        if (row.kind == SetKind.DROP) return this
        return copy(rows = rows.map { if (it.id == rowId) it.copy(targetMin = min, targetMax = max) else it })
    }

    /** Parse [text] and store it; Incomplete/Invalid text keeps the stored target. */
    fun setTargetText(rowId: Long, text: String): BuilderState = when (val p = parseTarget(text)) {
        is TargetParse.Valid -> setTarget(rowId, p.min, p.max)
        else -> this
    }

    // ---- logger (Phase 4): the same state also drives the active session ----

    /** Type [text] into a logger field; invalid text keeps the old value, blank clears it. */
    fun setField(rowId: Long, field: LogField, text: String): BuilderState =
        updateRow(rowId) { applyFieldText(it, field, text) }

    /** Tapping a "Suggest ..." hint. */
    fun applySuggestion(rowId: Long, type: ExerciseType, fieldKg: Double): BuilderState =
        updateRow(rowId) { applySuggestionTo(it, type, fieldKg) }

    /** After syncSessionRows: swap temporary ids for the real ones (ids not in [idMap] stay). */
    fun remapIds(idMap: Map<Long, Long>): BuilderState = copy(
        rows = rows.map { it.copy(id = idMap[it.id] ?: it.id, parentId = it.parentId?.let { p -> idMap[p] ?: p }) }
    )

    private inline fun updateRow(rowId: Long, op: (SetRow) -> SetRow?): BuilderState {
        val row = rows.firstOrNull { it.id == rowId } ?: return this
        val updated = op(row) ?: return this
        return copy(rows = rows.map { if (it.id == rowId) updated else it })
    }

    private inline fun mutate(op: (IdSource) -> List<SetRow>): BuilderState {
        val ids = DescendingIds(nextTempId)
        val out = op(ids)
        return BuilderState(out, ids.peek())
    }

    private class DescendingIds(private var n: Long) : IdSource {
        override fun next(): Long = n--
        fun peek(): Long = n
    }

    companion object {
        /** Loaded template; a brand-new (empty) workout starts with one empty card. */
        fun from(saved: List<SetRow>): BuilderState {
            val base = BuilderState(SetTree.ordered(saved))
            return if (saved.isEmpty()) base.addCard() else base
        }
    }
}

/** Row captions inside one card: NORMAL rows count "Set 1", "Set 2"...; DROP = "Drop"; SUPERSET = "Superset". */
fun setLabels(card: SetCard): Map<Long, String> {
    var n = 0
    return card.rows.associate { r ->
        r.id to when (r.kind) {
            SetKind.NORMAL -> "Set ${++n}"
            SetKind.DROP -> "Drop"
            SetKind.SUPERSET -> "Superset"
        }
    }
}
