package org.njarasoa.fijerena

import android.content.Context
import android.util.Log
import org.njarasoa.fijerena.core.network.ai.AiManager

object AiInitializer {
    fun init(context: Context) {
        if (BuildConfig.USE_AI) {
            try {
                // Use reflection to avoid direct hard dependency in slim builds
                val clazz = Class.forName("org.njarasoa.fijerena.core.ai.FijerenaAiProvider")
                val provider = clazz.getConstructor(Context::class.java).newInstance(context) as org.njarasoa.fijerena.core.network.ai.AiProvider
                AiManager.register(provider)
                Log.i("AiInitializer", "AI features initialized successfully")
            } catch (e: Exception) {
                Log.e("AiInitializer", "Failed to initialize AI features: ${e.message}")
            }
        } else {
            Log.i("AiInitializer", "AI features disabled for this build flavor")
        }
    }
}
