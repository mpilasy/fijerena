@file:Suppress("DEPRECATION")

package org.njarasoa.fijerena.core.network
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse

@Serializable
data class StoredCredentials(
    val url: String,
    val username: String,
    val password: String? = null,
)

class AccountManager(
    context: Context,
    private val providerId: Long = 0L,
) {
    // Lazy on purpose: keystore unlock, crypto and disk cost ~340 ms, and the nav hosts build an
    // AccountManager during composition. Eager, it blocked the first frame; lazy, the cost lands on
    // the first reader, so [warmUp] makes that a background one.
    private val prefs: SharedPreferences by lazy { encryptedPrefs(context, providerId) }
    private val appContext = context.applicationContext

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    companion object {
        // Process-wide, not per instance: EncryptedSharedPreferences.create rebuilds the master
        // key and the crypto on each call. Keyed by providerId: 0 is the legacy file from before
        // multiple providers (migration and single-account flows); each real provider has its own
        // file, so provider B's background sync never overwrites what provider A's restore reads.
        private val sharedPrefs = java.util.concurrent.ConcurrentHashMap<Long, SharedPreferences>()

        private fun fileName(providerId: Long) =
            if (providerId <= 0L) "xtream_secure_credentials" else "xtream_secure_credentials_$providerId"

        @Suppress("DEPRECATION")
        private fun createEncryptedPrefs(
            context: Context,
            name: String,
        ): SharedPreferences =
            EncryptedSharedPreferences.create(
                context.applicationContext,
                name,
                MasterKey
                    .Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        // Jetpack Security Crypto is deprecated as of its first stable release (1.1.0). Still the
        // credential store; replacing it is tracked in docs/plans/20260828_secret-store-migration-plan.md.
        @Suppress("DEPRECATION")
        private fun encryptedPrefs(
            context: Context,
            providerId: Long,
        ): SharedPreferences =
            sharedPrefs[providerId] ?: synchronized(this) {
                sharedPrefs[providerId] ?: run {
                    val name = fileName(providerId)
                    try {
                        createEncryptedPrefs(context, name)
                    } catch (e: Exception) {
                        val appContext = context.applicationContext
                        CredentialStoreHealth.markLost(appContext, name, e)
                        appContext.deleteSharedPreferences(name)
                        try {
                            createEncryptedPrefs(appContext, name)
                        } catch (_: Exception) {
                            // Never a plaintext file — see CredentialStoreHealth.InMemoryPrefs.
                            CredentialStoreHealth.InMemoryPrefs()
                        }
                    }
                }.also { sharedPrefs[providerId] = it }
            }

        private const val KEY_URL = "url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_AUTH_RESPONSE = "auth_response"
        private const val KEY_REMEMBER_ME = "remember_me"
    }

    fun saveCredentials(
        url: String,
        username: String,
        password: String,
        authResponse: XtreamAuthResponse,
        rememberMe: Boolean,
    ) {
        prefs.edit().apply {
            putString(KEY_URL, url)
            putString(KEY_USERNAME, username)
            if (rememberMe) {
                putString(KEY_PASSWORD, password)
            } else {
                remove(KEY_PASSWORD)
            }
            putBoolean(KEY_REMEMBER_ME, rememberMe)
            putString(KEY_AUTH_RESPONSE, json.encodeToString(authResponse))
            apply()
        }
        CredentialStoreHealth.clear(appContext)
    }

    fun getCredentials(): StoredCredentials? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null)
        return StoredCredentials(url, username, password)
    }

    /**
     * Resolves the lazy [prefs] (keystore unlock, crypto and disk, about a third of a second). Call
     * it from a background thread at startup: else the first reader, a LaunchedEffect on the main
     * dispatcher, puts the stall on the UI thread.
     */
    fun warmUp() {
        prefs
    }

    fun hasStoredCredentials(): Boolean = prefs.contains(KEY_URL) && prefs.contains(KEY_USERNAME)

    fun hasRememberedCredentials(): Boolean = hasStoredCredentials() && prefs.contains(KEY_PASSWORD)

    fun getAuthResponse(): XtreamAuthResponse? {
        val authResponseJson = prefs.getString(KEY_AUTH_RESPONSE, null) ?: return null
        return try {
            json.decodeFromString<XtreamAuthResponse>(authResponseJson)
        } catch (e: Exception) {
            null
        }
    }

    fun clearCredentials() {
        prefs.edit().apply {
            remove(KEY_URL)
            remove(KEY_USERNAME)
            remove(KEY_PASSWORD)
            remove(KEY_REMEMBER_ME)
            remove(KEY_AUTH_RESPONSE)
            apply()
        }
    }

    /**
     * Stores a login without an auth response, so XtreamRepository.restoreSession() finds it
     * (MediaProviderFactory).
     */
    fun storeBasicCredentials(
        url: String,
        username: String,
        password: String,
    ) {
        prefs.edit().apply {
            putString(KEY_URL, url)
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
            putBoolean(KEY_REMEMBER_ME, true)
            apply()
        }
    }

    fun clearAuthResponse() {
        prefs.edit { remove(KEY_AUTH_RESPONSE) }
    }

    /**
     * The stored login for the one-time migration to multiple providers: (url, username, password),
     * or null when none is stored.
     */
    fun exportForMigration(): Triple<String, String, String>? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return Triple(url, username, password)
    }

    /**
     * Changes the provider URL and keeps the login. Clears the auth response, which the new URL
     * makes invalid.
     */
    fun updateUrl(newUrl: String) {
        prefs.edit().apply {
            putString(KEY_URL, newUrl)
            remove(KEY_AUTH_RESPONSE)
            apply()
        }
    }
}
