package org.njarasoa.fijerena.core.network.xmltv.epgindex
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.FreeSpace
import org.njarasoa.fijerena.core.network.NotEnoughSpaceException
import org.njarasoa.fijerena.core.network.SpaceNeed
import org.njarasoa.fijerena.core.network.StorageSpace
import org.njarasoa.fijerena.core.network.StoredError
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xmltv.EpgOffsetShift
import org.njarasoa.fijerena.core.network.xmltv.XmltvParser
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.njarasoa.fijerena.core.player.model.EpgResponse
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton that indexes XMLTV data into SQLite for fast FTS search.
 *
 * Streaming parse with batch inserts ([BATCH_SIZE_MOBILE] or [BATCH_SIZE_TV] rows), each batch in
 * its own Room transaction: a network drop or a parse error rolls back only the current batch.
 *
 * Direct path: REPLACE on the unique (channel_id, source_id, start_epoch) index so the database
 * stays searchable during sync, then [deleteProgrammesNotRewritten] drops what the new guide no
 * longer lists.
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

    // The guide sources whose index entries an incremental update is still writing: search reads
    // their rows without FTS (the pausable guide indexing plan).
    @Volatile
    private var pendingSourceIds: Set<Long> = emptySet()

    /** The guide sources whose search index entries are still being written after a refresh. */
    fun pendingIndexSourceIds(): Set<Long> = pendingSourceIds

    // Whether this process has looked for an incremental update a killed run left (or started one).
    private val progressChecked = AtomicBoolean(false)

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

        /**
         * How long a programme stays after it ends: the guide shows up to 7 past days for catch-up
         * (MAX_PAST_DAY_TABS), and one day more covers a programme that started the day before.
         */
        const val KEEP_ENDED_SECONDS = 8L * 24 * 60 * 60

        /** Free pages moved per `incremental_vacuum` batch — about 2 MB at this DB's page size. */
        private const val VACUUM_CHUNK_PAGES = 2000

        /** Programmes per delete in [clearSource]. */
        private const val CLEAR_BATCH_ROWS = 5000

        private fun getBatchSize(context: Context): Int {
            val isTv = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
            return if (isTv) BATCH_SIZE_TV else BATCH_SIZE_MOBILE
        }

        // Room's FTS content-sync triggers for epg_programme_fts. Dropped before a bulk ingestion
        // and created again after it: one rebuild() at the end is cheaper than millions of updates
        // per row.
        private val FTS_TRIGGER_NAMES =
            listOf(
                "room_fts_content_sync_epg_programme_fts_BEFORE_UPDATE",
                "room_fts_content_sync_epg_programme_fts_BEFORE_DELETE",
                "room_fts_content_sync_epg_programme_fts_AFTER_UPDATE",
                "room_fts_content_sync_epg_programme_fts_AFTER_INSERT",
            )

        // FTS4 (and FTS5) uses `docid` (or `rowid`) in content-table triggers.
        // Room FTS4 defaults to `docid`. Using `rowid` as target column can fail.
        private val FTS_TRIGGER_DDL =
            listOf(
                "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_epg_programme_fts_BEFORE_UPDATE` BEFORE UPDATE ON `epg_programme` BEGIN DELETE FROM `epg_programme_fts` WHERE `docid`=OLD.`id`; END",
                "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_epg_programme_fts_BEFORE_DELETE` BEFORE DELETE ON `epg_programme` BEGIN DELETE FROM `epg_programme_fts` WHERE `docid`=OLD.`id`; END",
                "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_epg_programme_fts_AFTER_UPDATE` AFTER UPDATE ON `epg_programme` BEGIN INSERT INTO `epg_programme_fts`(`docid`,`title`,`description`) VALUES (NEW.`id`,NEW.`title`,NEW.`description`); END",
                "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_epg_programme_fts_AFTER_INSERT` AFTER INSERT ON `epg_programme` BEGIN INSERT INTO `epg_programme_fts`(`docid`,`title`,`description`) VALUES (NEW.`id`,NEW.`title`,NEW.`description`); END",
            )

        // Query-only indexes on epg_programme, dropped during a bulk ingestion and rebuilt after
        // it. The dedup unique index stays: INSERT … REPLACE needs it for conflict resolution.
        private val BULK_DROP_INDEX_NAMES =
            listOf(
                "idx_programme_end",
                "idx_programme_source",
            )
        internal val BULK_DROP_INDEX_DDL =
            listOf(
                "CREATE INDEX IF NOT EXISTS `idx_programme_end` ON `epg_programme` (`end_epoch`)",
                "CREATE INDEX IF NOT EXISTS `idx_programme_source` ON `epg_programme` (`source_id`)",
            )

        @Volatile
        private var instance: EpgIndexer? = null

        fun getInstance(context: Context): EpgIndexer =
            instance ?: synchronized(this) {
                instance ?: EpgIndexer(context.applicationContext).also { instance = it }
            }

        /**
         * A storage-low error when there is not enough free space for a rebuild of the search
         * index ([SpaceNeed.ftsRebuild]); null when there is.
         */
        fun ftsRebuildShortfall(context: Context): StoredError? =
            StorageSpace.shortfall(
                SpaceNeed.ftsRebuild(context.getDatabasePath(EpgIndexDatabase.DB_NAME).length()),
                FreeSpace.of(context),
            )
    }

    private val _state = MutableStateFlow<EpgIndexState>(EpgIndexState.NotIndexed)
    val state: StateFlow<EpgIndexState> = _state.asStateFlow()

    /** The stats of one ingestFromStream() call. */
    data class IngestionStats(
        val channelsIngested: Int = 0,
        val programmesIngested: Int = 0,
        /** The UTC offset, in minutes, found on every programme's start time; null when they differ. */
        val fileUtcOffsetMinutes: Int? = null,
    )

    @Volatile
    var lastIngestionStats: IngestionStats = IngestionStats()
        private set

    /**
     * Restores the Indexed state and the ftsStale flag from stored metadata, with no new index.
     * True when the FTS index is stale and a background rebuild must start.
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore index state", e)
                _state.value = EpgIndexState.NotIndexed
            }
            // Once per process, at its start: initialize() is called again later (Guide Sources
            // calls it when the state reads NotIndexed), and a run in progress would look killed.
            wasStale || (progressChecked.compareAndSet(false, true) && restoreIncrementalProgress())
        }

    // An incremental index update a killed run left (the pausable guide indexing plan
    // → IX2). Add or Merge unfinished: true, the worker continues it. Remove unfinished: the swap
    // never happened and some entries are gone, so the index is marked stale for a full rebuild
    // (true too); the next refresh imports those sources again.
    private suspend fun restoreIncrementalProgress(): Boolean =
        try {
            val sdb = EpgIndexDatabase.getInstance(context).openHelper.writableDatabase
            val progress = writeMutex.withLock { IncrementalFts.read(sdb) }
            when (progress?.phase) {
                null -> {
                    false
                }

                FtsProgress.Phase.REMOVE -> {
                    Log.w(TAG, "initialize: an index update stopped before its swap; rebuilding the index")
                    writeMutex.withLock { IncrementalFts.clear(sdb) }
                    markFtsStale()
                    true
                }

                FtsProgress.Phase.ADD -> {
                    pendingSourceIds = progress.sourceIds.toSet()
                    true
                }

                FtsProgress.Phase.MERGE -> {
                    true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read the index update progress", e)
            false
        }

    /** Sets Indexing unless already Indexed. Call once before parallel ingestion begins. */
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
        batchSize: Int = getBatchSize(context),
        useStaging: Boolean = false,
        isPlaybackActive: () -> Boolean = { false },
        onProgress: ((channels: Int, programmes: Int) -> Unit)? = null,
    ): IngestionStats =
        withContext(Dispatchers.IO) {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()
            var channelCount = 0
            var programmeCount = 0
            // The zone part of the programmes' start times: the first one seen, and whether another
            // one came later (a file in local time across a daylight saving change).
            var fileZone: String? = null
            var mixedZones = false

            // Every programme the source provides is kept, past or future — no date filter.

            try {
                val channelBatch = mutableListOf<EpgChannelEntity>()
                val programmeBatch = mutableListOf<EpgProgrammeEntity>()
                val channelStagingBatch = mutableListOf<EpgChannelStagingEntity>()
                val programmeStagingBatch = mutableListOf<EpgProgrammeStagingEntity>()
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
                                    if (useStaging) channelStagingBatch.add(it.toStaging()) else channelBatch.add(it)
                                    channelCount++

                                    if (channelBatch.size >= batchSize || channelStagingBatch.size >= batchSize) {
                                        writeMutex.withLock {
                                            db.withTransaction {
                                                if (useStaging) {
                                                    dao.insertChannelsStagingIgnore(
                                                        channelStagingBatch,
                                                    )
                                                } else {
                                                    dao.insertChannelsIgnore(channelBatch)
                                                }
                                            }
                                        }
                                        channelBatch.clear()
                                        channelStagingBatch.clear()
                                        // Yield CPU briefly only when video is actively decoding
                                        if (isPlaybackActive()) delay(5)
                                    }
                                }
                            }

                            "programme" -> {
                                // Flush any remaining channels before starting programmes
                                if (channelBatch.isNotEmpty() || channelStagingBatch.isNotEmpty()) {
                                    writeMutex.withLock {
                                        db.withTransaction {
                                            if (useStaging) {
                                                dao.insertChannelsStagingIgnore(
                                                    channelStagingBatch,
                                                )
                                            } else {
                                                dao.insertChannelsIgnore(channelBatch)
                                            }
                                        }
                                    }
                                    channelBatch.clear()
                                    channelStagingBatch.clear()
                                }

                                val zone =
                                    parser
                                        .getAttributeValue(null, "start")
                                        ?.let { XmltvParser.splitTimestamp(it)?.second }
                                if (zone != null && !mixedZones) {
                                    if (fileZone == null) {
                                        fileZone = zone
                                    } else if (zone != fileZone) {
                                        mixedZones = true
                                    }
                                }
                                XmltvParser.parseProgrammeForIndex(parser, sourceId, timezoneOverrideHours)?.let {
                                    if (useStaging) programmeStagingBatch.add(it.toStaging()) else programmeBatch.add(it)
                                    programmeCount++
                                    itemsSinceLastProgressUpdate++

                                    if (programmeBatch.size >= batchSize || programmeStagingBatch.size >= batchSize) {
                                        writeMutex.withLock {
                                            db.withTransaction {
                                                if (useStaging) {
                                                    dao.insertProgrammesStaging(
                                                        programmeStagingBatch,
                                                    )
                                                } else {
                                                    dao.insertProgrammes(programmeBatch)
                                                }
                                            }
                                        }
                                        programmeBatch.clear()
                                        programmeStagingBatch.clear()

                                        // Throttled UI updates, less main thread work during playback.
                                        if (itemsSinceLastProgressUpdate >= 50000) {
                                            onProgress?.invoke(channelCount, programmeCount)
                                            itemsSinceLastProgressUpdate = 0
                                        }

                                        // Yield only while video plays: 100 ms sleeps over millions of
                                        // rows made an idle ingestion take hours.
                                        if (isPlaybackActive()) delay(100)
                                    }
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }

                if (channelBatch.isNotEmpty() || channelStagingBatch.isNotEmpty()) {
                    writeMutex.withLock {
                        db.withTransaction {
                            if (useStaging) dao.insertChannelsStagingIgnore(channelStagingBatch) else dao.insertChannelsIgnore(channelBatch)
                        }
                    }
                }
                if (programmeBatch.isNotEmpty() || programmeStagingBatch.isNotEmpty()) {
                    writeMutex.withLock {
                        db.withTransaction {
                            if (useStaging) dao.insertProgrammesStaging(programmeStagingBatch) else dao.insertProgrammes(programmeBatch)
                        }
                    }
                }

                val fileOffset = fileZone?.takeUnless { mixedZones }?.let { XmltvParser.zoneMinutes(it) }
                val stats = IngestionStats(channelCount, programmeCount, fileOffset)
                lastIngestionStats = stats
                stats
            } catch (e: OutOfMemoryError) {
                // Kept as a failed source, not a crash: EPG sync runs at every start, so a crash
                // here would repeat at every start. No System.gc(): the runtime already ran a full
                // collection before it threw, and the batches above are freed when this returns.
                val msg = "Out of memory during stream indexing ($channelCount ch, $programmeCount prg ingested before failure)"
                Log.e(TAG, msg, e)
                lastIngestionStats = IngestionStats(channelCount, programmeCount)
                throw java.io.IOException(msg, e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = "Stream indexing failed: ${e.message} ($channelCount ch, $programmeCount prg ingested before failure)"
                Log.e(TAG, msg, e)
                lastIngestionStats = IngestionStats(channelCount, programmeCount)
                throw e
            }
        }

    /**
     * Deletes the `xtream://<providerId>` EPG sources that older builds created for themselves, one
     * per Xtream provider, with their index rows. Runs at every start, not behind a one-shot flag:
     * the scan of a small table costs almost nothing. The FTS rebuild runs only after a delete.
     */
    suspend fun purgeXtreamApiSources() =
        withContext(Dispatchers.IO) {
            try {
                val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
                val stale = sourceDao.getAllSourcesOnce().filter { it.url.startsWith("xtream://") }
                if (stale.isEmpty()) return@withContext

                val ids = stale.map { it.id }
                writeMutex.withLock {
                    EpgIndexDatabase.getInstance(context).epgIndexDao().deleteBySourceIds(ids)
                    ids.forEach { sourceDao.deleteGuide(it) }
                }
                Log.i(TAG, "purgeXtreamApiSources: removed ${ids.size} self-created source(s)")

                rebuildFtsAndUpdateState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "purgeXtreamApiSources failed: ${e.message}", e)
            }
        }

    /**
     * Deletes the guide index rows of guide sources that are no longer in the settings database:
     * the copies [SettingsDatabase.MIGRATION_19_20] merged into one guide source (the one guide
     * source per URL plan → Design C), and any other path that left rows behind. Runs at every
     * start; with nothing to delete it is one query. Nothing is deleted when the settings
     * database has no guide source or cannot be read, so a failed read never empties the guide.
     * After a delete, the merged guide sources' times follow their offset ([EpgOffsetShift]).
     */
    suspend fun purgeOrphanedSources() =
        withContext(Dispatchers.IO) {
            try {
                val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
                val known = sourceDao.getAllSourcesOnce().mapTo(HashSet()) { it.id }
                if (known.isEmpty()) return@withContext
                val orphans =
                    EpgIndexDatabase
                        .getInstance(context)
                        .epgIndexDao()
                        .getIndexedSourceIds()
                        .filter { it !in known }
                if (orphans.isEmpty()) return@withContext

                writeMutex.withLock {
                    EpgIndexDatabase.getInstance(context).epgIndexDao().deleteBySourceIds(orphans)
                }
                Log.i(TAG, "purgeOrphanedSources: removed the rows of ${orphans.size} guide source(s) no longer configured")
                rebuildFtsAndUpdateState()
                known.forEach { EpgOffsetShift.reconcile(it, sourceDao, this@EpgIndexer) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "purgeOrphanedSources failed: ${e.message}", e)
            }
        }

    /**
     * Rebuilds the FTS index and updates metadata and state from the database. Marks FTS stale
     * first (needed when called alone, for example by the recovery worker) and clean on success.
     */
    suspend fun rebuildFtsAndUpdateState() =
        withContext(Dispatchers.IO) {
            val startMs = System.currentTimeMillis()
            val previousState = _state.value
            Log.i(TAG, "rebuildFtsAndUpdateState: starting FTS rebuild")
            try {
                // Not enough space: the rebuild does not start, and the index is not marked stale
                // by it (see the catch below).
                ftsRebuildShortfall(context)?.let { throw NotEnoughSpaceException(it) }
                val db = EpgIndexDatabase.getInstance(context)
                val dao = db.epgIndexDao()

                writeMutex.withLock {
                    markFtsStale()
                    val sdb = db.openHelper.writableDatabase

                    val currentChannelCount = dao.getChannelCount()
                    val currentProgrammeCount = dao.getProgrammeCount()
                    _state.value = EpgIndexState.Optimizing(currentChannelCount, currentProgrammeCount)

                    // Checkpoint the WAL for less contention during the rebuild. PASSIVE, not
                    // TRUNCATE: TRUNCATE needs exclusive access and blocked every other reader and
                    // writer (now playing lookups) for tens of seconds on each EPG sync. PASSIVE
                    // never blocks another connection.
                    sdb.execPragma("PRAGMA wal_checkpoint(PASSIVE)")

                    // cache_size capped at 16 MB: at 64 MB, files with millions of programmes pushed
                    // native RSS above 150 MB, a Low Memory Killer risk on 1-2 GB Android TV devices.
                    // synchronous stays NORMAL, never OFF: a power cut mid-write (TV boxes get
                    // unplugged) can corrupt epg_index.db with OFF. NORMAL under WAL syncs only at
                    // checkpoints, and no measurement showed OFF to be faster here.
                    sdb.execSQL("PRAGMA synchronous = NORMAL")
                    sdb.execSQL("PRAGMA cache_size = -16000")

                    try {
                        deleteExpiredProgrammes(dao)
                        Log.d(TAG, "rebuildFtsAndUpdateState: executing FTS rebuild")
                        // 'rebuild' fills FTS again from epg_programme. Needed after a bulk ingestion
                        // because the FTS triggers were dropped; 'optimize' only merges segments.
                        sdb.execSQL("INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')")
                        optimizeFts(sdb)
                    } finally {
                        // Room pools this connection, so a value left set here stays for the life of
                        // the process: reset even when the rebuild throws.
                        sdb.execSQL("PRAGMA synchronous = NORMAL")
                        sdb.execSQL("PRAGMA cache_size = -2000")
                    }

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

                    // A full rebuild covers every row: an incremental update left unfinished has
                    // nothing more to do.
                    IncrementalFts.clear(sdb)
                    pendingSourceIds = emptySet()
                    // Clean before Indexed, not after: a search waiting on Indexed reruns the
                    // moment it's published and must not still find the index marked stale.
                    markFtsClean()
                    _state.value =
                        EpgIndexState.Indexed(
                            channelCount = finalChannelCount,
                            programmeCount = finalProgrammeCount,
                            indexedAtMs = now,
                        )
                }
                Log.i(TAG, "rebuildFtsAndUpdateState: FTS complete in ${System.currentTimeMillis() - startMs}ms")
            } catch (e: CancellationException) {
                // Stopped mid-rebuild: show Failed rather than leaving the state on
                // Optimizing. The FTS stays marked stale, so the next start rebuilds it.
                _state.value = EpgIndexState.Failed(friendlyErrorMessage(e, context, AppSettings(context).isNerdMode))
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "FTS rebuild failed: ${e.message}", e)
                CrashLog.record("guide search index rebuild failed", e)
                // A rebuild with no space for it leaves an indexed guide as it was: its rows are all
                // there, and search reads them without the index while it is stale.
                _state.value = previousState.takeIf { e is NotEnoughSpaceException && it is EpgIndexState.Indexed }
                    ?: EpgIndexState.Failed(friendlyErrorMessage(e, context, AppSettings(context).isNerdMode))
            }
        }

    /**
     * Move [sourceIds]' staging rows into the primary tables and rebuild FTS, in **one**
     * transaction. In WAL mode a reader keeps seeing the last committed snapshot until the commit,
     * so a search during this sees the old guide with the old FTS, then the new guide with the
     * new FTS — never the new rows with the old FTS that a separate swap and rebuild exposed. That
     * is why nothing is marked stale here, and why search keeps working through the whole refresh.
     * A failure or a killed process rolls the swap and the rebuild back together.
     *
     * Costs: the WAL grows by roughly the FTS size before it can checkpoint (the staging path is
     * only taken with 1.5× the database size free, see EpgFileManager.shouldUseStaging), and
     * other writers wait on the write lock for the rebuild.
     *
     * Throws if the swap or the rebuild fails, leaving the previous guide and state in place.
     */
    suspend fun swapAndRebuildFts(sourceIds: List<Long>) =
        withContext(Dispatchers.IO) {
            val startMs = System.currentTimeMillis()
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()
            writeMutex.withLock {
                val previousState = _state.value
                val sdb = db.openHelper.writableDatabase
                _state.value = EpgIndexState.Optimizing(dao.getChannelCount(), dao.getProgrammeCount())

                // Same pragmas as rebuildFtsAndUpdateState(), set outside the transaction: SQLite
                // refuses to change `synchronous` inside one. NORMAL, not OFF — see there.
                sdb.execPragma("PRAGMA wal_checkpoint(PASSIVE)")
                sdb.execSQL("PRAGMA synchronous = NORMAL")
                sdb.execSQL("PRAGMA cache_size = -16000")
                val now = System.currentTimeMillis()
                var channelCount = 0
                var programmeCount = 0
                try {
                    Log.i(TAG, "swapAndRebuildFts: swapping and rebuilding FTS for sources: $sourceIds")
                    db.withTransaction {
                        dao.executeSwap(sourceIds)
                        deleteExpiredProgrammes(dao)
                        // Same connection and thread as the transaction (Room runs this block on
                        // its transaction thread), so the rebuild commits or rolls back with the swap.
                        sdb.execSQL("INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')")
                        optimizeFts(sdb)
                        channelCount = dao.getChannelCount()
                        programmeCount = dao.getProgrammeCount()
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
                    }
                } catch (e: Throwable) {
                    // Rolled back: the previous guide is still the live one.
                    _state.value = previousState
                    throw e
                } finally {
                    sdb.execSQL("PRAGMA synchronous = NORMAL")
                    sdb.execSQL("PRAGMA cache_size = -2000")
                }
                // The rebuild covered every row, so a flag left by an earlier interrupted rebuild
                // is resolved too. Before Indexed, as in rebuildFtsAndUpdateState().
                IncrementalFts.clear(sdb)
                pendingSourceIds = emptySet()
                markFtsClean()
                _state.value = EpgIndexState.Indexed(channelCount, programmeCount, now)
            }
            Log.i(TAG, "swapAndRebuildFts: complete in ${System.currentTimeMillis() - startMs}ms")
        }

    /**
     * Moves [sourceIds]' staging rows into the primary tables and brings the search index up to
     * date for them only, in batches that wait while video plays (the pausable guide indexing plan):
     *
     * 1. Remove: the index entries of the rows the swap deletes (those sources' live rows and the
     *    ended ones), while those rows still exist.
     * 2. Swap: one short transaction, as [swapAndRebuildFts] without the rebuild; then [onSwapped].
     * 3. Add: the index entries of the new rows.
     * 4. Merge: the index's segments, a step at a time.
     *
     * Before each batch, [pause] waits while video plays; [onWaiting] is told when a wait starts
     * and ends. From Remove to the end of Add, [pendingIndexSourceIds] has [sourceIds], and search
     * reads their rows without FTS. Each batch commits with its progress, so a killed run continues
     * where it stopped ([resumeIncrementalIndex]). Returns the time spent indexing, waits left out.
     *
     * Call it with the FTS triggers dropped (during a refresh run) and the index not stale.
     * Throws if a step fails; a failure before the swap leaves the previous guide live and marks
     * the index stale, so the next start rebuilds it.
     */
    suspend fun swapAndIndexIncrementally(
        sourceIds: List<Long>,
        pause: PlaybackPause,
        onWaiting: (Boolean) -> Unit,
        onSwapped: suspend () -> Unit,
    ): Long =
        withContext(Dispatchers.IO) {
            require(sourceIds.isNotEmpty()) { "no guide source to index" }
            val startMs = System.currentTimeMillis()
            val db = EpgIndexDatabase.getInstance(context)
            val sdb = db.openHelper.writableDatabase
            val cutoff = System.currentTimeMillis() / 1000 - KEEP_ENDED_SECONDS
            var progress = FtsProgress(FtsProgress.Phase.REMOVE, sourceIds, cursor = 0, cutoff = cutoff, boundary = 0)
            var waitedMs = 0L
            Log.i(TAG, "swapAndIndexIncrementally: sources $sourceIds")
            // The progress is this run's from now on, not a killed run's.
            progressChecked.set(true)
            writeMutex.withLock { IncrementalFts.write(sdb, progress) }
            pendingSourceIds = sourceIds.toSet()
            try {
                while (true) {
                    waitedMs += pause.await(onWaiting)
                    progress = writeMutex.withLock { withBatchCache(sdb) { IncrementalFts.removeBatch(sdb, progress) } } ?: break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The swap didn't happen and some entries are gone: only a full rebuild can tell.
                Log.e(TAG, "swapAndIndexIncrementally: removing index entries failed: ${e.message}", e)
                writeMutex.withLock { IncrementalFts.clear(sdb) }
                pendingSourceIds = emptySet()
                markFtsStale()
                throw e
            }

            writeMutex.withLock {
                val dao = db.epgIndexDao()
                val previousState = _state.value
                val now = System.currentTimeMillis()
                var channelCount = 0
                var programmeCount = 0
                try {
                    db.withTransaction {
                        val boundary = dao.getMaxProgrammeId()
                        dao.executeSwap(sourceIds)
                        dao.deleteStaleProgrammes(cutoff)
                        progress = progress.copy(phase = FtsProgress.Phase.ADD, cursor = boundary, boundary = boundary)
                        IncrementalFts.write(sdb, progress)
                        channelCount = dao.getChannelCount()
                        programmeCount = dao.getProgrammeCount()
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
                    }
                } catch (e: Throwable) {
                    // Rolled back: the previous guide is still live, but its removed entries are
                    // gone. As a failed Remove.
                    _state.value = previousState
                    if (e !is CancellationException) {
                        IncrementalFts.clear(sdb)
                        pendingSourceIds = emptySet()
                        markFtsStale()
                    }
                    throw e
                }
                // A new generation: the guide screens read the new listings now, before the index
                // has them (search reads the pending sources without it).
                _state.value = EpgIndexState.Indexed(channelCount, programmeCount, now)
            }
            onSwapped()

            waitedMs += finishIncrementalIndex(sdb, progress, pause, onWaiting)
            val indexMs = System.currentTimeMillis() - startMs - waitedMs
            Log.i(TAG, "swapAndIndexIncrementally: complete, ${indexMs}ms indexing, ${waitedMs}ms waiting for playback to stop")
            indexMs
        }

    /**
     * Continues an incremental index update a killed run left (the pausable guide indexing plan,
     * IX2): Add and Merge from where they stopped. False when there was nothing to continue.
     */
    suspend fun resumeIncrementalIndex(
        pause: PlaybackPause,
        onWaiting: (Boolean) -> Unit = {},
    ): Boolean =
        withContext(Dispatchers.IO) {
            val sdb = EpgIndexDatabase.getInstance(context).openHelper.writableDatabase
            val progress = writeMutex.withLock { IncrementalFts.read(sdb) } ?: return@withContext false
            if (progress.phase == FtsProgress.Phase.REMOVE) return@withContext false
            Log.i(TAG, "resumeIncrementalIndex: continuing ${progress.phase} after id ${progress.cursor} for ${progress.sourceIds}")
            if (progress.phase == FtsProgress.Phase.ADD) pendingSourceIds = progress.sourceIds.toSet()
            // The triggers are back after a restart: off while the batches write, as in a refresh run.
            writeMutex.withLock { FTS_TRIGGER_NAMES.forEach { sdb.execSQL("DROP TRIGGER IF EXISTS `$it`") } }
            try {
                finishIncrementalIndex(sdb, progress, pause, onWaiting)
            } finally {
                withContext(NonCancellable) { writeMutex.withLock { FTS_TRIGGER_DDL.forEach { sdb.execSQL(it) } } }
            }
            true
        }

    // Add (from [start]'s cursor, when it is Add) and Merge, then clears the progress. Returns the
    // time spent waiting for playback to stop.
    private suspend fun finishIncrementalIndex(
        sdb: SupportSQLiteDatabase,
        start: FtsProgress,
        pause: PlaybackPause,
        onWaiting: (Boolean) -> Unit,
    ): Long {
        var waitedMs = 0L
        var progress = start
        if (progress.phase == FtsProgress.Phase.ADD) {
            while (true) {
                waitedMs += pause.await(onWaiting)
                progress = writeMutex.withLock { withBatchCache(sdb) { IncrementalFts.addBatch(sdb, progress) } } ?: break
            }
            pendingSourceIds = emptySet()
            progress = progress.copy(phase = FtsProgress.Phase.MERGE)
            writeMutex.withLock { IncrementalFts.write(sdb, progress) }
        }
        while (true) {
            waitedMs += pause.await(onWaiting)
            if (!writeMutex.withLock { withBatchCache(sdb) { IncrementalFts.mergeStep(sdb) } }) break
        }
        writeMutex.withLock { IncrementalFts.clear(sdb) }
        return waitedMs
    }

    // A larger page cache for one batch, as the full rebuild sets, then back to the default.
    private inline fun <T> withBatchCache(
        sdb: SupportSQLiteDatabase,
        block: () -> T,
    ): T {
        sdb.execSQL("PRAGMA cache_size = -16000")
        try {
            return block()
        } finally {
            sdb.execSQL("PRAGMA cache_size = -2000")
        }
    }

    suspend fun clearStaging() =
        withContext(Dispatchers.IO) {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()
            writeMutex.withLock {
                dao.clearStaging()
            }
        }

    /** Clears the staging rows of [sourceIds] only; other sources' staging rows stay. */
    suspend fun clearStagingForSources(sourceIds: List<Long>) =
        withContext(Dispatchers.IO) {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()
            writeMutex.withLock {
                dao.clearStagingForSources(sourceIds)
            }
        }

    /**
     * Prepares the database for a bulk ingestion session:
     *  - Drop Room's per-row FTS sync triggers: one rebuild() at the end is far cheaper than an
     *    FTS update for each of millions of inserts.
     *  - Keep synchronous=NORMAL (not OFF): under WAL it only syncs at checkpoints, and OFF
     *    risks a corrupt epg_index.db on a power cut (TV boxes get unplugged).
     *  - Drop the query-only indexes on `epg_programme`, only when [useStaging] is false: on the
     *    staging path the live guide keeps reading the primary table, and without its indexes
     *    every guide lookup scanned 2M+ rows.
     *
     * Always call endBulkIngestion() in a finally block, with the same [useStaging] value.
     */
    suspend fun beginBulkIngestion(useStaging: Boolean) =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "beginBulkIngestion: setting up database for bulk ingest (useStaging=$useStaging)")
            val startMs = System.currentTimeMillis()
            try {
                writeMutex.withLock {
                    val db = EpgIndexDatabase.getInstance(context)
                    // Request writable database explicitly to avoid read-only issues with Requery/Room
                    val sdb = db.openHelper.writableDatabase
                    if (sdb.isReadOnly) {
                        Log.w(TAG, "beginBulkIngestion: connection is read-only, attempting to modify might fail")
                    }

                    if (!useStaging) {
                        // The FTS triggers are dropped below, so direct writes to epg_programme no
                        // longer update FTS. Marked stale now, so a search mid-ingest gets "still
                        // updating" and not a mismatched index. The staging path never marks stale
                        // (see swapAndRebuildFts()).
                        markFtsStale()
                    }

                    FTS_TRIGGER_NAMES.forEach { name ->
                        sdb.execSQL("DROP TRIGGER IF EXISTS `$name`")
                    }
                    if (!useStaging) {
                        BULK_DROP_INDEX_NAMES.forEach { name ->
                            sdb.execSQL("DROP INDEX IF EXISTS `$name`")
                        }
                    }
                    sdb.execSQL("PRAGMA synchronous = NORMAL")
                    sdb.execSQL("PRAGMA cache_size = -32000") // 32 MB during bulk
                }
                Log.i(TAG, "beginBulkIngestion: setup complete in ${System.currentTimeMillis() - startMs}ms")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "beginBulkIngestion setup failed: ${e.message}", e)
                CrashLog.record("guide index: dropping indexes before a refresh failed", e)
                // Not fatal, but ingestion is much slower with the triggers and indexes still there.
            }
        }

    /**
     * Restores the database after a bulk ingestion session:
     *  - Recreate the Room FTS sync triggers dropped in beginBulkIngestion().
     *  - Recreate the query indexes dropped in beginBulkIngestion(), only when [useStaging] is
     *    false, as that call did.
     *  - Restore synchronous=NORMAL.
     */
    suspend fun endBulkIngestion(useStaging: Boolean) =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "endBulkIngestion: restoring database state after bulk ingest (useStaging=$useStaging)")
            val startMs = System.currentTimeMillis()
            try {
                writeMutex.withLock {
                    val db = EpgIndexDatabase.getInstance(context)
                    val sdb = db.openHelper.writableDatabase

                    // Rebuild query indexes before restoring normal mode
                    if (!useStaging) {
                        BULK_DROP_INDEX_DDL.forEach { ddl ->
                            val indexStart = System.currentTimeMillis()
                            sdb.execSQL(ddl)
                            Log.d(TAG, "endBulkIngestion: recreated index in ${System.currentTimeMillis() - indexStart}ms")
                        }
                    }
                    sdb.execSQL("PRAGMA synchronous = NORMAL")
                    sdb.execSQL("PRAGMA cache_size = -8000") // restore 8 MB
                    FTS_TRIGGER_DDL.forEach { ddl -> sdb.execSQL(ddl) }
                }
                Log.i(TAG, "endBulkIngestion: restore complete in ${System.currentTimeMillis() - startMs}ms")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "endBulkIngestion teardown failed: ${e.message}", e)
                CrashLog.record("guide index: restoring indexes after a refresh failed", e)
            }
        }

    /** Clears all EPG data and resets the state to NotIndexed. The sources, in SettingsDatabase, stay. */
    suspend fun clearAll() =
        withContext(Dispatchers.IO) {
            try {
                writeMutex.withLock {
                    EpgIndexDatabase.destroy(context)

                    // Reopen: Room recreates all tables from schema
                    EpgIndexDatabase.getInstance(context)

                    _state.value = EpgIndexState.NotIndexed
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear EPG data: ${e.message}", e)
            }
        }

    /** The highest programme id in the live guide, 0 when empty — the mark [deleteProgrammesNotRewritten] takes. */
    suspend fun maxProgrammeId(): Long =
        withContext(Dispatchers.IO) {
            EpgIndexDatabase.getInstance(context).epgIndexDao().getMaxProgrammeId()
        }

    /**
     * After a direct (non-staging) ingest of [sourceId]: delete its programmes the new guide did not
     * write again, those with an id up to [maxIdBefore] ([maxProgrammeId] taken before the ingest).
     * REPLACE only overwrites a programme with the same channel and start, so without this a
     * changed schedule left the old listings beside the new ones, overlapping in the TV Guide. The
     * staging path needs none of this: its swap replaces a source's guide whole.
     */
    suspend fun deleteProgrammesNotRewritten(
        sourceId: Long,
        maxIdBefore: Long,
    ): Int =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                EpgIndexDatabase.getInstance(context).epgIndexDao().deleteProgrammesOfSourceUpTo(sourceId, maxIdBefore)
            }
        }

    /**
     * End [sourceId]'s programmes at the next start on their channel where they run past it
     * ([EpgIndexDao.clipOverlaps]), in the staging table when [useStaging]. After the ingest, and on
     * the direct path after [deleteProgrammesNotRewritten], so only the new guide's rows count.
     * See the guide overlaps plan.
     */
    suspend fun clipOverlaps(
        sourceId: Long,
        useStaging: Boolean,
    ): Int =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val dao = EpgIndexDatabase.getInstance(context).epgIndexDao()
                if (useStaging) dao.clipOverlapsStaging(sourceId) else dao.clipOverlaps(sourceId)
            }
        }

    /**
     * Moves every programme time of [sourceId] in the live guide by [deltaSeconds]: its offset
     * changed (see `xmltv.EpgOffsetShift`). The search index holds titles and descriptions only, so
     * its two update triggers are left out for the update, then put back if they were there. Two
     * steps: moving the starts in place could meet the next programme's start on the same channel,
     * which the unique dedup index refuses, so the starts first go negative, then back.
     */
    suspend fun shiftSourceTimes(
        sourceId: Long,
        deltaSeconds: Long,
    ) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val db = EpgIndexDatabase.getInstance(context)
            val sdb = db.openHelper.writableDatabase
            db.withTransaction {
                val updateTriggers = FTS_TRIGGER_NAMES.filter { it.endsWith("_UPDATE") }
                val present =
                    updateTriggers.filter { name ->
                        sdb.query("SELECT 1 FROM sqlite_master WHERE type = 'trigger' AND name = ?", arrayOf(name)).use { it.moveToFirst() }
                    }
                present.forEach { sdb.execSQL("DROP TRIGGER `$it`") }
                sdb.execSQL(
                    "UPDATE epg_programme SET start_epoch = -(start_epoch + ?), end_epoch = end_epoch + ? WHERE source_id = ?",
                    arrayOf<Any>(deltaSeconds, deltaSeconds, sourceId),
                )
                sdb.execSQL(
                    "UPDATE epg_programme SET start_epoch = -start_epoch WHERE source_id = ? AND start_epoch < 0",
                    arrayOf<Any>(sourceId),
                )
                FTS_TRIGGER_DDL.filter { ddl -> present.any { ddl.contains("`$it`") } }.forEach { sdb.execSQL(it) }
            }
            // A new generation, as after an import: the listings caches keyed on it read the
            // moved times again, and the open guide screens reload. During an import its end does it.
            (_state.value as? EpgIndexState.Indexed)?.let { _state.value = it.copy(indexedAtMs = System.currentTimeMillis()) }
        }
    }

    /**
     * Whether the live guide (primary tables, never staging) holds any programme of [sourceId].
     * False on any failure too — the caller treats "unknown" as "empty" and re-ingests.
     */
    suspend fun hasProgrammesForSource(sourceId: Long): Boolean =
        withContext(Dispatchers.IO) {
            try {
                EpgIndexDatabase.getInstance(context).epgIndexDao().getLatestProgrammeEndTimeForSource(sourceId) != null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "hasProgrammesForSource($sourceId) failed, assuming empty", e)
                false
            }
        }

    suspend fun countStaleProgrammes(cutoffEpoch: Long): Int =
        withContext(Dispatchers.IO) {
            try {
                EpgIndexDatabase.getInstance(context).epgIndexDao().countStaleProgrammes(cutoffEpoch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                0
            }
        }

    /**
     * Deletes all of guide source [sourceId]'s channels and programmes, and gives the freed space
     * back to the device. The programmes go in batches: one delete of a guide of a million rows
     * (the search index follows each row through its triggers) would need room for all of it in
     * the write-ahead log, on a device that has little room left (a Chromecast HD). [onProgress]
     * gets the programmes deleted so far after each batch, then [ClearProgress.FreeingSpace].
     */
    suspend fun clearSource(
        sourceId: Long,
        onProgress: (ClearProgress) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val db = EpgIndexDatabase.getInstance(context)
        val dao = db.epgIndexDao()
        writeMutex.withLock {
            val total = dao.countProgrammesOfSource(sourceId)
            var done = 0
            onProgress(ClearProgress.Deleting(done, total))
            do {
                val deleted = dao.deleteProgrammesOfSourceBatch(sourceId, CLEAR_BATCH_ROWS)
                done += deleted
                onProgress(ClearProgress.Deleting(done, total))
            } while (deleted > 0)
            dao.deleteChannelsBySourceId(sourceId)
            val channelCount = dao.getChannelCount()
            val programmeCount = dao.getProgrammeCount()
            // The totals Guide Data Maintenance shows, also after a restart.
            dao.getMetadata()?.let { dao.insertMetadata(it.copy(channelCount = channelCount, programmeCount = programmeCount)) }
            if (_state.value is EpgIndexState.Indexed) {
                _state.value =
                    if (programmeCount > 0) {
                        EpgIndexState.Indexed(channelCount, programmeCount, System.currentTimeMillis())
                    } else {
                        EpgIndexState.NotIndexed
                    }
            }
            onProgress(ClearProgress.FreeingSpace)
            incrementalVacuumLocked()
        }
    }

    /** How far [clearSource] is. */
    sealed interface ClearProgress {
        /** [done] of the guide source's [total] programmes deleted; total 0 before the count. */
        data class Deleting(
            val done: Int,
            val total: Int,
        ) : ClearProgress

        /** The rows are gone; the space goes back to the device. */
        data object FreeingSpace : ClearProgress
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
                    optimizeFts(db.openHelper.writableDatabase)

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

                    incrementalVacuumLocked()
                    deleted
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Purge failed: ${e.message}", e)
                0
            }
        }

    /** The number of configured EPG sources, whatever the index state. */
    suspend fun getSourceCount(): Int =
        withContext(Dispatchers.IO) {
            try {
                SettingsDatabase.getInstance(context).epgSourceDao().getSourceCount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get source count: ${e.message}")
                0
            }
        }

    /**
     * Reclaims the free pages that deletes leave. Takes [writeMutex] itself, so callers outside
     * this class use this and not [incrementalVacuumLocked]: its `PRAGMA wal_checkpoint(TRUNCATE)`
     * needs exclusive access.
     */
    suspend fun incrementalVacuum() =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                incrementalVacuumLocked()
            }
        }

    private fun incrementalVacuumLocked() {
        try {
            val sdb = EpgIndexDatabase.getInstance(context).openHelper.writableDatabase
            var remaining = freelistCount(sdb)
            val started = remaining
            // One unbounded `PRAGMA incremental_vacuum` moves every free page in one transaction: with
            // a million of them, a write of minutes with the DB locked. Chunks give readers a gap
            // between batches and keep each transaction small.
            var progressing = true
            while (remaining > 0 && progressing) {
                sdb.execPragma("PRAGMA incremental_vacuum($VACUUM_CHUNK_PAGES)")
                var left = freelistCount(sdb)
                if (left >= remaining) {
                    // A batch that frees nothing is usually not the end of the freelist: in WAL mode
                    // moved pages leave the file only at a checkpoint, and when the log reaches
                    // journal_size_limit with a reader on it, the vacuum stops. Force a checkpoint
                    // and retry (on a Shield: 43 MB left instead of 1.3 GB).
                    sdb.execPragma("PRAGMA wal_checkpoint(TRUNCATE)")
                    sdb.execPragma("PRAGMA incremental_vacuum($VACUUM_CHUNK_PAGES)")
                    left = freelistCount(sdb)
                }
                progressing = left < remaining
                remaining = left
            }
            if (started > 0) {
                Log.i(TAG, "Incremental vacuum reclaimed ${started - remaining} of $started free pages")
            }
            // The WAL shrinks only at a TRUNCATE checkpoint: journal_size_limit does not cut it
            // while the import's PASSIVE checkpoints run. Without free pages the loop above never
            // truncates, and on a Shield the WAL stayed at 231 MB. This runs after each import,
            // under writeMutex, when no import writes.
            sdb.execPragma("PRAGMA wal_checkpoint(TRUNCATE)")
        } catch (e: Exception) {
            // cancellation-ok: non-suspend helper
            Log.w(TAG, "Incremental vacuum failed: ${e.message}", e)
        }
    }

    /**
     * Deletes the programmes that ended more than [KEEP_ENDED_SECONDS] ago, just before a rebuild
     * of the search index. A refresh replaces only the sources it downloads, so a source that stops
     * refreshing kept its programmes for ever (29% of the guide on the TV emulator). See the disk
     * footprint plan → Phase 2.
     */
    private suspend fun deleteExpiredProgrammes(dao: EpgIndexDao) {
        dao.deleteStaleProgrammes(System.currentTimeMillis() / 1000 - KEEP_ENDED_SECONDS)
    }

    /**
     * Merges the search index into one segment. A 'rebuild' leaves it in several (96 MB against
     * 84 MB merged on the TV emulator), and searches return the same rows either way: under a second
     * of work after each rebuild, inside the caller's lock and transaction. See the disk footprint
     * plan → Phase 1.
     */
    private fun optimizeFts(sdb: SupportSQLiteDatabase) {
        sdb.execSQL("INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('optimize')")
    }

    private fun freelistCount(sdb: SupportSQLiteDatabase): Long = pragmaLong(sdb, "PRAGMA freelist_count")

    private fun pragmaLong(
        sdb: SupportSQLiteDatabase,
        sql: String,
    ): Long = sdb.query(sql).use { if (it.moveToFirst()) it.getLong(0) else 0L }
}

/**
 * Runs a PRAGMA that Requery refuses through `execSQL` because it can return rows.
 *
 * [SupportSQLiteDatabase.query] alone is not enough: Android's cursors are lazy, so the statement
 * runs only when something fills the cursor window. A cursor closed without a step discards the
 * PRAGMA silently (this once left the EPG database at 87% free pages).
 */
internal fun SupportSQLiteDatabase.execPragma(sql: String) {
    query(sql).use { it.moveToFirst() }
}
