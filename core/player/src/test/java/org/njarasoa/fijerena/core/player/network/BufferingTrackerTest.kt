package org.njarasoa.fijerena.core.player.network

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.BufferingInfo
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** The per-wait counts behind the Expert-mode lines under "Buffering…". */
class BufferingTrackerTest {
    private val tracker = BufferingTracker()

    private fun state(
        buffering: Boolean = false,
        playing: Boolean = false,
        now: Long,
        afterSeek: Boolean = false,
    ) = tracker.onStateChanged(buffering, playing, now, afterSeek)

    @Test
    fun `a wait counts its own time, bytes and errors only`() {
        tracker.onBytes(500)
        tracker.onLoadError("HTTP 404")
        state(buffering = true, now = 1_000)
        tracker.onBytes(2_000)
        tracker.onLoadError("HTTP 503")
        tracker.onLoadError("timeout")

        assertEquals(4_000, tracker.waitingMs(5_000))
        assertEquals(2_000, tracker.bytesThisWait())
        assertEquals(2, tracker.errorsThisWait())
        assertEquals("timeout", tracker.lastErrorThisWait())
    }

    @Test
    fun `an error from before the wait is not this wait's`() {
        tracker.onLoadError("HTTP 404")
        state(buffering = true, now = 0)

        assertEquals(0, tracker.errorsThisWait())
        assertNull(tracker.lastErrorThisWait())
    }

    @Test
    fun `the wait ends when playback starts`() {
        state(buffering = true, now = 0)
        tracker.onBytes(100)
        state(playing = true, now = 3_000)

        assertEquals(0, tracker.waitingMs(9_000))
        assertEquals(0, tracker.bytesThisWait())
    }

    @Test
    fun `a stall while playing is a rebuffer, a start or a seek is not`() {
        state(buffering = true, now = 0)
        assertFalse("first start", tracker.isRebuffering)

        state(playing = true, now = 1_000)
        state(buffering = true, now = 2_000)
        assertTrue("stall", tracker.isRebuffering)

        state(playing = true, now = 3_000)
        state(buffering = true, now = 4_000, afterSeek = true)
        assertFalse("seek", tracker.isRebuffering)
    }

    @Test
    fun `a new stream's first wait is a start`() {
        state(playing = true, now = 0)
        tracker.onNewStream()
        state(buffering = true, now = 1_000)

        assertFalse(tracker.isRebuffering)
    }

    @Test
    fun `failed loads read as a code, a timeout or DNS`() {
        val dataSpec = DataSpec(mockk<Uri>(relaxed = true))
        val http503 =
            HttpDataSource.InvalidResponseCodeException(503, "Service Unavailable", null, emptyMap(), dataSpec, ByteArray(0))
        assertEquals("HTTP 503", loadErrorLabel(http503))
        assertEquals("timeout", loadErrorLabel(IOException(SocketTimeoutException())))
        assertEquals("DNS", loadErrorLabel(IOException(UnknownHostException("cf.example"))))
    }

    @Test
    fun `too slow only when the stream's bitrate is known and not met`() {
        fun info(
            receiving: Long,
            stream: Long?,
        ) = BufferingInfo(0, 2_500, 0, receiving, stream, null, 0, null, 0)

        assertTrue(info(1_000_000, 4_000_000).tooSlow)
        assertFalse(info(5_000_000, 4_000_000).tooSlow)
        assertFalse("bitrate unknown", info(1_000_000, null).tooSlow)
        assertFalse("nothing measured yet", info(0, 4_000_000).tooSlow)
    }
}
