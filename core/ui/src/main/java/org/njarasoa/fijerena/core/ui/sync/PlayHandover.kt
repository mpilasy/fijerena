package org.njarasoa.fijerena.core.ui.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * The player's hand-over for a Send to TV's Play (the send to TV plan): the screen that plays now
 * registers [PlayHandoverEffect]; [handOver] asks it to save the watch position and stop the player
 * as Back does, so that a new player screen takes the one player over. Nothing plays: nothing happens.
 */
object PlayHandover {
    private var taker: (suspend () -> Unit)? = null

    /** Saves what the playing screen has and stops the player; returns once that is done. */
    suspend fun handOver() {
        taker?.invoke()
    }

    internal fun register(handOver: suspend () -> Unit) {
        taker = handOver
    }

    internal fun unregister(handOver: suspend () -> Unit) {
        if (taker === handOver) taker = null
    }
}

/** For every screen that plays: [onHandOver] saves, stops the player and marks the screen as handed off. */
@Composable
fun PlayHandoverEffect(onHandOver: suspend () -> Unit) {
    val current by rememberUpdatedState(onHandOver)
    DisposableEffect(Unit) {
        val taker: suspend () -> Unit = { current() }
        PlayHandover.register(taker)
        onDispose { PlayHandover.unregister(taker) }
    }
}
