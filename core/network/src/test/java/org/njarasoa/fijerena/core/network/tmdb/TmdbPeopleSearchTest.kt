package org.njarasoa.fijerena.core.network.tmdb

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.wanted.TmdbOnlyItem
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType

/** Search's People type: `search/multi` read and put in its three groups (the people search plan). */
class TmdbPeopleSearchTest {
    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    // A `search/multi` answer as TMDB sends it (2026-10-31, shortened): an actor, a director, an
    // actress without a photo, a show and two films.
    private val body =
        """
        {"page":1,"total_pages":1,"total_results":6,"results":[
        {"adult":false,"id":31,"name":"Tom Hanks","original_name":"Tom Hanks","media_type":"person",
         "popularity":12.3,"gender":2,"known_for_department":"Acting","profile_path":"/oFvZoKI6lvU03n4YoNGAll9rkas.jpg",
         "known_for":[
          {"id":13,"title":"Forrest Gump","original_title":"Forrest Gump","media_type":"movie","release_date":"1994-06-23"},
          {"id":862,"title":"Toy Story","original_title":"Toy Story","media_type":"movie","release_date":"1995-11-22"},
          {"id":857,"title":"Saving Private Ryan","original_title":"Saving Private Ryan","media_type":"movie"}]},
        {"id":488,"name":"Steven Spielberg","media_type":"person","known_for_department":"Directing",
         "profile_path":"/tZxcg19YQ3e8fJ0pOs7hjlnmmr6.jpg","known_for":[]},
        {"id":99,"name":"Bae Doona","media_type":"person","known_for_department":"Acting","profile_path":null,
         "known_for":[{"id":70523,"name":"Sense8","original_name":"Sense8","media_type":"tv"},
          {"id":11,"title":"The Host","original_title":"괴물","media_type":"movie"}]},
        {"id":316992,"name":"World War II with Tom Hanks","original_name":"World War II with Tom Hanks",
         "media_type":"tv","first_air_date":"2026-05-25","poster_path":"/MPN0glmZV3JnZfmXtKF32SRhDD.jpg"},
        {"id":1128918,"title":"Tom Hanks: The Nomad","original_title":"Tom Hanks: The Nomad","media_type":"movie",
         "release_date":"2023-05-20"},
        {"id":1116589,"title":"Die Tom Hanks Story","original_title":"Die Tom Hanks Story","media_type":"movie",
         "release_date":"2021-07-14"}]}
        """.trimIndent()

    private val results = json.decodeFromString<TmdbRecommendationsResponse>(body).results

    private fun item(name: String) = MediaItem(id = name, name = name, mediaType = MediaType.VIDEO_FILE, categoryId = "c")

    /** A source holding the titles [has]: the content types it is asked for go in [asked]. */
    private val asked = mutableListOf<String>()

    private fun source(vararg has: String): PersonTitleSource =
        { titles, contentType ->
            asked += contentType
            titles.map { t -> t.displayTitle?.takeIf { it in has }?.let(::item) }
        }

    @Test
    fun `a person's photo, department and known-for titles are read`() {
        val hanks = results.first()
        assertEquals("person", hanks.mediaType)
        assertEquals("Acting", hanks.knownForDepartment)
        assertEquals("/oFvZoKI6lvU03n4YoNGAll9rkas.jpg", hanks.profilePath)
        assertEquals(listOf("Forrest Gump", "Toy Story", "Saving Private Ryan"), hanks.knownFor.map { it.displayTitle })
    }

    @Test
    fun `only actors and actresses, in TMDB's order, with two known-for titles at most`() {
        val people = foundPeople(results)
        assertEquals(listOf("Tom Hanks", "Bae Doona"), people.map { it.name })
        assertEquals(listOf("Forrest Gump", "Toy Story"), people[0].knownFor)
        assertEquals(31, people[0].id)
    }

    @Test
    fun `a person without a photo has none, and known-for titles follow the list title rule`() {
        val doona = foundPeople(results)[1]
        assertNull(doona.profilePath)
        // The Korean original is not in Latin letters: the English name.
        assertEquals(listOf("Sense8", "The Host"), doona.knownFor)
    }

    @Test
    fun `films and shows go apart, each matched on its own content type`() =
        runTest {
            val found = peopleSearchResults(results, listOf(source("Tom Hanks: The Nomad")))
            assertEquals(listOf("Tom Hanks: The Nomad", "Die Tom Hanks Story"), found.movies.map { it.listTitle ?: it.name })
            assertEquals(listOf("World War II with Tom Hanks"), found.shows.map { it.listTitle ?: it.name })
            assertEquals(listOf(ContentType.MOVIES, ContentType.TV_SHOWS), asked)
        }

    @Test
    fun `a title no source has is a TMDB-only card in its place`() =
        runTest {
            val found = peopleSearchResults(results, listOf(source("Die Tom Hanks Story")))
            assertEquals(listOf("Tom Hanks: The Nomad", "Die Tom Hanks Story"), found.movies.map { it.listTitle ?: it.name })
            assertTrue(found.movies[0].id.startsWith(TmdbOnlyItem.PREFIX))
            assertFalse(found.movies[1].id.startsWith(TmdbOnlyItem.PREFIX))
            assertEquals(MediaType.SERIES, found.shows.single().mediaType)
            assertEquals("World War II with Tom Hanks", TmdbOnlyItem.titleOf(found.shows.single(), ContentType.TV_SHOWS)?.title)
        }

    @Test
    fun `a title on two sources comes from the first in the profile's order`() =
        runTest {
            val first: PersonTitleSource = { titles, _ -> titles.map { item("first:" + it.displayTitle) } }
            val second: PersonTitleSource = { titles, _ -> titles.map { item("second:" + it.displayTitle) } }
            val found = peopleSearchResults(results, listOf(first, second))
            assertTrue(found.movies.all { it.name.startsWith("first:") })
            assertEquals(2, found.movies.size)
        }

    @Test
    fun `no result gives empty groups`() =
        runTest {
            assertTrue(peopleSearchResults(emptyList(), listOf(source())).isEmpty)
        }
}
