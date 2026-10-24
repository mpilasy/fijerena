package org.njarasoa.fijerena.core.ui.components

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.suspendCancellableCoroutine
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/** Holds the player's video view so the frame on screen can be copied (scene colour of the wait indicator). */
class SceneFrame {
    // Weak: the player view belongs to the composition, and this holder may outlive it.
    internal var videoView: WeakReference<View>? = null

    /** A [width]×[height] copy of the frame on screen, or null (no view, no frame, copy refused). Main thread. */
    suspend fun capture(
        width: Int = 64,
        height: Int = 36,
    ): Bitmap? {
        val view = videoView?.get() ?: return null
        return when (view) {
            is SurfaceView -> copySurface(view, width, height)
            is TextureView -> copyTexture(view, width, height)
            else -> null
        }
    }

    /**
     * The frame on screen only when it is the playing stream's own kept frame: null while a new
     * stream starts (the surface still holds the previous stream's frame behind the black shutter).
     */
    @OptIn(UnstableApi::class)
    suspend fun captureKept(): Bitmap? = if (StreamingPlaybackService.getInstance()?.newStreamStarting?.value == false) capture() else null

    // Null while the TextureView has no surface yet.
    private fun copyTexture(
        view: TextureView,
        width: Int,
        height: Int,
    ): Bitmap? =
        try {
            view.getBitmap(width, height)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Frame copy failed: $e")
            null
        }

    private suspend fun copySurface(
        view: SurfaceView,
        width: Int,
        height: Int,
    ): Bitmap? {
        // No surface yet, or already gone: nothing to copy.
        if (!view.holder.surface.isValid) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return suspendCancellableCoroutine { continuation ->
            try {
                PixelCopy.request(
                    view,
                    bitmap,
                    { result ->
                        if (continuation.isActive) continuation.resume(bitmap.takeIf { result == PixelCopy.SUCCESS })
                    },
                    Handler(Looper.getMainLooper()),
                )
            } catch (e: IllegalArgumentException) {
                // PixelCopy refuses a surface that went invalid since the check above.
                Log.w(TAG, "Frame copy refused: $e")
                continuation.resume(null)
            }
        }
    }

    private companion object {
        const val TAG = "SceneFrame"
    }
}

@Composable
fun rememberSceneFrame(): SceneFrame = remember { SceneFrame() }
