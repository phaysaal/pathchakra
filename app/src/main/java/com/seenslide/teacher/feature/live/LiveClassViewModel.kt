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
import com.seenslide.teacher.core.network.model.SlideInfo
import com.seenslide.teacher.core.network.websocket.SeenSlideWebSocket
import com.seenslide.teacher.core.recording.StrokeRecorder
import com.seenslide.teacher.core.recording.StrokeRecordingStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
)

@HiltViewModel
class LiveClassViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val slideRepository: SlideRepository,
    private val webSocket: SeenSlideWebSocket,
    private val voiceService: VoiceStreamingService,
    private val recordingStore: StrokeRecordingStore,
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
                    error = "Could not load slides",
                )
            }
        }
    }

    fun slideImageUrl(slideNumber: Int): String {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        return "$base/api/cloud/slides/$sessionId/$slideNumber"
    }

    // --- Go Live ---

    fun goLive() {
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
                _uiState.value = _uiState.value.copy(error = "Could not start voice")
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

    fun endClass() {
        viewModelScope.launch {
            // Stop voice
            if (_uiState.value.isVoiceStreaming) {
                voiceService.stop()
            }

            // Stop stroke recording and save
            strokeRecorder.stopRecording()
            recordingStore.save(talkId, strokeRecorder.toJson())

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

        // Notify viewers via WebSocket
        if (_uiState.value.isLive) {
            webSocket.sendSlideChange(slideNumber)
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

    override fun onCleared() {
        super.onCleared()
        webSocket.disconnect()
    }
}
