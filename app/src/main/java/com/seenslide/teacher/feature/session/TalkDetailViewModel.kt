package com.seenslide.teacher.feature.session

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import com.seenslide.teacher.BuildConfig
import com.seenslide.teacher.core.data.SessionRepository
import com.seenslide.teacher.core.data.SlideQueuedLocallyException
import com.seenslide.teacher.core.data.SlideRepository
import com.seenslide.teacher.core.data.SlideUploadQueue
import com.seenslide.teacher.core.data.UploadQueueEvent
import com.seenslide.teacher.core.network.NetworkMonitor
import com.seenslide.teacher.core.ui.ErrorClassifier
import com.seenslide.teacher.core.network.api.SlideApi
import com.seenslide.teacher.core.network.model.SlideInfo
import com.seenslide.teacher.core.network.model.UpdateTalkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TalkDetailUiState(
    val slides: List<SlideInfo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val talkTitle: String = "",
    val viewingSlide: SlideInfo? = null,
    val slideMenuTarget: SlideInfo? = null,
    val showRenameTalk: Boolean = false,
    val renameTalkText: String = "",
    val showDeleteTalkConfirm: Boolean = false,
    val showDeleteSlideConfirm: SlideInfo? = null,
    val isOffline: Boolean = false,
    val pendingUploadCount: Int = 0,
    val uploadCompletedSlide: Int? = null,
    val galleryUploadProgress: GalleryUploadProgress? = null,
    val imageCacheBust: Long = 0L,
)

data class GalleryUploadProgress(
    val current: Int,
    val total: Int,
)

@HiltViewModel
class TalkDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val slideRepository: SlideRepository,
    private val sessionRepository: SessionRepository,
    private val slideApi: SlideApi,
    private val imageLoader: ImageLoader,
    private val networkMonitor: NetworkMonitor,
    private val uploadQueue: SlideUploadQueue,
    private val errorClassifier: ErrorClassifier,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""
    val talkId: String = savedStateHandle["talkId"] ?: ""

    private val _uiState = MutableStateFlow(TalkDetailUiState())
    val uiState: StateFlow<TalkDetailUiState> = _uiState

    private var wasOffline = false

    init {
        loadTalkAndSlides()
        networkMonitor.isOnline
            .onEach { online ->
                val comingBackOnline = wasOffline && online
                wasOffline = !online
                _uiState.value = _uiState.value.copy(isOffline = !online)
                if (comingBackOnline) loadTalkAndSlides()
            }
            .launchIn(viewModelScope)
        uploadQueue.pendingCount
            .onEach { count -> _uiState.value = _uiState.value.copy(pendingUploadCount = count) }
            .launchIn(viewModelScope)
        uploadQueue.lastEvent
            .onEach { event ->
                if (event is UploadQueueEvent.Uploaded) {
                    _uiState.value = _uiState.value.copy(uploadCompletedSlide = event.slideNumber)
                }
            }
            .launchIn(viewModelScope)
    }

    fun dismissUploadCompleted() {
        _uiState.value = _uiState.value.copy(uploadCompletedSlide = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun loadTalkAndSlides() {
        viewModelScope.launch {
            val currentCacheBust = _uiState.value.imageCacheBust
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Show cached data immediately
            val cachedTalk = sessionRepository.getTalk(talkId)
            val cachedSlides = slideRepository.getSlides(talkId)
            if (cachedTalk != null || cachedSlides.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    slides = cachedSlides,
                    isLoading = true,
                    talkTitle = cachedTalk?.title ?: "",
                )
                prefetchSlideImages(cachedSlides)
            }

            try {
                val talks = sessionRepository.refreshTalks(sessionId)
                val talk = talks.find { it.talkId == talkId }
                val slides = slideRepository.getSlides(talkId)
                _uiState.value = _uiState.value.copy(
                    slides = slides,
                    isLoading = false,
                    talkTitle = talk?.title ?: cachedTalk?.title ?: "",
                    imageCacheBust = if (currentCacheBust > 0L) System.currentTimeMillis() else 0L,
                )
                prefetchSlideImages(slides)
            } catch (e: Exception) {
                if (cachedSlides.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorClassifier.classify(e))
                }
            }
        }
    }

    fun slideImageUrl(slideNumber: Int, cacheBust: Long = 0L): String {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        return "$base/api/cloud/talk/$talkId/slides/$slideNumber/thumbnail?v=$cacheBust"
    }

    fun slideFullImageUrl(slideNumber: Int, cacheBust: Long = 0L): String {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        return "$base/api/cloud/talk/$talkId/slides/$slideNumber?v=$cacheBust"
    }

    /** Pre-fetch slide thumbnails and full images into Coil disk cache for offline viewing. */
    private fun prefetchSlideImages(slides: List<SlideInfo>) {
        for (slide in slides) {
            imageLoader.enqueue(
                ImageRequest.Builder(appContext)
                    .data(slideImageUrl(slide.slideNumber))
                    .build()
            )
            imageLoader.enqueue(
                ImageRequest.Builder(appContext)
                    .data(slideFullImageUrl(slide.slideNumber))
                    .build()
            )
        }
    }

    // --- Slide viewer ---

    fun viewSlide(slide: SlideInfo) {
        _uiState.value = _uiState.value.copy(viewingSlide = slide)
    }

    fun dismissSlideViewer() {
        _uiState.value = _uiState.value.copy(viewingSlide = null)
    }

    // --- Slide actions ---

    fun showSlideMenu(slide: SlideInfo) {
        _uiState.value = _uiState.value.copy(slideMenuTarget = slide)
    }

    fun dismissSlideMenu() {
        _uiState.value = _uiState.value.copy(slideMenuTarget = null)
    }

    fun showDeleteSlideConfirm(slide: SlideInfo) {
        _uiState.value = _uiState.value.copy(
            slideMenuTarget = null,
            showDeleteSlideConfirm = slide,
        )
    }

    fun dismissDeleteSlideConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteSlideConfirm = null)
    }

    fun deleteSlide() {
        val slide = _uiState.value.showDeleteSlideConfirm ?: return
        val slideId = slide.slideId ?: return
        viewModelScope.launch {
            try {
                sessionRepository.deleteSlide(sessionId, slideId)
                _uiState.value = _uiState.value.copy(
                    showDeleteSlideConfirm = null,
                    slides = _uiState.value.slides.filter { it.slideId != slideId },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showDeleteSlideConfirm = null,
                    error = errorClassifier.classify(e),
                )
            }
        }
    }

    // --- Talk rename/delete from within talk detail ---

    fun showRenameTalkDialog() {
        _uiState.value = _uiState.value.copy(
            showRenameTalk = true,
            renameTalkText = _uiState.value.talkTitle,
        )
    }

    fun dismissRenameTalkDialog() {
        _uiState.value = _uiState.value.copy(showRenameTalk = false)
    }

    fun onRenameTalkTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(renameTalkText = text)
    }

    fun submitRenameTalk() {
        val newTitle = _uiState.value.renameTalkText.trim()
        if (newTitle.isBlank()) return

        viewModelScope.launch {
            try {
                sessionRepository.updateTalk(talkId, UpdateTalkRequest(title = newTitle))
                _uiState.value = _uiState.value.copy(
                    showRenameTalk = false,
                    talkTitle = newTitle,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showRenameTalk = false,
                    error = errorClassifier.classify(e),
                )
            }
        }
    }

    fun showDeleteTalkConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteTalkConfirm = true)
    }

    fun dismissDeleteTalkConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteTalkConfirm = false)
    }

    fun deleteTalk(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                sessionRepository.deleteTalk(talkId)
                onDeleted()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showDeleteTalkConfirm = false,
                    error = errorClassifier.classify(e),
                )
            }
        }
    }

    fun addUploadedSlide(slideNumber: Int, slideId: String?) {
        val newSlide = SlideInfo(
            slideNumber = slideNumber,
            slideId = slideId,
        )
        // Replace existing slide at same number, or add new
        val existing = _uiState.value.slides.toMutableList()
        val idx = existing.indexOfFirst { it.slideNumber == slideNumber }
        if (idx >= 0) {
            existing[idx] = newSlide
        } else {
            existing.add(newSlide)
        }
        _uiState.value = _uiState.value.copy(
            slides = existing.distinctBy { it.slideId ?: "slide-${it.slideNumber}" }
                .sortedBy { it.slideNumber },
        )
    }

    // --- Gallery import ---

    fun uploadGalleryImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val total = uris.size
            _uiState.value = _uiState.value.copy(
                galleryUploadProgress = GalleryUploadProgress(current = 0, total = total),
            )
            for ((index, uri) in uris.withIndex()) {
                _uiState.value = _uiState.value.copy(
                    galleryUploadProgress = GalleryUploadProgress(current = index + 1, total = total),
                )
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        val input = appContext.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Cannot read image")
                        val bitmap = BitmapFactory.decodeStream(input)
                        input.close()
                        val out = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        bitmap.recycle()
                        out.toByteArray()
                    }
                    val response = slideRepository.uploadSlide(sessionId, talkId, bytes)
                    val slideNumber = response.slideNumber ?: continue
                    addUploadedSlide(slideNumber, response.slideId)
                } catch (e: SlideQueuedLocallyException) {
                    addUploadedSlide(e.slideNumber, null)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        galleryUploadProgress = null,
                        error = errorClassifier.classify(e),
                    )
                    return@launch
                }
            }
            _uiState.value = _uiState.value.copy(galleryUploadProgress = null)
        }
    }

    // --- Slide reorder ---

    fun moveSlideUp(slide: SlideInfo) {
        val slides = _uiState.value.slides
        val idx = slides.indexOfFirst { it.slideNumber == slide.slideNumber }
        if (idx <= 0) return
        val above = slides[idx - 1]
        swapSlides(slide.slideNumber, above.slideNumber)
    }

    fun moveSlideDown(slide: SlideInfo) {
        val slides = _uiState.value.slides
        val idx = slides.indexOfFirst { it.slideNumber == slide.slideNumber }
        if (idx < 0 || idx >= slides.size - 1) return
        val below = slides[idx + 1]
        swapSlides(slide.slideNumber, below.slideNumber)
    }

    private fun swapSlides(slideA: Int, slideB: Int) {
        viewModelScope.launch {
            try {
                slideApi.swapSlides(talkId, slideA, slideB)
                // Swap locally — server already swapped both DB records and files
                val updated = _uiState.value.slides.map { slide ->
                    when (slide.slideNumber) {
                        slideA -> slide.copy(slideNumber = slideB)
                        slideB -> slide.copy(slideNumber = slideA)
                        else -> slide
                    }
                }.sortedBy { it.slideNumber }
                _uiState.value = _uiState.value.copy(
                    slides = updated,
                    imageCacheBust = System.currentTimeMillis(),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = errorClassifier.classify(e))
            }
        }
    }
}
