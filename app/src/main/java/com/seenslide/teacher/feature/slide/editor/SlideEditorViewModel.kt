package com.seenslide.teacher.feature.slide.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.BuildConfig
import com.seenslide.teacher.core.data.SlideQueuedLocallyException
import com.seenslide.teacher.core.data.SlideRepository
import com.seenslide.teacher.core.drawing.DrawElement
import com.seenslide.teacher.core.drawing.DrawTool
import com.seenslide.teacher.core.drawing.DrawingState
import com.seenslide.teacher.core.drawing.StrokePoint
import com.seenslide.teacher.core.media.ImageEnhancer
import com.seenslide.teacher.core.media.VoiceStreamingService
import com.seenslide.teacher.core.network.model.SlideInfo
import com.seenslide.teacher.core.network.websocket.OStudiWebSocket
import com.seenslide.teacher.core.ui.ErrorClassifier
import com.seenslide.teacher.core.recording.StrokeRecorder
import com.seenslide.teacher.core.recording.StrokeRecordingStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

data class SlideEditorUiState(
    val isLive: Boolean = false,
    val isRecording: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val savedLocally: Boolean = false,
    val error: String? = null,
    val showTextDialog: Boolean = false,
    val currentTool: DrawTool = DrawTool.PEN,
    val currentColor: Long = 0xFF000000,
    val currentWidth: Float = 3f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val uploadedSlide: SlideInfo? = null,
)

@HiltViewModel
class SlideEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val slideRepository: SlideRepository,
    private val webSocket: OStudiWebSocket,
    private val imageEnhancer: ImageEnhancer,
    private val recordingStore: StrokeRecordingStore,
    private val voiceStreamingService: VoiceStreamingService,
    private val okHttpClient: OkHttpClient,
    private val errorClassifier: ErrorClassifier,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""
    val talkId: String? = savedStateHandle.get<String>("talkId")?.takeIf { it != "none" }
    val mode: String = savedStateHandle["mode"] ?: "blank" // "blank", "photo:{path}", "pdf"
    private val slideId: String = savedStateHandle.get<String>("slideId") ?: sessionId
    private val replaceSlideNumber: Int? = savedStateHandle.get<Int>("replaceSlide")?.takeIf { it > 0 }

    val drawingState = DrawingState()
    val strokeRecorder = StrokeRecorder()

    private val _uiState = MutableStateFlow(SlideEditorUiState())
    val uiState: StateFlow<SlideEditorUiState> = _uiState

    var backgroundBitmap: Bitmap? = null
        private set

    init {
        when {
            mode.startsWith("photo:") -> {
                val path = mode.removePrefix("photo:")
                viewModelScope.launch {
                    backgroundBitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(path)
                    }
                }
            }
            mode.startsWith("edit:") -> {
                // edit:{talkId}:{slideNumber} — download existing slide as background
                val parts = mode.removePrefix("edit:").split(":")
                if (parts.size == 2) {
                    val editTalkId = parts[0]
                    val slideNum = parts[1]
                    val base = BuildConfig.API_BASE_URL.trimEnd('/')
                    val url = "$base/api/cloud/talk/$editTalkId/slides/$slideNum"
                    viewModelScope.launch {
                        backgroundBitmap = withContext(Dispatchers.IO) {
                            try {
                                val request = Request.Builder().url(url).build()
                                val response = okHttpClient.newCall(request).execute()
                                response.use { resp ->
                                    resp.body?.byteStream()?.let { BitmapFactory.decodeStream(it) }
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }
            }
        }
    }

    fun onToolSelected(tool: DrawTool) {
        drawingState.currentTool = tool
        _uiState.value = _uiState.value.copy(currentTool = tool)
    }

    fun onColorSelected(color: Long) {
        drawingState.currentColor = color
        _uiState.value = _uiState.value.copy(currentColor = color)
    }

    fun onWidthChanged(width: Float) {
        drawingState.currentWidth = width
        _uiState.value = _uiState.value.copy(currentWidth = width)
    }

    fun onUndo() {
        drawingState.undo()
        markCanvasChanged()
        refreshUndoRedo()
    }

    fun onRedo() {
        drawingState.redo()
        markCanvasChanged()
        refreshUndoRedo()
    }

    fun showTextDialog() {
        _uiState.value = _uiState.value.copy(showTextDialog = true)
    }

    fun dismissTextDialog() {
        _uiState.value = _uiState.value.copy(showTextDialog = false)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun addTextElement(element: DrawElement.TextElement) {
        drawingState.addElement(element)
        onStrokeCompleted(element)
        dismissTextDialog()
    }

    fun clearCanvas() {
        if (drawingState.elements.isEmpty()) return
        drawingState.clear()
        _uiState.value = _uiState.value.copy(
            canUndo = false,
            canRedo = false,
            hasUnsavedChanges = true,
        )
    }

    // --- Live streaming ---

    fun goLive(groupId: String) {
        webSocket.connect(groupId)
        _uiState.value = _uiState.value.copy(isLive = true)
    }

    fun stopLive() {
        webSocket.disconnect()
        _uiState.value = _uiState.value.copy(isLive = false)
    }

    // --- Recording ---

    /**
     * Start recording strokes + voice together.
     * Voice requires RECORD_AUDIO permission — call [startRecordingWithVoice]
     * from the UI after permission is granted, or [startRecordingStrokesOnly]
     * if permission was denied.
     */
    fun startRecordingWithVoice() {
        talkId?.let { tid ->
            strokeRecorder.startRecording(tid)
            _uiState.value = _uiState.value.copy(isRecording = true)

            viewModelScope.launch {
                val started = voiceStreamingService.start(sessionId, talkId, viewModelScope)
                if (!started) {
                    android.util.Log.w("SlideEditorVM", "Voice streaming failed to start, strokes still recording")
                }
            }
        }
    }

    fun startRecordingStrokesOnly() {
        talkId?.let { tid ->
            strokeRecorder.startRecording(tid)
            _uiState.value = _uiState.value.copy(isRecording = true)
        }
    }

    fun stopRecording() {
        strokeRecorder.stopRecording()
        _uiState.value = _uiState.value.copy(isRecording = false)

        viewModelScope.launch {
            val hadVoice = voiceStreamingService.isStreaming
            // Stop voice streaming
            if (hadVoice) {
                voiceStreamingService.stop()
            }

            // Save stroke recording locally — rebased onto the audio
            // timeline when voice ran (see LiveClassViewModel.endClass)
            val rebaseMs = if (hadVoice &&
                voiceStreamingService.voiceStartWallMs > 0L &&
                strokeRecorder.recordingStartTime > 0L
            ) {
                (voiceStreamingService.voiceStartWallMs - strokeRecorder.recordingStartTime)
                    .coerceAtLeast(0L)
            } else 0L
            talkId?.let { tid ->
                recordingStore.save(tid, strokeRecorder.toJson(rebaseMs))
            }
        }
    }

    // --- Stroke callbacks (called from DrawingCanvas) ---

    fun onStrokeStarted(stroke: com.seenslide.teacher.core.drawing.Stroke) {
        if (_uiState.value.isLive) {
            webSocket.sendStrokeStart(slideId, stroke.tool, stroke.color, stroke.width)
        }
    }

    fun onStrokePointAdded(points: List<StrokePoint>) {
        if (_uiState.value.isLive) {
            webSocket.sendStrokePoints(points)
        }
    }

    fun onStrokeCompleted(element: DrawElement) {
        // Live sync
        if (_uiState.value.isLive) {
            webSocket.sendStrokeEnd(element, slideId)
        }

        // Recording
        if (_uiState.value.isRecording) {
            strokeRecorder.recordElement(element)
        }

        markCanvasChanged()
        refreshUndoRedo()
    }

    // --- Save slide as image ---

    fun saveSlideAsImage(canvasBitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val bytes = withContext(Dispatchers.Default) {
                    imageEnhancer.compressToBytes(canvasBitmap, quality = 90)
                }
                canvasBitmap.recycle()
                val response = slideRepository.uploadSlide(sessionId, talkId, bytes, replaceSlideNumber)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveSuccess = true,
                    hasUnsavedChanges = false,
                    uploadedSlide = response.slideNumber?.let {
                        SlideInfo(
                            slideNumber = it,
                            slideId = response.slideId,
                        )
                    },
                )
            } catch (e: SlideQueuedLocallyException) {
                // Saved to disk — treat as success, will upload when online
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveSuccess = true,
                    savedLocally = true,
                    hasUnsavedChanges = false,
                    uploadedSlide = SlideInfo(slideNumber = e.slideNumber, slideId = null),
                )
            } catch (e: Exception) {
                canvasBitmap.recycle()
                android.util.Log.e("SlideEditorVM", "Slide upload failed", e)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = errorClassifier.classify(e),
                )
            }
        }
    }

    fun onSaveSuccessHandled() {
        _uiState.value = _uiState.value.copy(saveSuccess = false, savedLocally = false)
    }

    private fun refreshUndoRedo() {
        _uiState.value = _uiState.value.copy(
            canUndo = drawingState.canUndo,
            canRedo = drawingState.canRedo,
        )
    }

    private fun markCanvasChanged() {
        _uiState.value = _uiState.value.copy(hasUnsavedChanges = true)
    }

    override fun onCleared() {
        super.onCleared()
        if (_uiState.value.isLive) webSocket.disconnect()
        if (_uiState.value.isRecording) stopRecording()
        backgroundBitmap?.recycle()
        backgroundBitmap = null
    }
}
