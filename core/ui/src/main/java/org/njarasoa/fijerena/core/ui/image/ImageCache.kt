package org.njarasoa.fijerena.core.ui.image

import android.content.Context
import coil3.SingletonImageLoader
import java.io.File

/**
 * The posters and logos kept on disk (Coil's `DiskCache`, in cacheDir/[DIRECTORY]). Its limit is
 * 2% of the storage, from 64 MB to 512 MB (the low storage plan, LS-Q2): about 160 MB on an 8 GB
 * TV, so that a small TV keeps its space for the guide and the catalogue.
 */
object ImageCache {
    const val DIRECTORY = "image_cache"

    private const val MB = 1024L * 1024
    const val MIN_BYTES = 64L * MB
    const val MAX_BYTES = 512L * MB
    private const val PERCENT = 2

    /** The limit on a storage of [totalBytes]. A size that the app cannot read (0) gives the minimum. */
    fun maxBytes(totalBytes: Long): Long = (totalBytes / 100 * PERCENT).coerceIn(MIN_BYTES, MAX_BYTES)

    /** The limit on the storage that holds [dir]. */
    fun maxBytes(dir: File): Long = maxBytes(dir.totalSpace)

    /**
     * Removes every image from the memory cache and the disk cache. The screens download their
     * images again when they show them. Call it off the main thread.
     */
    fun clear(context: Context) {
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }
}
