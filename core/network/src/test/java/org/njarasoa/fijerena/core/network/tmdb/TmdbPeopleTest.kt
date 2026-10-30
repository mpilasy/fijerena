package org.njarasoa.fijerena.core.network.tmdb

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.wanted.TmdbOnlyItem
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import java.io.File
import java.nio.file.Files

/** The people cache and the actor page's rows (docs/plans/20261030_cast-tab-plan.md, Phase 1). */
class TmdbPeopleTest {
    private val dir: File = Files.createTempDirectory("people").toFile()
    private val calls = mutableListOf<Int>()
    private var now = 1_000_000L
    private var language = "en-US"

    private fun people() =
        TmdbPeople(
            dir,
            fetchPerson = { id ->
                calls += id
                TmdbPerson(id = id, name = "Person $id")
            },
            language = { language },
            now = { now },
        )

    @Test
    fun `a person read once is not read again for 7 days, also by a new instance`() =
        runTest {
            people().person(7)
            now += 7 * 24 * 60 * 60 * 1000L - 1
            assertEquals("Person 7", people().person(7).name)
            assertEquals(listOf(7), calls)
        }

    @Test
    fun `7 days old, or in another language, the person is read again`() =
        runTest {
            val people = people()
            people.person(7)
            now += 7 * 24 * 60 * 60 * 1000L
            people.person(7)
            language = "fr-FR"
            people.person(7)
            assertEquals(listOf(7, 7, 7), calls)
        }

    @Test
    fun `after clear, every person is read again, also by a new instance`() =
        runTest {
            val people = people()
            people.person(7)
            people.person(8)
            people.clear()
            people().person(7)
            people.person(8)
            assertEquals(listOf(7, 8, 7, 8), calls)
        }

    private fun credit(
        id: Int,
        type: String,
        popularity: Double,
        department: String? = null,
    ) = TmdbPersonCredit(
        id = id,
        mediaType = type,
        title = if (type == "movie") "Title $id" else null,
        name = if (type == "tv") "Title $id" else null,
        popularity = popularity,
        department = department,
    )

    private fun item(name: String) = MediaItem(id = name, name = name, mediaType = MediaType.VIDEO_FILE, categoryId = "c")

    /** A source holding the titles [has], which also records the content types it is asked for. */
    private val asked = mutableListOf<String>()

    private fun source(has: (String) -> Boolean): PersonTitleSource =
        { titles, contentType ->
            asked += contentType
            titles.map { t -> t.displayTitle?.takeIf(has)?.let(::item) }
        }

    private val person =
        TmdbPerson(
            id = 1,
            combinedCredits =
                TmdbCombinedCredits(
                    cast =
                        listOf(
                            credit(1, "movie", 5.0),
                            credit(2, "tv", 9.0),
                            credit(3, "movie", 7.0),
                            // Two roles in one film: one card.
                            credit(3, "movie", 7.0),
                            credit(4, "person", 99.0),
                        ),
                    crew =
                        listOf(
                            credit(10, "movie", 1.0, department = "Directing"),
                            credit(11, "tv", 2.0, department = "Writing"),
                            credit(12, "movie", 3.0, department = "Production"),
                        ),
                ),
        )

    @Test
    fun `acting credits each once, most popular first, films and shows apart`() =
        runTest {
            val rows = personRows(person, crew = false, sources = listOf(source { true }))
            assertEquals(listOf("Title 3", "Title 1"), rows.movies.map { it.name })
            assertEquals(listOf("Title 2"), rows.shows.map { it.name })
            assertEquals(listOf(ContentType.MOVIES, ContentType.TV_SHOWS), asked)
        }

    @Test
    fun `crew credits are the titles the person directed or wrote`() =
        runTest {
            val rows = personRows(person, crew = true, sources = listOf(source { true }))
            assertEquals(listOf("Title 10"), rows.movies.map { it.name })
            assertEquals(listOf("Title 11"), rows.shows.map { it.name })
        }

    @Test
    fun `only the 50 most popular credits are matched`() {
        val many = TmdbPerson(combinedCredits = TmdbCombinedCredits(cast = (1..80).map { credit(it, "movie", it.toDouble()) }))
        val credits = personCredits(many, crew = false)
        assertEquals(PERSON_MAX_CREDITS, credits.size)
        assertEquals(80, credits.first().id)
        assertEquals(31, credits.last().id)
    }

    @Test
    fun `a title no source has is left out, or a TMDB-only card with the missing titles`() =
        runTest {
            val sources = listOf(source { it == "Title 1" })
            assertEquals(listOf("Title 1"), personRows(person, crew = false, sources = sources).movies.map { it.name })
            val rows = personRows(person, crew = false, sources = sources, withMissing = true)
            assertEquals(listOf(TmdbOnlyItem.PREFIX + "3", "Title 1"), rows.movies.map { it.id })
            assertEquals(MediaType.SERIES, rows.shows.single().mediaType)
            assertTrue(
                rows.shows
                    .single()
                    .id
                    .startsWith(TmdbOnlyItem.PREFIX),
            )
        }
}
