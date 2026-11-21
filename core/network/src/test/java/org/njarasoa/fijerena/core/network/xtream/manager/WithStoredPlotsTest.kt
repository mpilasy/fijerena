package org.njarasoa.fijerena.core.network.xtream.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeEntity

/**
 * A show's episode list fetched again keeps the episode summaries TMDB filled in: the new episode
 * check stored the list without them, and the series screen then showed none for 30 days.
 */
class WithStoredPlotsTest {
    private fun episode(
        id: String,
        plot: String? = null,
        plotFetchedAt: Long? = null,
    ) = XtreamEpisodeEntity(
        id = id,
        seriesId = 1,
        providerId = 1L,
        episodeNum = id.toInt(),
        title = "Episode $id",
        containerExtension = "mkv",
        plot = plot,
        plotFetchedAt = plotFetchedAt,
    )

    @Test
    fun `an episode Xtream sends without a plot keeps the stored one and its date`() {
        val result = withStoredPlots(listOf(episode("1")), listOf(episode("1", "Carrie asks Deacon", 42L)))

        assertEquals("Carrie asks Deacon", result.single().plot)
        assertEquals(42L, result.single().plotFetchedAt)
    }

    @Test
    fun `a plot Xtream sends wins over the stored one`() {
        val result = withStoredPlots(listOf(episode("1", "From Xtream")), listOf(episode("1", "From TMDB", 42L)))

        assertEquals("From Xtream", result.single().plot)
    }

    @Test
    fun `a new episode and a blank stored plot stay without a plot`() {
        val result = withStoredPlots(listOf(episode("1"), episode("2", plot = "")), listOf(episode("2", " ")))

        assertNull(result[0].plot)
        assertEquals("", result[1].plot)
    }
}
