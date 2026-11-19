@file:Suppress("DEPRECATION")

package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog

/**
 * This device's link to a sync account: the server URL, the device token and the account key
 * (credentials — kept in EncryptedSharedPreferences, like provider passwords), and the pull cursor.
 * Per device, never synced.
 */
class SyncAccountStore private constructor(
    opener: () -> SharedPreferences?,
) {
    constructor(context: Context) : this({ open(context) })

    /** For tests: a store on plain [prefs], with no encryption. */
    internal constructor(prefs: SharedPreferences) : this({ prefs })

    // Null only when the encrypted store can't be opened even after a reset (see [open]): this
    // device then behaves as unlinked, and pairing fails with an error instead of storing
    // credentials anywhere less safe.
    private val prefs: SharedPreferences? by lazy(opener)

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

    /**
     * Moves this device's link to the same group on another server (the sync server move plan,
     * step 8): the new address and device credentials, with the same account key. The next pull
     * starts at the new server's first record. Unlike [saveLink], it keeps [seeded], [deferred],
     * [knownKinds] and the old-server record, and the pending local changes (in the databases) are
     * not touched: this device sends only those, never all its local data again. The state of the
     * move ([moveProgress], [moveFollow], [movedAway]) goes in the same commit.
     */
    fun switchServer(
        serverUrl: String,
        credentials: SyncWire.DeviceCredentials,
    ) = checkNotNull(prefs) { "Sync link store unavailable on this device" }.edit(commit = true) {
        putString(KEY_URL, serverUrl.trimEnd('/'))
        putString(KEY_ACCOUNT, credentials.accountId)
        putString(KEY_DEVICE, credentials.deviceId)
        putString(KEY_TOKEN, credentials.deviceToken)
        putLong(KEY_CURSOR, 0)
        remove(KEY_MOVE_PROGRESS)
        remove(KEY_MOVE_FOLLOW)
        remove(KEY_MOVED_AWAY)
        remove(KEY_LAST_ERROR)
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

    /** The [SyncKind.KEPT] of the build that last started a full pull here; null before the first. */
    val knownKinds: Set<String>?
        get() = prefs?.getStringSet(KEY_KNOWN_KINDS, null)

    /** The next pull starts from the server's first record; [knownKinds] becomes [kinds]. */
    fun startOver(kinds: Set<String>) =
        prefs?.edit(commit = true) {
            putLong(KEY_CURSOR, 0)
            putStringSet(KEY_KNOWN_KINDS, kinds)
        } ?: Unit

    var lastSyncAt: Long
        get() = prefs?.getLong(KEY_LAST_SYNC, 0) ?: 0
        set(value) = prefs?.edit(commit = true) { putLong(KEY_LAST_SYNC, value) } ?: Unit

    var lastError: String?
        get() = prefs?.getString(KEY_LAST_ERROR, null)
        set(value) = prefs?.edit(commit = true) { putString(KEY_LAST_ERROR, value) } ?: Unit

    /**
     * A move that this device started and that is not finished (the sync server move plan →
     * Resume): null when there is none. No sync pass runs while it is set.
     */
    var moveProgress: SyncServerMove.Progress?
        get() = read(KEY_MOVE_PROGRESS, SyncServerMove.Progress.serializer())
        set(value) = write(KEY_MOVE_PROGRESS, value, SyncServerMove.Progress.serializer())

    /** A move that another device started and that this device follows, not finished; null when there is none. */
    var moveFollow: SyncServerMove.Follow?
        get() = read(KEY_MOVE_FOLLOW, SyncServerMove.Follow.serializer())
        set(value) = write(KEY_MOVE_FOLLOW, value, SyncServerMove.Follow.serializer())

    /** The address that the group moved to when this device is not in the move record; null otherwise. */
    var movedAway: String?
        get() = prefs?.getString(KEY_MOVED_AWAY, null)
        set(value) = prefs?.edit(commit = true) { if (value == null) remove(KEY_MOVED_AWAY) else putString(KEY_MOVED_AWAY, value) } ?: Unit

    /**
     * The last `sync_previous_server` record this device took, with its clock value. Its
     * [SyncServerMove.PreviousServerRecord.server] is null after a deletion.
     */
    var previousServerRecord: SyncServerMove.PreviousServerRecord?
        get() = read(KEY_PREVIOUS_SERVER, SyncServerMove.PreviousServerRecord.serializer())
        set(value) = write(KEY_PREVIOUS_SERVER, value, SyncServerMove.PreviousServerRecord.serializer())

    /** The old server of the group after a move, while the group has not purged or forgotten it; null otherwise. */
    val previousServer: SyncServerMove.PreviousServer?
        get() = previousServerRecord?.server

    private fun <T> read(
        key: String,
        serializer: KSerializer<T>,
    ): T? = prefs?.getString(key, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    private fun <T> write(
        key: String,
        value: T?,
        serializer: KSerializer<T>,
    ) = prefs?.edit(commit = true) {
        if (value == null) remove(key) else putString(key, json.encodeToString(serializer, value))
    } ?: Unit

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
        const val KEY_KNOWN_KINDS = "known_kinds"
        const val KEY_LAST_SYNC = "last_sync_at"
        const val KEY_LAST_ERROR = "last_error"
        const val KEY_MOVE_PROGRESS = "move_progress"
        const val KEY_MOVE_FOLLOW = "move_follow"
        const val KEY_MOVED_AWAY = "moved_away"
        const val KEY_PREVIOUS_SERVER = "previous_server"

        val json = Json { ignoreUnknownKeys = true }

        /**
         * The Keystore master key can be lost (OEM Keystore resets, data restored without its key):
         * the file then can't be decrypted and `create` throws. Read at every app start (SyncManager),
         * so a throw would crash every launch. The link is lost either way: reset it, so this device
         * shows as unlinked and can pair again. See the rock solid stability resilience plan → F-21.
         */
        fun open(context: Context): SharedPreferences? {
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
        fun create(context: Context): SharedPreferences =
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
    }
}
