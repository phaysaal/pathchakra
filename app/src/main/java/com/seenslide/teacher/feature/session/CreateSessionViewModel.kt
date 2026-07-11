package com.seenslide.teacher.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.R
import com.seenslide.teacher.core.data.SessionRepository
import com.seenslide.teacher.core.ui.ErrorClassifier
import com.seenslide.teacher.core.network.auth.TokenStore
import com.seenslide.teacher.core.network.model.CreateSessionRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateSessionUiState(
    val className: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val createdSessionId: String? = null,
    val createdSessionName: String? = null,
)

@HiltViewModel
class CreateSessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val tokenStore: TokenStore,
    private val errorClassifier: ErrorClassifier,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateSessionUiState())
    val uiState: StateFlow<CreateSessionUiState> = _uiState

    fun onClassNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(className = name, error = null)
    }

    fun createSession() {
        val name = _uiState.value.className.trim()
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(error = errorClassifier.getString(R.string.error_empty_class_name))
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val email = tokenStore.userEmail.first() ?: ""
                val response = sessionRepository.createSession(
                    CreateSessionRequest(
                        presenterName = name,
                        presenterEmail = email,
                    ),
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    createdSessionId = response.sessionId,
                    createdSessionName = response.presenterName ?: name,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = errorClassifier.classify(e),
                )
            }
        }
    }
}
