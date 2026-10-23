package org.njarasoa.fijerena.core.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProgressTest {
    private val episode = 47 * 60_000L

    @Test
    fun `the watched stretch is 12 percent of the length or 90 s, whichever is longer`() {
        assertEquals(338_400L, watchedTailMs(episode))
        assertEquals(90_000L, watchedTailMs(5 * 60_000L))
        assertEquals(0L, watchedTailMs(0L))
    }

    @Test
    fun `watched once inside the stretch, resumable before it`() {
        val start = episode - watchedTailMs(episode)
        assertFalse(isWatchedAt(start - 1, episode))
        assertTrue(isResumableAt(start - 1, episode))
        assertTrue(isWatchedAt(start, episode))
        assertFalse(isResumableAt(start, episode))
    }

    @Test
    fun `not worth resuming under 2 percent in, nor with an unknown length`() {
        assertFalse(isResumableAt(episode / 100, episode))
        assertTrue(isResumableAt(episode / 50, episode))
        assertFalse(isResumableAt(1_000L, 0L))
        assertFalse(isWatchedAt(1_000L, 0L))
    }
}
