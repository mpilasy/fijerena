package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog

/**
 * This device's link to a sync account: the server URL, the device token and the account key
 * (credentials — kept in EncryptedSharedPreferences, like provider passwords), and the pull cursor.
 * Per device, never synced.
 */
class SyncAccountStore(
    context: Context,
) {
    // Null only when the encrypted store can't be opened even after a reset (see [open]): this
    // device then behaves as unlinked, and pairing fails with an error instead of storing
    // credentials anywhere less safe.
    private val prefs: SharedPreferences? by lazy { open(context) }

    /**
     * The Keystore master key can be lost (OEM Keystore resets, data restored without its key):
     * the file then can't be decrypted and `create` throws. Read on every app start (SyncManager),
     * that used to crash the app on every launch. The link is unrecoverable either way — reset it,
     * so this device shows as unlinked and can pair again. See
     * docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-21.
     */
    private fun open(context: Context): SharedPreferences? {
        val prefs =
            try {
                create(context)
            } catch (e: Exception) {
                Log.e(TAG, "Sync link store unreadable — resetting; this device must pair again", e)
                CrashLog.record("sync link store reset", e)
                context.deleteSharedPreferences(FILE_NAME)
                try {
                    create(context)
                } catch (e2: Exception) {
                    Log.e(TAG, "Sync link store still unusable after reset — sync disabled", e2)
                    CrashLog.record("sync link store unusable", e2)
                    null
                }
            }
        return prefs
    }

    // Jetpack Security Crypto is deprecated as of its first stable release (1.1.0). Still the
    // credential store; replacing it is tracked in docs/plans/20260828_secret-store-migration-plan.md.
    @Suppress("DEPRECATION")
    private fun create(context: Context): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    data class Link(
        val serverUrl: String,
        val accountId: String,
        val deviceId: String,
        val deviceToken: String,
        /** The account key every device of the account shares: payloads, keys and tags are made with it. */
        val accountKey: ByteArray,
    )

    /**
     * Null when not linked — and for a link made before Phase 8, which has no account key: its
     * plaintext records can't be read by an encrypted device anyway, so it must pair again.
     */
    val link: Link?
        get() {
            val prefs = prefs ?: return null
            val url = prefs.getString(KEY_URL, null) ?: return null
            val token = prefs.getString(KEY_TOKEN, null) ?: return null
            val key = prefs.getString(KEY_ACCOUNT_KEY, null)?.let { runCatching { B64URL.decode(it) }.getOrNull() } ?: return null
            return Link(url, prefs.getString(KEY_ACCOUNT, "").orEmpty(), prefs.getString(KEY_DEVICE, "").orEmpty(), token, key)
        }

    /** Links this device; the next sync pulls everything, then uploads everything local. */
    fun saveLink(
        serverUrl: String,
        credentials: SyncWire.DeviceCredentials,
        accountKey: ByteArray,
    ) = checkNotNull(prefs) { "Sync link store unavailable on this device" }.edit(commit = true) {
        clear()
        putString(KEY_URL, serverUrl.trimEnd('/'))
        putString(KEY_ACCOUNT, credentials.accountId)
        putString(KEY_DEVICE, credentials.deviceId)
        putString(KEY_TOKEN, credentials.deviceToken)
        putString(KEY_ACCOUNT_KEY, B64URL.encodeToString(accountKey))
    }

    fun unlink() = prefs?.edit(commit = true) { clear() }

    /** Last server `seq` applied here. */
    var cursor: Long
        get() = prefs?.getLong(KEY_CURSOR, 0) ?: 0
        set(value) = prefs?.edit(commit = true) { putLong(KEY_CURSOR, value) } ?: Unit

    /** Whether everything that existed locally when this device linked has been queued for upload. */
    var seeded: Boolean
        get() = prefs?.getBoolean(KEY_SEEDED, false) ?: false
        set(value) = prefs?.edit(commit = true) { putBoolean(KEY_SEEDED, value) } ?: Unit

    /** Received records waiting for their provider or profile, as their wire JSON. */
    var deferred: Set<String>
        get() = prefs?.getStringSet(KEY_DEFERRED, emptySet()).orEmpty()
        set(value) = prefs?.edit(commit = true) { putStringSet(KEY_DEFERRED, value) } ?: Unit

    /** [cursor] and [deferred] in one commit, so a crash can't leave one advanced without the other. */
    fun savePullProgress(
        cursor: Long,
        deferred: Set<String>,
    ) = prefs?.edit(commit = true) {
        putLong(KEY_CURSOR, cursor)
        putStringSet(KEY_DEFERRED, deferred)
    } ?: Unit

    var lastSyncAt: Long
        get() = prefs?.getLong(KEY_LAST_SYNC, 0) ?: 0
        set(value) = prefs?.edit(commit = true) { putLong(KEY_LAST_SYNC, value) } ?: Unit

    var lastError: String?
        get() = prefs?.getString(KEY_LAST_ERROR, null)
        set(value) = prefs?.edit(commit = true) { putString(KEY_LAST_ERROR, value) } ?: Unit

    private companion object {
        const val TAG = "SyncAccountStore"
        const val FILE_NAME = "sync_account"
        const val KEY_URL = "server_url"
        const val KEY_ACCOUNT = "account_id"
        const val KEY_DEVICE = "device_id"
        const val KEY_TOKEN = "device_token"
        const val KEY_ACCOUNT_KEY = "account_key"
        const val KEY_CURSOR = "cursor"
        const val KEY_SEEDED = "seeded"
        const val KEY_DEFERRED = "deferred"
        const val KEY_LAST_SYNC = "last_sync_at"
        const val KEY_LAST_ERROR = "last_error"
    }
}
