package org.njarasoa.fijerena.core.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [episodeIdMatchingName]: finding an episode played from another copy of the show in this one. */
class EpisodeIdMatchingNameTest {
    private val show =
        SeriesDetail(
            id = "4770",
            name = "EN - Criminal Minds (2005) (US)",
            episodes =
                mapOf(
                    "5" to
                        listOf(
                            EpisodeItem("en-s5e22", 22, "S05E22", seasonNumber = 5),
                            EpisodeItem("en-s5e23", 23, "S05E23", seasonNumber = 5),
                        ),
                    // No seasonNumber on the items: the map key says which season.
                    "6" to listOf(EpisodeItem("en-s6e1", 1, "S06E01")),
                ),
        )

    @Test
    fun `finds the same season and episode from another copy's name`() =
        assertEquals("en-s5e23", show.episodeIdMatchingName("D+ - Criminal Minds (2005) (US) - S05E23 - Our Darkest Hour"))

    @Test
    fun `uses the season key when episodes carry no season`() =
        assertEquals("en-s6e1", show.episodeIdMatchingName("DE - Criminal Minds s06e01"))

    @Test
    fun `nothing when the name has no season and episode, or this show lacks it`() {
        assertNull(show.episodeIdMatchingName("Criminal Minds - Pilot"))
        assertNull(show.episodeIdMatchingName("D+ - Criminal Minds - S09E01"))
    }
}
