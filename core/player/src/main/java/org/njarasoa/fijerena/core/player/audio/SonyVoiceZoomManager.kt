package org.njarasoa.fijerena.core.player.audio

import android.content.Context
import android.content.Intent
import android.util.Log
import org.njarasoa.fijerena.core.player.device.DeviceDetector
import org.njarasoa.fijerena.core.player.device.DeviceType

/**
 * Utility to control Sony Bravia's hardware "Voice Zoom" feature via internal intents.
 * This provides a zero-latency alternative to software AI dialogue enhancement for Bravia users.
 */
object SonyVoiceZoomManager {
    private const val TAG = "SonyVoiceZoomManager"

    // Sony internal action to launch audio settings or toggle voice zoom
    private const val ACTION_SONY_AUDIO_SETTINGS = "com.sony.dtv.intent.action.AUDIO_SETTINGS"
    private const val EXTRA_VOICE_ZOOM = "voice_zoom_level"

    fun isSupported(): Boolean {
        return DeviceDetector.detect().deviceType == DeviceType.SONY_BRAVIA
    }

    /**
     * Attempts to enable or adjust the Sony Voice Zoom.
     * Some Sony TVs allow direct intent broadcasts, while others require launching the settings panel.
     */
    fun setVoiceZoom(context: Context, level: Int = 3) {
        if (!isSupported()) {
            Log.w(TAG, "Device is not a Sony Bravia. Voice Zoom not supported.")
            return
        }

        try {
            // First attempt: Broadcast intent to set level directly (if supported by specific Bravia firmware)
            val intent = Intent("com.sony.dtv.intent.action.SET_AUDIO_SOUND_MODE").apply {
                putExtra(EXTRA_VOICE_ZOOM, level)
            }
            context.sendBroadcast(intent)
            Log.i(TAG, "Sent Sony Voice Zoom broadcast with level $level")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Sony Voice Zoom broadcast", e)
        }
    }

    /**
     * Opens the Sony TV Audio Settings panel so the user can manually adjust Voice Zoom.
     */
    fun openAudioSettings(context: Context) {
        if (!isSupported()) return

        try {
            val intent = Intent(ACTION_SONY_AUDIO_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "Opened Sony Audio Settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Sony Audio Settings. Feature may not be available on this model.", e)

            // Fallback to standard Android sound settings
            try {
                val fallbackIntent = Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Fallback to Android sound settings also failed", fallbackEx)
            }
        }
    }
}
