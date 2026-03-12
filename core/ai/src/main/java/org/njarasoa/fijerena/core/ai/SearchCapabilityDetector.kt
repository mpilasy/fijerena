package org.njarasoa.fijerena.core.ai

import android.content.Context
import android.os.Build
import android.util.Log
import org.njarasoa.fijerena.core.player.device.DeviceDetector
import org.njarasoa.fijerena.core.player.device.DeviceType
import org.tensorflow.lite.gpu.GpuDelegate

/**
 * Detects if the device is capable of running on-device AI semantic search.
 */
class SearchCapabilityDetector(private val context: Context) {

    enum class SearchTier {
        /** Capable of running vector embeddings and cosine similarity in real-time. */
        PREMIUM,
        /** Fallback to classic keyword-based FTS search. */
        STANDARD
    }

    /**
     * Determines the search tier based on device hardware and TFLite support.
     */
    fun detectTier(): SearchTier {
        val caps = DeviceDetector.detect()
        val manufacturer = Build.MANUFACTURER.uppercase()
        val model = Build.MODEL.uppercase()
        
        // 1. Check for known premium hardware
        val isPremiumSoC = when {
            // NVIDIA Shield
            caps.deviceType == DeviceType.NVIDIA_SHIELD -> true
            // Modern flagship Snapdragon (OnePlus 13 Snapdragon 8 Elite, etc.)
            model.contains("CPH2655") -> true // OnePlus 13
            // Sony Bravia premium SoC (VH2 line)
            caps.deviceType == DeviceType.SONY_BRAVIA && model.contains("VH2") -> true
            else -> false
        }

        if (isPremiumSoC) {
            Log.i(TAG, "Device identified as PREMIUM tier via hardware signature ($model)")
            return SearchTier.PREMIUM
        }

        // 2. Runtime check for GPU acceleration support
        return try {
            GpuDelegate().use {
                Log.i(TAG, "Device identified as PREMIUM tier via TFLite GPU support")
                SearchTier.PREMIUM
            }
        } catch (e: Exception) {
            Log.i(TAG, "Device identified as STANDARD tier (No GPU acceleration available)")
            SearchTier.STANDARD
        }
    }

    companion object {
        private const val TAG = "SearchCapabilityDetector"
    }
}
