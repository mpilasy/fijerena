package org.njarasoa.fijerena.core.network.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `/movie/{id}/images` and `/tv/{id}/images`. `logos` is the transparent-PNG wordmark art for
 * the OSD title treatment and the TV detail hero headline; `backdrops` is the hero's full-bleed
 * background — the Xtream catalogue's own backdrop (when it has one) is preferred over neither. */
@Serializable
data class TmdbImagesResponse(
    @SerialName("logos")
    val logos: List<TmdbImage> = emptyList(),
    @SerialName("backdrops")
    val backdrops: List<TmdbImage> = emptyList(),
)

/** One entry from `logos` or `backdrops` — same shape either way. */
@Serializable
data class TmdbImage(
    @SerialName("file_path")
    val filePath: String,
    // Null/blank means "language-neutral" (text-free art, or a backdrop — most backdrops carry
    // no language at all) — TMDB's own preferred pick when a request's language has none of its own.
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
    /** ISO date-time, e.g. `2021-09-15T00:00:00.000Z`. */
    @SerialName("release_date")
    val releaseDate: String? = null,
    /** 1 premiere, 2 limited cinema, 3 cinema, 4 digital, 5 disc, 6 TV. */
    @SerialName("type")
    val type: Int? = null,
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
    // Only on a `search/multi` result: `movie`, `tv` or `person`.
    @SerialName("media_type")
    val mediaType: String? = null,
    // Only on a `/tv/{id}` details response: `Returning Series`, `Ended`, `In Production`…
    @SerialName("status")
    val status: String? = null,
    // The rest only on a details response: a TMDB page shows them (wishlist UX plan → TMDB page as a film page).
    @SerialName("genres")
    val genres: List<TmdbNamed> = emptyList(),
    // Minutes; a film's.
    @SerialName("runtime")
    val runtime: Int? = null,
    // A show's makers.
    @SerialName("created_by")
    val createdBy: List<TmdbNamed> = emptyList(),
    // Only with `append_to_response=credits`.
    @SerialName("credits")
    val credits: TmdbCredits? = null,
    // Only with `append_to_response=videos`.
    @SerialName("videos")
    val videos: TmdbVideos? = null,
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

/** A genre, or a show's maker: only the name is used. */
@Serializable
data class TmdbNamed(
    @SerialName("name")
    val name: String? = null,
)

/** `credits` on a details response: the cast in billing order, and the crew. */
@Serializable
data class TmdbCredits(
    @SerialName("cast")
    val cast: List<TmdbNamed> = emptyList(),
    @SerialName("crew")
    val crew: List<TmdbCrewMember> = emptyList(),
)

@Serializable
data class TmdbCrewMember(
    @SerialName("name")
    val name: String? = null,
    @SerialName("job")
    val job: String? = null,
)

/** `videos` on a details response: trailers, teasers, clips… */
@Serializable
data class TmdbVideos(
    @SerialName("results")
    val results: List<TmdbVideo> = emptyList(),
)

@Serializable
data class TmdbVideo(
    // The site's own id: a YouTube video id when [site] is `YouTube`.
    @SerialName("key")
    val key: String? = null,
    @SerialName("site")
    val site: String? = null,
    // `Trailer`, `Teaser`, `Clip`, `Featurette`…
    @SerialName("type")
    val type: String? = null,
    @SerialName("official")
    val official: Boolean = false,
    @SerialName("iso_639_1")
    val language: String? = null,
)
