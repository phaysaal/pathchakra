package com.seenslide.teacher.feature.slide.camera

import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.core.data.SlideQueuedLocallyException
import com.seenslide.teacher.core.data.SlideRepository
import com.seenslide.teacher.core.media.ImageEnhancer
import com.seenslide.teacher.core.network.model.SlideInfo
import com.seenslide.teacher.core.ui.ErrorClassifier
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
    val savedLocally: Boolean = false,
    val error: String? = null,
    val uploadedSlide: SlideInfo? = null,
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val slideRepository: SlideRepository,
    private val imageEnhancer: ImageEnhancer,
    private val errorClassifier: ErrorClassifier,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""
    val talkId: String? = savedStateHandle.get<String>("talkId")?.takeIf { it != "none" }
    private val replaceSlideNumber: Int? = savedStateHandle.get<Int>("replaceSlide")?.takeIf { it > 0 }

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
                    val enhancedBitmap = if (enhanced) imageEnhancer.enhance(bitmap) else bitmap
                    // Normalize to the canonical slide aspect so camera
                    // photos match every other slide kind in the deck.
                    val finalBitmap = com.seenslide.teacher.core.slidedoc.SlideCanvas
                        .fitToCanonical(enhancedBitmap)
                    val result = imageEnhancer.compressToBytes(finalBitmap)
                    finalBitmap.recycle()
                    if (enhancedBitmap !== bitmap) enhancedBitmap.recycle()
                    bitmap.recycle()
                    result
                }

                val response = slideRepository.uploadSlide(sessionId, talkId, bytes, replaceSlideNumber)
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadSuccess = true,
                    uploadedSlide = response.slideNumber?.let {
                        SlideInfo(
                            slideNumber = it,
                            slideId = response.slideId,
                        )
                    },
                )
            } catch (e: SlideQueuedLocallyException) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadSuccess = true,
                    savedLocally = true,
                    uploadedSlide = SlideInfo(slideNumber = e.slideNumber, slideId = null),
                )
            } catch (e: Exception) {
                android.util.Log.e("CameraViewModel", "Slide upload failed", e)
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = errorClassifier.classify(e),
                )
            }
        }
    }

    fun onUploadSuccessHandled() {
        _uiState.value = CameraUiState()
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
