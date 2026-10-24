package org.njarasoa.fijerena.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.PlaybackState

class StreamInfoHoldTest {
    @Test
    fun `buffering, idle and tuning are waiting`() {
        assertTrue(isStreamWaiting(PlaybackState.Buffering, null))
        assertTrue(isStreamWaiting(PlaybackState.Idle, null))
        assertTrue(isStreamWaiting(PlaybackState.Playing(0L, 0L), "BBC One"))
    }

    @Test
    fun `playing, paused, error and ended are not waiting`() {
        assertFalse(isStreamWaiting(PlaybackState.Playing(0L, 0L), null))
        assertFalse(isStreamWaiting(PlaybackState.Paused(0L, 0L), null))
        assertFalse(isStreamWaiting(PlaybackState.Error("boom"), null))
        assertFalse(isStreamWaiting(PlaybackState.Ended(), null))
    }

    @Test
    fun `the banner alone holds while the stream waits`() {
        assertTrue(holdStreamInfo(showControls = false, showStreamInfo = true, scrubbing = false, waiting = true))
    }

    @Test
    fun `the banner timer runs once the stream plays`() {
        assertFalse(holdStreamInfo(showControls = false, showStreamInfo = true, scrubbing = false, waiting = false))
    }

    @Test
    fun `controls, scrubbing or a hidden banner never hold`() {
        assertFalse(holdStreamInfo(showControls = true, showStreamInfo = true, scrubbing = false, waiting = true))
        assertFalse(holdStreamInfo(showControls = false, showStreamInfo = true, scrubbing = true, waiting = true))
        assertFalse(holdStreamInfo(showControls = false, showStreamInfo = false, scrubbing = false, waiting = true))
    }
}
