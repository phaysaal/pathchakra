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
import androidx.compose.ui.graphics.drawscope.withTransform
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
    // When true, two-finger drag/pinch pans and zooms the canvas while a
    // single finger draws — essential for meaningful editing on a phone.
    zoomable: Boolean = false,
    // Draw a faint grid, border and centre cross so pan/zoom is legible on
    // an otherwise featureless blank canvas. Off when there's a background.
    showGuides: Boolean = false,
    onStrokeStarted: ((Stroke) -> Unit)? = null,
    onStrokePointAdded: ((List<StrokePoint>) -> Unit)? = null,
    onStrokeCompleted: ((DrawElement) -> Unit)? = null,
) {
    var currentStroke by remember { mutableStateOf<Stroke?>(null) }
    var shapeStart by remember { mutableStateOf<Offset?>(null) }
    var shapePreview by remember { mutableStateOf<ShapeStroke?>(null) }
    var laserPoint by remember { mutableStateOf<Offset?>(null) }
    // Track version to force recomposition
    var drawVersion by remember { mutableStateOf(0) }
    val textMeasurer = rememberTextMeasurer()

    // View transform (pan/zoom). Identity when not zoomable.
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Batch points for WebSocket
    var pendingPoints by remember { mutableStateOf(listOf<StrokePoint>()) }

    Canvas(
        modifier = modifier
            // Two-finger pan/zoom layer (only when zoomable). Consumes the
            // gesture when 2+ fingers are down so the draw handler bails.
            .pointerInput(zoomable) {
                if (!zoomable) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pressed: List<PointerInputChange>
                    do {
                        val event = awaitPointerEvent()
                        pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            val a = pressed[0]
                            val b = pressed[1]
                            val prevA = a.position - a.positionChange()
                            val prevB = b.position - b.positionChange()
                            val curCentroid = (a.position + b.position) / 2f
                            val prevCentroid = (prevA + prevB) / 2f
                            val curDist = (a.position - b.position).getDistance()
                            val prevDist = (prevA - prevB).getDistance()
                            val zoom = if (prevDist > 0f) curDist / prevDist else 1f
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            val actualZoom = if (scale > 0f) newScale / scale else 1f
                            // Keep the world point under the centroid fixed,
                            // then pan by the centroid's movement.
                            offset = curCentroid - (prevCentroid - offset) * actualZoom
                            scale = newScale
                            offset = clampOffset(offset, scale, size.width.toFloat(), size.height.toFloat())
                            pressed.forEach { it.consume() }
                        }
                    } while (pressed.any())
                }
            }
            .pointerInput(drawingState.currentTool) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    // Map screen → logical canvas coords through the inverse
                    // of the pan/zoom transform.
                    val nx = ((down.position.x - offset.x) / scale) / size.width
                    val ny = ((down.position.y - offset.y) / scale) / size.height
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
                        DrawTool.LASER -> {
                            laserPoint = Offset(nx, ny)
                            drawVersion++
                        }
                        DrawTool.TEXT -> {
                            // Text placement handled separately via dialog
                        }
                    }

                    // Drag
                    do {
                        val event = awaitPointerEvent()
                        // Second finger down → a pan/zoom, not a stroke.
                        // Abandon the in-progress stroke and let the
                        // transform handler take over.
                        if (event.changes.count { it.pressed } >= 2) {
                            currentStroke = null
                            shapeStart = null
                            shapePreview = null
                            break
                        }
                        for (change in event.changes) {
                            if (change.pressed) {
                                change.consume()
                                val mx = ((change.position.x - offset.x) / scale) / size.width
                                val my = ((change.position.y - offset.y) / scale) / size.height
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
                                    DrawTool.LASER -> {
                                        laserPoint = Offset(mx, my)
                                        drawVersion++
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
                        DrawTool.LASER -> {
                            laserPoint = null
                            drawVersion++
                        }
                        else -> {}
                    }
                }
            },
    ) {
        // Depend on BOTH the local gesture counter and the shared
        // DrawingState.revision, so undo/redo/clear from the toolbar
        // redraw immediately (they don't touch drawVersion).
        drawVersion.let { _ ->
            drawingState.revision.let { _ ->
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                // Reference guides for a blank canvas: border, thirds grid,
                // and centre cross. Drawn inside the transform so they pan
                // and zoom with the content — that's what makes the motion
                // legible on plain white.
                if (showGuides) {
                    val grid = Color(0x11000000)
                    val edge = Color(0x33000000)
                    val cross = Color(0x22000000)
                    val sw = 1f * scale.coerceAtLeast(1f)
                    // thirds
                    for (k in 1..2) {
                        val gx = size.width * k / 3f
                        val gy = size.height * k / 3f
                        drawLine(grid, Offset(gx, 0f), Offset(gx, size.height), sw)
                        drawLine(grid, Offset(0f, gy), Offset(size.width, gy), sw)
                    }
                    // centre cross
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val armX = size.width * 0.04f
                    val armY = size.height * 0.04f
                    drawLine(cross, Offset(cx - armX, cy), Offset(cx + armX, cy), sw)
                    drawLine(cross, Offset(cx, cy - armY), Offset(cx, cy + armY), sw)
                    // border
                    drawRect(
                        color = edge,
                        topLeft = Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(size.width, size.height),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f * scale.coerceAtLeast(1f)),
                    )
                }

                // Background: fit inside the canvas preserving the image's
                // own aspect (letterboxed), NOT stretched — so a photo/PDF
                // page isn't distorted while the slide stays a fixed aspect.
                backgroundBitmap?.let { bmp ->
                    val img = bmp.asImageBitmap()
                    val s = minOf(size.width / bmp.width, size.height / bmp.height)
                    val dw = bmp.width * s
                    val dh = bmp.height * s
                    drawImage(
                        image = img,
                        dstOffset = androidx.compose.ui.unit.IntOffset(
                            ((size.width - dw) / 2f).toInt(),
                            ((size.height - dh) / 2f).toInt(),
                        ),
                        dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt()),
                    )
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

                laserPoint?.let { point ->
                    drawCircle(
                        color = Color(0xFFE53935),
                        radius = size.minDimension * 0.018f,
                        center = Offset(point.x * size.width, point.y * size.height),
                    )
                    drawCircle(
                        color = Color(0x66FF5252),
                        radius = size.minDimension * 0.035f,
                        center = Offset(point.x * size.width, point.y * size.height),
                    )
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
    }
}

/** Clamp pan so the scaled content never leaves a gap in the viewport. */
private fun clampOffset(o: Offset, scale: Float, w: Float, h: Float): Offset {
    val minX = w * (1f - scale)
    val minY = h * (1f - scale)
    return Offset(
        o.x.coerceIn(minX, 0f),
        o.y.coerceIn(minY, 0f),
    )
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
