package org.njarasoa.fijerena.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.player.api.XtreamApiService
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamStream

class XtreamRepository(
    private val accountManager: AccountManager
) {
    private var apiService: XtreamApiService? = null

    suspend fun login(
        url: String,
        username: String,
        password: String,
        rememberMe: Boolean
    ): Result<XtreamAuthResponse> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = XtreamApiService(url, username, password)
            val authResponse = service.authenticate()

            // Validate authentication response
            if (authResponse.userInfo?.auth != 1) {
                throw Exception("Authentication failed: Invalid credentials")
            }

            if (authResponse.userInfo?.status != "Active") {
                throw Exception("Account is not active: ${authResponse.userInfo?.status}")
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

            val service = XtreamApiService(credentials.url, credentials.username, password)
            val authResponse = service.authenticate()

            // Validate authentication response
            if (authResponse.userInfo?.auth != 1) {
                accountManager.clearCredentials()
                throw Exception("Stored credentials are invalid")
            }

            if (authResponse.userInfo?.status != "Active") {
                accountManager.clearCredentials()
                throw Exception("Account is not active: ${authResponse.userInfo?.status}")
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

    suspend fun getCategories(): Result<List<XtreamCategory>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = apiService
                ?: throw Exception("Not authenticated. Please login first.")
            service.getCategories()
        }
    }

    suspend fun getStreams(categoryId: String): Result<List<XtreamStream>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = apiService
                ?: throw Exception("Not authenticated. Please login first.")
            service.getStreams(categoryId)
        }
    }

    fun buildStreamUrl(streamId: Int): Result<String> = resultOf {
        val service = apiService
            ?: throw Exception("Not authenticated. Please login first.")
        service.buildStreamUrl(streamId)
    }

    fun isAuthenticated(): Boolean {
        return apiService != null && accountManager.hasStoredCredentials()
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        resultOf {
            accountManager.clearCredentials()
            apiService = null
        }
    }

    fun getCurrentUrl(): String? {
        return accountManager.getCredentials()?.url
    }

    fun getCurrentUsername(): String? {
        return accountManager.getCredentials()?.username
    }
}
