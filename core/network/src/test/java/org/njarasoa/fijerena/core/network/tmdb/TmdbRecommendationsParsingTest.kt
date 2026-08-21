package org.njarasoa.fijerena.core.network.tmdb

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TMDB answers a missing or mistyped id with a JSON error body rather than an HTTP-level failure
 * the client would throw on, so parsing has to survive it and come out as "nothing to show".
 */
class TmdbRecommendationsParsingTest {
    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Test
    fun `parses a movie recommendation`() {
        val body =
            """
            {"page":1,"results":[{"id":693134,"title":"Dune: Part Two","overview":"Paul unites with Chani.",
            "poster_path":"/8b8R8l88Qje9dn9OE8PY05Nxl1X.jpg","release_date":"2024-02-27","vote_average":8.2,
            "adult":false,"backdrop_path":"/xOMo8BRK7PfcJv9JCnx7s5hj0PX.jpg"}],"total_pages":1,"total_results":1}
            """.trimIndent()

        val parsed = json.decodeFromString<TmdbRecommendationsResponse>(body)
        val first = parsed.results.single()
        assertEquals(693134, first.id)
        assertEquals("Dune: Part Two", first.displayTitle)
        assertEquals(2024, first.year)
    }

    @Test
    fun `reads a TV recommendation from name and first air date`() {
        val body = """{"page":1,"results":[{"id":1399,"name":"Game of Thrones","first_air_date":"2011-04-17"}]}"""

        val first = json.decodeFromString<TmdbRecommendationsResponse>(body).results.single()
        assertEquals("Game of Thrones", first.displayTitle)
        assertEquals(2011, first.year)
    }

    @Test
    fun `parses an empty results array`() {
        val body = """{"page":1,"results":[],"total_pages":0,"total_results":0}"""
        assertTrue(json.decodeFromString<TmdbRecommendationsResponse>(body).results.isEmpty())
    }

    @Test
    fun `parses a 404 error body as no results`() {
        val body =
            """{"success":false,"status_code":34,"status_message":"The resource you requested could not be found."}"""
        assertTrue(json.decodeFromString<TmdbRecommendationsResponse>(body).results.isEmpty())
    }

    @Test
    fun `survives a result with neither title nor date`() {
        val body = """{"results":[{"id":42}]}"""
        val first = json.decodeFromString<TmdbRecommendationsResponse>(body).results.single()
        assertNull(first.displayTitle)
        assertNull(first.year)
    }
}
