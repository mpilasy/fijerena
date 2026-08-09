package org.njarasoa.fijerena.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Current epoch seconds, refreshed every 60s — avoids per-item `System.currentTimeMillis()`
 * calls when many items in a list each need to know "now" (e.g. EPG on-air checks).
 */
@Composable
fun rememberNowEpochSeconds(): Long {
    var nowEpochSeconds by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowEpochSeconds = System.currentTimeMillis() / 1000
        }
    }
    return nowEpochSeconds
}
