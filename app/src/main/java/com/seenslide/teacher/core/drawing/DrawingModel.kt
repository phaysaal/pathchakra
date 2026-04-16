package com.seenslide.teacher.core.drawing

import androidx.compose.ui.graphics.Color

/**
 * All coordinates are normalized 0-1 (resolution independent).
 * Multiply by canvas dimensions to render.
 * Compatible with SeenSlide web viewer annotation format.
 */

enum class DrawTool {
    PEN, HIGHLIGHTER, ERASER,
    RECT, CIRCLE, LINE, ARROW,
    TEXT,
}

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val t: Long = 0L, // ms since recording start (for replay)
)

data class Stroke(
    val id: String = java.util.UUID.randomUUID().toString(),
    val tool: DrawTool = DrawTool.PEN,
    val color: Long = 0xFF000000, // ARGB packed
    val width: Float = 3f,
    val points: List<StrokePoint> = emptyList(),
    val isDeleted: Boolean = false,
)

data class ShapeStroke(
    val id: String = java.util.UUID.randomUUID().toString(),
    val tool: DrawTool,
    val color: Long,
    val width: Float,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val tStart: Long = 0L,
    val tEnd: Long = 0L,
)

data class TextStroke(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val x: Float,
    val y: Float,
    val color: Long,
    val fontSize: Float = 0.04f, // normalized — 4% of canvas height
    val tPlaced: Long = 0L,
)

/**
 * Union of all drawable elements on the canvas.
 */
sealed class DrawElement {
    abstract val id: String
    abstract val timestamp: Long // ms since recording start

    data class FreehandElement(val stroke: Stroke) : DrawElement() {
        override val id get() = stroke.id
        override val timestamp get() = stroke.points.firstOrNull()?.t ?: 0L
    }
    data class ShapeElement(val shape: ShapeStroke) : DrawElement() {
        override val id get() = shape.id
        override val timestamp get() = shape.tStart
    }
    data class TextElement(val textStroke: TextStroke) : DrawElement() {
        override val id get() = textStroke.id
        override val timestamp get() = textStroke.tPlaced
    }
}

// Conversion helpers for WebSocket protocol compatibility
fun DrawTool.toWireString(): String = when (this) {
    DrawTool.PEN -> "pen"
    DrawTool.HIGHLIGHTER -> "highlighter"
    DrawTool.ERASER -> "eraser"
    else -> "pen"
}

fun Long.toHexColor(): String {
    return "#%06x".format(this and 0xFFFFFF)
}

fun String.fromHexColor(): Long {
    val hex = this.removePrefix("#")
    return (0xFF000000 or hex.toLong(16))
}
