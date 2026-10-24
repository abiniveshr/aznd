package com.ashrdev.aznd.ui.theme

enum class BorderKind(val label: String) { OUTLINE("Outline"), LINE("Line only") }

enum class TaperShape(val label: String) { FLAT("Flat"), UP("Up"), DOWN("Down"), CENTER("Center") }

enum class TaperRegion(val label: String) { BOTH("Symmetric"), LEFT("Left only"), RIGHT("Right only") }

data class TaperLine(
    val shape: TaperShape = TaperShape.CENTER,
    val region: TaperRegion = TaperRegion.BOTH,
    val from: Float = 1f,
    val to: Float = 0f,
    val start: Float = 0.5f
) {
    fun clean(): TaperLine = copy(
        from = from.coerceIn(0f, 1f),
        to = to.coerceIn(0f, 1f),
        start = start.coerceIn(0f, 1f)
    )

    fun profile(): List<Pair<Float, Float>> {
        val s = start.coerceIn(0f, 1f)
        val a = from.coerceIn(0f, 1f)
        val b = to.coerceIn(0f, 1f)
        if (shape == TaperShape.FLAT || s >= 1f) return listOf(0f to 1f, 1f to 1f)
        return when (region) {
            TaperRegion.BOTH -> {
                val l = 0.5f - s / 2f
                val r = 0.5f + s / 2f
                listOf(0f to b, l to a, l to 1f, r to 1f, r to a, 1f to b)
            }
            TaperRegion.LEFT -> {
                val l = 1f - s
                listOf(0f to b, l to a, l to 1f, 1f to 1f)
            }
            TaperRegion.RIGHT -> listOf(0f to 1f, s to 1f, s to a, 1f to b)
        }
    }

    fun outline(x: Float, y: Float, length: Float, thickness: Float, vertical: Boolean = false): FloatArray {
        val keys = profile()
        val near = ArrayList<Float>(keys.size * 2)
        val far = ArrayList<Float>(keys.size * 2)
        for ((u, f) in keys) {
            val along = u * length
            val t = thickness * f
            val (lo, hi) = when (shape) {
                TaperShape.FLAT -> 0f to thickness
                TaperShape.UP -> 0f to t
                TaperShape.DOWN -> (thickness - t) to thickness
                TaperShape.CENTER -> (thickness - t) / 2f to (thickness + t) / 2f
            }
            if (vertical) {
                near += x + lo; near += y + along
                far += x + hi; far += y + along
            } else {
                near += x + along; near += y + lo
                far += x + along; far += y + hi
            }
        }
        val out = FloatArray(near.size + far.size)
        for (i in near.indices) out[i] = near[i]
        var k = near.size
        var i = far.size - 2
        while (i >= 0) {
            out[k++] = far[i]
            out[k++] = far[i + 1]
            i -= 2
        }
        return out
    }
}
