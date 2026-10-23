package org.njarasoa.fijerena.core.ui.home

import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.isCategoryMarker
import org.njarasoa.fijerena.core.player.domain.sourceKey

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
 * Recent mixes the profile's sources: a channel is once per source (the same id on two sources is
 * two channels), and [lastSource], when given, says which source's [lastItemId] it is.
 */
fun mergeLiveRow(
    lastItemId: String?,
    recent: List<MediaItem>,
    max: Int = LIVE_ROW_MAX,
    lastSource: Long? = null,
): List<LiveRowEntry> {
    val entries = mutableListOf<LiveRowEntry>()
    // Provider separator rows (`#### SPORTS ####`) aren't channels: one tuned before they became
    // headings could still sit in Recent and come back here as "Last watched".
    val seen = recent.filter { it.isCategoryMarker }.mapTo(HashSet()) { it.sourceKey }
    val last =
        lastItemId
            ?.let { id ->
                recent.firstOrNull { it.id == id && (lastSource == null || it.providerId == lastSource) }
            }?.takeUnless { it.sourceKey in seen }
    if (last != null) {
        entries += LiveRowEntry(last, fromFavorites = false, lastWatched = true)
        seen += last.sourceKey
    }
    for (item in recent) if (seen.add(item.sourceKey)) entries += LiveRowEntry(item, fromFavorites = false, lastWatched = false)
    return entries.take(max)
}

/** The Favorite channels row: the favourite channels in their own order, each once, separator rows left out. */
fun favoriteChannelsRow(favorites: List<MediaItem>): List<LiveRowEntry> =
    favorites
        .filterNot { it.isCategoryMarker }
        .distinctBy { it.sourceKey }
        .map { LiveRowEntry(it, fromFavorites = true, lastWatched = false) }

const val LIVE_ROW_MAX = 20
