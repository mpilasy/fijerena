package org.njarasoa.fijerena.core.network

import kotlinx.coroutines.CancellationException

sealed class Result<out T> {
    data class Success<T>(
        val data: T,
    ) : Result<T>()

    data class Error(
        val exception: Exception,
        val message: String? = null,
    ) : Result<Nothing>()
}

inline fun <T> resultOf(block: () -> T): Result<T> =
    try {
        Result.Success(block())
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }

suspend inline fun <T> suspendResultOf(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.Success(block())
    } catch (e: CancellationException) {
        // Cancellation, not a failure: rethrown, so the cancelling scope completes and nothing
        // reports or retries it as an error.
        throw e
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
