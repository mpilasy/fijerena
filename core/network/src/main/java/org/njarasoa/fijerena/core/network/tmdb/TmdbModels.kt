package org.njarasoa.fijerena.core.network.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.njarasoa.fijerena.core.player.domain.TitleFacts

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
    // A chart's: how many pages it has (TMDB stops at 500).
    @SerialName("total_pages")
    val totalPages: Int = 1,
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
    val createdBy: List<TmdbCreator> = emptyList(),
    // Only with `append_to_response=credits` (a film's, from getDetailsWithCredits).
    @SerialName("credits")
    val credits: TmdbCredits? = null,
    // Only with `append_to_response=aggregate_credits` (a show's, from getDetailsWithCredits): the
    // cast and crew of all seasons, each with their roles or jobs and episode counts.
    @SerialName("aggregate_credits")
    val aggregateCredits: TmdbCredits? = null,
    // Only with `append_to_response=videos`.
    @SerialName("videos")
    val videos: TmdbVideos? = null,
    // The facts a details page lists (TitleFacts); only on a details response.
    @SerialName("original_language")
    val originalLanguage: String? = null,
    @SerialName("origin_country")
    val originCountry: List<String> = emptyList(),
    // A film's; TMDB's own fallback when `origin_country` is empty.
    @SerialName("production_countries")
    val productionCountries: List<TmdbCountry> = emptyList(),
    @SerialName("spoken_languages")
    val spokenLanguages: List<TmdbLanguage> = emptyList(),
    @SerialName("production_companies")
    val productionCompanies: List<TmdbNamed> = emptyList(),
    // A show's.
    @SerialName("networks")
    val networks: List<TmdbNamed> = emptyList(),
    @SerialName("tagline")
    val tagline: String? = null,
    @SerialName("number_of_seasons")
    val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes")
    val numberOfEpisodes: Int? = null,
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

    /** The facts a details page lists. A film's status ("Released") says nothing, so only a show's is kept. */
    val facts: TitleFacts
        get() =
            TitleFacts(
                countries = originCountry.ifEmpty { productionCountries.mapNotNull { it.code } }.filter { it.isNotBlank() },
                originalTitle = (originalTitle ?: originalName)?.takeIf { it.isNotBlank() },
                originalLanguage = originalLanguage?.takeIf { it.isNotBlank() },
                spokenLanguages = spokenLanguages.mapNotNull { it.code?.takeIf(String::isNotBlank) },
                studios = networks.ifEmpty { productionCompanies }.mapNotNull { it.name?.takeIf(String::isNotBlank) },
                creators = createdBy.mapNotNull { it.name?.takeIf(String::isNotBlank) },
                tagline = tagline?.takeIf { it.isNotBlank() },
                status = status?.takeIf { it.isNotBlank() && it != "Released" },
                seasonCount = numberOfSeasons?.takeIf { it > 0 },
                episodeCount = numberOfEpisodes?.takeIf { it > 0 },
            )

    /** Release/air year, or null when the date is missing or not a `yyyy-MM-dd` string. */
    val year: Int?
        get() = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()

    /** The credits a details response has: a film's [credits], else a show's [aggregateCredits]. */
    val anyCredits: TmdbCredits?
        get() = credits ?: aggregateCredits
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

@Serializable
data class TmdbCountry(
    @SerialName("iso_3166_1")
    val code: String? = null,
)

@Serializable
data class TmdbLanguage(
    @SerialName("iso_639_1")
    val code: String? = null,
)

/** A genre, or a show's maker: only the name is used. */
@Serializable
data class TmdbNamed(
    @SerialName("name")
    val name: String? = null,
)

/** A show's maker (`created_by`). */
@Serializable
data class TmdbCreator(
    @SerialName("id")
    val id: Int? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("profile_path")
    val profilePath: String? = null,
)

/**
 * `credits` or `aggregate_credits` on a details response: the cast in billing order, and the crew.
 * The two have the same shape, except that `aggregate_credits` gives a list of roles (cast) or jobs
 * (crew) with episode counts in place of one `character` or `job`.
 */
@Serializable
data class TmdbCredits(
    @SerialName("cast")
    val cast: List<TmdbCastMember> = emptyList(),
    @SerialName("crew")
    val crew: List<TmdbCrewMember> = emptyList(),
)

@Serializable
data class TmdbCastMember(
    // The TMDB person id.
    @SerialName("id")
    val id: Int? = null,
    @SerialName("name")
    val name: String? = null,
    // `credits` only.
    @SerialName("character")
    val character: String? = null,
    @SerialName("profile_path")
    val profilePath: String? = null,
    // `aggregate_credits` only.
    @SerialName("roles")
    val roles: List<TmdbRole> = emptyList(),
    @SerialName("total_episode_count")
    val totalEpisodeCount: Int? = null,
)

/** One role of a show's cast member (`aggregate_credits`). */
@Serializable
data class TmdbRole(
    @SerialName("character")
    val character: String? = null,
    @SerialName("episode_count")
    val episodeCount: Int = 0,
)

@Serializable
data class TmdbCrewMember(
    // The TMDB person id.
    @SerialName("id")
    val id: Int? = null,
    @SerialName("name")
    val name: String? = null,
    // `credits` only.
    @SerialName("job")
    val job: String? = null,
    @SerialName("department")
    val department: String? = null,
    @SerialName("profile_path")
    val profilePath: String? = null,
    // `aggregate_credits` only.
    @SerialName("jobs")
    val jobs: List<TmdbJob> = emptyList(),
    @SerialName("total_episode_count")
    val totalEpisodeCount: Int? = null,
)

/** One job of a show's crew member (`aggregate_credits`). */
@Serializable
data class TmdbJob(
    @SerialName("job")
    val job: String? = null,
    @SerialName("episode_count")
    val episodeCount: Int = 0,
)

/** `person/{id}`, with `combined_credits` when it is appended. */
@Serializable
data class TmdbPerson(
    @SerialName("id")
    val id: Int = 0,
    @SerialName("name")
    val name: String? = null,
    @SerialName("profile_path")
    val profilePath: String? = null,
    // `yyyy-MM-dd`.
    @SerialName("birthday")
    val birthday: String? = null,
    @SerialName("deathday")
    val deathday: String? = null,
    @SerialName("place_of_birth")
    val placeOfBirth: String? = null,
    @SerialName("biography")
    val biography: String? = null,
    @SerialName("combined_credits")
    val combinedCredits: TmdbCombinedCredits? = null,
)

/** A person's films and shows: as an actor ([cast]) and as crew ([crew]). */
@Serializable
data class TmdbCombinedCredits(
    @SerialName("cast")
    val cast: List<TmdbPersonCredit> = emptyList(),
    @SerialName("crew")
    val crew: List<TmdbPersonCredit> = emptyList(),
)

/** One film or show of a person. A film has `title` and `release_date`, a show `name` and `first_air_date`. */
@Serializable
data class TmdbPersonCredit(
    // The title's TMDB id.
    @SerialName("id")
    val id: Int,
    // `movie` or `tv`.
    @SerialName("media_type")
    val mediaType: String? = null,
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
    @SerialName("popularity")
    val popularity: Double = 0.0,
    @SerialName("vote_count")
    val voteCount: Int = 0,
    @SerialName("genre_ids")
    val genreIds: List<Int> = emptyList(),
    // Cast only.
    @SerialName("character")
    val character: String? = null,
    // A show's cast only: the episodes the person plays [character] in.
    @SerialName("episode_count")
    val episodeCount: Int = 0,
    // Crew only.
    @SerialName("job")
    val job: String? = null,
    @SerialName("department")
    val department: String? = null,
) {
    /** The title in the shape the catalogue matching takes. */
    fun toRecommendation(): TmdbRecommendation =
        TmdbRecommendation(
            id = id,
            title = title,
            name = name,
            originalTitle = originalTitle,
            originalName = originalName,
            releaseDate = releaseDate,
            firstAirDate = firstAirDate,
            posterPath = posterPath,
            overview = overview,
            mediaType = mediaType,
        )
}

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
