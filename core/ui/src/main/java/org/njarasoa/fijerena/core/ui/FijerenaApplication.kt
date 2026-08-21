package org.njarasoa.fijerena.core.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
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
import org.njarasoa.fijerena.core.player.network.NetworkModule

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
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache {
                DiskCache
                    .Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512L * 1024 * 1024) // 512 MB
                    .build()
            }.build()
}
