package com.seenslide.teacher.feature.slide.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.core.data.SlideRepository
import com.seenslide.teacher.core.media.ImageEnhancer
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
    val isUploading: Boolean = false,
    val uploadSuccess: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PdfViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val slideRepository: SlideRepository,
    private val imageEnhancer: ImageEnhancer,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""
    val talkId: String? = savedStateHandle.get<String>("talkId")?.takeIf { it != "none" }

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
                _uiState.value = PdfUiState(error = "Could not open PDF file")
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

    /**
     * Save the current page as a slide (full page).
     */
    fun savePageAsSlide() {
        val bitmap = _uiState.value.pageBitmap ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, error = null)
            try {
                val bytes = withContext(Dispatchers.Default) {
                    imageEnhancer.compressToBytes(bitmap, quality = 90)
                }
                slideRepository.uploadSlide(sessionId, talkId, bytes)
                _uiState.value = _uiState.value.copy(isUploading = false, uploadSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isUploading = false, error = "Upload failed")
            }
        }
    }

    fun onUploadSuccessHandled() {
        _uiState.value = _uiState.value.copy(uploadSuccess = false)
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
                // Render at 2x for crisp display
                val width = page.width * 2
                val height = page.height * 2
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bmp
            }
            _uiState.value = _uiState.value.copy(
                currentPage = pageIndex,
                pageBitmap = bitmap,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdfRenderer?.close()
        fileDescriptor?.close()
    }
}
