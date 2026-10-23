package org.njarasoa.fijerena.core.ui.viewmodels

import org.njarasoa.fijerena.core.network.wanted.TmdbTitle
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.SearchResult
import java.util.Locale

// Search's Decade, Genre and Language filter on TV and phone
// (the search decade genre filter plan).

/**
 * The decades, genres and languages chosen in Search's filter; a result matches any chosen decade,
 * any chosen genre and any chosen language.
 */
data class SearchFacetChoice(
    val decades: Set<Int> = emptySet(),
    /** [genreKey]s. */
    val genres: Set<String> = emptySet(),
    /** [ProviderCode.language]s. */
    val languages: Set<String> = emptySet(),
    /** [ProviderCode.region]s. */
    val regions: Set<String> = emptySet(),
) {
    /** A choice is made: results without the data it needs (categories, and channels for a year or genre) are left out. */
    val isActive: Boolean
        get() = decades.isNotEmpty() || genres.isNotEmpty() || languages.isNotEmpty() || regions.isNotEmpty()
}

/** One choice of a section: its value, its label and how many results it has. */
data class FacetCount<T>(
    val value: T,
    val label: String,
    val count: Int,
)

/** The Decade, Genre and Language sections' choices for a search's results. */
data class SearchFacets(
    val decades: List<FacetCount<Int>> = emptyList(),
    val genres: List<FacetCount<String>> = emptyList(),
    /** Labelled with the key: the screen names it ([languageLabel]). */
    val languages: List<FacetCount<String>> = emptyList(),
    /** Labelled with the key: the screen names it ([regionLabel]). */
    val regions: List<FacetCount<String>> = emptyList(),
) {
    val isEmpty: Boolean
        get() = decades.isEmpty() && genres.isEmpty() && languages.isEmpty() && regions.isEmpty()
}

/** The release year: the item's own, its release date's, else the year in its name ("Dune (2021)"). */
fun MediaItem.searchYear(): Int? =
    metadata.year
        ?: metadata.releaseDate
            ?.take(YEAR_LENGTH)
            ?.toIntOrNull()
            ?.takeIf { it in FIRST_YEAR..LAST_YEAR }
        ?: parseDisplayTitle(name).year

/** The catalogue's genre text as genres: "drama, Crime / Thriller" → Drama, Crime, Thriller. */
fun splitGenres(raw: String?): List<String> =
    raw
        .orEmpty()
        .split(',', '/', '|', '&', ';')
        .map { genre -> genre.trim().replaceFirstChar { it.uppercase() } }
        .filter { it.isNotEmpty() }
        .distinctBy(::genreKey)

/** A genre's key: the same genre in any case is one choice. */
fun genreKey(genre: String): String = genre.lowercase()

fun decadeOf(year: Int): Int = year / DECADE * DECADE

/**
 * What a provider's code in front of a name ("EN - Dune", "GR| …", "IN-PREM| …") says: the
 * [language] (an ISO 639 code in lower case, "en"; or the code itself in upper case for a tag
 * such as "NF") and the [region] (an ISO 3166 country code, "GR", or one of [CUSTOM_REGIONS],
 * "AF" Africa). A pure language code ("EN") has no region; a country with one main language
 * ("US", "GR") gives both; a country or region of many ("IN", "CA", "AF") only a region.
 */
data class ProviderCode(
    val language: String?,
    val region: String?,
)

fun providerCode(name: String): ProviderCode? {
    val badge = parseDisplayTitle(name).badge?.uppercase() ?: return null
    val code = badge.substringBefore('-').trim().ifEmpty { badge }
    if (code in TAGS) return ProviderCode(code, null)
    val region =
        when {
            code == "UK" -> "GB"
            code in CUSTOM_REGIONS || code in COUNTRY_LANGUAGES || code in REGION_ONLY -> code
            code.length == 2 && code in ISO_COUNTRIES && code.lowercase() !in ISO_LANGUAGES -> code
            else -> null
        }
    val language = COUNTRY_LANGUAGES[code] ?: if (region != null) null else languageOfCode(code) ?: code
    return ProviderCode(language, region)
}

private fun languageOfCode(code: String): String? {
    val lower = code.lowercase()
    return when (lower.length) {
        2 -> lower.takeIf { it in ISO_LANGUAGES }
        3 -> BIBLIOGRAPHIC_LANGUAGES[lower] ?: ISO3_LANGUAGES[lower]
        else -> null
    }
}

/**
 * A [ProviderCode.region]'s label: [customLabel]'s for one of [CUSTOM_REGIONS] (the screen's
 * string), else the country's name in [locale] ("Greece", "Grèce"), else the code.
 */
fun regionLabel(
    key: String,
    locale: Locale,
    customLabel: (String) -> String?,
): String =
    customLabel(key)
        ?: Locale
            .Builder()
            .setRegion(key)
            .build()
            .getDisplayCountry(locale)
            .ifEmpty { key }

/** A [ProviderCode.language]'s label: the language's name in [locale] ("English", "anglais"), else the code. */
fun languageLabel(
    key: String,
    locale: Locale,
): String =
    if (key.any { it.isUpperCase() }) {
        key
    } else {
        Locale
            .forLanguageTag(key)
            .getDisplayLanguage(locale)
            .replaceFirstChar { it.titlecase(locale) }
            .ifEmpty { key }
    }

/** [choice] without the decades and genres that [results] and [tmdb] don't have: a new search drops them. */
fun effectiveFacetChoice(
    choice: SearchFacetChoice,
    results: List<SearchResult>,
    tmdb: List<TmdbTitle>,
): SearchFacetChoice {
    if (!choice.isActive) return choice
    val decades = (results.mapNotNull { it.year } + tmdb.mapNotNull { it.year }).map(::decadeOf).toSet()
    val genres = results.flatMap { result -> result.genres.map(::genreKey) }.toSet()
    val languages = results.mapNotNull { it.language }.toSet()
    val regions = results.mapNotNull { it.region }.toSet()
    return SearchFacetChoice(
        choice.decades intersect decades,
        choice.genres intersect genres,
        choice.languages intersect languages,
        choice.regions intersect regions,
    )
}

fun SearchResult.matches(choice: SearchFacetChoice): Boolean =
    (choice.decades.isEmpty() || year?.let { decadeOf(it) in choice.decades } == true) &&
        (choice.genres.isEmpty() || genres.any { genreKey(it) in choice.genres }) &&
        (choice.languages.isEmpty() || language in choice.languages) &&
        (choice.regions.isEmpty() || region in choice.regions)

/** A TMDB title has a year but no genre names or provider code: a genre, language or region choice leaves it out. */
fun TmdbTitle.matches(choice: SearchFacetChoice): Boolean =
    (choice.decades.isEmpty() || year?.let { decadeOf(it) in choice.decades } == true) &&
        choice.genres.isEmpty() &&
        choice.languages.isEmpty() &&
        choice.regions.isEmpty()

/**
 * The choices of [results] and [tmdb], each section counting what the other sections' [choice]
 * leaves. A chosen value stays listed with no results, so it can be cleared. Decades newest first;
 * genres, languages and regions by count, then by name (a language or region by its key).
 */
fun searchFacets(
    results: List<SearchResult>,
    tmdb: List<TmdbTitle>,
    choice: SearchFacetChoice,
): SearchFacets {
    val forDecades = choice.copy(decades = emptySet())
    val forGenres = choice.copy(genres = emptySet())
    val forLanguages = choice.copy(languages = emptySet())
    val forRegions = choice.copy(regions = emptySet())
    val decadeCounts =
        (
            results.filter { it.matches(forDecades) }.mapNotNull { it.year } +
                tmdb.filter { it.matches(forDecades) }.mapNotNull { it.year }
        ).groupingBy(::decadeOf).eachCount()
    val decades =
        (decadeCounts.keys + choice.decades)
            .sortedDescending()
            .map { FacetCount(it, it.toString(), decadeCounts[it] ?: 0) }

    val labels = mutableMapOf<String, String>()
    val genreCounts = mutableMapOf<String, Int>()
    for (result in results) {
        for (genre in result.genres) labels.getOrPut(genreKey(genre)) { genre }
        if (!result.matches(forGenres)) continue
        for (genre in result.genres) genreCounts.merge(genreKey(genre), 1, Int::plus)
    }
    val genres =
        (genreCounts.keys + choice.genres.filter { it in labels })
            .distinct()
            .map { FacetCount(it, labels.getValue(it), genreCounts[it] ?: 0) }
            .sortedWith(compareByDescending<FacetCount<String>> { it.count }.thenBy { it.label.lowercase() })

    fun codeFacet(
        chosen: Set<String>,
        without: SearchFacetChoice,
        key: (SearchResult) -> String?,
    ): List<FacetCount<String>> {
        val known = results.mapNotNull(key).toSet()
        val counts =
            results
                .filter { it.matches(without) }
                .mapNotNull(key)
                .groupingBy { it }
                .eachCount()
        return (counts.keys + chosen.filter { it in known })
            .distinct()
            .map { FacetCount(it, it, counts[it] ?: 0) }
            .sortedWith(compareByDescending<FacetCount<String>> { it.count }.thenBy { it.value.lowercase() })
    }
    val languages = codeFacet(choice.languages, forLanguages) { it.language }
    val regions = codeFacet(choice.regions, forRegions) { it.region }
    return SearchFacets(decades, genres, languages, regions)
}

/** The active filter in a few words ("panel-a · 1990s, 2000s · Drama · English · Greece"), or null with none. */
fun filterSummary(
    sourceName: String?,
    facets: SearchFacets,
    choice: SearchFacetChoice,
    decadeLabel: (Int) -> String,
    languageLabel: (String) -> String,
    regionLabel: (String) -> String,
): String? {
    val decades = choice.decades.sortedDescending().joinToString(", ", transform = decadeLabel)
    val genres = facets.genres.filter { it.value in choice.genres }.joinToString(", ") { it.label }
    val languages =
        choice.languages
            .map(languageLabel)
            .sorted()
            .joinToString(", ")
    val regions =
        choice.regions
            .map(regionLabel)
            .sorted()
            .joinToString(", ")
    return listOfNotNull(
        sourceName,
        decades.ifEmpty { null },
        genres.ifEmpty { null },
        languages.ifEmpty { null },
        regions.ifEmpty { null },
    ).joinToString(" · ")
        .ifEmpty { null }
}

private const val YEAR_LENGTH = 4
private const val FIRST_YEAR = 1880
private const val LAST_YEAR = 2100
private const val DECADE = 10

private val ISO_LANGUAGES = Locale.getISOLanguages().toSet()
private val ISO_COUNTRIES = Locale.getISOCountries().toSet()
private val ISO3_LANGUAGES = Locale.getISOLanguages().associateBy { Locale.forLanguageTag(it).isO3Language }

/** ISO 639-2 bibliographic codes that differ from the terminology ones Java knows. */
private val BIBLIOGRAPHIC_LANGUAGES =
    mapOf(
        "fre" to "fr",
        "ger" to "de",
        "dut" to "nl",
        "gre" to "el",
        "per" to "fa",
        "alb" to "sq",
        "chi" to "zh",
        "cze" to "cs",
        "rum" to "ro",
        "slo" to "sk",
        "arm" to "hy",
        "geo" to "ka",
        "mac" to "mk",
        "ice" to "is",
    )

/** Provider codes that name a country with one main language: they give both. */
private val COUNTRY_LANGUAGES =
    mapOf(
        "US" to "en",
        "UK" to "en",
        "GB" to "en",
        "AU" to "en",
        "NZ" to "en",
        "IE" to "en",
        "GR" to "el",
        "IR" to "fa",
        "AL" to "sq",
        "SE" to "sv",
        "DK" to "da",
        "CZ" to "cs",
        "UA" to "uk",
        "JP" to "ja",
        "KR" to "ko",
        "CN" to "zh",
        "TW" to "zh",
        "HK" to "zh",
        "BR" to "pt",
        "MX" to "es",
        "AT" to "de",
        "RS" to "sr",
        "SI" to "sl",
        "EE" to "et",
        "VN" to "vi",
        "IL" to "he",
        "PK" to "ur",
        "EG" to "ar",
        "BG" to "bg",
        "PH" to "tl",
    )

/**
 * Regions named by the screen, not by ISO: Africa, ex-Yugoslavia, Latin America, Asia ("ASIA
 * MOVIES") and the Nordic countries ("SC-DO", "NORDIC SERIE"); AS, AF and SC are also ISO codes
 * (American Samoa, Afghanistan, Seychelles) or a language's (Assamese, Afrikaans, Sardinian).
 */
val CUSTOM_REGIONS = setOf("AF", "EX", "LAT", "AS", "SC")

/** Countries of many languages, some with a code that is also a language's (CA Catalan, CH Chamorro, BE Belarusian). */
private val REGION_ONLY = setOf("IN", "CA", "CH", "BE", "SA")

/** Tags in front of a name that are neither a language nor a region; shown as they are under Language. */
private val TAGS = setOf("NF", "VIP", "MULTI")
