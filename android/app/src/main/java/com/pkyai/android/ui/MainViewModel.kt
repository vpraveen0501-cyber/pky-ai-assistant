package com.pkyai.android.ui

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pkyai.android.data.repository.AuthRepository
import com.pkyai.android.data.repository.DataRepository
import com.pkyai.android.services.OnDeviceLLMService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    data object Loading : AuthState()
    data object Authenticated : AuthState()
    data object Unauthenticated : AuthState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val dataRepository: DataRepository,
    private val onDeviceLLM: OnDeviceLLMService,
    private val connectivityManager: ConnectivityManager
) : ViewModel() {

    companion object {
        private const val ALERT_POLL_INTERVAL_MS = 30_000L
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn

    private val _globalAlerts = MutableSharedFlow<String>()
    val globalAlerts: SharedFlow<String> = _globalAlerts

    fun checkAuth() {
        if (authRepository.hasToken()) {
            _authState.value = AuthState.Authenticated
            startAlertPolling()
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isLoggingIn.value = true
            _loginError.value = null
            val result = authRepository.login(email, password)
            result.fold(
                onSuccess = {
                    _authState.value = AuthState.Authenticated
                    startAlertPolling()
                },
                onFailure = { error ->
                    _loginError.value = error.message ?: "Login failed"
                    _authState.value = AuthState.Unauthenticated
                }
            )
            _isLoggingIn.value = false
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Hybrid Inference Router (Phase 2b)
    // ──────────────────────────────────────────────────────────────

    /**
     * Route a query to the appropriate inference tier:
     *   1. Screen context → Gemini Nano (on-device, TRAPS-compliant)
     *   2. Offline mode → MediaPipe Gemma-3 (on-device, no network)
     *   3. Short queries with Nano available → Gemini Nano (fast, private)
     *   4. Complex/long queries → Cloud via backend (full reasoning)
     *
     * @param query           The user's query text.
     * @param brainMode       Selected brain (general, coding, reasoning, etc.)
     * @param isScreenContext True when query involves AssistStructure screen data.
     * @param screenContext   Extracted screen text (only used when isScreenContext=true).
     * @return AI response string.
     */
    suspend fun processQuery(
        query: String,
        brainMode: String = "general",
        isScreenContext: Boolean = false,
        screenContext: String = ""
    ): String {
        val isOffline = !isNetworkAvailable()
        val tier = onDeviceLLM.selectTier(
            query = query,
            isOffline = isOffline,
            isScreenContext = isScreenContext
        )

        return when (tier) {
            OnDeviceLLMService.InferenceTier.GEMINI_NANO -> {
                if (isScreenContext) {
                    onDeviceLLM.processScreenContext(screenContext, query)
                } else {
                    onDeviceLLM.generateNano(query)
                }
            }
            OnDeviceLLMService.InferenceTier.MEDIAPIPE -> {
                onDeviceLLM.generateOffline(query)
            }
            OnDeviceLLMService.InferenceTier.CLOUD -> {
                // Delegate to backend via VoiceService/DataRepository
                // Voice interactions go through VoiceService; text queries use DataRepository.
                val result = dataRepository.chat(query, brainMode)
                result.getOrElse { "I'm having trouble connecting. Please try again." }
            }
            OnDeviceLLMService.InferenceTier.UNAVAILABLE -> {
                "AI is unavailable — no network and no offline model installed."
            }
        }
    }

    /** Returns true when the device has an active internet connection. */
    private fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun startAlertPolling() {
        viewModelScope.launch {
            while (isActive) {
                val result = dataRepository.getAlerts()
                if (result.isSuccess) {
                    val alerts = result.getOrNull()
                    val alertMessage = alerts?.get("user_alert") as? String
                        ?: alerts?.get("global_alert") as? String

                    if (alertMessage != null) {
                        _globalAlerts.emit(alertMessage)
                    }
                }
                delay(ALERT_POLL_INTERVAL_MS)
            }
        }
    }
}
