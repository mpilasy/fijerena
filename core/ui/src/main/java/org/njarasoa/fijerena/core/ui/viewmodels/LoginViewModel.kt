package org.njarasoa.fijerena.core.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse

/**
 * LoginViewModel manages authentication state for Xtream IPTV login.
 *
 * Handles:
 * - User authentication via XtreamRepository
 * - Encrypted credential storage via AccountManager
 * - Loading, Success, and Error states for D-pad friendly UI feedback
 *
 * @param repository Repository for Xtream API operations and credential management.
 */
class LoginViewModel(
    private val repository: XtreamRepository,
) : ViewModel() {
    /**
     * UI state sealed class representing all possible login states.
     */
    sealed class UiState {
        /**
         * Initial idle state before login attempt.
         */
        data object Idle : UiState()

        /**
         * Loading state during authentication request.
         */
        data object Loading : UiState()

        /**
         * Success state with authentication response data.
         */
        data class Success(
            val authResponse: XtreamAuthResponse,
        ) : UiState()

        /**
         * Error state with error message.
         */
        data class Error(
            val message: String,
        ) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Authenticates user with Xtream API.
     *
     * Flow:
     * 1. Sets state to Loading
     * 2. Calls repository.login() which handles authentication and credential storage
     * 3. On success: sets Success state
     * 4. On failure: sets Error state with user-friendly message
     *
     * @param url Base URL for Xtream server (e.g., "http://example.com:8080")
     * @param username Xtream account username
     * @param password Xtream account password
     * @param rememberMe Whether to persist credentials for auto-login
     */
    fun login(
        url: String,
        username: String,
        password: String,
        rememberMe: Boolean = false,
    ) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            when (val result = repository.login(url, username, password, rememberMe)) {
                is Result.Success -> {
                    _uiState.value = UiState.Success(result.data)
                }
                is Result.Error -> {
                    // Handle network errors, timeouts, invalid JSON, etc.
                    val errorMessage =
                        when {
                            result.message?.contains("timeout", ignoreCase = true) == true ->
                                "Connection timeout. Check your network or server URL."
                            result.message?.contains("401") == true || result.message?.contains("Unauthorized") == true ->
                                "Invalid username or password."
                            result.message?.contains("404") == true || result.message?.contains("Not Found") == true ->
                                "Server not found. Check your URL."
                            result.message?.contains("Invalid credentials") == true ->
                                "Invalid username or password."
                            result.message?.contains("not active", ignoreCase = true) == true ->
                                result.message ?: "Account is not active"
                            else ->
                                "Login failed: ${result.message ?: "Unknown error"}"
                        }
                    _uiState.value = UiState.Error(errorMessage)
                }
            }
        }
    }

    /**
     * Restores session from stored credentials (auto-login).
     */
    fun restoreSession() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            when (val result = repository.restoreSession()) {
                is Result.Success -> {
                    _uiState.value = UiState.Success(result.data)
                }
                is Result.Error -> {
                    _uiState.value = UiState.Idle
                }
            }
        }
    }

    /**
     * Gets the current server URL from stored credentials.
     */
    fun getServerUrl(): String = repository.getCurrentUrl() ?: ""

    /**
     * Resets UI state to Idle.
     * Call this when user navigates away from login screen or wants to retry.
     */
    fun resetState() {
        _uiState.value = UiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        // Cleanup API service if needed
        // Note: For login flow, we create a new service per attempt
        // so no persistent cleanup needed here
    }
}
