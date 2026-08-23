package org.njarasoa.fijerena.core.network

import org.njarasoa.fijerena.core.player.domain.SeriesId
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

    override suspend fun getCategories(contentType: String): kotlin.Result<List<MediaCategory>> {
        if (!connected) {
            val connectResult = connect()
            if (connectResult.isFailure) return kotlin.Result.failure(connectResult.exceptionOrNull() ?: Exception("Connect failed"))
        }

        // Single-pass set construction — avoid intermediate list from filter+map
        val filteredCategories =
            when (contentType) {
                ContentType.LIVE_TV -> {
                    val liveCategoryIds =
                        items.mapNotNullTo(HashSet()) {
                            if (it.mediaType == MediaType.LIVE_CHANNEL) it.categoryId else null
                        }
                    categories.filter { it.id in liveCategoryIds }
                }
                else -> {
                    val videoCategoryIds =
                        items.mapNotNullTo(HashSet()) {
                            if (it.mediaType == MediaType.VIDEO_FILE) it.categoryId else null
                        }
                    categories.filter { it.id in videoCategoryIds }
                }
            }
        return kotlin.Result.success(filteredCategories)
    }

    override suspend fun getItems(
        categoryId: String,
        contentType: String,
    ): kotlin.Result<List<MediaItem>> {
        if (!connected) {
            val connectResult = connect()
            if (connectResult.isFailure) return kotlin.Result.failure(connectResult.exceptionOrNull() ?: Exception("Connect failed"))
        }
        val filtered = items.filter { it.categoryId == categoryId }
        return kotlin.Result.success(filtered)
    }

    override suspend fun getSeriesDetail(seriesId: SeriesId): kotlin.Result<SeriesDetail> =
        kotlin.Result.failure(UnsupportedOperationException("M3U does not support series"))

    override suspend fun getMovieDetail(movieId: String): kotlin.Result<MovieDetail> {
        val item =
            items.find { it.id == movieId }
                ?: return kotlin.Result.failure(NoSuchElementException("Item not found: $movieId"))

        return kotlin.Result.success(
            MovieDetail(
                id = item.id,
                name = item.name,
                coverUrl = item.thumbnailUrl,
            ),
        )
    }

    override suspend fun resolvePlayableStream(
        itemId: String,
        contentType: String,
        episodeId: String?,
        extension: String?,
    ): kotlin.Result<PlayableStream> {
        val item =
            items.find { it.id == itemId }
                ?: return kotlin.Result.failure(NoSuchElementException("Item not found: $itemId"))

        val uri =
            item.streamUri
                ?: return kotlin.Result.failure(IllegalStateException("No stream URI for item: $itemId"))

        return kotlin.Result.success(
            PlayableStream(
                uri = uri,
                isLive = item.mediaType == MediaType.LIVE_CHANNEL,
                title = item.name,
            ),
        )
    }

    override suspend fun search(
        query: String,
        contentType: String,
        includeExcluded: Boolean,
    ): kotlin.Result<List<MediaItem>> {
        val matched = items.filter { item ->
            SearchUtils.matchesQuery(item.name, query)
        }
        return kotlin.Result.success(matched)
    }
}
