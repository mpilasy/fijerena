package org.njarasoa.fijerena.core.network.xtream

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.*
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.XtreamMediaProvider
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.xmltv.EpgChannelMatcher
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
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
                var syncError: String? = null
                try {
                    val mediaProvider = MediaProviderFactory.create(provider, context, password)
                    if (mediaProvider is XtreamMediaProvider) {
                        if (!mediaProvider.isConnected()) {
                            mediaProvider.connect()
                        }
                        if (mediaProvider.isConnected()) {
                            mediaProvider.syncAll()
                            
                            // Proactively warm the cache for the provider that was just synced
                            val streams = XtreamDatabase.getInstance(context)
                                .streamDao()
                                .getAllStreams(provider.id, org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity.TYPE_LIVE)
                            EpgChannelMatcher.warmCache(provider.id, streams)
                            
                            Log.d(TAG, "Sync completed for provider: ${provider.name}")
                        } else {
                            syncError = "Failed to connect"
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing provider ${provider.name}", e)
                    syncError = e.message ?: "Unknown error"
                } finally {
                    val endTime = System.currentTimeMillis()
                    val duration = endTime - startTime
                    providerRepo.updateSyncStats(provider.id, endTime, duration, syncError)
                }
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
    fun startManualSync(providerId: Long) {
        scope.launch {
            Log.i(TAG, "Starting manual sync for provider: $providerId")
            val providerRepo = ProviderRepository(context)
            val provider = providerRepo.getProviderById(providerId) ?: return@launch
            val password = providerRepo.getPassword(providerId) ?: return@launch

            val startTime = System.currentTimeMillis()
            var syncError: String? = null
            try {
                val mediaProvider = MediaProviderFactory.create(provider, context, password)
                if (mediaProvider is XtreamMediaProvider) {
                    if (!mediaProvider.isConnected()) {
                        mediaProvider.connect()
                    }
                    if (mediaProvider.isConnected()) {
                        mediaProvider.syncAll()
                        
                        // Proactively warm the cache for the provider that was just synced
                        val streams = XtreamDatabase.getInstance(context)
                            .streamDao()
                            .getAllStreams(provider.id, org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity.TYPE_LIVE)
                        org.njarasoa.fijerena.core.network.xmltv.EpgChannelMatcher.warmCache(provider.id, streams)
                        
                        Log.d(TAG, "Manual sync completed for provider: ${provider.name}")
                    } else {
                        syncError = "Failed to connect"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during manual sync for ${provider.name}", e)
                syncError = e.message ?: "Unknown error"
            } finally {
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                providerRepo.updateSyncStats(providerId, endTime, duration, syncError)
            }
        }
    }
}
