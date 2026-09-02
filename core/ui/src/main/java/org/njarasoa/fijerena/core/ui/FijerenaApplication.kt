package org.njarasoa.fijerena.core.ui

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncManager
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.network.NetworkModule
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.ui.di.AppContainer

class FijerenaApplication :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
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
        // One-time rewrite of any provider settings still storing the legacy category-filter
        // prefix shape — see ProviderRepository.migrateLegacyCategoryFilterPrefixes().
        CoroutineScope(Dispatchers.IO).launch {
            ProviderRepository(this@FijerenaApplication).migrateLegacyCategoryFilterPrefixes()
            // Build the encrypted credential store off the main thread, before the nav host's
            // session-restore effect asks for it from the main dispatcher.
            AccountManager(this@FijerenaApplication).warmUp()
            // Drop the EPG sources the app used to create for itself — see
            // EpgIndexer.purgeXtreamApiSources().
            EpgIndexer.getInstance(this@FijerenaApplication).purgeXtreamApiSources()
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

    private fun isPlaybackActive(): Boolean {
        val state = StreamingPlaybackService.getInstance()?.playbackState?.value ?: return false
        return state is PlaybackState.Playing || state is PlaybackState.Buffering
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                // Reuse the shared OkHttpClient for image loading to prevent memory leaks and OOM
                add(OkHttpNetworkFetcherFactory(NetworkModule.okHttpClient))
            }
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
                    .build()
            }.build()
}
