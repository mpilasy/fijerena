package org.njarasoa.fijerena.core.network.sync

import java.util.concurrent.ConcurrentHashMap

/**
 * Records of [SyncKind.VOLATILE] kinds waiting to be pushed. Unlike every other kind they have no
 * version row: only the latest state of each key matters, so it is held here, in memory, until a
 * push gets it to the server. Each push stamps it with the next tick of the settings sync clock, so
 * every send of a key is newer than the last and the server's last-write-wins takes it. See
 * `docs/plans/20261001_live-sync-now-playing-plan.md`.
 */
class VolatileRecords internal constructor() {
    /** One state put for a key; compared by identity, so a newer put is never marked sent by an older push. */
    private class Waiting(
        val payload: String,
    )

    private val waiting = ConcurrentHashMap<SyncKey, Waiting>()
    private val lastHlc = ConcurrentHashMap<SyncKey, Long>()

    /** Queues [payload] as [key]'s state, replacing whatever was still waiting for it. */
    fun put(
        key: SyncKey,
        payload: String,
    ) {
        waiting[key] = Waiting(payload)
    }

    /**
     * Everything waiting, each stamped with [nextClock] — and never at or below a value this key
     * was already sent with, whatever the clock says. A record stays waiting until marked sent, so
     * a failed push sends it again next time, with a newer stamp.
     */
    internal suspend fun take(nextClock: suspend () -> Long): List<LocalRecords.Outgoing> =
        waiting.entries.map { (key, item) ->
            val hlc = maxOf(nextClock(), (lastHlc[key] ?: 0L) + 1)
            lastHlc[key] = hlc
            LocalRecords.Outgoing(SyncRecord(key, hlc, payload = item.payload)) { waiting.remove(key, item) }
        }

    companion object {
        /** The process's records waiting for [SyncEngine]'s next push. */
        val outbox = VolatileRecords()
    }
}
