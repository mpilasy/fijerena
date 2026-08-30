package org.njarasoa.fijerena.core.player.domain

/**
 * The two kinds of related title a detail screen shows, each already filtered down to what the
 * provider carries.
 *
 * They are kept apart rather than concatenated because they are not the same claim: TMDB's
 * recommendations are "people who watched this watched that", while its similar list is keyword
 * and genre overlap that always fills a page whether or not anything is genuinely close. Merging
 * them would let the weaker source dilute the stronger one under a single heading.
 */
data class RelatedTitles(
    /** Other movies in the same TMDB collection (e.g. a franchise/trilogy). Movies only. */
    val collection: List<MediaItem> = emptyList(),
    /** The collection's TMDB name (e.g. "The Matrix Collection"); null whenever [collection] is empty. */
    val collectionName: String? = null,
    val recommended: List<MediaItem> = emptyList(),
    val similar: List<MediaItem> = emptyList(),
)
