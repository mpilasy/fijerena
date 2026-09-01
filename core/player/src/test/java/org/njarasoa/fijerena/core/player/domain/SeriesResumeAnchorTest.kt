package org.njarasoa.fijerena.core.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesResumeAnchorTest {
    private val seasons =
        listOf(
            SeasonInfo(seasonNumber = 1, name = "Season 1"),
            SeasonInfo(seasonNumber = 2, name = "Season 2"),
        )

    private val series =
        SeriesDetail(
            id = "s1",
            name = "Law and Order",
            seasons = seasons,
            episodes =
                mapOf(
                    "1" to listOf(episode("s1e1", 1), episode("s1e2", 2)),
                    "2" to listOf(episode("s2e1", 1), episode("s2e2", 2)),
                ),
        )

    private fun episode(
        id: String,
        number: Int,
    ) = EpisodeItem(id = id, episodeNumber = number, title = id)

    @Test
    fun `nothing played yields no anchor`() {
        assertNull(series.resumeAnchorEpisodeId(seasons, lastPlayedEpisodeId = null, isCompleted = { false }))
    }

    @Test
    fun `unfinished episode is the anchor`() {
        assertEquals("s2e1", series.resumeAnchorEpisodeId(seasons, "s2e1", isCompleted = { false }))
    }

    @Test
    fun `finished episode anchors on the next one, across a season boundary`() {
        assertEquals("s2e1", series.resumeAnchorEpisodeId(seasons, "s1e2", isCompleted = { it == "s1e2" }))
    }

    @Test
    fun `finished last episode stays on itself`() {
        assertEquals("s2e2", series.resumeAnchorEpisodeId(seasons, "s2e2", isCompleted = { it == "s2e2" }))
    }

    @Test
    fun `unknown episode id falls back to itself`() {
        assertEquals("gone", series.resumeAnchorEpisodeId(seasons, "gone", isCompleted = { true }))
    }

    @Test
    fun `single release year returns that year`() {
        val detail = SeriesDetail(
            id = "s1",
            name = "Test",
            metadata = MediaMetadata(year = 2020),
        )
        assertEquals("2020", detail.seriesYearRange())
    }

    @Test
    fun `past completed series returns start and end year range`() {
        val detail = SeriesDetail(
            id = "s1",
            name = "Test",
            metadata = MediaMetadata(year = 2008),
            episodes = mapOf(
                "1" to listOf(EpisodeItem("e1", 1, "E1", metadata = MediaMetadata(airDate = "2008-01-20"))),
                "5" to listOf(EpisodeItem("e2", 16, "E2", metadata = MediaMetadata(airDate = "2013-09-29"))),
            ),
        )
        assertEquals("2008–2013", detail.seriesYearRange())
    }

    @Test
    fun `ongoing series returns start year to ongoing label`() {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val detail = SeriesDetail(
            id = "s1",
            name = "Test",
            metadata = MediaMetadata(year = 2022),
            episodes = mapOf(
                "1" to listOf(EpisodeItem("e1", 1, "E1", metadata = MediaMetadata(airDate = "2022-01-01"))),
                "2" to listOf(EpisodeItem("e2", 1, "E2", metadata = MediaMetadata(airDate = "$currentYear-05-01"))),
            ),
        )
        assertEquals("2022–ongoing", detail.seriesYearRange("ongoing"))
    }
}
