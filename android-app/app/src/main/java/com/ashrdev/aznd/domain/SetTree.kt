package com.ashrdev.aznd.domain

const val DEFAULT_ROWS_PER_CARD = 3

/** Hands out IDs for rows created in memory before save. */
fun interface IdSource {
    fun next(): Long
}

class SequentialIdSource(start: Long = 1L) : IdSource {
    private var n = start
    override fun next(): Long = n++
}

/**
 * One row of the flat set table, for templates and logged sessions alike (templates leave the
 * value fields null). [position] orders siblings under the same parent.
 */
data class SetRow(
    val id: Long,
    val position: Int = 0,
    val exerciseId: Long? = null,
    val kind: SetKind = SetKind.NORMAL,
    val parentId: Long? = null,
    val targetMin: Int? = null,
    val targetMax: Int? = null,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val rpe: Double? = null
) {
    fun toEntry(): SetEntry = SetEntry(kind, weightKg, reps, durationSec, rpe)
}

/** A card: consecutive top-level rows sharing an exercise, plus all their descendants (depth-first). */
data class SetCard(val exerciseId: Long?, val rows: List<SetRow>)

/**
 * Pure operations on the flat set list. Every function returns a NEW list in depth-first order
 * (parent, then its children) with positions renumbered 0..n-1 among siblings. Unknown ids leave
 * the list unchanged. A row whose parent is missing is treated as top-level.
 */
object SetTree {

    /** Depth-first order: the order to show, and the order to log. */
    fun ordered(rows: List<SetRow>): List<SetRow> = Tree(rows).flatten()

    /** Nesting depth per row id (top-level = 0), for indenting. */
    fun depthById(rows: List<SetRow>): Map<Long, Int> {
        val depth = HashMap<Long, Int>()
        ordered(rows).forEach { r -> depth[r.id] = (r.parentId?.let { depth[it] } ?: -1) + 1 }
        return depth
    }

    /**
     * Group into cards. A row with no exercise yet is always its own card, so two freshly
     * added empty cards never merge.
     */
    fun cards(rows: List<SetRow>): List<SetCard> {
        val t = Tree(rows)
        val flat = t.flatten()
        val tops = t.kids(null).toList()
        val result = ArrayList<SetCard>()
        var i = 0
        while (i < tops.size) {
            val ex = tops[i].exerciseId
            var j = i + 1
            if (ex != null) while (j < tops.size && tops[j].exerciseId == ex) j++
            val topIds = tops.subList(i, j).map { it.id }.toSet()
            result += SetCard(ex, flat.filter { t.topAncestor(it.id)?.id in topIds })
            i = j
        }
        return result
    }

    /** DROP row under the tapped row (under its parent if the tapped row is a DROP), same exercise, empty. */
    fun addDrop(rows: List<SetRow>, tappedId: Long, ids: IdSource): List<SetRow> {
        val t = Tree(rows)
        val tapped = t.byId[tappedId] ?: return t.flatten()
        val parent = if (tapped.kind == SetKind.DROP) t.parentKey(tapped) ?: tapped.id else tapped.id
        t.kids(parent).add(SetRow(ids.next(), exerciseId = tapped.exerciseId, kind = SetKind.DROP, parentId = parent))
        return t.flatten()
    }

    /**
     * SUPERSET child of the tapped row, after its existing children. Its exercise is prefilled
     * from the nearest earlier SUPERSET row at the same nesting depth in this card, else unset.
     */
    fun addSuperset(rows: List<SetRow>, tappedId: Long, ids: IdSource): List<SetRow> {
        val t = Tree(rows)
        val tapped = t.byId[tappedId] ?: return t.flatten()
        val prefill = supersetPrefill(t, tapped)
        t.kids(tapped.id).add(SetRow(ids.next(), exerciseId = prefill, kind = SetKind.SUPERSET, parentId = tapped.id))
        return t.flatten()
    }

    /** Copy of the row and all its descendants, directly after the row's subtree, same parent and kind. */
    fun duplicate(rows: List<SetRow>, id: Long, ids: IdSource): List<SetRow> {
        val t = Tree(rows)
        val src = t.byId[id] ?: return t.flatten()
        val parent = t.parentKey(src)
        val copy = t.copySubtree(src, parent, ids)
        val siblings = t.kids(parent)
        siblings.add(siblings.indexOfFirst { it.id == src.id } + 1, copy)
        return t.flatten()
    }

    /**
     * Remove the row and its DROP children. Its SUPERSET children move up to the deleted row's
     * parent, in its place (NORMAL top-level rows if it had no parent).
     */
    fun delete(rows: List<SetRow>, id: Long): List<SetRow> {
        val t = Tree(rows)
        val target = t.byId[id] ?: return t.flatten()
        val parent = t.parentKey(target)
        val removed = HashSet<Long>()
        removed += id
        val stack = ArrayList<Long>()
        stack += id
        while (stack.isNotEmpty()) {
            val cur = stack.removeAt(stack.size - 1)
            for (c in t.kids(cur)) if (c.kind == SetKind.DROP && removed.add(c.id)) stack += c.id
        }
        val survivors = ArrayList<SetRow>()
        fun collect(nodeId: Long) {
            for (c in t.kids(nodeId)) if (c.id in removed) collect(c.id) else survivors += c
        }
        collect(id)
        val moved = survivors.map {
            it.copy(parentId = parent, kind = if (parent == null) SetKind.NORMAL else it.kind)
        }
        val siblings = t.kids(parent)
        val at = siblings.indexOfFirst { it.id == id }
        siblings.removeAt(at)
        siblings.addAll(at, moved)
        return t.flatten()
    }

    /** "Add exercise": a card with no exercise yet (one placeholder row). */
    fun addEmptyCard(rows: List<SetRow>, ids: IdSource): List<SetRow> {
        val t = Tree(rows)
        t.kids(null).add(SetRow(ids.next()))
        return t.flatten()
    }

    /**
     * Card dropdown: rewrite exerciseId on the card's top-level rows and their DROP children
     * (superset children keep theirs). [clearValues] = the exercise type changed, so logged values
     * on those rows are cleared. Picking for a card with no exercise yet tops it up to 3 NORMAL rows.
     */
    fun setCardExercise(
        rows: List<SetRow>,
        anyRowIdInCard: Long,
        exerciseId: Long,
        clearValues: Boolean,
        ids: IdSource
    ): List<SetRow> {
        val t = Tree(rows)
        val card = t.cardTops(anyRowIdInCard)
        if (card.isEmpty()) return t.flatten()
        val wasUnset = card.all { it.exerciseId == null }
        t.rewriteExercise(card.map { it.id }, exerciseId, clearValues)
        if (wasUnset && card.size < DEFAULT_ROWS_PER_CARD) {
            val tops = t.kids(null)
            val at = tops.indexOfFirst { it.id == card.last().id } + 1
            repeat(DEFAULT_ROWS_PER_CARD - card.size) { i ->
                tops.add(at + i, SetRow(ids.next(), exerciseId = exerciseId))
            }
        }
        return t.flatten()
    }

    /** A superset row's own dropdown: rewrite that row and its DROP children only. */
    fun setRowExercise(rows: List<SetRow>, rowId: Long, exerciseId: Long, clearValues: Boolean): List<SetRow> {
        val t = Tree(rows)
        if (rowId in t.byId) t.rewriteExercise(listOf(rowId), exerciseId, clearValues)
        return t.flatten()
    }

    /** Drop rows with no exercise (on save, and when starting a session); superset children move up. */
    fun dropUnsetRows(rows: List<SetRow>): List<SetRow> {
        var current = ordered(rows)
        while (true) {
            val unset = current.firstOrNull { it.exerciseId == null } ?: return current
            current = delete(current, unset.id)
        }
    }

    private fun supersetPrefill(t: Tree, tapped: SetRow): Long? {
        val flat = t.flatten()
        val depth = HashMap<Long, Int>()
        flat.forEach { r -> depth[r.id] = (r.parentId?.let { depth[it] } ?: -1) + 1 }
        val tappedIndex = flat.indexOfFirst { it.id == tapped.id }
        if (tappedIndex < 0) return null
        val tappedDepth = depth.getValue(tapped.id)
        var insertAt = tappedIndex + 1
        while (insertAt < flat.size && depth.getValue(flat[insertAt].id) > tappedDepth) insertAt++
        val card = t.cardTops(tapped.id)
        val cardStart = flat.indexOfFirst { it.id == card.first().id }
        return (cardStart until insertAt).lastOrNull {
            flat[it].kind == SetKind.SUPERSET && depth.getValue(flat[it].id) == tappedDepth + 1
        }?.let { flat[it].exerciseId }
    }

    /** Mutable children-by-parent view of the flat list. */
    private class Tree(rows: List<SetRow>) {
        val byId = HashMap<Long, SetRow>()
        private val children = HashMap<Long?, MutableList<SetRow>>()

        init {
            rows.forEach { byId[it.id] = it }
            rows.sortedWith(compareBy<SetRow>({ it.position }, { it.id })).forEach { r ->
                children.getOrPut(parentKey(r)) { mutableListOf() }.add(r)
            }
        }

        fun kids(parent: Long?): MutableList<SetRow> = children.getOrPut(parent) { mutableListOf() }

        /** The parent id if that parent exists, otherwise null (top-level). */
        fun parentKey(row: SetRow): Long? {
            val pid = row.parentId
            return if (pid != null && byId.containsKey(pid)) pid else null
        }

        fun flatten(): List<SetRow> {
            val out = ArrayList<SetRow>()
            val seen = HashSet<Long>()
            fun walk(parent: Long?) {
                kids(parent).forEachIndexed { index, r ->
                    if (seen.add(r.id)) {
                        out += r.copy(position = index, parentId = parent)
                        walk(r.id)
                    }
                }
            }
            walk(null)
            return out
        }

        fun topAncestor(id: Long): SetRow? {
            var cur = byId[id] ?: return null
            val seen = HashSet<Long>()
            while (true) {
                val parent = parentKey(cur) ?: return cur
                if (!seen.add(parent)) return cur
                cur = byId.getValue(parent)
            }
        }

        /** The top-level rows of the card containing [id]. */
        fun cardTops(id: Long): List<SetRow> {
            val root = topAncestor(id) ?: return emptyList()
            if (root.exerciseId == null) return listOf(root)
            val tops = kids(null)
            val p = tops.indexOfFirst { it.id == root.id }
            if (p < 0) return emptyList()
            var l = p
            var r = p
            while (l > 0 && tops[l - 1].exerciseId == root.exerciseId) l--
            while (r < tops.size - 1 && tops[r + 1].exerciseId == root.exerciseId) r++
            return tops.subList(l, r + 1).toList()
        }

        fun copySubtree(src: SetRow, newParent: Long?, ids: IdSource): SetRow {
            val copy = src.copy(id = ids.next(), parentId = newParent)
            val sourceKids = kids(src.id).toList()
            kids(copy.id).addAll(sourceKids.map { copySubtree(it, copy.id, ids) })
            return copy
        }

        /** Set the exercise on each root and on its DROP children, optionally clearing logged values. */
        fun rewriteExercise(rootIds: List<Long>, exerciseId: Long, clearValues: Boolean) {
            val affected = HashSet<Long>()
            for (rootId in rootIds) {
                affected += rootId
                kids(rootId).filter { it.kind == SetKind.DROP }.forEach { affected += it.id }
            }
            for (list in children.values) {
                val iter = list.listIterator()
                while (iter.hasNext()) {
                    val r = iter.next()
                    if (r.id !in affected) continue
                    val n = if (clearValues) {
                        r.copy(exerciseId = exerciseId, weightKg = null, reps = null, durationSec = null, rpe = null)
                    } else {
                        r.copy(exerciseId = exerciseId)
                    }
                    iter.set(n)
                    byId[n.id] = n
                }
            }
        }
    }
}
