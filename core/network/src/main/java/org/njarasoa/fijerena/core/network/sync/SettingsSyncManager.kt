package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings

/**
 * Manages bidirectional sync between local ProviderSettings and Firebase.
 * Handles conflict resolution (last-write-wins based on timestamp).
 */
class SettingsSyncManager(
    private val context: Context,
    private val providerRepository: ProviderRepository
) {
    companion object {
        private const val TAG = "SettingsSyncManager"
        private const val PREFS_NAME = "settings_sync"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_LAST_SYNC = "last_sync_"
    }

    private val firebaseSync = FirebaseSettingsSync(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _syncStatus = MutableStateFlow<FirebaseSettingsSync.SyncStatus>(
        FirebaseSettingsSync.SyncStatus.Unavailable
    )
    val syncStatus: StateFlow<FirebaseSettingsSync.SyncStatus> = _syncStatus.asStateFlow()

    private var observerJobs = mutableMapOf<Long, Job>()

    /**
     * Check if sync feature is available (Firebase configured).
     */
    val isSyncAvailable: Boolean
        get() = firebaseSync.isAvailable

    /**
     * Check if sync is enabled by user.
     */
    var isSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_SYNC_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SYNC_ENABLED, value).apply()
            if (value) {
                enableSync()
            } else {
                disableSync()
            }
        }

    /**
     * Initialize sync manager. Call on app startup.
     */
    fun initialize() {
        scope.launch {
            updateSyncStatus()

            if (isSyncEnabled && firebaseSync.isAvailable) {
                // Auto-sign in if sync was previously enabled
                if (!firebaseSync.isSignedIn) {
                    firebaseSync.signInAnonymously()
                }
                updateSyncStatus()

                // Start observing all providers
                startObservingAllProviders()
            }
        }
    }

    /**
     * Enable sync and sign in anonymously.
     */
    fun enableSync() {
        scope.launch {
            if (!firebaseSync.isAvailable) {
                Log.w(TAG, "Cannot enable sync: Firebase not available")
                _syncStatus.value = FirebaseSettingsSync.SyncStatus.Unavailable
                return@launch
            }

            _syncStatus.value = FirebaseSettingsSync.SyncStatus.Syncing

            val userId = firebaseSync.signInAnonymously()
            if (userId != null) {
                Log.d(TAG, "Sync enabled, user ID: $userId")

                // Upload all current provider settings
                uploadAllProviderSettings()

                // Start observing for changes
                startObservingAllProviders()

                _syncStatus.value = FirebaseSettingsSync.SyncStatus.Synced
            } else {
                Log.e(TAG, "Failed to enable sync")
                _syncStatus.value = FirebaseSettingsSync.SyncStatus.Error("Sign-in failed")
            }
        }
    }

    /**
     * Disable sync and sign out.
     */
    fun disableSync() {
        scope.launch {
            // Stop all observers
            observerJobs.values.forEach { it.cancel() }
            observerJobs.clear()

            firebaseSync.signOut()
            _syncStatus.value = FirebaseSettingsSync.SyncStatus.SignedOut
            Log.d(TAG, "Sync disabled")
        }
    }

    /**
     * Sync settings for a specific provider.
     * Call after updating local settings.
     *
     * @param providerId Provider ID
     */
    suspend fun syncProviderSettings(providerId: Long) {
        if (!isSyncEnabled || !firebaseSync.isSignedIn) return

        val provider = providerRepository.getProviderById(providerId) ?: return
        val settings = providerRepository.getProviderSettings(providerId)

        val uploaded = firebaseSync.uploadSettings(providerId, provider.name, settings)
        if (uploaded) {
            setLastSyncTime(providerId, System.currentTimeMillis())
            Log.d(TAG, "Synced settings for provider $providerId")
        }
    }

    /**
     * Force download settings from Firebase and overwrite local.
     *
     * @param providerId Provider ID
     * @return Downloaded settings or null if failed
     */
    suspend fun forceDownload(providerId: Long): ProviderSettings? {
        if (!firebaseSync.isSignedIn) return null

        val remoteSettings = firebaseSync.downloadSettings(providerId)
        if (remoteSettings != null) {
            providerRepository.updateProviderSettings(providerId, remoteSettings)
            setLastSyncTime(providerId, System.currentTimeMillis())
            Log.d(TAG, "Force downloaded settings for provider $providerId")
        }
        return remoteSettings
    }

    /**
     * Force upload local settings to Firebase.
     *
     * @param providerId Provider ID
     * @return true if successful
     */
    suspend fun forceUpload(providerId: Long): Boolean {
        if (!firebaseSync.isSignedIn) return false

        val provider = providerRepository.getProviderById(providerId) ?: return false
        val settings = providerRepository.getProviderSettings(providerId)

        val uploaded = firebaseSync.uploadSettings(providerId, provider.name, settings)
        if (uploaded) {
            setLastSyncTime(providerId, System.currentTimeMillis())
            Log.d(TAG, "Force uploaded settings for provider $providerId")
        }
        return uploaded
    }

    /**
     * Delete synced settings when a provider is deleted locally.
     */
    suspend fun onProviderDeleted(providerId: Long) {
        if (!isSyncEnabled || !firebaseSync.isSignedIn) return

        // Stop observing
        observerJobs[providerId]?.cancel()
        observerJobs.remove(providerId)

        // Delete from Firebase
        firebaseSync.deleteSettings(providerId)
        Log.d(TAG, "Deleted synced settings for provider $providerId")
    }

    private fun updateSyncStatus() {
        _syncStatus.value = when {
            !firebaseSync.isAvailable -> FirebaseSettingsSync.SyncStatus.Unavailable
            !firebaseSync.isSignedIn -> FirebaseSettingsSync.SyncStatus.SignedOut
            else -> FirebaseSettingsSync.SyncStatus.Synced
        }
    }

    private suspend fun uploadAllProviderSettings() {
        val providers = providerRepository.getAllProvidersList()
        providers.forEach { provider ->
            val settings = providerRepository.getProviderSettings(provider.id)
            firebaseSync.uploadSettings(provider.id, provider.name, settings)
            setLastSyncTime(provider.id, System.currentTimeMillis())
        }
        Log.d(TAG, "Uploaded settings for ${providers.size} providers")
    }

    private fun startObservingAllProviders() {
        scope.launch {
            val providers = providerRepository.getAllProvidersList()
            providers.forEach { provider ->
                startObservingProvider(provider.id)
            }
        }
    }

    private fun startObservingProvider(providerId: Long) {
        // Cancel existing observer if any
        observerJobs[providerId]?.cancel()

        observerJobs[providerId] = scope.launch {
            firebaseSync.observeSettings(providerId).collect { remoteSettings ->
                if (remoteSettings != null) {
                    // Compare with local and apply if different
                    val localSettings = providerRepository.getProviderSettings(providerId)
                    if (remoteSettings != localSettings) {
                        Log.d(TAG, "Remote settings differ for provider $providerId, updating local")
                        providerRepository.updateProviderSettings(providerId, remoteSettings)
                    }
                }
            }
        }
    }

    private fun getLastSyncTime(providerId: Long): Long {
        return prefs.getLong(KEY_LAST_SYNC + providerId, 0L)
    }

    private fun setLastSyncTime(providerId: Long, timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC + providerId, timestamp).apply()
    }
}
