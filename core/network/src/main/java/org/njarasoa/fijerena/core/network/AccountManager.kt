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
) {
    // Lazy on purpose: keystore unlock + crypto + disk cost ~340ms, and the nav hosts construct
    // an AccountManager during composition while only reading from it inside coroutines. Built
    // eagerly it blocked the first frame; deferred, the cost lands on whichever caller reads
    // first — so [warmUp] exists to make that caller a background one.
    private val prefs: SharedPreferences by lazy { encryptedPrefs(context) }

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    companion object {
        // Process-wide, not per instance: EncryptedSharedPreferences.create rebuilds the master
        // key and the crypto each call, so an AccountManager constructed per screen would pay the
        // full cost every time instead of sharing one store.
        @Volatile
        private var sharedPrefs: SharedPreferences? = null

        @Suppress("DEPRECATION")
        private fun createEncryptedPrefs(context: Context): SharedPreferences =
            EncryptedSharedPreferences.create(
                context.applicationContext,
                "xtream_secure_credentials",
                MasterKey
                    .Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        // Jetpack Security Crypto is deprecated as of its first stable release (1.1.0). Still the
        // credential store; replacing it is tracked in docs/plans/secret-store-migration-plan.md.
        @Suppress("DEPRECATION")
        private fun encryptedPrefs(context: Context): SharedPreferences =
            sharedPrefs ?: synchronized(this) {
                sharedPrefs ?: try {
                    createEncryptedPrefs(context)
                } catch (_: Exception) {
                    val appContext = context.applicationContext
                    appContext.deleteSharedPreferences("xtream_secure_credentials")
                    try {
                        createEncryptedPrefs(appContext)
                    } catch (_: Exception) {
                        appContext.getSharedPreferences("xtream_secure_credentials", Context.MODE_PRIVATE)
                    }
                }.also { sharedPrefs = it }
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
    }

    fun getCredentials(): StoredCredentials? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null)
        return StoredCredentials(url, username, password)
    }

    /**
     * Resolves the lazy [prefs] delegate — keystore unlock, crypto and disk, together roughly a
     * third of a second. Call it from a background thread at startup: the deferral above only
     * moves that cost to the first reader, and the first reader has turned out to be a
     * LaunchedEffect on the main dispatcher, which puts the whole stall back on the UI thread.
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
     * Store basic credentials without an auth response.
     * Used by MediaProviderFactory to seed credentials for XtreamRepository.restoreSession().
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
     * Exports existing credentials for one-time migration to the multi-provider system.
     * Returns Triple(url, username, password) or null if no credentials stored.
     */
    fun exportForMigration(): Triple<String, String, String>? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return Triple(url, username, password)
    }

    /**
     * Updates only the provider URL while keeping username/password unchanged.
     * Clears auth response as it will be invalid with the new URL.
     */
    fun updateUrl(newUrl: String) {
        prefs.edit().apply {
            putString(KEY_URL, newUrl)
            remove(KEY_AUTH_RESPONSE) // Clear cached auth, will re-authenticate on next request
            apply()
        }
    }
}
