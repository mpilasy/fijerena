package org.njarasoa.fijerena.core.player.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** See docs/plans/20261001_live-sync-now-playing-plan.md → Phase 1. */
class NowPlayingSnapshotTest {
    private val movie = PlayerMetadata(title = "Malcolm X", streamUrl = "http://h/u/p/1.mkv")
    private val live = PlayerMetadata(title = "BBC World News", channelName = "Provider", streamUrl = "http://h/u/p/2", isLive = true, programTitle = "Newsday")

    @Test
    fun `playing and paused map to the snapshot's state`() {
        assertFalse(NowPlayingSnapshot.of(movie, PlaybackState.Playing(1_000, 9_000), null)!!.paused)
        assertTrue(NowPlayingSnapshot.of(movie, PlaybackState.Paused(1_000, 9_000), null)!!.paused)
    }

    @Test
    fun `nothing plays when idle, ended, failed, or with no stream`() {
        assertNull(NowPlayingSnapshot.of(movie, PlaybackState.Idle, null))
        assertNull(NowPlayingSnapshot.of(movie, PlaybackState.Ended(9_000), null))
        assertNull(NowPlayingSnapshot.of(movie, PlaybackState.Error("x"), null))
        assertNull(NowPlayingSnapshot.of(PlayerMetadata(), PlaybackState.Playing(0, 0), null))
    }

    @Test
    fun `a rebuffer keeps the same item's state, a new item starts as playing`() {
        val paused = NowPlayingSnapshot.of(movie, PlaybackState.Paused(1_000, 9_000), null)
        assertTrue(NowPlayingSnapshot.of(movie, PlaybackState.Buffering, paused)!!.paused)
        assertFalse(NowPlayingSnapshot.of(live, PlaybackState.Buffering, paused)!!.paused)
    }

    @Test
    fun `live carries the channel and programme, not the provider name or a position`() {
        val snapshot = NowPlayingSnapshot.of(live, PlaybackState.Playing(5_000, 0), null)!!
        assertEquals("BBC World News", snapshot.channelName)
        assertEquals("Newsday", snapshot.programTitle)
        assertNull(snapshot.positionMs)
    }

    @Test
    fun `shown ignores the position`() {
        val a = NowPlayingSnapshot.of(movie, PlaybackState.Playing(1_000, 9_000), null)!!
        val b = NowPlayingSnapshot.of(movie, PlaybackState.Playing(2_000, 9_000), null)!!
        assertEquals(a.shown(), b.shown())
    }
}
