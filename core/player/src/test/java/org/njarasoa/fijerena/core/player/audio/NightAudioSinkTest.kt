package org.njarasoa.fijerena.core.player.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@androidx.media3.common.util.UnstableApi
class NightAudioSinkTest {
    private val delegate =
        mockk<AudioSink> {
            every { supportsFormat(any()) } returns true
            every { getFormatSupport(any()) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        }

    private fun format(mime: String) =
        Format
            .Builder()
            .setSampleMimeType(mime)
            .setChannelCount(6)
            .setSampleRate(48_000)
            .build()

    private val pcm =
        Format
            .Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setChannelCount(6)
            .setSampleRate(48_000)
            .build()

    @Test
    fun `AC-3 and E-AC-3 are unsupported while on`() {
        val sink = NightAudioSink(delegate) { true }
        listOf(MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_TRUEHD).forEach { mime ->
            assertFalse(sink.supportsFormat(format(mime)))
            assertEquals(AudioSink.SINK_FORMAT_UNSUPPORTED, sink.getFormatSupport(format(mime)))
        }
    }

    @Test
    fun `AC-3 and E-AC-3 are supported while off`() {
        val sink = NightAudioSink(delegate) { false }
        assertTrue(sink.supportsFormat(format(MimeTypes.AUDIO_AC3)))
        assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY, sink.getFormatSupport(format(MimeTypes.AUDIO_E_AC3)))
    }

    @Test
    fun `PCM is still taken while on`() {
        val sink = NightAudioSink(delegate) { true }
        assertTrue(sink.supportsFormat(pcm))
        assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY, sink.getFormatSupport(pcm))
    }

    @Test
    fun `the output line names PCM or the bitstream`() {
        assertEquals("PCM", audioOutputLabel(C.ENCODING_PCM_16BIT, "PCM", "passthrough %1\$s"))
        assertEquals("passthrough AC3", audioOutputLabel(C.ENCODING_AC3, "PCM", "passthrough %1\$s"))
        assertEquals("passthrough EAC3-JOC", audioOutputLabel(C.ENCODING_E_AC3_JOC, "PCM", "passthrough %1\$s"))
    }

    @Test
    fun `the gain line uses a real minus`() {
        assertEquals("−4.2 dB", nightAudioGainLabel(-4.2f))
        assertEquals("6.0 dB", nightAudioGainLabel(6f))
    }
}
