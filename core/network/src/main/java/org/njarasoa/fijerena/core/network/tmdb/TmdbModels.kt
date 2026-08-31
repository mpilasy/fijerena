package org.njarasoa.fijerena.core.network.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `/movie/{id}/images` and `/tv/{id}/images`. Only `logos` is used — the transparent-PNG
 * wordmark art for the OSD title treatment; posters/backdrops come from the Xtream catalogue. */
@Serializable
data class TmdbImagesResponse(
    @SerialName("logos")
    val logos: List<TmdbLogo> = emptyList(),
)

@Serializable
data class TmdbLogo(
    @SerialName("file_path")
    val filePath: String,
    // Null/blank means "language-neutral" (text-free or the studio's international mark) —
    // TMDB's own preferred pick when a request's language has no logo of its own.
    @SerialName("iso_639_1")
    val language: String? = null,
    @SerialName("width")
    val width: Int = 0,
    @SerialName("vote_average")
    val voteAverage: Double = 0.0,
)

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
    // Only present on a `/movie/{id}` details response, and only when the movie is part of a
    // franchise. Null for every other use of this shape (recommendations, similar, TV).
    @SerialName("belongs_to_collection")
    val belongsToCollection: TmdbCollectionRef? = null,
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

/** The collection a movie belongs to, as embedded in a `/movie/{id}` response. */
@Serializable
data class TmdbCollectionRef(
    @SerialName("id")
    val id: Int,
    @SerialName("name")
    val name: String? = null,
)

/** `/collection/{id}`: the franchise's name and every movie in it. */
@Serializable
data class TmdbCollectionResponse(
    @SerialName("id")
    val id: Int,
    @SerialName("name")
    val name: String? = null,
    // Each part has the same shape as a `/movie/{id}` result (id, title, original_title,
    // poster_path, release_date, overview, vote_average).
    @SerialName("parts")
    val parts: List<TmdbRecommendation> = emptyList(),
)
