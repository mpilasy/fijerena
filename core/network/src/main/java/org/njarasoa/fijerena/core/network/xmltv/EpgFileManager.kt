package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
import org.njarasoa.fijerena.core.player.network.NetworkMonitor
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Singleton managing multi-source EPG download-ingest-delete pipeline.
 *
 * For each source: download to temp file -> parse into SQLite -> delete temp file.
 * No permanent XML files on disk. Database stays searchable during sync
 * (append-only with REPLACE deduplication via unique index).
 *
 * Uses raw HttpURLConnection instead of Ktor to avoid in-memory buffering
 * of large (500MB+) responses.
 */
class EpgFileManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "EpgFileManager"
        private const val PREFS_NAME = "epg_file_manager"
        private const val KEY_MIGRATED_TO_SOURCES = "migrated_to_sources_v1"
        private const val BUFFER_SIZE = 65536
        private const val GZIP_BUFFER_SIZE = 65536
        private const val LOG_INTERVAL_BYTES = 50L * 1024 * 1024
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 5000L
        private const val CONNECT_TIMEOUT_MS = 60_000
        private const val READ_TIMEOUT_MS = 600_000

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
            val phase: String, // "Downloading" or "Ingesting"
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

    private val _state = MutableStateFlow<MultiSourceState>(MultiSourceState.Idle)
    val state: StateFlow<MultiSourceState> = _state.asStateFlow()

    // Track download bytes for current source (updated during download)
    @Volatile
    private var currentDownloadBytes: Long = 0
    @Volatile
    private var currentDownloadTotalBytes: Long = -1

    fun initialize() {
        scope.launch {
            migrateFromAppSettings()
            val indexer = EpgIndexer.getInstance(context)
            indexer.initialize()
            cleanupStrayFiles()
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
     * Process all enabled sources: download-ingest-delete each sequentially.
     * Append-only: database stays searchable throughout.
     */
    suspend fun processAllSources(sources: List<EpgSourceEntity>) {
        processJob?.cancel()

        if (sources.isEmpty()) {
            _state.value = MultiSourceState.Error("No sources to process")
            return
        }

        val networkType = NetworkMonitor.currentNetworkType
        if (networkType == NetworkType.CELLULAR) {
            _state.value = MultiSourceState.Error("WiFi required for EPG downloads")
            return
        }

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

        // Rebuild FTS once at the end
        indexer.rebuildFtsAndUpdateState()

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

        scope.launch {
            delay(10000)
            if (_state.value is MultiSourceState.Completed) {
                _state.value = MultiSourceState.Idle
            }
        }
    }

    /**
     * Process a single source by ID. Used for per-source refresh.
     */
    suspend fun processSingleSource(sourceId: Long) {
        val networkType = NetworkMonitor.currentNetworkType
        if (networkType == NetworkType.CELLULAR) {
            _state.value = MultiSourceState.Error("WiFi required for EPG downloads")
            return
        }

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

        scope.launch {
            delay(10000)
            if (_state.value is MultiSourceState.Completed) {
                _state.value = MultiSourceState.Idle
            }
        }
    }

    /**
     * Process a single source: download, ingest, delete temp file.
     * Returns stats for this source.
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
        currentDownloadBytes = 0
        currentDownloadTotalBytes = -1

        _state.value = MultiSourceState.Processing(
            sourceLabel = label,
            sourceIndex = index + 1,
            totalSources = total,
            phase = "Downloading",
            downloadedBytes = 0,
            downloadTotalBytes = -1,
            completedSourceStats = completedStats
        )

        val tmpFile = File(context.cacheDir, "xmltv_source_${source.id}_tmp")
        val tmpGzFile = File(context.cacheDir, "xmltv_source_${source.id}_tmp.gz")

        try {
            // Download
            val downloadedBytes = downloadToFile(source.url, tmpFile, tmpGzFile) { bytes, totalBytes ->
                currentDownloadBytes = bytes
                currentDownloadTotalBytes = totalBytes
                _state.value = MultiSourceState.Processing(
                    sourceLabel = label,
                    sourceIndex = index + 1,
                    totalSources = total,
                    phase = "Downloading",
                    downloadedBytes = bytes,
                    downloadTotalBytes = totalBytes,
                    completedSourceStats = completedStats
                )
            }

            if (downloadedBytes < 0) {
                sourceDao.markError(source.id, "Download failed")
                return SourceStats(source.id, label, error = "Download failed")
            }

            // Validate
            if (!validateXmltvFile(tmpFile)) {
                tmpFile.delete()
                sourceDao.markError(source.id, "Invalid XMLTV file")
                return SourceStats(source.id, label, downloadBytes = downloadedBytes, error = "Invalid XMLTV file")
            }

            _state.value = MultiSourceState.Processing(
                sourceLabel = label,
                sourceIndex = index + 1,
                totalSources = total,
                phase = "Ingesting",
                downloadedBytes = downloadedBytes,
                downloadTotalBytes = downloadedBytes,
                completedSourceStats = completedStats
            )

            // Ingest with per-source timezone
            val previousTz = XmltvParser.timezoneOverrideHours
            XmltvParser.timezoneOverrideHours = source.timezoneOffsetHours
            try {
                indexer.startIndexing(tmpFile)
            } finally {
                XmltvParser.timezoneOverrideHours = previousTz
            }

            val ingestionStats = indexer.lastIngestionStats
            sourceDao.markIngested(
                id = source.id,
                timestamp = System.currentTimeMillis(),
                channels = ingestionStats.channelsIngested,
                programmes = ingestionStats.programmesIngested,
                downloadBytes = downloadedBytes
            )
            Log.d(TAG, "Source ${index + 1}/$total ingested: $label (${ingestionStats.channelsIngested}ch, ${ingestionStats.programmesIngested}prg, ${downloadedBytes / 1024}KB)")

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
            return SourceStats(source.id, label, downloadBytes = currentDownloadBytes, error = e.message ?: "Unknown error")
        } finally {
            tmpFile.delete()
            tmpGzFile.delete()
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

    /**
     * Download a URL to a temp file, handling .gz decompression.
     * Returns downloaded bytes on success, -1 on failure.
     * Calls [onProgress] with (bytesDownloaded, totalBytes) during download.
     */
    private suspend fun downloadToFile(
        url: String,
        tmpFile: File,
        tmpGzFile: File,
        onProgress: (Long, Long) -> Unit
    ): Long {
        val isGzip = url.endsWith(".gz", ignoreCase = true)
        var lastError: String? = null

        for (attempt in 1..MAX_RETRIES) {
            var connection: HttpURLConnection? = null
            try {
                Log.d(TAG, "Downloading XMLTV from: $url (attempt $attempt/$MAX_RETRIES)")

                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("Accept-Encoding", "identity")
                }

                val statusCode = connection.responseCode
                if (statusCode !in 200..299) {
                    lastError = "server returned HTTP $statusCode"
                    Log.w(TAG, "EPG download: $lastError (attempt $attempt)")
                    if (statusCode in 400..499) break
                    if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }
                    break
                }

                val contentLength = connection.contentLengthLong
                val downloadTarget = if (isGzip) tmpGzFile else tmpFile

                downloadTarget.delete()
                connection.inputStream.buffered(BUFFER_SIZE).use { input ->
                    downloadTarget.outputStream().buffered(BUFFER_SIZE).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        var totalBytes = 0L
                        var lastLoggedMb = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                            val currentMb = totalBytes / (1024 * 1024)
                            if (currentMb >= lastLoggedMb + (LOG_INTERVAL_BYTES / (1024 * 1024))) {
                                Log.d(TAG, "XMLTV download: ${currentMb}MB written")
                                lastLoggedMb = currentMb
                            }
                            // Report progress every 256KB
                            if (totalBytes % (256 * 1024) < BUFFER_SIZE.toLong()) {
                                onProgress(totalBytes, contentLength)
                            }
                        }
                        output.flush()
                        onProgress(totalBytes, totalBytes)
                        Log.d(TAG, "XMLTV download complete: ${totalBytes / (1024 * 1024)}MB")
                    }
                }

                val rawDownloadSize = downloadTarget.length()

                if (isGzip) {
                    Log.d(TAG, "Decompressing .gz file (${tmpGzFile.length() / (1024 * 1024)}MB)")
                    tmpFile.delete()
                    GZIPInputStream(tmpGzFile.inputStream().buffered(BUFFER_SIZE), GZIP_BUFFER_SIZE).use { gzInput ->
                        tmpFile.outputStream().buffered(BUFFER_SIZE).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytesRead: Int
                            var totalBytes = 0L
                            var lastLoggedMb = 0L
                            while (gzInput.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytes += bytesRead
                                val currentMb = totalBytes / (1024 * 1024)
                                if (currentMb >= lastLoggedMb + (LOG_INTERVAL_BYTES / (1024 * 1024))) {
                                    Log.d(TAG, "XMLTV decompress: ${currentMb}MB written")
                                    lastLoggedMb = currentMb
                                }
                            }
                            output.flush()
                            Log.d(TAG, "XMLTV decompress complete: ${totalBytes / (1024 * 1024)}MB")
                        }
                    }
                    tmpGzFile.delete()
                }

                return rawDownloadSize

            } catch (e: java.net.UnknownHostException) {
                tmpFile.delete(); tmpGzFile.delete()
                Log.w(TAG, "No internet connection")
                return -1
            } catch (e: OutOfMemoryError) {
                tmpFile.delete(); tmpGzFile.delete()
                System.gc()
                lastError = "out of memory during download"
                Log.e(TAG, "OOM during EPG download (attempt $attempt)", e)
                if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }
            } catch (e: java.net.SocketTimeoutException) {
                tmpFile.delete(); tmpGzFile.delete()
                lastError = "connection timed out"
                Log.w(TAG, "EPG download timeout (attempt $attempt)", e)
                if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }
            } catch (e: java.io.IOException) {
                tmpFile.delete(); tmpGzFile.delete()
                lastError = if (e.message?.contains("No space", ignoreCase = true) == true) {
                    "insufficient storage"
                } else {
                    e.message ?: "I/O error"
                }
                Log.w(TAG, "EPG download I/O error (attempt $attempt): $lastError", e)
                if (lastError == "insufficient storage") break
                if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }
            } catch (e: Exception) {
                tmpFile.delete(); tmpGzFile.delete()
                lastError = e.message ?: "unknown error"
                Log.w(TAG, "EPG download error (attempt $attempt): $lastError", e)
                if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }
            } finally {
                connection?.disconnect()
            }
        }
        return -1
    }

    private fun validateXmltvFile(file: File): Boolean {
        return try {
            file.bufferedReader().use { reader ->
                val header = CharArray(256)
                val read = reader.read(header)
                if (read <= 0) return false
                val headerStr = String(header, 0, read)
                headerStr.contains("<?xml", ignoreCase = true) ||
                    headerStr.contains("<tv", ignoreCase = true)
            }
        } catch (e: Exception) {
            false
        }
    }

}
