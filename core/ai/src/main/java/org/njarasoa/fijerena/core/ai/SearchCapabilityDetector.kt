package org.njarasoa.fijerena.core.ai

import android.content.Context
import android.os.Build
import android.util.Log
import org.njarasoa.fijerena.core.player.device.DeviceDetector
import org.njarasoa.fijerena.core.player.device.DeviceType
import org.tensorflow.lite.gpu.GpuDelegate

/**
 * Detects if the device is capable of running on-device AI features.
 */
class SearchCapabilityDetector(private val context: Context) {

    enum class SearchTier {
        /** Capable of running vector embeddings and cosine similarity in real-time. */
        PREMIUM,
        /** Fallback to classic keyword-based FTS search. */
        STANDARD
    }

    enum class AudioProcessingTier {
        /** Can run real-time TFLite audio inference (GPU/NPU). */
        REALTIME,
        /** Cannot run real-time inference. Audio effects only via platform APIs. */
        BASIC
    }

    /**
     * Determines the search tier based on device hardware and TFLite support.
     */
    fun detectTier(): SearchTier {
        val caps = DeviceDetector.detect()
        val manufacturer = Build.MANUFACTURER.uppercase()
        val model = Build.MODEL.uppercase()
        
        Log.d(TAG, "Detecting tier for device: $manufacturer $model, type: ${caps.deviceType}")

        // 1. Check for known premium hardware
        val isPremiumSoC = when {
            // NVIDIA Shield (all variants are Tegra X1/X1+)
            caps.deviceType == DeviceType.NVIDIA_SHIELD || 
            Build.DEVICE.uppercase().contains("DARCY") || 
            Build.DEVICE.uppercase().contains("MDARCY") -> true
            // Modern flagship Snapdragon (OnePlus 12/12R/13, etc.)
            model.startsWith("CPH25") || model.startsWith("CPH26") || 
            model.startsWith("PJD") || model.startsWith("PJR") -> true
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
        } catch (e: Throwable) {
            Log.i(TAG, "Device identified as STANDARD tier (No GPU acceleration available: ${e.message})")
            SearchTier.STANDARD
        }
    }

    /**
     * Determines if the device can sustain real-time TFLite audio inference.
     * REALTIME tier maps to PREMIUM search tier (same hardware requirements).
     */
    fun detectAudioTier(): AudioProcessingTier {
        return when (detectTier()) {
            SearchTier.PREMIUM -> AudioProcessingTier.REALTIME
            SearchTier.STANDARD -> AudioProcessingTier.BASIC
        }
    }

    companion object {
        private const val TAG = "SearchCapabilityDetector"
    }
}
