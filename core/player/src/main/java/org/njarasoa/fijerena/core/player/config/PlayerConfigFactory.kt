@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.config

import android.content.Context
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import org.njarasoa.fijerena.core.player.device.DeviceDetector
import org.njarasoa.fijerena.core.player.device.DeviceType
import org.njarasoa.fijerena.core.player.network.NetworkMonitor

object PlayerConfigFactory {
    enum class ContentType {
        LIVE_TV,
        VOD
    }

    fun createLoadControl(contentType: ContentType = ContentType.VOD): DefaultLoadControl {
        return when (contentType) {
            ContentType.LIVE_TV -> {
                // IPTV optimized - fast zapping, minimal latency
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        2000,   // minBufferMs - 2s for live streams
                        5000,   // maxBufferMs - 5s max to avoid over-buffering
                        250,    // bufferForPlaybackMs - fast startup
                        500     // bufferForPlaybackAfterRebufferMs - quick recovery
                    )
                    .setBackBuffer(
                        0,      // backBufferDurationMs - no back buffer for live
                        false   // retainBackBufferFromKeyframe
                    )
                    .build()
            }
            ContentType.VOD -> {
                // VOD optimized - smooth playback during network fluctuations
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        15000,  // minBufferMs - 15s buffer for smooth playback
                        50000,  // maxBufferMs - 50s max buffer for network fluctuations
                        2500,   // bufferForPlaybackMs - 2.5s before starting playback
                        5000    // bufferForPlaybackAfterRebufferMs - 5s to recover from buffering
                    )
                    .setBackBuffer(
                        10000,  // backBufferDurationMs - 10s back buffer for seeking
                        true    // retainBackBufferFromKeyframe
                    )
                    .build()
            }
        }
    }

    /**
     * Create a DefaultTrackSelector with network-aware bitrate constraints.
     */
    fun createTrackSelector(context: Context): DefaultTrackSelector {
        val capabilities = DeviceDetector.detect()
        val isCellular = NetworkMonitor.currentNetworkType == NetworkType.CELLULAR

        // Use the non-deprecated constructor (Media3 1.9+)
        val parameters = DefaultTrackSelector.Parameters.Builder()
            .setPreferredAudioLanguage("en")
            .apply {
                // Set bitrate and resolution constraints based on network and device
                if (isCellular) {
                    // Be very conservative on cellular to prevent buffering
                    setMaxVideoSize(854, 480)
                    setMaxVideoBitrate(1_000_000) // 1 Mbps
                } else {
                    val (maxWidth, maxHeight) = capabilities.maxResolution
                    setMaxVideoSize(maxWidth, maxHeight)

                    val maxBitrate = when (capabilities.deviceType) {
                        DeviceType.NVIDIA_SHIELD -> if (capabilities.supports4K) 20_000_000 else 10_000_000
                        DeviceType.SONY_BRAVIA -> if (capabilities.supports4K) 20_000_000 else 10_000_000
                        DeviceType.CHROMECAST_TV -> if (capabilities.supports4K) 20_000_000 else 10_000_000
                        DeviceType.GENERIC_TV -> 10_000_000
                        DeviceType.GENERIC_MOBILE -> 5_000_000
                    }
                    setMaxVideoBitrate(maxBitrate)
                }

                // Prioritize hardware-accelerated codecs based on device capabilities
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
