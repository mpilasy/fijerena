package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AppSettings
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
            val sourceLabel: String,
            val sourceIndex: Int,
            val totalSources: Int,
            val phase: String, // "Streaming" or "Downloading" or "Ingesting"
            val downloadedBytes: Long = 0,
            val downloadTotalBytes: Long = -1, // -1 = unknown
            val sourceChannels: Int = 0,
            val sourceProgrammes: Int = 0,
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
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var processJob: Job? = null
    private var autoRefreshJob: Job? = null

    private val _state = MutableStateFlow<MultiSourceState>(MultiSourceState.Idle)
    val state: StateFlow<MultiSourceState> = _state.asStateFlow()

    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(true)
                followSslRedirects(true)
                connectTimeout(60, TimeUnit.SECONDS)
                readTimeout(10, TimeUnit.MINUTES)
            }
        }
    }

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
            processAllSourcesInternal(sources)
            onComplete?.invoke()
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

            processSingleSourceInternal(sourceId)
            onComplete?.invoke()
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

            var errorCount = 0
            val allStats = mutableListOf<SourceStats>()

            sources.forEachIndexed { index, source ->
                val label = source.label.ifBlank { extractLabel(source.url) }
                val stats = processSource(source, label, index, sources.size, sourceDao, indexer, allStats)
                allStats.add(stats)
                if (stats.error != null) errorCount++
            }

            // Rebuild FTS once at the end, then reclaim freed pages
            indexer.rebuildFtsAndUpdateState()
            indexer.incrementalVacuum()

            val totalChannels = allStats.sumOf { it.channelsIngested }
            val totalProgrammes = allStats.sumOf { it.programmesIngested }
            val totalBytes = allStats.sumOf { it.downloadBytes }

            _state.value = MultiSourceState.Completed(
                sourcesProcessed = sources.size,
                errors = errorCount,
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

            val stats = processSource(source, label, 0, 1, sourceDao, indexer, emptyList())

            // Rebuild FTS
            indexer.rebuildFtsAndUpdateState()

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
     * Process a single source. Dispatches to streaming (TV) or download (mobile) path.
     */
    private suspend fun processSource(
        source: EpgSourceEntity,
        label: String,
        index: Int,
        total: Int,
        sourceDao: org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSourceDao,
        indexer: EpgIndexer,
        completedStats: List<SourceStats>
    ): SourceStats {
        val isGzip = source.url.endsWith(".gz", ignoreCase = true)

        return if (isFixedDevice()) {
            processSourceStream(source, label, index, total, isGzip, sourceDao, indexer, completedStats)
        } else {
            processSourceDownload(source, label, index, total, isGzip, sourceDao, indexer, completedStats)
        }
    }

    /**
     * TV path: stream directly from network to database. Zero disk writes.
     * Ktor HttpClient → InputStream → BufferedInputStream → GZIPInputStream (if .gz) → XmlPullParser → DB.
     */
    private suspend fun processSourceStream(
        source: EpgSourceEntity,
        label: String,
        index: Int,
        total: Int,
        isGzip: Boolean,
        sourceDao: org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSourceDao,
        indexer: EpgIndexer,
        completedStats: List<SourceStats>
    ): SourceStats {
        _state.value = MultiSourceState.Processing(
            sourceLabel = label,
            sourceIndex = index + 1,
            totalSources = total,
            phase = "Streaming",
            completedSourceStats = completedStats
        )

        var lastError: String? = null

        for (attempt in 1..MAX_RETRIES) {
            try {
                Log.d(TAG, "Streaming EPG from: ${source.url} (attempt $attempt/$MAX_RETRIES)")

                httpClient.prepareGet(source.url).execute { response ->
                    val statusCode = response.status.value
                    if (statusCode !in 200..299) {
                        lastError = "server returned HTTP $statusCode"
                        Log.w(TAG, "EPG streaming: $lastError (attempt $attempt)")
                        if (statusCode in 400..499) return@execute
                        return@execute
                    }

                    val rawStream = response.bodyAsChannel().toInputStream()
                    val buffered = BufferedInputStream(rawStream, STREAM_BUFFER_SIZE)
                    val stream = if (isGzip) GZIPInputStream(buffered, STREAM_BUFFER_SIZE) else buffered

                    val previousTz = XmltvParser.timezoneOverrideHours
                    XmltvParser.timezoneOverrideHours = source.timezoneOffsetHours
                    try {
                        stream.use {
                            indexer.ingestFromStream(it, sourceId = source.id) { ch, prg ->
                                _state.value = MultiSourceState.Processing(
                                    sourceLabel = label,
                                    sourceIndex = index + 1,
                                    totalSources = total,
                                    phase = "Streaming",
                                    sourceChannels = ch,
                                    sourceProgrammes = prg,
                                    completedSourceStats = completedStats
                                )
                            }
                        }
                    } finally {
                        XmltvParser.timezoneOverrideHours = previousTz
                    }

                    val ingestionStats = indexer.lastIngestionStats
                    sourceDao.markIngested(
                        id = source.id,
                        timestamp = System.currentTimeMillis(),
                        channels = ingestionStats.channelsIngested,
                        programmes = ingestionStats.programmesIngested,
                        downloadBytes = 0,
                        ingestMethod = "STREAMED"
                    )
                    Log.d(TAG, "Source ${index + 1}/$total streamed: $label (${ingestionStats.channelsIngested}ch, ${ingestionStats.programmesIngested}prg)")

                    lastError = null // Success — clear error
                }

                // If we got here without error, return success
                if (lastError == null) {
                    val ingestionStats = indexer.lastIngestionStats
                    return SourceStats(
                        sourceId = source.id,
                        label = label,
                        channelsIngested = ingestionStats.channelsIngested,
                        programmesIngested = ingestionStats.programmesIngested
                    )
                }

                // 4xx error — don't retry
                if (lastError?.contains("HTTP 4") == true) break
                if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }

            } catch (e: java.net.UnknownHostException) {
                Log.w(TAG, "No internet connection")
                sourceDao.markError(source.id, "No internet connection")
                return SourceStats(source.id, label, error = "No internet connection")
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
                break // No point retrying invalid data
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
     * Mobile path: download to cache file, then ingest from file.
     * Ktor HttpClient → File → BufferedInputStream → GZIPInputStream (if .gz) → XmlPullParser → DB.
     * Temp file deleted after ingestion.
     */
    private suspend fun processSourceDownload(
        source: EpgSourceEntity,
        label: String,
        index: Int,
        total: Int,
        isGzip: Boolean,
        sourceDao: org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSourceDao,
        indexer: EpgIndexer,
        completedStats: List<SourceStats>
    ): SourceStats {
        _state.value = MultiSourceState.Processing(
            sourceLabel = label,
            sourceIndex = index + 1,
            totalSources = total,
            phase = "Downloading",
            completedSourceStats = completedStats
        )

        val tmpFile = File(context.cacheDir, "xmltv_source_${source.id}_tmp")
        var downloadedBytes = 0L
        var lastError: String? = null

        try {
            // Download to cache file
            for (attempt in 1..MAX_RETRIES) {
                try {
                    Log.d(TAG, "Downloading EPG to cache: ${source.url} (attempt $attempt/$MAX_RETRIES)")

                    httpClient.prepareGet(source.url).execute { response ->
                        val statusCode = response.status.value
                        if (statusCode !in 200..299) {
                            lastError = "server returned HTTP $statusCode"
                            Log.w(TAG, "EPG download: $lastError (attempt $attempt)")
                            return@execute
                        }

                        val channel = response.bodyAsChannel()
                        tmpFile.outputStream().buffered(STREAM_BUFFER_SIZE).use { output ->
                            val buffer = ByteArray(STREAM_BUFFER_SIZE)
                            val input = channel.toInputStream()
                            var bytesRead: Int
                            var totalBytes = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytes += bytesRead
                                if (totalBytes % (256 * 1024) < STREAM_BUFFER_SIZE.toLong()) {
                                    _state.value = MultiSourceState.Processing(
                                        sourceLabel = label,
                                        sourceIndex = index + 1,
                                        totalSources = total,
                                        phase = "Downloading",
                                        downloadedBytes = totalBytes,
                                        completedSourceStats = completedStats
                                    )
                                }
                            }
                            downloadedBytes = totalBytes
                        }
                        lastError = null
                    }

                    if (lastError == null) break
                    if (lastError?.contains("HTTP 4") == true) break
                    if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }

                } catch (e: java.net.UnknownHostException) {
                    Log.w(TAG, "No internet connection")
                    sourceDao.markError(source.id, "No internet connection")
                    return SourceStats(source.id, label, error = "No internet connection")
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
            _state.value = MultiSourceState.Processing(
                sourceLabel = label,
                sourceIndex = index + 1,
                totalSources = total,
                phase = "Ingesting",
                downloadedBytes = downloadedBytes,
                downloadTotalBytes = downloadedBytes,
                completedSourceStats = completedStats
            )

            val fileStream = BufferedInputStream(tmpFile.inputStream(), STREAM_BUFFER_SIZE)
            val stream = if (isGzip) GZIPInputStream(fileStream, STREAM_BUFFER_SIZE) else fileStream

            val previousTz = XmltvParser.timezoneOverrideHours
            XmltvParser.timezoneOverrideHours = source.timezoneOffsetHours
            try {
                stream.use {
                    indexer.ingestFromStream(it, sourceId = source.id) { ch, prg ->
                        _state.value = MultiSourceState.Processing(
                            sourceLabel = label,
                            sourceIndex = index + 1,
                            totalSources = total,
                            phase = "Ingesting",
                            downloadedBytes = downloadedBytes,
                            downloadTotalBytes = downloadedBytes,
                            sourceChannels = ch,
                            sourceProgrammes = prg,
                            completedSourceStats = completedStats
                        )
                    }
                }
            } finally {
                XmltvParser.timezoneOverrideHours = previousTz
            }

            val ingestionStats = indexer.lastIngestionStats
            sourceDao.markIngested(
                id = source.id,
                timestamp = System.currentTimeMillis(),
                channels = ingestionStats.channelsIngested,
                programmes = ingestionStats.programmesIngested,
                downloadBytes = downloadedBytes,
                ingestMethod = "DOWNLOADED"
            )
            Log.d(TAG, "Source ${index + 1}/$total downloaded+ingested: $label (${ingestionStats.channelsIngested}ch, ${ingestionStats.programmesIngested}prg, ${downloadedBytes / 1024}KB)")

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
            indexer.incrementalVacuum()
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
            processJob?.cancel()
            processJob = scope.launch {
                processAllSourcesInternal(staleSources)
            }
            processJob?.join()
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
                delay(STALE_THRESHOLD_MS)
            }
        }
    }

    fun updateAutoRefreshSchedule() {
        autoRefreshJob?.cancel()
        scope.launch {
            scheduleAutoRefresh()
        }
    }

    fun cleanupStrayFiles() {
        try {
            val cacheDir = context.cacheDir
            val strayFiles = cacheDir.listFiles { file ->
                file.name.startsWith("xmltv_") && file.isFile
            }
            strayFiles?.forEach { file ->
                file.delete()
                Log.d(TAG, "Cleaned up stray file: ${file.name}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup failed", e)
        }
    }

}
