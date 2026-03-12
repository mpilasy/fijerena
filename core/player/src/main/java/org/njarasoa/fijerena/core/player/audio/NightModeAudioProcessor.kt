package org.njarasoa.fijerena.core.player.audio

import android.media.audiofx.DynamicsProcessing
import android.util.Log

class NightModeAudioProcessor {
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var isEnabled = false
    private var currentSessionId: Int = -1

    fun attach(sessionId: Int) {
        if (currentSessionId == sessionId) return
        detach()
        currentSessionId = sessionId
        try {
            // Priority 0 is fine, we just want to attach to the session
            dynamicsProcessing = DynamicsProcessing(0, sessionId, createNightModeConfig())
            dynamicsProcessing?.enabled = isEnabled
            Log.i(TAG, "DynamicsProcessing attached to session $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach DynamicsProcessing to session $sessionId", e)
        }
    }

    fun detach() {
        try {
            dynamicsProcessing?.release()
            dynamicsProcessing = null
            currentSessionId = -1
            Log.i(TAG, "DynamicsProcessing detached")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detach DynamicsProcessing", e)
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        dynamicsProcessing?.enabled = enabled
        Log.i(TAG, "Night Mode enabled: $enabled")
    }

    private fun createNightModeConfig(): DynamicsProcessing.Config {
        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2, // channel count
            true, // enable preEQ
            0, // preEQ bands
            true, // enable MBC
            1, // MBC bands (we'll just use 1 band for simplicity first)
            true, // enable postEQ
            0, // postEQ bands
            true // enable limiter
        )

        // Multi-Band Compressor (MBC) Setup
        // Compress the dynamic range
        val mbc = DynamicsProcessing.Mbc(true, true, 1)
        val mbcBand = DynamicsProcessing.MbcBand(
            true,
            20000.0f, // cutoff freq
            -40.0f, // attack time
            -20.0f, // release time
            2.0f, // ratio (2:1 compression)
            -30.0f, // threshold
            0.0f, // knee width
            0.0f, // noise gate threshold
            0.0f, // expander ratio
            0.0f, // preGain
            5.0f // postGain (makeup gain)
        )
        mbc.setBand(0, mbcBand)

        // Apply MBC to all channels
        builder.setMbcByChannelIndex(0, mbc)
        builder.setMbcByChannelIndex(1, mbc)

        // Limiter Setup
        // Catch any peaks that get through
        val limiter = DynamicsProcessing.Limiter(
            true,
            true,
            0, // link group
            -2.0f, // attack time
            -10.0f, // release time
            2.0f, // ratio
            -3.0f, // threshold
            0.0f // postGain
        )
        builder.setLimiterByChannelIndex(0, limiter)
        builder.setLimiterByChannelIndex(1, limiter)

        return builder.build()
    }

    companion object {
        private const val TAG = "NightModeAudioProcessor"
    }
}
