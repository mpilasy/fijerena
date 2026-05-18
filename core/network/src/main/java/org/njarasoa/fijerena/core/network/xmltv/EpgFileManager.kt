package org.njarasoa.fijerena.core.network.xmltv
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.OptIn
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import org.njarasoa.fijerena.core.network.utils.await
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.EpgPipelineStatsEntity
import org.njarasoa.fijerena.core.network.provider.EpgSourceDao
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.queue.RefreshPriority
import org.njarasoa.fijerena.core.network.queue.RefreshQueue
import org.njarasoa.fijerena.core.network.queue.RefreshTask
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.player.config.NetworkType
import org.njarasoa.fijerena.core.player.device.DeviceDetector
import org.njarasoa.fijerena.core.player.device.DeviceType
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
 * Singleton managing multi-source EPG download-ingest pipeline.
 *
 * Dual-mode architecture based on device type:
 * - **TV/fixed devices:** Stream directly from network to database (zero disk I/O)
 * - **Mobile:** Download to cache first, then ingest from file
 *
 * Both modes use Room withTransaction in EpgIndexer for atomic ingestion.
 * Uses Ktor HttpClient(OkHttp) for HTTP requests.
 */
class EpgFileManager private constructor(
    private val context: Context,
) {
    companion object {
        private const val TAG = "EpgFileManager"
        private const val PREFS_NAME = "epg_file_manager"
        private const val KEY_MIGRATED_TO_SOURCES = "migrated_to_sources_v1"
        private const val STREAM_BUFFER_SIZE = 131072 // 128KB
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 5000L
        private const val SCHEDULED_REFRESH_AGE_MS = 3_600_000L // scheduled runs refresh if data is older than 1 hour

        @Volatile
        private var instance: EpgFileManager? = null

        fun getInstance(context: Context): EpgFileManager =
            instance ?: synchronized(this) {
                instance ?: EpgFileManager(context.applicationContext).also { instance = it }
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
                "Source"
            }
    }

    /**
     * Per-source stats collected during processing.
     */
    data class SourceStats(
        val sourceId: Long,
        val label: String,
        val downloadBytes: Long = 0,
        val channelsIngested: Int = 0,
        val programmesIngested: Int = 0,
        val durationMs: Long = 0,
        val error: String? = null,
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
         * Post-ingestion phase: index rebuild, FTS rebuild, or vacuum.
         * Emitted after all sources are ingested so the UI can show a working
         * indicator instead of appearing stuck at 100%.
         */
        data class Finalizing(
            val phase: String,
            val totalChannels: Int = 0,
            val totalProgrammes: Int = 0,
            val totalDownloadBytes: Long = 0,
            val durationMs: Long = 0,
        ) : MultiSourceState
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appSettings by lazy { AppSettings(context) }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val staleThresholdMs: Long
        get() {
            val interval = appSettings.epgRefreshInterval
            return if (interval <= 0) {
                24L * 3600 * 1000 // 24h if disabled or "Never"
            } else {
                interval.toLong() * 3600 * 1000
            }
        }

    private var processJob: Job? = null

    private val _state = MutableStateFlow<MultiSourceState>(MultiSourceState.Idle)
    val state: StateFlow<MultiSourceState> = _state.asStateFlow()

    private val okHttpClient =
        org.njarasoa.fijerena.core.player.network.NetworkModule.okHttpClient
            .newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.MINUTES)
            .build()

    private fun isFixedDevice(): Boolean {
        val type = DeviceDetector.detect().deviceType
        return type != DeviceType.GENERIC_MOBILE
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

            // Schedule WorkManager periodic sync (Doze-aware, works on both mobile and TV)
            updateAutoRefreshSchedule()
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
                if (sourceDao.getSourceCount() == 0) {
                    val label = extractLabel(oldUrl)
                    sourceDao.insertSource(
                        EpgSourceEntity(
                            url = oldUrl,
                            label = label,
                            timezoneOffsetHours = oldTz,
                        ),
                    )
                }
            }

            val oldFile = File(context.cacheDir, "xmltv_global.xml")
            if (oldFile.exists()) {
                oldFile.delete()
            }

            prefs.edit { putBoolean(KEY_MIGRATED_TO_SOURCES, true) }
        } catch (e: Exception) {
            Log.w(TAG, "Migration from AppSettings failed", e)
        }
    }

    /**
     * Start processing all enabled sources. Shows confirmation dialog on cellular.
     * The job runs on the file manager's own scope so it survives ViewModel
     * clearing and auto-refresh rescheduling.
     * Only one processing job runs at a time — a new request cancels the old one.
     *
     * @param onComplete Callback invoked after processing completes (on WiFi or after confirmation).
     * @param onCellularConfirm Callback invoked if user must confirm on cellular.
     *                          Return false to cancel processing.
     */
    private fun launchGenericTask(
        taskId: String,
        onComplete: (suspend () -> Unit)? = null,
        onCellularConfirm: (suspend () -> Boolean)? = null,
        dbQueryAndProcess: suspend () -> Unit,
    ) {
        processJob?.cancel()
        if (_state.value !is MultiSourceState.Processing) {
            _state.value = MultiSourceState.Pending
        }

        processJob =
            scope.launch {
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
                            val retryDelaysMs = listOf(
                                1L * 60 * 1000,
                                2L * 60 * 1000,
                                4L * 60 * 1000,
                                8L * 60 * 1000,
                                16L * 60 * 1000
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
                                    return // Success
                                } catch (e: Exception) {
                                    lastException = e
                                    currentAttempt++
                                    
                                    if (currentAttempt <= maxAttempts) {
                                        val delayMs = retryDelaysMs[currentAttempt - 1]
                                        val nextRetryAt = System.currentTimeMillis() + delayMs
                                        _state.value = MultiSourceState.Retrying(
                                            attempt = currentAttempt,
                                            maxAttempts = maxAttempts,
                                            nextRetryAtMs = nextRetryAt,
                                            reason = e.message ?: "Unknown error"
                                        )
                                        Log.w(TAG, "Task $taskId failed (attempt $currentAttempt/$maxAttempts). Retrying in ${delayMs/60000} min. Error: ${e.message}")
                                        delay(delayMs)
                                    }
                                }
                            }

                            // All attempts failed
                            Log.e(TAG, "Task $taskId failed after $maxAttempts retries: ${lastException?.message}", lastException)
                            _state.value = MultiSourceState.Error(lastException?.message ?: "Task failed after $maxAttempts retries")
                            onComplete?.invoke()
                        }
                    }
                RefreshQueue.submit(task)
            }
    }

    fun launchRefreshStale(
        onComplete: (suspend () -> Unit)? = null,
        onCellularConfirm: (suspend () -> Boolean)? = null,
    ) {
        launchGenericTask("epg_refresh_stale", onComplete, onCellularConfirm) {
            val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
            val thresholdMs = System.currentTimeMillis() - staleThresholdMs
            val staleSources = sourceDao.getStaleSources(thresholdMs)
            if (staleSources.isNotEmpty()) {
                processAllSourcesInternal(staleSources)
            } else {
                if (_state.value is MultiSourceState.Pending) {
                    _state.value = MultiSourceState.Idle
                }
            }
        }
    }

    fun launchRefreshFailed(
        onComplete: (suspend () -> Unit)? = null,
        onCellularConfirm: (suspend () -> Boolean)? = null,
    ) {
        launchGenericTask("epg_refresh_failed", onComplete, onCellularConfirm) {
            val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
            val failedSources = sourceDao.getFailedSources()
            if (failedSources.isNotEmpty()) {
                processAllSourcesInternal(failedSources)
            } else {
                if (_state.value is MultiSourceState.Pending) {
                    _state.value = MultiSourceState.Idle
                }
            }
        }
    }

    fun launchRefreshSelected(
        selectedIds: Set<Long>,
        onComplete: (suspend () -> Unit)? = null,
        onCellularConfirm: (suspend () -> Boolean)? = null,
    ) {
        launchGenericTask("epg_refresh_selected", onComplete, onCellularConfirm) {
            val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
            val selectedSources = sourceDao.getAllSourcesOnce().filter { it.id in selectedIds }
            if (selectedSources.isNotEmpty()) {
                processAllSourcesInternal(selectedSources)
            } else {
                if (_state.value is MultiSourceState.Pending) {
                    _state.value = MultiSourceState.Idle
                }
            }
        }
    }

    fun launchProcessSingleSource(
        sourceId: Long,
        onComplete: (suspend () -> Unit)? = null,
        onCellularConfirm: (suspend () -> Boolean)? = null,
    ) {
        launchGenericTask("epg_refresh_source_$sourceId", onComplete, onCellularConfirm) {
            processSingleSourceInternal(sourceId)
        }
    }

    /**
     * Process all enabled sources: ingest using 2 parallel workers.
     * Append-only: database stays searchable throughout.
     *
     * Must be called via [launchProcessAllSources] to ensure proper job tracking.
     * The [processAllSources] suspend overload is kept for [autoRefreshIfStale]
     * which already runs inside a tracked [processJob].
     */
    suspend fun processAllSources(sources: List<EpgSourceEntity>) {
        processAllSourcesInternal(sources)
    }

    /**
     * Holds a downloaded file ready for ingestion.
     */
    private data class DownloadedSource(
        val source: EpgSourceEntity,
        val label: String,
        val tmpFile: File,
        val downloadedBytes: Long,
        val downloadDurationMs: Long = 0,
    )

    private suspend fun processAllSourcesInternal(sources: List<EpgSourceEntity>) {
        if (sources.isEmpty()) {
            _state.value = MultiSourceState.Error("No sources to process")
            return
        }

        val startTime = System.currentTimeMillis()
        val fixedDevice = isFixedDevice()
        val batchSize = if (fixedDevice) EpgIndexer.BATCH_SIZE_TV else EpgIndexer.BATCH_SIZE_MOBILE
        try {
            val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
            val indexer = EpgIndexer.getInstance(context)

            val maxDownloadConcurrency = if (fixedDevice) 2 else 3
            val downloadSemaphore = Semaphore(maxDownloadConcurrency)
            val maxIngestionConcurrency = 2
            val completedStats = CopyOnWriteArrayList<SourceStats>()
            val activeLabels = CopyOnWriteArrayList<String>()
            val activeProgress = ConcurrentHashMap<Long, ActiveSourceProgress>()

            // Channel: downloads produce, ingestion consumes
            val ingestionQueue = Channel<DownloadedSource>(Channel.UNLIMITED)
            val sourceStartTimeMap = ConcurrentHashMap<Long, Long>()

            indexer.setIndexing()

            _state.value =
                MultiSourceState.Processing(
                    completedCount = 0,
                    totalSources = sources.size,
                    activeSourceLabels = emptyList(),
                )

            val allStats =
                coroutineScope {
                    // Start bulk setup in parallel with downloads — downloads write to cache files
                    // and never touch the DB, so there is no ordering constraint here.
                    // Consumers await this before touching the DB.
                    val bulkReady = async(Dispatchers.IO) { indexer.beginBulkIngestion() }

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

                                    val stats =
                                        ingestDownloadedSource(
                                            downloaded,
                                            sourceDao,
                                            indexer,
                                            activeProgress,
                                            batchSize = batchSize,
                                            isPlaybackActive = ::isPlaybackActive,
                                        ) {
                                            updateAggregateProgress(completedStats, activeLabels, activeProgress, sources.size)
                                        }

                                    val sourceId = downloaded.source.id
                                    val sourceDuration = sourceStartTimeMap[sourceId]?.let { System.currentTimeMillis() - it } ?: 0
                                    val finalStats = stats.copy(durationMs = sourceDuration)

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

                                    if (result != null) {
                                        // Success — send to ingestion pipeline
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
                                    } else {
                                        // Download failed — record and clean up
                                        val sourceDuration = sourceStartTimeMap[source.id]?.let { System.currentTimeMillis() - it } ?: 0
                                        activeLabels.remove(label)
                                        activeProgress.remove(source.id)
                                        completedStats.add(
                                            SourceStats(source.id, label, durationMs = sourceDuration, error = "Download failed"),
                                        )
                                        updateAggregateProgress(completedStats, activeLabels, activeProgress, sources.size)
                                    }
                                }
                            }
                        }

                    // Wait for all downloads, then close the ingestion queue
                    downloadJobs.forEach { it.await() }
                    ingestionQueue.close()

                    // Wait for ingestion to drain
                    ingestionJobs.forEach { it.join() }

                    completedStats.toList()
                }

            val totalChannels = allStats.sumOf { it.channelsIngested }
            val totalProgrammes = allStats.sumOf { it.programmesIngested }
            val totalBytes = allStats.sumOf { it.downloadBytes }

            // Finalizing: rebuild B-tree query indexes (fast compared to FTS rebuild).
            // Show this phase so the UI doesn't appear stuck after ingestion completes.
            _state.value =
                MultiSourceState.Finalizing(
                    phase = "Rebuilding indexes\u2026",
                    totalChannels = totalChannels,
                    totalProgrammes = totalProgrammes,
                    totalDownloadBytes = totalBytes,
                )
            indexer.endBulkIngestion()

            val anyIngested = allStats.any { it.error == null && (it.channelsIngested > 0 || it.programmesIngested > 0) }

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

            // FTS rebuild runs in the caller's coroutine so the WorkManager wake lock
            // covers the full operation. Killing the process mid-rebuild leaves fts_stale=true
            // persisted to prefs, which on Shield causes permanent LIKE fallback via Doze.
            if (anyIngested) {
                try {
                    indexer.rebuildFtsAndUpdateState()
                    indexer.incrementalVacuum()
                } catch (e: Exception) {
                    Log.e(TAG, "FTS rebuild failed: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "processAllSources failed: ${e.message}", e)
            EpgIndexer.getInstance(context).endBulkIngestion()
            _state.value = MultiSourceState.Error(e.message ?: "Processing failed")
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

    private suspend fun processSingleSourceInternal(sourceId: Long) {
        val startTime = System.currentTimeMillis()
        val batchSize = if (isFixedDevice()) EpgIndexer.BATCH_SIZE_TV else EpgIndexer.BATCH_SIZE_MOBILE
        try {
            val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
            val source =
                sourceDao.getSourceById(sourceId) ?: run {
                    _state.value = MultiSourceState.Error("Source not found")
                    return
                }
            val indexer = EpgIndexer.getInstance(context)
            val label = source.label.ifBlank { extractLabel(source.url) }

            indexer.setIndexing()
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

            // Start bulk setup in parallel with the download — same rationale as processAllSourcesInternal.
            val bulkReady = scope.async(Dispatchers.IO) { indexer.beginBulkIngestion() }

            // Download phase
            activeProgress[source.id] = ActiveSourceProgress(source.id, label, "Downloading")
            updateSingleProgress()

            val downloaded = downloadSource(source, label, sourceDao, activeProgress) { updateSingleProgress() }

            bulkReady.await() // Ensure indexes are dropped before ingesting

            val stats =
                if (downloaded != null) {
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

                    // Ingest phase
                    activeProgress[source.id] =
                        ActiveSourceProgress(
                            sourceId = source.id,
                            label = label,
                            phase = "Ingesting",
                            downloadedBytes = downloaded.downloadedBytes,
                            downloadTotalBytes = downloaded.downloadedBytes,
                        )
                    updateSingleProgress()

                    ingestDownloadedSource(
                        downloaded,
                        sourceDao,
                        indexer,
                        activeProgress,
                        batchSize = batchSize,
                        isPlaybackActive = ::isPlaybackActive,
                    ) { updateSingleProgress() }
                } else {
                    // Download failed — error already logged
                    SourceStats(source.id, label, error = "Download failed")
                }

            // Finalizing: rebuild B-tree query indexes.
            _state.value =
                MultiSourceState.Finalizing(
                    phase = "Rebuilding indexes\u2026",
                    totalChannels = stats.channelsIngested,
                    totalProgrammes = stats.programmesIngested,
                    totalDownloadBytes = stats.downloadBytes,
                )
            indexer.endBulkIngestion()

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

            // Inline — same reasoning as processAllSourcesInternal.
            if (stats.error == null && (stats.channelsIngested > 0 || stats.programmesIngested > 0)) {
                try {
                    indexer.rebuildFtsAndUpdateState()
                    indexer.incrementalVacuum()
                } catch (e: Exception) {
                    Log.e(TAG, "FTS rebuild failed: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "processSingleSource failed: ${e.message}", e)
            EpgIndexer.getInstance(context).endBulkIngestion()
            _state.value = MultiSourceState.Error(e.message ?: "Processing failed")
        }
    }

    /**
     * Cancel all running and queued EPG refresh processing.
     * Cancels the coroutine job which causes all downloads and ingestion to stop.
     */
    fun cancelProcessing() {
        processJob?.cancel()
        processJob = null
        scope.launch {
            RefreshQueue.cancelAll()
        }
        _state.value = MultiSourceState.Idle
    }

    /**
     * Cancel all processing, then clear all EPG data.
     * Runs entirely on this manager's scope (survives ViewModel destruction).
     */
    fun launchClearAllData(onComplete: (suspend () -> Unit)? = null) {
        processJob?.cancel()
        processJob = null
        _state.value = MultiSourceState.Clearing
        scope.launch {
            try {
                RefreshQueue.cancelAll()
                // Small delay to let cancelled tasks finish their catch blocks
                delay(100)
                EpgIndexer.getInstance(context).clearAll()
            } catch (e: Exception) {
                Log.e(TAG, "Clear all data failed: ${e.message}", e)
            } finally {
                _state.value = MultiSourceState.Idle
                onComplete?.invoke()
            }
        }
    }

    /**
     * Download a source to a cache file with progress tracking.
     * Returns [DownloadedSource] on success, null on failure (error recorded in sourceDao).
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
        val downloadStartMs = System.currentTimeMillis()

        try {
            for (attempt in 1..5) {
                try {
                    val request = Request.Builder().url(source.url).build()
                    withContext(Dispatchers.IO) {
                        okHttpClient.newCall(request).await().use { response ->
                            if (!response.isSuccessful) {
                                lastError = "server returned HTTP ${response.code}"
                                Log.w(TAG, "EPG download: $lastError (attempt $attempt)")
                                return@use
                            }

                            val body = response.body
                            val contentLength = body.contentLength()

                            tmpFile.outputStream().buffered(STREAM_BUFFER_SIZE).use { output ->
                                val input = body.byteStream()
                                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                                var totalRead = 0L
                                var lastReportedBytes = 0L
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    totalRead += read
                                    // Throttle UI updates to every 512KB
                                    if (totalRead - lastReportedBytes >= 524288) {
                                        lastReportedBytes = totalRead
                                        val pct = if (contentLength > 0) ((totalRead * 100) / contentLength).toInt().coerceIn(0, 100) else -1
                                        activeProgress[source.id] = ActiveSourceProgress(
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
                            downloadedBytes = tmpFile.length()
                            lastError = null
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
                    Log.w(TAG, "EPG download DNS failure (attempt $attempt): $lastError", e)
                    if (attempt < 5) {
                        val backoff = (5000L * (1 shl (attempt - 1))).coerceAtMost(60000L)
                        delay(backoff)
                        continue
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: "unknown error"
                    Log.w(TAG, "EPG download error (attempt $attempt): $lastError", e)
                    if (attempt < 5) {
                        val backoff = (5000L * (1 shl (attempt - 1))).coerceAtMost(60000L)
                        delay(backoff)
                        continue
                    }
                }
            }

            if (lastError != null) {
                sourceDao.markError(source.id, lastError)
                tmpFile.delete()
                return null
            }

            return DownloadedSource(source, label, tmpFile, downloadedBytes, System.currentTimeMillis() - downloadStartMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading source: $label", e)
            sourceDao.markError(source.id, e.message ?: "Unknown error")
            tmpFile.delete()
            return null
        }
    }

    /**
     * Ingest a previously downloaded source file into the index.
     * Deletes the temp file when done.
     */
    private suspend fun ingestDownloadedSource(
        downloaded: DownloadedSource,
        sourceDao: EpgSourceDao,
        indexer: EpgIndexer,
        activeProgress: ConcurrentHashMap<Long, ActiveSourceProgress>,
        batchSize: Int = EpgIndexer.BATCH_SIZE_MOBILE,
        isPlaybackActive: () -> Boolean = { false },
        onProgressUpdate: () -> Unit,
    ): SourceStats {
        val source = downloaded.source
        val label = downloaded.label
        val isGzip = source.url.endsWith(".gz", ignoreCase = true)

        try {
            val ingestStartMs = System.currentTimeMillis()
            val fileSize = downloaded.tmpFile.length()
            val countingStream = CountingInputStream(downloaded.tmpFile.inputStream())
            val bufferedStream = BufferedInputStream(countingStream, STREAM_BUFFER_SIZE)
            val stream = if (isGzip) GZIPInputStream(bufferedStream, STREAM_BUFFER_SIZE) else bufferedStream

            val ingestionStats =
                stream.use {
                    indexer.ingestFromStream(
                        it,
                        sourceId = source.id,
                        timezoneOverrideHours = source.timezoneOffsetHours,
                        batchSize = batchSize,
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

            sourceDao.markIngested(
                id = source.id,
                timestamp = System.currentTimeMillis(),
                channels = ingestionStats.channelsIngested,
                programmes = ingestionStats.programmesIngested,
                downloadBytes = downloaded.downloadedBytes,
                ingestMethod = "DOWNLOADED",
                ingestionDurationMs = System.currentTimeMillis() - ingestStartMs,
                downloadDurationMs = downloaded.downloadDurationMs,
            )

            return SourceStats(
                sourceId = source.id,
                label = label,
                downloadBytes = downloaded.downloadedBytes,
                channelsIngested = ingestionStats.channelsIngested,
                programmesIngested = ingestionStats.programmesIngested,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error ingesting source: $label", e)
            sourceDao.markError(source.id, e.message ?: "Unknown error")
            return SourceStats(source.id, label, downloadBytes = downloaded.downloadedBytes, error = e.message ?: "Unknown error")
        } finally {
            downloaded.tmpFile.delete()
        }
    }

    /**
     * Refresh all enabled sources that are considered stale (last ingested > interval).
     * Returns true if refresh was started (stale sources found), false otherwise.
     */
    suspend fun refreshOutdatedSources(): Boolean {
        val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
        val sources = sourceDao.getEnabledSources()
        if (sources.isEmpty()) return false

        if (_state.value is MultiSourceState.Processing) {
            return true // Treat as success/active
        }

        val now = System.currentTimeMillis()
        val staleSources =
            sources.filter { source ->
                source.lastIngestedAtMs == 0L || (now - source.lastIngestedAtMs) > SCHEDULED_REFRESH_AGE_MS
            }

        return if (staleSources.isNotEmpty()) {
            val task =
                object : RefreshTask {
                    override val id = "epg_auto_refresh"
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

    /**
     * Returns all enabled sources whose data is considered stale.
     * Used by [EpgSyncWorker] to query which sources to process before calling [processAllSources].
     */
    internal suspend fun getAllSources(): List<EpgSourceEntity> {
        return SettingsDatabase.getInstance(context).epgSourceDao().getEnabledSources()
    }

    internal suspend fun getStaleSources(): List<EpgSourceEntity> {
        val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
        val sources = sourceDao.getEnabledSources()
        if (sources.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        return sources.filter { source ->
            source.lastIngestedAtMs == 0L || (now - source.lastIngestedAtMs) > SCHEDULED_REFRESH_AGE_MS
        }
    }

    private fun calculateDelayUntil(time: String): Long {
        try {
            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance()
            val parts = time.split(":")
            if (parts.size != 2) return 0
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            target.set(java.util.Calendar.HOUR_OF_DAY, hour)
            target.set(java.util.Calendar.MINUTE, minute)
            target.set(java.util.Calendar.SECOND, 0)
            target.set(java.util.Calendar.MILLISECOND, 0)

            if (target.before(now)) {
                target.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to calculate delay for $time", e)
            return 0
        }
    }

    fun updateAutoRefreshSchedule(forceReschedule: Boolean = false) {
        scope.launch {
            val intervalHours = appSettings.epgRefreshInterval
            if (intervalHours == -1) {
                WorkManager.getInstance(context).cancelUniqueWork("epg_sync")
                return@launch
            }

            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(WorkNetworkType.CONNECTED)
                    .build()
            val policy = if (forceReschedule) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.UPDATE
            val request =
                PeriodicWorkRequestBuilder<EpgSyncWorker>(intervalHours.toLong(), TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                    .apply { if (forceReschedule) setInitialDelay(calculateDelayUntil(appSettings.epgRefreshTime), TimeUnit.MILLISECONDS) }
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "epg_sync",
                policy,
                request,
            )
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
            Log.w(TAG, "Cleanup failed", e)
            return CleanupResult(0, 0)
        }
    }

    /**
     * InputStream wrapper that tracks total bytes read.
     */
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save pipeline stats: ${e.message}")
        }
    }
}
