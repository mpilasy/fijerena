package org.njarasoa.fijerena.core.data

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse

/**
 * Shared authentication ViewModel that holds the current user session.
 *
 * This ViewModel is shared across both :mobile and :tv modules to maintain
 * consistent authentication state throughout the app lifecycle.
 *
 * Features:
 * - Holds authenticated user's XtreamAuthResponse
 * - Provides authentication status check
 * - Manages session lifecycle
 * - Can be accessed from any screen via shared ViewModel scope
 *
 * Usage:
 * ```kotlin
 * val authViewModel: AuthViewModel = viewModel()
 * val authResponse by authViewModel.authResponse.collectAsStateWithLifecycle()
 *
 * if (authResponse != null) {
 *     // User is authenticated
 * }
 * ```
 */
class AuthViewModel : ViewModel() {
    /**
     * Current authentication response.
     * Null if user is not authenticated.
     */
    private val _authResponse = MutableStateFlow<XtreamAuthResponse?>(null)
    val authResponse: StateFlow<XtreamAuthResponse?> = _authResponse.asStateFlow()

    /**
     * Server URL used for authenticated session.
     */
    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    /**
     * Sets the authenticated session.
     *
     * Call this after successful login to store the auth response
     * and make it available throughout the app.
     *
     * @param response The authentication response from Xtream API
     * @param url The server URL used for authentication
     */
    fun setAuthSession(
        response: XtreamAuthResponse,
        url: String,
    ) {
        _authResponse.value = response
        _serverUrl.value = url
    }

    /**
     * Clears the authenticated session.
     *
     * Call this on logout or session expiration.
     */
    fun clearAuthSession() {
        _authResponse.value = null
        _serverUrl.value = null
    }

    /**
     * Checks if the user is currently authenticated.
     *
     * @return true if auth response exists and user is active
     */
    fun isAuthenticated(): Boolean {
        val response = _authResponse.value
        return response != null &&
            response.userInfo.auth == 1 &&
            response.userInfo.status == "Active"
    }

    /**
     * Gets the username of the authenticated user.
     *
     * @return username or null if not authenticated
     */
    fun getUsername(): String? = _authResponse.value?.userInfo?.username

    /**
     * Gets the expiration date of the current session.
     *
     * @return expiration date string or null if not authenticated
     */
    fun getExpirationDate(): String? = _authResponse.value?.userInfo?.expDate

    /**
     * Checks if the session is expired.
     *
     * @return true if session is expired
     */
    fun isSessionExpired(): Boolean {
        val expDate = _authResponse.value?.userInfo?.expDate
        if (expDate.isNullOrEmpty()) return true
        if (expDate.equals("Unlimited", ignoreCase = true)) return false

        val expirationTimestamp = expDate.toLongOrNull() ?: return true
        val currentTimestamp = System.currentTimeMillis() / 1000
        return currentTimestamp > expirationTimestamp
    }

    override fun onCleared() {
        super.onCleared()
        // Could implement session cleanup here if needed
    }
}
