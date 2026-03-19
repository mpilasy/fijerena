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

    /** Indicates if the HAL-level effect was successfully created and enabled */
    var isActuallyActive: Boolean = false
        private set

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

            val config =
                DynamicsProcessing.Config
                    .Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        channelCount,
                        // preEqInUse
                        true,
                        // preEqBandCount
                        3,
                        // mbcInUse
                        true,
                        // mbcBandCount
                        3,
                        // postEqInUse
                        false,
                        // postEqBandCount
                        0,
                        // limiterInUse
                        true,
                    ).build()

            dynamicsProcessing =
                DynamicsProcessing(0, audioSessionId, config).apply {
                    // ... (existing config)
                    for (ch in 0 until channelCount) {
                        // Pre-EQ: boost speech frequencies (2-4kHz) for dialogue clarity
                        setPreEqBandByChannelIndex(ch, 0, DynamicsProcessing.EqBand(true, 200f, 0f))
                        setPreEqBandByChannelIndex(ch, 1, DynamicsProcessing.EqBand(true, 3000f, 3f))
                        setPreEqBandByChannelIndex(ch, 2, DynamicsProcessing.EqBand(true, 10000f, 0f))

                        // Multi-band compressor: tame loud passages while preserving dynamics
                        setMbcBandByChannelIndex(ch, 0, DynamicsProcessing.MbcBand(true, 250f, 5f, 100f, 4f, -25f, 6f, -80f, 1f, 0f, 0f))
                        setMbcBandByChannelIndex(ch, 1, DynamicsProcessing.MbcBand(true, 4000f, 3f, 80f, 2.5f, -20f, 6f, -80f, 1f, 2f, 0f))
                        setMbcBandByChannelIndex(ch, 2, DynamicsProcessing.MbcBand(true, 20000f, 2f, 60f, 3f, -22f, 6f, -80f, 1f, 0f, 0f))

                        // Limiter: hard ceiling
                        setLimiterByChannelIndex(ch, DynamicsProcessing.Limiter(true, true, 0, 1f, 50f, 10f, -6f, 0f))
                    }
                    setEnabled(true)
                    isActuallyActive = true
                }
            Log.i(TAG, "Night Mode attached to session $audioSessionId. Enabled: ${dynamicsProcessing?.enabled}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DynamicsProcessing on session $audioSessionId: ${e.message}", e)
            dynamicsProcessing = null
            isActuallyActive = false
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
        isActuallyActive = false
    }

    companion object {
        private const val TAG = "NightModeManager"
    }
}
