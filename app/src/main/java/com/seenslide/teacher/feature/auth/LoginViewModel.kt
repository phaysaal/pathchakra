package com.seenslide.teacher.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.core.network.api.AuthApi
import com.seenslide.teacher.core.network.auth.TokenStore
import com.seenslide.teacher.core.network.model.MagicLinkRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val linkSent: Boolean = false,
    val error: String? = null,
    val isAlreadyLoggedIn: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    init {
        viewModelScope.launch {
            val loggedIn = tokenStore.isLoggedIn.first()
            if (loggedIn) {
                _uiState.value = _uiState.value.copy(isAlreadyLoggedIn = true)
            }
        }
    }

    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }

    fun requestMagicLink() {
        val email = _uiState.value.email.trim()
        if (email.isBlank() || !email.contains("@")) {
            _uiState.value = _uiState.value.copy(error = "Please enter a valid email")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                authApi.requestMagicLink(MagicLinkRequest(email = email))
                _uiState.value = _uiState.value.copy(isLoading = false, linkSent = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Could not send login link. Check your internet.",
                )
            }
        }
    }

    fun handleDeepLinkToken(token: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = authApi.verifyToken(
                    com.seenslide.teacher.core.network.model.VerifyTokenRequest(token),
                )
                tokenStore.saveAuth(
                    token = response.sessionToken,
                    email = response.email,
                    userId = response.userId,
                    name = response.fullName,
                )
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Login failed. Please try again.",
                )
            }
        }
    }
}
