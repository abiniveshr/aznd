package com.ashrdev.aznd.ui.common.tiling

/**
 * Which blocks a crowded fixed-size card leaves off when its content does not all fit.
 *
 * The old rule hid the last block in reading order, again and again, until nothing was clipped. That throws away
 * a closing paragraph and a photo alike, and sometimes the last block is not what is in the way. [choose] gives
 * blocks up in the order people miss them least: text first, then statistics, then titles, drawables last, and
 * within a kind the later in reading order first. A title that opens the card (the banner) is the last title to go.
 * A drawable (calendar, graph, snapshot, photo) is only hidden when no other block's removal lowers the clipped count.
 *
 * The caller lays the card out; [choose] only sees how many blocks are clipped for a list of shown blocks.
 * That is the expensive part, so it is asked at most [choose]'s `maxTries` times while searching, and never twice
 * for the same list.
 *
 * Guarantee, as a ranking of results: fewer clipped blocks first, then fewer drawables hidden, then fewer blocks hidden.
 * The result never ranks below what [trailing] gives: never more blocks clipped; at equal clipping never more
 * drawables hidden; at equal clipping and drawables never more blocks hidden. A greedy search can be unlucky, so
 * unless the result provably cannot be beaten (nothing hidden because no single block helps; nothing clipped after
 * hiding one non-drawable; the old rule's first step is known not to help) the old rule is run as well, its layouts
 * mostly already known from the search, and the better of the two is returned, the search winning exact ties.
 * Sparing a drawable can therefore cost more hidden blocks than the old rule. When the search runs out of tries,
 * the old rule's result is returned as it is.
 */
object TileHide {

    /** The old rule: hide the last block while that strictly lowers the clipped count. Returns the ids hidden, in order. */
    fun trailing(order: List<Int>, cutsOf: (List<Int>) -> Int): List<Int> {
        var shown = order
        if (shown.size < 2) return emptyList()
        var cuts = cutsOf(shown)
        val hide = ArrayList<Int>()
        while (cuts > 0 && shown.size > 1) {
            val fewer = shown.dropLast(1)
            val next = cutsOf(fewer)
            if (next >= cuts) break
            hide += shown.last()
            shown = fewer
            cuts = next
        }
        return hide
    }

    /**
     * The ids to hide, in the order chosen. [order] is the shown blocks in reading order, [kindOf] says what each is,
     * and [cutsOf] gives the number of clipped blocks when exactly the given sub-list of [order] is laid out.
     * Empty when nothing is clipped or hiding does not help. At most [maxTries] layouts are asked for while searching;
     * the old rule's comparison run may ask for more (at most one per block). The result ranks as described on [TileHide].
     */
    fun choose(order: List<Int>, kindOf: (Int) -> TileKind, cutsOf: (List<Int>) -> Int, maxTries: Int = 16): List<Int> {
        if (order.size < 2) return emptyList()
        val memo = HashMap<List<Int>, Int>()
        var asked = 0
        fun cuts(l: List<Int>): Int = memo.getOrPut(l) { cutsOf(l) }
        fun known(l: List<Int>) = memo.containsKey(l)
        val known0 = cuts(order)
        asked++
        if (known0 == 0) return emptyList()

        var shown = order
        var cur = known0
        val hide = ArrayList<Int>()
        var exhausted = false
        while (cur > 0 && shown.size > 1 && !exhausted) {
            var bestCuts = cur
            var bestId = -1
            var bestShown: List<Int>? = null
            for (group in 0..1) {
                val pool = ranked(shown, kindOf).filter { (kindOf(it) == TileKind.DRAWABLE) == (group == 1) }
                for (id in pool) {
                    val fewer = shown.filter { it != id }
                    if (!known(fewer)) {
                        if (asked >= maxTries) { exhausted = true; break }
                        asked++
                    }
                    val n = cuts(fewer)
                    if (n < bestCuts) {
                        bestCuts = n
                        bestId = id
                        bestShown = fewer
                        if (n == 0) break
                    }
                }
                if (exhausted || bestShown != null) break
            }
            if (exhausted || bestShown == null) break
            hide += bestId
            shown = bestShown
            cur = bestCuts
        }
        if (exhausted) return trailing(order, ::cuts)
        if (hide.isEmpty()) return hide
        fun drawables(l: List<Int>) = l.count { kindOf(it) == TileKind.DRAWABLE }
        if (cur == 0 && hide.size == 1 && drawables(hide) == 0) return hide
        val firstStep = memo[order.dropLast(1)]
        if (firstStep != null && firstStep >= known0) return hide
        val old = trailing(order, ::cuts)
        val oldCuts = cuts(order.filter { it !in old })
        val oldWins = when {
            oldCuts != cur -> oldCuts < cur
            drawables(old) != drawables(hide) -> drawables(old) < drawables(hide)
            else -> old.size < hide.size
        }
        return if (oldWins) old else hide
    }

    /** The shown blocks, the one given up first first: by kind, then the later in reading order first. */
    private fun ranked(shown: List<Int>, kindOf: (Int) -> TileKind): List<Int> =
        shown.indices.sortedWith(
            compareBy<Int> { rank(kindOf(shown[it])) }
                .thenBy { if (it == 0 && kindOf(shown[it]) == TileKind.TITLE) 1 else 0 }
                .thenByDescending { it }
        ).map { shown[it] }

    private fun rank(kind: TileKind): Int = when (kind) {
        TileKind.TEXT -> 0
        TileKind.STATISTIC -> 1
        TileKind.TITLE -> 2
        TileKind.DRAWABLE -> 3
    }
}
