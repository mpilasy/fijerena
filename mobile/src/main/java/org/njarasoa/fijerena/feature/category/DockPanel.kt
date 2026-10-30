package org.njarasoa.fijerena.feature.category

import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.sourceKey
import org.njarasoa.fijerena.core.player.domain.sourceOr
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.isSameChannel

/** Which list the docked Live TV preview's channel panel shows, picked with its tabs. */
internal enum class PreviewListSource { CATEGORY, RECENT, FAVORITES }

/**
 * The category a channel picked from the list docks with (UX-D8): the selected category's key, or
 * null when the list is Recent, Favourites or another list that is not a category.
 */
internal fun dockCategoryKey(
    selectedCategoryId: String?,
    selectedCategoryKey: String?,
): String? =
    selectedCategoryKey?.takeIf {
        selectedCategoryId != null && selectedCategoryId !in CategoryViewModel.VIRTUAL_CATEGORY_IDS
    }

/** The dock's tabs: the category first, as on TV, only when the channel was docked from one. */
internal fun dockTabs(hasCategory: Boolean): List<PreviewListSource> =
    if (hasCategory) {
        PreviewListSource.entries
    } else {
        listOf(PreviewListSource.RECENT, PreviewListSource.FAVORITES)
    }

/** The list the dock shows: [selected], or Recent when the category tab is not there. */
internal fun dockListSource(
    selected: PreviewListSource,
    hasCategory: Boolean,
): PreviewListSource = if (selected == PreviewListSource.CATEGORY && !hasCategory) PreviewListSource.RECENT else selected

/**
 * The key ([sourceKey]) of the channel the dock's loader plays: the stream it resolved on
 * [loaderSource], else the dock's target (still loading, or failed). A zap in full screen moves the
 * loader and not the target, so the Playing mark follows the loader (UX audit finding 5).
 */
internal fun dockPlayingKey(
    target: MediaItem?,
    resolvedStreamId: String?,
    loaderSource: Long,
): String? = target?.let { sourceKey(loaderSource, resolvedStreamId ?: it.id) }

/** Whether [item] is the row of the channel [playingKey] names; a row that leaves its source 0 is on [listSource]. */
internal fun isPlayingRow(
    item: MediaItem,
    playingKey: String?,
    listSource: Long,
): Boolean = playingKey != null && sourceKey(item.sourceOr(listSource), item.id) == playingKey

/**
 * The channel the dock shows when full screen returns to it: the loader's channel ([loaderStreamId],
 * found in [loaderChannels] or made from [target]), or [target] when the loader is still on it.
 */
internal fun dockTargetAfterFullScreen(
    target: MediaItem,
    loaderStreamId: String?,
    loaderStreamName: String?,
    loaderChannels: List<MediaItem>,
): MediaItem =
    when (loaderStreamId) {
        null, target.id -> {
            target
        }

        else -> {
            loaderChannels.firstOrNull { it.id == loaderStreamId }?.let { it.copy(providerId = it.sourceOr(target.providerId)) }
                ?: target.copy(id = loaderStreamId, name = loaderStreamName ?: target.name, thumbnailUrl = null)
        }
    }

/**
 * The channel after [current] in [list] that [canPlay] accepts, wrapping at the end; the first one
 * when [current] is not in the list. Null when no other channel can play (UX-D2's Next).
 */
internal fun nextDockChannel(
    list: List<MediaItem>?,
    current: MediaItem,
    listSource: Long,
    canPlay: (MediaItem) -> Boolean,
): MediaItem? {
    val items = list.orEmpty()
    val start = items.indexOfFirst { it.isSameChannel(current, listSource) }
    return items.indices
        .asSequence()
        .map { step -> items[(start + 1 + step).mod(items.size)] }
        .firstOrNull { !it.isSameChannel(current, listSource) && canPlay(it) }
}
