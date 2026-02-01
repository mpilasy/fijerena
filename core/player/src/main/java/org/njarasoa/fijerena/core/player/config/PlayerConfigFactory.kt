@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.config

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import org.njarasoa.fijerena.core.player.device.DeviceDetector
import org.njarasoa.fijerena.core.player.device.DeviceType

object PlayerConfigFactory {
    fun createLoadControl(): DefaultLoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2000,  // minBufferMs - minimal buffering for live streams
                5000,  // maxBufferMs - don't over-buffer for live content
                250,   // bufferForPlaybackMs - faster zapping/channel switching
                500    // bufferForPlaybackAfterRebufferMs - faster recovery
            )
            .setBackBuffer(
                0,     // backBufferDurationMs - no back buffer for live streams
                false  // retainBackBufferFromKeyframe
            )
            .build()
    }

    fun createTrackSelector(context: Context): DefaultTrackSelector {
        val capabilities = DeviceDetector.detect()

        // Use the non-deprecated constructor (Media3 1.9+)
        val parameters = DefaultTrackSelector.Parameters.Builder()
            .setPreferredAudioLanguage("en")
            .apply {
                // Set max resolution based on device
                val (maxWidth, maxHeight) = capabilities.maxResolution
                setMaxVideoSize(maxWidth, maxHeight)

                // Set bitrate constraints
                val maxBitrate = when (capabilities.deviceType) {
                    DeviceType.NVIDIA_SHIELD -> if (capabilities.supports4K) 20_000_000 else 10_000_000
                    DeviceType.SONY_BRAVIA -> if (capabilities.supports4K) 20_000_000 else 10_000_000
                    DeviceType.CHROMECAST_TV -> 5_000_000
                    DeviceType.GENERIC_TV -> 10_000_000
                    DeviceType.GENERIC_MOBILE -> 5_000_000
                }
                setMaxVideoBitrate(maxBitrate)

                // Prioritize hardware-accelerated codecs based on device capabilities
                // Media3 will select the first available codec from the preferredMimeTypes list
                if (capabilities.preferredCodecs.isNotEmpty()) {
                    setPreferredVideoMimeTypes(*capabilities.preferredCodecs.toTypedArray())
                }
            }
            .build()

        return DefaultTrackSelector(context).apply {
            setParameters(parameters)
        }
    }
}
