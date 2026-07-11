package com.seenslide.teacher.feature.live

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.BuildConfig
import com.seenslide.teacher.core.data.SlideRepository
import com.seenslide.teacher.core.drawing.DrawElement
import com.seenslide.teacher.core.drawing.DrawingState
import com.seenslide.teacher.core.drawing.StrokePoint
import com.seenslide.teacher.core.media.VoiceStreamingService
import com.seenslide.teacher.core.network.api.SessionApi
import com.seenslide.teacher.core.network.model.NavigateSlideRequest
import com.seenslide.teacher.core.network.model.SlideInfo
import com.seenslide.teacher.R
import com.seenslide.teacher.core.network.websocket.OStudiWebSocket
import com.seenslide.teacher.core.network.websocket.WsConnectionState
import com.seenslide.teacher.core.ui.ErrorClassifier
import com.seenslide.teacher.core.recording.StrokeRecorder
import com.seenslide.teacher.core.recording.StrokeRecordingStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveClassUiState(
    val slides: List<SlideInfo> = emptyList(),
    val currentSlideIndex: Int = 0,
    val isLive: Boolean = false,
    val isVoiceStreaming: Boolean = false,
    val isRecordingStrokes: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showEndConfirmation: Boolean = false,
    val wsState: WsConnectionState = WsConnectionState.DISCONNECTED,
)

@HiltViewModel
class LiveClassViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val slideRepository: SlideRepository,
    private val sessionApi: SessionApi,
    private val webSocket: OStudiWebSocket,
    private val voiceService: VoiceStreamingService,
    private val recordingStore: StrokeRecordingStore,
    private val errorClassifier: ErrorClassifier,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""
    val talkId: String = savedStateHandle["talkId"] ?: ""

    // Use sessionId as groupId for WebSocket (matches web viewer behavior)
    private val groupId: String = sessionId

    val drawingState = DrawingState()
    val strokeRecorder = StrokeRecorder()

    private val _uiState = MutableStateFlow(LiveClassUiState())
    val uiState: StateFlow<LiveClassUiState> = _uiState

    init {
        loadSlides()
        // Observe WebSocket connection state
        webSocket.connectionState
            .onEach { wsState ->
                _uiState.value = _uiState.value.copy(wsState = wsState)
            }
            .launchIn(viewModelScope)
    }

    private fun loadSlides() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val slides = slideRepository.getSlides(talkId)
                _uiState.value = _uiState.value.copy(slides = slides, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = errorClassifier.classify(e),
                )
            }
        }
    }

    fun slideImageUrl(slideNumber: Int): String {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        return "$base/api/cloud/talk/$talkId/slides/$slideNumber"
    }

    // --- Go Live ---

    fun goLive() {
        viewModelScope.launch {
            try {
                // Notify server this talk is now live
                sessionApi.goLive(talkId)
            } catch (e: Exception) {
                // Non-fatal: live navigation still works via WebSocket fallback
            }
        }

        webSocket.connect(groupId)

        // Start stroke recording
        strokeRecorder.startRecording(talkId, currentSlideNumber())

        _uiState.value = _uiState.value.copy(
            isLive = true,
            isRecordingStrokes = true,
        )

        // Send initial slide position
        webSocket.sendSlideChange(currentSlideNumber())
    }

    fun startVoiceStreaming() {
        viewModelScope.launch {
            val started = voiceService.start(sessionId, talkId, viewModelScope)
            _uiState.value = _uiState.value.copy(isVoiceStreaming = started)
            if (!started) {
                _uiState.value = _uiState.value.copy(error = errorClassifier.getString(R.string.error_voice_start))
            }
        }
    }

    fun stopVoiceStreaming() {
        viewModelScope.launch {
            voiceService.stop()
            _uiState.value = _uiState.value.copy(isVoiceStreaming = false)
        }
    }

    fun requestEndClass() {
        _uiState.value = _uiState.value.copy(showEndConfirmation = true)
    }

    fun dismissEndConfirmation() {
        _uiState.value = _uiState.value.copy(showEndConfirmation = false)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun endClass() {
        viewModelScope.launch {
            // Stop voice
            if (_uiState.value.isVoiceStreaming) {
                voiceService.stop()
            }

            // Stop stroke recording, save locally, and upload.
            // Rebase stroke timestamps onto the AUDIO timeline so ink and
            // voice replay in sync: offset = voice start - stroke start
            // (both wall clock). Strokes drawn before voice began clamp to
            // t=0 — they're the baseline picture when playback starts. If
            // voice never ran, offset is 0 and wall-relative times remain
            // (stroke-only replay).
            strokeRecorder.stopRecording()
            val rebaseMs = if (voiceService.voiceStartWallMs > 0L && strokeRecorder.recordingStartTime > 0L) {
                (voiceService.voiceStartWallMs - strokeRecorder.recordingStartTime).coerceAtLeast(0L)
            } else 0L
            val recordingJson = strokeRecorder.toJson(rebaseMs)
            recordingStore.save(talkId, recordingJson)

            // Upload stroke recording to server
            try {
                val slides = mutableListOf<Map<String, Any>>()
                for (i in 0 until recordingJson.length()) {
                    val obj = recordingJson.getJSONObject(i)
                    slides.add(jsonObjectToMap(obj))
                }
                sessionApi.uploadStrokeRecording(
                    talkId,
                    mapOf("slides" to slides, "clock" to "audio-ms"),
                )
            } catch (_: Exception) {
                // Non-fatal — local copy saved, can retry later
            }

            // Notify server that live session ended
            try {
                sessionApi.endLive(talkId)
            } catch (_: Exception) {
                // Non-fatal
            }

            // Disconnect WebSocket
            webSocket.disconnect()

            _uiState.value = _uiState.value.copy(
                isLive = false,
                isVoiceStreaming = false,
                isRecordingStrokes = false,
                showEndConfirmation = false,
            )
        }
    }

    // --- Slide Navigation ---

    fun nextSlide() {
        val state = _uiState.value
        if (state.currentSlideIndex < state.slides.size - 1) {
            val newIndex = state.currentSlideIndex + 1
            _uiState.value = state.copy(currentSlideIndex = newIndex)
            onSlideChanged(newIndex)
        }
    }

    fun previousSlide() {
        val state = _uiState.value
        if (state.currentSlideIndex > 0) {
            val newIndex = state.currentSlideIndex - 1
            _uiState.value = state.copy(currentSlideIndex = newIndex)
            onSlideChanged(newIndex)
        }
    }

    private fun onSlideChanged(index: Int) {
        val slideNumber = _uiState.value.slides.getOrNull(index)?.slideNumber ?: return

        // Clear drawing state for new slide
        drawingState.clear()

        // Notify viewers via WebSocket + update server live state
        if (_uiState.value.isLive) {
            webSocket.sendSlideChange(slideNumber)
            viewModelScope.launch {
                try {
                    sessionApi.navigateSlide(talkId, NavigateSlideRequest(slideNumber))
                } catch (_: Exception) {
                    // Non-fatal: WebSocket already sent the change
                }
            }
        }

        // Update stroke recorder
        strokeRecorder.setCurrentSlide(slideNumber)

        // Add voice marker
        if (_uiState.value.isVoiceStreaming) {
            viewModelScope.launch {
                voiceService.addSlideMarker(slideNumber)
            }
        }
    }

    // --- Drawing callbacks (live sync) ---

    fun onStrokeStarted(stroke: com.seenslide.teacher.core.drawing.Stroke) {
        if (_uiState.value.isLive) {
            val slideId = currentSlideId()
            webSocket.sendStrokeStart(slideId, stroke.tool, stroke.color, stroke.width)
        }
    }

    fun onStrokePointAdded(points: List<StrokePoint>) {
        if (_uiState.value.isLive) {
            webSocket.sendStrokePoints(points)
        }
    }

    fun onStrokeCompleted(element: DrawElement) {
        if (_uiState.value.isLive) {
            webSocket.sendStrokeEnd(element, currentSlideId())
        }
        if (_uiState.value.isRecordingStrokes) {
            strokeRecorder.recordElement(element)
        }
    }

    private fun currentSlideNumber(): Int {
        return _uiState.value.slides.getOrNull(_uiState.value.currentSlideIndex)?.slideNumber ?: 1
    }

    private fun currentSlideId(): String {
        return _uiState.value.slides.getOrNull(_uiState.value.currentSlideIndex)?.slideId
            ?: sessionId
    }

    /** Convert a JSONObject to a Map for Retrofit serialization. */
    private fun jsonObjectToMap(obj: org.json.JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        for (key in obj.keys()) {
            val value = obj.get(key)
            map[key] = when (value) {
                is org.json.JSONObject -> jsonObjectToMap(value)
                is org.json.JSONArray -> jsonArrayToList(value)
                org.json.JSONObject.NULL -> ""
                else -> value
            }
        }
        return map
    }

    private fun jsonArrayToList(arr: org.json.JSONArray): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until arr.length()) {
            val value = arr.get(i)
            list.add(
                when (value) {
                    is org.json.JSONObject -> jsonObjectToMap(value)
                    is org.json.JSONArray -> jsonArrayToList(value)
                    org.json.JSONObject.NULL -> ""
                    else -> value
                },
            )
        }
        return list
    }

    override fun onCleared() {
        super.onCleared()
        webSocket.disconnect()
    }
}
