package org.njarasoa.fijerena.core.network.xtream

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.*
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.device.DeviceDetector
import org.njarasoa.fijerena.core.player.device.DeviceType
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Manages periodic background synchronization for Xtream IPTV providers.
 * Handles scheduling of XtreamSyncWorker.
 */
class ProviderSyncManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoRefreshJob: Job? = null

    /** Manual syncs currently running, keyed by provider id. Guarded by [inFlightLock]. */
    private val inFlightLock = Any()
    private val inFlight = mutableMapOf<Long, Deferred<SyncResult>>()

    /** Outcome of a manual sync — see [startManualSync]. */
    sealed interface SyncResult {
        data object Success : SyncResult

        data class Failed(val message: String) : SyncResult
    }

    companion object {
        private const val TAG = "ProviderSyncManager"
        private const val WORK_NAME = "provider_content_sync"
        private const val AUTO_REFRESH_CHECK_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

        /** Content syncs run on this cadence, anchored on `AppSettings.contentRefreshTime`. */
        private const val REFRESH_INTERVAL_HOURS = 4

        @Volatile
        private var instance: ProviderSyncManager? = null

        fun getInstance(context: Context): ProviderSyncManager {
            return instance ?: synchronized(this) {
                instance ?: ProviderSyncManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Initialize sync management. Call on app startup.
     */
    fun initialize() {
        scheduleAutoRefresh()
    }

    private fun isFixedDevice(): Boolean {
        val type = DeviceDetector.detect().deviceType
        return type != DeviceType.GENERIC_MOBILE
    }

    /**
     * Internal scheduling for both background (WorkManager) and foreground (Coroutine).
     */
    private fun scheduleAutoRefresh() {
        autoRefreshJob?.cancel()
        val appSettings = AppSettings(context)

        // Enqueued once per call rather than on every loop pass. Re-enqueuing with UPDATE rewrote
        // initialDelay every 15 minutes, and WorkManager only reads that field before the job's
        // first ever run — where a shrinking delay against a preserved lastEnqueueTime walked the
        // first run one tick earlier each pass. Settings changes come back in via updateSchedule().
        if (appSettings.contentAutoRefreshEnabled) {
            updateWorkManagerSchedule(appSettings)
        } else {
            cancelWorkManager()
        }

        // TV/fixed devices additionally refresh in-process whenever the app happens to be running.
        if (!isFixedDevice()) return
        autoRefreshJob = scope.launch {
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

    /**
     * Triggers a full sync for all Xtream providers.
     */
    private suspend fun performFullSync() {
        Log.i(TAG, "Starting periodic content sync for all providers")
        val providerRepo = ProviderRepository(context)
        val providers = providerRepo.getAllProvidersList()

        providers.filter { it.type == "XTREAM" }.forEach { provider ->
            val password = providerRepo.getPassword(provider.id)
            if (password != null) {
                val startTime = System.currentTimeMillis()
                val outcome = ProviderSyncRunner.syncProvider(context, provider, password)
                if (outcome is ProviderSyncRunner.Outcome.Success) {
                    Log.d(TAG, "Sync completed for provider: ${provider.name}")
                }
                val endTime = System.currentTimeMillis()
                val delta = (outcome as? ProviderSyncRunner.Outcome.Success)?.delta
                providerRepo.updateSyncStats(
                    provider.id, endTime, endTime - startTime, outcome.errorOrNull(),
                    inserted = delta?.inserted, updated = delta?.updated, deleted = delta?.deleted,
                )
            }
        }
    }

    private fun updateWorkManagerSchedule(appSettings: AppSettings) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val initialDelay = calculateDelayUntilNextSlot(appSettings.contentRefreshTime)

        val request = PeriodicWorkRequestBuilder<XtreamSyncWorker>(REFRESH_INTERVAL_HOURS.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
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
            val target = Calendar.getInstance().apply {
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
            Log.w(TAG, "Failed to calculate delay for $time", e)
            return 0
        }
    }

    fun updateSchedule() {
        scheduleAutoRefresh()
    }

    /**
     * Start a manual sync for a specific provider.
     * This runs in the manager's scope, so it persists even if the calling ViewModel is cleared.
     */
    /**
     * Runs in [scope] — a singleton scope outside any ViewModel — so the sync itself keeps going
     * even if the screen that requested it is closed. The returned [Deferred] is just a handle a
     * caller can await to know when *this* attempt is over; it resolves exactly once on every
     * path (success, a provider-side error, or bailing out early because the provider or its
     * password vanished) via `try`/`finally`, so nothing awaiting it is ever left hanging — unlike
     * the previous approach of a ViewModel polling for `lastSyncedAtMs` to have moved in the last
     * 30 seconds, which never noticed a sync that bailed out before reaching that DB write.
     *
     * Calls for a provider that is already syncing return that sync's handle instead of starting
     * a second one.
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
     * The manual sync currently running for [providerId], or null if none is. Lets a screen that
     * was closed and reopened mid-sync re-attach to the sync it started instead of showing an
     * idle button while the sync is still running.
     */
    fun inFlightSync(providerId: Long): Deferred<SyncResult>? = synchronized(inFlightLock) { inFlight[providerId] }

    private fun newManualSync(providerId: Long): Deferred<SyncResult> =
        scope.async {
            Log.i(TAG, "Starting manual sync for provider: $providerId")
            try {
                val providerRepo = ProviderRepository(context)
                val provider =
                    providerRepo.getProviderById(providerId)
                        ?: return@async SyncResult.Failed("Provider no longer exists")
                val password =
                    providerRepo.getPassword(providerId)
                        ?: return@async SyncResult.Failed("Stored credentials could not be read")

                val startTime = System.currentTimeMillis()
                val outcome = ProviderSyncRunner.syncProvider(context, provider, password)
                if (outcome is ProviderSyncRunner.Outcome.Success) {
                    Log.d(TAG, "Manual sync completed for provider: ${provider.name}")
                }
                val endTime = System.currentTimeMillis()
                val delta = (outcome as? ProviderSyncRunner.Outcome.Success)?.delta
                providerRepo.updateSyncStats(
                    providerId, endTime, endTime - startTime, outcome.errorOrNull(),
                    inserted = delta?.inserted, updated = delta?.updated, deleted = delta?.deleted,
                )
                if (outcome is ProviderSyncRunner.Outcome.Success) {
                    SyncResult.Success
                } else {
                    SyncResult.Failed(outcome.errorOrNull() ?: "Sync failed")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Manual sync threw for provider: $providerId", e)
                SyncResult.Failed(e.message ?: "Sync failed")
            }
        }
}
