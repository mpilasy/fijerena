@file:Suppress("DEPRECATION")

package org.njarasoa.fijerena.core.network.provider
import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/** Result of [ProviderRepository.pruneOrphanedCatalogData]. */
data class OrphanedDataPruneResult(
    val rowsRemoved: Long,
    val bytesReclaimed: Long,
)

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

    // Jetpack Security Crypto is deprecated as of its first stable release (1.1.0). Still the
    // credential store; replacing it is tracked in docs/plans/20260828_secret-store-migration-plan.md.
    @Suppress("DEPRECATION")
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
     * Deactivates all other providers and activates this one, unless [activate] is false — used
     * by [ProviderCopyManager.duplicateProvider] so cloning a provider doesn't steal "active" away
     * from whatever the user currently has selected.
     */
    suspend fun addProvider(
        name: String,
        url: String,
        username: String,
        password: String,
        type: String = "XTREAM",
        config: String = "",
        initialSettings: ProviderSettings = ProviderSettings.DEFAULT,
        activate: Boolean = true,
    ): Long {
        if (activate) dao.deactivateAll()
        val settingsJson = json.encodeToString(initialSettings)
        val entity =
            ProviderEntity(
                name = name,
                url = url,
                username = username,
                type = type,
                config = config,
                providerSettings = settingsJson,
                isActive = activate,
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
        val entity = dao.getProviderById(id)
        if (entity != null) {
            dao.deleteProvider(entity)
            deleteProviderEpgSources(id)
            clearProviderPassword(id)
            clearProviderCache(id)
            clearProviderWatchState(id)
            clearProviderCatalog(id)
            settingsCache.remove(id)
            // Clear cached provider instance
            MediaProviderFactory.clearCache(id)
        }
    }

    /**
     * `xtream_v2.db`'s catalog rows (streams, series, episodes, categories, favorites, the
     * per-stream EPG payload cache) are keyed by `providerId` but live in the same app-wide
     * database file as every other provider's rows, so no SQL cascade reaches them from
     * `providers.db` — same reasoning as [clearProviderWatchState], delete by hand. Before this,
     * [clearProviderCache] only ever cleared a small SharedPreferences blob, never these Room
     * tables — hundreds of thousands of orphaned rows could accumulate indefinitely across
     * provider deletions, and SQLite does not shrink `xtream_v2.db` back down on its own even once
     * the rows are gone (hence the `VACUUM` below).
     */
    private suspend fun clearProviderCatalog(providerId: Long) {
        // Every DAO call here is a plain blocking Room method, not `suspend` — XtreamDatabase's
        // builder never calls allowMainThreadQueries(), so without this withContext they would
        // run on whatever dispatcher the caller happens to be on (ProviderViewModel.deleteProvider
        // calls in from a bare viewModelScope.launch { }, i.e. Main) and Room would throw.
        withContext(Dispatchers.IO) {
            val db = XtreamDatabase.getInstance(context)
            db.streamDao().deleteAllForProvider(providerId)
            db.seriesDao().deleteAll(providerId)
            db.episodeDao().deleteAll(providerId)
            db.categoryDao().deleteAllForProvider(providerId)
            db.favoriteStateDao().deleteAll(providerId)
            db.epgCacheDao().deleteAll(providerId)
            try {
                val sdb = db.openHelper.writableDatabase
                sdb.execSQL("VACUUM")
                sdb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            } catch (e: Exception) {
                // VACUUM needs the connection free of any other open transaction/statement; if
                // one is mid-flight this just skips reclaiming disk space this time around — the
                // rows themselves are already deleted regardless, which is the correctness-
                // critical part; VACUUM only recovers the now-unused disk space.
                android.util.Log.w("ProviderRepository", "VACUUM after provider delete failed", e)
            }
        }
    }

    /**
     * Sweeps `xtream_v2.db` for rows whose `providerId` doesn't match any provider that exists
     * right now, covering both drift from before [clearProviderCatalog] started running on every
     * deletion, and any other divergence this class doesn't already know to clean up on its own.
     * Guarded so it never runs if providers cannot be loaded, protecting good data.
     */
    suspend fun pruneOrphanedCatalogData(forceVacuum: Boolean = false): OrphanedDataPruneResult {
        val result =
            withContext(Dispatchers.IO) {
                val startMs = System.currentTimeMillis()
                val validProviderIds = dao.getAllProvidersList().map { it.id }
                if (validProviderIds.isEmpty()) {
                    OrphanedDataPruneResult(0L, 0L)
                } else {
                    val db = XtreamDatabase.getInstance(context)
                    val dbFile = context.getDatabasePath("xtream_v2.db")
                    val walFile = context.getDatabasePath("xtream_v2.db-wal")
                    val sizeBeforeBytes =
                        (if (dbFile.exists()) dbFile.length() else 0L) + (if (walFile.exists()) walFile.length() else 0L)

                    val rowsRemoved =
                        db.streamDao().deleteOrphaned(validProviderIds) +
                            db.seriesDao().deleteOrphaned(validProviderIds) +
                            db.episodeDao().deleteOrphaned(validProviderIds) +
                            db.categoryDao().deleteOrphaned(validProviderIds) +
                            db.favoriteStateDao().deleteOrphaned(validProviderIds) +
                            db.epgCacheDao().deleteOrphaned(validProviderIds) +
                            db.watchStateDao().deleteOrphaned(validProviderIds)

                    cleanupOrphanedPrefs(validProviderIds.toSet())

                    val sourceDao = this@ProviderRepository.db.epgSourceDao()
                    val allSourceProviderIds = sourceDao.getAllSourcesOnce().map { it.providerId }.toSet()
                    val orphanSourceProviderIds = allSourceProviderIds - validProviderIds.toSet()
                    for (orphanId in orphanSourceProviderIds) {
                        deleteProviderEpgSources(orphanId)
                    }

                    if (rowsRemoved > 0 || forceVacuum) {
                        try {
                            val sdb = db.openHelper.writableDatabase
                            sdb.execSQL("VACUUM")
                            sdb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
                        } catch (e: Exception) {
                            android.util.Log.w("ProviderRepository", "VACUUM during orphan prune failed", e)
                        }
                    }

                    val sizeAfterBytes =
                        (if (dbFile.exists()) dbFile.length() else 0L) + (if (walFile.exists()) walFile.length() else 0L)
                    val bytesReclaimed = (sizeBeforeBytes - sizeAfterBytes).coerceAtLeast(0L)
                    val durationMs = System.currentTimeMillis() - startMs
                    AppSettings(context).saveShrinkStats(durationMs, rowsRemoved.toLong(), bytesReclaimed)
                    OrphanedDataPruneResult(rowsRemoved.toLong(), bytesReclaimed)
                }
            }
        return result
    }

    private fun cleanupOrphanedPrefs(validProviderIds: Set<Long>) {
        try {
            val prefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
            if (prefsDir.exists() && prefsDir.isDirectory) {
                val files = prefsDir.listFiles() ?: emptyArray()
                val prefixPatterns = listOf("provider_creds_", "media_cache_", "xtream_cache_")
                for (file in files) {
                    val name = file.name
                    for (prefix in prefixPatterns) {
                        if (name.startsWith(prefix) && name.endsWith(".xml")) {
                            val idStr = name.removePrefix(prefix).removeSuffix(".xml")
                            val id = idStr.toLongOrNull()
                            if (id != null && id !in validProviderIds) {
                                file.delete()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ProviderRepository", "Failed cleaning up orphaned prefs", e)
        }
    }

    /**
     * EPG sources belong to a single provider, and their indexed channels/programmes live in a
     * separate database ([EpgIndexDatabase]), so no SQL cascade is possible - delete both by hand.
     */
    private suspend fun deleteProviderEpgSources(id: Long) {
        val sourceDao = db.epgSourceDao()
        val sourceIds = sourceDao.getSourceIdsForProvider(id)
        if (sourceIds.isNotEmpty()) {
            EpgIndexDatabase.getInstance(context).epgIndexDao().deleteBySourceIds(sourceIds)
        }
        sourceDao.deleteSourcesForProvider(id)
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
        inserted: Int? = null,
        updated: Int? = null,
        deleted: Int? = null,
    ) {
        dao.updateSyncStats(id, timestamp, durationMs, error, inserted, updated, deleted)
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
        // Runs on IO: the DAO calls inside recompute() are synchronous, non-suspend Room queries.
        if (entity.type == "XTREAM") {
            withContext(Dispatchers.IO) {
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

    /**
     * One-time rewrite of any stored provider settings still using the legacy
     * `categoryFilters.prefixes: List<String>` shape into the current
     * `categoryFilters.rules: List<CategoryMatcher>` shape. [CategoryMatcherSerializer] already
     * tolerates reading the old shape indefinitely, so this isn't required for correctness — it
     * just ensures the stored bytes actually reflect the new format instead of relying on the
     * lenient decoder forever. Safe to call on every app start: a no-op once migrated.
     */
    suspend fun migrateLegacyCategoryFilterPrefixes() {
        dao.getAllProvidersList().forEach { entity ->
            if (!entity.providerSettings.contains("\"prefixes\"")) return@forEach
            val settings = parseProviderSettings(entity.providerSettings)
            dao.updateProvider(entity.copy(providerSettings = json.encodeToString(settings)))
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

    /**
     * `watch_state` lives in `XtreamDatabase` (one app-wide file) while provider definitions live
     * in `SettingsDatabase`, so no SQL cascade reaches it — delete by hand. Not Xtream-specific:
     * `MediaRepository` backs SMB, Local and Remote M3U too, and all of them write rows keyed by
     * this `providerId`. `media_cache_$providerId` (favorites, favorite categories) is cleared in
     * the same pass — `deleteProvider` never touched it before, leaking that prefs file on every
     * deletion; bounded while history was capped at 25 rows, no longer bounded once storage is.
     * See docs/plans/20260828_watch-state-durable-storage-plan.md.
     */
    private suspend fun clearProviderWatchState(providerId: Long) {
        XtreamDatabase.getInstance(context).watchStateDao().deleteAll(providerId)
        try {
            context
                .getSharedPreferences("media_cache_$providerId", Context.MODE_PRIVATE)
                .edit { clear() }
        } catch (_: Exception) {
            // Ignore errors clearing cache for deleted provider
        }
    }

    @Suppress("DEPRECATION")
    private fun getProviderPrefs(providerId: Long): android.content.SharedPreferences =
        encryptedPrefsCache.computeIfAbsent(providerId) { id ->
            val fileName = "provider_creds_$id"
            val prefs =
                try {
                    EncryptedSharedPreferences.create(
                        context,
                        fileName,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                    )
                } catch (_: Exception) {
                    context.deleteSharedPreferences(fileName)
                    try {
                        EncryptedSharedPreferences.create(
                            context,
                            fileName,
                            masterKey,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                        )
                    } catch (_: Exception) {
                        context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
                    }
                }
            prefs
        }
}
