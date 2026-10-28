package org.njarasoa.fijerena.core.network.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * PINs typed on another device — see the PIN on the phone plan. Both ends, in memory only:
 * - The asking device (a TV) [ask]s, then receives each [answers] for a request it still waits for, once.
 * - The answering device (a phone) keeps the [incoming] requests of other devices that are open and
 *   recent, and drops one when it is [done] or the asking device closes it.
 *
 * A request id is random and used once: a record read again (a resync from 0) never shows a prompt
 * again or unlocks anything again.
 */
object PinRequests {
    /** A request older than this by the receiving device's clock is ignored: a stale one from an earlier session. */
    const val MAX_AGE_MS = 5 * 60_000L

    /** A request from [deviceId], open on this device since [receivedAt] (this device's clock). */
    data class Incoming(
        val deviceId: String,
        val request: SyncPayloads.PinRequest,
        val hlc: Long,
        val receivedAt: Long,
    )

    fun requestKey(askingDeviceId: String) = SyncKey(SyncKind.SHARED, "", SyncKind.PIN_REQUEST, askingDeviceId)

    fun answerKey(askingDeviceId: String) = SyncKey(SyncKind.SHARED, "", SyncKind.PIN_ANSWER, askingDeviceId)

    // --- The answering device ---

    private val _incoming = MutableStateFlow<Map<String, Incoming>>(emptyMap())

    /** Open requests from other devices, by asking device id: at most one each (a device waits for one PIN at a time). */
    val incoming: StateFlow<Map<String, Incoming>> = _incoming.asStateFlow()

    // Request ids answered here, or closed by the asking device: never shown again.
    private val finished = ConcurrentHashMap.newKeySet<String>()

    /**
     * Keeps [request] from [deviceId] if it is open, recent by [now] and not finished; a closed one
     * removes it. A record older than what is held for [deviceId] (by [hlc]) changes nothing. True
     * when it changed what is held.
     */
    fun receiveRequest(
        deviceId: String,
        request: SyncPayloads.PinRequest,
        hlc: Long,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val held = _incoming.value[deviceId]
        if (held != null && held.hlc > hlc) return false
        if (!request.open) {
            finished.add(request.requestId)
            return remove(deviceId)
        }
        val recent = kotlin.math.abs(now - request.sentAt) <= MAX_AGE_MS
        if (!recent || request.requestId.isEmpty() || request.requestId in finished || request.profileId.isEmpty()) {
            return remove(deviceId)
        }
        if (held?.request?.requestId == request.requestId) return false
        _incoming.update { it + (deviceId to Incoming(deviceId, request, hlc, now)) }
        return true
    }

    /** This device answered [requestId] (or cancelled it): it is not shown again. */
    fun done(requestId: String) {
        finished.add(requestId)
        _incoming.update { current -> current.filterValues { it.request.requestId != requestId } }
    }

    private fun remove(deviceId: String): Boolean {
        if (deviceId !in _incoming.value) return false
        _incoming.update { it - deviceId }
        return true
    }

    // --- The asking device ---

    private val asked = ConcurrentHashMap.newKeySet<String>()
    private val answered = ConcurrentHashMap.newKeySet<String>()
    private val _answers = MutableSharedFlow<SyncPayloads.PinAnswer>(extraBufferCapacity = 8)

    /** Each answer to a request this device waits for, once. Not replayed: a dialog that is gone waits for nothing. */
    val answers: SharedFlow<SyncPayloads.PinAnswer> = _answers.asSharedFlow()

    /** This device waits for an answer to [requestId]. */
    fun ask(requestId: String) {
        asked.add(requestId)
    }

    /** This device stopped waiting for [requestId]: a late answer does nothing. */
    fun stopWaiting(requestId: String) {
        asked.remove(requestId)
    }

    /** Emits [answer] if this device waits for its request and no answer to it came before. True when it did. */
    fun receiveAnswer(answer: SyncPayloads.PinAnswer): Boolean =
        answer.requestId in asked &&
            answer.result in setOf(SyncPayloads.PinAnswer.UNLOCKED, SyncPayloads.PinAnswer.CANCELLED) &&
            answered.add(answer.requestId) &&
            _answers.tryEmit(answer)

    /** This device left the group: no request of the others applies any more. */
    fun clear() {
        _incoming.value = emptyMap()
    }

    /** Tests only: forget everything. */
    internal fun reset() {
        _incoming.value = emptyMap()
        finished.clear()
        asked.clear()
        answered.clear()
    }
}
