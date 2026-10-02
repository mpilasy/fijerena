package org.njarasoa.fijerena.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.EpisodeItem

/** What the player does when an episode ends on its own — autoplay next episode. */
class UpNextTest {
    private val next = EpisodeItem(id = "ep-2", episodeNumber = 2, title = "The Return", seasonNumber = 1)

    @Test
    fun `setting off - no countdown, even with a next episode`() {
        assertNull(upNextOnEnd(autoplayEnabled = false, nextEpisode = next))
    }

    @Test
    fun `no next episode - no countdown, even with the setting on`() {
        assertNull(upNextOnEnd(autoplayEnabled = true, nextEpisode = null))
    }

    @Test
    fun `setting on and a next episode - counts down to it`() {
        assertEquals(next, upNextOnEnd(autoplayEnabled = true, nextEpisode = next))
    }

    @Test
    fun `the card names season, episode and title`() {
        assertEquals("S1:E2 · The Return", upNextLabel(next))
        assertEquals("The Return", upNextLabel(next.copy(seasonNumber = null)))
    }
}
