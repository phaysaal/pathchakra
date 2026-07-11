package com.seenslide.teacher.core.slidedoc

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

/**
 * Structured slide document. Stored in cloud_slides.metadata under the "doc" key.
 *
 * Coordinate system: all positions and sizes are fractions of the slide box
 * (0-1), rendered identically at any resolution. The slide has aspect ratio
 * [aspect]; the renderer fits the box to available canvas preserving aspect.
 */
@JsonClass(generateAdapter = true)
data class SlideDoc(
    val version: Int = 1,
    // Canonical slide aspect — matches the editor and camera output so
    // every slide kind shares one shape (see SlideCanvas.ASPECT).
    val aspect: Float = 4f / 3f,
    val background: SlideBackground = SlideBackground(),
    val elements: List<SlideElement> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SlideBackground(
    val type: String = "color",
    val color: String? = "#FFFFFF",
    val src: String? = null,
    val fit: String? = null,
)

/**
 * Flat element model with [type] discriminator. Each element type uses a
 * subset of fields — fields not relevant to a given type are null/default.
 *
 * Types:
 *   - "text":  content, fontSize, color, bold, italic, align
 *   - "image": src, fit
 *   - "shape": kind ("rect"|"circle"|"line"|"arrow"), fill, stroke, strokeWidth
 */
@JsonClass(generateAdapter = true)
data class SlideElement(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val rotation: Float = 0f,
    val z: Int = 0,

    val content: String? = null,
    val fontSize: Float? = null,
    val color: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val align: String? = null,

    val src: String? = null,
    val fit: String? = null,

    val kind: String? = null,
    val fill: String? = null,
    val stroke: String? = null,
    val strokeWidth: Float? = null,
)

object SlideElementType {
    const val TEXT = "text"
    const val IMAGE = "image"
    const val SHAPE = "shape"
}

object ShapeKind {
    const val RECT = "rect"
    const val CIRCLE = "circle"
    const val LINE = "line"
    const val ARROW = "arrow"
}
