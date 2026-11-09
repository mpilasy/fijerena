package org.njarasoa.fijerena.core.ui.viewmodels

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.sync.DeviceBuild
import org.njarasoa.fijerena.core.network.sync.NowPlayingStore
import org.njarasoa.fijerena.core.network.sync.PlayRequests
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.network.sync.SyncPayloads.PlayAnswer
import org.njarasoa.fijerena.core.network.sync.SyncWire
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.launchGuarded
import java.util.UUID

/** What the phone sends: a film, an episode or a channel; its source is the phone's [providerId]. */
data class SendItem(
    val providerId: Long,
    val streamId: String,
    val streamName: String,
    val categoryId: String,
    val contentType: String,
    val episodeId: String? = null,
    val episodeExtension: String? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    /** The phone's position; null reads the phone's watch row before it is sent. */
    val positionMs: Long? = null,
    val title: String = streamName,
    val subtitle: String? = null,
)

/** Whether a TV can take the item now (the send to TV plan, ST-D2). */
enum class TvStatus { OPEN, NOT_OPEN, NEEDS_UPDATE }

/** One row of the picker: [playing] when the TV shares what it plays. */
data class TvRow(
    val deviceId: String,
    val name: String,
    val status: TvStatus,
    val playing: String? = null,
)

/** The picker shows, for [item], with its rows while they are fresh. */
data class SendToTvSheet(
    val rows: List<TvRow>,
    internal val item: SendItem,
    internal val beforeSend: (suspend () -> Long?)?,
)

/** The picker's rows from the group's devices: the TVs only, not revoked, not this device. */
fun tvRows(
    devices: List<SyncWire.Device>,
    builds: Map<String, DeviceBuild>,
    names: Map<String, String>,
    now: Long,
    playing: (SyncWire.Device) -> String? = { null },
): List<TvRow> =
    devices
        .filter { !it.current && !it.revoked }
        .filter { builds[it.id]?.app != "phone" }
        .map { device ->
            val build = builds[device.id]
            val status =
                when {
                    build == null || !build.takesPlayRequests -> TvStatus.NEEDS_UPDATE
                    now - device.lastSeen < SendToTvViewModel.OPEN_WITHIN_MS -> TvStatus.OPEN
                    else -> TvStatus.NOT_OPEN
                }
            TvRow(device.id, names[device.id] ?: device.name, status, playing(device))
        }

/** The app's edge to the sync, the source and the notices; a seam for the tests. */
interface SendToTvGateway {
    /** Linked to a sync group that has a TV with the feature. */
    val available: Flow<Boolean>

    /** Each answer to a request this device waits for. */
    val answers: SharedFlow<PlayAnswer>

    suspend fun rows(): List<TvRow>

    /** The request for [item], with the position and the file type read if the item has none; null: no key for its source. */
    suspend fun buildRequest(
        item: SendItem,
        position: Long?,
    ): SyncPayloads.PlayRequest?

    fun send(
        targetDeviceId: String,
        request: SyncPayloads.PlayRequest,
    )

    fun close(
        targetDeviceId: String,
        request: SyncPayloads.PlayRequest,
    )

    fun stopWaiting(requestId: String)

    fun notify(
        @StringRes text: Int,
        vararg args: Any,
    )
}

/**
 * The phone's Send to TV (the send to TV plan, ST-D2, ST-D4, ST-D12, ST-D13): opens the picker, or
 * with exactly one TV open sends at once; shows the answers. The caller's [send] runs [beforeSend]
 * first (the player: stop here, then give the position), after the TV is chosen.
 */
class SendToTvViewModel(
    private val gateway: SendToTvGateway,
    private val receivedWaitMs: Long = RECEIVED_WAIT_MS,
    private val finalWaitMs: Long = FINAL_WAIT_MS,
    private val refreshMs: Long = REFRESH_MS,
) : ViewModel() {
    val available: StateFlow<Boolean> = gateway.available.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _sheet = MutableStateFlow<SendToTvSheet?>(null)

    /** The picker, or null when it is closed. */
    val sheet: StateFlow<SendToTvSheet?> = _sheet.asStateFlow()

    private var refresh: Job? = null

    /** Sends [item] after [beforeSend] (the phone stops and gives its position); the TV is picked first. */
    fun send(
        item: SendItem,
        beforeSend: (suspend () -> Long?)? = null,
    ) {
        viewModelScope.launchGuarded("SendToTv.start") {
            val rows = safeRows()
            val open = rows.filter { it.status == TvStatus.OPEN }
            if (open.size == 1) {
                dispatch(open.single(), item, beforeSend)
            } else {
                _sheet.value = SendToTvSheet(rows, item, beforeSend)
                startRefresh()
            }
        }
    }

    /** The picker's row [row] was tapped: only an open TV takes the item. */
    fun pick(row: TvRow) {
        if (row.status != TvStatus.OPEN) return
        val shown = _sheet.value ?: return
        closeSheet()
        viewModelScope.launchGuarded("SendToTv.pick") { dispatch(row, shown.item, shown.beforeSend) }
    }

    fun closeSheet() {
        refresh?.cancel()
        refresh = null
        _sheet.value = null
    }

    /** One fetch of the devices when the picker opens, then this while it shows (ST-D2). */
    private fun startRefresh() {
        refresh?.cancel()
        refresh =
            viewModelScope.launchGuarded("SendToTv.refresh") {
                while (true) {
                    delay(refreshMs)
                    val rows = safeRows()
                    _sheet.update { it?.copy(rows = rows) }
                }
            }
    }

    private suspend fun safeRows(): List<TvRow> =
        try {
            gateway.rows()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

    private suspend fun dispatch(
        row: TvRow,
        item: SendItem,
        beforeSend: (suspend () -> Long?)?,
    ) {
        val position = beforeSend?.invoke()
        val request = safeRequest(item, position ?: item.positionMs)
        if (request == null) {
            gateway.notify(R.string.send_to_tv_no_source, row.name)
            return
        }
        gateway.notify(R.string.send_to_tv_sending, row.name)
        val answer = awaitAnswer(row, request)
        if (answer == null) {
            gateway.close(row.deviceId, request)
            gateway.notify(R.string.send_to_tv_unreachable, row.name)
            return
        }
        gateway.stopWaiting(request.requestId)
        notifyResult(answer.result, row.name, request.profileName)
    }

    /** Null when the source cannot be read: by then the phone can have stopped, so the user gets a message, not silence. */
    private suspend fun safeRequest(
        item: SendItem,
        position: Long?,
    ): SyncPayloads.PlayRequest? =
        try {
            gateway.buildRequest(item, position)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private suspend fun awaitAnswer(
        row: TvRow,
        request: SyncPayloads.PlayRequest,
    ): PlayAnswer? =
        coroutineScope {
            val received = Channel<PlayAnswer>(Channel.UNLIMITED)
            val listening =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    gateway.answers
                        .onSubscription { gateway.send(row.deviceId, request) }
                        .collect { answer -> if (answer.requestId == request.requestId) received.trySend(answer) }
                }
            val first = withTimeoutOrNull(receivedWaitMs) { received.receive() }
            val final =
                when (first?.result) {
                    null -> null
                    PlayAnswer.RECEIVED -> withTimeoutOrNull(finalWaitMs) { received.receive() }
                    else -> first
                }
            listening.cancel()
            final
        }

    private fun notifyResult(
        result: String,
        tv: String,
        profile: String,
    ) {
        when (result) {
            PlayAnswer.PLAYING -> gateway.notify(R.string.send_to_tv_playing_on, tv)
            PlayAnswer.DISMISSED -> gateway.notify(R.string.send_to_tv_dismissed, tv)
            PlayAnswer.BUSY -> gateway.notify(R.string.send_to_tv_busy, tv)
            PlayAnswer.NO_SOURCE -> gateway.notify(R.string.send_to_tv_no_source, tv)
            PlayAnswer.NO_PROFILE -> gateway.notify(R.string.send_to_tv_no_profile, profile, tv)
            PlayAnswer.NOT_ALLOWED -> gateway.notify(R.string.send_to_tv_not_allowed, tv, profile)
            else -> Unit
        }
    }

    companion object {
        /** A TV seen within this time counts as open (ST-D2). */
        const val OPEN_WITHIN_MS = 90_000L

        /** No `received` within this time: "Couldn't reach" (ST-D4). */
        const val RECEIVED_WAIT_MS = 20_000L

        /** After `received`, the banner's own time on the TV and some slack. */
        const val FINAL_WAIT_MS = PlayRequests.MAX_AGE_MS + 30_000L

        const val REFRESH_MS = 15_000L

        fun newRequestId(): String = UUID.randomUUID().toString()

        /** What the row shows the TV plays now, from what it shares; null otherwise. */
        fun playingTitle(
            device: SyncWire.Device,
            now: Long,
        ): String? =
            NowPlayingStore.devices.value[device.id]
                ?.takeIf { it.isCurrent(now, device.lastSeen) }
                ?.nowPlaying
                ?.title
                ?.takeIf { it.isNotBlank() }
    }
}
