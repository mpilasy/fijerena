package org.njarasoa.fijerena.core.ui.di

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

            mutex.withLock {
                val repo =
                    mediaRepositories[resolvedId] ?: run {
                        val settings = providerRepository.getProviderSettings(resolvedId)
                        val newRepo = MediaRepository(context.applicationContext, resolvedId, settings)

                        // Set the provider implementation
                        val entity =
                            if (providerId > 0L) {
                                providerRepository.getProviderById(providerId)
                            } else {
                                providerRepository.getActiveProvider()
                            }

                        if (entity != null) {
                            val password = providerRepository.getPassword(entity.id) ?: ""
                            val provider = MediaProviderFactory.create(entity, context.applicationContext, password)
                            newRepo.setProvider(provider)
                            // Only cache once we actually have a backing provider — otherwise this
                            // provider-less repo would get stuck at mediaRepositories[0L] forever,
                            // even after a real active provider is set up later.
                            mediaRepositories[resolvedId] = newRepo
                        }
                        newRepo
                    }

                // Surgical Fix: Auto-restore session if not connected.
                // This ensures direct navigation (from search, EPG browser, etc.) has a valid session.
                // We do this inside the lock to prevent concurrent restoration attempts.
                if (!repo.isConnected()) {
                    try {
                        repo.connect()
                    } catch (e: Exception) {
                        android.util.Log.e("AppContainer", "Auto-connect failed for provider $resolvedId", e)
                    }
                }

                repo
            }
        }

    /**
     * Clears all cached repositories and providers.
     * Call this when switching providers or on logout to ensure fresh state.
     */
    suspend fun clearAllCaches() {
        mutex.withLock {
            mediaRepositories.clear()
            MediaProviderFactory.clearAllCaches()
        }
    }

    /**
     * Evicts a single cached MediaRepository. Call this after a provider's credentials
     * change (URL/username/password) so the next getMediaRepository() call rebuilds it
     * with a fresh MediaProvider instead of reusing one built from the old credentials.
     */
    suspend fun evictMediaRepository(providerId: Long) {
        mutex.withLock {
            mediaRepositories.remove(providerId)
        }
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
