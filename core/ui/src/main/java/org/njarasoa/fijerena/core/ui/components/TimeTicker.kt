package org.njarasoa.fijerena.core.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.AppSettings

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

/**
 * One-time "long-press/hold to favorite" hint — true for the first few seconds ever shown
 * (marked seen as soon as it's shown), then auto-dismisses. `AppSettings.hasSeenFavoriteHint`
 * makes this a once-ever hint across app restarts, not just once per composition.
 */
@Composable
fun rememberFavoriteHintVisible(context: Context = LocalContext.current): Boolean {
    var visible by remember {
        mutableStateOf(!AppSettings(context.applicationContext).hasSeenFavoriteHint)
    }
    LaunchedEffect(visible) {
        if (visible) {
            AppSettings(context.applicationContext).hasSeenFavoriteHint = true
            delay(4000)
            visible = false
        }
    }
    return visible
}
