package com.seenslide.teacher.feature.slide.maker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.seenslide.teacher.core.slidedoc.ShapeKind
import com.seenslide.teacher.core.slidedoc.SlideDoc
import com.seenslide.teacher.core.slidedoc.SlideElement
import com.seenslide.teacher.core.slidedoc.SlideElementType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Rasterize a [SlideDoc] to a native Android canvas. Mirrors the layout logic
 * of the Compose renderer so the saved image matches what the user sees.
 */
object SlideDocRasterizer {

    fun draw(
        canvas: Canvas,
        doc: SlideDoc,
        width: Float,
        height: Float,
        imageResolver: ((String) -> Bitmap?)? = null,
    ) {
        val bg = Paint().apply { color = parseColor(doc.background.color ?: "#FFFFFF") }
        canvas.drawRect(0f, 0f, width, height, bg)

        val sorted = doc.elements.sortedBy { it.z }
        for (el in sorted) {
            val x = el.x * width
            val y = el.y * height
            val w = el.w * width
            val h = el.h * height
            when (el.type) {
                SlideElementType.TEXT -> drawText(canvas, el, x, y, w, h, height)
                SlideElementType.SHAPE -> drawShape(canvas, el, x, y, w, h, width)
                SlideElementType.IMAGE -> {
                    val bmp = el.src?.let { imageResolver?.invoke(it) }
                    if (bmp != null) {
                        val dst = RectF(x, y, x + w, y + h)
                        val paint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
                        canvas.drawBitmap(bmp, null as Rect?, dst, paint)
                    } else {
                        val p = Paint().apply {
                            color = Color.LTGRAY
                            style = Paint.Style.STROKE
                            strokeWidth = 2f
                        }
                        canvas.drawRect(x, y, x + w, y + h, p)
                    }
                }
            }
        }
    }

    private fun drawText(
        canvas: Canvas,
        el: SlideElement,
        x: Float, y: Float, w: Float, h: Float,
        slideHeight: Float,
    ) {
        val text = el.content ?: return
        val fontPx = (el.fontSize ?: 0.05f) * slideHeight
        val paint = Paint().apply {
            color = parseColor(el.color ?: "#000000")
            textSize = fontPx
            isAntiAlias = true
            isFakeBoldText = el.bold
            textSkewX = if (el.italic) -0.25f else 0f
            textAlign = when (el.align) {
                "center" -> Paint.Align.CENTER
                "right" -> Paint.Align.RIGHT
                else -> Paint.Align.LEFT
            }
        }
        val lines = wrap(text, paint, w)
        val lineHeight = fontPx * 1.2f
        val startX = when (paint.textAlign) {
            Paint.Align.CENTER -> x + w / 2f
            Paint.Align.RIGHT -> x + w
            else -> x
        }
        var yCursor = y + fontPx
        for (line in lines) {
            if (yCursor > y + h + fontPx) break
            canvas.drawText(line, startX, yCursor, paint)
            yCursor += lineHeight
        }
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
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

    private fun drawShape(
        canvas: Canvas,
        el: SlideElement,
        x: Float, y: Float, w: Float, h: Float,
        slideWidth: Float,
    ) {
        val fill = el.fill?.let { parseColor(it) }
        val stroke = el.stroke?.let { parseColor(it) }
        val strokeW = (el.strokeWidth ?: 0.002f) * slideWidth
        val rect = RectF(x, y, x + w, y + h)

        when (el.kind) {
            ShapeKind.RECT -> {
                if (fill != null) {
                    canvas.drawRect(rect, Paint().apply { color = fill; isAntiAlias = true })
                }
                if (stroke != null) {
                    canvas.drawRect(rect, Paint().apply {
                        color = stroke
                        style = Paint.Style.STROKE
                        strokeWidth = strokeW
                        isAntiAlias = true
                    })
                }
            }
            ShapeKind.CIRCLE -> {
                val radius = min(w, h) / 2f
                val cx = x + w / 2f
                val cy = y + h / 2f
                if (fill != null) {
                    canvas.drawCircle(cx, cy, radius, Paint().apply {
                        color = fill
                        isAntiAlias = true
                    })
                }
                if (stroke != null) {
                    canvas.drawCircle(cx, cy, radius, Paint().apply {
                        color = stroke
                        style = Paint.Style.STROKE
                        strokeWidth = strokeW
                        isAntiAlias = true
                    })
                }
            }
            ShapeKind.LINE -> {
                val color = stroke ?: fill ?: Color.BLACK
                val paint = Paint().apply {
                    this.color = color
                    strokeWidth = strokeW
                    isAntiAlias = true
                }
                canvas.drawLine(x, y, x + w, y + h, paint)
            }
            ShapeKind.ARROW -> {
                val color = stroke ?: fill ?: Color.BLACK
                val paint = Paint().apply {
                    this.color = color
                    strokeWidth = strokeW
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                }
                val endX = x + w
                val endY = y + h
                canvas.drawLine(x, y, endX, endY, paint)
                val headLen = min(w, h) * 0.25f
                val angle = atan2(endY - y, endX - x)
                val a1 = angle + Math.PI.toFloat() - 0.4f
                val a2 = angle + Math.PI.toFloat() + 0.4f
                val path = Path()
                path.moveTo(endX + headLen * cos(a1), endY + headLen * sin(a1))
                path.lineTo(endX, endY)
                path.lineTo(endX + headLen * cos(a2), endY + headLen * sin(a2))
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun parseColor(hex: String): Int {
        val clean = hex.removePrefix("#")
        return when (clean.length) {
            6 -> (0xFF000000.toInt() or clean.toInt(16))
            8 -> clean.toLong(16).toInt()
            else -> Color.BLACK
        }
    }
}
