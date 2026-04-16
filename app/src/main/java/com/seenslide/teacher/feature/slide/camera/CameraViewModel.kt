package com.seenslide.teacher.feature.slide.camera

import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.core.data.SlideRepository
import com.seenslide.teacher.core.media.ImageEnhancer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CameraUiState(
    val capturedPhotoPath: String? = null,
    val isUploading: Boolean = false,
    val uploadSuccess: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val slideRepository: SlideRepository,
    private val imageEnhancer: ImageEnhancer,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""
    val talkId: String? = savedStateHandle.get<String>("talkId")?.takeIf { it != "none" }

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState

    fun onPhotoCaptured(path: String) {
        _uiState.value = CameraUiState(capturedPhotoPath = path)
    }

    fun onRetake() {
        _uiState.value = CameraUiState()
    }

    fun onAccept(enhanced: Boolean) {
        val path = _uiState.value.capturedPhotoPath ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, error = null)
            try {
                val bytes = withContext(Dispatchers.Default) {
                    val bitmap = BitmapFactory.decodeFile(path)
                    val finalBitmap = if (enhanced) imageEnhancer.enhance(bitmap) else bitmap
                    val result = imageEnhancer.compressToBytes(finalBitmap)
                    if (finalBitmap !== bitmap) finalBitmap.recycle()
                    bitmap.recycle()
                    result
                }

                slideRepository.uploadSlide(sessionId, talkId, bytes)
                _uiState.value = _uiState.value.copy(isUploading = false, uploadSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = "Upload failed. Please try again.",
                )
            }
        }
    }

    fun onUploadSuccessHandled() {
        _uiState.value = CameraUiState()
    }
}
