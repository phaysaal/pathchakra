package com.seenslide.teacher.feature.narration

import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.BuildConfig
import com.seenslide.teacher.core.data.SlideRepository
import com.seenslide.teacher.core.drawing.DrawElement
import com.seenslide.teacher.core.drawing.DrawingState
import com.seenslide.teacher.core.media.AacEncoder
import com.seenslide.teacher.core.media.AudioChunkRecorder
import com.seenslide.teacher.core.media.NarrationStore
import com.seenslide.teacher.core.network.api.SessionApi
import com.seenslide.teacher.core.network.api.VoiceApi
import com.seenslide.teacher.core.recording.StrokeWire
import org.json.JSONArray
import org.json.JSONObject
import com.seenslide.teacher.core.network.model.MarkerItem
import com.seenslide.teacher.core.network.model.SlideInfo
import com.seenslide.teacher.core.network.model.SyncMarkersRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import javax.inject.Inject

sealed class PublishState {
    data object Idle : PublishState()
    data class InProgress(val done: Int, val total: Int) : PublishState()
    data object Success : PublishState()
    data class Failed(val message: String) : PublishState()
}

data class NarrationUiState(
    val slides: List<SlideInfo> = emptyList(),
    val currentIndex: Int = 0,
    val isRecording: Boolean = false,
    val recordElapsedSec: Double = 0.0,
    val isPlaying: Boolean = false,
    /** slide_number → narration duration seconds */
    val narrated: Map<Int, Double> = emptyMap(),
    val publishState: PublishState = PublishState.Idle,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Ink recorded with the current slide's narration (audio-relative ms). */
    val inkStrokes: List<ReplayStroke> = emptyList(),
    /** Audio playback position, drives the ink replay animation. */
    val playbackPositionMs: Long = Long.MAX_VALUE,
    /** Whether the drawing tools are open while recording. */
    val inkEnabled: Boolean = true,
) {
    val currentSlide: SlideInfo? get() = slides.getOrNull(currentIndex)
    val narratedCount: Int get() = slides.count { narrated.containsKey(it.slideNumber) }
    val totalNarratedSeconds: Double
        get() = slides.sumOf { narrated[it.slideNumber] ?: 0.0 }
}

/**
 * Mode 2 — record a lecture slide-by-slide, offline, at your own pace.
 *
 * Each slide's narration is an independent local AAC file; re-record to
 * replace. Publish stitches them WITHOUT re-encoding: each file uploads
 * as an ordered chunk, markers land at the cumulative start of each
 * narrated slide, and the server merge produces one seekable recording —
 * indistinguishable to viewers from a live-recorded talk.
 */
@HiltViewModel
class NarrationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val slideRepository: SlideRepository,
    private val recorder: AudioChunkRecorder,
    private val aacEncoder: AacEncoder,
    private val narrationStore: NarrationStore,
    private val voiceApi: VoiceApi,
    private val sessionApi: SessionApi,
) : ViewModel() {

    companion object {
        private const val TAG = "Narration"
    }

    val sessionId: String = savedStateHandle["sessionId"] ?: ""
    val talkId: String = savedStateHandle["talkId"] ?: ""

    private val _uiState = MutableStateFlow(NarrationUiState())
    val uiState = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var playTickerJob: Job? = null
    private var player: MediaPlayer? = null

    /** Live canvas state while recording (tools, undo, elements). */
    val drawingState = DrawingState()

    /** Wall instant this slide's recording started — the ink clock anchor.
     *  Set at the same moment audio capture starts, so wall-relative stroke
     *  timestamps ARE audio-relative for this session. */
    var strokeStartWallMs: Long = 0L
        private set

    private val recordedElements = mutableListOf<DrawElement>()

    fun onStrokeCompleted(element: DrawElement) {
        // DrawingCanvas already added the element to drawingState —
        // here we only capture it for the timeline.
        if (!_uiState.value.isRecording) return
        recordedElements.add(element)
    }

    init {
        viewModelScope.launch {
            try {
                val slides = slideRepository.getSlides(talkId).sortedBy { it.slideNumber }
                _uiState.value = _uiState.value.copy(
                    slides = slides,
                    narrated = narrationStore.durations(talkId),
                    isLoading = false,
                )
                loadInkForCurrentSlide()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load slides: ${e.message}",
                )
            }
        }
    }

    fun slideImageUrl(slideNumber: Int): String {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        return "$base/api/cloud/talk/$talkId/slides/$slideNumber"
    }

    fun selectSlide(index: Int) {
        if (_uiState.value.isRecording) return // finish/stop first
        stopPlayback()
        if (index in _uiState.value.slides.indices) {
            _uiState.value = _uiState.value.copy(currentIndex = index)
            loadInkForCurrentSlide()
        }
    }

    private fun loadInkForCurrentSlide() {
        val slide = _uiState.value.currentSlide
        val ink = slide
            ?.let { narrationStore.loadStrokes(talkId, it.slideNumber) }
            ?.let { parseReplayStrokes(it) }
            ?: emptyList()
        _uiState.value = _uiState.value.copy(
            inkStrokes = ink,
            playbackPositionMs = Long.MAX_VALUE,
        )
    }

    fun nextSlide() = selectSlide(_uiState.value.currentIndex + 1)
    fun previousSlide() = selectSlide(_uiState.value.currentIndex - 1)

    // --- Recording ---

    fun startRecording() {
        if (_uiState.value.isRecording) return
        stopPlayback()
        try {
            if (!recorder.start()) {
                _uiState.value = _uiState.value.copy(error = "Could not open microphone")
                return
            }
        } catch (e: SecurityException) {
            _uiState.value = _uiState.value.copy(error = "Microphone permission missing")
            return
        }
        // Ink clock anchor = audio t0. Re-recording replaces both audio
        // and ink together, so the canvas starts clean.
        strokeStartWallMs = System.currentTimeMillis()
        drawingState.clear()
        recordedElements.clear()
        _uiState.value = _uiState.value.copy(
            isRecording = true,
            recordElapsedSec = 0.0,
            inkStrokes = emptyList(),
        )
        tickerJob = viewModelScope.launch {
            while (isActive && recorder.isRecording) {
                _uiState.value = _uiState.value.copy(recordElapsedSec = recorder.elapsedSeconds)
                delay(250)
            }
        }
    }

    fun stopRecording() {
        if (!_uiState.value.isRecording) return
        tickerJob?.cancel()
        tickerJob = null
        val slide = _uiState.value.currentSlide
        val pcm = recorder.stop()
        val duration = recorder.elapsedSeconds
        _uiState.value = _uiState.value.copy(isRecording = false)

        if (slide == null || pcm == null || pcm.isEmpty() || duration < 0.3) {
            _uiState.value = _uiState.value.copy(error = "Recording too short")
            return
        }

        // Snapshot ink drawn during this take (timestamps already
        // audio-relative — the anchor was set at audio start)
        val inkElements = recordedElements.toList()
        drawingState.clear()
        recordedElements.clear()

        viewModelScope.launch(Dispatchers.Default) {
            val aac = aacEncoder.encode(pcm, recorder.sampleRate, 1)
            if (aac == null || aac.isEmpty()) {
                _uiState.value = _uiState.value.copy(error = "Audio encoding failed")
                return@launch
            }
            narrationStore.save(talkId, slide.slideNumber, aac, duration)

            if (inkElements.isNotEmpty()) {
                val arr = JSONArray()
                for (el in inkElements) arr.put(StrokeWire.elementToJson(el))
                narrationStore.saveStrokes(talkId, slide.slideNumber, arr.toString())
            } else {
                narrationStore.deleteStrokes(talkId, slide.slideNumber)
            }

            _uiState.value = _uiState.value.copy(
                narrated = narrationStore.durations(talkId),
            )
            loadInkForCurrentSlide()
            Log.d(TAG, "Narration saved: slide ${slide.slideNumber}, ${duration}s, ${aac.size} bytes, ${inkElements.size} ink elements")
        }
    }

    fun deleteNarration() {
        val slide = _uiState.value.currentSlide ?: return
        stopPlayback()
        narrationStore.delete(talkId, slide.slideNumber)
        _uiState.value = _uiState.value.copy(
            narrated = narrationStore.durations(talkId),
            inkStrokes = emptyList(),
        )
    }

    // --- Local playback (review before publishing) ---

    fun playNarration() {
        val slide = _uiState.value.currentSlide ?: return
        val file = narrationStore.fileFor(talkId, slide.slideNumber)
        if (!file.exists()) return
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    playTickerJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        isPlaying = false,
                        playbackPositionMs = Long.MAX_VALUE,
                    )
                }
                prepare()
                start()
            }
            _uiState.value = _uiState.value.copy(isPlaying = true, playbackPositionMs = 0L)
            // Drive the ink replay off the audio position (~30fps)
            playTickerJob?.cancel()
            playTickerJob = viewModelScope.launch {
                while (isActive) {
                    val pos = try { player?.currentPosition?.toLong() } catch (_: Exception) { null }
                    if (pos != null && _uiState.value.isPlaying) {
                        _uiState.value = _uiState.value.copy(playbackPositionMs = pos)
                    }
                    delay(33)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Playback failed: ${e.message}")
            _uiState.value = _uiState.value.copy(error = "Playback failed", isPlaying = false)
        }
    }

    fun stopPlayback() {
        playTickerJob?.cancel()
        playTickerJob = null
        player?.let {
            try { it.stop(); it.release() } catch (_: Exception) {}
        }
        player = null
        if (_uiState.value.isPlaying || _uiState.value.playbackPositionMs != Long.MAX_VALUE) {
            _uiState.value = _uiState.value.copy(
                isPlaying = false,
                playbackPositionMs = Long.MAX_VALUE,
            )
        }
    }

    // --- Publish ---

    /**
     * Upload all narrated slides (in slide order) as one recording:
     * chunk i = slide i's AAC file, marker at each slide's cumulative
     * start, bulk marker sync, then finalize. Slides without narration
     * are skipped — they simply don't appear on the playback timeline.
     */
    fun publish() {
        val state = _uiState.value
        if (state.isRecording || state.publishState is PublishState.InProgress) return
        val narratedSlides = state.slides.filter { state.narrated.containsKey(it.slideNumber) }
        if (narratedSlides.isEmpty()) {
            _uiState.value = state.copy(error = "Record narration for at least one slide first")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                publishState = PublishState.InProgress(0, narratedSlides.size)
            )
            try {
                val start = voiceApi.startRecording(
                    sessionId = sessionId,
                    audioFormat = "aac",
                    talkId = talkId,
                    sampleRate = recorder.sampleRate,
                    channels = 1,
                )
                val rid = start.recordingId

                val markers = mutableListOf<MarkerItem>()
                val inkSlides = mutableListOf<Map<String, Any>>()
                var cumulative = 0.0
                narratedSlides.forEachIndexed { i, slide ->
                    val file = narrationStore.fileFor(talkId, slide.slideNumber)
                    val part = MultipartBody.Part.createFormData(
                        "file",
                        "chunk_%04d.aac".format(i + 1),
                        file.asRequestBody("audio/aac".toMediaType()),
                    )
                    voiceApi.uploadChunk(rid, part, chunkIndex = i + 1)
                    markers.add(MarkerItem(slide.slideNumber, cumulative))

                    // Marker via the per-slide POST — the same path the
                    // live class uses, which lands reliably. The bulk PUT
                    // below is kept as reinforcement, but this is what
                    // actually made narration markers appear. Individually
                    // guarded so one failure can't abort the publish.
                    try {
                        voiceApi.addMarker(rid, slide.slideNumber, cumulative)
                    } catch (e: Exception) {
                        Log.w(TAG, "per-slide marker failed (slide ${slide.slideNumber})", e)
                    }

                    // Ink: shift this slide's stroke timeline to its position
                    // in the merged audio — the same offset as its marker.
                    narrationStore.loadStrokes(talkId, slide.slideNumber)?.let { json ->
                        val shifted = shiftStrokeTimes(json, (cumulative * 1000).toLong())
                        if (shifted.length() > 0) {
                            inkSlides.add(
                                mapOf(
                                    "talk_id" to talkId,
                                    "slide_number" to slide.slideNumber,
                                    "recording_start" to 0.0,
                                    "strokes" to jsonArrayToList(shifted),
                                )
                            )
                        }
                    }

                    cumulative += _uiState.value.narrated[slide.slideNumber] ?: 0.0
                    _uiState.value = _uiState.value.copy(
                        publishState = PublishState.InProgress(i + 1, narratedSlides.size)
                    )
                }

                // Reinforce with the bulk sync, but never let it abort the
                // finalize — a failure here (e.g. response parsing) must not
                // skip stopRecording and leave the recording stuck.
                try {
                    voiceApi.syncMarkers(rid, SyncMarkersRequest(markers))
                } catch (e: Exception) {
                    Log.w(TAG, "bulk marker sync failed (per-slide markers already sent)", e)
                }
                voiceApi.stopRecording(rid, cumulative)

                // Publish the merged ink timeline (replaces the talk's
                // previous stroke recording — same semantics as re-publish)
                if (inkSlides.isNotEmpty()) {
                    try {
                        sessionApi.uploadStrokeRecording(
                            talkId,
                            mapOf("slides" to inkSlides, "clock" to "audio-ms"),
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Ink upload failed (audio published fine)", e)
                    }
                }

                _uiState.value = _uiState.value.copy(publishState = PublishState.Success)
                Log.d(TAG, "Published: ${narratedSlides.size} slides, ${cumulative}s, recording=$rid")
            } catch (e: Exception) {
                Log.e(TAG, "Publish failed", e)
                _uiState.value = _uiState.value.copy(
                    publishState = PublishState.Failed(e.message ?: "Upload failed")
                )
            }
        }
    }

    fun dismissPublishResult() {
        _uiState.value = _uiState.value.copy(publishState = PublishState.Idle)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Append a blank white slide to the deck — a fresh writing surface for
     * an annotated explanation mid-lecture. Uploads immediately (slides are
     * cloud-backed) and jumps to it.
     */
    fun addBlankSlide() {
        if (_uiState.value.isRecording) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bmp = android.graphics.Bitmap.createBitmap(
                    1600, 1200, android.graphics.Bitmap.Config.ARGB_8888
                )
                bmp.eraseColor(android.graphics.Color.WHITE)
                val out = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                bmp.recycle()
                slideRepository.uploadSlide(sessionId, talkId, out.toByteArray())
                val slides = slideRepository.getSlides(talkId).sortedBy { it.slideNumber }
                _uiState.value = _uiState.value.copy(
                    slides = slides,
                    currentIndex = (slides.size - 1).coerceAtLeast(0),
                )
                loadInkForCurrentSlide()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to add blank slide: ${e.message}"
                )
            }
        }
    }

    /** Add [offsetMs] to every t / t_start / t_end in a wire strokes array. */
    private fun shiftStrokeTimes(json: String, offsetMs: Long): JSONArray {
        val out = JSONArray()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.has("t_start")) o.put("t_start", o.optLong("t_start") + offsetMs)
                if (o.has("t_end")) o.put("t_end", o.optLong("t_end") + offsetMs)
                if (o.has("t")) o.put("t", o.optLong("t") + offsetMs)
                o.optJSONArray("points")?.let { pts ->
                    for (j in 0 until pts.length()) {
                        val p = pts.getJSONObject(j)
                        p.put("t", p.optLong("t") + offsetMs)
                    }
                }
                out.put(o)
            }
        } catch (e: Exception) {
            Log.w(TAG, "shiftStrokeTimes failed: ${e.message}")
        }
        return out
    }

    /** JSONArray → plain lists/maps for Moshi map serialization. */
    private fun jsonArrayToList(arr: JSONArray): List<Any?> =
        (0 until arr.length()).map { i ->
            when (val v = arr.get(i)) {
                is JSONObject -> jsonObjectToMap(v)
                is JSONArray -> jsonArrayToList(v)
                else -> v
            }
        }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> =
        obj.keys().asSequence().associateWith { k ->
            when (val v = obj.get(k)) {
                is JSONObject -> jsonObjectToMap(v)
                is JSONArray -> jsonArrayToList(v)
                else -> v
            }
        }

    override fun onCleared() {
        stopPlayback()
        if (recorder.isRecording) recorder.stop()
        super.onCleared()
    }
}
