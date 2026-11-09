package org.njarasoa.fijerena.core.player.service

import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.MediaSession
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.source.StreamingMediaSourceFactory

/** Night Audio during a bitstream re-prepares at the position, by the silent path (the Night Audio plan, NA-D5). */
@androidx.media3.common.util.UnstableApi
class StreamingPlaybackServiceNightAudioTest {
    private val player = mockk<ExoPlayer>(relaxed = true)
    private val sourceFactory = mockk<StreamingMediaSourceFactory>()
    private val handler = mockk<Handler>(relaxed = true)
    private val sources = mutableListOf<MediaSource>()
    private lateinit var service: StreamingPlaybackService

    private val live = PlayerMetadata(title = "News", streamUrl = "http://example.test/live/1.ts", isLive = true)
    private val ac3 =
        Format
            .Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AC3)
            .setChannelCount(6)
            .setSampleRate(48_000)
            .build()

    companion object {
        // Media3 1.9's ExoPlayer reads Build.DEVICE when its class loads.
        @JvmStatic
        @BeforeClass
        fun stubEmulatorCheck() {
            mockkStatic(Util::class)
            every { Util.isRunningOnEmulator() } returns false
        }

        @JvmStatic
        @AfterClass
        fun unstubEmulatorCheck() {
            unmockkStatic(Util::class)
        }
    }

    @Before
    fun setup() {
        every { player.playWhenReady } returns true
        every { player.playbackState } returns Player.STATE_READY
        every { player.currentPosition } returns 42_000L
        every { player.audioFormat } returns ac3
        every { sourceFactory.createMediaSource(any(), any(), any(), any(), any(), any()) } answers {
            mockk<MediaSource>().also { sources += it }
        }
        val session = mockk<MediaSession>()
        every { session.player } returns player
        service = spyk(StreamingPlaybackService())
        service.mediaSession = session
        service.mediaSourceFactory = sourceFactory
        service.mainHandler = handler
        every { service.outputTakesBitstream(any(), any()) } returns true
        service.playStream(live)
    }

    @Test
    fun `a bitstream re-prepares at its position, releasing the decoder first`() {
        val before = sources.size
        service.setNightAudio(true)

        assertEquals(before + 1, sources.size)
        assertTrue(service.isRecycling())
        verifyOrder {
            player.stop()
            player.setMediaSource(sources.last(), false)
            player.prepare()
        }
    }

    @Test
    fun `turning it off during decoded audio goes back to the bitstream the same way`() {
        service.setNightAudio(true)
        val before = sources.size

        service.setNightAudio(false)

        assertEquals(before + 1, sources.size)
    }

    @Test
    fun `the same value twice re-prepares only once`() {
        val before = sources.size
        service.setNightAudio(true)
        service.setNightAudio(true)
        assertEquals(before + 1, sources.size)
    }

    @Test
    fun `PCM output needs no re-prepare`() {
        every { service.outputTakesBitstream(any(), any()) } returns false
        val before = sources.size

        service.setNightAudio(true)

        assertEquals(before, sources.size)
        assertFalse(service.isRecycling())
        assertTrue(service.nightAudioProcessor.isEnabled)
    }

    @Test
    fun `nothing playing needs no re-prepare`() {
        every { player.playbackState } returns Player.STATE_IDLE
        val before = sources.size
        service.setNightAudio(true)
        assertEquals(before, sources.size)
    }

    @Test
    fun `a paused film stays paused`() {
        every { player.playWhenReady } returns false
        clearMocks(player, answers = false)
        service.setNightAudio(true)
        verify { player.playWhenReady = false }
        verify(exactly = 0) { player.playWhenReady = true }
    }

    @Test
    fun `a re-prepare is not a retry`() {
        service.setNightAudio(true)
        assertEquals(0, service.streamRetryCount.value)
    }
}
