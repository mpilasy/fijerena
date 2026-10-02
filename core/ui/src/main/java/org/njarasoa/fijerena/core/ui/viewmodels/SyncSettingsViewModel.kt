package org.njarasoa.fijerena.core.ui.viewmodels

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.sync.NowPlayingStore
import org.njarasoa.fijerena.core.network.sync.PairingQr
import org.njarasoa.fijerena.core.network.sync.SyncAccountManager
import org.njarasoa.fijerena.core.network.sync.SyncApiException
import org.njarasoa.fijerena.core.network.sync.SyncKey
import org.njarasoa.fijerena.core.network.sync.SyncKind
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.network.sync.SyncWire
import org.njarasoa.fijerena.core.network.sync.VolatileRecords
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.sync.NowPlayingPublisher
import org.njarasoa.fijerena.core.ui.sync.SyncManager

/**
 * Settings → Sync on both platforms: linking this device to a sync account and managing it. See
 * `docs/plans/20260929_live-sync-plan.md` → Phase 9.
 */
class SyncSettingsViewModel(
    private val app: Application,
) : ViewModel() {
    private val accounts = SyncAccountManager(app)
    private val manager = SyncManager.getInstance(app)
    private val publisher = NowPlayingPublisher.getInstance(app)
    private val deviceName = Build.MODEL ?: "Device"

    /** What the screen is doing beyond showing the status. */
    data class Ui(
        val busy: Boolean = false,
        /** Friendly error for the last action (raw detail added in dev mode). */
        val error: String? = null,
        /** The server URL typed so far, and what checking it found. */
        val serverUrl: String = "",
        val serverChecked: Boolean = false,
        val setupSecretRequired: Boolean = false,
        /** An invite QR code being shown (another device scans it to join). */
        val inviteQr: String? = null,
        /** A handoff QR code being shown (a device of the account scans it; this one then joins). */
        val handoffQr: String? = null,
        val devices: List<SyncWire.Device>? = null,
        /** A scan just finished: what it did. */
        val scanDone: ScanResult? = null,
    )

    enum class ScanResult { JOINED, HANDED_OVER }

    private val ui = MutableStateFlow(Ui())
    private var handoffJob: Job? = null
    private var inviteJob: Job? = null

    /** Dev mode adds the raw sync error under the friendly status. */
    val devMode: Boolean get() = AppSettings(app).isDevMode

    val state: StateFlow<Pair<SyncManager.Status, Ui>> =
        combine(manager.status, ui) { status, ui -> status to ui }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), manager.status.value to ui.value)

    val currentUi: StateFlow<Ui> = ui.asStateFlow()

    /** The device-local "Share what's playing with my sync group" switch. */
    val shareNowPlaying: StateFlow<Boolean> = publisher.isSharing

    fun setShareNowPlaying(enabled: Boolean) = publisher.setSharing(enabled)

    /**
     * What each listed device is playing, by device id — only devices playing or paused right
     * now. Re-checked every [STALENESS_CHECK_MS] too, so a device that went quiet drops off while
     * the screen is open.
     */
    val nowPlaying: StateFlow<Map<String, SyncPayloads.NowPlaying>> =
        combine(
            ui,
            NowPlayingStore.devices,
            ticker(),
        ) { current, entries, now -> currentNowPlaying(current.devices.orEmpty(), entries, now) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        refreshDevicesOnNewRecords()
    }

    /**
     * The list's "Last seen" ages while the screen is open, and [NowPlayingStore.Entry.isCurrent]
     * leans on it: so when a newer record arrives, reload the list, at most once per
     * [DEVICES_REFRESH_MS] (conflate keeps the latest while waiting). Best effort.
     */
    private fun refreshDevicesOnNewRecords() {
        viewModelScope.launch {
            NowPlayingStore.devices
                .map { entries -> entries.mapValues { it.value.hlc } }
                .distinctUntilChanged()
                .conflate()
                .collect {
                    if (ui.value.devices != null && manager.status.value.linked) {
                        try {
                            loadDevicesNow()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Best effort: the list just stays as it was.
                        }
                        delay(DEVICES_REFRESH_MS)
                    }
                }
        }
    }

    private fun ticker(periodMs: Long = STALENESS_CHECK_MS) =
        flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(periodMs)
            }
        }

    /** A remote Stop this device sent (phone app only): for which playback, and when. */
    data class StopRequest(
        val sessionId: String,
        val sentAt: Long,
    )

    enum class StopState { STOPPING, UNREACHABLE }

    private val stopRequests = MutableStateFlow<Map<String, StopRequest>>(emptyMap())

    /**
     * By device id, the remote Stops still waiting on their device — see [pendingStops]. Re-checked
     * every [STOP_CHECK_MS], so "Couldn't reach" shows close to [STOP_TIMEOUT_MS].
     */
    val stopStates: StateFlow<Map<String, StopState>> =
        combine(stopRequests, nowPlaying, ticker(STOP_CHECK_MS)) { requests, playing, now ->
            // A request whose line is gone or shows another session is done: forget it.
            val open = openStops(requests, playing)
            if (open.size != requests.size) stopRequests.update { current -> current.filter { (id, request) -> open[id] == request } }
            pendingStops(open, playing, now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Asks [deviceId] to stop the playback it is sharing right now: queues a
     * [SyncKind.REMOTE_COMMAND] naming that playback's session and pushes it at once. The device
     * obeys only while that same playback is on. See
     * docs/plans/20261001_live-sync-now-playing-plan.md → Remote Stop.
     */
    fun requestStop(deviceId: String) {
        val sessionId = nowPlaying.value[deviceId]?.sessionId
        if (sessionId != null) {
            val me = ui.value.devices?.firstOrNull { it.current }
            val command =
                SyncPayloads.RemoteCommand(
                    command = SyncPayloads.RemoteCommand.STOP,
                    sessionId = sessionId,
                    fromDeviceName = me?.name ?: deviceName,
                    issuedBy = me?.id.orEmpty(),
                )
            VolatileRecords.outbox.put(SyncKey(SyncKind.SHARED, "", SyncKind.REMOTE_COMMAND, deviceId), SyncPayloads.encode(command))
            stopRequests.update { it + (deviceId to StopRequest(sessionId, System.currentTimeMillis())) }
            manager.requestSync(0)
        }
    }

    fun onServerUrlChanged(url: String) {
        ui.value = ui.value.copy(serverUrl = url.trim(), serverChecked = false, error = null)
    }

    /** Validates the URL against `GET /info` before anything is saved. */
    fun checkServer() =
        action {
            val info = accounts.serverInfo(normalizedUrl())
            ui.value = ui.value.copy(serverChecked = true, setupSecretRequired = info.setupSecretRequired)
        }

    fun createAccount(setupSecret: String?) =
        action {
            try {
                accounts.createAccount(normalizedUrl(), deviceName, setupSecret?.takeIf { it.isNotBlank() })
            } catch (e: SyncApiException) {
                // Only creating an account sends the secret, so only here does a 401 mean it's wrong.
                throw if (e.status == 401) SetupSecretRejected(e) else e
            }
            manager.onLinkChanged()
        }

    private class SetupSecretRejected(
        cause: SyncApiException,
    ) : Exception(cause.message, cause)

    /** For a device that can't scan (a TV): shows a handoff QR code and joins when it is scanned. */
    fun startHandoff() {
        handoffJob?.cancel()
        handoffJob =
            viewModelScope.launch {
                try {
                    ui.value = ui.value.copy(busy = true, error = null)
                    val handoff = accounts.startHandoff(normalizedUrl())
                    ui.value = ui.value.copy(busy = false, handoffQr = PairingQr.encode(handoff.qr))
                    accounts.awaitHandoff(handoff, deviceName)
                    ui.value = ui.value.copy(handoffQr = null)
                    manager.onLinkChanged()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ui.value = ui.value.copy(busy = false, handoffQr = null, error = friendly(e))
                }
            }
    }

    fun cancelHandoff() {
        handoffJob?.cancel()
        ui.value = ui.value.copy(handoffQr = null, busy = false)
    }

    /** What the camera read: joins an invite's account, or hands this account to a TV. */
    fun onScanned(text: String) =
        action {
            val qr = PairingQr.decode(text)
            if (qr == null) {
                ui.value = ui.value.copy(error = app.getString(R.string.sync_error_not_a_code))
                return@action
            }
            accounts.scanned(qr, deviceName)
            if (qr is PairingQr.Invite) manager.onLinkChanged()
            ui.value =
                ui.value.copy(
                    scanDone = if (qr is PairingQr.Invite) ScanResult.JOINED else ScanResult.HANDED_OVER,
                )
            if (qr is PairingQr.HandoffRequest) loadDevicesNow()
        }

    fun consumeScanDone() {
        ui.value = ui.value.copy(scanDone = null)
    }

    /**
     * An invite for another device to scan — shown only while the screen asks for it, and closed
     * once a new device appears in the account (or the code expires).
     */
    fun showInvite() {
        inviteJob?.cancel()
        inviteJob =
            viewModelScope.launch {
                try {
                    ui.value = ui.value.copy(busy = true, error = null)
                    val before = accounts.devices().count { !it.revoked }
                    val invite = accounts.createInvite()
                    ui.value = ui.value.copy(busy = false, inviteQr = PairingQr.encode(invite))
                    val until = System.currentTimeMillis() + INVITE_TTL_MS
                    while (System.currentTimeMillis() < until) {
                        delay(INVITE_POLL_MS)
                        val devices = runCatching { accounts.devices() }.getOrNull() ?: continue
                        if (devices.count { !it.revoked } > before) {
                            ui.value = ui.value.copy(devices = devices)
                            break
                        }
                    }
                    ui.value = ui.value.copy(inviteQr = null)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ui.value = ui.value.copy(busy = false, inviteQr = null, error = friendly(e))
                }
            }
    }

    fun hideInvite() {
        inviteJob?.cancel()
        ui.value = ui.value.copy(inviteQr = null, busy = false)
        loadDevices()
    }

    fun loadDevices() = action { loadDevicesNow() }

    fun revoke(deviceId: String) =
        action {
            accounts.revoke(deviceId)
            loadDevicesNow()
        }

    fun syncNow() = manager.requestSync(0)

    /** Leaves the account; local data stays. */
    fun leave() =
        action {
            inviteJob?.cancel()
            // Best effort, while still linked: other devices drop this one's line now, not when
            // it goes stale.
            publisher.stopBeforeLeaving()
            manager.flush()
            accounts.leave()
            NowPlayingStore.clear()
            manager.onLinkChanged()
            ui.value = Ui()
        }

    fun dismissError() {
        ui.value = ui.value.copy(error = null)
    }

    private suspend fun loadDevicesNow() {
        ui.value = ui.value.copy(devices = accounts.devices())
    }

    private fun normalizedUrl(): String {
        val url =
            ui.value.serverUrl
                .trim()
                .trimEnd('/')
        return if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            ui.value = ui.value.copy(busy = true, error = null)
            try {
                block()
                ui.value = ui.value.copy(busy = false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ui.value = ui.value.copy(busy = false, error = friendly(e))
            }
        }
    }

    internal companion object {
        /** The server's pairing-code lifetime. */
        const val INVITE_TTL_MS = 10 * 60 * 1000L
        const val INVITE_POLL_MS = 3_000L
        const val STALENESS_CHECK_MS = 30_000L
        const val DEVICES_REFRESH_MS = 30_000L
        const val STOP_CHECK_MS = 5_000L

        /** How long a remote Stop may go unanswered before the row says the device couldn't be reached. */
        const val STOP_TIMEOUT_MS = 90_000L

        /** The [requests] whose device still shows the playback they named. */
        fun openStops(
            requests: Map<String, StopRequest>,
            playing: Map<String, SyncPayloads.NowPlaying>,
        ): Map<String, StopRequest> = requests.filter { (deviceId, request) -> playing[deviceId]?.sessionId == request.sessionId }

        /**
         * Each request whose device still shows the playback it named ([playing] holds only
         * current lines): [StopState.STOPPING], then [StopState.UNREACHABLE] after
         * [STOP_TIMEOUT_MS]. Done — dropped — once that device's line goes (stopped or stale) or
         * names another session.
         */
        fun pendingStops(
            requests: Map<String, StopRequest>,
            playing: Map<String, SyncPayloads.NowPlaying>,
            now: Long,
        ): Map<String, StopState> =
            openStops(requests, playing)
                .mapValues { (_, request) -> if (now - request.sentAt > STOP_TIMEOUT_MS) StopState.UNREACHABLE else StopState.STOPPING }

        /**
         * [entries] joined to the listed, not revoked [devices] by id, keeping only what is
         * current at [now] (see [NowPlayingStore.Entry.isCurrent], given the device's server `lastSeen`): a record of a device no
         * longer listed is never shown.
         */
        fun currentNowPlaying(
            devices: List<SyncWire.Device>,
            entries: Map<String, NowPlayingStore.Entry>,
            now: Long,
        ): Map<String, SyncPayloads.NowPlaying> =
            devices
                .filterNot { it.revoked }
                .mapNotNull { device ->
                    entries[device.id]?.takeIf { it.isCurrent(now, device.lastSeen) }?.let { device.id to it.nowPlaying }
                }.toMap()
    }

    private fun friendly(e: Exception): String {
        val message =
            when {
                e is SyncApiException && e.status == SyncApiException.INCOMPATIBLE -> app.getString(R.string.sync_error_not_a_server)
                e is kotlinx.serialization.SerializationException -> app.getString(R.string.sync_error_not_a_server)
                e is SyncApiException && e.status == 404 -> app.getString(R.string.sync_error_not_a_server)
                e is SyncApiException && e.status == 0 && e.cause != null -> app.getString(R.string.sync_error_unreachable)
                e is SetupSecretRejected -> app.getString(R.string.sync_error_setup_secret)
                e is SyncApiException && e.status == 401 && manager.status.value.linked -> app.getString(R.string.sync_error_revoked)
                e is SyncApiException && (e.status == 403 || e.status == 410) -> app.getString(R.string.sync_error_code_expired)
                else -> app.getString(org.njarasoa.fijerena.core.network.R.string.error_generic)
            }
        val raw = e.message
        return if (AppSettings(app).isDevMode && !raw.isNullOrBlank() && raw != message) "$message\n\n[dev] $raw" else message
    }
}
