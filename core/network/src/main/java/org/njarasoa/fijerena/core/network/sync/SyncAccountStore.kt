package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * This device's link to a sync account: the server URL, the device token (a credential — kept in
 * EncryptedSharedPreferences, like provider passwords), and the pull cursor. Per device, never
 * synced. Phase 8 adds the account key.
 */
class SyncAccountStore(
    context: Context,
) {
    // Jetpack Security Crypto is deprecated as of its first stable release (1.1.0). Still the
    // credential store; replacing it is tracked in docs/plans/20260828_secret-store-migration-plan.md.
    @Suppress("DEPRECATION")
    private val prefs: SharedPreferences by lazy {
        val masterKey =
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    data class Link(
        val serverUrl: String,
        val accountId: String,
        val deviceId: String,
        val deviceToken: String,
    )

    val link: Link?
        get() {
            val url = prefs.getString(KEY_URL, null) ?: return null
            val token = prefs.getString(KEY_TOKEN, null) ?: return null
            return Link(url, prefs.getString(KEY_ACCOUNT, "").orEmpty(), prefs.getString(KEY_DEVICE, "").orEmpty(), token)
        }

    /** Links this device; the next sync pulls everything, then uploads everything local. */
    fun saveLink(
        serverUrl: String,
        credentials: SyncWire.DeviceCredentials,
    ) = prefs.edit(commit = true) {
        clear()
        putString(KEY_URL, serverUrl.trimEnd('/'))
        putString(KEY_ACCOUNT, credentials.accountId)
        putString(KEY_DEVICE, credentials.deviceId)
        putString(KEY_TOKEN, credentials.deviceToken)
    }

    fun unlink() = prefs.edit(commit = true) { clear() }

    /** Last server `seq` applied here. */
    var cursor: Long
        get() = prefs.getLong(KEY_CURSOR, 0)
        set(value) = prefs.edit(commit = true) { putLong(KEY_CURSOR, value) }

    /** Whether everything that existed locally when this device linked has been queued for upload. */
    var seeded: Boolean
        get() = prefs.getBoolean(KEY_SEEDED, false)
        set(value) = prefs.edit(commit = true) { putBoolean(KEY_SEEDED, value) }

    /** Received records waiting for their provider or profile, as their wire JSON. */
    var deferred: Set<String>
        get() = prefs.getStringSet(KEY_DEFERRED, emptySet()).orEmpty()
        set(value) = prefs.edit(commit = true) { putStringSet(KEY_DEFERRED, value) }

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0)
        set(value) = prefs.edit(commit = true) { putLong(KEY_LAST_SYNC, value) }

    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) = prefs.edit(commit = true) { putString(KEY_LAST_ERROR, value) }

    private companion object {
        const val FILE_NAME = "sync_account"
        const val KEY_URL = "server_url"
        const val KEY_ACCOUNT = "account_id"
        const val KEY_DEVICE = "device_id"
        const val KEY_TOKEN = "device_token"
        const val KEY_CURSOR = "cursor"
        const val KEY_SEEDED = "seeded"
        const val KEY_DEFERRED = "deferred"
        const val KEY_LAST_SYNC = "last_sync_at"
        const val KEY_LAST_ERROR = "last_error"
    }
}
