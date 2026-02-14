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
 * No permanent XML files on disk.
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

        fun extractDomainLabel(url: String): String {
            return try {
                URL(url).host
                    .removePrefix("www.")
                    .removeSuffix(".com")
                    .removeSuffix(".org")
                    .removeSuffix(".net")
                    .take(30)
            } catch (e: Exception) {
                "Source"
            }
        }
    }

    sealed interface MultiSourceState {
        data object Idle : MultiSourceState
        data class Processing(
            val sourceLabel: String,
            val sourceIndex: Int,
            val totalSources: Int,
            val phase: String // "Downloading" or "Ingesting"
        ) : MultiSourceState
        data class Completed(
            val sourcesProcessed: Int,
            val errors: Int
        ) : MultiSourceState
        data class Error(val reason: String) : MultiSourceState
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var processJob: Job? = null

    private val _state = MutableStateFlow<MultiSourceState>(MultiSourceState.Idle)
    val state: StateFlow<MultiSourceState> = _state.asStateFlow()

    /**
     * Initialize the EPG file manager. Call from MainActivity.onCreate().
     * Handles migration from old single-URL AppSettings to EpgSourceEntity.
     */
    fun initialize() {
        scope.launch {
            migrateFromAppSettings()
            val indexer = EpgIndexer.getInstance(context)
            indexer.initialize()
            cleanupStrayFiles()
        }
    }

    /**
     * One-time migration: if old AppSettings.epgUrl is non-blank and no sources exist,
     * create an EpgSourceEntity from it, then clear the old settings.
     */
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
                    val label = extractDomainLabel(oldUrl)
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

            // Delete old xmltv_global.xml if it exists
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
     * First source clears existing data (full rebuild), subsequent sources append.
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

        sources.forEachIndexed { index, source ->
            val label = source.label.ifBlank { extractDomainLabel(source.url) }

            _state.value = MultiSourceState.Processing(
                sourceLabel = label,
                sourceIndex = index + 1,
                totalSources = sources.size,
                phase = "Downloading"
            )

            val tmpFile = File(context.cacheDir, "xmltv_source_${source.id}_tmp")
            val tmpGzFile = File(context.cacheDir, "xmltv_source_${source.id}_tmp.gz")

            try {
                // Download
                val downloaded = downloadToFile(source.url, tmpFile, tmpGzFile)
                if (!downloaded) {
                    sourceDao.markError(source.id, "Download failed")
                    errorCount++
                    return@forEachIndexed
                }

                // Validate
                if (!validateXmltvFile(tmpFile)) {
                    tmpFile.delete()
                    sourceDao.markError(source.id, "Invalid XMLTV file")
                    errorCount++
                    return@forEachIndexed
                }

                _state.value = MultiSourceState.Processing(
                    sourceLabel = label,
                    sourceIndex = index + 1,
                    totalSources = sources.size,
                    phase = "Ingesting"
                )

                // Ingest
                if (index == 0) {
                    // First source: full rebuild (clears DB)
                    indexer.startIndexing(tmpFile)
                } else {
                    // Subsequent sources: append
                    indexer.appendFromFile(tmpFile, source.timezoneOffsetHours)
                }

                // Mark success
                sourceDao.markIngested(source.id, System.currentTimeMillis())
                Log.d(TAG, "Source ${index + 1}/${sources.size} ingested: $label")

            } catch (e: Exception) {
                Log.e(TAG, "Error processing source: $label", e)
                sourceDao.markError(source.id, e.message ?: "Unknown error")
                errorCount++
            } finally {
                // Always delete temp files
                tmpFile.delete()
                tmpGzFile.delete()
            }
        }

        // If multiple sources, rebuild FTS once at the end
        if (sources.size > 1) {
            indexer.rebuildFtsAndUpdateState()
        }

        _state.value = MultiSourceState.Completed(
            sourcesProcessed = sources.size,
            errors = errorCount
        )

        // Reset to Idle after a brief display period
        scope.launch {
            delay(3000)
            _state.value = MultiSourceState.Idle
        }
    }

    /**
     * Delete any stray xmltv_* files in the cache directory.
     */
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
     * Returns true on success.
     */
    private suspend fun downloadToFile(url: String, tmpFile: File, tmpGzFile: File): Boolean {
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
                        }
                        output.flush()
                        Log.d(TAG, "XMLTV download complete: ${totalBytes / (1024 * 1024)}MB")
                    }
                }

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

                return true

            } catch (e: java.net.UnknownHostException) {
                tmpFile.delete(); tmpGzFile.delete()
                Log.w(TAG, "No internet connection")
                return false
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
        return false
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
