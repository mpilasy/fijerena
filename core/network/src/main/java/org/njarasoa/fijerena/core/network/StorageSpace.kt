package org.njarasoa.fijerena.core.network

import android.annotation.SuppressLint
import android.content.Context

/**
 * Whether the device's storage is low, as Android counts it for WorkManager's
 * `setRequiresStorageNotLow`: less free space than 5% of the storage, at most 500 MB. A guide
 * refresh or a catalogue sync does not start then, as it can need a few hundred MB more while it
 * runs.
 */
object StorageSpace {
    private const val LOW_PERCENT = 5
    private const val LOW_MAX_BYTES = 500L * 1024 * 1024

    /** Below this many free bytes, storage of [totalBytes] is low. */
    fun lowThresholdBytes(totalBytes: Long): Long = minOf(totalBytes * LOW_PERCENT / 100, LOW_MAX_BYTES)

    fun isLow(
        freeBytes: Long,
        totalBytes: Long,
    ): Boolean = freeBytes < lowThresholdBytes(totalBytes)

    /**
     * The free bytes of the app's storage when it is low; null when it is not. Free space, not
     * allocatable bytes: Android's low-storage state, which WorkManager waits on, counts free space.
     */
    @SuppressLint("UsableSpace")
    fun lowFreeBytes(context: Context): Long? {
        val dir = context.filesDir
        val free = dir.usableSpace
        return free.takeIf { isLow(it, dir.totalSpace) }
    }

    /** The masked text a Nerd sees under the message. */
    fun rawText(freeBytes: Long): String = "Storage low: ${freeBytes / (1024 * 1024)} MB free"
}
