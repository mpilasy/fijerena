package org.njarasoa.fijerena.core.player.domain

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a film or show page tells about the title besides its plot and its people: from TMDB's
 * details call, or from the source itself (Jellyfin, an Xtream `country`). Every part is empty
 * when nobody sent it. Stored as JSON in the detail cache, so a cached page shows it too.
 */
@Immutable
@Serializable
data class TitleFacts(
    /** ISO 3166 codes ("US") from TMDB, or the names a source sends ("United States"). */
    val countries: List<String> = emptyList(),
    val originalTitle: String? = null,
    /** ISO 639-1 code ("fr"). */
    val originalLanguage: String? = null,
    /** ISO 639-1 codes. */
    val spokenLanguages: List<String> = emptyList(),
    /** A film's studios, or a show's networks. */
    val studios: List<String> = emptyList(),
    /** A show's creators: a show has no director. */
    val creators: List<String> = emptyList(),
    val tagline: String? = null,
    /** A show's status as TMDB ("Returning Series", "Ended"…) or Jellyfin ("Continuing") sends it. */
    val status: String? = null,
    val seasonCount: Int? = null,
    val episodeCount: Int? = null,
    /**
     * TMDB's plot in [textLanguage]. Kept here so a cached page shows it too: it wins over the
     * source's plot, which is often in another language (UX-D4).
     */
    val overview: String? = null,
    /** TMDB's genre names in [textLanguage]; they win over the source's genre, as [overview]. */
    val genres: List<String> = emptyList(),
    /** The TMDB `language` ("fr-FR", "en-US") of [overview] and [genres]; null when TMDB gave no text. */
    val textLanguage: String? = null,
) {
    /** These facts (TMDB's), with [source]'s countries when these have none. */
    fun withCountriesFrom(source: TitleFacts): TitleFacts = if (countries.isEmpty()) copy(countries = source.countries) else this

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The facts [toJson] stored, or empty ones when there are none or they no longer read. */
        fun fromJson(text: String?): TitleFacts =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: TitleFacts()
    }
}
