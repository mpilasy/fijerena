package org.njarasoa.fijerena.core.player.audio

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import org.njarasoa.fijerena.core.player.device.DeviceDetector
import org.njarasoa.fijerena.core.player.device.DeviceType

/**
 * Manages Sony Bravia Voice Zoom hardware feature integration.
 *
 * Voice Zoom (ClearAudio+) is a hardware feature of Sony's XR Cognitive Processor
 * on Bravia TVs. This manager attempts programmatic control via SettingsProvider
 * properties, falling back to launching Sony's sound settings activity.
 *
 * Only active on Sony Bravia devices.
 */
class BraviaVoiceZoomManager(private val context: Context) {

    /** Whether this device is a Sony Bravia TV that may support Voice Zoom */
    val isAvailable: Boolean by lazy {
        DeviceDetector.detect().deviceType == DeviceType.SONY_BRAVIA
    }

    /** Current Voice Zoom state (null if unavailable or unknown) */
    var enabled: Boolean = false
        private set

    private var settingsProviderWorks: Boolean? = null

    /**
     * Try to enable/disable Voice Zoom programmatically.
     * Returns true if the setting was applied, false if user must adjust manually.
     */
    fun setVoiceZoom(enable: Boolean): Boolean {
        if (!isAvailable) return false

        // Try SettingsProvider approach first (cached after first attempt)
        if (settingsProviderWorks != false) {
            if (trySettingsProvider(enable)) {
                enabled = enable
                settingsProviderWorks = true
                Log.i(TAG, "Voice Zoom ${if (enable) "enabled" else "disabled"} via SettingsProvider")
                return true
            }
            settingsProviderWorks = false
        }

        // SettingsProvider didn't work — launch Sony sound settings
        return false
    }

    /**
     * Launch Sony's sound settings activity as a fallback when programmatic
     * control isn't available.
     */
    fun openSonySettings(): Boolean {
        return try {
            // Try Sony-specific sound settings first
            val sonyIntent = Intent().apply {
                setClassName("com.sony.dtv.settings", "com.sony.dtv.settings.SoundSettingsActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(sonyIntent, 0) != null) {
                context.startActivity(sonyIntent)
                Log.i(TAG, "Launched Sony sound settings")
                return true
            }

            // Fallback: try generic Sony TV settings path
            val altIntent = Intent().apply {
                setClassName("com.sony.dtv.settings", "com.sony.dtv.settings.sound.SoundActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(altIntent, 0) != null) {
                context.startActivity(altIntent)
                Log.i(TAG, "Launched Sony sound settings (alt path)")
                return true
            }

            // Last resort: Android sound settings
            val genericIntent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(genericIntent)
            Log.i(TAG, "Launched generic sound settings (Sony-specific not found)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open sound settings: ${e.message}")
            false
        }
    }

    /**
     * Read the current Voice Zoom state from SettingsProvider.
     * Returns null if the setting doesn't exist on this device.
     */
    fun readCurrentState(): Boolean? {
        if (!isAvailable) return null

        val resolver = context.contentResolver
        return try {
            // Try different known property names for Voice Zoom
            for (key in VOICE_ZOOM_KEYS) {
                val value = tryReadSetting(resolver, key)
                if (value != null) {
                    enabled = value != 0
                    return enabled
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read Voice Zoom state: ${e.message}")
            null
        }
    }

    private fun trySettingsProvider(enable: Boolean): Boolean {
        val resolver = context.contentResolver
        val value = if (enable) 1 else 0

        for (key in VOICE_ZOOM_KEYS) {
            try {
                // Try Settings.System first
                if (tryReadSetting(resolver, key) != null) {
                    val success = Settings.System.putInt(resolver, key, value)
                    if (success) return true
                }
            } catch (_: SecurityException) {
                // Permission denied — expected on many Bravia models
            } catch (_: Exception) {
                // Setting doesn't exist
            }
        }
        return false
    }

    private fun tryReadSetting(resolver: ContentResolver, key: String): Int? {
        return try {
            Settings.System.getInt(resolver, key)
        } catch (_: Settings.SettingNotFoundException) {
            try {
                Settings.Global.getInt(resolver, key)
            } catch (_: Settings.SettingNotFoundException) {
                null
            }
        }
    }

    companion object {
        private const val TAG = "BraviaVoiceZoom"

        // Known Sony Bravia SettingsProvider keys for Voice Zoom
        private val VOICE_ZOOM_KEYS = arrayOf(
            "voice_zoom_level",
            "sony_voice_zoom",
            "clear_audio_plus",
            "voice_zoom"
        )
    }
}
