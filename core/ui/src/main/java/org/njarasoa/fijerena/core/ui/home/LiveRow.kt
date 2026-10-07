package org.njarasoa.fijerena.core.ui.home

import org.njarasoa.fijerena.core.player.domain.MediaItem

/**
 * A channel on Home's Live row (TV home overhaul plan, Phase 4). [fromFavorites] picks the list it
 * zaps through once opened: Favorites for a favourite, Recent for the last-watched and recent ones.
 */
data class LiveRowEntry(
    val item: MediaItem,
    val fromFavorites: Boolean,
    val lastWatched: Boolean,
)

/**
 * The Live row: the last channel watched first, then favourite channels, then recent ones, each
 * channel once (the first place it appears wins, so a favourite that is also recent is a
 * favourite), at most [max].
 */
fun mergeLiveRow(
    lastItemId: String?,
    recent: List<MediaItem>,
    favorites: List<MediaItem>,
    max: Int = LIVE_ROW_MAX,
): List<LiveRowEntry> {
    val entries = mutableListOf<LiveRowEntry>()
    val seen = HashSet<String>()
    val last = lastItemId?.let { id -> recent.firstOrNull { it.id == id } ?: favorites.firstOrNull { it.id == id } }
    if (last != null) {
        entries += LiveRowEntry(last, fromFavorites = false, lastWatched = true)
        seen += last.id
    }
    for (item in favorites) if (seen.add(item.id)) entries += LiveRowEntry(item, fromFavorites = true, lastWatched = false)
    for (item in recent) if (seen.add(item.id)) entries += LiveRowEntry(item, fromFavorites = false, lastWatched = false)
    return entries.take(max)
}

const val LIVE_ROW_MAX = 20
