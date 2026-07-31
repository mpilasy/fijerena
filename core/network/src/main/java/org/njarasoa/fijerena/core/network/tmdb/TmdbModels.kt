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
