package org.njarasoa.fijerena.core.network

import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.PlayableStream
import org.njarasoa.fijerena.core.player.domain.SeriesDetail

abstract class BaseM3uMediaProvider : MediaProvider {

    protected var categories = emptyList<MediaCategory>()
    protected var items = emptyList<MediaItem>()
    protected var connected = false

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
        return Result.failure(UnsupportedOperationException("M3U does not support series"))
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
