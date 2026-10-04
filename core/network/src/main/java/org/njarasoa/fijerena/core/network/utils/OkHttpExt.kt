package org.njarasoa.fijerena.core.network.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * By using the asynchronous enqueue method, we avoid blocking the coroutine dispatcher thread.
 */
suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    // Cancelled while the response was on its way: nobody will read it, so close
                    // it here or its connection is never returned to the pool. See
                    // docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-14.
                    continuation.resume(response) { _, unread, _ -> unread.close() }
                }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(e)
                }
            },
        )

        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (ex: Throwable) {
                // Ignore cancel exception
            }
        }
    }
}
