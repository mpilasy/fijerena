package org.njarasoa.fijerena.core.network.xmltv.epgindex

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.xmltv.XmltvParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Singleton that indexes XMLTV data into SQLite for fast FTS search.
 *
 * Streaming parse with batch INSERT (500 rows per batch) wrapped in
 * Room withTransaction for atomicity. Network drop or parse error
 * triggers transaction rollback — DB stays consistent.
 *
 * Append-only: uses REPLACE on unique (channel_id, start_epoch) index
 * so the database stays searchable during sync.
 */
class EpgIndexer private constructor(private val context: Context) {

    companion object {
        private const val TAG = "EpgIndexer"
        private const val BATCH_SIZE = 500
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
     * Ingestion stats for the most recent ingestFromStream() call.
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
     * Ingest EPG data from an InputStream into the SQLite index.
     *
     * All inserts are wrapped in a single Room transaction for atomicity.
     * On IOException or parse error, the transaction rolls back and the
     * database remains in its previous consistent state.
     *
     * Uses 500-row batch inserts for memory efficiency (~100KB per batch).
     */
    suspend fun ingestFromStream(
        inputStream: InputStream,
        sourceId: Long = 0,
        onProgress: ((channels: Int, programmes: Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Ingesting from stream (sourceId=$sourceId)")

        val db = EpgIndexDatabase.getInstance(context)
        val dao = db.epgIndexDao()
        var channelCount = 0
        var programmeCount = 0
        val wasIndexed = _state.value is EpgIndexState.Indexed
        if (!wasIndexed) {
            _state.value = EpgIndexState.Indexing(
                progressPercent = 0,
                channelsIndexed = 0,
                programmesIndexed = 0
            )
        }

        try {
            db.withTransaction {
                val channelBatch = mutableListOf<EpgChannelEntity>()
                val programmeBatch = mutableListOf<EpgProgrammeEntity>()
                var channelsFlushed = false

                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = false
                val parser = factory.newPullParser()
                parser.setInput(inputStream, null)

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "channel" -> {
                                XmltvParser.parseChannelForIndex(parser)?.let {
                                    channelBatch.add(it)
                                    channelCount++
                                    if (channelBatch.size >= BATCH_SIZE) {
                                        dao.insertChannelsIgnore(channelBatch.toList())
                                        channelBatch.clear()
                                    }
                                }
                            }
                            "programme" -> {
                                if (!channelsFlushed) {
                                    channelsFlushed = true
                                    if (channelBatch.isNotEmpty()) {
                                        dao.insertChannelsIgnore(channelBatch.toList())
                                        channelBatch.clear()
                                    }
                                }
                                XmltvParser.parseProgrammeForIndex(parser, sourceId)?.let {
                                    programmeBatch.add(it)
                                    programmeCount++
                                    if (programmeBatch.size >= BATCH_SIZE) {
                                        dao.insertProgrammes(programmeBatch.toList())
                                        programmeBatch.clear()
                                        onProgress?.invoke(channelCount, programmeCount)
                                        if (!wasIndexed) {
                                            _state.value = EpgIndexState.Indexing(
                                                progressPercent = 0,
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

                // Flush remaining
                if (channelBatch.isNotEmpty()) dao.insertChannelsIgnore(channelBatch.toList())
                if (programmeBatch.isNotEmpty()) dao.insertProgrammes(programmeBatch.toList())
            }

            lastIngestionStats = IngestionStats(channelCount, programmeCount)
            Log.d(TAG, "Stream ingestion complete: $channelCount channels, $programmeCount programmes")

        } catch (e: OutOfMemoryError) {
            System.gc()
            val msg = "Out of memory during stream indexing"
            Log.e(TAG, msg, e)
            lastIngestionStats = IngestionStats()
            if (!wasIndexed) _state.value = EpgIndexState.Failed(msg)
        } catch (e: Exception) {
            val msg = e.message ?: "Stream indexing failed"
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
}
