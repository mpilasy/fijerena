package org.njarasoa.fijerena.core.player.audio

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * The player's renderers, with [processor] in the audio chain. The MediaCodec and FFmpeg audio
 * renderers both get this sink, and float output stays off (the factory's default), because the
 * sink's processors do not run on a float output.
 */
@UnstableApi
class NightAudioRenderersFactory(
    context: Context,
    private val processor: NightAudioProcessor,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink =
        DefaultAudioSink
            .Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
            .setAudioProcessors(arrayOf(processor))
            .build()
}
