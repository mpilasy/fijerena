package org.njarasoa.fijerena.core.network.provider

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow

/**
 * Manages provider CRUD and per-provider encrypted password storage.
 * Passwords are stored in per-provider EncryptedSharedPreferences files.
 * Cache data uses per-provider namespaced SharedPreferences (handled by XtreamRepository).
 */
class ProviderRepository(private val context: Context) {

    private val db = ProviderDatabase.getInstance(context)
    private val dao = db.providerDao()

    fun getAllProviders(): Flow<List<ProviderEntity>> = dao.getAllProviders()

    suspend fun getAllProvidersList(): List<ProviderEntity> = dao.getAllProvidersList()

    suspend fun getActiveProvider(): ProviderEntity? = dao.getActiveProvider()

    suspend fun getProviderById(id: Long): ProviderEntity? = dao.getProviderById(id)

    suspend fun getProviderCount(): Int = dao.getProviderCount()

    /**
     * Add a new provider. Stores the password in a per-provider encrypted prefs file.
     * Deactivates all other providers and activates this one.
     */
    suspend fun addProvider(
        name: String,
        url: String,
        username: String,
        password: String
    ): Long {
        dao.deactivateAll()
        val entity = ProviderEntity(
            name = name,
            url = url,
            username = username,
            isActive = true
        )
        val id = dao.insertProvider(entity)
        savePassword(id, password)
        return id
    }

    /**
     * Update an existing provider's details.
     */
    suspend fun updateProvider(
        id: Long,
        name: String,
        url: String,
        username: String,
        password: String
    ) {
        val existing = dao.getProviderById(id) ?: return
        dao.updateProvider(
            existing.copy(
                name = name,
                url = url,
                username = username
            )
        )
        savePassword(id, password)
    }

    /**
     * Delete a provider and clean up its encrypted prefs and cache.
     */
    suspend fun deleteProvider(id: Long) {
        val entity = dao.getProviderById(id) ?: return
        dao.deleteProvider(entity)
        clearProviderPassword(id)
        clearProviderCache(id)
    }

    /**
     * Set a provider as active (deactivates all others).
     */
    suspend fun setActiveProvider(id: Long) {
        dao.deactivateAll()
        dao.activateProvider(id)
    }

    /**
     * Get the stored password for a provider.
     */
    fun getPassword(providerId: Long): String? {
        return getProviderPrefs(providerId)?.getString("password", null)
    }

    // --- Private helpers ---

    private fun savePassword(providerId: Long, password: String) {
        getProviderPrefs(providerId)?.edit()
            ?.putString("password", password)
            ?.apply()
    }

    private fun clearProviderPassword(providerId: Long) {
        try {
            getProviderPrefs(providerId)?.edit()?.clear()?.apply()
        } catch (_: Exception) {
            // Ignore errors clearing prefs for deleted provider
        }
    }

    private fun clearProviderCache(providerId: Long) {
        try {
            val cacheName = "xtream_cache_$providerId"
            context.getSharedPreferences(cacheName, Context.MODE_PRIVATE)
                .edit().clear().apply()
        } catch (_: Exception) {
            // Ignore errors clearing cache for deleted provider
        }
    }

    private fun getProviderPrefs(providerId: Long): android.content.SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "provider_creds_$providerId",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            null
        }
    }
}
