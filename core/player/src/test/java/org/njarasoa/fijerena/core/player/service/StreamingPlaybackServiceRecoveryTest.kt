package org.njarasoa.fijerena.core.player.service

import android.os.Handler
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.MediaSession
import io.mockk.every
import io.mockk.mockk
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
 * docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-01. (F-02, the live HLS recycle
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
}
