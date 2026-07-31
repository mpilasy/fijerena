package org.njarasoa.fijerena.core.network.provider
import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.XtreamRepository

/**
 * Manages provider CRUD and per-provider encrypted password storage.
 * Passwords are stored in per-provider EncryptedSharedPreferences files.
 * Cache data uses per-provider namespaced SharedPreferences (handled by XtreamRepository).
 */
class ProviderRepository(
    private val context: Context,
) {
    private val db = SettingsDatabase.getInstance(context)
    private val dao = db.providerDao()

    private val masterKey: MasterKey by lazy {
        MasterKey
            .Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefsCache = java.util.concurrent.ConcurrentHashMap<Long, android.content.SharedPreferences>()
    private val settingsCache = java.util.concurrent.ConcurrentHashMap<Long, ProviderSettings>()

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
        password: String,
        type: String = "XTREAM",
        config: String = "",
        initialSettings: ProviderSettings = ProviderSettings.DEFAULT,
    ): Long {
        dao.deactivateAll()
        val settingsJson = json.encodeToString(initialSettings)
        val entity =
            ProviderEntity(
                name = name,
                url = url,
                username = username,
                type = type,
                config = config,
                providerSettings = settingsJson,
                isActive = true,
            )
        val id = dao.insertProvider(entity)
        savePassword(id, password)
        settingsCache[id] = initialSettings
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
        password: String,
        type: String? = null,
        config: String? = null,
    ) {
        val existing = dao.getProviderById(id) ?: return
        dao.updateProvider(
            existing.copy(
                name = name,
                url = url,
                username = username,
                type = type ?: existing.type,
                config = config ?: existing.config,
            ),
        )
        savePassword(id, password)
        // If Jellyfin credentials changed, discard the cached session token so the
        // provider re-authenticates with the new username/password on next use.
        val effectiveType = type ?: existing.type
        if (effectiveType == "JELLYFIN") {
            getProviderPrefs(id).edit {
                remove("jellyfin_token")
                    .remove("jellyfin_user_id")
            }
        }
        // Clear cached provider instance since credentials may have changed
        MediaProviderFactory.clearCache(id)
    }

    /**
     * Delete a provider and clean up its encrypted prefs and cache.
     */
    suspend fun deleteProvider(id: Long) {
        val entity = dao.getProviderById(id) ?: return
        dao.deleteProvider(entity)
        clearProviderPassword(id)
        clearProviderCache(id)
        settingsCache.remove(id)
        // Clear cached provider instance
        MediaProviderFactory.clearCache(id)
    }

    /**
     * Set a provider as active (deactivates all others).
     */
    suspend fun setActiveProvider(id: Long) {
        dao.deactivateAll()
        dao.activateProvider(id)
        // Clear all cached providers to ensure fresh session on provider switch
        MediaProviderFactory.clearAllCaches()
    }

    /**
     * Update sync statistics for a provider.
     */
    suspend fun updateSyncStats(
        id: Long,
        timestamp: Long,
        durationMs: Long,
        error: String?,
    ) {
        dao.updateSyncStats(id, timestamp, durationMs, error)
    }

    /**
     * Get the stored password for a provider.
     */
    fun getPassword(providerId: Long): String? = getProviderPrefs(providerId).getString("password", null)

    /**
     * Persist a Jellyfin session token (from Quick Connect or normal auth) so the
     * provider can restore it on next launch without re-authenticating.
     */
    fun saveJellyfinSession(
        providerId: Long,
        token: String,
        userId: String,
    ) {
        getProviderPrefs(providerId).edit {
            putString("jellyfin_token", token)
                .putString("jellyfin_user_id", userId)
        }
    }

    // --- Provider Settings ---

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Get the settings for a provider.
     * Returns default settings if provider not found or settings are invalid.
     */
    suspend fun getProviderSettings(providerId: Long): ProviderSettings {
        settingsCache[providerId]?.let { return it }
        val entity = dao.getProviderById(providerId) ?: return ProviderSettings.DEFAULT
        val settings = parseProviderSettings(entity.providerSettings)
        settingsCache[providerId] = settings
        return settings
    }

    /**
     * Get provider settings synchronously (for use in non-suspend contexts).
     * Note: This performs a blocking database call on cache miss - use getProviderSettings() when possible.
     */
    fun getProviderSettingsSync(providerId: Long): ProviderSettings {
        settingsCache[providerId]?.let { return it }
        // ⚡ Bolt: Use synchronous DAO method instead of runBlocking to avoid coroutine overhead and thread blocking
        val entity = dao.getProviderByIdSync(providerId) ?: return ProviderSettings.DEFAULT
        val settings = parseProviderSettings(entity.providerSettings)
        settingsCache[providerId] = settings
        return settings
    }

    /**
     * Update the settings for a provider.
     */
    suspend fun updateProviderSettings(
        providerId: Long,
        settings: ProviderSettings,
    ) {
        val entity = dao.getProviderById(providerId) ?: return
        val settingsJson = json.encodeToString(settings)
        dao.updateProvider(entity.copy(providerSettings = settingsJson))
        settingsCache[providerId] = settings
        // Clear cached provider so it picks up new settings
        MediaProviderFactory.clearCache(providerId)

        // Recompute category-filter exclusion flags immediately, purely locally (no network) —
        // lets a filter change take effect right away instead of waiting for the next sync.
        if (entity.type == "XTREAM") {
            val database = org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase.getInstance(context)
            org.njarasoa.fijerena.core.network.xtream.manager.XtreamCategoryExclusionSync.recompute(
                database.categoryDao(),
                database.streamDao(),
                database.seriesDao(),
                providerId,
                settings.categoryFilters,
            )
        }
    }

    /**
     * Parse provider settings from JSON string.
     * Returns default settings if parsing fails.
     */
    private fun parseProviderSettings(settingsJson: String): ProviderSettings {
        if (settingsJson.isBlank() || settingsJson == "{}") return ProviderSettings.DEFAULT
        return try {
            json.decodeFromString<ProviderSettings>(settingsJson)
        } catch (_: Exception) {
            ProviderSettings.DEFAULT
        }
    }

    // --- Cache management ---

    suspend fun getCacheStatsForProvider(providerId: Long): XtreamRepository.CacheStats {
        // We need an instance of XtreamRepository to get accurate DB stats.
        // Since we don't have dependency injection here, we create a temporary instance.
        // This is safe because XtreamRepository uses singletons (Database) internally.
        val accountManager =
            org.njarasoa.fijerena.core.network
                .AccountManager(context)
        val repo = XtreamRepository(accountManager, context, providerId)
        return repo.getCacheStats()
    }

    suspend fun clearAllCacheForProvider(providerId: Long) {
        val accountManager =
            org.njarasoa.fijerena.core.network
                .AccountManager(context)
        val repo = XtreamRepository(accountManager, context, providerId)
        repo.clearCache()
    }

    suspend fun clearCacheForProviderContentType(
        providerId: Long,
        contentType: String,
    ) {
        val accountManager =
            org.njarasoa.fijerena.core.network
                .AccountManager(context)
        val repo = XtreamRepository(accountManager, context, providerId)
        repo.clearCacheForContentType(contentType)
    }

    // --- Private helpers ---

    private fun savePassword(
        providerId: Long,
        password: String,
    ) {
        getProviderPrefs(providerId).edit {
            putString("password", password)
        }
    }

    private fun clearProviderPassword(providerId: Long) {
        try {
            getProviderPrefs(providerId).edit { clear() }
            encryptedPrefsCache.remove(providerId)
        } catch (_: Exception) {
            // Ignore errors clearing prefs for deleted provider
        }
    }

    private fun clearProviderCache(providerId: Long) {
        try {
            val cacheName = "xtream_cache_$providerId"
            context
                .getSharedPreferences(cacheName, Context.MODE_PRIVATE)
                .edit { clear() }
        } catch (_: Exception) {
            // Ignore errors clearing cache for deleted provider
        }
    }

    private fun getProviderPrefs(providerId: Long): android.content.SharedPreferences =
        encryptedPrefsCache.getOrPut(providerId) {
            EncryptedSharedPreferences.create(
                context,
                "provider_creds_$providerId",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
}
