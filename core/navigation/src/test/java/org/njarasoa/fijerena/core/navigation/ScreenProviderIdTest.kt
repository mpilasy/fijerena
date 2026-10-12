package org.njarasoa.fijerena.core.navigation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The routes that open on a source keep it through serialization, and a route saved without one
 * (an older back stack, a caller that doesn't say) reads as 0, the active source. See
 * docs/plans/20261012_multi-source-search-history-plan.md → Phase 1.
 */
class ScreenProviderIdTest {
    private val routes: List<Screen> =
        listOf(
            Screen.MovieDetails(movieId = "m1", movieName = "Film", categoryId = "c1"),
            Screen.EpisodeSelection(seriesId = "s1", seriesName = "Show", categoryId = "c1", initialEpisodeId = "e1"),
            Screen.Player(streamId = "v1", streamName = "Film", categoryId = "c1", contentType = "MOVIES"),
            Screen.Player(
                streamId = "ch1",
                streamName = "News",
                categoryId = "c1",
                contentType = "LIVE_TV",
                catchupStartSec = 1_700_000_000L,
                catchupDurationSec = 3_600L,
                programTitle = "Evening news",
            ),
            Screen.CategoryList(contentType = "LIVE_TV", initialCategoryId = "c1", initialStreamId = "ch1"),
            Screen.EpgGuide(categoryId = "c1", categoryName = "News", focusChannelId = "ch1"),
        )

    @Test
    fun `routes without a source round-trip with 0`() {
        routes.forEach { route ->
            val decoded = roundTrip(route)
            assertEquals(route, decoded)
            assertEquals(0L, decoded.providerIdOrNull())
        }
    }

    @Test
    fun `routes with a source keep it`() {
        routes.map { it.withProviderId(SOURCE) }.forEach { route ->
            val decoded = roundTrip(route)
            assertEquals(route, decoded)
            assertEquals(SOURCE, decoded.providerIdOrNull())
        }
    }

    @Test
    fun `a route saved before routes had a source reads as the active one`() {
        val old = """{"movieId":"m1","movieName":"Film","categoryId":"c1"}"""
        assertEquals(0L, Json.decodeFromString(Screen.MovieDetails.serializer(), old).providerId)
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

    private fun Screen.withProviderId(id: Long): Screen =
        when (this) {
            is Screen.MovieDetails -> copy(providerId = id)
            is Screen.EpisodeSelection -> copy(providerId = id)
            is Screen.Player -> copy(providerId = id)
            is Screen.CategoryList -> copy(providerId = id)
            is Screen.EpgGuide -> copy(providerId = id)
            else -> this
        }

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
