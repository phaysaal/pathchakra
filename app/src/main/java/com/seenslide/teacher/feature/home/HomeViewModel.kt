package com.seenslide.teacher.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.core.data.SessionRepository
import com.seenslide.teacher.core.data.SlideUploadQueue
import com.seenslide.teacher.core.data.SyncManager
import com.seenslide.teacher.core.network.NetworkMonitor
import com.seenslide.teacher.core.ui.ErrorClassifier
import com.seenslide.teacher.core.network.model.SessionResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val sessions: List<SessionResponse> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isOffline: Boolean = false,
    val pendingUploads: Int = 0,
    val pendingChanges: Int = 0,
    val isSyncing: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val networkMonitor: NetworkMonitor,
    private val errorClassifier: ErrorClassifier,
    private val uploadQueue: SlideUploadQueue,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private var wasOffline = false

    init {
        loadSessions()
        networkMonitor.isOnline
            .onEach { online ->
                val comingBackOnline = wasOffline && online
                wasOffline = !online
                _uiState.value = _uiState.value.copy(isOffline = !online)
                if (comingBackOnline) loadSessions()
            }
            .launchIn(viewModelScope)
        uploadQueue.pendingCount
            .onEach { count ->
                _uiState.value = _uiState.value.copy(pendingUploads = count)
            }
            .launchIn(viewModelScope)
        syncManager.syncState
            .onEach { state ->
                _uiState.value = _uiState.value.copy(
                    pendingChanges = state.pendingChanges,
                    isSyncing = state.isSyncing,
                )
            }
            .launchIn(viewModelScope)
    }

    fun retryUploads() {
        uploadQueue.drainQueue()
    }

    fun loadSessions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Show cached data immediately
            val cached = sessionRepository.getSessions()
            if (cached.isNotEmpty()) {
                _uiState.value = HomeUiState(sessions = cached, isLoading = true)
            }

            // Refresh from network
            try {
                val fresh = sessionRepository.refreshSessions()
                _uiState.value = HomeUiState(sessions = fresh, isLoading = false)
            } catch (e: Exception) {
                // If we have cached data, show it with no error
                if (cached.isNotEmpty()) {
                    _uiState.value = HomeUiState(sessions = cached, isLoading = false)
                } else {
                    _uiState.value = HomeUiState(isLoading = false, error = errorClassifier.classify(e))
                }
            }
        }
    }

    fun upsertSession(sessionId: String, presenterName: String) {
        val current = _uiState.value.sessions
        val existing = current.find { it.sessionId == sessionId }
        val updated = if (existing != null) {
            current.map {
                if (it.sessionId == sessionId) it.copy(presenterName = presenterName) else it
            }
        } else {
            listOf(
                SessionResponse(
                    sessionId = sessionId,
                    presenterName = presenterName,
                )
            ) + current
        }
        _uiState.value = _uiState.value.copy(sessions = updated)
    }

    fun removeSession(sessionId: String) {
        _uiState.value = _uiState.value.copy(
            sessions = _uiState.value.sessions.filter { it.sessionId != sessionId },
        )
    }
}
