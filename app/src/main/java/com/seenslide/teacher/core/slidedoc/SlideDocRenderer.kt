package com.seenslide.teacher.core.slidedoc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Render a [SlideDoc] to a Compose Canvas, preserving its aspect ratio within
 * the available constraints. Text, image, and shape elements are drawn in
 * z-order (stable-sorted by element.z).
 *
 * @param imageResolver resolves an image src (URL or local id) to a Bitmap
 *   loaded elsewhere. Pass null for a text/shape-only preview.
 */
@Composable
fun SlideDocRenderer(
    doc: SlideDoc,
    modifier: Modifier = Modifier,
    imageResolver: ((String) -> android.graphics.Bitmap?)? = null,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val slideW: Float
        val slideH: Float
        if (maxW / maxH > doc.aspect) {
            slideH = maxH
            slideW = slideH * doc.aspect
        } else {
            slideW = maxW
            slideH = slideW / doc.aspect
        }

        val sorted = remember(doc) { doc.elements.sortedBy { it.z } }

        Canvas(
            modifier = Modifier
                .width(with(density) { slideW.toDp() })
                .height(with(density) { slideH.toDp() }),
        ) {
            drawBackground(doc.background, Size(slideW, slideH))
            for (el in sorted) {
                drawElement(el, Size(slideW, slideH), imageResolver)
            }
        }
    }
}

private fun DrawScope.drawBackground(
    bg: SlideBackground,
    size: Size,
) {
    when (bg.type) {
        "color" -> drawRect(parseColor(bg.color ?: "#FFFFFF"), size = size)
        "image" -> drawRect(Color.White, size = size)
        else -> drawRect(Color.White, size = size)
    }
}

private fun DrawScope.drawElement(
    el: SlideElement,
    slide: Size,
    imageResolver: ((String) -> android.graphics.Bitmap?)?,
) {
    val topLeft = Offset(el.x * slide.width, el.y * slide.height)
    val boxSize = Size(el.w * slide.width, el.h * slide.height)

    when (el.type) {
        SlideElementType.TEXT -> drawText(el, topLeft, boxSize, slide)
        SlideElementType.IMAGE -> {
            val bmp = el.src?.let { imageResolver?.invoke(it) }
            if (bmp != null) {
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply { isFilterBitmap = true }
                    val dst = android.graphics.RectF(
                        topLeft.x, topLeft.y,
                        topLeft.x + boxSize.width, topLeft.y + boxSize.height,
                    )
                    canvas.nativeCanvas.drawBitmap(bmp, null, dst, paint)
                }
            } else {
                drawRect(
                    Color(0xFFE0E0E0), topLeft = topLeft, size = boxSize,
                    style = Stroke(width = 1f),
                )
            }
        }
        SlideElementType.SHAPE -> drawShape(el, topLeft, boxSize, slide)
    }
}

private fun DrawScope.drawText(
    el: SlideElement,
    topLeft: Offset,
    boxSize: Size,
    slide: Size,
) {
    val text = el.content ?: return
    val color = parseColor(el.color ?: "#000000").toArgb()
    val fontPx = (el.fontSize ?: 0.05f) * slide.height
    val paint = android.graphics.Paint().apply {
        this.color = color
        textSize = fontPx
        isAntiAlias = true
        isFakeBoldText = el.bold
        textSkewX = if (el.italic) -0.25f else 0f
        textAlign = when (el.align) {
            "center" -> android.graphics.Paint.Align.CENTER
            "right" -> android.graphics.Paint.Align.RIGHT
            else -> android.graphics.Paint.Align.LEFT
        }
    }
    val lines = wrapText(text, paint, boxSize.width)
    val lineHeight = fontPx * 1.2f
    val startX = when (paint.textAlign) {
        android.graphics.Paint.Align.CENTER -> topLeft.x + boxSize.width / 2f
        android.graphics.Paint.Align.RIGHT -> topLeft.x + boxSize.width
        else -> topLeft.x
    }
    drawIntoCanvas { canvas ->
        var yCursor = topLeft.y + fontPx
        for (line in lines) {
            if (yCursor > topLeft.y + boxSize.height + fontPx) break
            canvas.nativeCanvas.drawText(line, startX, yCursor, paint)
            yCursor += lineHeight
        }
    }
}

private fun wrapText(
    text: String,
    paint: android.graphics.Paint,
    maxWidth: Float,
): List<String> {
    val out = mutableListOf<String>()
    for (paragraph in text.split("\n")) {
        val words = paragraph.split(" ")
        val line = StringBuilder()
        for (word in words) {
            val trial = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(trial) <= maxWidth || line.isEmpty()) {
                line.clear()
                line.append(trial)
            } else {
                out.add(line.toString())
                line.clear()
                line.append(word)
            }
        }
        if (line.isNotEmpty()) out.add(line.toString())
    }
    return out
}

private fun DrawScope.drawShape(
    el: SlideElement,
    topLeft: Offset,
    boxSize: Size,
    slide: Size,
) {
    val fillColor = el.fill?.let { parseColor(it) }
    val strokeColor = el.stroke?.let { parseColor(it) }
    val strokeW = (el.strokeWidth ?: 0.002f) * slide.width

    when (el.kind) {
        ShapeKind.RECT -> {
            if (fillColor != null) drawRect(fillColor, topLeft, boxSize)
            if (strokeColor != null) drawRect(strokeColor, topLeft, boxSize, style = Stroke(strokeW))
        }
        ShapeKind.CIRCLE -> {
            val center = Offset(topLeft.x + boxSize.width / 2, topLeft.y + boxSize.height / 2)
            val radius = min(boxSize.width, boxSize.height) / 2f
            if (fillColor != null) drawCircle(fillColor, radius, center)
            if (strokeColor != null) drawCircle(strokeColor, radius, center, style = Stroke(strokeW))
        }
        ShapeKind.LINE -> {
            val c = strokeColor ?: fillColor ?: Color.Black
            drawLine(c, topLeft, Offset(topLeft.x + boxSize.width, topLeft.y + boxSize.height), strokeW)
        }
        ShapeKind.ARROW -> {
            val c = strokeColor ?: fillColor ?: Color.Black
            val start = topLeft
            val end = Offset(topLeft.x + boxSize.width, topLeft.y + boxSize.height)
            drawLine(c, start, end, strokeW)
            val headLen = min(boxSize.width, boxSize.height) * 0.25f
            val angle = kotlin.math.atan2(end.y - start.y, end.x - start.x)
            val a1 = angle + Math.PI.toFloat() - 0.4f
            val a2 = angle + Math.PI.toFloat() + 0.4f
            drawLine(c, end, Offset(end.x + headLen * cos(a1), end.y + headLen * sin(a1)), strokeW)
            drawLine(c, end, Offset(end.x + headLen * cos(a2), end.y + headLen * sin(a2)), strokeW)
        }
    }
}

private fun parseColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    val value = when (clean.length) {
        6 -> 0xFF000000 or clean.toLong(16)
        8 -> clean.toLong(16)
        else -> 0xFF000000
    }
    return Color(value)
}
