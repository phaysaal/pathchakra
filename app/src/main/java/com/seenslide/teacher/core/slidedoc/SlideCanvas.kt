package com.seenslide.teacher.core.slidedoc

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * Canonical slide geometry. Every slide the app produces — blank, photo,
 * PDF page, structured maker doc — is normalized to this one aspect so the
 * viewer never has to reflow between differently-shaped slides.
 */
object SlideCanvas {
    const val ASPECT = 4f / 3f
    const val WIDTH = 1920
    const val HEIGHT = 1440 // 1920 / (4/3)

    /**
     * Return [src] fitted (letterboxed on white) into a canonical-aspect
     * bitmap. Never crops or stretches — the whole image stays visible with
     * its own proportions. Returns [src] unchanged if it already matches.
     */
    fun fitToCanonical(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val s = minOf(WIDTH.toFloat() / src.width, HEIGHT.toFloat() / src.height)
        val dw = src.width * s
        val dh = src.height * s
        val left = (WIDTH - dw) / 2f
        val top = (HEIGHT - dh) / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(
            src,
            Rect(0, 0, src.width, src.height),
            RectF(left, top, left + dw, top + dh),
            paint,
        )
        return out
    }
}
