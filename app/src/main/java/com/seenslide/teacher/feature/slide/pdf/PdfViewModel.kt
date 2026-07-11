package com.seenslide.teacher.feature.slide.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.R
import com.seenslide.teacher.core.data.SlideQueuedLocallyException
import com.seenslide.teacher.core.data.SlideRepository
import com.seenslide.teacher.core.media.ImageEnhancer
import com.seenslide.teacher.core.network.model.SlideInfo
import com.seenslide.teacher.core.ui.ErrorClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class PdfUiState(
    val pdfLoaded: Boolean = false,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val pageBitmap: Bitmap? = null,
    val isCropping: Boolean = false,
    val isUploading: Boolean = false,
    val uploadSuccess: Boolean = false,
    val savedLocally: Boolean = false,
    val error: String? = null,
    val uploadedSlide: SlideInfo? = null,
)

@HiltViewModel
class PdfViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val slideRepository: SlideRepository,
    private val imageEnhancer: ImageEnhancer,
    private val errorClassifier: ErrorClassifier,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""
    val talkId: String? = savedStateHandle.get<String>("talkId")?.takeIf { it != "none" }
    private val replaceSlideNumber: Int? = savedStateHandle.get<Int>("replaceSlide")?.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(PdfUiState())
    val uiState: StateFlow<PdfUiState> = _uiState

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    fun loadPdf(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Copy URI to local file (content:// URIs can't be used directly with PdfRenderer)
                    val tempFile = File(context.cacheDir, "imported_pdf.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    fileDescriptor?.close()
                    pdfRenderer?.close()

                    val fd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    fileDescriptor = fd
                    pdfRenderer = PdfRenderer(fd)
                }

                val pageCount = pdfRenderer?.pageCount ?: 0
                _uiState.value = PdfUiState(pdfLoaded = true, pageCount = pageCount)

                if (pageCount > 0) {
                    renderPage(0)
                }
            } catch (e: Exception) {
                _uiState.value = PdfUiState(error = context.getString(R.string.error_pdf_open))
            }
        }
    }

    fun goToPage(page: Int) {
        val pageCount = _uiState.value.pageCount
        if (page in 0 until pageCount) {
            renderPage(page)
        }
    }

    fun nextPage() = goToPage(_uiState.value.currentPage + 1)
    fun previousPage() = goToPage(_uiState.value.currentPage - 1)

    /** Enter crop mode for the current page. */
    fun enterCropMode() {
        _uiState.value = _uiState.value.copy(isCropping = true)
    }

    /** Exit crop mode back to page selection. */
    fun exitCropMode() {
        _uiState.value = _uiState.value.copy(isCropping = false)
    }

    /**
     * Save a cropped region of the current page as a slide.
     * Coordinates are fractions 0..1 relative to the bitmap dimensions.
     */
    fun saveCroppedRegion(leftFrac: Float, topFrac: Float, rightFrac: Float, bottomFrac: Float) {
        val bitmap = _uiState.value.pageBitmap ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, error = null)
            try {
                val bytes = withContext(Dispatchers.Default) {
                    val x = (leftFrac * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                    val y = (topFrac * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                    val w = ((rightFrac - leftFrac) * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
                    val h = ((bottomFrac - topFrac) * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
                    val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
                    val fitted = com.seenslide.teacher.core.slidedoc.SlideCanvas.fitToCanonical(cropped)
                    val result = imageEnhancer.compressToBytes(fitted, quality = 90)
                    fitted.recycle()
                    if (cropped !== bitmap) cropped.recycle()
                    result
                }
                uploadBytes(bytes)
            } catch (e: Exception) {
                android.util.Log.e("PdfViewModel", "Cropped slide upload failed", e)
                _uiState.value = _uiState.value.copy(isUploading = false, error = errorClassifier.classify(e))
            }
        }
    }

    /**
     * Save the current page as a slide (full page).
     */
    fun savePageAsSlide() {
        val bitmap = _uiState.value.pageBitmap ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, error = null)
            try {
                val bytes = withContext(Dispatchers.Default) {
                    val fitted = com.seenslide.teacher.core.slidedoc.SlideCanvas.fitToCanonical(bitmap)
                    val result = imageEnhancer.compressToBytes(fitted, quality = 90)
                    fitted.recycle()
                    result
                }
                uploadBytes(bytes)
            } catch (e: Exception) {
                android.util.Log.e("PdfViewModel", "Slide upload failed", e)
                _uiState.value = _uiState.value.copy(isUploading = false, error = errorClassifier.classify(e))
            }
        }
    }

    private suspend fun uploadBytes(bytes: ByteArray) {
        try {
            val response = slideRepository.uploadSlide(sessionId, talkId, bytes, replaceSlideNumber)
            _uiState.value = _uiState.value.copy(
                isUploading = false,
                isCropping = false,
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
                isCropping = false,
                uploadSuccess = true,
                savedLocally = true,
                uploadedSlide = SlideInfo(slideNumber = e.slideNumber, slideId = null),
            )
        }
    }

    fun onUploadSuccessHandled() {
        _uiState.value = _uiState.value.copy(uploadSuccess = false)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Get path to rendered page bitmap for opening in slide editor (draw over).
     */
    fun savePageToTempFile(): String? {
        val bitmap = _uiState.value.pageBitmap ?: return null
        val file = File(context.cacheDir, "pdf_page_${_uiState.value.currentPage}.jpg")
        file.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
        return file.absolutePath
    }

    private fun renderPage(pageIndex: Int) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val renderer = pdfRenderer ?: return@withContext null
                val page = renderer.openPage(pageIndex)
                // Render at 2x, capped at 2048px to limit memory on large-format PDFs
                val scale = minOf(2, 2048 / maxOf(page.width, page.height).coerceAtLeast(1))
                val width = page.width * scale
                val height = page.height * scale
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bmp
            }
            // Recycle old bitmap before replacing
            val oldBitmap = _uiState.value.pageBitmap
            _uiState.value = _uiState.value.copy(
                currentPage = pageIndex,
                pageBitmap = bitmap,
            )
            oldBitmap?.recycle()
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdfRenderer?.close()
        fileDescriptor?.close()
    }
}
