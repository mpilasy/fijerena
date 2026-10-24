package org.njarasoa.fijerena.core.player.service

import android.os.Handler
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.MediaSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.source.StreamingMediaSourceFactory

/**
 * The retry/recycle state machine of [StreamingPlaybackService], behind a fake player, source
 * factory and main-thread handler (delayed retries are captured, then run by hand).
 *
 * F-01: a seamless recycle whose fresh source then failed left `isRecycling` set, and the retry
 * path returned early on it — no retry ever ran: a frozen frame, no spinner, no error. See
 * the rock solid stability resilience plan → F-01. (F-02, the live HLS recycle
 * position, was not reproduced and changed nothing — there is no fix to lock in.)
 */
@androidx.media3.common.util.UnstableApi
class StreamingPlaybackServiceRecoveryTest {
    private val player = mockk<ExoPlayer>(relaxed = true)
    private val sourceFactory = mockk<StreamingMediaSourceFactory>()
    private val handler = mockk<Handler>(relaxed = true)

    /** Runnables posted to the main handler, with their delays, in order. */
    private val posted = mutableListOf<Pair<Runnable, Long>>()
    private val removed = mutableListOf<Runnable>()
    private val sources = mutableListOf<MediaSource>()
    private lateinit var service: StreamingPlaybackService

    private val live = PlayerMetadata(title = "News", streamUrl = "http://example.test/live/1.ts", isLive = true)
    private val vod = PlayerMetadata(title = "Film", streamUrl = "http://example.test/movie/1.mkv", isLive = false)

    @Before
    fun setup() {
        every { handler.postDelayed(any(), any()) } answers {
            posted += firstArg<Runnable>() to secondArg<Long>()
            true
        }
        every { handler.removeCallbacks(any<Runnable>()) } answers { removed += firstArg<Runnable>() }
        // Playing unless a test pauses: a retry due while paused is held (Phase 2).
        every { player.playWhenReady } returns true
        every { sourceFactory.createMediaSource(any(), any(), any(), any(), any(), any()) } answers {
            mockk<MediaSource>().also { sources += it }
        }
        val session = mockk<MediaSession>()
        every { session.player } returns player
        service = StreamingPlaybackService()
        service.mediaSession = session
        service.mediaSourceFactory = sourceFactory
        service.mainHandler = handler
    }

    @Test
    fun `an error while a recycle is in flight schedules a hard retry and clears recycling`() {
        service.playStream(live)
        service.performSeamlessRecycle(live, currentPos = 10_000)
        assertTrue(service.isRecycling())

        // The fresh source failed to prepare: onPlayerError → handleStreamEndedOrError.
        service.handleStreamEndedOrError("Server unavailable (503)")

        assertFalse("recycling must not stay set once the recycle failed", service.isRecycling())
        assertEquals(PlaybackState.Buffering, service.playbackState.value)
        assertEquals(1, posted.size)
        assertEquals(2_000L, posted.single().second)

        val sourceBeforeRetry = sources.size
        posted.single().first.run()
        assertEquals(sourceBeforeRetry + 1, sources.size)
        // A hard retry: the source replaces the old one from its default position, then prepares.
        verify { player.setMediaSource(sources.last()) }
        verify(atLeast = 2) { player.prepare() }
    }

    @Test
    fun `a server down says so through the retry, and the retry keeps the message up`() {
        service = spyk(service)
        every { service.getString(any(), any()) } returns "server down"
        service.playStream(vod)

        service.handleStreamEndedOrError("HTTP 523", httpStatus = 523)

        assertEquals(PlaybackState.Error("server down"), service.playbackState.value)
        posted.single().first.run()
        assertEquals(PlaybackState.Error("server down"), service.playbackState.value)
        verify { player.setMediaSource(sources.last()) }
    }

    @Test
    fun `another HTTP error retries behind buffering`() {
        service.playStream(vod)

        service.handleStreamEndedOrError("HTTP 404", httpStatus = 404)

        assertEquals(PlaybackState.Buffering, service.playbackState.value)
    }

    @Test
    fun `a seamless recycle cancels a hard retry already scheduled, and keeps the frame`() {
        service.playStream(live)
        service.handleStreamEndedOrError("Network failed")
        val scheduled = posted.single().first

        service.performSeamlessRecycle(live, currentPos = 10_000)

        assertTrue(scheduled in removed)
        assertTrue(service.isRecycling())
        verify { player.setMediaSource(sources.last(), false) }
    }

    @Test
    fun `a recycle that can't build a source doesn't leave recycling set`() {
        service.playStream(live)
        service.mediaSourceFactory = null

        service.performSeamlessRecycle(live, currentPos = 10_000)

        assertFalse(service.isRecycling())
    }

    @Test
    fun `retries back off linearly, and each new fault keeps retrying`() {
        service.playStream(live)
        repeat(3) { attempt ->
            service.performSeamlessRecycle(live, currentPos = 10_000)
            service.handleStreamEndedOrError("Timeout")
            assertFalse(service.isRecycling())
            assertEquals(2_000L * (attempt + 1), posted.last().second)
        }
        assertEquals(3, posted.size)
        assertEquals(3, service.streamRetryCount.value)
    }

    @Test
    fun `a final error shows at once and cancels any scheduled retry`() {
        service.playStream(vod)
        service.handleStreamEndedOrError("Network failed")
        val scheduled = posted.single().first

        service.handleFinalError("Video codec not supported on this device: Dolby Vision profile 5")

        assertTrue(scheduled in removed)
        assertEquals(1, posted.size)
        assertEquals(
            PlaybackState.Error("Video codec not supported on this device: Dolby Vision profile 5"),
            service.playbackState.value,
        )
    }

    @Test
    fun `a VOD retry resumes where the fault happened`() {
        service.playStream(vod)
        every { player.currentPosition } returns 42_000L

        service.handleStreamEndedOrError("Network failed")
        assertEquals(3_000L, posted.single().second)
        posted.single().first.run()

        verify { player.seekTo(42_000L) }
    }

    @Test
    fun `a live retry reconnects at the live edge`() {
        service.playStream(live)
        every { player.currentPosition } returns 42_000L

        service.handleStreamEndedOrError("Network failed")
        posted.single().first.run()

        verify(exactly = 0) { player.seekTo(any<Long>()) }
    }

    @Test
    fun `a new stream clears a recycle left in flight and any retry waiting`() {
        service.playStream(live)
        service.handleStreamEndedOrError("Network failed")
        service.performSeamlessRecycle(live, currentPos = 10_000)
        val waiting = posted.single().first

        service.playStream(live.copy(streamUrl = "http://example.test/live/2.ts"))

        assertFalse(service.isRecycling())
        assertTrue(waiting in removed)
        assertEquals(0, service.streamRetryCount.value)
    }

    @Test
    fun `a failed stream on screen is retried when the network is back`() {
        service.playStream(live)
        service.handleFinalError("Connection lost")

        assertTrue(service.wantsRetryOnReconnect())
    }

    @Test
    fun `a stopped stream is never retried when the network is back, even if an error lands after`() {
        service.playStream(live)
        service.handleFinalError("Connection lost")
        service.stop()
        // A handler posted before the stop, running after it.
        service.handleFinalError("Connection lost")

        assertFalse(service.wantsRetryOnReconnect())
        assertTrue("stop must remove a posted recovery-exhausted handler", service.recoveryExhaustedHandler in removed)
    }

    @Test
    fun `a new stream after a stop is retried again`() {
        service.playStream(live)
        service.stop()
        service.playStream(vod)
        service.handleFinalError("Connection lost")

        assertTrue(service.wantsRetryOnReconnect())
    }

    @Test
    fun `a retry due while paused opens nothing and shows Paused until Play`() {
        service.playStream(vod)
        service.handleStreamEndedOrError("Network failed")
        every { player.playWhenReady } returns false
        val sourcesBefore = sources.size

        posted.single().first.run()

        assertEquals("a paused retry must not open the stream", sourcesBefore, sources.size)
        assertTrue(service.playbackState.value is PlaybackState.Paused)

        every { player.playWhenReady } returns true
        val posts = mutableListOf<Runnable>()
        every { handler.post(any()) } answers {
            posts += firstArg<Runnable>()
            true
        }
        service.runHeldRetry()
        posts.single().run()

        assertEquals(sourcesBefore + 1, sources.size)
    }

    @Test
    fun `a stop drops a retry held while paused`() {
        service.playStream(vod)
        service.handleStreamEndedOrError("Network failed")
        every { player.playWhenReady } returns false
        posted.single().first.run()
        service.stop()
        val posts = mutableListOf<Runnable>()
        every { handler.post(any()) } answers {
            posts += firstArg<Runnable>()
            true
        }

        service.runHeldRetry()

        assertTrue("nothing left to run after a stop", posts.isEmpty())
    }

    @Test
    fun `a stop clears the stream from the session, so nothing can reopen it from there`() {
        service.playStream(vod)

        service.stop()

        verify { player.stop() }
        verify { player.clearMediaItems() }
    }

    private fun pausedAt(position: Long) {
        every { player.playWhenReady } returns false
        every { player.playbackState } returns androidx.media3.common.Player.STATE_READY
        every { player.currentPosition } returns position
        every { player.duration } returns 600_000L
    }

    @Test
    fun `a film paused for 5 minutes is stopped, stays Paused, and Play reopens it there`() {
        service.playStream(vod)
        pausedAt(60_000L)

        service.freePausedHandler.run()

        verify(exactly = 2) { player.stop() } // playStream's, then the release
        assertEquals(PlaybackState.Paused(60_000L, 600_000L), service.playbackState.value)

        every { player.playWhenReady } returns true
        val posts = mutableListOf<Runnable>()
        every { handler.post(any()) } answers {
            posts += firstArg<Runnable>()
            true
        }
        val sourcesBefore = sources.size
        service.runHeldRetry()
        posts.single().run()

        assertEquals(sourcesBefore + 1, sources.size)
        verify { player.seekTo(60_000L) }
    }

    @Test
    fun `a seek while freed moves where Play reopens`() {
        service.playStream(vod)
        pausedAt(60_000L)
        service.freePausedHandler.run()

        service.seekTo(90_000L)

        assertEquals(PlaybackState.Paused(90_000L, 600_000L), service.playbackState.value)
    }

    @Test
    fun `live and a film playing again are never freed`() {
        service.playStream(live)
        pausedAt(60_000L)
        service.freePausedHandler.run()

        service.playStream(vod)
        every { player.playWhenReady } returns true
        service.freePausedHandler.run()

        verify(exactly = 2) { player.stop() } // the two playStream calls only
    }

    @Test
    fun `the wake lock goes when a film ends by itself or a final error stops it`() {
        val lock = mockk<android.os.PowerManager.WakeLock>(relaxed = true)
        every { lock.isHeld } returns true
        service.wakeLock = lock
        service.playStream(vod)
        every { player.duration } returns 600_000L

        service.handleStreamEndedOrError(null)
        verify(exactly = 1) { lock.release() }

        service.playStream(vod)
        service.handleFinalError("Connection lost")
        verify(exactly = 2) { lock.release() }
    }

    // The full-screen surface keeps the last frame unless newStreamStarting is set.

    @Test
    fun `a new stream starts from black until its first frame`() {
        service.playStream(live)
        assertTrue(service.newStreamStarting.value)

        service.onFirstFrameRendered()
        assertFalse(service.newStreamStarting.value)

        service.playStream(live.copy(streamUrl = "http://example.test/live/2.ts"))
        assertTrue(service.newStreamStarting.value)
    }

    @Test
    fun `a reconnect of the same stream keeps its last frame`() {
        service.playStream(live)
        service.onFirstFrameRendered()

        service.handleStreamEndedOrError("Network failed")
        posted.single().first.run()
        service.performSeamlessRecycle(live, currentPos = 10_000)

        assertFalse(service.newStreamStarting.value)
    }

    @Test
    fun `a film freed while paused keeps its last frame through the reopen`() {
        service.playStream(vod)
        service.onFirstFrameRendered()
        pausedAt(60_000L)

        service.freePausedHandler.run()
        every { player.playWhenReady } returns true
        val posts = mutableListOf<Runnable>()
        every { handler.post(any()) } answers {
            posts += firstArg<Runnable>()
            true
        }
        service.runHeldRetry()
        posts.single().run()

        assertFalse(service.newStreamStarting.value)
    }

    @Test
    fun `an error before the first frame keeps the screen black`() {
        service.playStream(live)

        service.handleStreamEndedOrError("Network failed")
        posted.single().first.run()

        assertTrue("the previous stream's last frame is still on the surface", service.newStreamStarting.value)
    }

    @Test
    fun `a stopped stream is not kept on screen`() {
        service.playStream(vod)
        service.onFirstFrameRendered()

        service.stop()

        assertTrue(service.newStreamStarting.value)
    }
}
