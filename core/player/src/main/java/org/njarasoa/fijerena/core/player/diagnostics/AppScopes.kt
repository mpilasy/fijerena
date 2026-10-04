package org.njarasoa.fijerena.core.player.diagnostics

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * The one way to build a scope that outlives any screen (singletons, services, repositories).
 *
 * A bare `CoroutineScope(SupervisorJob() + dispatcher)` hands any exception that escapes a
 * `launch` to the thread's uncaught-exception handler, which kills the process — one bad record,
 * one unreadable file, and the app is gone, on every launch if it happens at startup. Scopes from
 * here log the exception and record it in [CrashLog] instead; the failed coroutine ends, the
 * scope and its siblings keep running. See
 * docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-24.
 */
object AppScopes {
    fun create(
        name: String,
        dispatcher: CoroutineDispatcher,
    ): CoroutineScope =
        CoroutineScope(
            SupervisorJob() + dispatcher + CoroutineName(name) +
                CoroutineExceptionHandler { _, throwable ->
                    Log.e("AppScopes", "Uncaught exception in scope '$name'", throwable)
                    CrashLog.record("scope $name", throwable)
                },
        )
}
