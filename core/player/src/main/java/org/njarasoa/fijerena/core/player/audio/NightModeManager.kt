package org.njarasoa.fijerena.core.player.audio

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log
import androidx.media3.common.C

/**
 * Manages Night Mode audio processing via Android's DynamicsProcessing API.
 *
 * Attaches a multi-band compressor and limiter to the player's audio session
 * to tame loud passages (explosions, music) while preserving dialogue clarity.
 * Operates at the Android audio HAL level — zero CPU overhead in the app process.
 *
 * Requires API 28+ (Android 9). All target devices meet this requirement.
 */
class NightModeManager {

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var currentSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    var enabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value && currentSessionId != C.AUDIO_SESSION_ID_UNSET) {
                attachProcessing(currentSessionId)
            } else if (!value) {
                releaseProcessing()
            }
        }

    /**
     * Attach to a new audio session. Called from onAudioSessionIdChanged.
     * If Night Mode is enabled, creates and configures a DynamicsProcessing instance.
     */
    fun attach(audioSessionId: Int) {
        releaseProcessing()
        currentSessionId = audioSessionId
        if (!enabled || audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        attachProcessing(audioSessionId)
    }

    private fun attachProcessing(audioSessionId: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.w(TAG, "DynamicsProcessing requires API 28+, current: ${Build.VERSION.SDK_INT}")
            return
        }

        try {
            val channelCount = 2

            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                channelCount,
                /* preEqInUse */ true,
                /* preEqBandCount */ 3,
                /* mbcInUse */ true,
                /* mbcBandCount */ 3,
                /* postEqInUse */ false,
                /* postEqBandCount */ 0,
                /* limiterInUse */ true
            ).build()

            dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config).apply {
                for (ch in 0 until channelCount) {
                    // Pre-EQ: boost speech frequencies (2-4kHz) for dialogue clarity
                    setPreEqBandByChannelIndex(
                        ch, 0,
                        DynamicsProcessing.EqBand(true, 200f, 0f)  // Low — no change
                    )
                    setPreEqBandByChannelIndex(
                        ch, 1,
                        DynamicsProcessing.EqBand(true, 3000f, 3f) // Mid (speech) — +3dB boost
                    )
                    setPreEqBandByChannelIndex(
                        ch, 2,
                        DynamicsProcessing.EqBand(true, 10000f, 0f) // High — no change
                    )

                    // Multi-band compressor: tame loud passages while preserving dynamics
                    // Band 0: Low frequencies (bass, explosions)
                    setMbcBandByChannelIndex(
                        ch, 0,
                        DynamicsProcessing.MbcBand(
                            /* enabled */ true,
                            /* cutoffFrequency */ 250f,
                            /* attackTime */ 5f,
                            /* releaseTime */ 100f,
                            /* ratio */ 4f,
                            /* threshold */ -25f,
                            /* kneeWidth */ 6f,
                            /* noiseGateThreshold */ -80f,
                            /* expanderRatio */ 1f,
                            /* preGain */ 0f,
                            /* postGain */ 0f
                        )
                    )
                    // Band 1: Mid frequencies (dialogue, music)
                    setMbcBandByChannelIndex(
                        ch, 1,
                        DynamicsProcessing.MbcBand(
                            /* enabled */ true,
                            /* cutoffFrequency */ 4000f,
                            /* attackTime */ 3f,
                            /* releaseTime */ 80f,
                            /* ratio */ 2.5f,
                            /* threshold */ -20f,
                            /* kneeWidth */ 6f,
                            /* noiseGateThreshold */ -80f,
                            /* expanderRatio */ 1f,
                            /* preGain */ 2f,    // Slight boost to speech band
                            /* postGain */ 0f
                        )
                    )
                    // Band 2: High frequencies (cymbals, sibilance)
                    setMbcBandByChannelIndex(
                        ch, 2,
                        DynamicsProcessing.MbcBand(
                            /* enabled */ true,
                            /* cutoffFrequency */ 20000f,
                            /* attackTime */ 2f,
                            /* releaseTime */ 60f,
                            /* ratio */ 3f,
                            /* threshold */ -22f,
                            /* kneeWidth */ 6f,
                            /* noiseGateThreshold */ -80f,
                            /* expanderRatio */ 1f,
                            /* preGain */ 0f,
                            /* postGain */ 0f
                        )
                    )

                    // Limiter: hard ceiling to prevent clipping
                    setLimiterByChannelIndex(
                        ch,
                        DynamicsProcessing.Limiter(
                            /* inUse */ true,
                            /* enabled */ true,
                            /* linkGroup */ 0,
                            /* attackTime */ 1f,
                            /* releaseTime */ 50f,
                            /* ratio */ 10f,
                            /* threshold */ -6f,
                            /* postGain */ 0f
                        )
                    )
                }
                setEnabled(true)
            }
            Log.i(TAG, "Night Mode attached to audio session $audioSessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DynamicsProcessing: ${e.message}", e)
            dynamicsProcessing = null
        }
    }

    /**
     * Release resources. Called on player destroy or when Night Mode is disabled.
     */
    fun release() {
        releaseProcessing()
        currentSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private fun releaseProcessing() {
        try {
            dynamicsProcessing?.setEnabled(false)
            dynamicsProcessing?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing DynamicsProcessing: ${e.message}")
        }
        dynamicsProcessing = null
    }

    companion object {
        private const val TAG = "NightModeManager"
    }
}
