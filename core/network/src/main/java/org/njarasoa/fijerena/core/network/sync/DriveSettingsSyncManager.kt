package org.njarasoa.fijerena.core.network.sync
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.provider.ProviderRepository

/**
 * Manages bidirectional sync between local ProviderSettings and Google Drive.
 * Uses the appDataFolder which is a private, app-specific folder on Drive.
 */
class DriveSettingsSyncManager(
    private val context: Context,
    private val providerRepository: ProviderRepository,
) {
    companion object {
        private const val TAG = "DriveSettingsSyncManager"
        private const val PREFS_NAME = "drive_sync_prefs"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_LAST_SYNC = "last_sync"
    }

    val authManager = GoogleAuthManager(context)
    private val driveRepository = DriveSettingsRepository(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Sync status for UI display.
     */
    sealed interface SyncStatus {
        data object NotSignedIn : SyncStatus

        data object Idle : SyncStatus

        data object Syncing : SyncStatus

        data object Synced : SyncStatus

        data class Error(
            val message: String,
        ) : SyncStatus
    }

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.NotSignedIn)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _signedInEmail = MutableStateFlow<String?>(null)
    val signedInEmail: StateFlow<String?> = _signedInEmail.asStateFlow()

    /**
     * Check if sync is enabled.
     */
    var isSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_SYNC_ENABLED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_SYNC_ENABLED, value) }
        }

    /**
     * Get last sync timestamp.
     */
    val lastSyncTime: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)

    /**
     * Initialize sync manager. Call on app startup.
     * Attempts silent sign-in and downloads settings if signed in.
     */
    fun initialize() {
        scope.launch {
            if (!isSyncEnabled) {
                _syncStatus.value = SyncStatus.NotSignedIn
                return@launch
            }

            val signedIn = authManager.trySilentSignIn()
            if (signedIn) {
                _signedInEmail.value = authManager.getAccount()?.email
                _syncStatus.value = SyncStatus.Idle

                // Download settings on startup
                downloadAndApplySettings()
            } else {
                _syncStatus.value = SyncStatus.NotSignedIn
            }
        }
    }

    /**
     * Get sign-in intent. Use when user wants to enable sync.
     */
    fun getSignInIntent(): Intent = authManager.getSignInIntent()

    /**
     * Handle sign-in result from activity.
     */
    suspend fun handleSignInResult(data: Intent?): Boolean {
        val account = authManager.handleSignInResult(data)
        if (account != null) {
            _signedInEmail.value = account.email
            isSyncEnabled = true
            _syncStatus.value = SyncStatus.Idle

            // Download existing settings from new account
            downloadAndApplySettings()
            return true
        }
        return false
    }

    /**
     * Sign out and disable sync.
     */
    suspend fun signOut() {
        authManager.signOut()
        _signedInEmail.value = null
        isSyncEnabled = false
        _syncStatus.value = SyncStatus.NotSignedIn
    }

    /**
     * Download settings from Drive and apply to local storage.
     * Use on startup and when connecting new device.
     */
    suspend fun downloadAndApplySettings(): Boolean {
        val credential =
            authManager.getCredential() ?: run {
                _syncStatus.value = SyncStatus.NotSignedIn
                return false
            }

        _syncStatus.value = SyncStatus.Syncing
        try {
            val remoteSettings = driveRepository.downloadSettings(credential)
            if (remoteSettings != null) {
                // Apply each provider's settings
                remoteSettings.providers.values.forEach { providerData ->
                    try {
                        providerRepository.updateProviderSettings(
                            providerData.providerId,
                            providerData.settings,
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not apply settings for provider ${providerData.providerId}", e)
                    }
                }
                prefs.edit { putLong(KEY_LAST_SYNC, System.currentTimeMillis()) }
                _syncStatus.value = SyncStatus.Synced
                return true
            } else {
                // No remote settings yet - upload current settings
                return uploadAllSettings()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download settings", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Download failed")
            return false
        }
    }

    /**
     * Upload all provider settings to Drive.
     */
    suspend fun uploadAllSettings(): Boolean {
        val credential =
            authManager.getCredential() ?: run {
                _syncStatus.value = SyncStatus.NotSignedIn
                return false
            }

        _syncStatus.value = SyncStatus.Syncing
        try {
            val providers = providerRepository.getAllProvidersList()
            val providerDataMap =
                providers.associate { provider ->
                    val settings = providerRepository.getProviderSettings(provider.id)
                    provider.id.toString() to
                        DriveSettingsRepository.ProviderSyncData(
                            providerId = provider.id,
                            providerName = provider.name,
                            providerType = provider.type,
                            settings = settings,
                        )
                }

            val syncedSettings =
                DriveSettingsRepository.SyncedSettings(
                    providers = providerDataMap,
                )

            val success = driveRepository.uploadSettings(credential, syncedSettings)
            if (success) {
                prefs.edit { putLong(KEY_LAST_SYNC, System.currentTimeMillis()) }
                _syncStatus.value = SyncStatus.Synced
            } else {
                _syncStatus.value = SyncStatus.Error("Upload failed")
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload settings", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Upload failed")
            return false
        }
    }

    /**
     * Sync a single provider's settings to Drive.
     * Call after updating local settings.
     */
    suspend fun syncProviderSettings(providerId: Long) {
        if (!isSyncEnabled || !authManager.isSignedIn()) return

        // Just upload all settings (simpler than partial updates)
        uploadAllSettings()
    }

    /**
     * Called when a provider is deleted.
     */
    suspend fun onProviderDeleted(providerId: Long) {
        if (!isSyncEnabled || !authManager.isSignedIn()) return
        uploadAllSettings()
    }

    /**
     * Force sync now.
     */
    suspend fun syncNow(): Boolean {
        if (!authManager.isSignedIn()) {
            _syncStatus.value = SyncStatus.NotSignedIn
            return false
        }
        return uploadAllSettings()
    }
}
