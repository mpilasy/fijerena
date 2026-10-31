package org.njarasoa.fijerena.core.network.tmdb

import org.njarasoa.fijerena.core.network.wanted.TmdbOnlyItem
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType

/**
 * A person that Search's People type found on TMDB: the photo ([profilePath]), the name, and up to
 * [PEOPLE_SEARCH_KNOWN_FOR] titles the person is known for, each in the list title rule
 * ([tmdbListTitle]). See the people search plan.
 */
data class FoundPerson(
    val id: Int,
    val name: String,
    val profilePath: String?,
    val knownFor: List<String>,
)

/**
 * The People type's three groups, people first, each in TMDB's order. [movies] and [shows] are the
 * source's titles (`MediaItem.providerId` names the source) and TMDB-only cards (`TmdbOnlyItem`)
 * for the titles no source has. A child profile never gets the People type.
 */
data class PeopleSearchResults(
    val people: List<FoundPerson> = emptyList(),
    val movies: List<MediaItem> = emptyList(),
    val shows: List<MediaItem> = emptyList(),
) {
    val isEmpty: Boolean get() = people.isEmpty() && movies.isEmpty() && shows.isEmpty()
}

/** The most titles a found person shows that they are known for. */
const val PEOPLE_SEARCH_KNOWN_FOR = 2

private const val PERSON = "person"
private const val MOVIE = "movie"
private const val TV = "tv"
private const val ACTING = "Acting"

/**
 * The actors and actresses among [results] (`search/multi`), in TMDB's order: a person whose
 * department is Acting, with a name. Directors, writers and the rest are left out.
 */
fun foundPeople(results: List<TmdbRecommendation>): List<FoundPerson> =
    results
        .filter { it.mediaType == PERSON && it.knownForDepartment == ACTING }
        .mapNotNull { result ->
            result.displayTitle?.let { name ->
                FoundPerson(
                    id = result.id,
                    name = name,
                    profilePath = result.profilePath?.takeIf { it.isNotBlank() },
                    knownFor =
                        result.knownFor
                            .mapNotNull { it.listTitle }
                            .distinct()
                            .take(PEOPLE_SEARCH_KNOWN_FOR),
                )
            }
        }

/**
 * The People type's groups for [results] (`search/multi`): the actors ([foundPeople]), then the
 * films and the shows, each matched to [sources] as the actor pages are (the first source in the
 * profile's order that has it; `MediaRepository.findTmdbTitles`, so the profile's filters apply).
 * A title no source has is a TMDB-only card in its place. Makes no TMDB call.
 */
suspend fun peopleSearchResults(
    results: List<TmdbRecommendation>,
    sources: List<PersonTitleSource>,
): PeopleSearchResults {
    suspend fun group(
        mediaType: String,
        contentType: String,
        itemType: MediaType,
    ): List<MediaItem> {
        val titles = results.filter { it.mediaType == mediaType }
        val typed = sources.map { source -> suspend { list: List<TmdbRecommendation> -> source(list, contentType) } }
        return if (titles.isEmpty()) emptyList() else titlesWithMissing(titles, typed, missing = { TmdbOnlyItem.of(it, itemType) })
    }
    return PeopleSearchResults(
        people = foundPeople(results),
        movies = group(MOVIE, ContentType.MOVIES, MediaType.MOVIE),
        shows = group(TV, ContentType.TV_SHOWS, MediaType.SERIES),
    )
}
