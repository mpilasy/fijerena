package org.njarasoa.fijerena.core.ai.audio

import android.content.Context
import android.util.Log
import org.njarasoa.fijerena.core.ai.SearchCapabilityDetector
import java.io.Closeable

/**
 * Manages the lifecycle of the AI dialogue boost processor.
 *
 * Handles initialization, tier gating (only creates processor on PREMIUM devices),
 * and resource cleanup. The processor is lazily initialized on first enable.
 */
class AudioEnhancementManager(private val context: Context) : Closeable {

    private var enhancer: AiSpeechEnhancer? = null
    private var _processor: DialogueBoostProcessor? = null

    /** Whether the device supports real-time AI audio processing */
    val isDialogueBoostAvailable: Boolean by lazy {
        val tier = SearchCapabilityDetector(context).detectTier()
        val available = tier == SearchCapabilityDetector.SearchTier.PREMIUM
        Log.i(TAG, "Dialogue boost available: $available (tier: $tier)")
        available
    }

    /**
     * Get or create the DialogueBoostProcessor.
     * Returns null if the device doesn't support real-time AI audio processing
     * or if TFLite model initialization fails.
     */
    fun getProcessor(): DialogueBoostProcessor? {
        if (!isDialogueBoostAvailable) return null

        if (_processor != null) return _processor

        return try {
            val speechEnhancer = AiSpeechEnhancer(context)
            if (!speechEnhancer.initialize()) {
                speechEnhancer.close()
                Log.w(TAG, "Failed to initialize AiSpeechEnhancer")
                return null
            }

            enhancer = speechEnhancer
            val processor = DialogueBoostProcessor(speechEnhancer)
            _processor = processor
            Log.i(TAG, "DialogueBoostProcessor created successfully")
            processor
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create DialogueBoostProcessor: ${e.message}", e)
            null
        }
    }

    override fun close() {
        _processor = null
        enhancer?.close()
        enhancer = null
    }

    companion object {
        private const val TAG = "AudioEnhancementMgr"
    }
}
