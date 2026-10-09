package org.njarasoa.fijerena.core.ui.home

import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.isCategoryMarker

/**
 * A channel on one of Home's Live rows (TV home overhaul plan, Phase 4). [fromFavorites] picks the
 * list it zaps through once opened: Favorites for the Favorite channels row, Recent for the
 * Channels row.
 */
data class LiveRowEntry(
    val item: MediaItem,
    val fromFavorites: Boolean,
    val lastWatched: Boolean,
)

/**
 * The Channels row: the last channel watched first, then Recent in its own order (newest first),
 * each channel once, at most [max]. Favourites have their own row ([favoriteChannelsRow]).
 */
fun mergeLiveRow(
    lastItemId: String?,
    recent: List<MediaItem>,
    max: Int = LIVE_ROW_MAX,
): List<LiveRowEntry> {
    val entries = mutableListOf<LiveRowEntry>()
    // Provider separator rows (`#### SPORTS ####`) aren't channels: one tuned before they became
    // headings could still sit in Recent and come back here as "Last watched".
    val seen = recent.filter { it.isCategoryMarker }.mapTo(HashSet()) { it.id }
    val last = lastItemId?.takeIf { it !in seen }?.let { id -> recent.firstOrNull { it.id == id } }
    if (last != null) {
        entries += LiveRowEntry(last, fromFavorites = false, lastWatched = true)
        seen += last.id
    }
    for (item in recent) if (seen.add(item.id)) entries += LiveRowEntry(item, fromFavorites = false, lastWatched = false)
    return entries.take(max)
}

/** The Favorite channels row: the favourite channels in their own order, each once, separator rows left out. */
fun favoriteChannelsRow(favorites: List<MediaItem>): List<LiveRowEntry> =
    favorites
        .filterNot { it.isCategoryMarker }
        .distinctBy { it.id }
        .map { LiveRowEntry(it, fromFavorites = true, lastWatched = false) }

const val LIVE_ROW_MAX = 20
