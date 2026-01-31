package com.example.firstvideoplayer.core.player.config

import android.content.Context
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.firstvideoplayer.core.player.device.DeviceDetector
import com.example.firstvideoplayer.core.player.device.DeviceType

object PlayerConfigFactory {
    fun createLoadControl(): DefaultLoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                500,  // bufferForPlaybackMs - fast startup
                1000  // bufferForPlaybackAfterRebufferMs
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
            }
            .build()

        return DefaultTrackSelector(context).apply {
            setParameters(parameters)
        }
    }
}
