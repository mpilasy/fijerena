package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
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
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Singleton managing background EPG file download lifecycle.
 * Downloads the XMLTV EPG file in the background, refreshes every 24h,
 * and exposes state for UI to show/hide EPG buttons.
 */
class EpgFileManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "EpgFileManager"
        private const val PREFS_NAME = "epg_file_manager"
        private const val KEY_DOWNLOAD_TIMESTAMP = "file_download_timestamp"
        private const val KEY_FILE_URL = "file_url"
        private const val REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1000 // 24 hours
        private const val BUFFER_SIZE = 8192
        private const val LOG_INTERVAL_BYTES = 10L * 1024 * 1024 // 10MB

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

    private val client = HttpClient(Android) {
        engine {
            connectTimeout = 30_000
            socketTimeout = 300_000 // 5 minutes for large XMLTV files
        }
    }

    private val _state = MutableStateFlow<EpgFileState>(EpgFileState.NoUrl)
    val state: StateFlow<EpgFileState> = _state.asStateFlow()

    /**
     * Initialize the EPG file manager. Call from MainActivity.onCreate().
     * Checks local file, sets initial state, schedules download/refresh.
     */
    fun initialize() {
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
        try {
            Log.d(TAG, "Downloading XMLTV from: $url")
            val response = client.get(url)
            val statusCode = response.status.value
            if (statusCode !in 200..299) {
                handleError("EPG download failed: server returned HTTP $statusCode", keepReadyDuringRefresh)
                return
            }

            val channel = response.bodyAsChannel()
            val rawStream: InputStream = channel.toInputStream()

            val inputStream = if (url.endsWith(".gz", ignoreCase = true)) {
                GZIPInputStream(rawStream)
            } else {
                rawStream
            }

            tmpFile.delete()
            inputStream.use { input ->
                tmpFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var totalBytes = 0L
                    var lastLoggedMb = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        val currentMb = totalBytes / (1024 * 1024)
                        if (currentMb >= lastLoggedMb + (LOG_INTERVAL_BYTES / (1024 * 1024))) {
                            Log.d(TAG, "XMLTV download: ${currentMb}MB")
                            lastLoggedMb = currentMb
                        }
                    }
                    Log.d(TAG, "XMLTV download complete: ${totalBytes / (1024 * 1024)}MB")
                }
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

            // Schedule next refresh
            scheduleRefreshAfter(url, REFRESH_INTERVAL_MS)

        } catch (e: java.net.UnknownHostException) {
            handleError("EPG download failed: no internet connection", keepReadyDuringRefresh)
        } catch (e: java.net.SocketTimeoutException) {
            handleError("EPG download failed: connection timed out", keepReadyDuringRefresh)
        } catch (e: java.io.IOException) {
            val message = if (e.message?.contains("No space", ignoreCase = true) == true) {
                "EPG download failed: insufficient storage"
            } else {
                "EPG download failed: ${e.message}"
            }
            handleError(message, keepReadyDuringRefresh)
        } catch (e: Exception) {
            handleError("EPG download failed: ${e.message}", keepReadyDuringRefresh)
        } finally {
            tmpFile.delete()
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
