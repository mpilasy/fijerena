package org.njarasoa.fijerena.core.network.remote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.BaseM3uMediaProvider
import org.njarasoa.fijerena.core.network.local.M3uParser
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class RemoteM3uMediaProvider(
    override val providerId: Long,
    private val m3uUrl: String,
    private val context: Context,
) : BaseM3uMediaProvider() {
    companion object {
        private const val TAG = "RemoteM3uProvider"
        private const val ID_PREFIX = "rm3u"
        private const val CACHE_TTL_MS = 6L * 60 * 60 * 1000 // 6 hours
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val BUFFER_SIZE = 65536
        private const val MAX_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 1000L
    }

    override val capabilities =
        ProviderCapabilities(
            supportedContentTypes = setOf(ContentType.LIVE_TV, ContentType.MOVIES),
            supportsEpg = false,
            supportsSearch = true,
            supportsAuthentication = false,
            supportsProgressSync = false,
        )

    private val cacheFile = File(context.cacheDir, "remote_m3u_$providerId.m3u")

    override suspend fun connect(): Result<Unit> {
        return try {
            val file = loadM3uContent()
            val (cats, its) =
                file.bufferedReader().use { reader ->
                    M3uParser.processEntries(reader, ID_PREFIX)
                }
            if (its.isEmpty()) {
                return Result.failure(IllegalStateException("No valid entries found in M3U playlist"))
            }
            categories = cats
            items = its
            connected = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun loadM3uContent(): File {
        // Check cache
        if (cacheFile.exists()) {
            val age = System.currentTimeMillis() - cacheFile.lastModified()
            if (age < CACHE_TTL_MS) {
                return cacheFile
            }
        }

        // Download with retries
        downloadWithRetries()
        return cacheFile
    }

    private suspend fun downloadWithRetries() {
        var lastError: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            var connection: HttpURLConnection? = null
            try {
                connection =
                    (URL(m3uUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = CONNECT_TIMEOUT_MS
                        readTimeout = READ_TIMEOUT_MS
                        instanceFollowRedirects = true
                        setRequestProperty("Accept-Encoding", "identity")
                    }

                val statusCode = connection.responseCode
                if (statusCode !in 200..299) {
                    lastError = Exception("Server returned HTTP $statusCode")
                    if (statusCode in 400..499) break // Client errors won't recover
                    if (attempt < MAX_RETRIES) {
                        delay(RETRY_BASE_DELAY_MS * attempt)
                        continue
                    }
                    break
                }

                val tmpFile = File(context.cacheDir, "remote_m3u_${providerId}_tmp")
                try {
                    // Stream to temp file
                    connection.inputStream.buffered(BUFFER_SIZE).use { input ->
                        tmpFile.outputStream().buffered(BUFFER_SIZE).use { output ->
                            input.copyTo(output, BUFFER_SIZE)
                            output.flush()
                        }
                    }

                    // Validate #EXTM3U header
                    val header =
                        tmpFile.bufferedReader().use { reader ->
                            val buf = CharArray(256)
                            val read = reader.read(buf)
                            if (read > 0) String(buf, 0, read) else ""
                        }
                    if (!header.trimStart().startsWith("#EXTM3U")) {
                        throw Exception("Invalid M3U file: missing #EXTM3U header")
                    }

                    if (cacheFile.exists()) {
                        cacheFile.delete()
                    }
                    if (!tmpFile.renameTo(cacheFile)) {
                        tmpFile.copyTo(cacheFile, overwrite = true)
                        tmpFile.delete()
                    }
                    return
                } finally {
                    if (tmpFile.exists()) tmpFile.delete()
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "M3U download error (attempt $attempt): ${e.message}")
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_BASE_DELAY_MS * attempt)
                }
            } finally {
                connection?.disconnect()
            }
        }

        // All retries exhausted — fall back to stale cache if available
        if (cacheFile.exists()) {
            Log.w(TAG, "Download failed, using stale cache: ${lastError?.message}")
            return
        }

        throw lastError ?: Exception("Failed to download M3U playlist")
    }
}
