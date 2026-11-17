package com.ashrdev.aznd.ui.calendar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath

/**
 * Paints an [ImageVector] straight into a draw, in one tint colour (what Icon does with its tint). A draw cannot
 * make a VectorPainter, which the calendar's badges would need, because their icons are only known while drawing.
 * The converted paths are kept, so each icon is parsed once.
 */
internal class VectorBadgePainter {
    private val paths = java.util.IdentityHashMap<Any, Path>()

    fun draw(scope: DrawScope, icon: ImageVector, tint: Color, left: Float, top: Float, size: Float) {
        val fit = minOf(size / icon.viewportWidth, size / icon.viewportHeight)
        val dx = left + (size - icon.viewportWidth * fit) / 2f
        val dy = top + (size - icon.viewportHeight * fit) / 2f
        with(scope) {
            withTransform({
                translate(dx, dy)
                scale(fit, fit, pivot = Offset.Zero)
            }) {
                drawGroup(icon.root, tint)
            }
        }
    }

    private fun DrawScope.drawGroup(group: VectorGroup, tint: Color) {
        val pivot = Offset(group.pivotX, group.pivotY)
        withTransform({
            translate(group.translationX, group.translationY)
            rotate(group.rotation, pivot)
            scale(group.scaleX, group.scaleY, pivot)
        }) {
            if (group.clipPathData.isNotEmpty()) {
                val clip = paths.getOrPut(group) { PathParser().addPathNodes(group.clipPathData).toPath() }
                clipPath(clip) { drawNodes(group, tint) }
            } else {
                drawNodes(group, tint)
            }
        }
    }

    private fun DrawScope.drawNodes(group: VectorGroup, tint: Color) {
        for (node: VectorNode in group) {
            when (node) {
                is VectorGroup -> drawGroup(node, tint)
                is VectorPath -> drawPathNode(node, tint)
                else -> Unit
            }
        }
    }

    private fun DrawScope.drawPathNode(node: VectorPath, tint: Color) {
        val path = paths.getOrPut(node) {
            PathParser().addPathNodes(node.pathData).toPath().also { it.fillType = node.pathFillType }
        }
        if (node.fill != null) {
            drawPath(path, tint, alpha = node.fillAlpha, style = Fill)
        }
        if (node.stroke != null && node.strokeLineWidth > 0f) {
            drawPath(
                path,
                tint,
                alpha = node.strokeAlpha,
                style = Stroke(
                    width = node.strokeLineWidth,
                    miter = node.strokeLineMiter,
                    cap = node.strokeLineCap,
                    join = node.strokeLineJoin
                )
            )
        }
    }
}
