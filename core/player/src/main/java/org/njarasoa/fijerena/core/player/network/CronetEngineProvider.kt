package org.njarasoa.fijerena.core.player.network

import android.content.Context
import android.util.Log
import androidx.media3.datasource.cronet.CronetUtil
import org.chromium.net.CronetEngine

/**
 * Singleton providing a shared [CronetEngine] for HTTP/2 and QUIC/HTTP3 streaming.
 *
 * Uses [CronetUtil.buildCronetEngine] (Media3 utility) which selects the best available
 * Cronet provider — Google Play Services Cronet on GMS devices, or falls back gracefully.
 *
 * Lifecycle:
 * - [init] called in [StreamingPlaybackService.onCreate]
 * - [release] called in [StreamingPlaybackService.onDestroy]
 */
@androidx.media3.common.util.UnstableApi
object CronetEngineProvider {

    private const val TAG = "CronetEngineProvider"

    @Volatile
    private var engine: CronetEngine? = null

    /**
     * Initialize the CronetEngine. Idempotent — subsequent calls are no-ops.
     * Returns silently if Cronet is unavailable (devices without Play Services).
     */
    fun init(context: Context) {
        if (engine != null) return
        try {
            engine = CronetUtil.buildCronetEngine(context)
            if (engine != null) {
                Log.i(TAG, "CronetEngine initialized")
            } else {
                Log.w(TAG, "CronetEngine unavailable, will fall back to DefaultHttpDataSource")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize CronetEngine: ${e.message}")
            engine = null
        }
    }

    /**
     * Returns the CronetEngine, or null if unavailable/not initialized.
     */
    fun get(): CronetEngine? = engine

    /**
     * Release the CronetEngine. Safe to call even if not initialized.
     */
    fun release() {
        engine?.shutdown()
        engine = null
    }
}
