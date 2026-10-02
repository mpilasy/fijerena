package org.njarasoa.fijerena.core.player.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class PlaybackServiceConnection(
    private val context: Context,
) {
    @Volatile private var controllerFuture: ListenableFuture<MediaController>? = null

    @Volatile private var controller: MediaController? = null

    /**
     * Each collection owns its own controller future. They used to share [controllerFuture]: when
     * PlaybackViewModel restarted a collection after a service restart, the old collection's
     * cleanup ran after the new one had started and released the *new* future through the shared
     * field, leaving the ViewModel holding a released controller (no audio or subtitle tracks, no
     * chapters). Cleanup now releases only its own future, and clears the shared fields only if
     * they still point at it. See docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-06.
     */
    fun connect(): Flow<MediaController?> =
        callbackFlow {
            val sessionToken =
                SessionToken(
                    context,
                    ComponentName(context, StreamingPlaybackService::class.java),
                )
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future

            future.addListener(
                {
                    val built =
                        try {
                            future.get()
                        } catch (e: Exception) {
                            // cancellation-ok: future listener on the direct executor, not suspend
                            null
                        }
                    if (controllerFuture === future) controller = built
                    trySend(built)
                },
                MoreExecutors.directExecutor(),
            )

            awaitClose {
                try {
                    MediaController.releaseFuture(future)
                } catch (e: Exception) {
                    // cancellation-ok: awaitClose's block is not suspend
                }
                if (controllerFuture === future) {
                    controllerFuture = null
                    controller = null
                }
            }
        }

    fun disconnect() {
        controllerFuture?.let { future ->
            try {
                MediaController.releaseFuture(future)
            } catch (e: Exception) {
                // cancellation-ok: non-suspend; ignore release errors
            }
        }
        controllerFuture = null
        controller = null
    }

    fun getController(): MediaController? = controller

    fun getService(): StreamingPlaybackService? = StreamingPlaybackService.getInstance()
}
