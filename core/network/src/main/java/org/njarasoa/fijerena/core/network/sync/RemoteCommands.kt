package org.njarasoa.fijerena.core.network.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Remote Stop commands addressed to this device that match what it is playing — see
 * `docs/plans/20261001_live-sync-now-playing-plan.md` → Remote Stop. Here, not in `core:ui`,
 * because [SyncApplier] (this module) decides what fires; the player screens and the app-wide
 * fallback (`core:ui`) collect [stops].
 */
object RemoteCommands {
    /** Stop the playback [sessionId]; [fromDeviceName] is shown to whoever was watching. */
    data class Stop(
        val sessionId: String,
        val fromDeviceName: String,
    )

    private val _stops = MutableSharedFlow<Stop>(extraBufferCapacity = 8)

    /** Each matching Stop, once. Not replayed: a screen that wasn't there has nothing to stop. */
    val stops: SharedFlow<Stop> = _stops.asSharedFlow()

    // Sessions a Stop already fired for, and sessions something already acted on: a playback id
    // is random and never reused, so these only grow by one per remote stop in this process.
    private val fired = ConcurrentHashMap.newKeySet<String>()
    private val claimed = ConcurrentHashMap.newKeySet<String>()

    /**
     * Fires [command] if it is a Stop for [playingSessionId] — the playback on right now, null when
     * nothing plays — and no Stop fired for that session before. True when it fired. A stale or
     * replayed command (another session, or one already obeyed) and an unknown command never do.
     */
    fun receive(
        command: SyncPayloads.RemoteCommand,
        playingSessionId: String?,
    ): Boolean {
        val matches =
            command.command == SyncPayloads.RemoteCommand.STOP &&
                command.sessionId.isNotEmpty() &&
                command.sessionId == playingSessionId
        return matches && fired.add(command.sessionId) && _stops.tryEmit(Stop(command.sessionId, command.fromDeviceName))
    }

    /**
     * Whoever acts on [stop] claims it first; only the first claim wins. Several collectors can
     * see the same Stop (a player screen and the app-wide fallback), but one must handle it.
     */
    fun claim(stop: Stop): Boolean = claimed.add(stop.sessionId)

    /** Tests only: forget what fired and what was claimed. */
    internal fun reset() {
        fired.clear()
        claimed.clear()
    }
}
