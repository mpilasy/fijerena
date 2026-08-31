package org.njarasoa.fijerena.core.player.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SortedSeasonsTest {
    private fun episode(
        id: String,
        number: Int,
    ) = EpisodeItem(id = id, episodeNumber = number, title = id)

    private fun seasonName(num: Int) = "Season $num"

    @Test
    fun `season missing from the API list but present in the episode map is backfilled`() {
        // Real-world case: one alternate Xtream stream's `seasons` array omits season 3
        // even though its episode map still has episodes filed under "3".
        val series =
            SeriesDetail(
                id = "s1",
                name = "Silo",
                seasons =
                    listOf(
                        SeasonInfo(seasonNumber = 1, name = "Season 1"),
                        SeasonInfo(seasonNumber = 2, name = "Season 2"),
                    ),
                episodes =
                    mapOf(
                        "1" to List(10) { episode("s1e${it + 1}", it + 1) },
                        "2" to List(10) { episode("s2e${it + 1}", it + 1) },
                        "3" to List(9) { episode("s3e${it + 1}", it + 1) },
                    ),
            )

        val result = series.sortedSeasons(::seasonName)

        assertEquals(listOf(1, 2, 3), result.map { it.seasonNumber })
        assertEquals(9, result.last().episodeCount)
        assertEquals(29, series.episodes.values.sumOf { it.size })
    }

    @Test
    fun `no seasons list at all still derives every season from episodes`() {
        val series =
            SeriesDetail(
                id = "s2",
                name = "No Seasons Metadata",
                seasons = emptyList(),
                episodes =
                    mapOf(
                        "1" to listOf(episode("a", 1)),
                        "2" to listOf(episode("b", 1)),
                    ),
            )

        val result = series.sortedSeasons(::seasonName)

        assertEquals(listOf(1, 2), result.map { it.seasonNumber })
    }

    @Test
    fun `season with no episodes is dropped even when the API lists it`() {
        val series =
            SeriesDetail(
                id = "s3",
                name = "With Specials",
                seasons =
                    listOf(
                        SeasonInfo(seasonNumber = 0, name = "Specials"),
                        SeasonInfo(seasonNumber = 1, name = "Season 1"),
                    ),
                episodes = mapOf("1" to listOf(episode("a", 1))),
            )

        val result = series.sortedSeasons(::seasonName)

        assertEquals(listOf(1), result.map { it.seasonNumber })
    }
}
