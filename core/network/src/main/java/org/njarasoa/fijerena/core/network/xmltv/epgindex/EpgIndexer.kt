package org.njarasoa.fijerena.core.network.xmltv.epgindex
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xmltv.XmltvParser
import org.njarasoa.fijerena.core.player.model.EpgResponse
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton that indexes XMLTV data into SQLite for fast FTS search.
 *
 * Streaming parse with batch INSERT (500 rows per batch) wrapped in
 * Room withTransaction for atomicity. Network drop or parse error
 * triggers transaction rollback — DB stays consistent.
 *
 * Append-only: uses REPLACE on unique (channel_id, source_id, start_epoch) index
 * so the database stays searchable during sync.
 */
class EpgIndexer private constructor(
    private val context: Context,
) {
    private val writeMutex = Mutex()

    private val ftsStale = AtomicBoolean(false)
    private val stalePrefs by lazy {
        context.getSharedPreferences("epg_indexer_state", Context.MODE_PRIVATE)
    }

    fun isFtsStale(): Boolean = ftsStale.get()

    fun markFtsStale() {
        ftsStale.set(true)
        stalePrefs.edit { putBoolean("fts_stale", true) }
    }

    fun markFtsClean() {
        ftsStale.set(false)
        stalePrefs.edit { remove("fts_stale") }
    }

    companion object {
        private const val TAG = "EpgIndexer"
        const val BATCH_SIZE_MOBILE = 500
        const val BATCH_SIZE_TV = 5000
        private const val BATCH_SIZE = BATCH_SIZE_MOBILE
        private const val STREAM_BUFFER_SIZE = 65536

        // Room-generated FTS content-sync trigger names for epg_programme_fts.
        // These are created in onCreate and kept alive in the SQLite file.
        // We drop them before bulk ingestion and recreate afterwards so that
        // per-row FTS maintenance is skipped entirely; a single rebuild() at
        // the end of the session is cheaper than millions of incremental updates.
        private val FTS_TRIGGER_NAMES =
            listOf(
                "room_fts_content_sync_epg_programme_fts_BEFORE_UPDATE",
                "room_fts_content_sync_epg_programme_fts_BEFORE_DELETE",
                "room_fts_content_sync_epg_programme_fts_AFTER_UPDATE",
                "room_fts_content_sync_epg_programme_fts_AFTER_INSERT",
            )

        // FTS5 uses `rowid` instead of `docid` in content-table triggers.
        private val FTS_TRIGGER_DDL =
            listOf(
                "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_epg_programme_fts_BEFORE_UPDATE` BEFORE UPDATE ON `epg_programme` BEGIN DELETE FROM `epg_programme_fts` WHERE `rowid`=OLD.`rowid`; END",
                "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_epg_programme_fts_BEFORE_DELETE` BEFORE DELETE ON `epg_programme` BEGIN DELETE FROM `epg_programme_fts` WHERE `rowid`=OLD.`rowid`; END",
                "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_epg_programme_fts_AFTER_UPDATE` AFTER UPDATE ON `epg_programme` BEGIN INSERT INTO `epg_programme_fts`(`rowid`,`title`) VALUES (NEW.`rowid`,NEW.`title`); END",
                "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_epg_programme_fts_AFTER_INSERT` AFTER INSERT ON `epg_programme` BEGIN INSERT INTO `epg_programme_fts`(`rowid`,`title`) VALUES (NEW.`rowid`,NEW.`title`); END",
            )

        // Query-only indexes on epg_programme that are dropped during bulk
        // ingestion and rebuilt afterwards.  The dedup unique index is kept
        // because INSERT … REPLACE needs it for conflict resolution.
        private val BULK_DROP_INDEX_NAMES =
            listOf(
                "idx_programme_start",
                "idx_programme_end",
                "idx_programme_time_range",
                "idx_programme_channel",
                "idx_programme_title_lower",
                "idx_programme_source",
                "idx_programme_channel_source",
            )
        private val BULK_DROP_INDEX_DDL =
            listOf(
                "CREATE INDEX IF NOT EXISTS `idx_programme_start` ON `epg_programme` (`start_epoch`)",
                "CREATE INDEX IF NOT EXISTS `idx_programme_end` ON `epg_programme` (`end_epoch`)",
                "CREATE INDEX IF NOT EXISTS `idx_programme_time_range` ON `epg_programme` (`start_epoch`, `end_epoch`)",
                "CREATE INDEX IF NOT EXISTS `idx_programme_channel` ON `epg_programme` (`channel_id`)",
                "CREATE INDEX IF NOT EXISTS `idx_programme_title_lower` ON `epg_programme` (`title_lowercase`)",
                "CREATE INDEX IF NOT EXISTS `idx_programme_source` ON `epg_programme` (`source_id`)",
                "CREATE INDEX IF NOT EXISTS `idx_programme_channel_source` ON `epg_programme` (`channel_id`, `source_id`)",
            )

        @Volatile
        private var instance: EpgIndexer? = null

        fun getInstance(context: Context): EpgIndexer =
            instance ?: synchronized(this) {
                instance ?: EpgIndexer(context.applicationContext).also { instance = it }
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
        val programmesIngested: Int = 0,
    )

    @Volatile
    var lastIngestionStats: IngestionStats = IngestionStats()
        private set

    /**
     * Restore Indexed state from stored metadata without re-indexing.
     * Also restores the ftsStale flag so a background rebuild can be resumed if
     * the previous session was interrupted before the FTS index was refreshed.
     * Returns true if the FTS index is stale and a background rebuild should be launched.
     */
    suspend fun initialize(): Boolean =
        withContext(Dispatchers.IO) {
            val wasStale = stalePrefs.getBoolean("fts_stale", false)
            if (wasStale) {
                ftsStale.set(true)
            }
            try {
                val db = EpgIndexDatabase.getInstance(context)
                val dao = db.epgIndexDao()
                val metadata = dao.getMetadata()

                if (metadata != null && metadata.programmeCount > 0) {
                    _state.value =
                        EpgIndexState.Indexed(
                            channelCount = metadata.channelCount,
                            programmeCount = metadata.programmeCount,
                            indexedAtMs = metadata.indexedAtMs,
                        )
                } else {
                    // Fallback: check actual counts in case metadata is missing/out of sync
                    val actualChannels = dao.getChannelCount()
                    val actualProgrammes = dao.getProgrammeCount()
                    if (actualProgrammes > 0) {
                        val now = System.currentTimeMillis()
                        _state.value =
                            EpgIndexState.Indexed(
                                channelCount = actualChannels,
                                programmeCount = actualProgrammes,
                                indexedAtMs = now,
                            )
                        // Repair metadata table
                        dao.insertMetadata(
                            EpgIndexMetadata(
                                fileSizeBytes = 0,
                                fileLastModifiedMs = 0,
                                indexedAtMs = now,
                                channelCount = actualChannels,
                                programmeCount = actualProgrammes,
                                timezoneOffsetHours = 0,
                            ),
                        )
                    } else {
                        _state.value = EpgIndexState.NotIndexed
                    }
                }

            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore index state", e)
                _state.value = EpgIndexState.NotIndexed
            }
            wasStale
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
            _state.value =
                EpgIndexState.Indexing(
                    progressPercent = 0,
                    channelsIndexed = 0,
                    programmesIndexed = 0,
                )
        }
    }

    suspend fun ingestFromStream(
        inputStream: InputStream,
        sourceId: Long = 0,
        timezoneOverrideHours: Int = 0,
        batchSize: Int = BATCH_SIZE,
        isPlaybackActive: () -> Boolean = { false },
        onProgress: ((channels: Int, programmes: Int) -> Unit)? = null,
    ): IngestionStats =
        withContext(Dispatchers.IO) {
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
                                XmltvParser.parseChannelForIndex(parser, sourceId)?.let {
                                    channelBatch.add(it)
                                    channelCount++

                                    if (channelBatch.size >= batchSize) {
                                        writeMutex.withLock {
                                            db.withTransaction {
                                                dao.insertChannelsIgnore(channelBatch)
                                            }
                                        }
                                        channelBatch.clear()
                                        // Yield CPU briefly only when video is actively decoding
                                        if (isPlaybackActive()) delay(5)
                                    }
                                }
                            }
                            "programme" -> {
                                // Flush any remaining channels before starting programmes
                                if (channelBatch.isNotEmpty()) {
                                    writeMutex.withLock {
                                        db.withTransaction {
                                            dao.insertChannelsIgnore(channelBatch)
                                        }
                                    }
                                    channelBatch.clear()
                                }

                                XmltvParser.parseProgrammeForIndex(parser, sourceId, timezoneOverrideHours)?.let {
                                    if (it.endEpoch < cutoffEpoch) return@let
                                    programmeBatch.add(it)
                                    programmeCount++
                                    itemsSinceLastProgressUpdate++

                                    if (programmeBatch.size >= batchSize) {
                                        writeMutex.withLock {
                                            db.withTransaction {
                                                dao.insertProgrammes(programmeBatch)
                                            }
                                        }
                                        programmeBatch.clear()

                                        // Throttled UI updates to reduce main thread pressure during playback
                                        if (itemsSinceLastProgressUpdate >= 50000) {
                                            onProgress?.invoke(channelCount, programmeCount)
                                            itemsSinceLastProgressUpdate = 0
                                        }

                                        // Only yield when video is actively playing; skip the sleep entirely
                                        // when the Shield is idle to avoid the multi-hour ingestion caused
                                        // by accumulated 100ms sleeps across millions of programme rows.
                                        if (isPlaybackActive()) delay(100)
                                    }
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }

                // Flush remaining
                if (channelBatch.isNotEmpty()) {
                    writeMutex.withLock {
                        db.withTransaction { dao.insertChannelsIgnore(channelBatch) }
                    }
                }
                if (programmeBatch.isNotEmpty()) {
                    writeMutex.withLock {
                        db.withTransaction { dao.insertProgrammes(programmeBatch) }
                    }
                }

                val stats = IngestionStats(channelCount, programmeCount)
                lastIngestionStats = stats
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
        val iconUrl: String?,
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
        providerId: Long,
    ) = withContext(Dispatchers.IO) {
        if (epgByStreamId.isEmpty()) return@withContext

        val ingestStartMs = System.currentTimeMillis()

        try {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()
            val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()

            // Upsert EpgSource
            val sourceUrl = "xtream://$providerId"
            val (sourceId, existingSource) =
                writeMutex.withLock {
                    val existing = sourceDao.getSourceByUrl(sourceUrl)
                    if (existing != null) {
                        existing.id to existing
                    } else {
                        sourceDao.insertSource(
                            EpgSourceEntity(
                                url = sourceUrl,
                                label = "Xtream Provider $providerId",
                                ingestMethod = "XTREAM_API",
                            ),
                        ) to null
                    }
                }

            // Clean slate for this source in the index
            writeMutex.withLock {
                dao.deleteBySourceId(sourceId)
            }

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
                        iconUrl = info.iconUrl,
                        sourceId = sourceId,
                    ),
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
                            sourceId = sourceId,
                        ),
                    )
                    totalProgrammes++

                    if (programmeBatch.size >= BATCH_SIZE) {
                        writeMutex.withLock {
                            db.withTransaction { dao.insertProgrammes(programmeBatch) }
                        }
                        programmeBatch.clear()
                    }
                }
            }

            // Flush remaining
            if (channelEntities.isNotEmpty()) {
                writeMutex.withLock {
                    db.withTransaction { dao.insertChannelsIgnore(channelEntities) }
                }
            }
            if (programmeBatch.isNotEmpty()) {
                writeMutex.withLock {
                    db.withTransaction { dao.insertProgrammes(programmeBatch) }
                }
            }

            // Update source stats in Settings database
            writeMutex.withLock {
                sourceDao.markIngested(
                    id = sourceId,
                    timestamp = System.currentTimeMillis(),
                    channels = channelEntities.size,
                    programmes = totalProgrammes,
                    downloadBytes = 0,
                    ingestMethod = "XTREAM_API",
                    ingestionDurationMs = System.currentTimeMillis() - ingestStartMs,
                )
            }

            lastIngestionStats = IngestionStats(channelEntities.size, totalProgrammes)

            rebuildFtsAndUpdateState()
        } catch (e: Exception) {
            Log.e(TAG, "Xtream EPG ingestion failed: ${e.message}", e)
        }
    }

    /**
     * Rebuild FTS index and update metadata/state from current DB contents.
     */
    suspend fun rebuildFtsAndUpdateState() =
        withContext(Dispatchers.IO) {
            try {
                val db = EpgIndexDatabase.getInstance(context)
                val dao = db.epgIndexDao()

                writeMutex.withLock {
                    // Checkpoint WAL to reduce contention during rebuild
                    db.openHelper.writableDatabase
                        .query("PRAGMA wal_checkpoint(TRUNCATE)")
                        .close()

                    db.openHelper.writableDatabase.execSQL(
                        "INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')",
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
                            timezoneOffsetHours = 0,
                        ),
                    )

                    _state.value =
                        EpgIndexState.Indexed(
                            channelCount = finalChannelCount,
                            programmeCount = finalProgrammeCount,
                            indexedAtMs = now,
                        )
                }
            } catch (e: Exception) {
                Log.e(TAG, "FTS rebuild failed: ${e.message}", e)
                _state.value = EpgIndexState.Failed(e.message ?: "FTS rebuild failed")
            }
        }

    /**
     * Prepare the database for a bulk ingestion session:
     *  - Drop Room's per-row FTS sync triggers so that millions of inserts don't
     *    each update the FTS shadow table. A single rebuild() at the end is far
     *    cheaper than incremental maintenance.
     *  - Set synchronous=OFF to skip WAL fsync overhead during ingestion.
     *    Safe because EPG data is re-downloadable on crash.
     *
     * Always call endBulkIngestion() in a finally block to restore state.
     */
    suspend fun beginBulkIngestion() =
        withContext(Dispatchers.IO) {
            try {
                val db = EpgIndexDatabase.getInstance(context)
                db.openHelper.writableDatabase.apply {
                    FTS_TRIGGER_NAMES.forEach { name ->
                        execSQL("DROP TRIGGER IF EXISTS `$name`")
                    }
                    BULK_DROP_INDEX_NAMES.forEach { name ->
                        execSQL("DROP INDEX IF EXISTS `$name`")
                    }
                    execSQL("PRAGMA synchronous = OFF")
                    execSQL("PRAGMA temp_store = MEMORY")
                    execSQL("PRAGMA cache_size = -32000") // 32 MB during bulk
                }
            } catch (e: Exception) {
                Log.w(TAG, "beginBulkIngestion setup failed (non-fatal): ${e.message}", e)
            }
        }

    /**
     * Restore the database after a bulk ingestion session:
     *  - Recreate the Room FTS sync triggers that were dropped in beginBulkIngestion().
     *  - Restore synchronous=NORMAL.
     *
     * Call this before rebuildFtsAndUpdateState() so the triggers are in place
     * for all incremental updates after the session completes.
     */
    suspend fun endBulkIngestion() =
        withContext(Dispatchers.IO) {
            try {
                val db = EpgIndexDatabase.getInstance(context)
                db.openHelper.writableDatabase.apply {
                    // Rebuild query indexes before restoring normal mode
                    BULK_DROP_INDEX_DDL.forEach { ddl -> execSQL(ddl) }
                    execSQL("PRAGMA synchronous = NORMAL")
                    execSQL("PRAGMA temp_store = DEFAULT")
                    execSQL("PRAGMA cache_size = -8000") // restore 8 MB
                    FTS_TRIGGER_DDL.forEach { ddl -> execSQL(ddl) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "endBulkIngestion teardown failed (non-fatal): ${e.message}", e)
            }
        }

    /**
     * Clear all EPG data from the database and reset state to NotIndexed.
     * User-configured sources are now stored in SettingsDatabase and are preserved.
     */
    suspend fun clearAll() =
        withContext(Dispatchers.IO) {
            try {
                writeMutex.withLock {
                    // SettingsDatabase handles sources persistently.
                    // We only need to destroy and recreate the indexing database.
                    EpgIndexDatabase.destroy(context)

                    // Reopen: Room recreates all tables from schema
                    EpgIndexDatabase.getInstance(context)

                    _state.value = EpgIndexState.NotIndexed
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear EPG data: ${e.message}", e)
            }
        }

    /**
     * Delete programmes older than the given epoch and rebuild FTS.
     */
    suspend fun countStaleProgrammes(cutoffEpoch: Long): Int =
        withContext(Dispatchers.IO) {
            try {
                EpgIndexDatabase.getInstance(context).epgIndexDao().countStaleProgrammes(cutoffEpoch)
            } catch (e: Exception) {
                0
            }
        }

    suspend fun purgeOldProgrammes(cutoffEpoch: Long): Int =
        withContext(Dispatchers.IO) {
            try {
                val db = EpgIndexDatabase.getInstance(context)
                val dao = db.epgIndexDao()

                writeMutex.withLock {
                    val countBefore = dao.getProgrammeCount()
                    dao.deleteStaleProgrammes(cutoffEpoch)

                    db.openHelper.writableDatabase.execSQL(
                        "INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')",
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
                            timezoneOffsetHours = 0,
                        ),
                    )

                    _state.value =
                        if (programmeCount > 0) {
                            EpgIndexState.Indexed(channelCount, programmeCount, now)
                        } else {
                            EpgIndexState.NotIndexed
                        }

                    incrementalVacuum()
                    deleted
                }
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
    suspend fun getSourceCount(): Int =
        withContext(Dispatchers.IO) {
            try {
                SettingsDatabase.getInstance(context).epgSourceDao().getSourceCount()
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
            db.openHelper.writableDatabase
                .query("PRAGMA incremental_vacuum")
                .close()
        } catch (e: Exception) {
            Log.w(TAG, "Incremental vacuum failed: ${e.message}", e)
        }
    }
}
