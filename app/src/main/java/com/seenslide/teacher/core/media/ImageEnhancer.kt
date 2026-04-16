package com.seenslide.teacher.core.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhances photos of book pages / whiteboards for better slide readability.
 * Increases contrast and brightness so text is crisp on projector/screen.
 */
@Singleton
class ImageEnhancer @Inject constructor() {

    fun enhance(source: Bitmap, contrast: Float = 1.4f, brightness: Float = 20f): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Contrast: scale around 128 midpoint; Brightness: offset
        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness - 128f * (contrast - 1f),
                0f, contrast, 0f, 0f, brightness - 128f * (contrast - 1f),
                0f, 0f, contrast, 0f, brightness - 128f * (contrast - 1f),
                0f, 0f, 0f, 1f, 0f,
            )
        )

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    fun compressToBytes(bitmap: Bitmap, quality: Int = 80, maxWidth: Int = 1920): ByteArray {
        val scaled = if (bitmap.width > maxWidth) {
            val ratio = maxWidth.toFloat() / bitmap.width
            Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * ratio).toInt(), true)
        } else {
            bitmap
        }

        val stream = java.io.ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        if (scaled !== bitmap) scaled.recycle()
        return stream.toByteArray()
    }
}
