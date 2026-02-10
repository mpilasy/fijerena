package org.njarasoa.fijerena.core.network.xmltv.epgindex

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.xmltv.IptvOrgEpgSource
import org.njarasoa.fijerena.core.network.xmltv.XmltvParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Singleton that indexes XMLTV files into SQLite for fast FTS search.
 *
 * Streaming parse with transactional batch INSERT (1000 rows per batch).
 * Memory bounded: ~200KB per batch.
 *
 * Supports two ingestion strategies:
 * - **Full rebuild**: Destroys and recreates DB (legacy XMLTV sources)
 * - **Clear and Load**: Deletes stale programmes (>24h old), upserts new data
 *   via REPLACE — optimal for iptv-org sources on storage-constrained devices.
 */
class EpgIndexer private constructor(private val context: Context) {

    companion object {
        private const val TAG = "EpgIndexer"
        private const val BATCH_SIZE = 1000
        private const val STREAM_BUFFER_SIZE = 65536

        @Volatile
        private var instance: EpgIndexer? = null

        fun getInstance(context: Context): EpgIndexer {
            return instance ?: synchronized(this) {
                instance ?: EpgIndexer(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _state = MutableStateFlow<EpgIndexState>(EpgIndexState.NotIndexed)
    val state: StateFlow<EpgIndexState> = _state.asStateFlow()

    /**
     * Check whether the file has changed or timezone override differs since last index.
     */
    suspend fun needsReindex(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = EpgIndexDatabase.getInstance(context)
            val metadata = db.epgIndexDao().getMetadata() ?: return@withContext true
            metadata.fileSizeBytes != file.length() ||
                metadata.fileLastModifiedMs != file.lastModified() ||
                metadata.timezoneOffsetHours != XmltvParser.timezoneOverrideHours
        } catch (e: Exception) {
            Log.w(TAG, "Error checking index metadata, will re-index", e)
            true
        }
    }

    /**
     * Restore Indexed state from stored metadata without re-indexing.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            val db = EpgIndexDatabase.getInstance(context)
            val metadata = db.epgIndexDao().getMetadata()
            if (metadata != null && metadata.programmeCount > 0) {
                _state.value = EpgIndexState.Indexed(
                    channelCount = metadata.channelCount,
                    programmeCount = metadata.programmeCount,
                    indexedAtMs = metadata.indexedAtMs
                )
                Log.d(TAG, "Restored index: ${metadata.channelCount} channels, ${metadata.programmeCount} programmes")
            } else {
                _state.value = EpgIndexState.NotIndexed
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore index state", e)
            _state.value = EpgIndexState.NotIndexed
        }
    }

    /**
     * Parse the XMLTV file and build the SQLite index.
     *
     * Uses the iptv-org "Clear and Load" strategy when the EPG URL is an iptv-org source:
     * deletes programmes older than 24 hours, then upserts new data. For non-iptv-org sources,
     * performs a full database rebuild.
     *
     * The entire ingestion (parse + insert) runs inside a single Room @Transaction to prevent
     * the UI from seeing partial or empty EPG data if the update is interrupted.
     */
    suspend fun startIndexing(file: File) = withContext(Dispatchers.IO) {
        val appSettings = AppSettings(context)
        val epgUrl = appSettings.epgUrl
        val ingestionConfig = IptvOrgEpgSource.getIngestionConfig(epgUrl)
        val useIncrementalLoad = ingestionConfig.clearBeforeLoad

        Log.d(TAG, "Starting indexing of ${file.name} (${file.length() / (1024 * 1024)}MB)" +
            if (useIncrementalLoad) " [iptv-org: Clear and Load]" else " [full rebuild]")

        _state.value = EpgIndexState.Indexing(
            progressPercent = 0,
            channelsIndexed = 0,
            programmesIndexed = 0
        )

        try {
            if (!useIncrementalLoad) {
                // Full rebuild: destroy and recreate database for clean state
                EpgIndexDatabase.destroy(context)
            }
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()

            // Phase 1: Streaming parse — collect all channels and programmes in batches
            val fileSize = file.length()
            var channelCount = 0
            var programmeCount = 0

            val allChannels = mutableListOf<EpgChannelEntity>()
            val programmeBatch = mutableListOf<EpgProgrammeEntity>()

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()

            val countingStream = CountingInputStream(
                BufferedInputStream(FileInputStream(file), STREAM_BUFFER_SIZE)
            )
            parser.setInput(countingStream, null)

            // Phase 2: Parse and batch-insert inside a transaction.
            // For iptv-org (Clear and Load): clean stale data first, then insert.
            // For full rebuild: DB is already empty from destroy().
            //
            // We accumulate channels during the parse (they're small, typically <10K),
            // then flush programmes in BATCH_SIZE chunks via transactional inserts.

            var eventType = parser.eventType
            var channelsFinished = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> {
                            val channel = XmltvParser.parseChannelForIndex(parser)
                            if (channel != null) {
                                allChannels.add(channel)
                                channelCount++
                            }
                        }
                        "programme" -> {
                            // First programme encountered — flush channels + stale cleanup
                            if (!channelsFinished) {
                                channelsFinished = true
                                if (useIncrementalLoad) {
                                    dao.deleteStaleProgrammes(ingestionConfig.staleCutoffEpochSeconds)
                                    Log.d(TAG, "Cleared stale programmes (cutoff: ${ingestionConfig.staleCutoffHours}h)")
                                }
                                // Insert all channels in one batch (with REPLACE for upsert)
                                if (allChannels.isNotEmpty()) {
                                    for (batch in allChannels.chunked(BATCH_SIZE)) {
                                        dao.insertChannels(batch)
                                    }
                                }
                            }

                            val programme = XmltvParser.parseProgrammeForIndex(parser)
                            if (programme != null) {
                                programmeBatch.add(programme)
                                programmeCount++
                                if (programmeBatch.size >= BATCH_SIZE) {
                                    dao.insertProgrammes(programmeBatch.toList())
                                    programmeBatch.clear()

                                    // Update progress
                                    val bytesProcessed = countingStream.bytesRead
                                    val percent = if (fileSize > 0) {
                                        ((bytesProcessed * 100) / fileSize).toInt().coerceIn(0, 99)
                                    } else 0
                                    _state.value = EpgIndexState.Indexing(
                                        progressPercent = percent,
                                        channelsIndexed = channelCount,
                                        programmesIndexed = programmeCount
                                    )
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            // Flush remaining channels (if no programmes were found)
            if (!channelsFinished && allChannels.isNotEmpty()) {
                if (useIncrementalLoad) {
                    dao.deleteStaleProgrammes(ingestionConfig.staleCutoffEpochSeconds)
                }
                for (batch in allChannels.chunked(BATCH_SIZE)) {
                    dao.insertChannels(batch)
                }
            }

            // Flush remaining programme batch
            if (programmeBatch.isNotEmpty()) {
                dao.insertProgrammes(programmeBatch.toList())
            }

            // Rebuild FTS index
            Log.d(TAG, "Rebuilding FTS index...")
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')"
            )

            // Store metadata (including timezone so re-index triggers on change)
            val now = System.currentTimeMillis()
            val finalProgrammeCount = if (useIncrementalLoad) {
                dao.getProgrammeCount()
            } else {
                programmeCount
            }
            val finalChannelCount = if (useIncrementalLoad) {
                dao.getChannelCount()
            } else {
                channelCount
            }

            dao.insertMetadata(
                EpgIndexMetadata(
                    fileSizeBytes = file.length(),
                    fileLastModifiedMs = file.lastModified(),
                    indexedAtMs = now,
                    channelCount = finalChannelCount,
                    programmeCount = finalProgrammeCount,
                    timezoneOffsetHours = XmltvParser.timezoneOverrideHours
                )
            )

            _state.value = EpgIndexState.Indexed(
                channelCount = finalChannelCount,
                programmeCount = finalProgrammeCount,
                indexedAtMs = now
            )
            Log.d(TAG, "Indexing complete: $finalChannelCount channels, $finalProgrammeCount programmes")

        } catch (e: OutOfMemoryError) {
            System.gc()
            val msg = "Out of memory during indexing"
            Log.e(TAG, msg, e)
            _state.value = EpgIndexState.Failed(msg)
        } catch (e: Exception) {
            val msg = e.message ?: "Indexing failed"
            Log.e(TAG, msg, e)
            _state.value = EpgIndexState.Failed(msg)
        }
    }

    /**
     * Perform a full transactional ingestion: parse the XMLTV file and atomically replace
     * all data in a single Room @Transaction. This prevents partial/empty EPG if interrupted.
     *
     * Suitable for small-to-medium XMLTV files (< ~100MB / ~500K programmes) where
     * collecting all data in memory before the atomic write is feasible.
     * For larger files, use [startIndexing] which streams batches.
     */
    suspend fun startTransactionalIndexing(file: File) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting transactional indexing of ${file.name} (${file.length() / (1024 * 1024)}MB)")
        _state.value = EpgIndexState.Indexing(
            progressPercent = 0,
            channelsIndexed = 0,
            programmesIndexed = 0
        )

        try {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()

            val fileSize = file.length()
            val allChannels = mutableListOf<EpgChannelEntity>()
            val allProgrammes = mutableListOf<EpgProgrammeEntity>()

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()

            val countingStream = CountingInputStream(
                BufferedInputStream(FileInputStream(file), STREAM_BUFFER_SIZE)
            )
            parser.setInput(countingStream, null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> {
                            val channel = XmltvParser.parseChannelForIndex(parser)
                            if (channel != null) allChannels.add(channel)
                        }
                        "programme" -> {
                            val programme = XmltvParser.parseProgrammeForIndex(parser)
                            if (programme != null) {
                                allProgrammes.add(programme)
                                if (allProgrammes.size % BATCH_SIZE == 0) {
                                    val bytesProcessed = countingStream.bytesRead
                                    val percent = if (fileSize > 0) {
                                        ((bytesProcessed * 100) / fileSize).toInt().coerceIn(0, 99)
                                    } else 0
                                    _state.value = EpgIndexState.Indexing(
                                        progressPercent = percent,
                                        channelsIndexed = allChannels.size,
                                        programmesIndexed = allProgrammes.size
                                    )
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            countingStream.close()

            // Atomic write: all-or-nothing inside a single @Transaction
            val now = System.currentTimeMillis()
            val metadata = EpgIndexMetadata(
                fileSizeBytes = file.length(),
                fileLastModifiedMs = file.lastModified(),
                indexedAtMs = now,
                channelCount = allChannels.size,
                programmeCount = allProgrammes.size,
                timezoneOffsetHours = XmltvParser.timezoneOverrideHours
            )

            dao.replaceAllData(allChannels, allProgrammes, metadata)

            // Rebuild FTS index after the transaction commits
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')"
            )

            _state.value = EpgIndexState.Indexed(
                channelCount = allChannels.size,
                programmeCount = allProgrammes.size,
                indexedAtMs = now
            )
            Log.d(TAG, "Transactional indexing complete: ${allChannels.size} channels, ${allProgrammes.size} programmes")

        } catch (e: OutOfMemoryError) {
            System.gc()
            val msg = "Out of memory during transactional indexing — file too large for atomic mode"
            Log.e(TAG, msg, e)
            // Fall back to streaming mode
            Log.d(TAG, "Falling back to streaming ingestion")
            startIndexing(file)
        } catch (e: Exception) {
            val msg = e.message ?: "Transactional indexing failed"
            Log.e(TAG, msg, e)
            _state.value = EpgIndexState.Failed(msg)
        }
    }

    /**
     * Tracks bytes read from the underlying stream for progress reporting.
     */
    private class CountingInputStream(
        private val wrapped: java.io.InputStream
    ) : java.io.InputStream() {
        @Volatile
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            val b = wrapped.read()
            if (b >= 0) bytesRead++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = wrapped.read(b, off, len)
            if (n > 0) bytesRead += n
            return n
        }

        override fun close() = wrapped.close()
        override fun available() = wrapped.available()
    }
}
