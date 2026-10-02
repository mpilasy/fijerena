package org.njarasoa.fijerena.core.network.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the other devices of the sync group are playing, by server device id, as last received
 * ([SyncKind.NOW_PLAYING]). In memory only: after a restart the devices' next heartbeat — or the
 * catch-up pull — refills it. See `docs/plans/20261001_live-sync-now-playing-plan.md`.
 */
object NowPlayingStore {
    /** How long a playing or paused device stays shown without a newer record: three missed heartbeats. */
    const val STALE_AFTER_MS = 3 * 60_000L

    /** [receivedAt] is this device's clock when it arrived; [hlc] orders the device's records. */
    data class Entry(
        val nowPlaying: SyncPayloads.NowPlaying,
        val hlc: Long,
        val receivedAt: Long,
    ) {
        /**
         * Playing or paused, and recent by both clocks: the sender's [SyncPayloads.NowPlaying.sentAt]
         * catches an old record arriving late (a catch-up pull), this device's [receivedAt] a
         * sender whose clock runs ahead and has gone quiet.
         */
        fun isCurrent(now: Long): Boolean =
            nowPlaying.state != SyncPayloads.NowPlaying.STOPPED &&
                now - nowPlaying.sentAt <= STALE_AFTER_MS &&
                now - receivedAt <= STALE_AFTER_MS
    }

    private val _devices = MutableStateFlow<Map<String, Entry>>(emptyMap())
    val devices: StateFlow<Map<String, Entry>> = _devices.asStateFlow()

    /** Keeps [entry] for [deviceId] unless what is there is newer (records can arrive out of order). */
    fun receive(
        deviceId: String,
        entry: Entry,
    ) {
        _devices.update { current ->
            if ((current[deviceId]?.hlc ?: Long.MIN_VALUE) > entry.hlc) current else current + (deviceId to entry)
        }
    }

    /** This device left the group: nothing it knew about the others applies any more. */
    fun clear() {
        _devices.value = emptyMap()
    }
}
