package org.njarasoa.fijerena.core.player.service

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.model.PositionSave
import org.njarasoa.fijerena.core.player.source.StreamingMediaSourceFactory

/** Subtitles Off and track picks belong to one item: a new item starts on its own default tracks. */
@androidx.media3.common.util.UnstableApi
class StreamingPlaybackServiceTrackResetTest {
    private val player = mockk<ExoPlayer>(relaxed = true)
    private val sourceFactory = mockk<StreamingMediaSourceFactory>()

    // DefaultTrackSelector.Parameters needs Android's SparseArray, so the selector and its
    // parameters are mocks: the test checks what the service asks of them.
    private val built = mockk<DefaultTrackSelector.Parameters>()
    private val builder =
        mockk<DefaultTrackSelector.Parameters.Builder>().also {
            every { it.setTrackTypeDisabled(any(), any()) } returns it
            every { it.clearOverrides() } returns it
            every { it.setPreferredAudioLanguages(*anyVararg()) } returns it
            every { it.build() } returns built
        }
    private val selector =
        mockk<DefaultTrackSelector>(relaxed = true).also {
            every { it.parameters } returns mockk { every { buildUpon() } returns builder }
        }
    private lateinit var service: StreamingPlaybackService

    private val film = PlayerMetadata(title = "Film", streamUrl = "http://example.test/movie/1.mkv")
    private val otherFilm = PlayerMetadata(title = "Other film", streamUrl = "http://example.test/movie/2.mkv")
    private val frenchFilm =
        PlayerMetadata(title = "French film", streamUrl = "http://example.test/movie/3.mkv", preferredAudioLanguages = listOf("fr", "ja"))

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
        every { player.playbackState } returns Player.STATE_READY
        every { player.trackSelector } returns selector
        every { sourceFactory.createMediaSource(any(), any(), any(), any(), any(), any()) } returns mockk<MediaSource>()
        val session = mockk<MediaSession>()
        every { session.player } returns player
        service = spyk(StreamingPlaybackService())
        service.mediaSession = session
        service.mediaSourceFactory = sourceFactory
        service.mainHandler = mockk<Handler>(relaxed = true)
        service.playStream(film)
        clearMocks(builder, selector, answers = false, recordedCalls = true)
    }

    @Test
    fun `a new item turns subtitles back on and drops the previous picks`() {
        service.playStream(otherFilm)
        verifyOrder {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            builder.clearOverrides()
            selector.setParameters(built)
        }
    }

    @Test
    fun `a new item prefers the profile's audio languages, else English`() {
        service.playStream(frenchFilm)
        verify { builder.setPreferredAudioLanguages("fr", "ja") }

        service.playStream(otherFilm)
        verify { builder.setPreferredAudioLanguages("en") }
    }

    @Test
    fun `subtitles off from the profile's languages are not saved`() {
        val saves = mutableListOf<PositionSave>()
        runBlocking {
            val collector = launch(Dispatchers.Unconfined) { StreamingPlaybackService.positionSaves.collect { saves += it } }
            service.disableSubtitles(persist = false)
            service.selectSubtitleTrack(-1, persist = false)
            service.disableSubtitles()
            collector.cancel()
        }
        verify(exactly = 3) { builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true) }
        // Only the viewer's own Off is saved.
        assertEquals(listOf(-1), saves.map { it.subtitleTrackIndex })
    }

    @Test
    fun `the same item played again keeps the viewer's picks`() {
        service.playStream(film, startPositionMs = 42_000L)
        verify(exactly = 0) { selector.setParameters(any<DefaultTrackSelector.Parameters>()) }
    }
}
