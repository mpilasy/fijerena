package org.njarasoa.fijerena.core.network.xtream.manager

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // Guards every method that reads-then-replaces apiService. Without it, two callers hitting
    // connect() around the same time (e.g. AppContainer's initial connect racing a screen's own
    // CategoryViewModel.connect(), both against the one cached XtreamMediaProvider/session per
    // provider) can each build their own XtreamApiService and authenticate concurrently; whichever
    // finishes second closes the first one's client via replaceApiService() while the first
    // caller is still using it to fetch data, failing that in-flight request with "executor
    // rejected" against its own now-closed Dispatcher. Serializing here means the second caller
    // simply waits and reuses the session the first one already finished setting up.
    private val sessionMutex = Mutex()

    suspend fun login(
        url: String,
        username: String,
        password: String,
        rememberMe: Boolean,
    ): Result<XtreamAuthResponse> =
        withContext(Dispatchers.IO) {
            sessionMutex.withLock {
                suspendResultOf {
                    var serviceAssigned = false
                    val service = XtreamApiService(url, username, password, streamOutputFormat)
                    try {
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

                        // Store the API service for future use, closing whatever it replaces so its
                        // HttpClient (own Dispatcher + ConnectionPool, see XtreamApiService) doesn't leak.
                        replaceApiService(service)
                        serviceAssigned = true

                        // Auto-discover and add XMLTV source
                        ensureXmltvSourceAdded(url, username, password)

                        authResponse
                    } finally {
                        if (!serviceAssigned) {
                            service.close()
                        }
                    }
                }
            }
        }

    suspend fun restoreSession(): Result<XtreamAuthResponse> =
        withContext(Dispatchers.IO) {
            sessionMutex.withLock {
                suspendResultOf {
                    val credentials =
                        accountManager.getCredentials()
                            ?: throw Exception("No stored credentials found")

                    val password =
                        credentials.password
                            ?: throw Exception("Password not stored. Please login again.")

                    var serviceAssigned = false
                    val service = XtreamApiService(credentials.url, credentials.username, password, streamOutputFormat)
                    try {
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
                        replaceApiService(service)
                        serviceAssigned = true

                        // Auto-discover and add XMLTV source
                        ensureXmltvSourceAdded(credentials.url, credentials.username, password)

                        authResponse
                    } finally {
                        if (!serviceAssigned) {
                            service.close()
                        }
                    }
                }
            }
        }

    /**
     * Updates the provider URL without changing username/password.
     * Re-authenticates with the new URL and clears cached data.
     */
    suspend fun updateProviderUrl(newUrl: String): Result<XtreamAuthResponse> =
        withContext(Dispatchers.IO) {
            sessionMutex.withLock {
                suspendResultOf {
                    val credentials =
                        accountManager.getCredentials()
                            ?: throw Exception("No stored credentials found")

                    val password =
                        credentials.password
                            ?: throw Exception("Password not stored. Please login again.")

                    // Update URL in storage
                    accountManager.updateUrl(newUrl)

                    var serviceAssigned = false
                    // Create new API service with updated URL
                    val service = XtreamApiService(newUrl, credentials.username, password, streamOutputFormat)
                    try {
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
                        replaceApiService(service)
                        serviceAssigned = true

                        // Auto-discover and add XMLTV source
                        ensureXmltvSourceAdded(newUrl, credentials.username, password)

                        authResponse
                    } finally {
                        if (!serviceAssigned) {
                            service.close()
                        }
                    }
                }
            }
        }

    private suspend fun ensureXmltvSourceAdded(
        baseUrl: String,
        user: String,
        pass: String,
    ) {
        // EPG sources belong to a provider - without a real provider id there is nothing to attach to.
        if (providerId > 0) {
            try {
                val normalizedUrl = baseUrl.trimEnd('/')
                val xmltvUrl = "$normalizedUrl/xmltv.php?username=$user&password=$pass"
                val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()

                val existing = sourceDao.getSourceByUrl(xmltvUrl, providerId)
                if (existing == null) {
                    val label = EpgFileManager.extractLabel(baseUrl) + " (Bulk)"
                    sourceDao.insertSource(
                        EpgSourceEntity(
                            url = xmltvUrl,
                            label = label,
                            enabled = true,
                            providerId = providerId,
                        ),
                    )
                    // Trigger an immediate background refresh if the index is empty
                    EpgFileManager.getInstance(context).refreshOutdatedSources(providerId)
                }
            } catch (e: Exception) {
                android.util.Log.e("XtreamSessionManager", "Failed to auto-add XMLTV source", e)
            }
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
            sessionMutex.withLock {
                suspendResultOf {
                    var serviceAssigned = false
                    val service = XtreamApiService(url, username, password, streamOutputFormat)
                    try {
                        val authResponse = service.authenticate()

                        if (authResponse.userInfo.auth != 1) {
                            throw Exception("Authentication failed: Invalid credentials")
                        }

                        if (authResponse.userInfo.status != "Active") {
                            throw Exception("Account is not active: ${authResponse.userInfo.status}")
                        }

                        replaceApiService(service)
                        serviceAssigned = true
                        authResponse
                    } finally {
                        if (!serviceAssigned) {
                            service.close()
                        }
                    }
                }
            }
        }

    fun isAuthenticated(): Boolean = apiService != null && accountManager.hasStoredCredentials()

    /**
     * Tears down the network client only — unlike [logout], keeps stored credentials and the
     * local catalog cache intact. For a provider that's merely being backgrounded/deselected
     * (switching to another provider, a settings screen closing), not one the user actually
     * logged out of or deleted.
     */
    suspend fun disconnect() =
        withContext(Dispatchers.IO) {
            sessionMutex.withLock {
                replaceApiService(null)
            }
        }

    suspend fun logout(): Result<Unit> =
        withContext(Dispatchers.IO) {
            sessionMutex.withLock {
                resultOf {
                    accountManager.clearCredentials()
                    replaceApiService(null)
                    onClearCache()
                }
            }
        }

    /**
     * Swaps in [newService], closing whatever it replaces. `XtreamApiService` owns its own
     * Dispatcher and ConnectionPool (not the app-wide shared ones — see its constructor), so
     * closing it here can't affect Jellyfin/TMDB/EPG/playback traffic; without this, every
     * login/reconnect/logout left the previous instance's HttpClient (and the coroutine Ktor
     * parks on it internally) running forever. See docs/plans/20260920_xtream-concurrency-fixes-plan.md.
     *
     * Caller must hold [sessionMutex] — see its kdoc for why an unsynchronized swap here is
     * exactly the race that broke category loading and playback with "executor rejected".
     */
    private fun replaceApiService(newService: XtreamApiService?) {
        apiService?.close()
        apiService = newService
    }

    fun getCurrentUrl(): String? = accountManager.getCredentials()?.url

    fun getCurrentUsername(): String? = accountManager.getCredentials()?.username

    fun getCurrentPassword(): String? = accountManager.getCredentials()?.password
}
