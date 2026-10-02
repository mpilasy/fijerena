package org.njarasoa.fijerena.core.ui.sync

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.sync.RemoteCommands
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.ui.R

/**
 * Remote Stop on the device being stopped — see `docs/plans/20261001_live-sync-now-playing-plan.md`
 * → Remote Stop. Every screen that plays (TV player and split preview, mobile player and Live TV
 * dock) runs this with what its Back does: finalise the session, stop, then leave for Home.
 * "Playback stopped from <device>" is shown first, so it survives the screen leaving.
 */
@Composable
fun RemoteStopEffect(onStop: suspend () -> Unit) {
    val context = LocalContext.current.applicationContext
    val currentOnStop by rememberUpdatedState(onStop)
    LaunchedEffect(Unit) {
        RemoteCommands.stops.collect { stop ->
            if (RemoteCommands.claim(stop)) {
                showStoppedFrom(context, stop)
                currentOnStop()
            }
        }
    }
}

/**
 * The service can still be playing with no player screen to obey a Stop (audio carrying on behind
 * another screen). A screen claims a Stop at once; one nobody claimed within [UNCLAIMED_MS] whose
 * session is still on is handled here: playback stops — no watch position is saved, no screen
 * being there to save it — and the message is shown.
 */
object RemoteStopFallback {
    private const val UNCLAIMED_MS = 3_000L

    @androidx.annotation.OptIn(UnstableApi::class)
    fun start(context: Context) {
        val app = context.applicationContext
        val scope = AppScopes.create("RemoteStopFallback", Dispatchers.Main)
        scope.launch {
            RemoteCommands.stops.collect { stop ->
                launch {
                    delay(UNCLAIMED_MS)
                    val stillOn = StreamingPlaybackService.nowPlaying.value?.sessionId == stop.sessionId
                    if (stillOn && RemoteCommands.claim(stop)) {
                        StreamingPlaybackService.getInstance()?.stop()
                        showStoppedFrom(app, stop)
                    }
                }
            }
        }
    }
}

private fun showStoppedFrom(
    context: Context,
    stop: RemoteCommands.Stop,
) {
    val from = stop.fromDeviceName.ifBlank { context.getString(R.string.live_sync_stopped_from_another_device) }
    Toast.makeText(context, context.getString(R.string.live_sync_stopped_from, from), Toast.LENGTH_LONG).show()
}
