package org.njarasoa.fijerena.core.network.local

import android.content.Context
import android.net.Uri
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.PlayableStream
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities
import org.njarasoa.fijerena.core.player.domain.SeriesDetail

class LocalMediaProvider(
    override val providerId: Long,
    private val context: Context,
    private val config: LocalProviderConfig
) : MediaProvider {

    data class LocalProviderConfig(
        val rootPaths: List<String> = emptyList(),
        val m3uPath: String? = null
    )

    override val capabilities = ProviderCapabilities(
        supportedContentTypes = buildSet {
            add("MOVIES")
            if (config.m3uPath != null) add("LIVE_TV")
        },
        supportsEpg = false,
        supportsSearch = true,
        supportsAuthentication = false,
        supportsProgressSync = false
    )

    private var categories = emptyList<MediaCategory>()
    private var items = emptyList<MediaItem>()
    private var connected = false

    override suspend fun connect(): Result<Unit> {
        return try {
            categories = mutableListOf()
            items = mutableListOf()

            // Parse M3U if configured
            if (config.m3uPath != null) {
                val m3uUri = Uri.parse(config.m3uPath)
                val entries = context.contentResolver.openInputStream(m3uUri)
                    ?.bufferedReader()?.use { M3uParser.parse(it) }

                if (entries != null) {
                    val m3uCategories = M3uParser.entriesToCategories(entries)
                    val m3uItems = M3uParser.entriesToItems(entries, m3uCategories)
                    categories = categories + m3uCategories
                    items = items + m3uItems
                }
            }

            // Scan local directories
            for (rootPath in config.rootPaths) {
                val rootUri = Uri.parse(rootPath)
                val (dirCategories, dirItems) = LocalFileScanner.scanDirectory(context, rootUri)
                categories = categories + dirCategories
                items = items + dirItems
            }

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

        val filteredCategories = when (contentType) {
            "LIVE_TV" -> {
                // Only categories containing live channels
                val liveCategoryIds = items.filter { it.mediaType == MediaType.LIVE_CHANNEL }
                    .map { it.categoryId }.toSet()
                categories.filter { it.id in liveCategoryIds }
            }
            else -> {
                // Categories containing video files
                val videoCategoryIds = items.filter { it.mediaType == MediaType.VIDEO_FILE }
                    .map { it.categoryId }.toSet()
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
        return Result.failure(UnsupportedOperationException("Local media does not support series"))
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
}
