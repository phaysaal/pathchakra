package com.seenslide.teacher.core.recording

import com.seenslide.teacher.core.drawing.DrawElement
import com.seenslide.teacher.core.drawing.DrawTool
import com.seenslide.teacher.core.drawing.toHexColor
import com.seenslide.teacher.core.drawing.toWireString
import org.json.JSONArray
import org.json.JSONObject

/**
 * Records all drawing activity with timestamps for later replay.
 * Each stroke/shape/text is recorded with relative time (ms since recording start).
 * The recording can be serialized to JSON for upload and later playback.
 */
class StrokeRecorder {

    private var startTime: Long = 0L
    private var _isRecording = false
    private val recordings = mutableMapOf<Int, SlideRecording>() // slideNumber -> recording
    private var currentSlide: Int = 1
    private var talkId: String = ""

    val isRecording: Boolean get() = _isRecording
    val recordingStartTime: Long get() = startTime

    fun startRecording(talkId: String, slideNumber: Int = 1) {
        this.talkId = talkId
        this.currentSlide = slideNumber
        this.startTime = System.currentTimeMillis()
        this._isRecording = true
        ensureSlideRecording(slideNumber)
    }

    fun stopRecording() {
        _isRecording = false
    }

    fun setCurrentSlide(slideNumber: Int) {
        currentSlide = slideNumber
        if (_isRecording) {
            ensureSlideRecording(slideNumber)
        }
    }

    fun recordElement(element: DrawElement) {
        if (!_isRecording) return
        val recording = ensureSlideRecording(currentSlide)
        recording.elements.add(element)
    }

    private fun ensureSlideRecording(slideNumber: Int): SlideRecording {
        return recordings.getOrPut(slideNumber) {
            SlideRecording(
                slideNumber = slideNumber,
                talkId = talkId,
                recordingStart = startTime,
            )
        }
    }

    /**
     * Export all slide recordings as JSON array.
     */
    fun toJson(): JSONArray {
        val arr = JSONArray()
        for ((_, recording) in recordings) {
            arr.put(recording.toJson())
        }
        return arr
    }

    /**
     * Export a single slide recording as JSON.
     */
    fun getSlideRecordingJson(slideNumber: Int): JSONObject? {
        return recordings[slideNumber]?.toJson()
    }

    fun clear() {
        recordings.clear()
        startTime = 0L
        _isRecording = false
    }
}

private data class SlideRecording(
    val slideNumber: Int,
    val talkId: String,
    val recordingStart: Long,
    val elements: MutableList<DrawElement> = mutableListOf(),
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("talk_id", talkId)
        json.put("slide_number", slideNumber)
        json.put("recording_start", recordingStart / 1000.0)

        val strokesArr = JSONArray()
        for (element in elements) {
            strokesArr.put(elementToJson(element))
        }
        json.put("strokes", strokesArr)
        return json
    }

    private fun elementToJson(element: DrawElement): JSONObject {
        return when (element) {
            is DrawElement.FreehandElement -> {
                val s = element.stroke
                JSONObject().apply {
                    put("type", "freehand")
                    put("tool", s.tool.toWireString())
                    put("color", s.color.toHexColor())
                    put("width", s.width.toDouble())
                    put("t_start", s.points.firstOrNull()?.t ?: 0L)
                    put("t_end", s.points.lastOrNull()?.t ?: 0L)
                    put("points", JSONArray().apply {
                        for (p in s.points) {
                            put(JSONObject().apply {
                                put("x", p.x.toDouble())
                                put("y", p.y.toDouble())
                                put("p", p.pressure.toDouble())
                                put("t", p.t)
                            })
                        }
                    })
                }
            }
            is DrawElement.ShapeElement -> {
                val s = element.shape
                JSONObject().apply {
                    put("type", "shape")
                    put("shape", s.tool.name.lowercase())
                    put("color", s.color.toHexColor())
                    put("width", s.width.toDouble())
                    put("t_start", s.tStart)
                    put("t_end", s.tEnd)
                    put("start_x", s.startX.toDouble())
                    put("start_y", s.startY.toDouble())
                    put("end_x", s.endX.toDouble())
                    put("end_y", s.endY.toDouble())
                }
            }
            is DrawElement.TextElement -> {
                val t = element.textStroke
                JSONObject().apply {
                    put("type", "text")
                    put("text", t.text)
                    put("color", t.color.toHexColor())
                    put("font_size", t.fontSize.toDouble())
                    put("x", t.x.toDouble())
                    put("y", t.y.toDouble())
                    put("t", t.tPlaced)
                }
            }
        }
    }
}
