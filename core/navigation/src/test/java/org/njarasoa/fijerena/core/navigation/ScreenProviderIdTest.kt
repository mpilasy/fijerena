package org.njarasoa.fijerena.core.navigation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The routes that open on a source keep it through serialization. Every one of them but a
 * section's list names a real source (there is no source in use to fall back to); a section's
 * list without one reads as 0, every Xtream source merged. See
 * docs/plans/20261012_multi-source-search-history-plan.md → Phases 1 and 6.
 */
class ScreenProviderIdTest {
    private val routes: List<Screen> =
        listOf(
            Screen.MovieDetails(movieId = "m1", movieName = "Film", categoryId = "c1", providerId = SOURCE),
            Screen.EpisodeSelection(seriesId = "s1", seriesName = "Show", categoryId = "c1", initialEpisodeId = "e1", providerId = SOURCE),
            Screen.Player(streamId = "v1", streamName = "Film", categoryId = "c1", contentType = "MOVIES", providerId = SOURCE),
            Screen.Player(
                streamId = "ch1",
                streamName = "News",
                categoryId = "c1",
                contentType = "LIVE_TV",
                catchupStartSec = 1_700_000_000L,
                catchupDurationSec = 3_600L,
                programTitle = "Evening news",
                providerId = SOURCE,
            ),
            Screen.CategoryList(contentType = "LIVE_TV", initialCategoryId = "c1", initialStreamId = "ch1", providerId = SOURCE),
            Screen.EpgGuide(categoryId = "c1", categoryName = "News", focusChannelId = "ch1", providerId = SOURCE),
        )

    @Test
    fun `routes with a source keep it`() {
        routes.forEach { route ->
            val decoded = roundTrip(route)
            assertEquals(route, decoded)
            assertEquals(SOURCE, decoded.providerIdOrNull())
        }
    }

    @Test
    fun `a section's list has no source of its own`() {
        val route = Screen.CategoryList(contentType = "MOVIES")

        assertEquals(0L, (roundTrip(route) as Screen.CategoryList).providerId)
    }

    // Navigation serializes each route with its own class's serializer.
    private fun roundTrip(route: Screen): Screen =
        when (route) {
            is Screen.MovieDetails -> roundTrip(Screen.MovieDetails.serializer(), route)
            is Screen.EpisodeSelection -> roundTrip(Screen.EpisodeSelection.serializer(), route)
            is Screen.Player -> roundTrip(Screen.Player.serializer(), route)
            is Screen.CategoryList -> roundTrip(Screen.CategoryList.serializer(), route)
            is Screen.EpgGuide -> roundTrip(Screen.EpgGuide.serializer(), route)
            else -> error("not a route with a source: $route")
        }

    private fun <T : Screen> roundTrip(
        serializer: KSerializer<T>,
        route: T,
    ): T = Json.decodeFromString(serializer, Json.encodeToString(serializer, route))

    private fun Screen.providerIdOrNull(): Long? =
        when (this) {
            is Screen.MovieDetails -> providerId
            is Screen.EpisodeSelection -> providerId
            is Screen.Player -> providerId
            is Screen.CategoryList -> providerId
            is Screen.EpgGuide -> providerId
            else -> null
        }

    private companion object {
        const val SOURCE = 7L
    }
}
