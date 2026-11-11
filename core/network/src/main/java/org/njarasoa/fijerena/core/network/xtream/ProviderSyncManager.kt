package org.njarasoa.fijerena.core.network.xtream

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.R
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.xmltv.EpgSyncWorker
import org.njarasoa.fijerena.core.player.device.DeviceDetector
import org.njarasoa.fijerena.core.player.device.DeviceType
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Periodic background sync of Xtream providers: schedules XtreamSyncWorker, and on a TV also
 * refreshes in-process.
 */
class ProviderSyncManager private constructor(
    private val context: Context,
) {
    private val scope = AppScopes.create("ProviderSyncManager", Dispatchers.Main)
    private var autoRefreshJob: Job? = null

    /** Manual syncs currently running, keyed by provider id. Guarded by [inFlightLock]. */
    private val inFlightLock = Any()
    private val inFlight = mutableMapOf<Long, Deferred<SyncResult>>()

    /** Outcome of a manual sync — see [startManualSync]. */
    sealed interface SyncResult {
        data object Success : SyncResult

        data class Failed(
            val message: String,
        ) : SyncResult
    }

    companion object {
        private const val TAG = "ProviderSyncManager"
        private const val WORK_NAME = "provider_content_sync"
        private const val AUTO_REFRESH_CHECK_INTERVAL_MS = 15 * 60 * 1000L

        /** Content syncs run on this cadence, anchored on `AppSettings.contentRefreshTime`. */
        private const val REFRESH_INTERVAL_HOURS = 4

        @Volatile
        private var instance: ProviderSyncManager? = null

        fun getInstance(context: Context): ProviderSyncManager =
            instance ?: synchronized(this) {
                instance ?: ProviderSyncManager(context.applicationContext).also { instance = it }
            }

        /**
         * The periodic catalog sync and the periodic guide refresh (`epg_sync`, scheduled by
         * `EpgFileManager`) as WorkManager holds them, each null when not enqueued. Read-only, for
         * Settings → Device info (the device info screen plan → P3).
         */
        suspend fun scheduledSyncWork(context: Context): Pair<ScheduledWork?, ScheduledWork?> {
            val workManager = WorkManager.getInstance(context)

            suspend fun read(name: String): ScheduledWork? =
                workManager
                    .getWorkInfosForUniqueWorkFlow(name)
                    .first()
                    .let { infos -> infos.firstOrNull { !it.state.isFinished } ?: infos.firstOrNull() }
                    ?.let { ScheduledWork(it.state.name, it.nextScheduleTimeMillis.takeIf { ms -> ms != Long.MAX_VALUE }) }
            return read(WORK_NAME) to read(EpgSyncWorker.WORK_NAME)
        }
    }

    /** A periodic job's WorkManager state (`ENQUEUED`, `RUNNING`…) and next run, null when none is planned. */
    data class ScheduledWork(
        val state: String,
        val nextRunAtMs: Long?,
    )

    fun initialize() {
        scheduleAutoRefresh()
    }

    private fun isFixedDevice(): Boolean {
        val type = DeviceDetector.detect().deviceType
        return type != DeviceType.GENERIC_MOBILE
    }

    private fun scheduleAutoRefresh() {
        autoRefreshJob?.cancel()
        val appSettings = AppSettings(context)

        // Enqueued once per call, not on every loop pass: a re-enqueue with UPDATE rewrote
        // initialDelay every 15 minutes, which moved the first run one tick earlier each pass.
        // Settings changes come in through updateSchedule().
        if (appSettings.contentAutoRefreshEnabled) {
            updateWorkManagerSchedule(appSettings)
        } else {
            cancelWorkManager()
        }

        // TV and other fixed devices also refresh in-process while the app runs.
        if (!isFixedDevice()) return
        autoRefreshJob =
            scope.launch {
                while (true) {
                    val delayMs =
                        if (appSettings.contentAutoRefreshEnabled) {
                            calculateDelayUntilNextSlot(appSettings.contentRefreshTime)
                        } else {
                            Long.MAX_VALUE
                        }
                    if (delayMs < AUTO_REFRESH_CHECK_INTERVAL_MS) {
                        delay(delayMs)
                        performFullSync()
                    }
                    delay(AUTO_REFRESH_CHECK_INTERVAL_MS) // Avoid immediate re-trigger
                }
            }
    }

    private suspend fun performFullSync() {
        Log.i(TAG, "Starting periodic content sync for all providers")
        val providerRepo = ProviderRepository(context)
        val providers = providerRepo.getAllProvidersList()

        providers.filter { it.type == "XTREAM" }.forEach { provider ->
            // The first encrypted-prefs open touches the Keystore (disk I/O) — keep it off Main.
            val password = withContext(Dispatchers.IO) { providerRepo.getPassword(provider.id) }
            if (password != null) {
                val startTime = System.currentTimeMillis()
                val outcome = ProviderSyncRunner.syncProvider(context, provider, password)
                if (outcome is ProviderSyncRunner.Outcome.Success) {
                    Log.d(TAG, "Sync completed for provider: ${provider.name}")
                }
                val endTime = System.currentTimeMillis()
                val delta = (outcome as? ProviderSyncRunner.Outcome.Success)?.delta
                providerRepo.updateSyncStats(
                    provider.id,
                    endTime,
                    endTime - startTime,
                    outcome.errorOrNull()?.encode(),
                    inserted = delta?.inserted,
                    updated = delta?.updated,
                    deleted = delta?.deleted,
                )
            }
        }
    }

    private fun updateWorkManagerSchedule(appSettings: AppSettings) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build()

        val initialDelay = calculateDelayUntilNextSlot(appSettings.contentRefreshTime)

        val request =
            PeriodicWorkRequestBuilder<XtreamSyncWorker>(REFRESH_INTERVAL_HOURS.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun cancelWorkManager() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Delay until the next sync slot. Slots repeat every [REFRESH_INTERVAL_HOURS] hours anchored
     * on [time], so an anchor of 04:00 gives 04:00, 08:00, 12:00, 16:00, 20:00, 00:00.
     */
    private fun calculateDelayUntilNextSlot(time: String): Long {
        try {
            val parts = time.split(":")
            if (parts.size != 2) return 0

            val hour = parts[0].toInt()
            val minute = parts[1].toInt()

            val now = Calendar.getInstance()
            val target =
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    // The anchor may be later today; step back a full day so the loop below always
                    // walks forward from a slot that is in the past.
                    add(Calendar.DAY_OF_YEAR, -1)
                }

            while (!target.after(now)) {
                target.add(Calendar.HOUR_OF_DAY, REFRESH_INTERVAL_HOURS)
            }
            return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
        } catch (e: Exception) {
            // cancellation-ok: non-suspend
            Log.w(TAG, "Failed to calculate delay for $time", e)
            return 0
        }
    }

    fun updateSchedule() {
        scheduleAutoRefresh()
    }

    /**
     * Runs in [scope], outside any ViewModel, so the sync goes on when the screen that asked for it
     * closes. The [Deferred] tells when *this* attempt ends; `try`/`finally` resolves it once on
     * every path, so no awaiter hangs. A provider that is already syncing returns that sync's
     * handle.
     */
    fun startManualSync(providerId: Long): Deferred<SyncResult> =
        synchronized(inFlightLock) {
            inFlight[providerId] ?: newManualSync(providerId).also { deferred ->
                inFlight[providerId] = deferred
                deferred.invokeOnCompletion {
                    synchronized(inFlightLock) { inFlight.remove(providerId) }
                }
            }
        }

    /**
     * The manual sync running for [providerId], or null, so a screen reopened mid-sync attaches to
     * it instead of showing an idle button.
     */
    fun inFlightSync(providerId: Long): Deferred<SyncResult>? = synchronized(inFlightLock) { inFlight[providerId] }

    private fun newManualSync(providerId: Long): Deferred<SyncResult> =
        scope.async {
            Log.i(TAG, "Starting manual sync for provider: $providerId")
            try {
                val providerRepo = ProviderRepository(context)
                val provider =
                    providerRepo.getProviderById(providerId)
                        ?: return@async SyncResult.Failed(context.getString(R.string.error_generic))
                // No password: the same words as XtreamSessionManager, shown as a lost or wrong login.
                val password =
                    withContext(Dispatchers.IO) { providerRepo.getPassword(providerId) }
                        ?: return@async SyncResult.Failed(friendlyErrorMessage(Exception("Password not stored"), context))

                val startTime = System.currentTimeMillis()
                val outcome = ProviderSyncRunner.syncProvider(context, provider, password)
                if (outcome is ProviderSyncRunner.Outcome.Success) {
                    Log.d(TAG, "Manual sync completed for provider: ${provider.name}")
                }
                val endTime = System.currentTimeMillis()
                val delta = (outcome as? ProviderSyncRunner.Outcome.Success)?.delta
                providerRepo.updateSyncStats(
                    providerId,
                    endTime,
                    endTime - startTime,
                    outcome.errorOrNull()?.encode(),
                    inserted = delta?.inserted,
                    updated = delta?.updated,
                    deleted = delta?.deleted,
                )
                if (outcome is ProviderSyncRunner.Outcome.Success) {
                    SyncResult.Success
                } else {
                    SyncResult.Failed(
                        outcome.errorOrNull()?.message(
                            context,
                            AppSettings(context).isNerdMode,
                        ) ?: context.getString(R.string.error_generic),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Manual sync threw for provider: $providerId", e)
                SyncResult.Failed(friendlyErrorMessage(e, context, AppSettings(context).isNerdMode))
            }
        }
}
