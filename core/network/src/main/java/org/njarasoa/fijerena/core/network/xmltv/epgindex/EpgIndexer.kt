package org.njarasoa.fijerena.core.network.xmltv.epgindex

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.xmltv.XmltvParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.njarasoa.fijerena.core.player.model.EpgResponse
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
     * Uses 500-row batch inserts for memory efficiency (~100KB per batch).
     * Commits transactions every 5000 items to prevent Room/SQLite from buffering
     * too much data in memory for a single massive transaction.
     */
    /**
     * Set state to Indexing if not already Indexed.
     * Call once before parallel ingestion begins.
     */
    fun setIndexing() {
        if (_state.value !is EpgIndexState.Indexed) {
            _state.value = EpgIndexState.Indexing(
                progressPercent = 0,
                channelsIndexed = 0,
                programmesIndexed = 0
            )
        }
    }

    suspend fun ingestFromStream(
        inputStream: InputStream,
        sourceId: Long = 0,
        timezoneOverrideHours: Int = 0,
        onProgress: ((channels: Int, programmes: Int) -> Unit)? = null
    ): IngestionStats = withContext(Dispatchers.IO) {
        Log.d(TAG, "Ingesting from stream (sourceId=$sourceId, tz=$timezoneOverrideHours)")

        val db = EpgIndexDatabase.getInstance(context)
        val dao = db.epgIndexDao()
        var channelCount = 0
        var programmeCount = 0

        // Skip programmes that ended before yesterday
        val cutoffEpoch = (System.currentTimeMillis() / 1000) - 86400

        try {
            val channelBatch = mutableListOf<EpgChannelEntity>()
            val programmeBatch = mutableListOf<EpgProgrammeEntity>()
            var itemsSinceLastProgressUpdate = 0

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
                                    db.withTransaction {
                                        dao.insertChannelsIgnore(channelBatch.toList())
                                    }
                                    channelBatch.clear()
                                    // Yield CPU briefly to other tasks (like video playback)
                                    delay(5)
                                }
                            }
                        }
                        "programme" -> {
                            // Flush any remaining channels before starting programmes
                            if (channelBatch.isNotEmpty()) {
                                db.withTransaction {
                                    dao.insertChannelsIgnore(channelBatch.toList())
                                }
                                channelBatch.clear()
                            }

                            XmltvParser.parseProgrammeForIndex(parser, sourceId, timezoneOverrideHours)?.let {
                                if (it.endEpoch < cutoffEpoch) return@let
                                programmeBatch.add(it)
                                programmeCount++
                                itemsSinceLastProgressUpdate++

                                if (programmeBatch.size >= BATCH_SIZE) {
                                    db.withTransaction {
                                        dao.insertProgrammes(programmeBatch.toList())
                                    }
                                    programmeBatch.clear()

                                    // Throttled UI updates to reduce main thread pressure during playback
                                    if (itemsSinceLastProgressUpdate >= 50000) {
                                        onProgress?.invoke(channelCount, programmeCount)
                                        itemsSinceLastProgressUpdate = 0
                                    }

                                    // Yield CPU more aggressively to other tasks (like video decoding)
                                    delay(100)
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            // Flush remaining
            if (channelBatch.isNotEmpty()) {
                db.withTransaction { dao.insertChannelsIgnore(channelBatch.toList()) }
            }
            if (programmeBatch.isNotEmpty()) {
                db.withTransaction { dao.insertProgrammes(programmeBatch.toList()) }
            }

            val stats = IngestionStats(channelCount, programmeCount)
            lastIngestionStats = stats
            Log.d(TAG, "Stream ingestion complete: $channelCount channels, $programmeCount programmes")
            stats

        } catch (e: OutOfMemoryError) {
            System.gc()
            val msg = "Out of memory during stream indexing ($channelCount ch, $programmeCount prg ingested before failure)"
            Log.e(TAG, msg, e)
            lastIngestionStats = IngestionStats(channelCount, programmeCount)
            throw java.io.IOException(msg, e)
        } catch (e: Exception) {
            val msg = "Stream indexing failed: ${e.message} ($channelCount ch, $programmeCount prg ingested before failure)"
            Log.e(TAG, msg, e)
            lastIngestionStats = IngestionStats(channelCount, programmeCount)
            throw e
        }
    }

    data class XtreamStreamInfo(
        val streamId: Int,
        val name: String,
        val epgChannelId: String?,
        val iconUrl: String?
    )

    /**
     * Ingest EPG data fetched from the Xtream API into the SQLite index.
     *
     * Creates/upserts an EpgSource with ingestMethod=XTREAM_API.
     * Deletes old programmes for that source before inserting fresh data.
     * Channels are inserted with IGNORE to avoid overwriting XMLTV channels.
     */
    suspend fun ingestFromXtreamEpg(
        epgByStreamId: Map<Int, EpgResponse>,
        streamInfo: Map<Int, XtreamStreamInfo>,
        providerId: Long
    ) = withContext(Dispatchers.IO) {
        if (epgByStreamId.isEmpty()) return@withContext

        Log.d(TAG, "Ingesting Xtream EPG: ${epgByStreamId.size} streams for provider $providerId")

        try {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()
            val sourceDao = db.epgSourceDao()

            // Upsert EpgSource
            val sourceUrl = "xtream://$providerId"
            val existingSource = sourceDao.getSourceByUrl(sourceUrl)
            val sourceId = if (existingSource != null) {
                existingSource.id
            } else {
                sourceDao.insertSource(
                    EpgSourceEntity(
                        url = sourceUrl,
                        label = "Xtream Provider $providerId",
                        ingestMethod = "XTREAM_API"
                    )
                )
            }

            // Clean slate for this source
            dao.deleteBySourceId(sourceId)

            // Skip programmes that ended before yesterday
            val cutoffEpoch = (System.currentTimeMillis() / 1000) - 86400

            // Build channels and programmes
            val channelEntities = mutableListOf<EpgChannelEntity>()
            val programmeBatch = mutableListOf<EpgProgrammeEntity>()
            var totalProgrammes = 0

            for ((streamId, epgResponse) in epgByStreamId) {
                val info = streamInfo[streamId] ?: continue
                val channelId = info.epgChannelId ?: streamId.toString()

                channelEntities.add(
                    EpgChannelEntity(
                        xmltvId = channelId,
                        displayName = info.name,
                        iconUrl = info.iconUrl
                    )
                )

                for (prog in epgResponse.listings) {
                    if (prog.endTime < cutoffEpoch) continue
                    programmeBatch.add(
                        EpgProgrammeEntity(
                            channelId = channelId,
                            title = prog.title,
                            titleLowercase = prog.title.lowercase(),
                            description = prog.description,
                            startEpoch = prog.startTime,
                            endEpoch = prog.endTime,
                            sourceId = sourceId
                        )
                    )
                    totalProgrammes++

                    if (programmeBatch.size >= BATCH_SIZE) {
                        db.withTransaction { dao.insertProgrammes(programmeBatch.toList()) }
                        programmeBatch.clear()
                    }
                }
            }

            // Flush remaining
            if (channelEntities.isNotEmpty()) {
                db.withTransaction { dao.insertChannelsIgnore(channelEntities) }
            }
            if (programmeBatch.isNotEmpty()) {
                db.withTransaction { dao.insertProgrammes(programmeBatch.toList()) }
            }

            // Update source stats
            sourceDao.markIngested(
                id = sourceId,
                timestamp = System.currentTimeMillis(),
                channels = channelEntities.size,
                programmes = totalProgrammes,
                downloadBytes = 0,
                ingestMethod = "XTREAM_API"
            )

            lastIngestionStats = IngestionStats(channelEntities.size, totalProgrammes)
            Log.d(TAG, "Xtream EPG ingestion complete: ${channelEntities.size} channels, $totalProgrammes programmes")

            rebuildFtsAndUpdateState()
        } catch (e: Exception) {
            Log.e(TAG, "Xtream EPG ingestion failed: ${e.message}", e)
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
            // Save sources before destroying DB — they're user config, not EPG data
            val oldDb = EpgIndexDatabase.getInstance(context)
            val savedSources = oldDb.epgSourceDao().getAllSourcesOnce()
            Log.d(TAG, "Clearing EPG: saved ${savedSources.size} sources, destroying database...")

            // Close DB and delete file — instant regardless of data size
            EpgIndexDatabase.destroy(context)

            // Reopen: Room recreates all tables from schema
            val newDb = EpgIndexDatabase.getInstance(context)
            Log.d(TAG, "Clearing EPG: database recreated, restoring sources...")

            // Restore sources with stats reset
            for (source in savedSources) {
                newDb.epgSourceDao().insertSource(
                    source.copy(
                        lastIngestedAtMs = 0,
                        lastChannels = 0,
                        lastProgrammes = 0,
                        lastDownloadBytes = 0,
                        lastError = null
                    )
                )
            }

            _state.value = EpgIndexState.NotIndexed
            Log.d(TAG, "All EPG data cleared successfully (${savedSources.size} sources restored)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear EPG data: ${e.message}", e)
        }
    }

    /**
     * Delete programmes older than the given epoch and rebuild FTS.
     */
    suspend fun countStaleProgrammes(cutoffEpoch: Long): Int = withContext(Dispatchers.IO) {
        try {
            EpgIndexDatabase.getInstance(context).epgIndexDao().countStaleProgrammes(cutoffEpoch)
        } catch (e: Exception) {
            0
        }
    }

    suspend fun purgeOldProgrammes(cutoffEpoch: Long): Int = withContext(Dispatchers.IO) {
        try {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()
            val countBefore = dao.getProgrammeCount()
            dao.deleteStaleProgrammes(cutoffEpoch)

            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')"
            )

            val now = System.currentTimeMillis()
            val channelCount = dao.getChannelCount()
            val programmeCount = dao.getProgrammeCount()
            val deleted = countBefore - programmeCount

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
            Log.d(TAG, "Purge complete: $deleted deleted, $channelCount channels, $programmeCount programmes remaining")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Purge failed: ${e.message}", e)
            0
        }
    }

    /**
     * Reclaim free pages left by delete-heavy operations.
     * Requires auto_vacuum=INCREMENTAL (set in EpgIndexDatabase onOpen callback).
     * No page limit = free all available pages.
     */
    /**
     * Get the number of configured EPG sources (regardless of index state).
     */
    suspend fun getSourceCount(): Int = withContext(Dispatchers.IO) {
        try {
            val db = EpgIndexDatabase.getInstance(context)
            db.epgSourceDao().getSourceCount()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get source count: ${e.message}")
            0
        }
    }

    fun incrementalVacuum() {
        try {
            val db = EpgIndexDatabase.getInstance(context)
            // Use query() instead of execSQL() — Android's SQLite wrapper rejects
            // execSQL for PRAGMAs that may return results.
            db.openHelper.writableDatabase.query("PRAGMA incremental_vacuum").close()
            Log.d(TAG, "Incremental vacuum completed")
        } catch (e: Exception) {
            Log.w(TAG, "Incremental vacuum failed: ${e.message}", e)
        }
    }
}
