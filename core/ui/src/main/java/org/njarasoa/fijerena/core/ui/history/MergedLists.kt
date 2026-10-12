package org.njarasoa.fijerena.core.ui.history

import android.content.Context
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.RecentCategory
import org.njarasoa.fijerena.core.network.RecentRemoval
import org.njarasoa.fijerena.core.network.suspendRunCatching
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ContinueWatchingItem
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.isCategoryMarker
import org.njarasoa.fijerena.core.ui.di.AppContainer

/**
 * Recent, Continue Watching, favourites and Recent Categories of every source the profile has, as
 * one list each (docs/plans/20261012_multi-source-search-history-plan.md → Phase 4). Each source
 * keeps its own lists, its own Recent size (P4) included; only what is shown is merged, each row
 * carrying its source (`MediaItem.providerId`), and whatever acts on a row (a removal, its Undo)
 * goes to that row's source. Xtream sources only (Q6): when the screen's source is of another kind,
 * its lists are its own, as before.
 */
class MergedLists(
    /** The screen's source first, then the profile's other Xtream sources. */
    val sources: List<Source>,
    /** [sources] in the profile's order: the merged category lists' order (Phase 6). */
    val inProfileOrder: List<Source> = sources,
) {
    /** One of the profile's sources and its repository. */
    class Source(
        val providerId: Long,
        val name: String,
        val repo: MediaRepository,
    )

    /** The screen's own source. */
    val primary: MediaRepository get() = sources.first().repo

    /** Each source's name by id when the lists mix more than one (their rows show it), else empty. */
    val sourceNames: Map<Long, String> =
        if (sources.size > 1) sources.associate { it.providerId to it.name } else emptyMap()

    /** [providerId]'s repository; the screen's own for 0 or a source not among these. */
    fun repoFor(providerId: Long): MediaRepository = sources.firstOrNull { it.providerId == providerId }?.repo ?: primary

    /**
     * Recent of [contentType], merged as each source publishes its own (a play, a removal, a live
     * sync change on any of them shows). Null until every source's list has loaded.
     */
    fun recentItems(contentType: String): Flow<List<MediaItem>?> =
        combine(sources.map { it.repo.recentItems(contentType) }) { lists ->
            if (lists.any { it == null }) null else mergeRecent(lists.map { it.orEmpty() })
        }

    /** Recent of [contentType] as the sources last published it, merged; empty where one hasn't yet. */
    fun publishedRecent(contentType: String): List<MediaItem> =
        mergeRecent(
            sources.map {
                it.repo
                    .recentItems(contentType)
                    .value
                    .orEmpty()
            },
        )

    /** Loads the Recent of each source that hasn't published one yet ([recentItems] waits for all). */
    suspend fun loadRecentItems(contentType: String) {
        each { repo -> if (repo.recentItems(contentType).value == null) repo.refreshRecentItems(contentType) }
    }

    /** Re-reads every source's Recent, publishing each, and returns them merged. */
    suspend fun refreshRecentItems(contentType: String): List<MediaItem> = mergeRecent(each { it.refreshRecentItems(contentType) })

    /** Recent of [contentType] read once, without publishing it (Home's rows). */
    suspend fun recent(contentType: String): List<MediaItem> = mergeRecent(each { it.getRecentItemsSuspend(contentType) })

    /** Favourites of [contentType], by name as each source sorts its own. */
    suspend fun favorites(contentType: String): List<MediaItem> = mergeFavorites(each { it.getFavoritesForContentTypeSuspend(contentType) })

    /** The Continue Watching shelf: every source's, newest first, at most [limit]. */
    suspend fun continueWatching(limit: Int = CONTINUE_WATCHING_LIMIT): List<ContinueWatchingItem> =
        mergeContinueWatching(each { it.getContinueWatchingItems(limit) }, limit)

    /** Recent Categories of [contentType], newest first, each with its source. */
    fun recentCategories(contentType: String): List<SourcedRecentCategory> =
        mergeRecentCategories(sources.map { source -> source.providerId to source.repo.getRecentlyViewedCategories(contentType) })

    /**
     * The last channel watched: each source's own (as one source chose it before), the newest of
     * them; null when none is in its source's Recent.
     */
    suspend fun lastWatchedChannel(): MediaItem? = lastPlayedItem(ContentType.LIVE_TV)

    /**
     * The item of [contentType] played last across the sources, as (source, id) — what a list
     * marks as current (Phase 6): each source's own last item, the newest of them. With one source
     * its last item alone, without reading its Recent; null when there is none.
     */
    suspend fun lastPlayed(contentType: String): Pair<Long, String>? =
        if (sources.size == 1) {
            sources.first().let { source -> source.repo.getLastItemId(contentType)?.let { source.providerId to it } }
        } else {
            lastPlayedItem(contentType)?.let { it.providerId to it.id }
        }

    private suspend fun lastPlayedItem(contentType: String): MediaItem? =
        newestLastWatched(each { repo -> repo.getLastItemId(contentType) to repo.getRecentItemsSuspend(contentType) })

    /** Takes a row out of Recent on its own source; null where that source keeps its own history. */
    suspend fun removeFromRecent(
        providerId: Long,
        itemId: String,
        contentType: String,
        seriesId: String? = null,
    ): RecentRemoval? = repoFor(providerId).removeFromRecent(itemId, contentType, seriesId)

    /** Undoes [removeFromRecent], on the source it was made on. */
    suspend fun restoreRecent(removal: RecentRemoval) {
        repoFor(removal.providerId).restoreRecent(removal)
    }

    /** Clear Recent: every source's list of [contentType]; true when any had one to clear. */
    suspend fun clearRecent(contentType: String): Boolean = each { it.clearRecent(contentType) }.any { it }

    // Every source at once, in [sources] order. A source whose read fails leaves its part out
    // rather than the whole list: the screen's own one is the only one allowed to fail it.
    private suspend fun <T> each(read: suspend (MediaRepository) -> T): List<T> =
        coroutineScope {
            sources
                .mapIndexed { index, source ->
                    async {
                        if (index == 0) {
                            read(source.repo)
                        } else {
                            suspendRunCatching { read(source.repo) }
                                .onFailure { Log.w(TAG, "Source ${source.providerId} left out of the merged lists", it) }
                                .getOrNull()
                        }
                    }
                }.awaitAll()
                .filterNotNull()
        }

    companion object {
        private const val TAG = "MergedLists"
        private const val XTREAM = "XTREAM"
        const val CONTINUE_WATCHING_LIMIT = 10

        /**
         * The lists of the merged sections (Phase 6): the profile's Xtream sources, its first one
         * first; null when it has none. Stored data only, so no source's session is waited for —
         * one that is offline holds nothing up.
         */
        suspend fun forSections(context: Context): MergedLists? {
            val container = AppContainer.getInstance(context)
            return browsingProviderId(container, 0L)?.let { withOthers(container, container.getStoredDataRepository(it)) }
        }

        /**
         * The lists of the profile in use around [primary], a repository the screen already holds:
         * it first, then the profile's other Xtream sources' when it is one. The others'
         * repositories don't wait for their session (only stored data is read here); one that can't
         * be opened is left out.
         */
        suspend fun withOthers(
            container: AppContainer,
            primary: MediaRepository,
        ): MergedLists {
            val primaryId = primary.getProviderId()
            val profileSources =
                suspendRunCatching { container.providerRepository.profileSourcesList() }
                    .onFailure { Log.w(TAG, "Profile sources unreadable; lists stay on one source", it) }
                    .getOrNull()
                    .orEmpty()
            val primaryEntity = profileSources.firstOrNull { it.id == primaryId }
            val others =
                if (primaryEntity?.type != XTREAM) {
                    emptyList()
                } else {
                    profileSources
                        .filter { it.type == XTREAM && it.id != primaryId }
                        .mapNotNull { entity ->
                            suspendRunCatching { Source(entity.id, entity.name, container.getStoredDataRepository(entity.id)) }
                                .onFailure { Log.w(TAG, "Source ${entity.id} left out of the merged lists", it) }
                                .getOrNull()
                        }
                }
            val sources = listOf(Source(primaryId, primaryEntity?.name.orEmpty(), primary)) + others
            return MergedLists(
                sources,
                profileSources
                    .mapNotNull { entity ->
                        sources.firstOrNull { it.providerId == entity.id }
                    }.ifEmpty { sources },
            )
        }

        /**
         * The source a list opened on [providerId] is built around: that one when given; for a
         * section (0) the profile's first Xtream source, so Live TV, Movies and TV Shows list every
         * Xtream source's categories (Phase 6). Null for a section when the profile has none (the
         * sections aren't offered then).
         */
        suspend fun browsingProviderId(
            container: AppContainer,
            providerId: Long,
        ): Long? = if (providerId != 0L) providerId else xtreamSourceIds(container).firstOrNull()

        /** The profile in use's Xtream sources, in its order: what the merged lists are built on. */
        suspend fun xtreamSourceIds(container: AppContainer): List<Long> =
            container.providerRepository
                .profileSourcesList()
                .filter { it.type == XTREAM }
                .map { it.id }

        /**
         * [profileId]'s Xtream sources ([xtreamSourceIds]) now and each time they change — one
         * turned on or off, added or deleted — and only then: a list built on them is built again.
         */
        fun xtreamSourceIdChanges(
            container: AppContainer,
            profileId: String,
        ): Flow<List<Long>> =
            container.providerRepository
                .profileSourceSet(profileId)
                .map { sources -> sources.filter { it.type == XTREAM }.map { it.id } }
                .distinctUntilChanged()

        /**
         * Recent across sources: each source's own list (its own size), all of them; rows worth
         * resuming first, then the rest, each part newest first — the rule one source's list
         * follows. Ties keep the sources' order.
         */
        internal fun mergeRecent(lists: List<List<MediaItem>>): List<MediaItem> =
            if (lists.size == 1) {
                lists.first()
            } else {
                lists.flatten().sortedWith(compareByDescending<MediaItem> { it.resumable }.thenByDescending { it.lastPlayedAt })
            }

        /** Favourites across sources: by name, as one source's are. */
        internal fun mergeFavorites(lists: List<List<MediaItem>>): List<MediaItem> =
            if (lists.size == 1) {
                lists.first()
            } else {
                lists.flatten().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }

        /** Continue Watching across sources: newest first, at most [limit]. */
        internal fun mergeContinueWatching(
            lists: List<List<ContinueWatchingItem>>,
            limit: Int,
        ): List<ContinueWatchingItem> =
            if (lists.size == 1) {
                lists.first()
            } else {
                lists.flatten().sortedByDescending { it.lastPlayedAt }.take(limit)
            }

        /** Recent Categories across sources: newest first. */
        internal fun mergeRecentCategories(lists: List<Pair<Long, List<RecentCategory>>>): List<SourcedRecentCategory> =
            lists
                .flatMap { (providerId, categories) -> categories.map { SourcedRecentCategory(providerId, it) } }
                .sortedByDescending { it.category.timestamp }

        /**
         * Of each source's last watched channel ([lastIds] paired with that source's Recent, where
         * it must be — a separator row never counts), the one played last.
         */
        internal fun newestLastWatched(lastIds: List<Pair<String?, List<MediaItem>>>): MediaItem? =
            lastIds
                .mapNotNull { (lastId, recent) -> recent.firstOrNull { it.id == lastId && !it.isCategoryMarker } }
                .maxByOrNull { it.lastPlayedAt }
    }
}

/** A Recent Categories entry and the source whose category it is. */
data class SourcedRecentCategory(
    val providerId: Long,
    val category: RecentCategory,
)
