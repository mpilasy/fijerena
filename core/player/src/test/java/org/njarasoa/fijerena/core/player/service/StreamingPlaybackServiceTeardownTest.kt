package org.njarasoa.fijerena.core.player.service

import android.os.Handler
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.PositionSave

/**
 * Teardown and position saves of [StreamingPlaybackService], behind a fake player and session.
 *
 * R-12: one teardown stage throwing must not skip the rest. R-04: saves are published on the
 * process-wide [StreamingPlaybackService.positionSaves], so they reach the player screen whatever
 * instance is playing. See docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md.
 */
@androidx.media3.common.util.UnstableApi
class StreamingPlaybackServiceTeardownTest {
    private val player = mockk<ExoPlayer>(relaxed = true)
    private val session = mockk<MediaSession>()
    private lateinit var service: StreamingPlaybackService

    @Before
    fun setup() {
        every { session.player } returns player
        every { session.release() } just runs
        every { player.playbackState } returns Player.STATE_READY
        every { player.currentPosition } returns 42_000L
        every { player.duration } returns 100_000L
        every { player.trackSelector } returns mockk<DefaultTrackSelector>(relaxed = true)
        service = StreamingPlaybackService()
        service.mediaSession = session
        service.mainHandler = mockk<Handler>(relaxed = true)
    }

    /** Runs [action] with a collector on [StreamingPlaybackService.positionSaves], returning what it saw. */
    private fun savesDuring(action: () -> Unit): List<PositionSave> =
        runBlocking {
            val saves = mutableListOf<PositionSave>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { StreamingPlaybackService.positionSaves.collect { saves += it } }
            action()
            yield()
            collector.cancel()
            saves
        }

    @Test
    fun `a player that throws on release still releases the session and resets the instance`() {
        every { player.release() } throws IllegalStateException("native release failed")
        service.publishInstance()
        assertSame(service, StreamingPlaybackService.getInstance())

        service.stopAndRelease()

        verify { session.release() }
        assertNull(StreamingPlaybackService.getInstance())
    }

    @Test
    fun `a session that throws on release still resets the instance`() {
        every { session.release() } throws IllegalStateException("session release failed")
        service.publishInstance()

        service.stopAndRelease()

        assertNull(StreamingPlaybackService.getInstance())
    }

    @Test
    fun `teardown publishes a final position save`() {
        val saves = savesDuring { service.stopAndRelease() }

        assertEquals(listOf(PositionSave(42_000L, 100_000L, isPaused = true)), saves)
    }

    @Test
    fun `turning subtitles off publishes the choice`() {
        val saves = savesDuring { service.disableSubtitles() }

        assertEquals(listOf(PositionSave(42_000L, 100_000L, isPaused = true, subtitleTrackIndex = -1)), saves)
    }

    @Test
    fun `saves from a recreated service reach the collector that started on the old one`() {
        // TV Home → return: the screen keeps collecting while its service is destroyed and a new
        // instance starts playing. A save tied to the first instance stopped after that (R-04).
        service.publishInstance()
        val saves =
            savesDuring {
                service.stopAndRelease()
                val recreated = StreamingPlaybackService()
                recreated.mediaSession = session
                recreated.mainHandler = mockk<Handler>(relaxed = true)
                recreated.publishInstance()
                recreated.disableSubtitles()
                recreated.stopAndRelease()
            }

        assertEquals(listOf(null, -1, null), saves.map { it.subtitleTrackIndex })
    }

    @Test
    fun `a save with no collector is dropped, not replayed to the next one`() {
        service.disableSubtitles()

        assertEquals(emptyList<PositionSave>(), savesDuring {})
    }
}
