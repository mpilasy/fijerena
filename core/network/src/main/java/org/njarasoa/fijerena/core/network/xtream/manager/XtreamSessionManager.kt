package org.njarasoa.fijerena.core.network.xtream.manager

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.resultOf
import org.njarasoa.fijerena.core.network.suspendResultOf
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import android.util.Log
import org.njarasoa.fijerena.core.player.api.XtreamApiService
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse

class XtreamSessionManager(
    private val context: Context,
    private val accountManager: AccountManager,
    private val onClearCache: suspend () -> Unit,
    private val streamOutputFormat: String = "m3u8",
    private val providerId: Long = 0L,
) {
    private companion object {
        const val TAG = "XtreamSession"
    }

    var apiService: XtreamApiService? = null
        private set

    suspend fun login(
        url: String,
        username: String,
        password: String,
        rememberMe: Boolean,
    ): Result<XtreamAuthResponse> =
        withContext(Dispatchers.IO) {
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

                // Auto-discover and add XMLTV source
                ensureXmltvSourceAdded(url, username, password)

                authResponse
            }
        }

    suspend fun restoreSession(): Result<XtreamAuthResponse> =
        withContext(Dispatchers.IO) {
            suspendResultOf {
                val credentials =
                    accountManager.getCredentials()
                        ?: throw Exception("No stored credentials found")

                val password =
                    credentials.password
                        ?: throw Exception("Password not stored. Please login again.")

                val service = XtreamApiService(credentials.url, credentials.username, password, streamOutputFormat)
                Log.d(TAG, "Attempting to authenticate with ${credentials.url}")
                val authResponse =
                    try {
                        service.authenticate()
                    } catch (e: Exception) {
                        Log.e(TAG, "Authentication failed for ${credentials.url}", e)
                        throw e
                    }

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
                    rememberMe = true,
                )

                // Store the API service for future use
                apiService = service

                // Auto-discover and add XMLTV source
                ensureXmltvSourceAdded(credentials.url, credentials.username, password)

                authResponse
            }
        }

    /**
     * Updates the provider URL without changing username/password.
     * Re-authenticates with the new URL and clears cached data.
     */
    suspend fun updateProviderUrl(newUrl: String): Result<XtreamAuthResponse> =
        withContext(Dispatchers.IO) {
            suspendResultOf {
                val credentials =
                    accountManager.getCredentials()
                        ?: throw Exception("No stored credentials found")

                val password =
                    credentials.password
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
                    rememberMe = true,
                )

                // Clear all cached data since it's from the old provider
                onClearCache()

                // Update the API service
                apiService = service

                // Auto-discover and add XMLTV source
                ensureXmltvSourceAdded(newUrl, credentials.username, password)

                authResponse
            }
        }

    private suspend fun ensureXmltvSourceAdded(
        baseUrl: String,
        user: String,
        pass: String,
    ) {
        try {
            val normalizedUrl = baseUrl.trimEnd('/')
            val xmltvUrl = "$normalizedUrl/xmltv.php?username=$user&password=$pass"
            val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()

            val existing = sourceDao.getSourceByUrl(xmltvUrl)
            if (existing == null) {
                val label = EpgFileManager.extractLabel(baseUrl) + " (Bulk)"
                sourceDao.insertSource(
                    EpgSourceEntity(
                        url = xmltvUrl,
                        label = label,
                        enabled = true,
                        providerId = if (providerId > 0) providerId else null,
                    ),
                )
                // Trigger an immediate background refresh if the index is empty
                EpgFileManager.getInstance(context).refreshOutdatedSources()
            }
        } catch (e: Exception) {
            android.util.Log.e("XtreamSessionManager", "Failed to auto-add XMLTV source", e)
        }
    }

    /**
     * Reinitialize the API service with new credentials (for provider switching).
     */
    suspend fun reinitialize(
        url: String,
        username: String,
        password: String,
    ): Result<XtreamAuthResponse> =
        withContext(Dispatchers.IO) {
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

    fun isAuthenticated(): Boolean = apiService != null && accountManager.hasStoredCredentials()

    suspend fun logout(): Result<Unit> =
        withContext(Dispatchers.IO) {
            resultOf {
                accountManager.clearCredentials()
                apiService = null
                onClearCache()
            }
        }

    fun getCurrentUrl(): String? = accountManager.getCredentials()?.url

    fun getCurrentUsername(): String? = accountManager.getCredentials()?.username

    fun getCurrentPassword(): String? = accountManager.getCredentials()?.password
}
