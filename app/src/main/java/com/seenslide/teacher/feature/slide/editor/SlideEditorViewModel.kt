package com.seenslide.teacher.feature.slide.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.core.data.SlideRepository
import com.seenslide.teacher.core.drawing.DrawElement
import com.seenslide.teacher.core.drawing.DrawTool
import com.seenslide.teacher.core.drawing.DrawingState
import com.seenslide.teacher.core.drawing.StrokePoint
import com.seenslide.teacher.core.media.ImageEnhancer
import com.seenslide.teacher.core.network.websocket.SeenSlideWebSocket
import com.seenslide.teacher.core.recording.StrokeRecorder
import com.seenslide.teacher.core.recording.StrokeRecordingStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SlideEditorUiState(
    val isLive: Boolean = false,
    val isRecording: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
    val showTextDialog: Boolean = false,
    val currentTool: DrawTool = DrawTool.PEN,
    val currentColor: Long = 0xFF000000,
    val currentWidth: Float = 3f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

@HiltViewModel
class SlideEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val slideRepository: SlideRepository,
    private val webSocket: SeenSlideWebSocket,
    private val imageEnhancer: ImageEnhancer,
    private val recordingStore: StrokeRecordingStore,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""
    val talkId: String? = savedStateHandle.get<String>("talkId")?.takeIf { it != "none" }
    val mode: String = savedStateHandle["mode"] ?: "blank" // "blank", "photo:{path}", "pdf"
    private val slideId: String = savedStateHandle.get<String>("slideId") ?: sessionId

    val drawingState = DrawingState()
    val strokeRecorder = StrokeRecorder()

    private val _uiState = MutableStateFlow(SlideEditorUiState())
    val uiState: StateFlow<SlideEditorUiState> = _uiState

    var backgroundBitmap: Bitmap? = null
        private set

    init {
        // Load background image if photo mode
        if (mode.startsWith("photo:")) {
            val path = mode.removePrefix("photo:")
            viewModelScope.launch {
                backgroundBitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(path)
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
        refreshUndoRedo()
    }

    fun onRedo() {
        drawingState.redo()
        refreshUndoRedo()
    }

    fun showTextDialog() {
        _uiState.value = _uiState.value.copy(showTextDialog = true)
    }

    fun dismissTextDialog() {
        _uiState.value = _uiState.value.copy(showTextDialog = false)
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

    fun startRecording() {
        talkId?.let { tid ->
            strokeRecorder.startRecording(tid)
            _uiState.value = _uiState.value.copy(isRecording = true)
        }
    }

    fun stopRecording() {
        strokeRecorder.stopRecording()
        _uiState.value = _uiState.value.copy(isRecording = false)

        // Save recording locally
        viewModelScope.launch {
            talkId?.let { tid ->
                recordingStore.save(tid, strokeRecorder.toJson())
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
                slideRepository.uploadSlide(sessionId, talkId, bytes)
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Could not save slide",
                )
            }
        }
    }

    fun onSaveSuccessHandled() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }

    private fun refreshUndoRedo() {
        _uiState.value = _uiState.value.copy(
            canUndo = drawingState.canUndo,
            canRedo = drawingState.canRedo,
        )
    }

    override fun onCleared() {
        super.onCleared()
        if (_uiState.value.isLive) webSocket.disconnect()
        if (_uiState.value.isRecording) stopRecording()
    }
}
