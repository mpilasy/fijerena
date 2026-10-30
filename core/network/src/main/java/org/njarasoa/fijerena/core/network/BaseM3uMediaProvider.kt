package org.njarasoa.fijerena.core.network

import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.PlayableStream
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.domain.SeriesId

abstract class BaseM3uMediaProvider : MediaProvider {
    // The lists and the category index as one immutable snapshot, null when not connected.
    // A reader reads it once: trimMemory() on Main can drop it at any time, and with separate
    // fields it emptied the lists between a reader's check and its read (thread races plan, 13).
    // Without the index getItems() would linear-scan the full list on every category open — cheap for a
    // typical playlist, but a real per-navigation cost on a 50k-100k entry IPTV catalog.
    private class Snapshot(
        val categories: List<MediaCategory>,
        val items: List<MediaItem>,
    ) {
        val itemsByCategory: Map<String, List<MediaItem>> = items.groupBy { it.categoryId }
    }

    @Volatile private var snapshot: Snapshot? = null

    /** Subclasses call this after a successful parse or scan; it also marks the provider connected. */
    protected fun publish(
        categories: List<MediaCategory>,
        items: List<MediaItem>,
    ) {
        snapshot = Snapshot(categories, items)
    }

    /** The current snapshot; connects first when there is none. */
    private suspend fun loaded(): kotlin.Result<Snapshot> {
        snapshot?.let { return kotlin.Result.success(it) }
        val connectResult = connect()
        if (connectResult.isFailure) return kotlin.Result.failure(connectResult.exceptionOrNull() ?: Exception("Connect failed"))
        // A trimMemory() right after the connect: show nothing now, the next call connects again.
        return kotlin.Result.success(snapshot ?: Snapshot(emptyList(), emptyList()))
    }

    override suspend fun disconnect() {
        snapshot = null
    }

    override fun isConnected(): Boolean = snapshot != null

    // Large IPTV/local playlists (50k-100k entries) hold the items, the categories, and the
    // itemsByCategory index in memory indefinitely otherwise. Dropping the snapshot also marks
    // the provider disconnected, so getCategories()/getItems() connect again on next use instead
    // of showing a permanently empty provider.
    override fun trimMemory() {
        snapshot = null
    }

    override suspend fun getCategories(contentType: String): kotlin.Result<List<MediaCategory>> {
        val current = loaded().getOrElse { return kotlin.Result.failure(it) }
        val items = current.items
        val categories = current.categories

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
        val current = loaded().getOrElse { return kotlin.Result.failure(it) }
        return kotlin.Result.success(current.itemsByCategory[categoryId].orEmpty())
    }

    /**
     * The ids among [itemIds] the playlist no longer lists. From the list in memory only: empty when
     * it is not loaded (never connected, or dropped by trimMemory()), so nothing is marked gone on a
     * guess.
     */
    override suspend fun missingItems(
        itemIds: Collection<String>,
        contentType: String,
    ): Set<String> {
        val current = snapshot?.takeIf { it.items.isNotEmpty() } ?: return emptySet()
        val listed = current.items.mapTo(HashSet()) { it.id }
        return itemIds.filterTo(HashSet()) { it !in listed }
    }

    override suspend fun getSeriesDetail(seriesId: SeriesId): kotlin.Result<SeriesDetail> =
        kotlin.Result.failure(UnsupportedOperationException("M3U does not support series"))

    override suspend fun getMovieDetail(movieId: String): kotlin.Result<MovieDetail> {
        val current = loaded().getOrElse { return kotlin.Result.failure(it) }
        val item =
            current.items.find { it.id == movieId }
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
        // loaded(), not the snapshot: after trimMemory() the list is read again, else every
        // channel would be "not found" until a list screen reconnected the source.
        val current = loaded().getOrElse { return kotlin.Result.failure(it) }
        val item =
            current.items.find { it.id == itemId }
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
        val current = loaded().getOrElse { return kotlin.Result.failure(it) }
        val matched =
            current.items.filter { item ->
                SearchUtils.matchesQuery(item.name, query)
            }
        return kotlin.Result.success(matched)
    }
}
