package com.seenslide.teacher.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.BuildConfig
import com.seenslide.teacher.core.data.SessionRepository
import com.seenslide.teacher.core.data.SlideRepository
import com.seenslide.teacher.core.network.NetworkMonitor
import com.seenslide.teacher.core.ui.ErrorClassifier
import com.seenslide.teacher.core.network.model.CreateTalkRequest
import com.seenslide.teacher.core.network.model.SessionResponse
import com.seenslide.teacher.core.network.model.TalkResponse
import com.seenslide.teacher.core.network.model.UpdateTalkRequest
import com.seenslide.teacher.core.network.auth.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

data class SessionDetailUiState(
    val session: SessionResponse? = null,
    val talks: List<TalkResponse> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isOffline: Boolean = false,
    val showCreateTalk: Boolean = false,
    val newTalkTitle: String = "",
    val isCreatingTalk: Boolean = false,
    val showRename: Boolean = false,
    val renameText: String = "",
    val showQrCode: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val showTalkMenu: TalkResponse? = null,
    val showRenameTalk: TalkResponse? = null,
    val renameTalkText: String = "",
    val showDeleteTalkConfirm: TalkResponse? = null,
    val isDuplicating: Boolean = false,
    val duplicatedTalkTitle: String? = null,
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val slideRepository: SlideRepository,
    private val tokenStore: TokenStore,
    private val networkMonitor: NetworkMonitor,
    private val okHttpClient: OkHttpClient,
    private val errorClassifier: ErrorClassifier,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState

    private var wasOffline = false

    init {
        loadSession()
        networkMonitor.isOnline
            .onEach { online ->
                val comingBackOnline = wasOffline && online
                wasOffline = !online
                _uiState.value = _uiState.value.copy(isOffline = !online)
                if (comingBackOnline) loadSession()
            }
            .launchIn(viewModelScope)
    }

    fun loadSession() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Show cached data immediately
            val cachedSessions = sessionRepository.getSessions()
            val cachedSession = cachedSessions.find { it.sessionId == sessionId }
            val cachedTalks = sessionRepository.getTalks(sessionId)
            if (cachedSession != null || cachedTalks.isNotEmpty()) {
                _uiState.value = SessionDetailUiState(
                    session = cachedSession,
                    talks = cachedTalks,
                    isLoading = true,
                )
            }

            // Refresh from network
            try {
                val sessions = sessionRepository.refreshSessions()
                val session = sessions.find { it.sessionId == sessionId }
                val talks = sessionRepository.refreshTalks(sessionId)
                _uiState.value = SessionDetailUiState(
                    session = session,
                    talks = talks,
                    isLoading = false,
                )
            } catch (e: Exception) {
                if (cachedSession != null || cachedTalks.isNotEmpty()) {
                    // Have cache — just stop loading
                    _uiState.value = _uiState.value.copy(isLoading = false)
                } else {
                    _uiState.value = SessionDetailUiState(
                        isLoading = false,
                        error = errorClassifier.classify(e),
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // --- Rename Session ---

    fun showRenameDialog() {
        _uiState.value = _uiState.value.copy(
            showRename = true,
            renameText = _uiState.value.session?.presenterName ?: "",
        )
    }

    fun dismissRenameDialog() {
        _uiState.value = _uiState.value.copy(showRename = false)
    }

    fun onRenameTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(renameText = text)
    }

    fun submitRename() {
        val newName = _uiState.value.renameText.trim()
        if (newName.isBlank()) return

        viewModelScope.launch {
            try {
                sessionRepository.renameSession(sessionId, newName)
                _uiState.value = _uiState.value.copy(
                    showRename = false,
                    session = _uiState.value.session?.copy(presenterName = newName),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showRename = false,
                    error = errorClassifier.classify(e),
                )
            }
        }
    }

    // --- Delete Session ---

    fun showDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = true)
    }

    fun dismissDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = false)
    }

    fun deleteSession(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                sessionRepository.deleteSession(sessionId)
                onDeleted()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showDeleteConfirm = false,
                    error = errorClassifier.classify(e),
                )
            }
        }
    }

    // --- QR Code ---

    fun showQrCode() {
        _uiState.value = _uiState.value.copy(showQrCode = true)
    }

    fun dismissQrCode() {
        _uiState.value = _uiState.value.copy(showQrCode = false)
    }

    // --- Create Talk ---

    fun showCreateTalkDialog() {
        _uiState.value = _uiState.value.copy(showCreateTalk = true, newTalkTitle = "")
    }

    fun dismissCreateTalkDialog() {
        _uiState.value = _uiState.value.copy(showCreateTalk = false, newTalkTitle = "")
    }

    fun onNewTalkTitleChanged(title: String) {
        _uiState.value = _uiState.value.copy(newTalkTitle = title)
    }

    fun createTalk(onCreated: (talkId: String) -> Unit) {
        val title = _uiState.value.newTalkTitle.trim()
        if (title.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingTalk = true)
            try {
                val name = tokenStore.userName.first()
                val talk = sessionRepository.createTalk(
                    sessionId,
                    CreateTalkRequest(title = title, presenterName = name),
                )
                // Add new talk to list immediately
                _uiState.value = _uiState.value.copy(
                    isCreatingTalk = false,
                    showCreateTalk = false,
                    newTalkTitle = "",
                    talks = _uiState.value.talks + talk,
                )
                onCreated(talk.talkId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreatingTalk = false,
                    error = errorClassifier.classify(e),
                )
            }
        }
    }

    // --- Talk Actions (rename / delete) ---

    fun showTalkMenu(talk: TalkResponse) {
        _uiState.value = _uiState.value.copy(showTalkMenu = talk)
    }

    fun dismissTalkMenu() {
        _uiState.value = _uiState.value.copy(showTalkMenu = null)
    }

    fun showRenameTalkDialog(talk: TalkResponse) {
        _uiState.value = _uiState.value.copy(
            showTalkMenu = null,
            showRenameTalk = talk,
            renameTalkText = talk.title,
        )
    }

    fun dismissRenameTalkDialog() {
        _uiState.value = _uiState.value.copy(showRenameTalk = null)
    }

    fun onRenameTalkTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(renameTalkText = text)
    }

    fun submitRenameTalk() {
        val talk = _uiState.value.showRenameTalk ?: return
        val newTitle = _uiState.value.renameTalkText.trim()
        if (newTitle.isBlank()) return

        viewModelScope.launch {
            try {
                sessionRepository.updateTalk(talk.talkId, UpdateTalkRequest(title = newTitle))
                _uiState.value = _uiState.value.copy(
                    showRenameTalk = null,
                    talks = _uiState.value.talks.map {
                        if (it.talkId == talk.talkId) it.copy(title = newTitle) else it
                    },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showRenameTalk = null,
                    error = errorClassifier.classify(e),
                )
            }
        }
    }

    fun showDeleteTalkConfirm(talk: TalkResponse) {
        _uiState.value = _uiState.value.copy(
            showTalkMenu = null,
            showDeleteTalkConfirm = talk,
        )
    }

    fun dismissDeleteTalkConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteTalkConfirm = null)
    }

    fun deleteTalk() {
        val talk = _uiState.value.showDeleteTalkConfirm ?: return
        viewModelScope.launch {
            try {
                sessionRepository.deleteTalk(talk.talkId)
                _uiState.value = _uiState.value.copy(
                    showDeleteTalkConfirm = null,
                    talks = _uiState.value.talks.filter { it.talkId != talk.talkId },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showDeleteTalkConfirm = null,
                    error = errorClassifier.classify(e),
                )
            }
        }
    }

    fun upsertTalk(talkId: String, title: String, slideCount: Int) {
        val current = _uiState.value.talks
        val existing = current.find { it.talkId == talkId }
        val updated = if (existing != null) {
            current.map {
                if (it.talkId == talkId) it.copy(title = title, slideCount = slideCount) else it
            }
        } else {
            current + TalkResponse(
                talkId = talkId,
                sessionId = sessionId,
                title = title,
                slideCount = slideCount,
            )
        }
        _uiState.value = _uiState.value.copy(talks = updated)
    }

    fun removeTalkById(talkId: String) {
        _uiState.value = _uiState.value.copy(
            talks = _uiState.value.talks.filter { it.talkId != talkId },
        )
    }

    // --- Duplicate Talk ---

    fun dismissDuplicated() {
        _uiState.value = _uiState.value.copy(duplicatedTalkTitle = null)
    }

    fun duplicateTalk(talk: TalkResponse) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDuplicating = true)
            try {
                val name = tokenStore.userName.first()
                val newTalk = sessionRepository.createTalk(
                    sessionId,
                    CreateTalkRequest(title = "${talk.title} (copy)", presenterName = name),
                )

                // Copy slides: download each from source talk, upload to new talk
                val base = BuildConfig.API_BASE_URL.trimEnd('/')
                for (slideNum in 1..talk.slideCount) {
                    val imageBytes = withContext(Dispatchers.IO) {
                        val url = "$base/api/cloud/talk/${talk.talkId}/slides/$slideNum"
                        val request = Request.Builder().url(url).build()
                        val response = okHttpClient.newCall(request).execute()
                        response.use { it.body?.bytes() }
                    } ?: continue
                    try {
                        slideRepository.uploadSlide(sessionId, newTalk.talkId, imageBytes)
                    } catch (_: Exception) {
                        // SlideQueuedLocallyException or network error — continue with next
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isDuplicating = false,
                    duplicatedTalkTitle = newTalk.title,
                    talks = _uiState.value.talks + newTalk.copy(slideCount = talk.slideCount),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDuplicating = false,
                    error = errorClassifier.classify(e),
                )
            }
        }
    }
}
