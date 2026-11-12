package org.njarasoa.fijerena.core.network.xmltv
import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.ErrorKind
import org.njarasoa.fijerena.core.network.FreeSpace
import org.njarasoa.fijerena.core.network.HttpStatusException
import org.njarasoa.fijerena.core.network.NotEnoughSpaceException
import org.njarasoa.fijerena.core.network.R
import org.njarasoa.fijerena.core.network.SpaceNeed
import org.njarasoa.fijerena.core.network.StorageSpace
import org.njarasoa.fijerena.core.network.StoredError
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.isDiskFull
import org.njarasoa.fijerena.core.network.provider.EpgPipelineStatsEntity
import org.njarasoa.fijerena.core.network.provider.EpgSourceDao
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.queue.RefreshPriority
import org.njarasoa.fijerena.core.network.queue.RefreshQueue
import org.njarasoa.fijerena.core.network.queue.RefreshTask
import org.njarasoa.fijerena.core.network.triesUntilDiskFull
import org.njarasoa.fijerena.core.network.utils.await
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xmltv.epgindex.PlaybackPause
import org.njarasoa.fijerena.core.network.xmltv.epgindex.isMediaPlaying
import org.njarasoa.fijerena.core.network.xtream.manager.AutoXmltvSources
import org.njarasoa.fijerena.core.player.config.NetworkType
import org.njarasoa.fijerena.core.player.device.DeviceDetector
import org.njarasoa.fijerena.core.player.device.DeviceType
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.network.NetworkMonitor
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import java.io.BufferedInputStream
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import androidx.work.NetworkType as WorkNetworkType

/**
 * Singleton running the multi-source EPG pipeline: sources are downloaded to cache files and
 * ingested by [EpgIndexer] in batches.
 */
class EpgFileManager private constructor(
    private val context: Context,
) {
    companion object {
        private const val TAG = "EpgFileManager"
        private const val PREFS_NAME = "epg_file_manager"

        /** Below this much RAM a device counts as low on memory: a Chromecast HD has 1.4 GB, a Shield 3 GB. */
        internal const val LOW_MEMORY_BYTES = 2L * 1024 * 1024 * 1024

        /** Whether a device with [totalMemBytes] of RAM counts as low on memory. */
        internal fun isLowMemory(totalMemBytes: Long): Boolean = totalMemBytes in 1 until LOW_MEMORY_BYTES

        /** Whether this device counts as low on memory ([isLowMemory]). */
        fun isLowMemoryDevice(context: Context): Boolean {
            val info = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info) ?: return false
            return isLowMemory(info.totalMem)
        }

        /** The Finalizing phases of the incremental index (localised on the cards and Settings rows). */
        const val PHASE_INDEXING = "Indexing"
        const val PHASE_INDEXING_WAITS = "Indexing waits"
        private const val KEY_MIGRATED_TO_SOURCES = "migrated_to_sources_v1"
        private const val STREAM_BUFFER_SIZE = 131072 // 128KB
        private const val MAX_RETRIES = 3

        /**
         * Task ids carry the providers, because [RefreshQueue] coalesces same-id submissions into
         * the running task. A refresh covers the guide sources of its providers only, so an
         * unsuffixed id would let a second provider's refresh silently become the first one's run.
         */
        fun refreshStaleTaskId(providerId: Long): String = refreshStaleTaskId(listOf(providerId))

        /** Refresh stale over several providers' sources (Search the guide's, multi-source Phase 7). */
        fun refreshStaleTaskId(providerIds: List<Long>): String = "epg_refresh_stale_${providerIds.joinToString("_")}"

        fun refreshFailedTaskId(providerIds: List<Long>): String = "epg_refresh_failed_${providerIds.joinToString("_")}"

        /** True when a run that covers [runProviderIds] (null: no run yet) is shown to a screen for [providerIds]. */
        internal fun isRunFor(
            runProviderIds: Set<Long>?,
            providerIds: Set<Long>,
        ): Boolean = runProviderIds == null || runProviderIds.any { it in providerIds }

        private const val RETRY_DELAY_MS = 5000L

        // A content-hash match skips ingestion (see canSkipIngest) unless the last real ingest is
        // older than this — ingestFromStream's programme window is wall-clock relative, so a
        // static file left un-ingested longer than this would fall behind regardless of content.
        private const val STALENESS_FORCE_INGEST_MS = 24 * 3600 * 1000L

        @Volatile
        private var instance: EpgFileManager? = null

        fun getInstance(context: Context): EpgFileManager =
            instance ?: synchronized(this) {
                instance ?: EpgFileManager(context.applicationContext).also { instance = it }
            }

        /**
         * The request for [source]. Validators (`If-None-Match` / `If-Modified-Since`) go out only
         * while the live index holds the source's rows: a `304` is only a valid answer when there
         * is a guide to keep. After a clear, or a staging run whose swap never committed, sending
         * them would let the server confirm "unchanged" against rows we no longer have, and the
         * guide would stay empty until the upstream file happened to change (G-11).
         *
         * [url] is where to download from: [EpgSourceEntity.url], or for an automatic Xtream guide
         * source that URL with the login added ([AutoXmltvSources.fetchUrl]).
         */
        internal fun buildDownloadRequest(
            source: EpgSourceEntity,
            indexHasRows: Boolean,
            url: String = source.url,
        ): Request {
            val requestBuilder = Request.Builder().url(url)
            if (indexHasRows) {
                source.etag?.let { requestBuilder.header("If-None-Match", it) }
                source.lastModifiedHeader?.let { requestBuilder.header("If-Modified-Since", it) }
            }
            return requestBuilder.build()
        }

        /** True when [file] starts with the gzip magic bytes (1f 8b). */
        internal fun isGzipFile(file: File): Boolean = file.inputStream().use { it.read() == 0x1f && it.read() == 0x8b }

        /**
         * Whether a matching content hash is enough to skip the ingest. A real ingest still runs
         * once a day, and a skip needs the live index to hold the source's rows ([indexHasRows],
         * see [buildDownloadRequest]).
         */
        internal fun canSkipIngest(
            source: EpgSourceEntity,
            newHash: String?,
            indexHasRows: Boolean,
        ): Boolean {
            var skip = false
            if (indexHasRows && newHash != null && newHash == source.lastContentSha256) {
                val age = System.currentTimeMillis() - source.lastIngestedAtMs
                skip = age < STALENESS_FORCE_INGEST_MS
            }
            return skip
        }

        /**
         * Staging path: commit the swap, then write the stats and validators [ingestDownloadedSource]
         * held back for the sources the swap carried over. Strictly in this order — until the swap
         * commits, the rows exist only in staging, and a source marked ingested (validators
         * included) with nothing in the live guide is exactly the empty index of G-11. If the swap
         * throws or the run is cancelled, nothing is marked and the next run downloads again.
         */
        internal suspend fun swapThenMarkIngested(
            indexer: EpgIndexer,
            sourceDao: EpgSourceDao,
            syncedIds: List<Long>,
            pending: Map<Long, IngestRecord>,
        ) {
            indexer.swapAndRebuildFts(syncedIds)
            markIngested(indexer, sourceDao, syncedIds, pending)
        }

        /** After the swap: records each refreshed source's import, then moves its times if its offset changed meanwhile. */
        internal suspend fun markIngested(
            indexer: EpgIndexer,
            sourceDao: EpgSourceDao,
            syncedIds: List<Long>,
            pending: Map<Long, IngestRecord>,
        ) {
            syncedIds.mapNotNull { pending[it] }.forEach { record ->
                sourceDao.markIngested(
                    id = record.sourceId,
                    timestamp = record.timestamp,
                    channels = record.channels,
                    programmes = record.programmes,
                    downloadBytes = record.downloadBytes,
                    ingestMethod = record.ingestMethod,
                    ingestionDurationMs = record.ingestionDurationMs,
                    downloadDurationMs = record.downloadDurationMs,
                    contentSha256 = record.contentSha256,
                    etag = record.etag,
                    lastModifiedHeader = record.lastModifiedHeader,
                    fileUtcOffsetMinutes = record.fileUtcOffsetMinutes,
                    appliedUtcOffsetMinutes = record.appliedUtcOffsetMinutes,
                )
                // The offset may have changed while the import ran.
                EpgOffsetShift.reconcile(record.sourceId, sourceDao, indexer)
            }
        }

        fun extractLabel(url: String): String =
            try {
                val path = URL(url).path.trimEnd('/')
                val filename =
                    path
                        .substringAfterLast('/')
                        .removeSuffix(".gz")
                        .removeSuffix(".xml")
                        .removeSuffix(".xmltv")
                if (filename.isNotBlank()) {
                    filename.take(30)
                } else {
                    URL(url)
                        .host
                        .removePrefix("www.")
                        .take(30)
                }
            } catch (e: Exception) {
                // cancellation-ok: non-suspend
                "Source"
            }
    }

    data class SourceStats(
        val sourceId: Long,
        val label: String,
        val downloadBytes: Long = 0,
        val channelsIngested: Int = 0,
        val programmesIngested: Int = 0,
        val durationMs: Long = 0,
        val error: String? = null,
        /**
         * True when the refresh found the source unchanged (304, or the same content hash) and
         * skipped the ingest. [channelsIngested]/[programmesIngested] are then the counts of the
         * last real ingest.
         */
        val unchanged: Boolean = false,
    )

    /**
     * Everything [EpgSourceDao.markIngested] writes for one source. On the staging path this is
     * held back until the swap commits — see [swapThenMarkIngested].
     */
    internal data class IngestRecord(
        val sourceId: Long,
        val timestamp: Long,
        val channels: Int,
        val programmes: Int,
        val downloadBytes: Long,
        val ingestMethod: String = "DOWNLOADED",
        val ingestionDurationMs: Long = 0,
        val downloadDurationMs: Long = 0,
        val contentSha256: String? = null,
        val etag: String? = null,
        val lastModifiedHeader: String? = null,
        val fileUtcOffsetMinutes: Int? = null,
        val appliedUtcOffsetMinutes: Int? = null,
    )

    data class ActiveSourceProgress(
        val sourceId: Long,
        val label: String,
        val phase: String, // "Downloading" or "Ingesting"
        val progressPercent: Int = -1, // 0-100, or -1 if unknown
        val downloadedBytes: Long = 0,
        val downloadTotalBytes: Long = -1,
        val channels: Int = 0,
        val programmes: Int = 0,
    )

    sealed interface MultiSourceState {
        data object Idle : MultiSourceState

        data object Pending : MultiSourceState

        data class Processing(
            val completedCount: Int,
            val totalSources: Int,
            val activeSourceLabels: List<String>,
            val activeProgress: Map<Long, ActiveSourceProgress> = emptyMap(),
            val totalChannels: Int = 0,
            val totalProgrammes: Int = 0,
            val totalDownloadedBytes: Long = 0,
            val completedSourceStats: Map<Long, SourceStats> = emptyMap(),
        ) : MultiSourceState

        data class Completed(
            val sourcesProcessed: Int,
            val errors: Int,
            val sourceStats: Map<Long, SourceStats> = emptyMap(),
            val totalChannels: Int = 0,
            val totalProgrammes: Int = 0,
            val totalDownloadBytes: Long = 0,
            val updatedAtMs: Long = System.currentTimeMillis(),
            val durationMs: Long = 0,
        ) : MultiSourceState

        data class Error(
            val reason: String,
        ) : MultiSourceState

        data class Retrying(
            val attempt: Int,
            val maxAttempts: Int,
            val nextRetryAtMs: Long,
            val reason: String,
        ) : MultiSourceState

        data object Clearing : MultiSourceState

        /**
         * The phase after ingestion (index rebuild, FTS rebuild or vacuum), so the UI shows work in
         * progress instead of a stuck 100%.
         */
        data class Finalizing(
            val phase: String,
            /** The guide sources whose import the guide index is taking in: their cards say "Indexing". */
            val indexingSourceIds: Set<Long> = emptySet(),
            /** Indexing waits until video stops playing: their cards say so. */
            val waitingForPlayback: Boolean = false,
            val totalChannels: Int = 0,
            val totalProgrammes: Int = 0,
            val totalDownloadBytes: Long = 0,
            val durationMs: Long = 0,
        ) : MultiSourceState
    }

    // Lazy: this class is built in Application.onCreate, ahead of everything else on startup.
    private val prefs: SharedPreferences by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val appSettings by lazy { AppSettings(context) }
    private val scope = AppScopes.create("EpgFileManager", Dispatchers.Default)

    /** The free space that each download and ingest checks before it starts. */
    private val freeSpace = FreeSpace.of(context)

    private var processJob: Job? = null

    private val _state = MutableStateFlow<MultiSourceState>(MultiSourceState.Idle)
    val state: StateFlow<MultiSourceState> = _state.asStateFlow()

    /**
     * The providers that read the guide sources [_state] is reporting on. A shared guide source
     * gives one run for all its providers. Null until the first refresh of the process.
     */
    private val _refreshProviderIds = MutableStateFlow<Set<Long>?>(null)

    /**
     * [state] as seen by [providerId]. One pipeline serves every provider, so a screen that reads
     * [state] directly shows another provider's refresh as its own. This reports Idle for a run
     * that is not this provider's.
     */
    fun stateFor(providerId: Long): Flow<MultiSourceState> = stateFor(setOf(providerId))

    /** [state] as seen by a screen reporting on all of [providerIds] (Search the guide's sources). */
    fun stateFor(providerIds: Set<Long>): Flow<MultiSourceState> =
        combine(_state, _refreshProviderIds) { state, runProviderIds ->
            if (isRunFor(runProviderIds, providerIds)) state else MultiSourceState.Idle
        }

    /** Sets the run's providers from the links of the guide sources it covers. */
    private suspend fun claimRun(sources: List<EpgSourceEntity>) {
        val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
        _refreshProviderIds.value = sources.flatMap { sourceDao.getProviderIds(it.id) }.toSet()
    }

    /**
     * Serialises ingestion. The staging tables are a single process-wide pair, so a second run
     * starting mid-flight clears the rows the first one is still filling. Not covered by
     * [processJob]: [EpgSyncWorker] calls [processAllSources] directly to stay inside its wake
     * lock, so a background sync and a screen's refresh can otherwise overlap.
     */
    private val ingestMutex = Mutex()

    private val okHttpClient by lazy {
        org.njarasoa.fijerena.core.player.network.NetworkModule.okHttpClient
            .newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.MINUTES)
            .build()
    }

    private fun isFixedDevice(): Boolean {
        val type = DeviceDetector.detect().deviceType
        return type != DeviceType.GENERIC_MOBILE
    }

    /**
     * Whether there is free space for staged ingestion ([SpaceNeed.stagedIngest]): staging doubles
     * epg_programme for a while. Else the run ingests directly into the live tables, and each
     * source checks its own space first (LS-Q1 (a), see [ingestDownloadedSource]).
     */
    private fun shouldUseStaging(): Boolean {
        // 0 when the file is not there (a fresh install): staging needs the margin only.
        val dbSize = context.getDatabasePath(EpgIndexDatabase.DB_NAME).length()
        val shortfall = StorageSpace.shortfall(SpaceNeed.stagedIngest(dbSize), freeSpace)
        if (shortfall != null) {
            Log.w(TAG, "Not enough space for staged ingestion (${shortfall.raw}): direct ingestion")
        }
        return shortfall == null
    }

    @OptIn(UnstableApi::class)
    private fun isPlaybackActive(): Boolean {
        val svc = StreamingPlaybackService.getInstance() ?: return false
        val state = svc.playbackState.value
        return state is PlaybackState.Playing || state is PlaybackState.Buffering
    }

    fun initialize() {
        scope.launch {
            migrateFromAppSettings()
            // Once per install: the duplicate automatic Xtream guide sources older builds left.
            AutoXmltvSources.cleanUpOnce(context)
            copyLegacyRefreshInterval()
            val indexer = EpgIndexer.getInstance(context)
            val ftsWasStale = indexer.initialize()
            cleanupStrayFiles()

            // If a previous session left fts_stale=true (process killed mid-rebuild by Doze),
            // schedule rebuild via WorkManager so it runs under a foreground-service wake lock
            // and survives if the user exits the app before it finishes.
            if (ftsWasStale) {
                val request = OneTimeWorkRequestBuilder<EpgFtsRebuildWorker>().build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    EpgFtsRebuildWorker.WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request,
                )
            }

            // Schedule WorkManager periodic sync (Doze-aware, works on both mobile and TV), and
            // again whenever a guide source is added, removed, switched or given another interval.
            SettingsDatabase
                .getInstance(context)
                .epgSourceDao()
                .getAllSources()
                .map { sources -> EpgRefreshSchedule.workIntervalHours(sources, EpgRefreshSchedule.legacyIntervalHours(appSettings)) }
                .distinctUntilChanged()
                .collect { intervalHours -> scheduleAutoRefresh(intervalHours) }
        }
    }

    /** Once per install, the retired device-wide interval goes into every guide source without one of its own. */
    private suspend fun copyLegacyRefreshInterval() {
        try {
            EpgRefreshSchedule.copyLegacyIntervalOnce(appSettings) { hours ->
                EpgRefreshSchedule.fillUnsetIntervals(SettingsDatabase.getInstance(context), hours)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Copying the device-wide guide refresh interval failed; retried next start", e)
        }
    }

    private suspend fun migrateFromAppSettings() {
        if (prefs.getBoolean(KEY_MIGRATED_TO_SOURCES, false)) return

        try {
            val appSettings = AppSettings(context)
            val oldUrl = appSettings.epgUrl
            val oldTz = appSettings.epgTimezoneOffsetHours

            if (oldUrl.isNotBlank()) {
                val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
                // EPG belongs to a provider - the legacy global URL migrates onto the profile's first.
                val ownerId =
                    ProviderRepository(context)
                        .profileSourcesList()
                        .firstOrNull()
                        ?.id
                if (sourceDao.getSourceCount() == 0 && ownerId != null) {
                    val label = extractLabel(oldUrl)
                    sourceDao.insertGuide(
                        EpgSourceEntity(
                            url = oldUrl,
                            label = label,
                            timezoneOffsetHours = oldTz,
                        ),
                        listOf(ownerId),
                    )
                }
            }

            val oldFile = File(context.cacheDir, "xmltv_global.xml")
            if (oldFile.exists()) {
                oldFile.delete()
            }

            prefs.edit { putBoolean(KEY_MIGRATED_TO_SOURCES, true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Migration from AppSettings failed", e)
        }
    }

    /**
     * Runs [dbQueryAndProcess] as the one processing job, on the file manager's own scope so it
     * survives ViewModel clearing and auto-refresh rescheduling. A new request cancels the old one.
     *
     * @param onComplete Callback invoked after processing completes (on WiFi or after confirmation).
     * @param onCellularConfirm Callback invoked if user must confirm on cellular.
     *                          Return false to cancel processing.
     */
    private fun launchGenericTask(
        taskId: String,
        providerIds: Set<Long>?,
        onComplete: (suspend () -> Unit)? = null,
        onCellularConfirm: (suspend () -> Boolean)? = null,
        dbQueryAndProcess: suspend () -> Unit,
    ) {
        processJob?.cancel()
        // Claim the state before the first Pending, so the waiting screen belongs to the providers
        // that asked for the refresh rather than to whoever ran last.
        _refreshProviderIds.value = providerIds
        if (_state.value !is MultiSourceState.Processing) {
            _state.value = MultiSourceState.Pending
        }

        processJob =
            scope.launch {
                // The monitor starts with the player; a refresh before any playback starts it here.
                NetworkMonitor.init(context)
                val networkType = NetworkMonitor.currentNetworkType
                val shouldProceed =
                    if (networkType == NetworkType.CELLULAR && onCellularConfirm != null) {
                        onCellularConfirm()
                    } else {
                        true
                    }

                if (!shouldProceed) {
                    if (_state.value is MultiSourceState.Pending) {
                        _state.value = MultiSourceState.Idle
                    }
                    return@launch
                }

                val task =
                    object : RefreshTask {
                        override val id = taskId
                        override val priority = RefreshPriority.MEDIUM

                        override suspend fun execute() {
                            val maxAttempts = 5
                            val retryDelaysMs =
                                listOf(
                                    1L * 60 * 1000,
                                    2L * 60 * 1000,
                                    4L * 60 * 1000,
                                    8L * 60 * 1000,
                                    16L * 60 * 1000,
                                )

                            var currentAttempt = 0
                            var lastException: Exception? = null

                            while (currentAttempt <= maxAttempts) {
                                try {
                                    if (currentAttempt > 0) {
                                        Log.i(TAG, "Retrying task $taskId (attempt $currentAttempt/$maxAttempts)")
                                    }
                                    dbQueryAndProcess()
                                    onComplete?.invoke()
                                    return
                                } catch (e: CancellationException) {
                                    // Not a failure: the caller cancelled this job. A retry would
                                    // keep it alive past the cancellation.
                                    throw e
                                } catch (e: Exception) {
                                    lastException = e
                                    // A full disk is not tried again: the run ends now with its error.
                                    currentAttempt = if (e.isDiskFull()) maxAttempts + 1 else currentAttempt + 1

                                    if (currentAttempt <= maxAttempts) {
                                        val delayMs = retryDelaysMs[currentAttempt - 1]
                                        val nextRetryAt = System.currentTimeMillis() + delayMs
                                        _state.value =
                                            MultiSourceState.Retrying(
                                                attempt = currentAttempt,
                                                maxAttempts = maxAttempts,
                                                nextRetryAtMs = nextRetryAt,
                                                reason = friendlyErrorMessage(e, context, appSettings.isNerdMode),
                                            )
                                        Log.w(
                                            TAG,
                                            "Task $taskId failed (attempt $currentAttempt/$maxAttempts). Retrying in ${delayMs / 60000} min. Error: ${e.message}",
                                        )
                                        delay(delayMs)
                                    }
                                }
                            }

                            Log.e(TAG, "Task $taskId failed after $maxAttempts retries: ${lastException?.message}", lastException)
                            _state.value =
                                MultiSourceState.Error(
                                    lastException?.let { friendlyErrorMessage(it, context, appSettings.isNerdMode) }
                                        ?: context.resources.getQuantityString(
                                            R.plurals.epg_error_task_failed_retries_format,
                                            maxAttempts,
                                            maxAttempts,
                                        ),
                                )
                            onComplete?.invoke()
                        }
                    }
                RefreshQueue.submit(task)
            }
    }

    fun launchRefreshStale(
        providerId: Long,
        onComplete: (suspend () -> Unit)? = null,
        onCellularConfirm: (suspend () -> Boolean)? = null,
    ) {
        launchRefreshStale(listOf(providerId), onComplete, onCellularConfirm)
    }

    /**
     * Refreshes the stale guide sources of [providerIds] in one run. A guide source that several
     * of them read is in the run one time.
     */
    fun launchRefreshStale(
        providerIds: List<Long>,
        onComplete: (suspend () -> Unit)? = null,
        onCellularConfirm: (suspend () -> Boolean)? = null,
    ) {
        if (providerIds.isEmpty()) return
        launchGenericTask(refreshStaleTaskId(providerIds), providerIds.toSet(), onComplete, onCellularConfirm) {
            val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
            val now = System.currentTimeMillis()
            val unsetHours = EpgRefreshSchedule.legacyIntervalHours(appSettings)
            val staleSources =
                sourceDao.getEnabledSourcesForProviders(providerIds).filter { EpgRefreshSchedule.isStale(it, now, unsetHours) }
            processOrIdle(staleSources)
        }
    }

    /** Retries the failed guide sources of [providerIds] in one run, as [launchRefreshStale]. */
    fun launchRefreshFailed(
        providerIds: List<Long>,
        onComplete: (suspend () -> Unit)? = null,
        onCellularConfirm: (suspend () -> Boolean)? = null,
    ) {
        if (providerIds.isEmpty()) return
        launchGenericTask(refreshFailedTaskId(providerIds), providerIds.toSet(), onComplete, onCellularConfirm) {
            processOrIdle(SettingsDatabase.getInstance(context).epgSourceDao().getFailedSourcesForProviders(providerIds))
        }
    }

    /** Processes [sources]; with none, a waiting screen goes back to Idle. */
    private suspend fun processOrIdle(sources: List<EpgSourceEntity>) {
        if (sources.isNotEmpty()) {
            processAllSourcesInternal(sources)
        } else if (_state.value is MultiSourceState.Pending) {
            _state.value = MultiSourceState.Idle
        }
    }

    fun launchProcessSingleSource(
        sourceId: Long,
        onComplete: (suspend () -> Unit)? = null,
        onCellularConfirm: (suspend () -> Boolean)? = null,
    ) {
        // The run claims its media sources once it reads the guide source (processSingleSourceInternal).
        launchGenericTask("epg_refresh_source_$sourceId", null, onComplete, onCellularConfirm) {
            processSingleSourceInternal(sourceId)
        }
    }

    /**
     * Ingests [sources] with parallel workers. Append-only: the database stays searchable throughout.
     * [EpgSyncWorker] calls this directly, outside [processJob].
     */
    suspend fun processAllSources(sources: List<EpgSourceEntity>) {
        processAllSourcesInternal(sources)
    }

    @VisibleForTesting
    internal data class DownloadedSource(
        val source: EpgSourceEntity,
        val label: String,
        val tmpFile: File,
        val downloadedBytes: Long,
        val downloadDurationMs: Long = 0,
        /** True when a `304 Not Modified` or a matching content hash shows the file is unchanged: no ingestion. */
        val unchanged: Boolean = false,
        /** SHA-256 of the payload as ingested (decompressed for `.gz`); null when [ingestDownloadedSource] computes it. */
        val contentSha256: String? = null,
        val etag: String? = null,
        val lastModifiedHeader: String? = null,
        /** Whether the live index held this source's rows when the download started, which any skip needs. */
        val indexHasRows: Boolean = false,
    )

    /** [ingestDownloadedSource]'s result: the stats, plus the [IngestRecord] it held back on the staging path. */
    @VisibleForTesting
    internal data class IngestOutcome(
        val stats: SourceStats,
        val pending: IngestRecord? = null,
    )

    private suspend fun processAllSourcesInternal(sources: List<EpgSourceEntity>) =
        ingestMutex.withLock {
            if (sources.isEmpty()) {
                _state.value = MultiSourceState.Error(context.getString(R.string.epg_error_no_sources))
                return@withLock
            }
            if (blockedByLowStorage(sources)) return@withLock

            claimRun(sources)
            val startTime = System.currentTimeMillis()
            val fixedDevice = isFixedDevice()
            val batchSize = if (fixedDevice) EpgIndexer.BATCH_SIZE_TV else EpgIndexer.BATCH_SIZE_MOBILE
            // Before the try, so the catch block's cleanup passes the value beginBulkIngestion() got.
            val useStaging = shouldUseStaging()
            try {
                val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
                val indexer = EpgIndexer.getInstance(context)

                val maxDownloadConcurrency = if (fixedDevice) 2 else 3
                val downloadSemaphore = Semaphore(maxDownloadConcurrency)
                val maxIngestionConcurrency = 2
                val completedStats = CopyOnWriteArrayList<SourceStats>()
                val activeLabels = CopyOnWriteArrayList<String>()
                val activeProgress = ConcurrentHashMap<Long, ActiveSourceProgress>()
                // Staging path: per-source markIngested writes held back until the swap commits.
                val pendingIngests = ConcurrentHashMap<Long, IngestRecord>()

                // Channel: downloads produce, ingestion consumes
                val ingestionQueue = Channel<DownloadedSource>(Channel.UNLIMITED)
                val sourceStartTimeMap = ConcurrentHashMap<Long, Long>()

                indexer.setIndexing()
                if (useStaging) {
                    indexer.clearStaging()
                }

                _state.value =
                    MultiSourceState.Processing(
                        completedCount = 0,
                        totalSources = sources.size,
                        activeSourceLabels = emptyList(),
                    )

                val allStats =
                    try {
                        coroutineScope {
                            // Bulk setup runs in parallel with the downloads, which write only cache
                            // files. Consumers await it before they touch the DB.
                            val bulkReady = async(Dispatchers.IO) { indexer.beginBulkIngestion(useStaging) }

                            // Consumer: ingest downloaded files in parallel (SQLite handles locking)
                            val ingestionJobs =
                                (1..maxIngestionConcurrency).map {
                                    launch {
                                        bulkReady.await() // Ensure indexes are dropped before first ingest
                                        for (downloaded in ingestionQueue) {
                                            activeProgress[downloaded.source.id] =
                                                ActiveSourceProgress(
                                                    sourceId = downloaded.source.id,
                                                    label = downloaded.label,
                                                    phase = "Ingesting",
                                                    downloadedBytes = downloaded.downloadedBytes,
                                                    downloadTotalBytes = downloaded.downloadedBytes,
                                                )
                                            updateAggregateProgress(completedStats, activeLabels, activeProgress, sources.size)

                                            val outcome =
                                                ingestDownloadedSource(
                                                    downloaded,
                                                    sourceDao,
                                                    indexer,
                                                    activeProgress,
                                                    batchSize = batchSize,
                                                    useStaging = useStaging,
                                                    isPlaybackActive = ::isPlaybackActive,
                                                ) {
                                                    updateAggregateProgress(completedStats, activeLabels, activeProgress, sources.size)
                                                }

                                            val sourceId = downloaded.source.id
                                            outcome.pending?.let { pendingIngests[sourceId] = it }
                                            val sourceDuration = sourceStartTimeMap[sourceId]?.let { System.currentTimeMillis() - it } ?: 0
                                            val finalStats = outcome.stats.copy(durationMs = sourceDuration)

                                            activeLabels.remove(downloaded.label)
                                            activeProgress.remove(sourceId)
                                            completedStats.add(finalStats)
                                            updateAggregateProgress(completedStats, activeLabels, activeProgress, sources.size)
                                        }
                                    }
                                }

                            // Producers: download sources concurrently
                            val downloadJobs =
                                sources.map { source ->
                                    async {
                                        sourceStartTimeMap[source.id] = System.currentTimeMillis()
                                        val label = source.label.ifBlank { extractLabel(source.url) }
                                        downloadSemaphore.withPermit {
                                            activeLabels.add(label)
                                            activeProgress[source.id] = ActiveSourceProgress(source.id, label, "Downloading")
                                            updateAggregateProgress(completedStats, activeLabels, activeProgress, sources.size)

                                            val result =
                                                downloadSource(source, label, sourceDao, activeProgress) {
                                                    updateAggregateProgress(completedStats, activeLabels, activeProgress, sources.size)
                                                }

                                            when {
                                                result != null && result.unchanged -> {
                                                    // Unchanged (304 or same content hash): no ingestion, keep the last counts.
                                                    val sourceDuration =
                                                        sourceStartTimeMap[source.id]?.let { System.currentTimeMillis() - it } ?: 0
                                                    activeLabels.remove(label)
                                                    activeProgress.remove(source.id)
                                                    completedStats.add(
                                                        SourceStats(
                                                            sourceId = source.id,
                                                            label = label,
                                                            downloadBytes = result.downloadedBytes,
                                                            channelsIngested = source.lastChannels,
                                                            programmesIngested = source.lastProgrammes,
                                                            durationMs = sourceDuration,
                                                            unchanged = true,
                                                        ),
                                                    )
                                                    updateAggregateProgress(completedStats, activeLabels, activeProgress, sources.size)
                                                }

                                                result != null -> {
                                                    activeProgress[source.id] =
                                                        ActiveSourceProgress(
                                                            sourceId = source.id,
                                                            label = label,
                                                            phase = "Awaiting Ingestion",
                                                            downloadedBytes = result.downloadedBytes,
                                                            downloadTotalBytes = result.downloadedBytes,
                                                        )
                                                    updateAggregateProgress(completedStats, activeLabels, activeProgress, sources.size)
                                                    ingestionQueue.send(result)
                                                }

                                                else -> {
                                                    val sourceDuration =
                                                        sourceStartTimeMap[source.id]?.let { System.currentTimeMillis() - it } ?: 0
                                                    activeLabels.remove(label)
                                                    activeProgress.remove(source.id)
                                                    completedStats.add(
                                                        SourceStats(
                                                            source.id,
                                                            label,
                                                            durationMs = sourceDuration,
                                                            error = context.getString(R.string.sync_error_download_failed),
                                                        ),
                                                    )
                                                    updateAggregateProgress(completedStats, activeLabels, activeProgress, sources.size)
                                                }
                                            }
                                        }
                                    }
                                }

                            downloadJobs.forEach { it.await() }
                            ingestionQueue.close()

                            ingestionJobs.forEach { it.join() }

                            completedStats.toList()
                        }
                    } finally {
                        ingestionQueue.close()
                        var remaining = ingestionQueue.tryReceive().getOrNull()
                        while (remaining != null) {
                            try {
                                remaining.tmpFile.delete()
                            } catch (e: Exception) {
                                // cancellation-ok: non-suspend
                                Log.w(TAG, "Error cleaning up temporary file for ${remaining.label}", e)
                            }
                            remaining = ingestionQueue.tryReceive().getOrNull()
                        }
                    }

                val totalChannels = allStats.sumOf { it.channelsIngested }
                val totalProgrammes = allStats.sumOf { it.programmesIngested }
                val totalBytes = allStats.sumOf { it.downloadBytes }
                // A skipped (unchanged) source must never be in here: swapAndRebuildFts deletes that
                // source's primary rows before transferring staging, and staging has nothing for it.
                val syncedIds = allStats.filter { it.error == null && !it.unchanged }.map { it.sourceId }
                val indexStartMs = System.currentTimeMillis()
                var incrementalIndexMs: Long? = null

                // Finalizing: rebuild the B-tree query indexes (fast compared to the FTS rebuild).
                _state.value =
                    MultiSourceState.Finalizing(
                        phase = "Rebuilding indexes\u2026",
                        indexingSourceIds = syncedIds.toSet(),
                        totalChannels = totalChannels,
                        totalProgrammes = totalProgrammes,
                        totalDownloadBytes = totalBytes,
                    )

                // `unchanged` stats carry the last counts forward but don't count as ingested, or a run
                // where every source is unchanged would swap and rebuild FTS for nothing.
                val anyIngested =
                    allStats.any {
                        it.error == null && !it.unchanged && (it.channelsIngested > 0 || it.programmesIngested > 0)
                    }

                // beginBulkIngestion() marked FTS stale before it knew. Nothing was written, so FTS
                // is still consistent.
                if (!anyIngested) {
                    indexer.markFtsClean()
                }

                // Swap and FTS rebuild in one transaction, so search keeps working throughout.
                if (anyIngested && useStaging) {
                    _state.value =
                        MultiSourceState.Finalizing(
                            phase = "Swapping to primary guide\u2026",
                            indexingSourceIds = syncedIds.toSet(),
                            totalChannels = totalChannels,
                            totalProgrammes = totalProgrammes,
                            totalDownloadBytes = totalBytes,
                        )
                    // The index of the refreshed sources only, in batches that wait while video
                    // plays (the pausable guide indexing plan). A stale index (an earlier rebuild was
                    // cut off) still needs the full rebuild.
                    if (indexer.isFtsStale()) {
                        swapThenMarkIngested(indexer, sourceDao, syncedIds, pendingIngests)
                        invalidateXmltvCache(sources, allStats)
                    } else {
                        fun indexing(waiting: Boolean) =
                            MultiSourceState.Finalizing(
                                phase = if (waiting) PHASE_INDEXING_WAITS else PHASE_INDEXING,
                                indexingSourceIds = syncedIds.toSet(),
                                waitingForPlayback = waiting,
                                totalChannels = totalChannels,
                                totalProgrammes = totalProgrammes,
                                totalDownloadBytes = totalBytes,
                            )
                        _state.value = indexing(waiting = false)
                        incrementalIndexMs =
                            indexer.swapAndIndexIncrementally(
                                sourceIds = syncedIds,
                                pause = PlaybackPause({ isMediaPlaying(context) }),
                                onWaiting = { waiting -> _state.value = indexing(waiting) },
                                onSwapped = {
                                    markIngested(indexer, sourceDao, syncedIds, pendingIngests)
                                    invalidateXmltvCache(sources, allStats)
                                },
                            )
                    }
                } else if (anyIngested) {
                    invalidateXmltvCache(sources, allStats)
                }

                // FTS rebuild runs in the caller's coroutine so the WorkManager wake lock covers all
                // of it. A kill mid-rebuild leaves fts_stale=true in prefs, which on Shield gave a
                // permanent LIKE fallback under Doze. The staging path already rebuilt above.
                if (anyIngested) {
                    try {
                        if (!useStaging) indexer.rebuildFtsAndUpdateState()
                        indexer.incrementalVacuum()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "FTS rebuild failed: ${e.message}", e)
                        CrashLog.record("guide index rebuild failed", e)
                    }
                    // The incremental index counts its own time, waits for playback left out.
                    sourceDao.setIndexDuration(syncedIds, incrementalIndexMs ?: (System.currentTimeMillis() - indexStartMs))
                }

                // Restore the FTS sync triggers dropped in beginBulkIngestion() only after the swap
                // and the FTS rebuild: the rebuild scans epg_programme and needs no triggers, so with
                // them off the swap's INSERT…SELECT fires no trigger per row. Unconditional: a run
                // where nothing changed still needs its triggers for the next incremental write.
                indexer.endBulkIngestion(useStaging)

                // Completed only after the FTS rebuild and the index and trigger restore: earlier, a
                // search hit EpgIndexBusyException (FTS still stale) or a full table scan (query
                // indexes not yet recreated).
                val endTime = System.currentTimeMillis()
                val finalState =
                    MultiSourceState.Completed(
                        sourcesProcessed = sources.size,
                        errors = allStats.count { it.error != null },
                        sourceStats = allStats.associateBy { it.sourceId },
                        totalChannels = totalChannels,
                        totalProgrammes = totalProgrammes,
                        totalDownloadBytes = totalBytes,
                        updatedAtMs = endTime,
                        durationMs = endTime - startTime,
                    )
                _state.value = finalState
                updateLastPipelineStats(finalState)
                noteFailedSources(sources, allStats)
                detectEmptyOwnGuides(allStats)
            } catch (e: Exception) {
                // cancellation-ok: cleans up, then rethrows CancellationException below
                withContext(NonCancellable) {
                    EpgIndexer.getInstance(context).endBulkIngestion(useStaging)
                }
                // Checked before the log and the Error state: a cancelled run (for example, the user
                // left the screen) is not a failure.
                if (e is CancellationException) {
                    // Idle, not the last mid-run state: that refresh no longer exists, and the next
                    // screen open must not show a sync that stays in progress.
                    _state.value = MultiSourceState.Idle
                    throw e
                }
                Log.e(TAG, "processAllSources failed: ${e.message}", e)
                CrashLog.record("guide refresh failed", e)
                // A full disk on the staging path (the swap, for example): staging goes now.
                if (useStaging && e.isDiskFull()) freeStagingAfterFullDisk(EpgIndexer.getInstance(context), null)
                _state.value = MultiSourceState.Error(friendlyErrorMessage(e, context, appSettings.isNerdMode))
            }
        }

    /**
     * With storage low ([StorageSpace]) the refresh does not start: each of [sources] keeps a
     * failure that says so, on its card and in Retry failed, and the run ends in that error.
     */
    private suspend fun blockedByLowStorage(sources: List<EpgSourceEntity>): Boolean {
        val free = StorageSpace.lowFreeBytes(context) ?: return false
        val error = StoredError(ErrorKind.STORAGE_LOW, raw = StorageSpace.rawText(free))
        val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
        sources.forEach { sourceDao.markError(it.id, error.encode()) }
        Log.w(TAG, "Guide refresh not started: ${StorageSpace.rawText(free)}")
        CrashLog.note("guide refresh not started: storage low", sources.joinToString("\n") { it.label.ifBlank { it.url } })
        _state.value = MultiSourceState.Error(error.message(context, appSettings.isNerdMode))
        return true
    }

    /** Each guide source this run couldn't read goes to Settings → Diagnostics: its name, address and why. */
    private fun noteFailedSources(
        sources: List<EpgSourceEntity>,
        stats: List<SourceStats>,
    ) {
        stats.filter { it.error != null }.forEach { stat ->
            val url = sources.firstOrNull { it.id == stat.sourceId }?.url.orEmpty()
            CrashLog.note("guide source failed: ${stat.label}", "$url\n${stat.error}")
        }
    }

    /**
     * Sources this run downloaded and ingested with no channels, and that never had any (one that
     * had is an error instead, [isFailedEmptyIngest]): an Xtream source's automatic guide source
     * among them turns its "Provides a guide" off ([AutoXmltvSources.onEmptyIngest]).
     */
    private suspend fun detectEmptyOwnGuides(stats: List<SourceStats>) {
        val emptyIds = stats.filter { it.error == null && !it.unchanged && it.channelsIngested == 0 }.map { it.sourceId }
        if (emptyIds.isNotEmpty()) AutoXmltvSources.onEmptyIngest(context, emptyIds)
    }

    /**
     * Clears the parsed XMLTV cache (in memory, 12 h TTL) of every media source that reads a guide
     * source this sync ingested, so the player does not show the old guide until the TTL ends.
     */
    private suspend fun invalidateXmltvCache(
        sources: List<EpgSourceEntity>,
        stats: List<SourceStats>,
    ) {
        val ingestedSourceIds = stats.filter { it.error == null }.map { it.sourceId }.toSet()
        val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
        sources
            .filter { it.id in ingestedSourceIds }
            .flatMap { sourceDao.getProviderIds(it.id) }
            .distinct()
            .forEach { providerId ->
                XmltvEpgService(context, providerId).clearCache()
            }
    }

    private fun updateAggregateProgress(
        completedStats: List<SourceStats>,
        activeLabels: List<String>,
        activeProgress: Map<Long, ActiveSourceProgress>,
        totalSources: Int,
    ) {
        _state.value =
            MultiSourceState.Processing(
                completedCount = completedStats.size,
                totalSources = totalSources,
                activeSourceLabels = activeLabels.toList(),
                activeProgress = activeProgress.toMap(),
                totalChannels = completedStats.sumOf { it.channelsIngested } + activeProgress.values.sumOf { it.channels },
                totalProgrammes = completedStats.sumOf { it.programmesIngested } + activeProgress.values.sumOf { it.programmes },
                totalDownloadedBytes = completedStats.sumOf { it.downloadBytes } + activeProgress.values.sumOf { it.downloadedBytes },
                completedSourceStats = completedStats.associateBy { it.sourceId },
            )
    }

    private suspend fun processSingleSourceInternal(sourceId: Long) =
        ingestMutex.withLock {
            val startTime = System.currentTimeMillis()
            val batchSize = if (isFixedDevice()) EpgIndexer.BATCH_SIZE_TV else EpgIndexer.BATCH_SIZE_MOBILE
            // Before the try, so the catch block's cleanup passes the value beginBulkIngestion() got.
            val useStaging = shouldUseStaging()
            try {
                val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
                val source =
                    sourceDao.getSourceById(sourceId) ?: run {
                        _state.value = MultiSourceState.Error(context.getString(R.string.epg_error_source_not_found))
                        return@withLock
                    }
                if (blockedByLowStorage(listOf(source))) return@withLock
                claimRun(listOf(source))
                val indexer = EpgIndexer.getInstance(context)
                val label = source.label.ifBlank { extractLabel(source.url) }

                indexer.setIndexing()
                if (useStaging) {
                    indexer.clearStaging()
                }
                val activeProgress = ConcurrentHashMap<Long, ActiveSourceProgress>()
                _state.value =
                    MultiSourceState.Processing(
                        completedCount = 0,
                        totalSources = 1,
                        activeSourceLabels = listOf(label),
                    )

                fun updateSingleProgress() {
                    _state.value =
                        MultiSourceState.Processing(
                            completedCount = 0,
                            totalSources = 1,
                            activeSourceLabels = listOf(label),
                            activeProgress = activeProgress.toMap(),
                            totalChannels = activeProgress.values.sumOf { it.channels },
                            totalProgrammes = activeProgress.values.sumOf { it.programmes },
                            totalDownloadedBytes = activeProgress.values.sumOf { it.downloadedBytes },
                        )
                }

                // Bulk setup runs in parallel with the download, as in processAllSourcesInternal.
                val bulkReady = scope.async(Dispatchers.IO) { indexer.beginBulkIngestion(useStaging) }

                activeProgress[source.id] = ActiveSourceProgress(source.id, label, "Downloading")
                updateSingleProgress()

                val downloaded = downloadSource(source, label, sourceDao, activeProgress) { updateSingleProgress() }

                bulkReady.await() // Ensure indexes are dropped before ingesting

                // Staging path: the markIngested write held back until the swap commits.
                var pendingIngest: IngestRecord? = null
                val stats =
                    if (downloaded != null && downloaded.unchanged) {
                        // Unchanged (304 or same content hash): downloadSource recorded it with
                        // markUnchanged. No ingestion.
                        SourceStats(
                            sourceId = source.id,
                            label = label,
                            downloadBytes = downloaded.downloadedBytes,
                            channelsIngested = source.lastChannels,
                            programmesIngested = source.lastProgrammes,
                            unchanged = true,
                        )
                    } else if (downloaded != null) {
                        // Buffer state between phases
                        activeProgress[source.id] =
                            ActiveSourceProgress(
                                sourceId = source.id,
                                label = label,
                                phase = "Awaiting Ingestion",
                                downloadedBytes = downloaded.downloadedBytes,
                                downloadTotalBytes = downloaded.downloadedBytes,
                            )
                        updateSingleProgress()

                        activeProgress[source.id] =
                            ActiveSourceProgress(
                                sourceId = source.id,
                                label = label,
                                phase = "Ingesting",
                                downloadedBytes = downloaded.downloadedBytes,
                                downloadTotalBytes = downloaded.downloadedBytes,
                            )
                        updateSingleProgress()

                        val outcome =
                            ingestDownloadedSource(
                                downloaded,
                                sourceDao,
                                indexer,
                                activeProgress,
                                batchSize = batchSize,
                                useStaging = useStaging,
                                isPlaybackActive = ::isPlaybackActive,
                            ) { updateSingleProgress() }
                        pendingIngest = outcome.pending
                        outcome.stats
                    } else {
                        // Download failed — error already logged
                        SourceStats(source.id, label, error = context.getString(R.string.sync_error_download_failed))
                    }

                val indexing = if (stats.error == null && !stats.unchanged) setOf(sourceId) else emptySet()
                val indexStartMs = System.currentTimeMillis()

                // Finalizing: rebuild B-tree query indexes.
                _state.value =
                    MultiSourceState.Finalizing(
                        phase = "Rebuilding indexes\u2026",
                        indexingSourceIds = indexing,
                        totalChannels = stats.channelsIngested,
                        totalProgrammes = stats.programmesIngested,
                        totalDownloadBytes = stats.downloadBytes,
                    )

                // Swap and FTS rebuild in one transaction, staging only. Never for an unchanged source:
                // staging has nothing for it, so the swap would delete its primary rows.
                if (stats.error == null && !stats.unchanged && (stats.channelsIngested > 0 || stats.programmesIngested > 0) && useStaging) {
                    _state.value =
                        MultiSourceState.Finalizing(
                            phase = "Swapping to primary guide\u2026",
                            indexingSourceIds = indexing,
                            totalChannels = stats.channelsIngested,
                            totalProgrammes = stats.programmesIngested,
                            totalDownloadBytes = stats.downloadBytes,
                        )
                    swapThenMarkIngested(indexer, sourceDao, listOf(sourceId), listOfNotNull(pendingIngest).associateBy { it.sourceId })
                }

                if (stats.error == null && !stats.unchanged && (stats.channelsIngested > 0 || stats.programmesIngested > 0)) {
                    invalidateXmltvCache(listOf(source), listOf(stats))
                }

                // Inline — same reasoning as processAllSourcesInternal (staging path already rebuilt).
                if (stats.error == null && !stats.unchanged && (stats.channelsIngested > 0 || stats.programmesIngested > 0)) {
                    try {
                        if (!useStaging) indexer.rebuildFtsAndUpdateState()
                        indexer.incrementalVacuum()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "FTS rebuild failed: ${e.message}", e)
                        CrashLog.record("guide index rebuild failed", e)
                    }
                    sourceDao.setIndexDuration(listOf(sourceId), System.currentTimeMillis() - indexStartMs)
                }

                // Restore the FTS sync triggers only after the swap and rebuild, and always (see
                // processAllSourcesInternal).
                indexer.endBulkIngestion(useStaging)

                // Completed only after the FTS rebuild and the index and trigger restore (see
                // processAllSourcesInternal).
                val endTime = System.currentTimeMillis()
                val finalState =
                    MultiSourceState.Completed(
                        sourcesProcessed = 1,
                        errors = if (stats.error != null) 1 else 0,
                        sourceStats =
                            mapOf(
                                stats.copy(durationMs = endTime - startTime).sourceId to stats.copy(durationMs = endTime - startTime),
                            ),
                        totalChannels = stats.channelsIngested,
                        totalProgrammes = stats.programmesIngested,
                        totalDownloadBytes = stats.downloadBytes,
                        updatedAtMs = endTime,
                        durationMs = endTime - startTime,
                    )
                _state.value = finalState
                updateLastPipelineStats(finalState)
                noteFailedSources(listOf(source), listOf(stats))
                detectEmptyOwnGuides(listOf(stats))
            } catch (e: Exception) {
                // cancellation-ok: cleans up, then rethrows CancellationException below
                withContext(NonCancellable) {
                    EpgIndexer.getInstance(context).endBulkIngestion(useStaging)
                }
                if (e is CancellationException) {
                    // Idle, as in processAllSourcesInternal: that refresh no longer exists.
                    _state.value = MultiSourceState.Idle
                    throw e
                }
                Log.e(TAG, "processSingleSource failed: ${e.message}", e)
                CrashLog.record("guide refresh failed", e)
                if (useStaging && e.isDiskFull()) freeStagingAfterFullDisk(EpgIndexer.getInstance(context), null)
                _state.value = MultiSourceState.Error(friendlyErrorMessage(e, context, appSettings.isNerdMode))
            }
        }

    private val _clearing = MutableStateFlow<Map<Long, EpgIndexer.ClearProgress>>(emptyMap())

    /** The guide sources whose data is being cleared ([launchClearSource]), with how far each is. */
    val clearing: StateFlow<Map<Long, EpgIndexer.ClearProgress>> = _clearing.asStateFlow()

    /**
     * Clears guide source [sourceId]'s data (Clear guide data on its card): its channels and
     * programmes, and its import state, so its next refresh imports it again in full. Runs in this
     * manager's scope, so leaving the page does not stop it, and after any refresh that is running
     * ([ingestMutex]). False, and nothing starts, when it is being cleared already.
     */
    fun launchClearSource(
        sourceId: Long,
        onComplete: suspend () -> Unit,
    ): Boolean {
        var started = false
        _clearing.update { now ->
            if (sourceId in now) {
                now
            } else {
                started = true
                now + (sourceId to EpgIndexer.ClearProgress.Deleting(0, 0))
            }
        }
        if (!started) return false
        scope.launch {
            try {
                ingestMutex.withLock {
                    EpgIndexer.getInstance(context).clearSource(sourceId) { progress ->
                        _clearing.update { it + (sourceId to progress) }
                    }
                    SettingsDatabase.getInstance(context).epgSourceDao().forgetIngest(sourceId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Clear source $sourceId failed: ${e.message}", e)
                CrashLog.record("Clear guide data failed", e)
            } finally {
                _clearing.update { it - sourceId }
                onComplete()
            }
        }
        return true
    }

    /** Cancels all processing, then clears all guide data, on this manager's scope (it outlives the ViewModel). */
    fun launchClearAllData(onComplete: (suspend () -> Unit)? = null) {
        processJob?.cancel()
        processJob = null
        _state.value = MultiSourceState.Clearing
        scope.launch {
            try {
                RefreshQueue.cancelAll()
                // Waits for a cancelled run to release the lock, so clearAll() never runs during a
                // batch write.
                ingestMutex.withLock {
                    EpgIndexer.getInstance(context).clearAll()
                    // The sources' stats and validators describe rows that no longer exist: kept, they
                    // let the next refresh skip on a 304 or hash match against an empty guide (G-11).
                    // Bookkeeping columns only: the settings-sync trigger ignores them.
                    SettingsDatabase.getInstance(context).epgSourceDao().resetAllIngestionState()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Clear all data failed: ${e.message}", e)
            } finally {
                _state.value = MultiSourceState.Idle
                onComplete?.invoke()
            }
        }
    }

    /**
     * Downloads a source to a cache file with progress. Null on failure (the error is recorded in
     * sourceDao).
     *
     * Sends `If-None-Match`/`If-Modified-Since` when the source has validators; a `304` gives an
     * unchanged result with no body read. For a source that is not `.gz`, the same read computes a
     * SHA-256, and a match with [EpgSourceEntity.lastContentSha256] also gives unchanged
     * ([canSkipIngest]). A `.gz` is hashed after decompression in [ingestDownloadedSource]: the
     * gzip mtime header changes the raw bytes.
     *
     * Both skips need the live index to hold the source's rows ([buildDownloadRequest]).
     */
    private suspend fun downloadSource(
        source: EpgSourceEntity,
        label: String,
        sourceDao: EpgSourceDao,
        activeProgress: ConcurrentHashMap<Long, ActiveSourceProgress>,
        onProgressUpdate: () -> Unit,
    ): DownloadedSource? {
        val tmpFile = File(context.cacheDir, "xmltv_source_${source.id}_tmp")
        var downloadedBytes = 0L
        var lastError: String? = null
        // The throwable gives the stored error kind; `lastError` stays for the HTTP 4xx check below.
        var lastException: Throwable? = null
        var notModified = false
        var responseEtag: String? = null
        var responseLastModified: String? = null
        var computedSha256: String? = null
        val isGzip = source.url.endsWith(".gz", ignoreCase = true)
        val downloadStartMs = System.currentTimeMillis()

        try {
            // The automatic Xtream guide source is stored without its login: added here, only for the request.
            val fetchUrl = AutoXmltvSources.fetchUrl(context, source)
            if (fetchUrl == null) {
                sourceDao.markError(source.id, StoredError(ErrorKind.GUIDE_LOGIN_MISSING).encode())
                return null
            }
            val indexHasRows = EpgIndexer.getInstance(context).hasProgrammesForSource(source.id)
            if (!indexHasRows && (source.etag != null || source.lastModifiedHeader != null || source.lastContentSha256 != null)) {
                Log.i(TAG, "EPG source $label has validators but no rows in the index — treating as changed")
            }
            // A full disk stops the tries at once: each try writes the whole file again.
            for (attempt in triesUntilDiskFull(5) { lastException }) {
                try {
                    val request = buildDownloadRequest(source, indexHasRows, fetchUrl)
                    withContext(Dispatchers.IO) {
                        okHttpClient.newCall(request).await().use { response ->
                            if (response.code == 304) {
                                notModified = true
                                lastError = null
                                return@use
                            }
                            if (!response.isSuccessful) {
                                lastError = "server returned HTTP ${response.code}"
                                lastException = HttpStatusException(response.code)
                                Log.w(TAG, "EPG download: $lastError (attempt $attempt)")
                                return@use
                            }

                            // Keep the previous validators if this response doesn't repeat them —
                            // some servers only send ETag/Last-Modified on the first response.
                            responseEtag = response.header("ETag") ?: source.etag
                            responseLastModified = response.header("Last-Modified") ?: source.lastModifiedHeader

                            val body = response.body
                            val contentLength = body.contentLength()
                            // Not enough space for the file: the download does not start, and is
                            // not tried again (a full disk, isDiskFull). The source keeps the error.
                            StorageSpace
                                .shortfall(SpaceNeed.xmltvDownload(contentLength, source.lastDownloadBytes), freeSpace)
                                ?.let { throw NotEnoughSpaceException(it) }
                            val digest = if (!isGzip) java.security.MessageDigest.getInstance("SHA-256") else null
                            var totalRead = 0L

                            tmpFile.outputStream().buffered(STREAM_BUFFER_SIZE).use { output ->
                                val input = body.byteStream()
                                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                                var lastReportedBytes = 0L
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    // Cancel and Clear all data must stop a 100+ MB download, not wait
                                    // for its end (the next level rock solid resilience plan, R-14).
                                    ensureActive()
                                    output.write(buffer, 0, read)
                                    digest?.update(buffer, 0, read)
                                    totalRead += read
                                    // Throttle UI updates to every 512KB
                                    if (totalRead - lastReportedBytes >= 524288) {
                                        lastReportedBytes = totalRead
                                        val pct =
                                            if (contentLength >
                                                0
                                            ) {
                                                ((totalRead * 100) / contentLength).toInt().coerceIn(0, 100)
                                            } else {
                                                -1
                                            }
                                        activeProgress[source.id] =
                                            ActiveSourceProgress(
                                                sourceId = source.id,
                                                label = label,
                                                phase = "Downloading",
                                                progressPercent = pct,
                                                downloadedBytes = totalRead,
                                                downloadTotalBytes = contentLength,
                                            )
                                        onProgressUpdate()
                                    }
                                }
                                output.flush()
                            }

                            // read() returning -1 only means the connection closed, so a download cut
                            // short looks complete and fails late, in the XML parser. Content-Length,
                            // when sent, gives the exact size to check.
                            if (contentLength > 0 && totalRead != contentLength) {
                                lastError = "truncated download: got $totalRead of $contentLength bytes"
                                lastException = java.io.IOException(lastError)
                                Log.w(TAG, "EPG download: $lastError (attempt $attempt)")
                            } else {
                                downloadedBytes = tmpFile.length()
                                computedSha256 = digest?.digest()?.joinToString("") { "%02x".format(it) }
                                lastError = null
                            }
                        }
                    }

                    if (lastError == null) break
                    if (lastError.contains("HTTP 4")) break
                    if (attempt < 5) {
                        val backoff = (5000L * (1 shl (attempt - 1))).coerceAtMost(60000L)
                        delay(backoff)
                        continue
                    }
                } catch (e: java.net.UnknownHostException) {
                    lastError = "DNS lookup failed for ${e.message ?: "host"}"
                    lastException = e
                    Log.w(TAG, "EPG download DNS failure (attempt $attempt): $lastError", e)
                    if (attempt < 5) {
                        val backoff = (5000L * (1 shl (attempt - 1))).coerceAtMost(60000L)
                        delay(backoff)
                        continue
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    lastError = e.message ?: "unknown error"
                    lastException = e
                    Log.w(TAG, "EPG download error (attempt $attempt): $lastError", e)
                    if (attempt < 5 && !e.isDiskFull()) {
                        val backoff = (5000L * (1 shl (attempt - 1))).coerceAtMost(60000L)
                        delay(backoff)
                        continue
                    }
                }
            }

            if (lastError != null) {
                val display =
                    (
                        lastException?.let {
                            StoredError.of(
                                it,
                                context,
                            )
                        } ?: StoredError(ErrorKind.GENERIC, raw = lastError)
                    ).encode()
                sourceDao.markError(source.id, display)
                tmpFile.delete()
                return null
            }

            if (notModified) {
                Log.i(TAG, "EPG source $label unchanged (304 Not Modified) — skipping ingest")
                sourceDao.markUnchanged(source.id, System.currentTimeMillis())
                tmpFile.delete()
                return DownloadedSource(
                    source,
                    label,
                    tmpFile,
                    downloadedBytes = 0,
                    downloadDurationMs = System.currentTimeMillis() - downloadStartMs,
                    unchanged = true,
                )
            }

            if (!isGzip && canSkipIngest(source, computedSha256, indexHasRows)) {
                Log.i(TAG, "EPG source $label unchanged (content hash ${computedSha256?.take(12)} matches) — skipping ingest")
                sourceDao.markUnchanged(source.id, System.currentTimeMillis())
                tmpFile.delete()
                return DownloadedSource(
                    source,
                    label,
                    tmpFile,
                    downloadedBytes,
                    downloadDurationMs = System.currentTimeMillis() - downloadStartMs,
                    unchanged = true,
                    contentSha256 = computedSha256,
                    etag = responseEtag,
                    lastModifiedHeader = responseLastModified,
                    indexHasRows = indexHasRows,
                )
            }

            return DownloadedSource(
                source,
                label,
                tmpFile,
                downloadedBytes,
                System.currentTimeMillis() - downloadStartMs,
                contentSha256 = computedSha256,
                etag = responseEtag,
                lastModifiedHeader = responseLastModified,
                indexHasRows = indexHasRows,
            )
        } catch (e: CancellationException) {
            tmpFile.delete() // a cancelled download leaves no partial file behind
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading source: $label", e)
            sourceDao.markError(source.id, StoredError.of(e, context).encode())
            tmpFile.delete()
            return null
        }
    }

    /**
     * SHA-256 of a `.gz` file's decompressed content — a local read-and-discard pass, not a full
     * parse. Returns null (never skip) on any failure, including a corrupt/truncated download;
     * the real ingest right after this will hit and report the same problem properly.
     */
    private fun hashDecompressedGzip(file: File): String? =
        try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            GZIPInputStream(file.inputStream().buffered(STREAM_BUFFER_SIZE), STREAM_BUFFER_SIZE).use { stream ->
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // cancellation-ok: non-suspend
            Log.w(TAG, "Decompressed-content hash failed, proceeding with full ingest", e)
            null
        }

    /**
     * Ingests a downloaded source file into the index, then deletes the file.
     *
     * Off the staging path the source is marked ingested here, as soon as its rows are live. On
     * the staging path the rows only reach the live guide at the swap, so the [IngestRecord] is
     * returned instead and the caller writes it after the swap commits ([swapThenMarkIngested]).
     */
    @VisibleForTesting
    internal suspend fun ingestDownloadedSource(
        downloaded: DownloadedSource,
        sourceDao: EpgSourceDao,
        indexer: EpgIndexer,
        activeProgress: ConcurrentHashMap<Long, ActiveSourceProgress>,
        batchSize: Int = EpgIndexer.BATCH_SIZE_MOBILE,
        useStaging: Boolean = false,
        isPlaybackActive: () -> Boolean = { false },
        onProgressUpdate: () -> Unit,
    ): IngestOutcome {
        val source = downloaded.source
        val label = downloaded.label

        try {
            // A guide can come compressed at a URL without `.gz` (an Xtream panel's xmltv.php,
            // with no Content-Encoding for OkHttp to undo): the file's first bytes tell.
            val isGzip = source.url.endsWith(".gz", ignoreCase = true) || withContext(Dispatchers.IO) { isGzipFile(downloaded.tmpFile) }
            // A `.gz` gets its hash here, from the decompressed content (see downloadSource),
            // before a real parse.
            var contentSha256 = downloaded.contentSha256
            if (isGzip) {
                contentSha256 = withContext(Dispatchers.IO) { hashDecompressedGzip(downloaded.tmpFile) }
                if (canSkipIngest(source, contentSha256, downloaded.indexHasRows)) {
                    Log.i(TAG, "EPG source $label unchanged (decompressed hash ${contentSha256?.take(12)} matches) — skipping ingest")
                    sourceDao.markUnchanged(source.id, System.currentTimeMillis())
                    return IngestOutcome(
                        SourceStats(
                            sourceId = source.id,
                            label = label,
                            downloadBytes = downloaded.downloadedBytes,
                            channelsIngested = source.lastChannels,
                            programmesIngested = source.lastProgrammes,
                            unchanged = true,
                        ),
                    )
                }
            }

            // Direct ingest with not enough space for the source's rows: the ingest does not start,
            // the source keeps its guide and a storage-low error (LS-Q1 (a)).
            if (!useStaging) {
                StorageSpace
                    .shortfall(SpaceNeed.directIngest(downloaded.tmpFile.length(), isGzip), freeSpace)
                    ?.let { throw NotEnoughSpaceException(it) }
            }

            val ingestStartMs = System.currentTimeMillis()
            // Direct ingest: the mark below which this source's rows are the old guide's.
            val maxIdBefore = if (useStaging) null else indexer.maxProgrammeId()
            val fileSize = downloaded.tmpFile.length()
            val countingStream = CountingInputStream(downloaded.tmpFile.inputStream())
            val bufferedStream = BufferedInputStream(countingStream, STREAM_BUFFER_SIZE)
            val stream =
                if (isGzip) {
                    try {
                        GZIPInputStream(bufferedStream, STREAM_BUFFER_SIZE)
                    } catch (e: Exception) {
                        // cancellation-ok: non-suspend, rethrows
                        // GZIPInputStream's constructor reads the magic bytes and throws on a bad
                        // file before `stream.use { }` owns a stream: close bufferedStream here, or
                        // its file descriptor leaks.
                        bufferedStream.close()
                        throw e
                    }
                } else {
                    bufferedStream
                }

            val ingestionStats =
                stream.use {
                    indexer.ingestFromStream(
                        it,
                        sourceId = source.id,
                        timezoneOverrideHours = source.timezoneOffsetHours,
                        batchSize = batchSize,
                        useStaging = useStaging,
                        isPlaybackActive = isPlaybackActive,
                    ) { channels, programmes ->
                        val pct = if (fileSize > 0) ((countingStream.bytesRead * 100) / fileSize).toInt().coerceIn(0, 100) else -1
                        activeProgress[source.id] =
                            ActiveSourceProgress(
                                sourceId = source.id,
                                label = label,
                                phase = "Ingesting",
                                progressPercent = pct,
                                downloadedBytes = downloaded.downloadedBytes,
                                downloadTotalBytes = downloaded.downloadedBytes,
                                channels = channels,
                                programmes = programmes,
                            )
                        onProgressUpdate()
                    }
                }

            if (isFailedEmptyIngest(ingestionStats.channelsIngested, source.lastChannels)) {
                Log.w(TAG, "EPG source $label came back empty after ${source.lastChannels} channels — keeping its old guide")
                val display = StoredError(ErrorKind.GUIDE_CAME_BACK_EMPTY).encode()
                sourceDao.markError(source.id, display)
                return IngestOutcome(SourceStats(source.id, label, downloadBytes = downloaded.downloadedBytes, error = display))
            }
            if (maxIdBefore != null) {
                val removed = indexer.deleteProgrammesNotRewritten(source.id, maxIdBefore)
                Log.i(TAG, "EPG source $label: removed $removed programmes the new guide no longer lists")
            }
            val clipped = indexer.clipOverlaps(source.id, useStaging)
            Log.i(TAG, "EPG source $label: $clipped programmes ended at the next start on their channel")
            Log.i(TAG, "EPG source $label fully ingested, content hash ${contentSha256?.take(12)}")
            val record =
                IngestRecord(
                    sourceId = source.id,
                    timestamp = System.currentTimeMillis(),
                    channels = ingestionStats.channelsIngested,
                    programmes = ingestionStats.programmesIngested,
                    downloadBytes = downloaded.downloadedBytes,
                    ingestMethod = "DOWNLOADED",
                    ingestionDurationMs = System.currentTimeMillis() - ingestStartMs,
                    downloadDurationMs = downloaded.downloadDurationMs,
                    contentSha256 = contentSha256,
                    etag = downloaded.etag,
                    lastModifiedHeader = downloaded.lastModifiedHeader,
                    fileUtcOffsetMinutes = ingestionStats.fileUtcOffsetMinutes,
                    appliedUtcOffsetMinutes =
                        EpgOffsetShift.readMinutes(source.timezoneOffsetHours, ingestionStats.fileUtcOffsetMinutes),
                )
            if (!useStaging) {
                sourceDao.markIngested(
                    id = record.sourceId,
                    timestamp = record.timestamp,
                    channels = record.channels,
                    programmes = record.programmes,
                    downloadBytes = record.downloadBytes,
                    ingestMethod = record.ingestMethod,
                    ingestionDurationMs = record.ingestionDurationMs,
                    downloadDurationMs = record.downloadDurationMs,
                    contentSha256 = record.contentSha256,
                    etag = record.etag,
                    lastModifiedHeader = record.lastModifiedHeader,
                    fileUtcOffsetMinutes = record.fileUtcOffsetMinutes,
                    appliedUtcOffsetMinutes = record.appliedUtcOffsetMinutes,
                )
                // The offset may have changed while the import ran.
                EpgOffsetShift.reconcile(record.sourceId, sourceDao, indexer)
            }

            return IngestOutcome(
                SourceStats(
                    sourceId = source.id,
                    label = label,
                    downloadBytes = downloaded.downloadedBytes,
                    channelsIngested = ingestionStats.channelsIngested,
                    programmesIngested = ingestionStats.programmesIngested,
                ),
                pending = if (useStaging) record else null,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error ingesting source: $label", e)
            // A full disk on the staging path: this source's staging rows go now, not at the start
            // of the next run. The live guide stays as it was.
            if (useStaging && e.isDiskFull()) freeStagingAfterFullDisk(indexer, listOf(source.id))
            val display = StoredError.of(e, context).encode()
            sourceDao.markError(source.id, display)
            return IngestOutcome(SourceStats(source.id, label, downloadBytes = downloaded.downloadedBytes, error = display))
        } finally {
            downloaded.tmpFile.delete()
        }
    }

    /**
     * After a full disk on the staging path: deletes the staging rows of [sourceIds] (all of them
     * when null) and gives their space back to the device at once ([EpgIndexer.incrementalVacuum]).
     * The live guide stays as it was. Never throws: on a full disk the delete can fail too, and
     * the next run clears staging before it starts.
     */
    private suspend fun freeStagingAfterFullDisk(
        indexer: EpgIndexer,
        sourceIds: List<Long>?,
    ) {
        try {
            if (sourceIds == null) indexer.clearStaging() else indexer.clearStagingForSources(sourceIds)
            indexer.incrementalVacuum()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Clearing staging after a full disk failed", e)
        }
    }

    /**
     * Refreshes the enabled sources of [providerId] that are due ([EpgRefreshSchedule.isDue]). True
     * when a refresh started or one is already running.
     */
    suspend fun refreshOutdatedSources(providerId: Long): Boolean {
        val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
        val sources = sourceDao.getEnabledSourcesForProvider(providerId)
        if (sources.isEmpty()) return false

        if (_state.value is MultiSourceState.Processing) {
            return true // Treat as success/active
        }

        val now = System.currentTimeMillis()
        val unsetHours = EpgRefreshSchedule.legacyIntervalHours(appSettings)
        val staleSources = sources.filter { EpgRefreshSchedule.isDue(it, now, unsetHours) }

        return if (staleSources.isNotEmpty()) {
            val task =
                object : RefreshTask {
                    // Per provider: [RefreshQueue] would drop a second provider's run with the same id.
                    override val id = "epg_auto_refresh_$providerId"
                    override val priority = RefreshPriority.MEDIUM

                    override suspend fun execute() {
                        processAllSourcesInternal(staleSources)
                    }
                }
            RefreshQueue.submit(task)
            true
        } else {
            false
        }
    }

    /** The enabled guide sources that [providerId] reads, for [EpgSyncWorker]. */
    internal suspend fun getAllSources(providerId: Long): List<EpgSourceEntity> =
        SettingsDatabase.getInstance(context).epgSourceDao().getEnabledSourcesForProvider(providerId)

    /** The enabled sources of [providerId] due by their own interval ([EpgRefreshSchedule.isDue]), for [EpgSyncWorker]. */
    internal suspend fun getStaleSources(providerId: Long): List<EpgSourceEntity> {
        val sources = SettingsDatabase.getInstance(context).epgSourceDao().getEnabledSourcesForProvider(providerId)
        val now = System.currentTimeMillis()
        val unsetHours = EpgRefreshSchedule.legacyIntervalHours(appSettings)
        return sources.filter { EpgRefreshSchedule.isDue(it, now, unsetHours) }
    }

    /**
     * Sets one guide source's auto-refresh interval in hours ([EpgSourceEntity.REFRESH_OFF] = off).
     * The periodic work follows by itself (see [initialize]).
     */
    suspend fun setRefreshInterval(
        sourceId: Long,
        hours: Int,
    ) {
        require(EpgRefreshSchedule.isValidInterval(hours)) { "Not a refresh interval: $hours" }
        SettingsDatabase.getInstance(context).epgSourceDao().setRefreshInterval(sourceId, hours)
    }

    /**
     * One periodic `epg_sync` every [intervalHours] — the shortest among the guide sources
     * ([EpgRefreshSchedule.workIntervalHours]), at least an hour, above WorkManager's 15-minute
     * minimum — or none when it is null (every source off). `UPDATE` keeps the existing work's
     * timing; there is no time of day.
     */
    private fun scheduleAutoRefresh(intervalHours: Int?) {
        val workManager = WorkManager.getInstance(context)
        if (intervalHours == null) {
            workManager.cancelUniqueWork(EpgSyncWorker.WORK_NAME)
        } else {
            // On a device with little memory (a Chromecast HD), the automatic refresh waits until
            // the device is idle (the TV off or asleep), so it never competes with an app in use
            // (the pausable guide indexing plan, IX4). WorkManager refuses a backoff on an idle
            // job, so that one has none: it runs at the next idle time.
            val idleOnly = isLowMemoryDevice(context)
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(WorkNetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .setRequiresDeviceIdle(idleOnly)
                    .build()
            val request =
                PeriodicWorkRequestBuilder<EpgSyncWorker>(intervalHours.toLong(), TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .apply { if (!idleOnly) setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES) }
                    .build()
            workManager.enqueueUniquePeriodicWork(EpgSyncWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }

    data class CleanupResult(
        val filesDeleted: Int,
        val bytesFreed: Long,
    )

    fun getStrayFiles(): List<File> =
        try {
            context.cacheDir
                .listFiles { file ->
                    file.name.startsWith("xmltv_") && file.isFile
                }?.toList() ?: emptyList()
        } catch (e: Exception) {
            // cancellation-ok: non-suspend
            emptyList()
        }

    fun cleanupStrayFiles(): CleanupResult {
        try {
            val strayFiles = getStrayFiles()
            var bytesFreed = 0L
            var filesDeleted = 0
            strayFiles.forEach { file ->
                val size = file.length()
                if (file.delete()) {
                    bytesFreed += size
                    filesDeleted++
                }
            }
            return CleanupResult(filesDeleted, bytesFreed)
        } catch (e: Exception) {
            // cancellation-ok: non-suspend
            Log.w(TAG, "Cleanup failed", e)
            return CleanupResult(0, 0)
        }
    }

    private class CountingInputStream(
        private val wrapped: java.io.InputStream,
    ) : java.io.InputStream() {
        @Volatile var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            val b = wrapped.read()
            if (b != -1) bytesRead++
            return b
        }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            val n = wrapped.read(b, off, len)
            if (n > 0) bytesRead += n
            return n
        }

        override fun close() = wrapped.close()

        override fun available() = wrapped.available()
    }

    private suspend fun updateLastPipelineStats(completed: MultiSourceState.Completed) {
        try {
            val stats =
                EpgPipelineStatsEntity(
                    updatedAtMs = completed.updatedAtMs,
                    durationMs = completed.durationMs,
                    sourcesProcessed = completed.sourcesProcessed,
                    errors = completed.errors,
                    totalChannels = completed.totalChannels,
                    totalProgrammes = completed.totalProgrammes,
                )
            SettingsDatabase.getInstance(context).epgPipelineStatsDao().insertStats(stats)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save pipeline stats: ${e.message}")
        }
    }
}

/**
 * A download that parsed to no channels for a guide source that had some ([previousChannels], its
 * `lastChannels`): a throttled or cut-off reply, not a server without a guide. It counts as that
 * source's error, so the swap leaves its old guide in place and the next refresh tries again.
 */
internal fun isFailedEmptyIngest(
    channelsIngested: Int,
    previousChannels: Int,
): Boolean = channelsIngested == 0 && previousChannels > 0
