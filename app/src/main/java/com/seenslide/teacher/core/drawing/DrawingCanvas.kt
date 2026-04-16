package com.seenslide.teacher.core.drawing

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

@Composable
fun DrawingCanvas(
    modifier: Modifier = Modifier,
    drawingState: DrawingState,
    backgroundBitmap: Bitmap? = null,
    recordingStartTime: Long = 0L,
    onStrokeStarted: ((Stroke) -> Unit)? = null,
    onStrokePointAdded: ((List<StrokePoint>) -> Unit)? = null,
    onStrokeCompleted: ((DrawElement) -> Unit)? = null,
) {
    var currentStroke by remember { mutableStateOf<Stroke?>(null) }
    var shapeStart by remember { mutableStateOf<Offset?>(null) }
    var shapePreview by remember { mutableStateOf<ShapeStroke?>(null) }
    // Track version to force recomposition
    var drawVersion by remember { mutableStateOf(0) }
    val textMeasurer = rememberTextMeasurer()

    // Batch points for WebSocket
    var pendingPoints by remember { mutableStateOf(listOf<StrokePoint>()) }

    Canvas(
        modifier = modifier
            .pointerInput(drawingState.currentTool) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    val nx = down.position.x / size.width
                    val ny = down.position.y / size.height
                    val pressure = down.pressure.coerceIn(0f, 1f)
                    val now = if (recordingStartTime > 0) System.currentTimeMillis() - recordingStartTime else 0L

                    when (drawingState.currentTool) {
                        DrawTool.PEN, DrawTool.HIGHLIGHTER -> {
                            val point = StrokePoint(nx, ny, pressure, now)
                            val stroke = Stroke(
                                tool = drawingState.currentTool,
                                color = drawingState.currentColor,
                                width = drawingState.currentWidth,
                                points = listOf(point),
                            )
                            currentStroke = stroke
                            pendingPoints = listOf(point)
                            onStrokeStarted?.invoke(stroke)
                        }
                        DrawTool.ERASER -> {
                            drawingState.eraseAt(nx, ny)
                            drawVersion++
                        }
                        DrawTool.RECT, DrawTool.CIRCLE, DrawTool.LINE, DrawTool.ARROW -> {
                            shapeStart = Offset(nx, ny)
                        }
                        DrawTool.TEXT -> {
                            // Text placement handled separately via dialog
                        }
                    }

                    // Drag
                    do {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            if (change.pressed) {
                                change.consume()
                                val mx = change.position.x / size.width
                                val my = change.position.y / size.height
                                val mp = change.pressure.coerceIn(0f, 1f)
                                val mt = if (recordingStartTime > 0) System.currentTimeMillis() - recordingStartTime else 0L

                                when (drawingState.currentTool) {
                                    DrawTool.PEN, DrawTool.HIGHLIGHTER -> {
                                        val point = StrokePoint(mx, my, mp, mt)
                                        currentStroke = currentStroke?.copy(
                                            points = currentStroke!!.points + point,
                                        )
                                        pendingPoints = pendingPoints + point

                                        // Flush batch every ~8 points (~100ms at 60fps)
                                        if (pendingPoints.size >= 8) {
                                            onStrokePointAdded?.invoke(pendingPoints)
                                            pendingPoints = emptyList()
                                        }
                                        drawVersion++
                                    }
                                    DrawTool.ERASER -> {
                                        drawingState.eraseAt(mx, my)
                                        drawVersion++
                                    }
                                    DrawTool.RECT, DrawTool.CIRCLE, DrawTool.LINE, DrawTool.ARROW -> {
                                        shapeStart?.let { start ->
                                            shapePreview = ShapeStroke(
                                                tool = drawingState.currentTool,
                                                color = drawingState.currentColor,
                                                width = drawingState.currentWidth,
                                                startX = start.x, startY = start.y,
                                                endX = mx, endY = my,
                                                tStart = now, tEnd = mt,
                                            )
                                            drawVersion++
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // Pointer up — finalize
                    when (drawingState.currentTool) {
                        DrawTool.PEN, DrawTool.HIGHLIGHTER -> {
                            currentStroke?.let { stroke ->
                                // Flush remaining points
                                if (pendingPoints.isNotEmpty()) {
                                    onStrokePointAdded?.invoke(pendingPoints)
                                    pendingPoints = emptyList()
                                }
                                val element = DrawElement.FreehandElement(stroke)
                                drawingState.addElement(element)
                                onStrokeCompleted?.invoke(element)
                            }
                            currentStroke = null
                            drawVersion++
                        }
                        DrawTool.RECT, DrawTool.CIRCLE, DrawTool.LINE, DrawTool.ARROW -> {
                            shapePreview?.let { shape ->
                                val element = DrawElement.ShapeElement(shape)
                                drawingState.addElement(element)
                                onStrokeCompleted?.invoke(element)
                            }
                            shapeStart = null
                            shapePreview = null
                            drawVersion++
                        }
                        else -> {}
                    }
                }
            },
    ) {
        // Force dependency on drawVersion
        drawVersion.let { _ ->
            // Draw background
            backgroundBitmap?.let { bmp ->
                drawImage(bmp.asImageBitmap())
            }

            // Draw all elements
            with(CanvasRenderer) {
                renderElements(drawingState.elements, currentStroke)
            }

            // Shape preview while dragging
            shapePreview?.let { shape ->
                with(CanvasRenderer) {
                    renderElements(listOf(DrawElement.ShapeElement(shape)), null)
                }
            }

            // Render text elements with TextMeasurer
            for (element in drawingState.elements) {
                if (element is DrawElement.TextElement) {
                    renderTextElement(element.textStroke, textMeasurer)
                }
            }
        }
    }
}

private fun DrawScope.renderTextElement(textStroke: TextStroke, textMeasurer: TextMeasurer) {
    val fontSize = (textStroke.fontSize * size.height).sp
    val style = TextStyle(
        color = Color(textStroke.color),
        fontSize = fontSize,
    )
    val layoutResult = textMeasurer.measure(textStroke.text, style)
    drawText(
        textLayoutResult = layoutResult,
        topLeft = Offset(textStroke.x * size.width, textStroke.y * size.height),
    )
}
