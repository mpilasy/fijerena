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
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.player.config.NetworkType
import org.njarasoa.fijerena.core.player.network.NetworkMonitor
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Singleton managing background EPG file download lifecycle.
 * Downloads the XMLTV EPG file in the background, refreshes every 24h,
 * and exposes state for UI to show/hide EPG buttons.
 *
 * Uses raw HttpURLConnection instead of Ktor to avoid in-memory buffering
 * of large (500MB+) responses.
 */
class EpgFileManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "EpgFileManager"
        private const val PREFS_NAME = "epg_file_manager"
        private const val KEY_DOWNLOAD_TIMESTAMP = "file_download_timestamp"
        private const val KEY_FILE_URL = "file_url"
        private const val REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1000 // 24 hours
        private const val BUFFER_SIZE = 65536 // 64KB for faster large-file I/O
        private const val GZIP_BUFFER_SIZE = 65536
        private const val LOG_INTERVAL_BYTES = 50L * 1024 * 1024 // 50MB
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 5000L
        private const val CONNECT_TIMEOUT_MS = 60_000
        private const val READ_TIMEOUT_MS = 600_000 // 10 minutes for large XMLTV files

        @Volatile
        private var instance: EpgFileManager? = null

        fun getInstance(context: Context): EpgFileManager {
            return instance ?: synchronized(this) {
                instance ?: EpgFileManager(context.applicationContext).also { instance = it }
            }
        }
    }

    sealed interface EpgFileState {
        data object NoUrl : EpgFileState
        data object Downloading : EpgFileState
        data class Ready(val file: File, val sizeBytes: Long, val lastModifiedMs: Long) : EpgFileState
        data class Failed(val reason: String) : EpgFileState
        data class Error(val reason: String, val file: File? = null) : EpgFileState
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appSettings = AppSettings(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null

    private val xmltvFile = File(context.cacheDir, "xmltv_global.xml")
    private val tmpFile = File(context.cacheDir, "xmltv_global_tmp")
    private val tmpGzFile = File(context.cacheDir, "xmltv_global_tmp.gz")

    private val _state = MutableStateFlow<EpgFileState>(EpgFileState.NoUrl)
    val state: StateFlow<EpgFileState> = _state.asStateFlow()

    /**
     * Initialize the EPG file manager. Call from MainActivity.onCreate().
     * Checks local file, sets initial state, schedules download/refresh.
     */
    fun initialize() {
        // Apply timezone override to parser
        XmltvParser.timezoneOverrideHours = appSettings.epgTimezoneOffsetHours

        val url = appSettings.epgUrl
        if (url.isBlank()) {
            _state.value = EpgFileState.NoUrl
            return
        }

        val savedUrl = prefs.getString(KEY_FILE_URL, null)
        val savedTimestamp = prefs.getLong(KEY_DOWNLOAD_TIMESTAMP, 0L)
        val age = System.currentTimeMillis() - savedTimestamp

        if (xmltvFile.exists() && savedUrl == url && savedTimestamp > 0) {
            // File exists for this URL
            _state.value = EpgFileState.Ready(xmltvFile, xmltvFile.length(), savedTimestamp)
            triggerIndexing(xmltvFile)

            if (age >= REFRESH_INTERVAL_MS) {
                // Stale file — refresh in background, keep Ready state
                Log.d(TAG, "EPG file stale (${age / 3600000}h old), refreshing in background")
                scheduleDownload(url, keepReadyDuringRefresh = true)
            } else {
                // Fresh file — schedule refresh for remaining time
                val remaining = REFRESH_INTERVAL_MS - age
                Log.d(TAG, "EPG file fresh, next refresh in ${remaining / 60000}min")
                scheduleRefreshAfter(url, remaining)
            }
        } else {
            // No valid file — download now
            Log.d(TAG, "No valid EPG file, starting download")
            scheduleDownload(url, keepReadyDuringRefresh = false)
        }
    }

    /**
     * Trigger a fresh download. Called when user changes EPG URL in settings.
     */
    fun triggerDownload() {
        downloadJob?.cancel()
        val url = appSettings.epgUrl
        if (url.isBlank()) {
            _state.value = EpgFileState.NoUrl
            return
        }
        scheduleDownload(url, keepReadyDuringRefresh = false)
    }

    /**
     * Get the EPG file if available. Returns null if not ready.
     */
    fun getEpgFile(): File? {
        val currentState = _state.value
        return when (currentState) {
            is EpgFileState.Ready -> currentState.file
            is EpgFileState.Error -> currentState.file
            else -> null
        }
    }

    private fun scheduleDownload(url: String, keepReadyDuringRefresh: Boolean) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            if (!keepReadyDuringRefresh) {
                _state.value = EpgFileState.Downloading
            }
            downloadFile(url, keepReadyDuringRefresh)
        }
    }

    private fun scheduleRefreshAfter(url: String, delayMs: Long) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            delay(delayMs)
            downloadFile(url, keepReadyDuringRefresh = true)
        }
    }

    private suspend fun downloadFile(url: String, keepReadyDuringRefresh: Boolean) {
        // Only download on WiFi/Ethernet — EPG files can be hundreds of MB
        val networkType = NetworkMonitor.currentNetworkType
        if (networkType == NetworkType.CELLULAR) {
            Log.d(TAG, "Skipping EPG download: on cellular network")
            handleError("EPG download skipped: WiFi required for large EPG files", keepReadyDuringRefresh)
            return
        }

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
                    // Disable automatic gzip decompression — we handle .gz ourselves
                    setRequestProperty("Accept-Encoding", "identity")
                }

                val statusCode = connection.responseCode
                if (statusCode !in 200..299) {
                    lastError = "server returned HTTP $statusCode"
                    Log.w(TAG, "EPG download: $lastError (attempt $attempt)")
                    if (statusCode in 400..499) break // Client errors won't recover
                    if (attempt < MAX_RETRIES) { delay(RETRY_DELAY_MS * attempt); continue }
                    break
                }

                val downloadTarget = if (isGzip) tmpGzFile else tmpFile

                // Stream directly from network to disk — zero in-memory buffering
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

                // If .gz, decompress to tmpFile
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

                // Basic validation: check it starts with XML-ish content
                if (!validateXmltvFile(tmpFile)) {
                    tmpFile.delete()
                    handleError("EPG file invalid: not a valid XMLTV file", keepReadyDuringRefresh)
                    return
                }

                // Atomic rename
                xmltvFile.delete()
                tmpFile.renameTo(xmltvFile)

                val now = System.currentTimeMillis()
                prefs.edit()
                    .putString(KEY_FILE_URL, url)
                    .putLong(KEY_DOWNLOAD_TIMESTAMP, now)
                    .apply()

                _state.value = EpgFileState.Ready(xmltvFile, xmltvFile.length(), now)
                Log.d(TAG, "EPG file ready: ${xmltvFile.length() / (1024 * 1024)}MB")
                triggerIndexing(xmltvFile)

                // Schedule next refresh
                scheduleRefreshAfter(url, REFRESH_INTERVAL_MS)
                return // Success — exit retry loop

            } catch (e: java.net.UnknownHostException) {
                tmpFile.delete(); tmpGzFile.delete()
                handleError("EPG download failed: no internet connection", keepReadyDuringRefresh)
                return // No point retrying without network
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
                if (lastError == "insufficient storage") break // Won't recover
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
        // All retries exhausted
        handleError("EPG download failed: $lastError", keepReadyDuringRefresh)
    }

    /**
     * Trigger re-indexing if the EPG file exists and the index is stale.
     * Called from settings when timezone override changes.
     */
    fun reindexIfNeeded() {
        if (xmltvFile.exists()) {
            triggerIndexing(xmltvFile)
        }
    }

    private fun triggerIndexing(file: File) {
        scope.launch {
            val indexer = EpgIndexer.getInstance(context)
            if (indexer.needsReindex(file)) {
                Log.d(TAG, "EPG file changed, starting indexing")
                indexer.startIndexing(file)
            } else {
                Log.d(TAG, "EPG index up-to-date, restoring state")
                indexer.initialize()
            }
        }
    }

    private fun handleError(reason: String, keepReadyDuringRefresh: Boolean) {
        Log.e(TAG, reason)
        if (keepReadyDuringRefresh && xmltvFile.exists()) {
            // Stale file still usable — report error with file reference
            _state.value = EpgFileState.Error(reason, xmltvFile)
        } else if (xmltvFile.exists()) {
            // File exists but we weren't in Ready state
            _state.value = EpgFileState.Error(reason, xmltvFile)
        } else {
            // No file at all
            _state.value = EpgFileState.Failed(reason)
        }
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
