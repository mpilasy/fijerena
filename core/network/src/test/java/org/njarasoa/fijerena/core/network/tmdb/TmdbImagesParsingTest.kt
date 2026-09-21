package org.njarasoa.fijerena.core.network.tmdb

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `/movie/{id}/images` and `/tv/{id}/images` parsing, and the logo/backdrop selection rules
 * built on top of it. See docs/plans/20260902_tv-detail-hero-ui-plan.md Phase 1.
 */
class TmdbImagesParsingTest {
    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Test
    fun `parses logos and backdrops from one response`() {
        val body =
            """
            {"logos":[{"file_path":"/logo.png","iso_639_1":"en","width":500,"vote_average":5.0}],
            "backdrops":[{"file_path":"/backdrop.jpg","width":1920,"vote_average":8.0}]}
            """.trimIndent()

        val parsed = json.decodeFromString<TmdbImagesResponse>(body)
        assertEquals("/logo.png", parsed.logos.single().filePath)
        assertEquals("/backdrop.jpg", parsed.backdrops.single().filePath)
    }

    @Test
    fun `parses a response with neither field present`() {
        val parsed = json.decodeFromString<TmdbImagesResponse>("{}")
        assertTrue(parsed.logos.isEmpty())
        assertTrue(parsed.backdrops.isEmpty())
    }

    @Test
    fun `bestBackdropUrl prefers the highest-voted language-neutral backdrop`() {
        val backdrops =
            listOf(
                TmdbImage(filePath = "/low.jpg", language = null, width = 1920, voteAverage = 5.0),
                TmdbImage(filePath = "/high.jpg", language = null, width = 1920, voteAverage = 9.0),
                // Tagged-language backdrops exist (title cards) but must lose to a neutral one.
                TmdbImage(filePath = "/tagged.jpg", language = "en", width = 3840, voteAverage = 10.0),
            )
        assertEquals(
            "https://image.tmdb.org/t/p/w1280/high.jpg",
            TmdbApiService.bestBackdropUrl(backdrops),
        )
    }

    @Test
    fun `bestBackdropUrl falls back to a tagged backdrop when no neutral one exists`() {
        val backdrops = listOf(TmdbImage(filePath = "/only.jpg", language = "en", width = 1920, voteAverage = 7.0))
        assertEquals("https://image.tmdb.org/t/p/w1280/only.jpg", TmdbApiService.bestBackdropUrl(backdrops))
    }

    @Test
    fun `bestBackdropUrl is null when TMDB has none`() {
        assertNull(TmdbApiService.bestBackdropUrl(emptyList()))
    }
}
