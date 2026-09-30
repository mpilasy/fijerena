package org.njarasoa.fijerena.core.ui.di

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository

/**
 * Dependency Injection container for the app.
 *
 * Provides singletons for repositories to avoid redundant instantiation and
 * to ensure consistent state across the app.
 */
class AppContainer(
    private val context: Context,
) {
    /**
     * Singleton instance of ProviderRepository.
     * Manages all configured media providers and their settings.
     */
    val providerRepository: ProviderRepository by lazy {
        ProviderRepository(context.applicationContext)
    }

    /**
     * Cache for MediaRepository instances per provider ID.
     */
    private val mediaRepositories = mutableMapOf<Long, MediaRepository>()
    private val mutex = Mutex()

    /**
     * Provides a MediaRepository instance for the specified provider ID.
     * If the ID is 0, the active provider is used.
     */
    suspend fun getMediaRepository(providerId: Long = 0L): MediaRepository =
        // Everything below touches disk: provider Room lookups, several SharedPreferences loads
        // (MediaRepository's cache, XtreamRepository's cache) and an OkHttp/Ktor client build.
        // Callers reach this from LaunchedEffect, which runs on Main, so without this the whole
        // ~500ms lands on the UI thread during startup — measured with StrictMode on a Shield.
        withContext(Dispatchers.IO) {
            val resolvedId =
                if (providerId > 0L) {
                    providerId
                } else {
                    providerRepository.getActiveProvider()?.id ?: 0L
                }

            val repo =
                mutex.withLock {
                    mediaRepositories[resolvedId] ?: run {
                        val settings = providerRepository.getProviderSettings(resolvedId)
                        val profileId = AppSettings(context.applicationContext).activeProfileId
                        val newRepo = MediaRepository(context.applicationContext, resolvedId, profileId, settings)

                        // Set the provider implementation
                        val entity =
                            if (providerId > 0L) {
                                providerRepository.getProviderById(providerId)
                            } else {
                                providerRepository.getActiveProvider()
                            }

                        if (entity != null) {
                            // This profile's login — its own for Jellyfin, the shared one otherwise.
                            val login = providerRepository.getLogin(entity)
                            val provider =
                                MediaProviderFactory.create(entity.copy(username = login.username), context.applicationContext, login.password)
                            newRepo.setProvider(provider)
                            // Only cache once we actually have a backing provider — otherwise this
                            // provider-less repo would get stuck at mediaRepositories[0L] forever,
                            // even after a real active provider is set up later.
                            mediaRepositories[resolvedId] = newRepo
                        }
                        newRepo
                    }
                }

            // Auto-restore session if not connected outside the global mutex so slow/failing
            // network calls for one provider don't block other repository operations.
            if (!repo.isConnected()) {
                try {
                    repo.connect()
                } catch (e: Exception) {
                    android.util.Log.e("AppContainer", "Auto-connect failed for provider $resolvedId", e)
                }
            }

            repo
        }

    /**
     * Clears all cached repositories and providers.
     * Call this when switching providers or on logout to ensure fresh state.
     */
    suspend fun clearAllCaches() {
        mutex.withLock {
            mediaRepositories.values.forEach { repo ->
                try {
                    repo.close()
                } catch (e: Exception) {
                    android.util.Log.w("AppContainer", "Error closing MediaRepository during clearAllCaches", e)
                }
            }
            mediaRepositories.clear()
            MediaProviderFactory.clearAllCaches()
        }
    }

    // (providerId, profileId) pairs already sent to sign in this process — see [shouldPromptSignIn].
    private val signInPrompted = java.util.concurrent.ConcurrentHashMap.newKeySet<Pair<Long, String>>()

    /**
     * Whether the home screen should send this device's profile to sign in to [providerId]: true
     * the first time per process for each provider/profile pair, false after. Once is enough —
     * backing out of the sign-in screen must land on a usable home (to switch profile, say), not
     * bounce straight back into sign-in.
     */
    fun shouldPromptSignIn(providerId: Long): Boolean = signInPrompted.add(providerId to AppSettings(context.applicationContext).activeProfileId)

    /**
     * Makes [profileId] the profile this device uses. Every cached MediaRepository belongs to the
     * previous profile (see its `profileId`), so all of them are closed and dropped; the next
     * getMediaRepository() builds one for the new profile. Shared provider sessions are kept —
     * switching person doesn't mean logging back in to Xtream — but Jellyfin's are dropped, since
     * each profile signs in to Jellyfin as its own user. Callers must also drop any screen still
     * holding a repository, which the nav hosts do by rebuilding the back stack from home.
     */
    suspend fun switchProfile(profileId: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                AppSettings(context.applicationContext).activeProfileId = profileId
                mediaRepositories.values.forEach { repo ->
                    try {
                        repo.close()
                    } catch (e: Exception) {
                        android.util.Log.w("AppContainer", "Error closing MediaRepository during profile switch", e)
                    }
                }
                mediaRepositories.clear()
                MediaProviderFactory.clearProfileScopedProviders()
            }
        }
    }

    /**
     * Evicts a single cached MediaRepository. Call this after a provider's credentials
     * change (URL/username/password) so the next getMediaRepository() call rebuilds it
     * with a fresh MediaProvider instead of reusing one built from the old credentials.
     */
    suspend fun evictMediaRepository(providerId: Long) {
        mutex.withLock {
            val repo = mediaRepositories.remove(providerId)
            try {
                repo?.close()
            } catch (e: Exception) {
                android.util.Log.w("AppContainer", "Error closing MediaRepository during eviction for provider $providerId", e)
            }
        }
    }

    /**
     * Drops in-memory detail/search caches held by every live provider, without evicting the
     * provider instances or repositories themselves (that would force a re-auth/reconnect).
     * Called on system memory pressure — see [org.njarasoa.fijerena.core.ui.FijerenaApplication.onTrimMemory].
     */
    fun trimMemory() {
        MediaProviderFactory.trimMemory()
    }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun getInstance(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
