package com.seenslide.teacher.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.seenslide.teacher.core.auth.DeviceHelper
import com.seenslide.teacher.core.auth.PinStore
import com.seenslide.teacher.core.auth.SimDetector
import com.seenslide.teacher.core.network.api.TeacherAuthApi
import com.seenslide.teacher.core.network.auth.TokenStore
import com.seenslide.teacher.core.network.model.RegisterDeviceRequest
import com.seenslide.teacher.core.network.model.UpdatePhoneRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val phone: String = "",
    val name: String = "",
    val pin: String = "",
    val confirmPin: String = "",
    val step: LoginStep = LoginStep.LOADING,
    val isLoading: Boolean = false,
    val error: String? = null,
)

enum class LoginStep {
    LOADING,
    PHONE_INPUT,
    PIN_SETUP,
    PIN_LOCK,
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val teacherAuthApi: TeacherAuthApi,
    private val tokenStore: TokenStore,
    private val pinStore: PinStore,
    private val deviceHelper: DeviceHelper,
    private val simDetector: SimDetector,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    init {
        viewModelScope.launch {
            val loggedIn = tokenStore.isLoggedIn.first()
            if (loggedIn && pinStore.isRegistered()) {
                // Check for SIM change
                checkSimChange()
                _uiState.value = LoginUiState(step = LoginStep.PIN_LOCK)
            } else {
                _uiState.value = LoginUiState(step = LoginStep.PHONE_INPUT)
            }
        }
    }

    private fun checkSimChange() {
        viewModelScope.launch {
            try {
                val simNumber = simDetector.getSimPhoneNumber() ?: return@launch
                val storedPhone = pinStore.getStoredPhone() ?: return@launch

                if (simNumber != storedPhone) {
                    Log.i("LoginVM", "SIM change detected: $storedPhone -> $simNumber")
                    teacherAuthApi.updatePhone(
                        UpdatePhoneRequest(
                            oldPhone = storedPhone,
                            newPhone = simNumber,
                            deviceId = deviceHelper.getDeviceId(),
                        ),
                    )
                    pinStore.savePhone(simNumber)
                    Log.i("LoginVM", "Phone number updated on server")
                }
            } catch (e: Exception) {
                Log.w("LoginVM", "SIM change check failed (non-critical)", e)
            }
        }
    }

    fun onPhoneChanged(phone: String) {
        _uiState.value = _uiState.value.copy(phone = phone, error = null)
    }

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun onPinChanged(pin: String) {
        if (pin.length <= 4) {
            _uiState.value = _uiState.value.copy(pin = pin, error = null)
        }
    }

    fun onConfirmPinChanged(pin: String) {
        if (pin.length <= 4) {
            _uiState.value = _uiState.value.copy(confirmPin = pin, error = null)
        }
    }

    fun submitPhone() {
        val phone = _uiState.value.phone.trim()
        if (phone.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Please enter a valid phone number")
            return
        }
        _uiState.value = _uiState.value.copy(step = LoginStep.PIN_SETUP, pin = "", confirmPin = "")
    }

    fun submitPin(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.pin.length != 4) {
            _uiState.value = state.copy(error = "PIN must be 4 digits")
            return
        }
        if (state.pin != state.confirmPin) {
            _uiState.value = state.copy(error = "PINs do not match", confirmPin = "")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val pinHash = pinStore.hashPin(state.pin)
                val response = teacherAuthApi.registerDevice(
                    RegisterDeviceRequest(
                        phoneNumber = state.phone.trim(),
                        deviceId = deviceHelper.getDeviceId(),
                        pinHash = pinHash,
                        fullName = state.name.trim().ifBlank { null },
                    ),
                )

                // Save locally
                pinStore.savePin(state.pin)
                pinStore.savePhone(state.phone.trim())
                tokenStore.saveAuth(
                    token = response.sessionToken,
                    email = "${state.phone.trim()}@teacher.pathchakra.app",
                    userId = response.userId,
                    name = response.fullName,
                )

                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Registration failed. Check your internet.",
                )
            }
        }
    }

    fun verifyPin(onSuccess: () -> Unit) {
        val pin = _uiState.value.pin
        if (pinStore.verifyPin(pin)) {
            onSuccess()
        } else {
            _uiState.value = _uiState.value.copy(pin = "", error = "Wrong PIN")
        }
    }

    fun goBackToPhone() {
        _uiState.value = _uiState.value.copy(step = LoginStep.PHONE_INPUT, pin = "", confirmPin = "")
    }
}
