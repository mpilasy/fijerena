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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.FavoriteCategoryRowCleanup
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.sync.pruneSyncTombstones
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncManager
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.network.NetworkModule
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.sync.NowPlayingPublisher
import org.njarasoa.fijerena.core.ui.sync.RemoteStopFallback
import org.njarasoa.fijerena.core.ui.sync.SyncManager

class FijerenaApplication :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // First, so anything that goes wrong from here on — startup included — is recorded.
        // Settings → Diagnostics (developer mode) shows it.
        CrashLog.install(this)
        // Debug-only: log any main-thread disk/DB access (with a stack trace) to pinpoint UI-thread
        // jank/ANRs. Gated on the debuggable flag so it never runs in release.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build(),
            )
        }
        // Initialize network module for robust DNS resolution
        NetworkModule.init(this)
        // Initialize EPG management
        EpgFileManager.getInstance(this).initialize()
        // Initialize Provider Content sync
        ProviderSyncManager.getInstance(this).initialize()
        // Live sync between devices, while the app is in use — a no-op until this device is linked.
        SyncManager.getInstance(this).start()
        // What this device plays, for the group's devices lists — sends nothing until turned on.
        NowPlayingPublisher.getInstance(this).start()
        // A remote Stop no player screen took (playback with nothing on screen to obey it).
        RemoteStopFallback.start(this)
        // One-time moves of each provider's category filters and the install-wide dev-mode flag to
        // every profile, and the install-wide search history to the default profile — see
        // ProviderRepository.migrateCategoryFiltersToProfiles().
        // AppScopes, not a bare CoroutineScope(Dispatchers.IO): an uncaught exception there would
        // reach the thread's uncaught-exception handler and crash the process on cold boot.
        AppScopes.create("FijerenaApplication.startup", Dispatchers.IO).launch {
            ProviderRepository(this@FijerenaApplication).migrateCategoryFiltersToProfiles()
            ProfileRepository(this@FijerenaApplication).migrateLegacyProfileSettings()
            // Live sync keeps deletions for 90 days — see SyncKind.TOMBSTONE_RETENTION_MS.
            pruneSyncTombstones(this@FijerenaApplication)
            // Build the encrypted credential store off the main thread, before the nav host's
            // session-restore effect asks for it from the main dispatcher.
            AccountManager(this@FijerenaApplication).warmUp()
            // Drop the EPG sources the app used to create for itself — see
            // EpgIndexer.purgeXtreamApiSources().
            EpgIndexer.getInstance(this@FijerenaApplication).purgeXtreamApiSources()
            // Once per install: drop the bogus stream favourites the Favourite categories list
            // used to save — see FavoriteCategoryRowCleanup. Last, so a failure here skips nothing.
            FavoriteCategoryRowCleanup.runOnce(
                AppSettings(this@FijerenaApplication),
                XtreamDatabase.getInstance(this@FijerenaApplication).favoriteStateDao(),
            ) { AppContainer.getInstance(this@FijerenaApplication).reloadAfterRemoteChange(it) }
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
            }
            .coroutineContext(Dispatchers.IO)
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
                    .maxSizeBytes(512L * 1024 * 1024) // 512 MB
                    .cleanupCoroutineContext(Dispatchers.IO)
                    .build()
            }.build()
}
