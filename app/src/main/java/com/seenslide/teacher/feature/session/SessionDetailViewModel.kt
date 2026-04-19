package com.seenslide.teacher.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.core.network.api.SessionApi
import com.seenslide.teacher.core.network.model.CreateTalkRequest
import com.seenslide.teacher.core.network.model.RenameSessionRequest
import com.seenslide.teacher.core.network.model.SessionResponse
import com.seenslide.teacher.core.network.model.TalkResponse
import com.seenslide.teacher.core.network.model.UpdateTalkRequest
import com.seenslide.teacher.core.network.auth.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionDetailUiState(
    val session: SessionResponse? = null,
    val talks: List<TalkResponse> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
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
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionApi: SessionApi,
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
                val sessionsResponse = sessionApi.listSessions()
                val session = sessionsResponse.sessions.find { it.sessionId == sessionId }

                val talksResponse = sessionApi.listTalks(sessionId)

                _uiState.value = SessionDetailUiState(
                    session = session,
                    talks = talksResponse.talks,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = SessionDetailUiState(
                    isLoading = false,
                    error = "Could not load class details",
                )
            }
        }
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
                sessionApi.renameSession(sessionId, RenameSessionRequest(newName))
                _uiState.value = _uiState.value.copy(
                    showRename = false,
                    session = _uiState.value.session?.copy(presenterName = newName),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showRename = false,
                    error = "Could not rename class",
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
                sessionApi.deleteSession(sessionId)
                onDeleted()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showDeleteConfirm = false,
                    error = "Could not delete class",
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
                val talk = sessionApi.createTalk(
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
                    error = "Could not create lesson",
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
                sessionApi.updateTalk(talk.talkId, UpdateTalkRequest(title = newTitle))
                _uiState.value = _uiState.value.copy(
                    showRenameTalk = null,
                    talks = _uiState.value.talks.map {
                        if (it.talkId == talk.talkId) it.copy(title = newTitle) else it
                    },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showRenameTalk = null,
                    error = "Could not rename lesson",
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
                sessionApi.deleteTalk(talk.talkId)
                _uiState.value = _uiState.value.copy(
                    showDeleteTalkConfirm = null,
                    talks = _uiState.value.talks.filter { it.talkId != talk.talkId },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showDeleteTalkConfirm = null,
                    error = "Could not delete lesson",
                )
            }
        }
    }
}
