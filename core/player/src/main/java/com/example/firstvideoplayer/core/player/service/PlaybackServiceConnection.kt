package com.example.firstvideoplayer.core.player.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class PlaybackServiceConnection(private val context: Context) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    fun connect(): Flow<MediaController?> = callbackFlow {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, StreamingPlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture?.addListener(
            {
                try {
                    controller = controllerFuture?.get()
                    trySend(controller)
                } catch (e: Exception) {
                    trySend(null)
                }
            },
            MoreExecutors.directExecutor()
        )

        awaitClose {
            controllerFuture?.let { future ->
                try {
                    MediaController.releaseFuture(future)
                } catch (e: Exception) {
                    // Ignore
                }
            }
            controllerFuture = null
            controller = null
        }
    }

    fun disconnect() {
        controllerFuture?.let { future ->
            try {
                MediaController.releaseFuture(future)
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
        controllerFuture = null
        controller = null
    }

    fun getController(): MediaController? = controller
}
