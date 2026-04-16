package com.seenslide.teacher.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.BuildConfig
import com.seenslide.teacher.core.network.api.SessionApi
import com.seenslide.teacher.core.network.model.SessionResponse
import com.seenslide.teacher.core.network.model.SlideInfo
import com.seenslide.teacher.core.network.model.StartTalkRequest
import com.seenslide.teacher.core.data.SlideRepository
import com.seenslide.teacher.core.network.auth.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionDetailUiState(
    val session: SessionResponse? = null,
    val activeTalkId: String? = null,
    val slides: List<SlideInfo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionApi: SessionApi,
    private val slideRepository: SlideRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState

    init {
        loadSession()
    }

    fun loadSession() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val sessions = sessionApi.listSessions()
                val session = sessions.find { it.sessionId == sessionId }
                val talkId = session?.activeTalkId

                val slides = if (talkId != null) {
                    try { slideRepository.getSlides(talkId) } catch (_: Exception) { emptyList() }
                } else {
                    emptyList()
                }

                _uiState.value = SessionDetailUiState(
                    session = session,
                    activeTalkId = talkId,
                    slides = slides,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = SessionDetailUiState(isLoading = false, error = "Could not load class details")
            }
        }
    }

    fun ensureTalkAndGetId(onReady: (talkId: String) -> Unit) {
        val existingTalkId = _uiState.value.activeTalkId
        if (existingTalkId != null) {
            onReady(existingTalkId)
            return
        }

        viewModelScope.launch {
            try {
                val email = tokenStore.userEmail.first() ?: "teacher"
                val talk = sessionApi.startTalk(
                    sessionId,
                    StartTalkRequest(title = "Class", presenterName = email),
                )
                _uiState.value = _uiState.value.copy(activeTalkId = talk.talkId)
                onReady(talk.talkId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Could not start talk")
            }
        }
    }

    fun slideImageUrl(slideNumber: Int): String {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        return "$base/api/cloud/slides/$sessionId/$slideNumber/thumbnail"
    }
}
