package org.njarasoa.fijerena.core.player.service

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/plans/20261004_playback-capability-errors-plan.md → P1, P2. The track and codecs values are
 * the ones Media3 reported on darcy and the Xperia XZ2 Compact for the 2026-10-04 test clips.
 */
@androidx.media3.common.util.UnstableApi
class PlaybackCapabilityTest {
    private val dolbyVision5 =
        Format
            .Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs("dvhe.05.09")
            .setWidth(3840)
            .setHeight(2160)
            .build()
    private val hevc8k =
        Format
            .Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setCodecs("hvc1.2.4.L186.B0")
            .setWidth(7680)
            .setHeight(4320)
            .build()
    private val aac = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()

    private fun group(
        format: Format,
        support: Int,
        selected: Boolean,
    ) = Tracks.Group(TrackGroup(format), false, intArrayOf(support), booleanArrayOf(selected))

    @Test
    fun `video with no decoder and nothing selected is unplayable`() {
        val tracks =
            Tracks(
                listOf(
                    group(dolbyVision5, C.FORMAT_UNSUPPORTED_TYPE, selected = false),
                    group(aac, C.FORMAT_HANDLED, selected = true),
                ),
            )
        assertEquals(dolbyVision5, unplayableVideoFormat(tracks))
    }

    @Test
    fun `a video track that is only over the decoder's rated limits is not flagged`() {
        // Media3 still selects it and both test devices played 4K60 at 150 Mbps this way.
        val tracks = Tracks(listOf(group(hevc8k, C.FORMAT_EXCEEDS_CAPABILITIES, selected = true)))
        assertNull(unplayableVideoFormat(tracks))
    }

    @Test
    fun `a playable video track that is deselected is not flagged`() {
        val tracks = Tracks(listOf(group(hevc8k, C.FORMAT_HANDLED, selected = false)))
        assertNull(unplayableVideoFormat(tracks))
    }

    @Test
    fun `audio-only streams and empty track lists are not flagged`() {
        assertNull(unplayableVideoFormat(Tracks(listOf(group(aac, C.FORMAT_HANDLED, selected = true)))))
        assertNull(unplayableVideoFormat(Tracks.EMPTY))
    }

    @Test
    fun `dolby vision profile comes from the codecs string`() {
        assertEquals(5, dolbyVisionProfile("dvhe.05.09"))
        assertEquals(8, dolbyVisionProfile("dvh1.08.06"))
        assertNull(dolbyVisionProfile("hvc1.2.4.L186.B0"))
        assertNull(dolbyVisionProfile("dvhe"))
        assertNull(dolbyVisionProfile(null))
    }

    @Test
    fun `codec label names the codec and resolution`() {
        assertEquals("HEVC 7680×4320", codecLabel(hevc8k))
        assertEquals("Dolby Vision 3840×2160", codecLabel(dolbyVision5))
        assertEquals("MPEG-2", codecLabel(Format.Builder().setSampleMimeType("video/mpeg2").build()))
        assertEquals("X-UNKNOWN", codecLabel(Format.Builder().setSampleMimeType("video/x-unknown").build()))
        assertEquals("?", codecLabel(Format.Builder().build()))
    }

    @Test
    fun `a codec error before the first frame on a format the decoder doesn't handle is final`() {
        // 8K HEVC: DECODING_FAILED on darcy, DECODER_INIT_FAILED on the Xperia, both EXCEEDS.
        assertTrue(isFinalCodecError(PlaybackException.ERROR_CODE_DECODING_FAILED, C.FORMAT_EXCEEDS_CAPABILITIES, false))
        assertTrue(isFinalCodecError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, C.FORMAT_EXCEEDS_CAPABILITIES, false))
        assertTrue(isFinalCodecError(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, C.FORMAT_UNSUPPORTED_SUBTYPE, false))
    }

    @Test
    fun `a codec error after frames played, or on a handled format, keeps the retry`() {
        assertFalse(isFinalCodecError(PlaybackException.ERROR_CODE_DECODING_FAILED, C.FORMAT_EXCEEDS_CAPABILITIES, true))
        assertFalse(isFinalCodecError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, C.FORMAT_HANDLED, false))
    }

    @Test
    fun `network errors are never final`() {
        assertFalse(isFinalCodecError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, C.FORMAT_HANDLED, false))
        assertFalse(isFinalCodecError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, C.FORMAT_EXCEEDS_CAPABILITIES, false))
    }
}
