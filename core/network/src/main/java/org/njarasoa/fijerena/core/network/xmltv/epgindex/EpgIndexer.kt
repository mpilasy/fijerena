package org.njarasoa.fijerena.core.network.xmltv.epgindex

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.xmltv.XmltvParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Singleton that indexes XMLTV files into SQLite for fast FTS search.
 *
 * Streaming parse with batch INSERT (1000 rows per batch).
 * Memory bounded: ~200KB per batch.
 *
 * Append-only: uses REPLACE on unique (channel_id, start_epoch) index
 * so the database stays searchable during sync. No clearing needed.
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
     * Ingestion stats for the most recent ingestFile() call.
     * Reset at the start of each ingestion.
     */
    data class IngestionStats(
        val channelsIngested: Int = 0,
        val programmesIngested: Int = 0
    )

    @Volatile
    var lastIngestionStats: IngestionStats = IngestionStats()
        private set

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
     * Parse the XMLTV file and append/replace data into the SQLite index.
     *
     * Append-only: uses IGNORE for channels (preserves existing) and REPLACE
     * for programmes (unique index on channel_id + start_epoch deduplicates).
     * The database remains searchable throughout ingestion.
     */
    suspend fun startIndexing(file: File, sourceId: Long = 0) = ingestFile(file, sourceId)

    /**
     * Append data from a file without clearing existing data.
     * Uses IGNORE for channels and REPLACE for programmes.
     *
     * @param file The XMLTV file to parse
     * @param timezoneOverride Per-source timezone offset applied during parsing
     */
    suspend fun appendFromFile(file: File, timezoneOverride: Int, sourceId: Long = 0) {
        val previousTz = XmltvParser.timezoneOverrideHours
        XmltvParser.timezoneOverrideHours = timezoneOverride
        try {
            ingestFile(file, sourceId)
        } finally {
            XmltvParser.timezoneOverrideHours = previousTz
        }
    }

    /**
     * Core ingestion: streaming parse + batch insert.
     * Append-only — never clears existing data.
     */
    private suspend fun ingestFile(file: File, sourceId: Long = 0) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Ingesting ${file.name} (${file.length() / (1024 * 1024)}MB)")

        // Only switch to Indexing state if no data exists yet.
        // When refreshing with existing data, keep Indexed state so the
        // database remains usable (EPG browser, TV guide, search all work).
        val wasIndexed = _state.value is EpgIndexState.Indexed
        if (!wasIndexed) {
            _state.value = EpgIndexState.Indexing(
                progressPercent = 0,
                channelsIndexed = 0,
                programmesIndexed = 0
            )
        }

        try {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()

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
                            // First programme encountered — flush channels (IGNORE keeps existing)
                            if (!channelsFinished) {
                                channelsFinished = true
                                if (allChannels.isNotEmpty()) {
                                    for (batch in allChannels.chunked(BATCH_SIZE)) {
                                        dao.insertChannelsIgnore(batch)
                                    }
                                }
                            }

                            val programme = XmltvParser.parseProgrammeForIndex(parser, sourceId)
                            if (programme != null) {
                                programmeBatch.add(programme)
                                programmeCount++
                                if (programmeBatch.size >= BATCH_SIZE) {
                                    dao.insertProgrammes(programmeBatch.toList())
                                    programmeBatch.clear()

                                    val bytesProcessed = countingStream.bytesRead
                                    val percent = if (fileSize > 0) {
                                        ((bytesProcessed * 100) / fileSize).toInt().coerceIn(0, 99)
                                    } else 0
                                    if (!wasIndexed) {
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
                }
                eventType = parser.next()
            }

            // Flush remaining channels
            if (!channelsFinished && allChannels.isNotEmpty()) {
                for (batch in allChannels.chunked(BATCH_SIZE)) {
                    dao.insertChannelsIgnore(batch)
                }
            }

            // Flush remaining programme batch
            if (programmeBatch.isNotEmpty()) {
                dao.insertProgrammes(programmeBatch.toList())
            }

            // Record per-file stats
            lastIngestionStats = IngestionStats(
                channelsIngested = channelCount,
                programmesIngested = programmeCount
            )

            Log.d(TAG, "Ingestion complete: $channelCount channels, $programmeCount programmes")

        } catch (e: OutOfMemoryError) {
            System.gc()
            val msg = "Out of memory during indexing"
            Log.e(TAG, msg, e)
            lastIngestionStats = IngestionStats()
            if (!wasIndexed) _state.value = EpgIndexState.Failed(msg)
        } catch (e: Exception) {
            val msg = e.message ?: "Indexing failed"
            Log.e(TAG, msg, e)
            lastIngestionStats = IngestionStats()
            if (!wasIndexed) _state.value = EpgIndexState.Failed(msg)
        }
    }

    /**
     * Rebuild FTS index and update metadata/state from current DB contents.
     */
    suspend fun rebuildFtsAndUpdateState() = withContext(Dispatchers.IO) {
        try {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()

            Log.d(TAG, "Rebuilding FTS index...")
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')"
            )

            val now = System.currentTimeMillis()
            val finalChannelCount = dao.getChannelCount()
            val finalProgrammeCount = dao.getProgrammeCount()

            dao.insertMetadata(
                EpgIndexMetadata(
                    fileSizeBytes = 0,
                    fileLastModifiedMs = 0,
                    indexedAtMs = now,
                    channelCount = finalChannelCount,
                    programmeCount = finalProgrammeCount,
                    timezoneOffsetHours = 0
                )
            )

            _state.value = EpgIndexState.Indexed(
                channelCount = finalChannelCount,
                programmeCount = finalProgrammeCount,
                indexedAtMs = now
            )
            Log.d(TAG, "FTS rebuild complete: $finalChannelCount channels, $finalProgrammeCount programmes")
        } catch (e: Exception) {
            Log.e(TAG, "FTS rebuild failed: ${e.message}", e)
            _state.value = EpgIndexState.Failed(e.message ?: "FTS rebuild failed")
        }
    }

    /**
     * Clear all EPG data from the database and reset state to NotIndexed.
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        try {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()
            dao.deleteAllProgrammes()
            dao.deleteAllChannels()
            dao.deleteAllMetadata()
            incrementalVacuum()
            _state.value = EpgIndexState.NotIndexed
            Log.d(TAG, "All EPG data cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear EPG data: ${e.message}", e)
        }
    }

    /**
     * Delete programmes older than the given epoch and rebuild FTS.
     */
    suspend fun purgeOldProgrammes(cutoffEpoch: Long) = withContext(Dispatchers.IO) {
        try {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()
            dao.deleteStaleProgrammes(cutoffEpoch)

            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')"
            )

            val now = System.currentTimeMillis()
            val channelCount = dao.getChannelCount()
            val programmeCount = dao.getProgrammeCount()

            dao.insertMetadata(
                EpgIndexMetadata(
                    fileSizeBytes = 0,
                    fileLastModifiedMs = 0,
                    indexedAtMs = now,
                    channelCount = channelCount,
                    programmeCount = programmeCount,
                    timezoneOffsetHours = 0
                )
            )

            _state.value = if (programmeCount > 0) {
                EpgIndexState.Indexed(channelCount, programmeCount, now)
            } else {
                EpgIndexState.NotIndexed
            }

            incrementalVacuum()
            Log.d(TAG, "Purge complete: $channelCount channels, $programmeCount programmes remaining")
        } catch (e: Exception) {
            Log.e(TAG, "Purge failed: ${e.message}", e)
        }
    }

    /**
     * Reclaim free pages left by delete-heavy operations.
     * Requires auto_vacuum=INCREMENTAL (set in EpgIndexDatabase onOpen callback).
     * No page limit = free all available pages.
     */
    fun incrementalVacuum() {
        try {
            val db = EpgIndexDatabase.getInstance(context)
            db.openHelper.writableDatabase.execSQL("PRAGMA incremental_vacuum")
            Log.d(TAG, "Incremental vacuum completed")
        } catch (e: Exception) {
            Log.w(TAG, "Incremental vacuum failed: ${e.message}", e)
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
