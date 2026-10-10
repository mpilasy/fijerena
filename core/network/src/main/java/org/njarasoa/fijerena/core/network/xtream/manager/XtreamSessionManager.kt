package org.njarasoa.fijerena.core.network.xtream.manager

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.resultOf
import org.njarasoa.fijerena.core.network.suspendResultOf
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.player.api.XtreamApiService
import org.njarasoa.fijerena.core.player.api.XtreamPanelClock
import org.njarasoa.fijerena.core.player.diagnostics.Redact
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
        const val LIVE_CHECK_TIMEOUT_MS = 10_000L
    }

    var apiService: XtreamApiService? = null
        private set

    /**
     * The panel's wall clock as its last login answer gave it ([XtreamPanelClock.from]); catch-up
     * URLs are written in it. Set with [apiService], so it always belongs to the session in use.
     */
    @Volatile
    var panelClock: XtreamPanelClock = XtreamPanelClock.UTC
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

                        if (authResponse.userInfo.auth != 1) {
                            throw Exception("Authentication failed: Invalid credentials")
                        }

                        if (authResponse.userInfo.status != "Active") {
                            throw Exception("Account is not active: ${authResponse.userInfo.status}")
                        }

                        accountManager.saveCredentials(url, username, password, authResponse, rememberMe)

                        // Store the API service for future use, closing whatever it replaces so its
                        // HttpClient (own Dispatcher + ConnectionPool, see XtreamApiService) doesn't leak.
                        replaceApiService(service, authResponse)
                        serviceAssigned = true

                        // One automatic XMLTV guide source, while the account has live channels
                        reconcileAutoXmltvSource(service, url, username, password)

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

                    // Empty, not only missing: a credentials file reset after a lost Keystore key
                    // seeds an empty password, and logging in with it just earns a server error
                    // that says nothing about why. See docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-28.
                    val password =
                        credentials.password?.takeIf { it.isNotEmpty() }
                            ?: throw Exception("Password not stored. Please login again.")

                    var serviceAssigned = false
                    val service = XtreamApiService(credentials.url, credentials.username, password, streamOutputFormat)
                    try {
                        Log.d(TAG, "Attempting to authenticate with ${Redact.text(credentials.url)}")
                        val authResponse =
                            try {
                                service.authenticate()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, Redact.text("Authentication failed for ${credentials.url}\n${Log.getStackTraceString(e)}"))
                                throw e
                            }

                        if (authResponse.userInfo.auth != 1) {
                            accountManager.clearCredentials()
                            throw Exception("Stored credentials are invalid")
                        }

                        if (authResponse.userInfo.status != "Active") {
                            accountManager.clearCredentials()
                            throw Exception("Account is not active: ${authResponse.userInfo.status}")
                        }

                        accountManager.saveCredentials(
                            credentials.url,
                            credentials.username,
                            password,
                            authResponse,
                            rememberMe = true,
                        )

                        replaceApiService(service, authResponse)
                        serviceAssigned = true

                        // One automatic XMLTV guide source, while the account has live channels
                        reconcileAutoXmltvSource(service, credentials.url, credentials.username, password)

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

                    accountManager.updateUrl(newUrl)

                    var serviceAssigned = false
                    val service = XtreamApiService(newUrl, credentials.username, password, streamOutputFormat)
                    try {
                        val authResponse = service.authenticate()

                        if (authResponse.userInfo.auth != 1) {
                            throw Exception("Authentication failed with new URL")
                        }

                        if (authResponse.userInfo.status != "Active") {
                            throw Exception("Account is not active: ${authResponse.userInfo.status}")
                        }

                        accountManager.saveCredentials(
                            newUrl,
                            credentials.username,
                            password,
                            authResponse,
                            rememberMe = true,
                        )

                        // Clear all cached data since it's from the old provider
                        onClearCache()

                        replaceApiService(service, authResponse)
                        serviceAssigned = true

                        // One automatic XMLTV guide source, carried over from the old server
                        reconcileAutoXmltvSource(service, newUrl, credentials.username, password, previousUrl = credentials.url)

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
     * Keeps this source's automatic XMLTV guide source (`<server>/xmltv.php?…`) in line with the
     * login: rewritten in place on a credential change, duplicates removed, added only when the
     * account has live channels and removed when it has none; disabled while the source's "Provides a
     * guide" is off. See [AutoXmltvSources].
     */
    private suspend fun reconcileAutoXmltvSource(
        service: XtreamApiService,
        baseUrl: String,
        user: String,
        pass: String,
        previousUrl: String? = null,
    ) {
        // EPG sources belong to a provider - without a real provider id there is nothing to attach to.
        if (providerId > 0) {
            try {
                // "Provides a guide" off: the automatic source stays disabled and is never added (no
                // live check needed, so it is never deleted for that either).
                val providesGuide = ProviderRepository(context).getProviderSettings(providerId).providesGuideOn
                val needsRefresh =
                    AutoXmltvSources.reconcile(
                        sourceDao = SettingsDatabase.getInstance(context).epgSourceDao(),
                        deleteIndexRows = AutoXmltvSources.indexRowsDeleter(context),
                        providerId = providerId,
                        providerUrl = baseUrl,
                        username = user,
                        password = pass,
                        hasLiveChannels = if (providesGuide) hasLiveChannels(service) else null,
                        previousProviderUrl = previousUrl,
                        providesGuide = providesGuide,
                    )
                if (needsRefresh) {
                    // Added or rewritten: fetch its guide now rather than at the next scheduled run.
                    EpgFileManager.getInstance(context).refreshOutdatedSources(providerId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, Redact.text("Failed to update the automatic XMLTV source\n${Log.getStackTraceString(e)}"))
            }
        }
    }

    /**
     * Whether the account has live channels: yes when the catalogue already holds some; otherwise
     * asks the server for its live categories (one small request, an empty list meaning none).
     * Null when that request fails or takes too long — then nothing is added or removed.
     */
    private suspend fun hasLiveChannels(service: XtreamApiService): Boolean? {
        val cached = XtreamDatabase.getInstance(context).streamDao().hasStreams(providerId, XtreamStreamEntity.TYPE_LIVE)
        val result =
            if (cached) {
                true
            } else {
                try {
                    withTimeoutOrNull(LIVE_CHECK_TIMEOUT_MS) { service.getCategories().isNotEmpty() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, Redact.text("Live categories check failed: ${e.message}"))
                    null
                }
            }
        return result
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

                        replaceApiService(service, authResponse)
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
     * parks on it internally) running forever. See docs/plans/archive/20260920_xtream-concurrency-fixes-plan.md.
     *
     * Caller must hold [sessionMutex] — see its kdoc for why an unsynchronized swap here is
     * exactly the race that broke category loading and playback with "executor rejected".
     */
    private fun replaceApiService(
        newService: XtreamApiService?,
        authResponse: XtreamAuthResponse? = null,
    ) {
        apiService?.close()
        panelClock = XtreamPanelClock.from(authResponse?.serverInfo)
        apiService = newService
    }

    fun getCurrentUrl(): String? = accountManager.getCredentials()?.url

    fun getCurrentUsername(): String? = accountManager.getCredentials()?.username

    fun getCurrentPassword(): String? = accountManager.getCredentials()?.password
}
