package org.njarasoa.fijerena.core.player.domain

import androidx.compose.runtime.Immutable

/**
 * What a browse row points at — the thing selecting it should open.
 *
 * Rows used to say this through `providerData`, a `Map<String, String>` the nav hosts read keys
 * out of: `resumeSeries`, `episodeId`, `isCategoryRef`. A missing key never failed, it just
 * selected a different screen, which is how an episode id once reached the series screen and had
 * its episodes asked for. Stated as a type, the same mistake is a compile error, and a new kind of
 * row breaks the build at every `when` that has to learn about it.
 *
 * Ids stay `String` here; making them distinct types is its own change.
 */
@Immutable
sealed interface BrowseTarget {
    /** A live channel. */
    data class Channel(val streamId: String) : BrowseTarget

    /** A film, which opens its detail screen. */
    data class Movie(val movieId: String) : BrowseTarget

    /**
     * A show, which opens its episode list. [resumeEpisodeId] is the episode to open the detail
     * panel on — set when the row came from watch history.
     */
    data class Series(val seriesId: String, val resumeEpisodeId: String? = null) : BrowseTarget

    /**
     * One episode, which plays. [seriesId] and [seriesName] are what the player reports progress
     * against; both are absent on history rows written before they were recorded, and an episode
     * that cannot name its show still plays.
     */
    data class Episode(
        val episodeId: String,
        val seriesId: String? = null,
        val seriesName: String? = null,
        val extension: String? = null,
    ) : BrowseTarget

    /** Not content at all: a row standing for a category, which browses into it. */
    data class CategoryRef(val categoryId: String) : BrowseTarget
}

/**
 * What selecting this row opens. Rows built from watch history or from a virtual category carry
 * [MediaItem.target] themselves; a plain catalogue row does not, and is whatever its content type
 * says it is — the one place that assumption is written down.
 */
fun MediaItem.browseTarget(contentType: String): BrowseTarget = target ?: browseTargetFor(contentType, id)

/** What a row with only an id to go on opens — see [browseTarget]. */
fun browseTargetFor(
    contentType: String,
    itemId: String,
): BrowseTarget =
    when (contentType) {
        ContentType.MOVIES -> BrowseTarget.Movie(itemId)
        ContentType.TV_SHOWS -> BrowseTarget.Series(itemId)
        else -> BrowseTarget.Channel(itemId)
    }
