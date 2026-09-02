package org.njarasoa.fijerena.core.player.domain

/**
 * The related titles a detail screen shows, each already filtered down to what the provider
 * carries.
 */
data class RelatedTitles(
    /** Other movies in the same TMDB collection (e.g. a franchise/trilogy). Movies only. */
    val collection: List<MediaItem> = emptyList(),
    /** The collection's TMDB name (e.g. "The Matrix Collection"); null whenever [collection] is empty. */
    val collectionName: String? = null,
    /**
     * TMDB's recommendations ("people who watched this watched that"), topped up with its
     * similar list (keyword/genre overlap) only when recommendations alone fall short. Capped at
     * [MAX_MORE_LIKE_THIS] items total.
     */
    val moreLikeThis: List<MediaItem> = emptyList(),
) {
    companion object {
        const val MAX_MORE_LIKE_THIS = 5
    }
}
