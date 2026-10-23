package org.njarasoa.fijerena.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.domain.MediaMetadata

/** Autoplay next episode: when the "Up next" card shows, and what an episode's end does. */
class UpNextTest {
    private val next = EpisodeItem(id = "ep-2", episodeNumber = 2, title = "The Return", seasonNumber = 1)

    private val longEpisode = 47 * 60_000L // lead 12% = 5 min 38.4 s
    private val shortEpisode = 5 * 60_000L // lead 90 s

    private fun shows(
        positionMs: Long,
        durationMs: Long = longEpisode,
        autoplay: Boolean = true,
        xtream: Boolean = true,
        nextEpisode: EpisodeItem? = next,
        dismissed: Boolean = false,
    ) = showUpNext(autoplay, xtream, nextEpisode, positionMs, durationMs, dismissed)

    @Test
    fun `lead is 12 percent for a long episode, 90 s for a short one, none for an unknown length`() {
        assertEquals(338_400L, upNextLeadMs(longEpisode))
        assertEquals(90_000L, upNextLeadMs(shortEpisode))
        assertEquals(0L, upNextLeadMs(0L))
        assertEquals(0L, upNextLeadMs(-1L))
    }

    @Test
    fun `long episode - shows in the last 12 percent, not before`() {
        assertFalse(shows(longEpisode - 338_401L))
        assertTrue(shows(longEpisode - 338_400L))
        assertTrue(shows(longEpisode - 5_000L))
    }

    @Test
    fun `short episode - shows in the last 90 s, not before`() {
        assertFalse(shows(shortEpisode - 90_001L, shortEpisode))
        assertTrue(shows(shortEpisode - 90_000L, shortEpisode))
    }

    @Test
    fun `unknown duration - never shows early`() {
        assertFalse(shows(positionMs = 0L, durationMs = 0L))
        assertFalse(shows(positionMs = 10_000L, durationMs = 0L))
    }

    @Test
    fun `cancelled - hidden for that episode`() {
        assertFalse(shows(longEpisode - 5_000L, dismissed = true))
    }

    @Test
    fun `setting off, no next episode, or a provider without autoplay - never shows`() {
        assertFalse(shows(longEpisode - 5_000L, autoplay = false))
        assertFalse(shows(longEpisode - 5_000L, nextEpisode = null))
        assertFalse(shows(longEpisode - 5_000L, xtream = false))
    }

    @Test
    fun `end of episode - plays the next one only with the setting on, an Xtream provider and no cancel`() {
        assertEquals(next, upNextOnEnd(autoplayEnabled = true, providerSupportsAutoplay = true, nextEpisode = next, dismissed = false))
        assertNull(upNextOnEnd(autoplayEnabled = false, providerSupportsAutoplay = true, nextEpisode = next, dismissed = false))
        assertNull(upNextOnEnd(autoplayEnabled = true, providerSupportsAutoplay = false, nextEpisode = next, dismissed = false))
        assertNull(upNextOnEnd(autoplayEnabled = true, providerSupportsAutoplay = true, nextEpisode = null, dismissed = false))
        assertNull(upNextOnEnd(autoplayEnabled = true, providerSupportsAutoplay = true, nextEpisode = next, dismissed = true))
    }

    @Test
    fun `countdown is the playback time left, rounded up`() {
        assertEquals(90, upNextSecondsLeft(longEpisode - 90_000L, longEpisode))
        assertEquals(1, upNextSecondsLeft(longEpisode - 1L, longEpisode))
        assertEquals(0, upNextSecondsLeft(longEpisode, longEpisode))
    }

    @Test
    fun `the countdown reads as minutes and seconds`() {
        assertEquals("5:38", upNextCountdown(338))
        assertEquals("1:00", upNextCountdown(60))
        assertEquals("0:09", upNextCountdown(9))
        assertEquals("0:00", upNextCountdown(0))
    }

    @Test
    fun `the card names season and episode`() {
        assertEquals("S1:E2", upNextCode(next))
        assertEquals("E2", upNextCode(next.copy(seasonNumber = null)))
    }

    @Test
    fun `details panel - the episode's own title and synopsis, null when missing`() {
        assertEquals("The Pilot", upNextTitle(next.copy(title = "EN - Some Show - S01E02 - The Pilot")))
        assertNull(upNextTitle(next.copy(title = "EN - Some Show - S01E02")))
        assertNull(upNextPlot(next))
        assertNull(upNextPlot(next.copy(metadata = MediaMetadata(plot = "  "))))
        assertEquals("A plot.", upNextPlot(next.copy(metadata = MediaMetadata(plot = " A plot. "))))
    }
}
