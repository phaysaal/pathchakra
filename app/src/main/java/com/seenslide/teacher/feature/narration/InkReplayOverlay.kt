package com.seenslide.teacher.feature.narration

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import org.json.JSONArray

/**
 * Lightweight parsed form of a wire-format stroke, for local replay of a
 * slide's ink while its narration audio plays. Only what rendering needs.
 */
data class ReplayStroke(
    val type: String,           // freehand | shape | text
    val tool: String,           // pen | highlighter | eraser
    val color: Long,            // ARGB
    val width: Float,           // normalized-ish px width
    val tStart: Long,
    val tEnd: Long,
    val points: List<Triple<Float, Float, Long>>, // x, y (0-1), t ms
    val shape: String = "",
    val box: FloatArray = FloatArray(4), // startX, startY, endX, endY for shapes
)

fun parseReplayStrokes(json: String): List<ReplayStroke> {
    return try {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val colorHex = o.optString("color", "#000000").removePrefix("#")
                val color = 0xFF000000L or (colorHex.toLongOrNull(16) ?: 0L)
                when (o.optString("type")) {
                    "freehand" -> {
                        val pts = o.optJSONArray("points") ?: JSONArray()
                        add(
                            ReplayStroke(
                                type = "freehand",
                                tool = o.optString("tool", "pen"),
                                color = color,
                                width = o.optDouble("width", 3.0).toFloat(),
                                tStart = o.optLong("t_start"),
                                tEnd = o.optLong("t_end"),
                                points = buildList {
                                    for (j in 0 until pts.length()) {
                                        val p = pts.getJSONObject(j)
                                        add(
                                            Triple(
                                                p.optDouble("x").toFloat(),
                                                p.optDouble("y").toFloat(),
                                                p.optLong("t"),
                                            )
                                        )
                                    }
                                },
                            )
                        )
                    }
                    "shape" -> add(
                        ReplayStroke(
                            type = "shape",
                            tool = "pen",
                            color = color,
                            width = o.optDouble("width", 3.0).toFloat(),
                            tStart = o.optLong("t_start"),
                            tEnd = o.optLong("t_end"),
                            points = emptyList(),
                            shape = o.optString("shape"),
                            box = floatArrayOf(
                                o.optDouble("start_x").toFloat(),
                                o.optDouble("start_y").toFloat(),
                                o.optDouble("end_x").toFloat(),
                                o.optDouble("end_y").toFloat(),
                            ),
                        )
                    )
                    // text intentionally skipped in the local preview (v1)
                }
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Draws a stroke timeline progressively up to [positionMs] — the writing
 * appears exactly as it did while the teacher recorded. Pass Long.MAX_VALUE
 * to show the finished annotation statically.
 */
@Composable
fun InkReplayOverlay(
    strokes: List<ReplayStroke>,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        for (s in strokes) {
            if (s.tStart > positionMs) continue
            val alpha = if (s.tool == "highlighter") 0.45f else 1f
            val color = Color(s.color).copy(alpha = alpha)
            val strokeWidth = s.width * (w / 400f).coerceAtLeast(1f)

            when (s.type) {
                "freehand" -> {
                    val visible = s.points.takeWhile { it.third <= positionMs }
                    if (visible.size < 2) continue
                    val path = Path()
                    path.moveTo(visible[0].first * w, visible[0].second * h)
                    for (k in 1 until visible.size) {
                        path.lineTo(visible[k].first * w, visible[k].second * h)
                    }
                    drawPath(
                        path, color,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                "shape" -> {
                    val (sx, sy, ex, ey) = s.box.let { arrayOf(it[0] * w, it[1] * h, it[2] * w, it[3] * h) }
                    when (s.shape) {
                        "line", "arrow" -> drawLine(color, Offset(sx, sy), Offset(ex, ey), strokeWidth)
                        "circle" -> drawOval(
                            color,
                            topLeft = Offset(minOf(sx, ex), minOf(sy, ey)),
                            size = androidx.compose.ui.geometry.Size(
                                kotlin.math.abs(ex - sx), kotlin.math.abs(ey - sy)
                            ),
                            style = Stroke(strokeWidth),
                        )
                        else -> drawRect(
                            color,
                            topLeft = Offset(minOf(sx, ex), minOf(sy, ey)),
                            size = androidx.compose.ui.geometry.Size(
                                kotlin.math.abs(ex - sx), kotlin.math.abs(ey - sy)
                            ),
                            style = Stroke(strokeWidth),
                        )
                    }
                }
            }
        }
    }
}
