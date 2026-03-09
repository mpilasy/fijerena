package org.njarasoa.fijerena.core.network.xtream.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.resultOf
import org.njarasoa.fijerena.core.network.suspendResultOf
import org.njarasoa.fijerena.core.player.api.XtreamApiService
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse

class XtreamSessionManager(
    private val accountManager: AccountManager,
    private val onClearCache: suspend () -> Unit,
    private val streamOutputFormat: String = "m3u8"
) {
    var apiService: XtreamApiService? = null
        private set

    suspend fun login(
        url: String,
        username: String,
        password: String,
        rememberMe: Boolean
    ): Result<XtreamAuthResponse> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = XtreamApiService(url, username, password, streamOutputFormat)
            val authResponse = service.authenticate()

            // Validate authentication response
            if (authResponse.userInfo.auth != 1) {
                throw Exception("Authentication failed: Invalid credentials")
            }

            if (authResponse.userInfo.status != "Active") {
                throw Exception("Account is not active: ${authResponse.userInfo.status}")
            }

            // Save credentials
            accountManager.saveCredentials(url, username, password, authResponse, rememberMe)

            // Store the API service for future use
            apiService = service

            authResponse
        }
    }

    suspend fun restoreSession(): Result<XtreamAuthResponse> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val credentials = accountManager.getCredentials()
                ?: throw Exception("No stored credentials found")

            val password = credentials.password
                ?: throw Exception("Password not stored. Please login again.")

            val service = XtreamApiService(credentials.url, credentials.username, password, streamOutputFormat)
            val authResponse = service.authenticate()

            // Validate authentication response
            if (authResponse.userInfo.auth != 1) {
                accountManager.clearCredentials()
                throw Exception("Stored credentials are invalid")
            }

            if (authResponse.userInfo.status != "Active") {
                accountManager.clearCredentials()
                throw Exception("Account is not active: ${authResponse.userInfo.status}")
            }

            // Update stored auth response
            accountManager.saveCredentials(
                credentials.url,
                credentials.username,
                password,
                authResponse,
                rememberMe = true
            )

            // Store the API service for future use
            apiService = service

            authResponse
        }
    }

    /**
     * Updates the provider URL without changing username/password.
     * Re-authenticates with the new URL and clears cached data.
     */
    suspend fun updateProviderUrl(newUrl: String): Result<XtreamAuthResponse> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val credentials = accountManager.getCredentials()
                ?: throw Exception("No stored credentials found")

            val password = credentials.password
                ?: throw Exception("Password not stored. Please login again.")

            // Update URL in storage
            accountManager.updateUrl(newUrl)

            // Create new API service with updated URL
            val service = XtreamApiService(newUrl, credentials.username, password, streamOutputFormat)
            val authResponse = service.authenticate()

            // Validate authentication response
            if (authResponse.userInfo.auth != 1) {
                throw Exception("Authentication failed with new URL")
            }

            if (authResponse.userInfo.status != "Active") {
                throw Exception("Account is not active: ${authResponse.userInfo.status}")
            }

            // Save updated credentials with new URL
            accountManager.saveCredentials(
                newUrl,
                credentials.username,
                password,
                authResponse,
                rememberMe = true
            )

            // Clear all cached data since it's from the old provider
            onClearCache()

            // Update the API service
            apiService = service

            authResponse
        }
    }

    /**
     * Reinitialize the API service with new credentials (for provider switching).
     */
    suspend fun reinitialize(
        url: String,
        username: String,
        password: String
    ): Result<XtreamAuthResponse> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = XtreamApiService(url, username, password, streamOutputFormat)
            val authResponse = service.authenticate()

            if (authResponse.userInfo.auth != 1) {
                throw Exception("Authentication failed: Invalid credentials")
            }

            if (authResponse.userInfo.status != "Active") {
                throw Exception("Account is not active: ${authResponse.userInfo.status}")
            }

            apiService = service
            authResponse
        }
    }

    fun isAuthenticated(): Boolean {
        return apiService != null && accountManager.hasStoredCredentials()
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        resultOf {
            accountManager.clearCredentials()
            apiService = null
            onClearCache()
        }
    }

    fun getCurrentUrl(): String? {
        return accountManager.getCredentials()?.url
    }

    fun getCurrentUsername(): String? {
        return accountManager.getCredentials()?.username
    }

    fun getCurrentPassword(): String? {
        return accountManager.getCredentials()?.password
    }
}
