package org.njarasoa.fijerena.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.EpisodeItem

/** Autoplay next episode: when the "Up next" card shows, and what an episode's end does. */
class UpNextTest {
    private val next = EpisodeItem(id = "ep-2", episodeNumber = 2, title = "The Return", seasonNumber = 1)

    private val longEpisode = 22 * 60_000L // lead 90 s
    private val shortEpisode = 5 * 60_000L // lead 15% = 45 s

    private fun shows(
        positionMs: Long,
        durationMs: Long = longEpisode,
        autoplay: Boolean = true,
        xtream: Boolean = true,
        nextEpisode: EpisodeItem? = next,
        dismissed: Boolean = false,
    ) = showUpNext(autoplay, xtream, nextEpisode, positionMs, durationMs, dismissed)

    @Test
    fun `lead is 90 s for a long episode, 15 percent for a short one, none for an unknown length`() {
        assertEquals(90_000L, upNextLeadMs(longEpisode))
        assertEquals(45_000L, upNextLeadMs(shortEpisode))
        assertEquals(0L, upNextLeadMs(0L))
        assertEquals(0L, upNextLeadMs(-1L))
    }

    @Test
    fun `long episode - shows in the last 90 s, not before`() {
        assertFalse(shows(longEpisode - 90_001L))
        assertTrue(shows(longEpisode - 90_000L))
        assertTrue(shows(longEpisode - 5_000L))
    }

    @Test
    fun `short episode - shows in the last 15 percent, not before`() {
        assertFalse(shows(shortEpisode - 45_001L, shortEpisode))
        assertTrue(shows(shortEpisode - 45_000L, shortEpisode))
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
    fun `setting off, no next episode, or a Jellyfin provider - never shows`() {
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
    fun `the card names season and episode`() {
        assertEquals("S1:E2", upNextCode(next))
        assertEquals("E2", upNextCode(next.copy(seasonNumber = null)))
    }
}
