package org.njarasoa.fijerena.core.ui.di

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes

/**
 * Dependency Injection container for the app.
 *
 * Provides singletons for repositories to avoid redundant instantiation and
 * to ensure consistent state across the app.
 */
class AppContainer(
    private val context: Context,
) {
    val providerRepository: ProviderRepository by lazy {
        ProviderRepository(context.applicationContext)
    }

    /**
     * Cache for MediaRepository instances per provider ID. Internal for AppContainerProviderChangeTest.
     */
    internal val mediaRepositories = mutableMapOf<Long, MediaRepository>()
    private val mutex = Mutex()

    // Runs the evictions MediaProviderFactory.providerChanged asks for, which can't wait on [mutex].
    private val scope = AppScopes.create("AppContainer", Dispatchers.IO)

    init {
        MediaProviderFactory.providerChangedListener = { providerId -> scope.launch { onProvidersChanged(setOf(providerId)) } }
    }

    /**
     * Provides a MediaRepository instance for the source [providerId]. Every caller names a real
     * source: there is no device or current source to fall back to (multi-source plan, Phase 6).
     */
    suspend fun getMediaRepository(providerId: Long): MediaRepository =
        // Everything below touches disk: provider Room lookups, several SharedPreferences loads
        // (MediaRepository's cache, XtreamRepository's cache) and an OkHttp/Ktor client build.
        // Callers reach this from LaunchedEffect, which runs on Main, so without this the whole
        // ~500ms lands on the UI thread during startup — measured with StrictMode on a Shield.
        withContext(Dispatchers.IO) {
            val repo = cachedOrNewRepository(providerId)

            // Auto-restore session if not connected outside the global mutex so slow/failing
            // network calls for one provider don't block other repository operations.
            if (!repo.isConnected()) connectLogged(repo)

            repo
        }

    /**
     * [getMediaRepository] for a caller that reads only what is stored on the device (the merged
     * Recent, favourites and Continue Watching of the profile's other sources, multi-source plan
     * Phase 4): the session is restored in the background, so a source that is slow or offline
     * doesn't hold the lists up.
     */
    suspend fun getStoredDataRepository(providerId: Long): MediaRepository =
        withContext(Dispatchers.IO) {
            val repo = cachedOrNewRepository(providerId)
            if (!repo.isConnected()) scope.launch { connectLogged(repo) }
            repo
        }

    private suspend fun connectLogged(repo: MediaRepository) {
        try {
            repo.connect()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("AppContainer", "Auto-connect failed for provider ${repo.getProviderId()}", e)
        }
    }

    private suspend fun cachedOrNewRepository(providerId: Long): MediaRepository {
        val repo =
            mutex.withLock {
                mediaRepositories[providerId] ?: run {
                    val settings = providerRepository.getProviderSettings(providerId)
                    val profileId = AppSettings(context.applicationContext).activeProfileId
                    val newRepo = MediaRepository(context.applicationContext, providerId, profileId, settings)

                    val entity = providerRepository.getProviderById(providerId)

                    if (entity != null) {
                        // This profile's login — its own for Jellyfin, the shared one otherwise.
                        val login = providerRepository.getLogin(entity)
                        val provider =
                            MediaProviderFactory.create(
                                entity.copy(username = login.username),
                                context.applicationContext,
                                login.password,
                            )
                        newRepo.setProvider(provider)
                        // Only cache once we actually have a backing provider — otherwise a
                        // provider-less repo (a source deleted meanwhile) would stay cached.
                        mediaRepositories[providerId] = newRepo
                    }
                    newRepo
                }
            }
        return repo
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
                    // cancellation-ok: non-suspend
                    android.util.Log.w("AppContainer", "Error closing MediaRepository during clearAllCaches", e)
                }
            }
            mediaRepositories.clear()
            MediaProviderFactory.clearAllCaches()
        }
    }

    /**
     * Makes [profileId] the profile this device uses. Every cached MediaRepository belongs to the
     * previous profile (see its `profileId`), so all of them are closed and dropped; the next
     * getMediaRepository() builds one for the new profile. Shared provider sessions are kept —
     * switching person doesn't mean logging back in to Xtream — but Jellyfin's are dropped, since
     * each profile signs in to Jellyfin as its own user. Xtream providers whose category filters
     * differ between the two profiles get their hidden-category flags recomputed. No source moves:
     * the lists reload over the new profile's own sources (multi-source plan, Phase 6). Callers must
     * also drop any screen still holding a repository, which the nav hosts do by rebuilding the back
     * stack from home.
     */
    suspend fun switchProfile(profileId: String) {
        withContext(Dispatchers.IO) {
            val started = android.os.SystemClock.elapsedRealtime()
            mutex.withLock {
                val locked = android.os.SystemClock.elapsedRealtime()
                val appSettings = AppSettings(context.applicationContext)
                val previousProfileId = appSettings.activeProfileId
                appSettings.activeProfileId = profileId
                providerRepository.applyCategoryFiltersForSwitch(previousProfileId, profileId)
                val filtered = android.os.SystemClock.elapsedRealtime()
                mediaRepositories.values.forEach { repo ->
                    try {
                        repo.close()
                    } catch (e: Exception) {
                        // cancellation-ok: non-suspend
                        android.util.Log.w("AppContainer", "Error closing MediaRepository during profile switch", e)
                    }
                }
                mediaRepositories.clear()
                MediaProviderFactory.clearProfileScopedProviders()
                val done = android.os.SystemClock.elapsedRealtime()
                android.util.Log.i(
                    "ProfileSwitch",
                    "to $profileId: ${done - started} ms (lock wait ${locked - started}, filters ${filtered - locked}, teardown ${done - filtered})",
                )
            }
        }
    }

    private val _externalSwitches = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * The profile in use was deleted on another device, and live sync moved this device to
     * another: the nav hosts drop every screen, playback included, and ask who is watching — TV's
     * profile picker, the phone's profile menu.
     */
    val externalSwitches: SharedFlow<Unit> = _externalSwitches.asSharedFlow()

    private val _sourceDeletions = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Another device deleted a source the profile in use had: the nav hosts rebuild the back stack
     * from home as for [externalSwitches], but only once nothing plays — a stream doesn't need its
     * source's settings, so what is on screen keeps playing.
     */
    val sourceDeletions: SharedFlow<Unit> = _sourceDeletions.asSharedFlow()

    /** [switchProfile], for a switch the UI didn't ask for: announces it on [externalSwitches]. */
    suspend fun switchProfileExternally(profileId: String) {
        switchProfile(profileId)
        _externalSwitches.emit(Unit)
    }

    /**
     * Another device deleted a source the profile in use had: announced on [sourceDeletions] so no
     * screen stays on it. Its cached repository is
     * already dropped ([onProvidersChanged]); the other sources' stay. See
     * docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-06 step 3.
     */
    suspend fun providerDeletedExternally() {
        _sourceDeletions.emit(Unit)
    }

    /**
     * Changes received from another device rewrote favourites or history of [providerIds]: every
     * cached repository of theirs refills its in-memory views and re-publishes its Recent lists.
     * See `SyncApplier.Result.userDataChangedProviderIds`.
     */
    suspend fun reloadAfterRemoteChange(providerIds: Set<Long>) {
        val repos = mutex.withLock { mediaRepositories.filterKeys { it in providerIds }.values.toList() }
        withContext(Dispatchers.IO) { repos.forEach { it.reloadAfterRemoteChange() } }
    }

    /**
     * The settings, URL or login of [providerIds] changed, here or on another device: their cached
     * MediaRepository and the factory's provider go together, under [mutex], so the next
     * getMediaRepository() builds both from the new values. A screen still
     * holding the old repository keeps it until it asks again; playback in progress keeps its
     * stream URL. See docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-06 step 2.
     */
    suspend fun onProvidersChanged(providerIds: Set<Long>) {
        mutex.withLock {
            providerIds.forEach { providerId ->
                val repo = mediaRepositories.remove(providerId)
                try {
                    repo?.close()
                } catch (e: Exception) {
                    // cancellation-ok: non-suspend
                    android.util.Log.w("AppContainer", "Error closing MediaRepository during eviction for provider $providerId", e)
                }
                MediaProviderFactory.clearCache(providerId)
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
