package org.njarasoa.fijerena.core.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.StorageSpace
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.sync.DeviceReportBuilder
import org.njarasoa.fijerena.core.network.sync.SyncAccountStore
import org.njarasoa.fijerena.core.network.sync.SyncEngine
import org.njarasoa.fijerena.core.network.sync.SyncKey
import org.njarasoa.fijerena.core.network.sync.SyncKind
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.network.sync.SyncRecord
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.ui.image.ImageCache
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

/**
 * The safe-mode screen's "Clear caches" (see `SafeMode`): the EPG index, each source's catalogue
 * cache and the poster cache — everything the app downloads again on its own. Sources, profiles,
 * favourites and watch history are never touched. Each step runs even if an earlier one failed,
 * since the broken one may be exactly what safe mode is working around. See
 * the next level rock solid resilience plan → R-10. Also "Send logs to my sync group": one push of
 * this device's report, with no other sync (the device diagnostics sync plan, Phase 4).
 */
class SafeModeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val context = application

    enum class ClearState { IDLE, CLEARING, DONE, FAILED }

    private val _clearState = MutableStateFlow(ClearState.IDLE)
    val clearState: StateFlow<ClearState> = _clearState.asStateFlow()

    enum class SendState { IDLE, SENDING, SENT, FAILED }

    private val canSendLogsState = MutableStateFlow(false)

    /**
     * "Send logs to my sync group" shows: this device is linked and its Share Logs with My Sync
     * Group switch is on (the device diagnostics sync plan).
     */
    val canSendLogs: StateFlow<Boolean> = canSendLogsState.asStateFlow()

    private val sendStateFlow = MutableStateFlow(SendState.IDLE)
    val sendState: StateFlow<SendState> = sendStateFlow.asStateFlow()

    private val sendErrorFlow = MutableStateFlow<String?>(null)

    /** Why the last send failed, masked: raw text that only a Nerd sees. */
    val sendError: StateFlow<String?> = sendErrorFlow.asStateFlow()

    /**
     * True while the device's storage is low ([StorageSpace]): a full disk is a likely cause of the
     * crashes, so the screen says what to clear (the low storage plan, Phase 4). Checked every
     * [STORAGE_CHECK_MS] while the screen shows.
     */
    val storageLow: StateFlow<Boolean> =
        flow {
            while (true) {
                emit(StorageSpace.lowFreeBytes(context) != null)
                delay(STORAGE_CHECK_MS)
            }
        }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    init {
        // The link lives in encrypted prefs: read off the main thread.
        viewModelScope.launchGuarded("SafeModeViewModel.canSendLogs") {
            canSendLogsState.value =
                withContext(Dispatchers.IO) { SyncAccountStore(context).link != null && AppSettings(context).shareLogsWithGroup }
        }
    }

    /**
     * Sends this device's report to its sync group, and nothing else: safe mode starts no sync, and
     * a crash loop is the case that needs the report most. One push of the one record
     * ([SyncEngine.pushOnly]): no pull, no apply, no socket. The record's clock is now, so it beats
     * the last report the device sent.
     */
    fun sendLogs() {
        if (sendStateFlow.value != SendState.SENDING) {
            sendStateFlow.value = SendState.SENDING
            sendErrorFlow.value = null
            viewModelScope.launchGuarded(
                "SafeModeViewModel.sendLogs",
                onError = { e ->
                    sendErrorFlow.value = Redact.text("${e.javaClass.simpleName}: ${e.message}")
                    sendStateFlow.value = SendState.FAILED
                },
            ) {
                val accepted = withContext(Dispatchers.IO) { pushReport() }
                sendStateFlow.value = if (accepted > 0) SendState.SENT else SendState.FAILED
            }
        }
    }

    private suspend fun pushReport(): Int {
        val store = SyncAccountStore(context)
        val deviceId = checkNotNull(store.link?.deviceId) { "This device is not linked" }
        val now = System.currentTimeMillis()
        val report = DeviceReportBuilder.build(context, now, AppSettings(context).diagnosticsClearedAt)
        val record =
            SyncRecord(
                SyncKey(SyncKind.SHARED, "", SyncKind.SETTING, AppSettings.deviceReportSettingKey(deviceId)),
                hlc = now,
                payload = SyncPayloads.encode(SyncPayloads.Setting(JsonPrimitive(DeviceReportBuilder.encode(report)))),
            )
        return SyncEngine(context, store = store).pushOnly(listOf(record))
    }

    fun clearCaches() {
        if (_clearState.value != ClearState.CLEARING) {
            _clearState.value = ClearState.CLEARING
            viewModelScope.launch {
                val results =
                    withContext(Dispatchers.IO) {
                        listOf(
                            step("EPG index") {
                                EpgIndexer.getInstance(context).clearAll()
                                // Stats and validators of the rows just destroyed; kept, they let
                                // the next refresh skip on a 304 / hash match against an empty
                                // guide (G-11). Bookkeeping columns only — not a synced change.
                                SettingsDatabase.getInstance(context).epgSourceDao().resetAllIngestionState()
                            },
                            step("catalogue") {
                                val providerRepo = ProviderRepository(context)
                                // Catalogue rows only (XtreamStatsManager.clearCache): favourites
                                // and watch history live in their own tables.
                                providerRepo.getAllProvidersList().forEach { providerRepo.clearAllCacheForProvider(it.id) }
                            },
                            step("images") { ImageCache.clear(context) },
                        )
                    }
                _clearState.value = if (results.all { it }) ClearState.DONE else ClearState.FAILED
            }
        }
    }

    /** Runs one clearing step; false, recorded in [CrashLog], if it threw. */
    private suspend fun step(
        name: String,
        block: suspend () -> Unit,
    ): Boolean =
        try {
            block()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("SafeModeViewModel", "Clearing $name failed", e)
            CrashLog.record("safe mode: clear $name", e)
            false
        }

    private companion object {
        const val STORAGE_CHECK_MS = 10_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
