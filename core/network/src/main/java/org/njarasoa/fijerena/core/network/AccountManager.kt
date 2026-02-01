package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse

@Serializable
data class StoredCredentials(
    val url: String,
    val username: String,
    val password: String? = null
)

class AccountManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "xtream_secure_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
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
        rememberMe: Boolean
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

    fun hasStoredCredentials(): Boolean {
        return prefs.contains(KEY_URL) && prefs.contains(KEY_USERNAME)
    }

    fun hasRememberedCredentials(): Boolean {
        return hasStoredCredentials() && prefs.contains(KEY_PASSWORD)
    }

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

    fun clearAuthResponse() {
        prefs.edit().remove(KEY_AUTH_RESPONSE).apply()
    }
}
