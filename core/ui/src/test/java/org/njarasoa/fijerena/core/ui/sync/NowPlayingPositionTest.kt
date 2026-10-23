package org.njarasoa.fijerena.core.ui.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.sync.SyncPayloads

/** The devices list's position for another device's film: run on while playing, never past the end. */
class NowPlayingPositionTest {
    private fun record(
        state: String = SyncPayloads.NowPlaying.PLAYING,
        positionMs: Long? = 60_000,
        durationMs: Long? = 600_000,
        positionAtMs: Long? = 1_000_000,
    ) = SyncPayloads.NowPlaying(
        state = state,
        title = "Malcolm X",
        positionMs = positionMs,
        durationMs = durationMs,
        positionAtMs = positionAtMs,
        sentAt = 1_000_000,
    )

    @Test
    fun `playing runs on from when the sender read it`() {
        assertEquals(90_000L, nowPlayingPosition(record(), nowMs = 1_030_000))
    }

    @Test
    fun `paused stays where it was`() {
        assertEquals(60_000L, nowPlayingPosition(record(state = SyncPayloads.NowPlaying.PAUSED), nowMs = 1_030_000))
    }

    @Test
    fun `never past the length`() {
        assertEquals(600_000L, nowPlayingPosition(record(), nowMs = 9_000_000))
    }

    @Test
    fun `a clock behind the sender's doesn't run it backwards`() {
        assertEquals(60_000L, nowPlayingPosition(record(), nowMs = 900_000))
    }

    @Test
    fun `an older sender's position is shown as sent, live has none`() {
        assertEquals(60_000L, nowPlayingPosition(record(positionAtMs = null), nowMs = 1_030_000))
        assertNull(nowPlayingPosition(record(positionMs = null), nowMs = 1_030_000))
    }
}
