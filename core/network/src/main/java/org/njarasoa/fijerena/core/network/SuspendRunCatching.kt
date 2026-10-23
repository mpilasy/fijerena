package org.njarasoa.fijerena.core.network

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for suspend code: a [CancellationException] is rethrown instead of being
 * returned as a failure, so a cancelled job stops rather than carrying on with stale work.
 * Every other throwable is wrapped, exactly like [runCatching].
 */
inline fun <T> suspendRunCatching(block: () -> T): kotlin.Result<T> =
    try {
        kotlin.Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        kotlin.Result.failure(e)
    }
