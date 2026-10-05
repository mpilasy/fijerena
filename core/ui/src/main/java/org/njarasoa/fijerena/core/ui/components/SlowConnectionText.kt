package org.njarasoa.fijerena.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.player.model.SlowConnection
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.ui.R
import kotlin.math.roundToInt

/**
 * The slow-connection banner's text while the playing stream gets less bandwidth than it needs,
 * else null. Polled, like the player's other service readings, so a recreated service is picked
 * up. See docs/plans/20261004_playback-capability-errors-plan.md → P4.
 */
@Composable
fun rememberSlowConnectionText(): String? {
    var slow by remember { mutableStateOf<SlowConnection?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            slow = StreamingPlaybackService.getInstance()?.slowConnection?.value
            delay(1_000L)
        }
    }
    val current = slow ?: return null
    val needed = current.neededBps
    val measured = current.measuredBps
    return if (needed != null && measured != null) {
        stringResource(R.string.slow_connection_banner_format, megabits(needed), megabits(measured))
    } else {
        stringResource(R.string.slow_connection_banner)
    }
}

private fun megabits(bps: Long): Int = (bps / 1_000_000.0).roundToInt().coerceAtLeast(1)
