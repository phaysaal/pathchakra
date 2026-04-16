package com.seenslide.teacher.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.core.network.api.SessionApi
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
)

@HiltViewModel
class CreateSessionViewModel @Inject constructor(
    private val sessionApi: SessionApi,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateSessionUiState())
    val uiState: StateFlow<CreateSessionUiState> = _uiState

    fun onClassNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(className = name, error = null)
    }

    fun createSession() {
        val name = _uiState.value.className.trim()
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter a class name")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val email = tokenStore.userEmail.first() ?: ""
                val response = sessionApi.createSession(
                    CreateSessionRequest(
                        presenterName = name,
                        presenterEmail = email,
                    ),
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    createdSessionId = response.sessionId,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Could not create class. Check your internet.",
                )
            }
        }
    }
}
