package org.njarasoa.fijerena.core.network.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesEntity
import org.njarasoa.fijerena.core.network.xtream.manager.buildCachedSeriesDetail
import org.njarasoa.fijerena.core.player.domain.sortedSeasons

/**
 * The stored rows are what the screen draws before the provider answers, so a cached detail has to
 * group and sort exactly like a fetched one — otherwise the episode list visibly reshuffles when
 * the fetch lands.
 */
class CachedSeriesDetailTest {
    private val series =
        XtreamSeriesEntity(
            seriesId = 4242,
            providerId = 1L,
            num = 1,
            name = "Law & Order",
            cover = "https://example.invalid/cover.jpg",
            plot = "Cops and lawyers.",
            genre = "Crime, Drama",
            rating = "8.1",
            youtubeTrailer = "dQw4w9WgXcQ",
            categoryId = "12",
            contentRating = "TV-14",
            tmdbId = "549",
        )

    private fun episode(
        id: String,
        season: Int?,
        number: Int,
        plot: String? = null,
        overview: String? = null,
    ) = XtreamEpisodeEntity(
        id = id,
        seriesId = 4242,
        providerId = 1L,
        season = season,
        episodeNum = number,
        title = "Episode $number",
        containerExtension = "mkv",
        overview = overview,
        plot = plot,
    )

    @Test
    fun `groups stored episodes by season`() {
        val detail =
            buildCachedSeriesDetail(
                series,
                listOf(
                    episode("1", season = 1, number = 1),
                    episode("2", season = 1, number = 2),
                    episode("3", season = 2, number = 1),
                ),
            )!!

        assertEquals(setOf("1", "2"), detail.episodes.keys)
        assertEquals(2, detail.episodes.getValue("1").size)
        assertEquals(1, detail.episodes.getValue("2").size)
    }

    @Test
    fun `derives seasons from episodes when none were persisted`() {
        val detail =
            buildCachedSeriesDetail(
                series,
                listOf(
                    episode("3", season = 2, number = 1),
                    episode("1", season = 1, number = 1),
                ),
            )!!

        // No season table is stored; sortedSeasons has to reconstruct them, in order.
        val seasons = detail.sortedSeasons { number -> "Season $number" }
        assertEquals(listOf(1, 2), seasons.map { it.seasonNumber })
    }

    @Test
    fun `no stored episodes yields null rather than an empty show`() {
        assertNull(buildCachedSeriesDetail(series, emptyList()))
    }

    @Test
    fun `carries the series metadata the detail screen displays`() {
        val detail = buildCachedSeriesDetail(series, listOf(episode("1", season = 1, number = 1)))!!

        assertEquals("Crime, Drama", detail.metadata.genre)
        assertEquals("TV-14", detail.metadata.contentRating)
        assertEquals("549", detail.metadata.tmdbId)
        // A bare video id is stored; the screen needs something openable.
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", detail.metadata.trailerUrl)
    }

    @Test
    fun `prefers the provider synopsis and falls back to the stored overview`() {
        val detail =
            buildCachedSeriesDetail(
                series,
                listOf(
                    episode("1", season = 1, number = 1, plot = "From Xtream", overview = "From TMDB"),
                    episode("2", season = 1, number = 2, overview = "From TMDB"),
                ),
            )!!

        val episodes = detail.episodes.getValue("1")
        assertEquals("From Xtream", episodes.first { it.id == "1" }.metadata.plot)
        assertEquals("From TMDB", episodes.first { it.id == "2" }.metadata.plot)
    }

    @Test
    fun `an episode with no season number lands in season zero, not dropped`() {
        val detail = buildCachedSeriesDetail(series, listOf(episode("1", season = null, number = 1)))!!

        assertEquals(setOf("0"), detail.episodes.keys)
    }
}
