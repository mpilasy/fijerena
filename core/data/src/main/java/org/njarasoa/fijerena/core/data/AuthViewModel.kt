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
 */
class AuthViewModel : ViewModel() {
    /**
     * Current authentication response.
     * Null if user is not authenticated.
     */
    private val _authResponse = MutableStateFlow<XtreamAuthResponse?>(null)
    val authResponse: StateFlow<XtreamAuthResponse?> = _authResponse.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    fun setAuthSession(
        response: XtreamAuthResponse,
        url: String,
    ) {
        _authResponse.value = response
        _serverUrl.value = url
    }

    fun isAuthenticated(): Boolean {
        val response = _authResponse.value
        return response != null &&
            response.userInfo.auth == 1 &&
            response.userInfo.status == "Active"
    }

    fun getUsername(): String? = _authResponse.value?.userInfo?.username

    fun getExpirationDate(): String? = _authResponse.value?.userInfo?.expDate

    fun isSessionExpired(): Boolean {
        val expDate = _authResponse.value?.userInfo?.expDate
        val isExpired =
            when {
                expDate.isNullOrEmpty() -> {
                    true
                }

                expDate.equals("Unlimited", ignoreCase = true) -> {
                    false
                }

                else -> {
                    val expirationTimestamp = expDate.toLongOrNull()
                    val currentTimestamp = System.currentTimeMillis() / 1000
                    expirationTimestamp == null || currentTimestamp > expirationTimestamp
                }
            }
        return isExpired
    }

    override fun onCleared() {
        super.onCleared()
    }
}
