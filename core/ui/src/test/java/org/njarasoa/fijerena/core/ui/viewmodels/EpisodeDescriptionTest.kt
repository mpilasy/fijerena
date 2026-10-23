package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.domain.MediaMetadata

/** The OSD describes the episode, never the series. */
class EpisodeDescriptionTest {
    private fun episode(plot: String?) =
        EpisodeItem(id = "e1", episodeNumber = 1, title = "Pilot", seasonNumber = 1, metadata = MediaMetadata(plot = plot))

    @Test
    fun `an episode with a plot gives it`() {
        assertEquals("Doug meets Carrie.", episodeDescription(episode("Doug meets Carrie.")))
    }

    @Test
    fun `an episode without a plot gives none`() {
        assertNull(episodeDescription(episode(null)))
        assertNull(episodeDescription(episode("  ")))
        assertNull(episodeDescription(null))
    }
}
