package org.njarasoa.fijerena.core.player.domain

import androidx.compose.runtime.Immutable

/**
 * What a browse row points at: the thing selecting it opens.
 *
 * As a type, not `providerData` keys (`resumeSeries`, `episodeId`, `isCategoryRef`): a missing key
 * never failed, it opened another screen (an episode id once reached the series screen). Now that
 * mistake is a compile error, and a new kind of row breaks the build at every `when` that must
 * handle it.
 *
 * Ids are [SeriesId] / [EpisodeId] where the two could be swapped; the rest stay raw.
 */
@Immutable
sealed interface BrowseTarget {
    /** A live channel. */
    data class Channel(
        val streamId: String,
    ) : BrowseTarget

    /** A film, which opens its detail screen. */
    data class Movie(
        val movieId: String,
    ) : BrowseTarget

    /**
     * A show, which opens its episode list. [resumeEpisodeId] is the episode to open the detail
     * panel on — set when the row came from watch history.
     */
    data class Series(
        val seriesId: SeriesId,
        val resumeEpisodeId: EpisodeId? = null,
    ) : BrowseTarget

    /**
     * One episode, which plays. [seriesId] and [seriesName] are what the player reports progress
     * against; both are absent on history rows written before they were recorded, and an episode
     * that cannot name its show still plays.
     */
    data class Episode(
        val episodeId: EpisodeId,
        val seriesId: SeriesId? = null,
        val seriesName: String? = null,
        val extension: String? = null,
    ) : BrowseTarget

    /** Not content at all: a row standing for a category, which browses into it. */
    data class CategoryRef(
        val categoryId: String,
    ) : BrowseTarget
}

/**
 * What selecting this row opens. Rows built from watch history or a virtual category carry
 * [MediaItem.target]; a plain catalogue row does not, and is what its content type says (this is
 * the one place that assumption is written).
 */
fun MediaItem.browseTarget(contentType: String): BrowseTarget = target ?: browseTargetFor(contentType, id)

/** The show this row stands for (a TV Shows catalogue row, or a Recent card for a show), else null. */
fun MediaItem.showSeriesId(contentType: String): String? = (browseTarget(contentType) as? BrowseTarget.Series)?.seriesId?.raw

/** What a row with only an id to go on opens — see [browseTarget]. */
fun browseTargetFor(
    contentType: String,
    itemId: String,
): BrowseTarget =
    when (contentType) {
        ContentType.MOVIES -> BrowseTarget.Movie(itemId)
        ContentType.TV_SHOWS -> BrowseTarget.Series(SeriesId(itemId))
        else -> BrowseTarget.Channel(itemId)
    }
