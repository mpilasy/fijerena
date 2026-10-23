@file:Suppress("DEPRECATION")

package org.njarasoa.fijerena.core.network.tmdb

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.BuildConfig
import org.njarasoa.fijerena.core.network.CredentialStoreHealth
import org.njarasoa.fijerena.core.network.sync.SettingsSyncQueue
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog

/**
 * Where a TMDB key stands. No build carries a key (D4 of
 * the public release plan): TMDB is on only with a key the user
 * entered, and a key TMDB refused (401) counts as no key until a different one is entered, so a
 * revoked key costs no request and shows no error anywhere but Settings.
 */
sealed interface TmdbKeyState {
    data object None : TmdbKeyState

    data class Active(
        val key: String,
    ) : TmdbKeyState

    data object Rejected : TmdbKeyState

    companion object {
        /** [rejectedKey] is the last key TMDB answered 401 to; it only matters while it is still [key]. */
        fun of(
            key: String?,
            rejectedKey: String?,
        ): TmdbKeyState =
            when {
                key.isNullOrBlank() -> None
                key == rejectedKey -> Rejected
                else -> Active(key)
            }
    }
}

/**
 * The user's TMDB key (a v3 API key or a v4 read access token), app-wide — not per profile or
 * source — in its own encrypted file, like provider passwords. Live sync carries it inside the E2E
 * envelope ([AppSettings.KEY_TMDB_API_KEY]); exports never do, since they carry no passwords.
 */
class TmdbKeyStore(
    context: Context,
) {
    private val appContext = context.applicationContext

    /** The key as entered (trimmed); empty when there is none. */
    val key: String get() = prefs(appContext).getString(KEY_API_KEY, null).orEmpty()

    val state: TmdbKeyState
        get() {
            val prefs = prefs(appContext)
            return TmdbKeyState.of(prefs.getString(KEY_API_KEY, null), prefs.getString(KEY_REJECTED, null))
        }

    /** The key to send, or null: none entered, or TMDB refused this one. */
    fun activeKey(): String? = (state as? TmdbKeyState.Active)?.key

    /** Saves [newKey] (blank removes it) and sends it to the other linked devices. */
    fun save(newKey: String) {
        write(newKey)
        SettingsSyncQueue.setting(appContext, AppSettings.KEY_TMDB_API_KEY)
    }

    /** A key received from another device: stored as is, not queued back. */
    fun applyRemote(newKey: String) = write(newKey)

    /** Whether the key was ever set or removed here — a removal syncs too, as an empty key. */
    fun hasValue(): Boolean = prefs(appContext).contains(KEY_API_KEY)

    /** TMDB answered 401 to [rejected]; ignored when the user has entered another key since. */
    fun markRejected(rejected: String) {
        val prefs = prefs(appContext)
        if (prefs.getString(KEY_API_KEY, null) == rejected) prefs.edit { putString(KEY_REJECTED, rejected) }
    }

    private fun write(newKey: String) =
        prefs(appContext).edit {
            putString(KEY_API_KEY, newKey.trim())
            remove(KEY_REJECTED)
        }

    private companion object {
        const val FILE_NAME = "tmdb_secure_key"
        const val KEY_API_KEY = "api_key"
        const val KEY_REJECTED = "rejected_key"

        @Volatile
        private var shared: SharedPreferences? = null

        // Process-wide, like AccountManager's: EncryptedSharedPreferences.create rebuilds the master
        // key and the crypto on every call. A file that can't be decrypted (lost Keystore key) is
        // reset — the key must be entered again; one that still can't be created is replaced by
        // memory-only prefs, never plaintext.
        fun prefs(context: Context): SharedPreferences =
            shared ?: synchronized(this) {
                shared ?: run {
                    try {
                        create(context)
                    } catch (e: Exception) {
                        // Not CredentialStoreHealth.markLost: that makes a failed source login
                        // blame lost credentials, and this file holds no login.
                        CrashLog.record("TMDB key store reset", e)
                        context.deleteSharedPreferences(FILE_NAME)
                        try {
                            create(context)
                        } catch (_: Exception) {
                            CredentialStoreHealth.InMemoryPrefs()
                        }
                    }
                }.also {
                    seedDebugKey(it)
                    shared = it
                }
            }

        // Debug builds only: the developer's key from local.properties, once, when this device has
        // never had a key set or removed. Not synced; each debug install seeds itself.
        private fun seedDebugKey(prefs: SharedPreferences) {
            val debugKey = BuildConfig.DEBUG_TMDB_API_KEY
            if (debugKey.isNotBlank() && !prefs.contains(KEY_API_KEY)) {
                prefs.edit { putString(KEY_API_KEY, debugKey.trim()) }
            }
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
