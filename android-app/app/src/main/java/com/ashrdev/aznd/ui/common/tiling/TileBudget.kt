package com.ashrdev.aznd.ui.common.tiling

/**
 * The content budget of a card: whether a set of blocks can be shown whole (nothing cut, every block at least at its
 * own least scale) in a room. The engine answers it itself, with the height it asks for at the budget level
 * ([TileZoom.Q_BUDGET]) for the tilings it can offer cheaply (a plain stack, a title over two columns, columns under
 * the card's own tiling), and, only when those say no, for the best tilings of a quick aligner run: a picture-heavy
 * card often fits side by side where it does not fit stacked.
 */
object TileBudget {

    /**
     * [items]: the tiles, each with its least scale ([TileItem.leastScale]); [align]: the same blocks as the aligner
     * sees them; [trees]: the cheap tilings to try first; [room]: the height they may take; [quick]: the aligner's
     * effort for the second chance, 0 for none.
     */
    fun fits(
        items: Map<Int, TileItem>,
        align: List<AlignItem>,
        trees: List<TileNode?>,
        width: Float,
        gap: Float,
        room: Float,
        rules: TileRules,
        quick: Int = TileAligner.QUICK_EFFORT
    ): Boolean {
        val solver = TileSolver(items, gap, rules, byContent = false, strict = true)
        val cheap = trees.filterNotNull()
        if (cheap.isEmpty()) return true
        if (cheap.any { solver.height(it, width, TileZoom.Q_BUDGET) <= room }) return true
        if (quick <= 0 || align.size < 2 || align.size > TileAligner.MAX_BLOCKS) return false
        val found = TileAligner(align, AlignSpace(width, room, gap), rules, quick).solve().sortedBy { it.cost }.take(ALIGNED)
        return found.any { c -> TileTree.relaxed(c.tree)?.let { solver.height(it, width, TileZoom.Q_BUDGET) <= room } == true }
    }

    private const val ALIGNED = 3
}
