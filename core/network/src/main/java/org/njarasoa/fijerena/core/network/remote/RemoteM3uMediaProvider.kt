package org.njarasoa.fijerena.core.network.remote

import android.content.Context
import android.util.Log
import org.njarasoa.fijerena.core.network.local.M3uParser
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.PlayableStream
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import kotlinx.coroutines.delay
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class RemoteM3uMediaProvider(
    override val providerId: Long,
    private val m3uUrl: String,
    private val context: Context
) : MediaProvider {

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

    override val capabilities = ProviderCapabilities(
        supportedContentTypes = setOf(ContentType.LIVE_TV, ContentType.MOVIES),
        supportsEpg = false,
        supportsSearch = true,
        supportsAuthentication = false,
        supportsProgressSync = false
    )

    private var categories = emptyList<MediaCategory>()
    private var items = emptyList<MediaItem>()
    private var connected = false

    private val cacheFile = File(context.cacheDir, "remote_m3u_${providerId}.m3u")

    override suspend fun connect(): Result<Unit> {
        return try {
            val file = loadM3uContent()
            val (cats, its) = file.bufferedReader().use { reader ->
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

    override suspend fun disconnect() {
        connected = false
        categories = emptyList()
        items = emptyList()
    }

    override fun isConnected(): Boolean = connected

    override suspend fun getCategories(contentType: String): Result<List<MediaCategory>> {
        if (!connected) {
            val connectResult = connect()
            if (connectResult.isFailure) return connectResult.map { emptyList() }
        }

        // Single-pass set construction — avoid intermediate list from filter+map
        val filteredCategories = when (contentType) {
            ContentType.LIVE_TV -> {
                val liveCategoryIds = items.mapNotNullTo(HashSet()) {
                    if (it.mediaType == MediaType.LIVE_CHANNEL) it.categoryId else null
                }
                categories.filter { it.id in liveCategoryIds }
            }
            else -> {
                val videoCategoryIds = items.mapNotNullTo(HashSet()) {
                    if (it.mediaType == MediaType.VIDEO_FILE) it.categoryId else null
                }
                categories.filter { it.id in videoCategoryIds }
            }
        }
        return Result.success(filteredCategories)
    }

    override suspend fun getItems(categoryId: String, contentType: String): Result<List<MediaItem>> {
        if (!connected) {
            val connectResult = connect()
            if (connectResult.isFailure) return connectResult.map { emptyList() }
        }
        val filtered = items.filter { it.categoryId == categoryId }
        return Result.success(filtered)
    }

    override suspend fun getSeriesDetail(seriesId: String): Result<SeriesDetail> {
        return Result.failure(UnsupportedOperationException("Remote M3U does not support series"))
    }

    override suspend fun getMovieDetail(movieId: String): Result<MovieDetail> {
        val item = items.find { it.id == movieId }
            ?: return Result.failure(NoSuchElementException("Item not found: $movieId"))

        return Result.success(
            MovieDetail(
                id = item.id,
                name = item.name,
                coverUrl = item.thumbnailUrl
            )
        )
    }

    override suspend fun resolvePlayableStream(
        itemId: String,
        contentType: String,
        episodeId: String?,
        extension: String?
    ): Result<PlayableStream> {
        val item = items.find { it.id == itemId }
            ?: return Result.failure(NoSuchElementException("Item not found: $itemId"))

        val uri = item.streamUri
            ?: return Result.failure(IllegalStateException("No stream URI for item: $itemId"))

        return Result.success(
            PlayableStream(
                uri = uri,
                isLive = item.mediaType == MediaType.LIVE_CHANNEL,
                title = item.name
            )
        )
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

                connection = (URL(m3uUrl).openConnection() as HttpURLConnection).apply {
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
                    val header = tmpFile.bufferedReader().use { reader ->
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
