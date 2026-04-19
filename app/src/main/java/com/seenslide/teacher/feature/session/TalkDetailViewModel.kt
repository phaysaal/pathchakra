package com.seenslide.teacher.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.BuildConfig
import com.seenslide.teacher.core.data.SlideRepository
import com.seenslide.teacher.core.network.api.SessionApi
import com.seenslide.teacher.core.network.model.SlideInfo
import com.seenslide.teacher.core.network.model.UpdateTalkRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
)

@HiltViewModel
class TalkDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val slideRepository: SlideRepository,
    private val sessionApi: SessionApi,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""
    val talkId: String = savedStateHandle["talkId"] ?: ""

    private val _uiState = MutableStateFlow(TalkDetailUiState())
    val uiState: StateFlow<TalkDetailUiState> = _uiState

    init {
        loadTalkAndSlides()
    }

    fun loadTalkAndSlides() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Load talk title
                val talksResponse = sessionApi.listTalks(sessionId)
                val talk = talksResponse.talks.find { it.talkId == talkId }

                val slides = slideRepository.getSlides(talkId)
                _uiState.value = TalkDetailUiState(
                    slides = slides,
                    isLoading = false,
                    talkTitle = talk?.title ?: "",
                )
            } catch (e: Exception) {
                _uiState.value = TalkDetailUiState(isLoading = false, error = "Could not load slides")
            }
        }
    }

    fun slideImageUrl(slideNumber: Int): String {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        return "$base/api/cloud/talk/$talkId/slides/$slideNumber/thumbnail"
    }

    fun slideFullImageUrl(slideNumber: Int): String {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        return "$base/api/cloud/slides/$sessionId/$slideNumber"
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
                sessionApi.deleteSlide(sessionId, slideId)
                _uiState.value = _uiState.value.copy(
                    showDeleteSlideConfirm = null,
                    slides = _uiState.value.slides.filter { it.slideId != slideId },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showDeleteSlideConfirm = null,
                    error = "Could not delete slide",
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
                sessionApi.updateTalk(talkId, UpdateTalkRequest(title = newTitle))
                _uiState.value = _uiState.value.copy(
                    showRenameTalk = false,
                    talkTitle = newTitle,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showRenameTalk = false,
                    error = "Could not rename lesson",
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
                sessionApi.deleteTalk(talkId)
                onDeleted()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showDeleteTalkConfirm = false,
                    error = "Could not delete lesson",
                )
            }
        }
    }
}
