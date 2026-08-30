package org.njarasoa.fijerena.core.network.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbSeasonResponse(
    @SerialName("season_number")
    val seasonNumber: Int? = null,
    @SerialName("episodes")
    val episodes: List<TmdbEpisode> = emptyList(),
)

@Serializable
data class TmdbEpisode(
    @SerialName("id")
    val id: Long? = null,
    @SerialName("episode_number")
    val episodeNumber: Int,
    @SerialName("season_number")
    val seasonNumber: Int? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("overview")
    val overview: String? = null,
    @SerialName("still_path")
    val stillPath: String? = null,
    @SerialName("air_date")
    val airDate: String? = null,
    @SerialName("runtime")
    val runtime: Int? = null,
    @SerialName("vote_average")
    val voteAverage: Double? = null,
)

@Serializable
data class TmdbReleaseDatesResponse(
    @SerialName("results")
    val results: List<TmdbReleaseDatesCountry> = emptyList(),
)

@Serializable
data class TmdbReleaseDatesCountry(
    @SerialName("iso_3166_1")
    val country: String,
    @SerialName("release_dates")
    val releaseDates: List<TmdbReleaseDateEntry> = emptyList(),
)

@Serializable
data class TmdbReleaseDateEntry(
    @SerialName("certification")
    val certification: String? = null,
)

@Serializable
data class TmdbContentRatingsResponse(
    @SerialName("results")
    val results: List<TmdbContentRatingCountry> = emptyList(),
)

@Serializable
data class TmdbContentRatingCountry(
    @SerialName("iso_3166_1")
    val country: String,
    @SerialName("rating")
    val rating: String? = null,
)

/**
 * One page of `/recommendations`. TMDB answers a 404 with a JSON error body rather than a
 * results array, which lands here as an empty list — the caller treats that the same as
 * "nothing to show".
 */
@Serializable
data class TmdbRecommendationsResponse(
    @SerialName("results")
    val results: List<TmdbRecommendation> = emptyList(),
)

/** Movies and TV share this shape apart from the title and date fields, which are named differently. */
@Serializable
data class TmdbRecommendation(
    @SerialName("id")
    val id: Int,
    @SerialName("title")
    val title: String? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("original_title")
    val originalTitle: String? = null,
    @SerialName("original_name")
    val originalName: String? = null,
    @SerialName("release_date")
    val releaseDate: String? = null,
    @SerialName("first_air_date")
    val firstAirDate: String? = null,
    @SerialName("poster_path")
    val posterPath: String? = null,
    @SerialName("overview")
    val overview: String? = null,
    @SerialName("vote_average")
    val voteAverage: Double? = null,
) {
    /** `title` for movies, `name` for TV; null when TMDB sent neither. */
    val displayTitle: String?
        get() = title?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() }

    /** `original_title` for movies, `original_name` for TV; falls back to [displayTitle]. */
    val originalDisplayTitle: String?
        get() =
            originalTitle?.takeIf { it.isNotBlank() }
                ?: originalName?.takeIf { it.isNotBlank() }
                ?: displayTitle

    /** Release/air year, or null when the date is missing or not a `yyyy-MM-dd` string. */
    val year: Int?
        get() = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()
}
