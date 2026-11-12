package org.njarasoa.fijerena.core.network

import android.annotation.SuppressLint
import android.content.Context

/**
 * Whether the device's storage is low, as Android counts it for WorkManager's
 * `setRequiresStorageNotLow`: less free space than 5% of the storage, at most 500 MB. A guide
 * refresh or a catalogue sync does not start then, as it can need a few hundred MB more while it
 * runs.
 *
 * [shortfall] is the second check: whether there is space for one job of a known size ([SpaceNeed]).
 */
object StorageSpace {
    private const val LOW_PERCENT = 5
    private const val LOW_MAX_BYTES = 500L * 1024 * 1024
    private const val MB = 1024L * 1024

    /**
     * Added to each need in [shortfall], so that small writes (watch history, settings) still work
     * after a large write.
     */
    const val MARGIN_BYTES = 50L * MB

    /**
     * A free-space value that the checks read in place of the real one; null reads the real one.
     * Only the debug-build `FreeSpaceDebugReceiver` sets it (over adb), so that the low-storage
     * messages can be seen on an emulator (the low storage plan, LS-Q6). It is not kept: a new
     * process reads the real value.
     */
    @Volatile var debugFreeBytes: Long? = null

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
        val free = debugFreeBytes ?: dir.usableSpace
        return free.takeIf { isLow(it, dir.totalSpace) }
    }

    /** The masked text a Nerd sees under the message. */
    fun rawText(freeBytes: Long): String = "Storage low: ${freeBytes / MB} MB free"

    /**
     * A [ErrorKind.STORAGE_LOW] error when [space] has less free than [needBytes] plus
     * [MARGIN_BYTES]; null when there is enough. A Nerd sees the two values ([needText]).
     */
    fun shortfall(
        needBytes: Long,
        space: FreeSpace,
    ): StoredError? {
        val required = needBytes.coerceAtLeast(0) + MARGIN_BYTES
        val free = space.freeBytes()
        return if (free >= required) null else StoredError(ErrorKind.STORAGE_LOW, raw = needText(required, free))
    }

    /** The masked text a Nerd sees for a job that did not start: "Need 420 MB, 180 MB free". */
    fun needText(
        needBytes: Long,
        freeBytes: Long,
    ): String = "Need ${needBytes / MB} MB, ${freeBytes / MB} MB free"
}

/** The free bytes of the app's storage. Tests give a fake. */
fun interface FreeSpace {
    fun freeBytes(): Long

    companion object {
        /**
         * The free space of the storage that holds the app's files, as [StorageSpace.lowFreeBytes]
         * reads it. A storage whose size reads 0 has no size that the app can read (File gives 0
         * when it cannot read it): it counts as having space, so that the check never stops every
         * job. [StorageSpace.debugFreeBytes], when set, comes first.
         */
        @SuppressLint("UsableSpace")
        fun of(context: Context): FreeSpace =
            FreeSpace {
                val dir = context.filesDir
                StorageSpace.debugFreeBytes ?: if (dir.totalSpace > 0) dir.usableSpace else Long.MAX_VALUE
            }
    }
}

/**
 * The space that each large write needs before it starts (the low storage plan → Space each
 * writer needs). [StorageSpace.shortfall] adds its margin.
 */
object SpaceNeed {
    private const val MB = 1024L * 1024

    /** An XMLTV download with no Content-Length and no earlier download. */
    const val DEFAULT_DOWNLOAD_BYTES = 50L * MB

    /** A `.gz` guide file is about this many times smaller than its XML. */
    private const val GZIP_RATIO = 10

    private const val CATALOGUE_MIN_BYTES = 100L * MB

    /** The XMLTV download: its Content-Length; else the source's last download; else 50 MB. */
    fun xmltvDownload(
        contentLength: Long,
        lastDownloadBytes: Long,
    ): Long =
        when {
            contentLength > 0 -> contentLength
            lastDownloadBytes > 0 -> lastDownloadBytes
            else -> DEFAULT_DOWNLOAD_BYTES
        }

    /**
     * A staged guide ingest: 1.5 × epg_index.db. Staging holds the new rows next to the live ones,
     * and the swap with its search index rebuild grows the WAL by about the index size.
     */
    fun stagedIngest(indexBytes: Long): Long = indexBytes * 3 / 2

    /** A direct guide ingest: the uncompressed file; a `.gz` file 10 × its size. */
    fun directIngest(
        fileBytes: Long,
        gzip: Boolean,
    ): Long = if (gzip) fileBytes * GZIP_RATIO else fileBytes

    /** A rebuild of the guide's search index: 0.5 × epg_index.db, as the index is a part of it. */
    fun ftsRebuild(indexBytes: Long): Long = indexBytes / 2

    /** A catalogue sync: 25% of xtream_v2.db, at least 100 MB. */
    fun catalogueSync(catalogueBytes: Long): Long = maxOf(catalogueBytes / 4, CATALOGUE_MIN_BYTES)

    /** A Remote M3U download: 2 × its Content-Length (the temp file, then the cache file). */
    fun remoteM3uDownload(contentLength: Long): Long = 2 * contentLength.coerceAtLeast(0)
}

/**
 * A job that did not start, because the storage has less free space than it needs ([error], from
 * [StorageSpace.shortfall]). It counts as a full disk ([isDiskFull]): it reads as storage low, and
 * nothing tries the job again in the same run.
 */
class NotEnoughSpaceException(
    val error: StoredError,
) : Exception(error.raw)
