package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.Request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.CopyOnWriteArrayList
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.queue.RefreshPriority
import org.njarasoa.fijerena.core.network.queue.RefreshQueue
import org.njarasoa.fijerena.core.network.queue.RefreshTask
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSourceEntity
import org.njarasoa.fijerena.core.player.config.NetworkType
import org.njarasoa.fijerena.core.player.device.DeviceDetector
import org.njarasoa.fijerena.core.player.device.DeviceType
import org.njarasoa.fijerena.core.player.network.NetworkMonitor
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType as WorkNetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.io.BufferedInputStream
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

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
class EpgFileManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "EpgFileManager"
        private const val PREFS_NAME = "epg_file_manager"
        private const val KEY_MIGRATED_TO_SOURCES = "migrated_to_sources_v1"
        private const val STREAM_BUFFER_SIZE = 131072 // 128KB
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 5000L
        private const val STALE_THRESHOLD_MS = 24L * 3600 * 1000
        private const val AUTO_REFRESH_CHECK_INTERVAL_MS = 4L * 3600 * 1000 // Check every 4 hours

        @Volatile
        private var instance: EpgFileManager? = null

        fun getInstance(context: Context): EpgFileManager {
            return instance ?: synchronized(this) {
                instance ?: EpgFileManager(context.applicationContext).also { instance = it }
            }
        }

        fun extractLabel(url: String): String {
            return try {
                val path = URL(url).path.trimEnd('/')
                val filename = path.substringAfterLast('/')
                    .removeSuffix(".gz")
                    .removeSuffix(".xml")
                    .removeSuffix(".xmltv")
                if (filename.isNotBlank()) {
                    filename.take(30)
                } else {
                    URL(url).host
                        .removePrefix("www.")
                        .take(30)
                }
            } catch (e: Exception) {
                "Source"
            }
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
        val error: String? = null
    )

    sealed interface MultiSourceState {
        data object Idle : MultiSourceState
        data class Processing(
            val completedCount: Int,
            val totalSources: Int,
            val activeSourceLabels: List<String>,
            val totalChannels: Int = 0,
            val totalProgrammes: Int = 0,
            val totalDownloadedBytes: Long = 0,
            val completedSourceStats: List<SourceStats> = emptyList()
        ) : MultiSourceState
        data class Completed(
            val sourcesProcessed: Int,
            val errors: Int,
            val sourceStats: List<SourceStats> = emptyList(),
            val totalChannels: Int = 0,
            val totalProgrammes: Int = 0,
            val totalDownloadBytes: Long = 0
        ) : MultiSourceState
        data class Error(val reason: String) : MultiSourceState
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var processJob: Job? = null
    private var autoRefreshJob: Job? = null

    private val _state = MutableStateFlow<MultiSourceState>(MultiSourceState.Idle)
    val state: StateFlow<MultiSourceState> = _state.asStateFlow()

    private val okHttpClient = org.njarasoa.fijerena.core.player.network.NetworkModule.okHttpClient.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.MINUTES)
        .build()

    private fun isFixedDevice(): Boolean {
        val type = DeviceDetector.detect().deviceType
        return type != DeviceType.GENERIC_MOBILE
    }

    fun initialize() {
        scope.launch {
            migrateFromAppSettings()
            val indexer = EpgIndexer.getInstance(context)
            indexer.initialize()
            cleanupStrayFiles()
            scheduleAutoRefresh()

            // Schedule WorkManager periodic sync on mobile only
            if (!isFixedDevice()) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(WorkNetworkType.CONNECTED)
                    .build()
                val request = PeriodicWorkRequestBuilder<EpgSyncWorker>(24, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "epg_sync",
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }
        }
    }

    private suspend fun migrateFromAppSettings() {
        if (prefs.getBoolean(KEY_MIGRATED_TO_SOURCES, false)) return

        try {
            val appSettings = AppSettings(context)
            val oldUrl = appSettings.epgUrl
            val oldTz = appSettings.epgTimezoneOffsetHours

            if (oldUrl.isNotBlank()) {
                val db = EpgIndexDatabase.getInstance(context)
                val sourceDao = db.epgSourceDao()
                if (sourceDao.getSourceCount() == 0) {
                    val label = extractLabel(oldUrl)
                    sourceDao.insertSource(
                        EpgSourceEntity(
                            url = oldUrl,
                            label = label,
                            timezoneOffsetHours = oldTz
                        )
                    )
                    Log.d(TAG, "Migrated old EPG URL to source entity: $oldUrl (tz=$oldTz)")
                }
            }

            val oldFile = File(context.cacheDir, "xmltv_global.xml")
            if (oldFile.exists()) {
                oldFile.delete()
                Log.d(TAG, "Deleted legacy xmltv_global.xml")
            }

            prefs.edit().putBoolean(KEY_MIGRATED_TO_SOURCES, true).apply()
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
    fun launchProcessAllSources(
        onComplete: (suspend () -> Unit)? = null,
        onCellularConfirm: (suspend () -> Boolean)? = null
    ) {
        processJob?.cancel()

        processJob = scope.launch {
            val networkType = NetworkMonitor.currentNetworkType
            val shouldProceed = if (networkType == NetworkType.CELLULAR && onCellularConfirm != null) {
                onCellularConfirm()
            } else {
                true
            }

            if (!shouldProceed) {
                return@launch
            }

            val db = EpgIndexDatabase.getInstance(context)
            val sources = db.epgSourceDao().getEnabledSources()

            val task = object : RefreshTask {
                override val id = "epg_refresh_all"
                override val priority = RefreshPriority.MEDIUM
                override suspend fun execute() {
                    processAllSourcesInternal(sources)
                    onComplete?.invoke()
                }
            }
            RefreshQueue.submit(task)
        }
    }

    /**
     * Start processing a pre-filtered list of sources. Shows confirmation dialog on cellular.
     * Same lifecycle guarantees as [launchProcessAllSources].
     */
    fun launchProcessSources(
        sources: List<EpgSourceEntity>,
        taskId: String,
        onComplete: (suspend () -> Unit)? = null,
        onCellularConfirm: (suspend () -> Boolean)? = null
    ) {
        processJob?.cancel()

        processJob = scope.launch {
            val networkType = NetworkMonitor.currentNetworkType
            val shouldProceed = if (networkType == NetworkType.CELLULAR && onCellularConfirm != null) {
                onCellularConfirm()
            } else {
                true
            }

            if (!shouldProceed) {
                return@launch
            }

            val task = object : RefreshTask {
                override val id = taskId
                override val priority = RefreshPriority.MEDIUM
                override suspend fun execute() {
                    processAllSourcesInternal(sources)
                    onComplete?.invoke()
                }
            }
            RefreshQueue.submit(task)
        }
    }

    /**
     * Start processing a single source. Shows confirmation dialog on cellular.
     * Same lifecycle guarantees as [launchProcessAllSources].
     *
     * @param sourceId The source to refresh
     * @param onComplete Callback invoked after processing completes (on WiFi or after confirmation).
     * @param onCellularConfirm Callback invoked if user must confirm on cellular.
     *                          Return false to cancel processing.
     */
    fun launchProcessSingleSource(
        sourceId: Long,
        onComplete: (suspend () -> Unit)? = null,
        onCellularConfirm: (suspend () -> Boolean)? = null
    ) {
        processJob?.cancel()

        processJob = scope.launch {
            val networkType = NetworkMonitor.currentNetworkType
            val shouldProceed = if (networkType == NetworkType.CELLULAR && onCellularConfirm != null) {
                onCellularConfirm()
            } else {
                true
            }

            if (!shouldProceed) {
                return@launch
            }

            val task = object : RefreshTask {
                override val id = "epg_refresh_source_$sourceId"
                override val priority = RefreshPriority.MEDIUM
                override suspend fun execute() {
                    processSingleSourceInternal(sourceId)
                    onComplete?.invoke()
                }
            }
            RefreshQueue.submit(task)
        }
    }

    /**
     * Process all enabled sources: ingest each sequentially.
     * Append-only: database stays searchable throughout.
     *
     * Must be called via [launchProcessAllSources] to ensure proper job tracking.
     * The [processAllSources] suspend overload is kept for [autoRefreshIfStale]
     * which already runs inside a tracked [processJob].
     */
    suspend fun processAllSources(sources: List<EpgSourceEntity>) {
        processAllSourcesInternal(sources)
    }

    private suspend fun processAllSourcesInternal(sources: List<EpgSourceEntity>) {
        if (sources.isEmpty()) {
            _state.value = MultiSourceState.Error("No sources to process")
            return
        }

        try {
            val db = EpgIndexDatabase.getInstance(context)
            val sourceDao = db.epgSourceDao()
            val indexer = EpgIndexer.getInstance(context)

            val maxConcurrency = if (isFixedDevice()) 2 else 3
            val semaphore = Semaphore(maxConcurrency)
            val completedStats = CopyOnWriteArrayList<SourceStats>()
            val activeLabels = CopyOnWriteArrayList<String>()

            indexer.setIndexing()

            _state.value = MultiSourceState.Processing(
                completedCount = 0,
                totalSources = sources.size,
                activeSourceLabels = emptyList()
            )

            val allStats = coroutineScope {
                sources.map { source ->
                    async {
                        val label = source.label.ifBlank { extractLabel(source.url) }
                        semaphore.withPermit {
                            activeLabels.add(label)
                            updateAggregateProgress(completedStats, activeLabels, sources.size)
                            val stats = processSourceParallel(source, label, sourceDao, indexer)
                            activeLabels.remove(label)
                            completedStats.add(stats)
                            updateAggregateProgress(completedStats, activeLabels, sources.size)
                            stats
                        }
                    }
                }.map { it.await() }
            }

            // Single FTS rebuild + vacuum after ALL sources complete
            indexer.rebuildFtsAndUpdateState()
            indexer.incrementalVacuum()

            val totalChannels = allStats.sumOf { it.channelsIngested }
            val totalProgrammes = allStats.sumOf { it.programmesIngested }
            val totalBytes = allStats.sumOf { it.downloadBytes }

            _state.value = MultiSourceState.Completed(
                sourcesProcessed = sources.size,
                errors = allStats.count { it.error != null },
                sourceStats = allStats,
                totalChannels = totalChannels,
                totalProgrammes = totalProgrammes,
                totalDownloadBytes = totalBytes
            )
        } catch (e: Exception) {
            Log.e(TAG, "processAllSources failed: ${e.message}", e)
            _state.value = MultiSourceState.Error(e.message ?: "Processing failed")
        }

        scheduleIdleReset()
    }

    private fun updateAggregateProgress(
        completedStats: List<SourceStats>,
        activeLabels: List<String>,
        totalSources: Int
    ) {
        _state.value = MultiSourceState.Processing(
            completedCount = completedStats.size,
            totalSources = totalSources,
            activeSourceLabels = activeLabels.toList(),
            totalChannels = completedStats.sumOf { it.channelsIngested },
            totalProgrammes = completedStats.sumOf { it.programmesIngested },
            totalDownloadedBytes = completedStats.sumOf { it.downloadBytes },
            completedSourceStats = completedStats.toList()
        )
    }

    private suspend fun processSingleSourceInternal(sourceId: Long) {
        try {
            val db = EpgIndexDatabase.getInstance(context)
            val sourceDao = db.epgSourceDao()
            val source = sourceDao.getSourceById(sourceId) ?: run {
                _state.value = MultiSourceState.Error("Source not found")
                return
            }
            val indexer = EpgIndexer.getInstance(context)
            val label = source.label.ifBlank { extractLabel(source.url) }

            indexer.setIndexing()
            _state.value = MultiSourceState.Processing(
                completedCount = 0,
                totalSources = 1,
                activeSourceLabels = listOf(label)
            )

            val stats = processSourceParallel(source, label, sourceDao, indexer)

            // Rebuild FTS
            indexer.rebuildFtsAndUpdateState()
            indexer.incrementalVacuum()

            _state.value = MultiSourceState.Completed(
                sourcesProcessed = 1,
                errors = if (stats.error != null) 1 else 0,
                sourceStats = listOf(stats),
                totalChannels = stats.channelsIngested,
                totalProgrammes = stats.programmesIngested,
                totalDownloadBytes = stats.downloadBytes
            )
        } catch (e: Exception) {
            Log.e(TAG, "processSingleSource failed: ${e.message}", e)
            _state.value = MultiSourceState.Error(e.message ?: "Processing failed")
        }

        scheduleIdleReset()
    }

    private fun scheduleIdleReset() {
        scope.launch {
            delay(10000)
            val current = _state.value
            if (current is MultiSourceState.Completed || current is MultiSourceState.Error) {
                _state.value = MultiSourceState.Idle
            }
        }
    }

    /**
     * Process a single source for parallel ingestion. Thread-safe: no singleton mutation.
     * Dispatches to streaming (TV) or download (mobile) path.
     */
    private suspend fun processSourceParallel(
        source: EpgSourceEntity,
        label: String,
        sourceDao: org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSourceDao,
        indexer: EpgIndexer
    ): SourceStats {
        val isGzip = source.url.endsWith(".gz", ignoreCase = true)

        return if (isFixedDevice()) {
            processSourceStreamParallel(source, label, isGzip, sourceDao, indexer)
        } else {
            processSourceDownloadParallel(source, label, isGzip, sourceDao, indexer)
        }
    }

    /**
     * TV path (parallel-safe): stream directly from network to database.
     * No XmltvParser.timezoneOverrideHours mutation — passes tz as parameter.
     */
    private suspend fun processSourceStreamParallel(
        source: EpgSourceEntity,
        label: String,
        isGzip: Boolean,
        sourceDao: org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSourceDao,
        indexer: EpgIndexer
    ): SourceStats {
        var lastError: String? = null

        for (attempt in 1..MAX_RETRIES) {
            try {
                Log.d(TAG, "Streaming EPG from: ${source.url} (attempt $attempt/$MAX_RETRIES)")

                val request = Request.Builder().url(source.url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    val statusCode = response.code
                    if (!response.isSuccessful) {
                        lastError = "server returned HTTP $statusCode"
                        Log.w(TAG, "EPG streaming: $lastError (attempt $attempt)")
                        return@use
                    }

                    val body = response.body ?: throw java.io.IOException("Empty response body")
                    val rawStream = body.byteStream()
                    val buffered = BufferedInputStream(rawStream, STREAM_BUFFER_SIZE)
                    val stream = if (isGzip) GZIPInputStream(buffered, STREAM_BUFFER_SIZE) else buffered

                    val ingestionStats = stream.use {
                        indexer.ingestFromStream(it, sourceId = source.id, timezoneOverrideHours = source.timezoneOffsetHours)
                    }

                    sourceDao.markIngested(
                        id = source.id,
                        timestamp = System.currentTimeMillis(),
                        channels = ingestionStats.channelsIngested,
                        programmes = ingestionStats.programmesIngested,
                        downloadBytes = 0,
                        ingestMethod = "STREAMED"
                    )
                    Log.d(TAG, "Source streamed: $label (${ingestionStats.channelsIngested}ch, ${ingestionStats.programmesIngested}prg)")

                    lastError = null
                }

                if (lastError == null) {
                    val ingestionStats = indexer.lastIngestionStats
                    return SourceStats(
                        sourceId = source.id,
                        label = label,
                        channelsIngested = ingestionStats.channelsIngested,
                        programmesIngested = ingestionStats.programmesIngested
                    )
                }

                if (lastError?.contains("HTTP 4") == true) break
                if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }

            } catch (e: java.net.UnknownHostException) {
                lastError = "DNS lookup failed for ${e.message ?: "host"}"
                Log.w(TAG, "EPG streaming DNS failure (attempt $attempt): $lastError", e)
                if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }
            } catch (e: OutOfMemoryError) {
                System.gc()
                lastError = "out of memory during streaming"
                Log.e(TAG, "OOM during EPG streaming (attempt $attempt)", e)
                if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }
            } catch (e: java.net.SocketTimeoutException) {
                lastError = "connection timed out"
                Log.w(TAG, "EPG streaming timeout (attempt $attempt)", e)
                if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }
            } catch (e: org.xmlpull.v1.XmlPullParserException) {
                lastError = "Invalid XMLTV data: ${e.message}"
                Log.e(TAG, "EPG streaming parse error: $lastError", e)
                break
            } catch (e: java.io.IOException) {
                lastError = e.message ?: "I/O error"
                Log.w(TAG, "EPG streaming I/O error (attempt $attempt): $lastError", e)
                if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }
            } catch (e: Exception) {
                lastError = e.message ?: "unknown error"
                Log.w(TAG, "EPG streaming error (attempt $attempt): $lastError", e)
                if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }
            }
        }

        val error = lastError ?: "Streaming failed"
        sourceDao.markError(source.id, error)
        return SourceStats(source.id, label, error = error)
    }

    /**
     * Mobile path (parallel-safe): download to cache file, then ingest.
     * No XmltvParser.timezoneOverrideHours mutation — passes tz as parameter.
     */
    private suspend fun processSourceDownloadParallel(
        source: EpgSourceEntity,
        label: String,
        isGzip: Boolean,
        sourceDao: org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSourceDao,
        indexer: EpgIndexer
    ): SourceStats {
        val tmpFile = File(context.cacheDir, "xmltv_source_${source.id}_tmp")
        var downloadedBytes = 0L
        var lastError: String? = null

        try {
            // Download to cache file
            for (attempt in 1..MAX_RETRIES) {
                try {
                    Log.d(TAG, "Downloading EPG to cache: ${source.url} (attempt $attempt/$MAX_RETRIES)")

                    val request = Request.Builder().url(source.url).build()
                    okHttpClient.newCall(request).execute().use { response ->
                        val statusCode = response.code
                        if (!response.isSuccessful) {
                            lastError = "server returned HTTP $statusCode"
                            Log.w(TAG, "EPG download: $lastError (attempt $attempt)")
                            return@use
                        }

                        val body = response.body ?: throw java.io.IOException("Empty response body")

                        tmpFile.outputStream().buffered(STREAM_BUFFER_SIZE).use { output ->
                            val input = body.byteStream()
                            input.copyTo(output, STREAM_BUFFER_SIZE)
                            output.flush()
                        }
                        if (tmpFile.exists()) {
                            downloadedBytes = tmpFile.length()
                        }
                        lastError = null
                    }

                    if (lastError == null) break
                    if (lastError?.contains("HTTP 4") == true) break
                    if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }

                } catch (e: java.net.UnknownHostException) {
                    lastError = "DNS lookup failed for ${e.message ?: "host"}"
                    Log.w(TAG, "EPG download DNS failure (attempt $attempt): $lastError", e)
                    if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }
                } catch (e: OutOfMemoryError) {
                    System.gc()
                    lastError = "out of memory during download"
                    Log.e(TAG, "OOM during EPG download (attempt $attempt)", e)
                    if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }
                } catch (e: java.net.SocketTimeoutException) {
                    lastError = "connection timed out"
                    Log.w(TAG, "EPG download timeout (attempt $attempt)", e)
                    if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }
                } catch (e: java.io.IOException) {
                    lastError = e.message ?: "I/O error"
                    Log.w(TAG, "EPG download I/O error (attempt $attempt): $lastError", e)
                    if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }
                } catch (e: Exception) {
                    lastError = e.message ?: "unknown error"
                    Log.w(TAG, "EPG download error (attempt $attempt): $lastError", e)
                    if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }
                }
            }

            if (lastError != null) {
                sourceDao.markError(source.id, lastError!!)
                return SourceStats(source.id, label, downloadBytes = downloadedBytes, error = lastError)
            }

            // Ingest from file
            val bufferedStream = BufferedInputStream(tmpFile.inputStream(), STREAM_BUFFER_SIZE)
            val stream = if (isGzip) GZIPInputStream(bufferedStream, STREAM_BUFFER_SIZE) else bufferedStream

            val ingestionStats = stream.use {
                indexer.ingestFromStream(it, sourceId = source.id, timezoneOverrideHours = source.timezoneOffsetHours)
            }

            sourceDao.markIngested(
                id = source.id,
                timestamp = System.currentTimeMillis(),
                channels = ingestionStats.channelsIngested,
                programmes = ingestionStats.programmesIngested,
                downloadBytes = downloadedBytes,
                ingestMethod = "DOWNLOADED"
            )
            Log.d(TAG, "Source downloaded+ingested: $label (${ingestionStats.channelsIngested}ch, ${ingestionStats.programmesIngested}prg, ${downloadedBytes / 1024}KB)")

            return SourceStats(
                sourceId = source.id,
                label = label,
                downloadBytes = downloadedBytes,
                channelsIngested = ingestionStats.channelsIngested,
                programmesIngested = ingestionStats.programmesIngested
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error processing source: $label", e)
            sourceDao.markError(source.id, e.message ?: "Unknown error")
            return SourceStats(source.id, label, downloadBytes = downloadedBytes, error = e.message ?: "Unknown error")
        } finally {
            tmpFile.delete()
        }
    }

    private suspend fun autoRefreshIfStale() {
        val db = EpgIndexDatabase.getInstance(context)
        val sourceDao = db.epgSourceDao()
        val sources = sourceDao.getEnabledSources()
        if (sources.isEmpty()) return

        if (_state.value is MultiSourceState.Processing) {
            Log.d(TAG, "Auto-refresh skipped: already processing")
            return
        }

        val now = System.currentTimeMillis()
        val staleSources = sources.filter { source ->
            source.lastIngestedAtMs == 0L || (now - source.lastIngestedAtMs) > STALE_THRESHOLD_MS
        }

        if (staleSources.isNotEmpty()) {
            Log.d(TAG, "Auto-refresh: ${staleSources.size} of ${sources.size} sources stale, refreshing those")
            val task = object : RefreshTask {
                override val id = "epg_auto_refresh"
                override val priority = RefreshPriority.MEDIUM
                override suspend fun execute() {
                    processAllSourcesInternal(staleSources)
                }
            }
            RefreshQueue.submit(task)
        } else {
            Log.d(TAG, "Auto-refresh: all sources fresh, skipping")
        }
    }

    private fun scheduleAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = scope.launch {
            while (true) {
                val appSettings = AppSettings(context)
                if (appSettings.epgAutoRefreshEnabled) {
                    try {
                        autoRefreshIfStale()
                    } catch (e: Exception) {
                        Log.w(TAG, "Auto-refresh failed", e)
                    }
                }
                delay(AUTO_REFRESH_CHECK_INTERVAL_MS)
            }
        }
    }

    fun updateAutoRefreshSchedule() {
        autoRefreshJob?.cancel()
        scope.launch {
            scheduleAutoRefresh()
        }
    }

    data class CleanupResult(val filesDeleted: Int, val bytesFreed: Long)

    fun getStrayFiles(): List<File> {
        return try {
            context.cacheDir.listFiles { file ->
                file.name.startsWith("xmltv_") && file.isFile
            }?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
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
                    Log.d(TAG, "Cleaned up stray file: ${file.name}")
                }
            }
            return CleanupResult(filesDeleted, bytesFreed)
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup failed", e)
            return CleanupResult(0, 0)
        }
    }

}
