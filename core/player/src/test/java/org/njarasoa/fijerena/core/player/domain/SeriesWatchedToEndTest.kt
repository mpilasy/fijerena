package org.njarasoa.fijerena.core.player.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a show counts as watched to the end, for the Wishlist question (consistency plan, C-D4). */
class SeriesWatchedToEndTest {
    private fun episode(
        id: String,
        number: Int,
    ) = EpisodeItem(id = id, episodeNumber = number, title = id)

    private val series =
        SeriesDetail(
            id = "s",
            name = "Show",
            episodes =
                mapOf(
                    "0" to listOf(episode("special", 1)),
                    "1" to listOf(episode("s1e2", 2), episode("s1e1", 1)),
                    "2" to listOf(episode("s2e2", 2), episode("s2e1", 1)),
                ),
        )
    private val seasons = series.sortedSeasons { "Season $it" }

    @Test
    fun `the last episode of the last season watched is the end`() {
        assertTrue(series.isWatchedToEnd(seasons, setOf("s2e2")))
    }

    @Test
    fun `other episodes watched are not the end`() {
        assertFalse(series.isWatchedToEnd(seasons, setOf("s1e1", "s1e2", "s2e1", "special")))
    }

    @Test
    fun `specials are the end only when they are the only season`() {
        val specials = SeriesDetail(id = "x", name = "X", episodes = mapOf("0" to listOf(episode("special", 1))))
        assertTrue(specials.isWatchedToEnd(specials.sortedSeasons { "Season $it" }, setOf("special")))
    }

    @Test
    fun `a show with no episodes is never watched to the end`() {
        val empty = SeriesDetail(id = "e", name = "E")
        assertFalse(empty.isWatchedToEnd(emptyList(), emptySet()))
    }
}
