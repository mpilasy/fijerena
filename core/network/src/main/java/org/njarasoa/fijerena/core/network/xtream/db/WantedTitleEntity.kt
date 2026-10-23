package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity

/**
 * A film or show on a profile's Wanted list: a title TMDB knows that the user wants, whether or
 * not a source has it. Kept per profile and not per provider, because a wanted title belongs to no
 * source. See the wanted titles plan.
 *
 * [contentType] is `MOVIES` or `TV_SHOWS` (`ContentType`); [tmdbId] the TMDB id. [title] (in the
 * app language) and [originalTitle] feed the title + year match for catalogue items without a TMDB
 * id, and [title], [year] and [posterPath] show the card without a network call.
 *
 * Whether a source has the title is not stored: each device computes it from its own catalogue,
 * so devices with different catalogues cannot undo each other through sync. [notifiedAt] (a banner
 * told the user that it is available) and [seenAt] (the user opened it while available) are only
 * ever set, never cleared.
 *
 * Added v27 (the wishlist ux plan): [askedAt] (the "remove it now you've
 * watched it?" prompt was answered, set once), and the title's release as TMDB gives it, refreshed
 * at most once a week for a title no source has: [releaseDate] (first release, ISO day),
 * [digitalDate] (first digital or disc release), [inCinemas] (a cinema release is known),
 * [showStatus] (TMDB's status of a show) and [releaseCheckedAt].
 */
@Entity(
    tableName = "wanted_title",
    primaryKeys = ["profileId", "contentType", "tmdbId"],
)
data class WantedTitleEntity(
    val profileId: String,
    val contentType: String,
    val tmdbId: String,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val posterPath: String? = null,
    val addedAt: Long,
    val notifiedAt: Long? = null,
    val seenAt: Long? = null,
    val askedAt: Long? = null,
    val releaseDate: String? = null,
    val digitalDate: String? = null,
    val inCinemas: Boolean = false,
    val showStatus: String? = null,
    val releaseCheckedAt: Long? = null,
)
