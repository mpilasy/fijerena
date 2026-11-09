package org.njarasoa.fijerena.core.player.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.util.Locale

/**
 * While [decodeToPcm] is true, reports every bitstream format (AC-3, E-AC-3, DTS, TrueHD, ...) as
 * unsupported, so a decoder takes it and the audio reaches [NightAudioProcessor] as PCM: no
 * processor can touch a bitstream (the Night Audio plan, NA-D5).
 */
@UnstableApi
class NightAudioSink(
    sink: AudioSink,
    private val decodeToPcm: () -> Boolean,
) : ForwardingAudioSink(sink) {
    override fun supportsFormat(format: Format): Boolean = !refuses(format) && super.supportsFormat(format)

    override fun getFormatSupport(format: Format): Int =
        if (refuses(format)) {
            AudioSink.SINK_FORMAT_UNSUPPORTED
        } else {
            super.getFormatSupport(format)
        }

    private fun refuses(format: Format): Boolean = decodeToPcm() && format.sampleMimeType != MimeTypes.AUDIO_RAW
}

/** What [encoding] (an `AudioSink` output encoding) means in Stats: [pcm], or [passthroughFormat] with the bitstream's name. */
fun audioOutputLabel(
    encoding: Int,
    pcm: String,
    passthroughFormat: String,
): String =
    if (Util.isEncodingLinearPcm(encoding)) {
        pcm
    } else {
        passthroughFormat.format(bitstreamName(encoding))
    }

/** Night Audio's gain for Stats: "−4.2 dB". A dB value is not translated. */
fun nightAudioGainLabel(gainDb: Float): String = String.format(Locale.ROOT, "%.1f dB", gainDb).replace('-', '−')

private fun bitstreamName(encoding: Int): String =
    when (encoding) {
        C.ENCODING_AC3 -> "AC3"
        C.ENCODING_E_AC3 -> "EAC3"
        C.ENCODING_E_AC3_JOC -> "EAC3-JOC"
        C.ENCODING_AC4 -> "AC4"
        C.ENCODING_DTS -> "DTS"
        C.ENCODING_DTS_HD -> "DTS-HD"
        C.ENCODING_DTS_UHD_P2 -> "DTS:X"
        C.ENCODING_DOLBY_TRUEHD -> "TrueHD"
        C.ENCODING_AAC_LC -> "AAC"
        C.ENCODING_MP3 -> "MP3"
        C.ENCODING_OPUS -> "Opus"
        else -> encoding.toString()
    }
