package org.njarasoa.fijerena.core.ui.utils

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * [launch] with an exception boundary, for `viewModelScope` (and composition scopes). An
 * exception escaping a plain `viewModelScope.launch` crashes the app; here it is logged, recorded
 * in [CrashLog] under [name], and handed to [onError] so the screen can show its error state.
 * Cancellation still propagates. Children launched inside [block] are covered too: a failing
 * child fails [block] instead of reaching the scope. See
 * docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-09.
 */
fun CoroutineScope.launchGuarded(
    name: String,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    onError: (Throwable) -> Unit = {},
    block: suspend CoroutineScope.() -> Unit,
): Job =
    launch(context, start) {
        try {
            coroutineScope(block)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("launchGuarded", "Coroutine '$name' failed", e)
            CrashLog.record("coroutine $name", e)
            onError(e)
        }
    }
