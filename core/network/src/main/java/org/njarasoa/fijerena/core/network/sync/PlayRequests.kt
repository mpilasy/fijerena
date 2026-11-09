package org.njarasoa.fijerena.core.network.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Films, episodes and channels sent to a TV — see the send to TV plan. Both ends, in memory only:
 * - The sending device (a phone) [ask]s, then receives each [answers] for a request it still waits for.
 * - The target TV keeps the one open request in [incoming], and drops it when it is [done] or the
 *   phone closes it.
 *
 * A request id is random and used once: a record read again never shows a banner again, as the TV
 * keeps every id it saw. A request older than [MAX_AGE_MS] is ignored, by the TV's clock when it
 * arrives and by its receive time afterwards ([isExpired]), so a TV that opens later does nothing.
 */
object PlayRequests {
    /** A request older than this is dropped: the TV never starts something sent minutes ago. */
    const val MAX_AGE_MS = 2 * 60_000L

    /** A request open on this device since [receivedAt] (this device's clock). */
    data class Incoming(
        val request: SyncPayloads.PlayRequest,
        val hlc: Long,
        val receivedAt: Long,
    )

    fun requestKey(targetDeviceId: String) = SyncKey(SyncKind.SHARED, "", SyncKind.PLAY_REQUEST, targetDeviceId)

    fun answerKey(targetDeviceId: String) = SyncKey(SyncKind.SHARED, "", SyncKind.PLAY_ANSWER, targetDeviceId)

    // --- The target TV ---

    private val _incoming = MutableStateFlow<Incoming?>(null)

    /** The open request for this TV: at most one, the newest; null when none. */
    val incoming: StateFlow<Incoming?> = _incoming.asStateFlow()

    // Every request id this TV received: none is shown a second time.
    private val seen = ConcurrentHashMap.newKeySet<String>()

    /**
     * Holds [request] if it is open, recent by [now] and new; a closed one removes it. An older
     * record than the one held (by [hlc]) changes nothing. True when it changed what is held.
     */
    fun receiveRequest(
        request: SyncPayloads.PlayRequest,
        hlc: Long,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val held = _incoming.value
        val fresh = request.requestId !in seen
        val recent = abs(now - request.sentAt) <= MAX_AGE_MS
        val usable = request.requestId.isNotEmpty() && request.profileId.isNotEmpty() && request.providerKey.isNotEmpty()
        return when {
            held != null && held.hlc > hlc -> {
                false
            }

            !request.open -> {
                seen.add(request.requestId)
                val removed = held?.request?.requestId == request.requestId
                if (removed) _incoming.value = null
                removed
            }

            !fresh || !recent || !usable -> {
                false
            }

            else -> {
                seen.add(request.requestId)
                _incoming.value = Incoming(request, hlc, now)
                true
            }
        }
    }

    /** The TV is finished with [requestId] (played, dismissed or refused): it is not shown again. */
    fun done(requestId: String) {
        seen.add(requestId)
        _incoming.update { current -> current?.takeIf { it.request.requestId != requestId } }
    }

    /** [incoming] has been held too long by the TV's own receive time ([now]). */
    fun isExpired(
        incoming: Incoming,
        now: Long = System.currentTimeMillis(),
    ): Boolean = now - incoming.receivedAt > MAX_AGE_MS

    // --- The sending device ---

    private val asked = ConcurrentHashMap.newKeySet<String>()
    private val answered = ConcurrentHashMap.newKeySet<String>()
    private val _answers = MutableSharedFlow<SyncPayloads.PlayAnswer>(extraBufferCapacity = 8)

    /** Each answer to a request this device waits for, each result once. Not replayed: nobody waits, nothing is told. */
    val answers: SharedFlow<SyncPayloads.PlayAnswer> = _answers.asSharedFlow()

    /** This device waits for answers to [requestId]. */
    fun ask(requestId: String) {
        asked.add(requestId)
    }

    /** This device stopped waiting for [requestId]: a late answer does nothing. */
    fun stopWaiting(requestId: String) {
        asked.remove(requestId)
    }

    /** Emits [answer] if this device waits for its request and that result did not come before. True when it did. */
    fun receiveAnswer(answer: SyncPayloads.PlayAnswer): Boolean =
        answer.requestId in asked &&
            answer.result in SyncPayloads.PlayAnswer.ALL &&
            answered.add("${answer.requestId}:${answer.result}") &&
            _answers.tryEmit(answer)

    /** This device left the group: no request of the others applies any more. */
    fun clear() {
        _incoming.value = null
    }

    /** Tests only: forget everything. */
    internal fun reset() {
        _incoming.value = null
        seen.clear()
        asked.clear()
        answered.clear()
    }
}
