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

    /** Outcome of a manual sync — see [startManualSync]. */
    sealed interface SyncResult {
        data object Success : SyncResult

        data class Failed(val message: String) : SyncResult
    }

    companion object {
        private const val TAG = "ProviderSyncManager"
        private const val WORK_NAME = "provider_content_sync"
        private const val AUTO_REFRESH_CHECK_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

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
        autoRefreshJob = scope.launch {
            val appSettings = AppSettings(context)
            while (true) {
                if (appSettings.contentAutoRefreshEnabled) {
                    val delayMs = calculateDelayUntil(appSettings.contentRefreshTime)
                    
                    // On TV/Fixed devices, we can perform the refresh directly if the app is running
                    if (isFixedDevice() && delayMs < AUTO_REFRESH_CHECK_INTERVAL_MS) {
                        delay(delayMs)
                        performFullSync()
                        delay(AUTO_REFRESH_CHECK_INTERVAL_MS) // Avoid immediate re-trigger
                    } else {
                        // For background execution, ensure WorkManager is scheduled
                        updateWorkManagerSchedule(appSettings)
                        delay(AUTO_REFRESH_CHECK_INTERVAL_MS)
                    }
                } else {
                    cancelWorkManager()
                    delay(AUTO_REFRESH_CHECK_INTERVAL_MS)
                }
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
                providerRepo.updateSyncStats(provider.id, endTime, endTime - startTime, outcome.errorOrNull())
            }
        }
    }

    private fun updateWorkManagerSchedule(appSettings: AppSettings) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val initialDelay = calculateDelayUntil(appSettings.contentRefreshTime)
        
        val request = PeriodicWorkRequestBuilder<XtreamSyncWorker>(24, TimeUnit.HOURS)
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

    private fun calculateDelayUntil(time: String): Long {
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
            }

            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
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
     */
    fun startManualSync(providerId: Long): Deferred<SyncResult> =
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
                providerRepo.updateSyncStats(providerId, endTime, endTime - startTime, outcome.errorOrNull())
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
