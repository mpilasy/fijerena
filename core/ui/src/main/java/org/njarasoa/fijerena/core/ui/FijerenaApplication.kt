package org.njarasoa.fijerena.core.ui

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncManager
import org.njarasoa.fijerena.core.player.network.NetworkModule

class FijerenaApplication :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // Initialize network module for robust DNS resolution
        NetworkModule.init(this)
        // Initialize EPG management
        EpgFileManager.getInstance(this).initialize()
        // Initialize Provider Content sync
        ProviderSyncManager.getInstance(this).initialize()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                // Reuse the shared OkHttpClient for image loading to prevent memory leaks and OOM
                add(OkHttpNetworkFetcherFactory(NetworkModule.okHttpClient))
            }.build()
}
