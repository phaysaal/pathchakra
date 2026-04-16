package com.seenslide.teacher.core.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke

/**
 * Renders DrawElements onto a Compose Canvas DrawScope.
 * Uses quadratic curve interpolation matching the web viewer algorithm.
 */
object CanvasRenderer {

    fun DrawScope.renderElements(elements: List<DrawElement>, currentStroke: Stroke?) {
        for (element in elements) {
            renderElement(element)
        }
        // Render in-progress stroke
        currentStroke?.let { renderFreehand(it) }
    }

    private fun DrawScope.renderElement(element: DrawElement) {
        when (element) {
            is DrawElement.FreehandElement -> renderFreehand(element.stroke)
            is DrawElement.ShapeElement -> renderShape(element.shape)
            is DrawElement.TextElement -> renderText(element.textStroke)
        }
    }

    private fun DrawScope.renderFreehand(stroke: Stroke) {
        if (stroke.points.size < 2) return

        val path = Path()
        val w = size.width
        val h = size.height
        val points = stroke.points

        path.moveTo(points[0].x * w, points[0].y * h)

        // Quadratic curve interpolation — same as web viewer
        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val midX = (p0.x + p1.x) / 2f * w
            val midY = (p0.y + p1.y) / 2f * h
            path.quadraticBezierTo(p0.x * w, p0.y * h, midX, midY)
        }
        val last = points.last()
        path.lineTo(last.x * w, last.y * h)

        val alpha = if (stroke.tool == DrawTool.HIGHLIGHTER) 0.4f else 1f
        val widthMultiplier = if (stroke.tool == DrawTool.HIGHLIGHTER) 3f else 1f
        val scaledWidth = stroke.width * (w / 1000f) * widthMultiplier

        drawPath(
            path = path,
            color = Color(stroke.color).copy(alpha = alpha),
            style = ComposeStroke(
                width = scaledWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }

    private fun DrawScope.renderShape(shape: ShapeStroke) {
        val w = size.width
        val h = size.height
        val scaledWidth = shape.width * (w / 1000f)
        val color = Color(shape.color)
        val stroke = ComposeStroke(width = scaledWidth, cap = StrokeCap.Round)

        val x1 = shape.startX * w
        val y1 = shape.startY * h
        val x2 = shape.endX * w
        val y2 = shape.endY * h

        when (shape.tool) {
            DrawTool.LINE -> {
                drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = scaledWidth, cap = StrokeCap.Round)
            }
            DrawTool.ARROW -> {
                drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = scaledWidth, cap = StrokeCap.Round)
                drawArrowHead(color, x1, y1, x2, y2, scaledWidth)
            }
            DrawTool.RECT -> {
                val topLeft = Offset(minOf(x1, x2), minOf(y1, y2))
                val rectSize = Size(kotlin.math.abs(x2 - x1), kotlin.math.abs(y2 - y1))
                drawRect(color, topLeft, rectSize, style = stroke)
            }
            DrawTool.CIRCLE -> {
                val cx = (x1 + x2) / 2f
                val cy = (y1 + y2) / 2f
                val rx = kotlin.math.abs(x2 - x1) / 2f
                val ry = kotlin.math.abs(y2 - y1) / 2f
                drawOval(color, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), style = stroke)
            }
            else -> {}
        }
    }

    private fun DrawScope.drawArrowHead(
        color: Color, x1: Float, y1: Float, x2: Float, y2: Float, strokeWidth: Float,
    ) {
        val angle = kotlin.math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val headLength = strokeWidth * 4f
        val headAngle = Math.toRadians(25.0)

        val ax = x2 - headLength * kotlin.math.cos(angle - headAngle).toFloat()
        val ay = y2 - headLength * kotlin.math.sin(angle - headAngle).toFloat()
        val bx = x2 - headLength * kotlin.math.cos(angle + headAngle).toFloat()
        val by = y2 - headLength * kotlin.math.sin(angle + headAngle).toFloat()

        drawLine(color, Offset(x2, y2), Offset(ax, ay), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(x2, y2), Offset(bx, by), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }

    private fun DrawScope.renderText(textStroke: TextStroke) {
        // Text rendering with drawText requires TextMeasurer — handled in the canvas composable
        // This is a placeholder; actual text is drawn in DrawingCanvas.kt with DrawScope.drawText
    }
}
