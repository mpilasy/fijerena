package org.njarasoa.fijerena.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * Current epoch seconds as observable state, refreshed every 60s.
 *
 * Prefer this over [rememberNowEpochSeconds] when the value is threaded through a list: holding
 * the [State] and reading `.value` only in the leaf that needs it keeps the tick from invalidating
 * every scope it passed through on the way down. [rememberNowEpochSeconds] reads it immediately,
 * so the caller itself recomposes each tick.
 */
@Composable
fun rememberNowEpochSecondsState(): State<Long> {
    val nowEpochSeconds = remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowEpochSeconds.longValue = System.currentTimeMillis() / 1000
        }
    }
    return nowEpochSeconds
}

/**
 * Current epoch seconds, refreshed every 60s — avoids per-item `System.currentTimeMillis()`
 * calls when many items in a list each need to know "now" (e.g. EPG on-air checks).
 */
@Composable
fun rememberNowEpochSeconds(): Long = rememberNowEpochSecondsState().value
