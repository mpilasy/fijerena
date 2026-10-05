package org.njarasoa.fijerena.core.ui

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.FavoriteCategoryRowCleanup
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProvidersDbGuard
import org.njarasoa.fijerena.core.network.sync.pruneSyncTombstones
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncManager
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.njarasoa.fijerena.core.player.diagnostics.SafeMode
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.network.NetworkModule
import org.njarasoa.fijerena.core.player.service.PlaybackServiceLocale
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.sync.NowPlayingPublisher
import org.njarasoa.fijerena.core.ui.sync.RemoteStopFallback
import org.njarasoa.fijerena.core.ui.sync.SyncManager
import org.njarasoa.fijerena.core.ui.utils.LocaleManager

class FijerenaApplication :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // First, so anything that goes wrong from here on — startup included — is recorded.
        // Settings → Diagnostics (developer mode) shows it.
        CrashLog.install(this)
        // Before anything can start the playback service, which builds its error messages in the
        // app's language only through this.
        PlaybackServiceLocale.wrap = LocaleManager::wrap
        // Next, before anything that could be what keeps crashing: counts this launch and decides
        // whether it starts in safe mode — see SafeMode.
        SafeMode.init(this)
        // Before anything opens providers.db: one written by a newer build can't be opened at all —
        // see ProvidersDbGuard.
        ProvidersDbGuard.check(this)
        // Debug-only: log any main-thread disk/DB access (with a stack trace) to pinpoint UI-thread
        // jank/ANRs. Gated on the debuggable flag so it never runs in release.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy
                    .Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build(),
            )
        }
        // Initialize network module for robust DNS resolution
        NetworkModule.init(this)
        // A remote Stop no player screen took (playback with nothing on screen to obey it).
        RemoteStopFallback.start(this)
        // Safe mode (the last launches kept crashing): none of the startup work below, any of which
        // may be the cause. The safe-mode screen's Continue restarts the process to run it.
        // Nor with a providers.db from a newer build, which every one of those steps would open.
        if (!SafeMode.isActive && !ProvidersDbGuard.isBlocked) startBackgroundWork()
    }

    @OptIn(UnstableApi::class)
    private fun startBackgroundWork() {
        EpgFileManager.getInstance(this).initialize()
        ProviderSyncManager.getInstance(this).initialize()
        // Live sync between devices, while the app is in use — a no-op until this device is linked.
        SyncManager.getInstance(this).start()
        // What this device plays, for the group's devices lists — sends nothing until turned on.
        NowPlayingPublisher.getInstance(this).start()
        // One-time moves of each provider's category filters and the install-wide dev-mode flag to
        // every profile, and the install-wide search history to the default profile — see
        // ProviderRepository.migrateCategoryFiltersToProfiles().
        // AppScopes, not a bare CoroutineScope(Dispatchers.IO): an uncaught exception there would
        // reach the thread's uncaught-exception handler and crash the process on cold boot.
        val startupScope = AppScopes.create("FijerenaApplication.startup", Dispatchers.IO)
        // In order, each step guarded on its own: none needs an earlier one, and a failing step
        // (say pruneSyncTombstones) must not skip the rest, credential warm-up included. See
        // docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-24.
        startupScope.launch {
            startupStep("migrateCategoryFiltersToProfiles") {
                ProviderRepository(this@FijerenaApplication).migrateCategoryFiltersToProfiles()
            }
            startupStep("migrateLegacyProfileSettings") {
                ProfileRepository(this@FijerenaApplication).migrateLegacyProfileSettings()
            }
            // Live sync keeps deletions for 90 days — see SyncKind.TOMBSTONE_RETENTION_MS.
            startupStep("pruneSyncTombstones") { pruneSyncTombstones(this@FijerenaApplication) }
            // Build the encrypted credential store off the main thread, before the nav host's
            // session-restore effect asks for it from the main dispatcher.
            startupStep("warmUpCredentials") { AccountManager(this@FijerenaApplication).warmUp() }
            // Drop the EPG sources the app used to create for itself — see
            // EpgIndexer.purgeXtreamApiSources().
            startupStep("purgeXtreamApiSources") {
                EpgIndexer.getInstance(this@FijerenaApplication).purgeXtreamApiSources()
            }
            // Once per install: drop the bogus stream favourites the Favourite categories list
            // used to save — see FavoriteCategoryRowCleanup.
            startupStep("FavoriteCategoryRowCleanup") {
                FavoriteCategoryRowCleanup.runOnce(
                    AppSettings(this@FijerenaApplication),
                    XtreamDatabase.getInstance(this@FijerenaApplication).favoriteStateDao(),
                ) { AppContainer.getInstance(this@FijerenaApplication).reloadAfterRemoteChange(it) }
            }
        }
        // Its own coroutine, so nothing above can skip it: finishes a provider deletion the app was
        // killed in the middle of. See docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-03.
        startupScope.launch {
            ProviderRepository(this@FijerenaApplication).sweepOrphanedCatalogData(onlyIfPending = true)
        }
        // Its own coroutine, so it is done before the first film starts: the player service checks
        // FFmpeg on the main thread, and the first check loads the native library from disk. See
        // docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-28.
        startupScope.launch {
            startupStep("StreamingPlaybackService.warmUp") { StreamingPlaybackService.warmUp() }
        }
    }

    /** One startup step: a failure is logged and recorded in [CrashLog], and the next step still runs. */
    private suspend fun startupStep(
        name: String,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("FijerenaApplication", "Startup step $name failed", e)
            CrashLog.record("FijerenaApplication.startup $name", e)
        }
    }

    // A busy day of browsing a large catalogue lets the poster memory cache and providers'
    // detail/search caches (see XtreamMediaProvider) grow for as long as the process lives —
    // nothing else ever shrinks them. RUNNING_LOW is the first level that reflects real system
    // pressure (below it, MODERATE only means "not foreground"), and every higher level implies
    // it, so this alone covers both foreground and background pressure.
    //
    // The OkHttp connection pool is a separate, more disruptive step: it's shared with the
    // player's streaming DataSource, so evicting it mid-playback would tear down an active
    // connection. It only runs once the app has actually left the foreground (UI_HIDDEN+), and
    // only when nothing is playing — background/PIP audio keeps that pool legitimately in use.
    //
    // TRIM_MEMORY_RUNNING_LOW is deprecated as of API 34 (the OS stopped delivering the
    // foreground RUNNING_* levels to apps targeting it) — the Shields this app actually ships
    // to mostly run well below that, where it's still the only mid-session pressure signal, and
    // on newer OS it's a harmless no-op: cache clearing simply happens later, at UI_HIDDEN.
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            Log.i("FijerenaApplication", "onTrimMemory(level=$level): clearing image and provider caches")
            SingletonImageLoader.get(this).memoryCache?.clear()
            AppContainer.getInstance(this).trimMemory()
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && !isPlaybackActive()) {
            Log.i("FijerenaApplication", "onTrimMemory(level=$level): evicting idle connection pool")
            NetworkModule.evictConnectionPool()
        }
    }

    @OptIn(UnstableApi::class)
    private fun isPlaybackActive(): Boolean {
        val state = StreamingPlaybackService.getInstance()?.playbackState?.value
        return state is PlaybackState.Playing || state is PlaybackState.Buffering
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                // Reuse the shared OkHttpClient for image loading to prevent memory leaks and OOM
                add(OkHttpNetworkFetcherFactory(NetworkModule.okHttpClient))
            }.coroutineContext(Dispatchers.IO)
            // Posters/thumbnails rarely change and there are thousands of them across a large
            // catalog — a generously sized disk cache means scrolling back through a category or
            // reopening a detail screen doesn't refetch images that were already downloaded.
            // The memory cache stays modest (largeHeap is on for both apps, so 25% of the app's
            // heap on a Shield was easily 60-100MB+ of decoded bitmaps); onTrimMemory() above
            // sweeps it under real pressure regardless.
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
            .diskCache {
                DiskCache
                    .Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512L * 1024 * 1024)
                    .cleanupCoroutineContext(Dispatchers.IO)
                    .build()
            }.build()
}
