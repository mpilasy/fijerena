package org.njarasoa.fijerena.core.network
import android.content.Context
import org.njarasoa.fijerena.core.network.R
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase

/**
 * Manages export and import of all application settings to/from a JSON file.
 *
 * Exported data:
 * - Global AppSettings (theme, UI scale, dev mode, EPG auto-refresh, etc.)
 * - Provider configurations (name, URL, username, type, config, per-provider settings)
 * - EPG sources (URL, label, timezone offset, enabled state)
 * - Favorites per provider (item ID, name, category, content type)
 *
 * NOT exported (for security/size reasons):
 * - Passwords (stored in EncryptedSharedPreferences)
 * - Cache data
 * - EPG programme data (re-downloaded on import)
 * - Timestamps (createdAt, lastUsedAt, lastIngestedAt)
 */
class SettingsExportManager(
    private val context: Context,
) {
    companion object {
        private const val TAG = "SettingsExportManager"
        private const val EXPORT_VERSION = 3
        private const val KEY_FAVORITES = "favorites_v2"
        private const val KEY_FAVORITE_CATEGORIES = "favorite_categories"
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

    @Serializable
    data class ExportedSettings(
        val version: Int = EXPORT_VERSION,
        val exportedAt: Long = System.currentTimeMillis(),
        val global: GlobalSettings = GlobalSettings(),
        val providers: List<ExportedProvider> = emptyList(),
        val epgSources: List<ExportedEpgSource> = emptyList(),
        val providerFavorites: List<ProviderFavorites> = emptyList(),
        val providerFavoriteCategories: List<ProviderFavoriteCategories> = emptyList(),
    )

    @Serializable
    data class GlobalSettings(
        val themeId: String = "deep_night",
        val uiScale: Float = 1.0f,
        val isDevMode: Boolean = false,
        val epgAutoRefreshEnabled: Boolean = true,
        val cellularLiveMultiplier: Float = 1.0f,
        val cellularVodMultiplier: Float = 1.0f,
    )

    @Serializable
    data class ExportedProvider(
        val name: String,
        val url: String,
        val username: String,
        val type: String,
        val config: String = "",
        val providerSettings: String = "{}",
        val isActive: Boolean = false,
    )

    @Serializable
    data class ExportedEpgSource(
        val url: String,
        val label: String = "",
        val timezoneOffsetHours: Int = 0,
        val enabled: Boolean = true,
    )

    @Serializable
    data class ProviderFavorites(
        val providerName: String,
        val providerUrl: String,
        val favorites: List<ExportedFavorite> = emptyList(),
    )

    @Serializable
    data class ExportedFavorite(
        val itemId: String,
        val itemName: String,
        val categoryId: String,
        val contentType: String,
    )

    @Serializable
    data class ProviderFavoriteCategories(
        val providerName: String,
        val providerUrl: String,
        val favoriteCategories: List<ExportedFavoriteCategory> = emptyList(),
    )

    @Serializable
    data class ExportedFavoriteCategory(
        val categoryId: String,
        val categoryName: String,
        val contentType: String,
    )

    /**
     * Export all application settings to a JSON string.
     */
    data class ImportOptions(
        val importProviders: Boolean = true,
        val importEpgSources: Boolean = true,
        val importGlobalSettings: Boolean = true,
        val importFavorites: Boolean = true,
    )

    enum class ConflictResolution {
        OVERWRITE,
        DUPLICATE,
        SKIP,
    }

    /**
     * Finds a candidate for "Quick Import" by checking the standard locations.
     * @return The absolute path to a readable fijerena_settings.json, or null if not found.
     */
    fun getQuickImportPath(): String? {
        val fileName = "fijerena_settings.json"

        // 1. Try public Download folder (primary choice for TV/easy access)
        val downloadPath = "/sdcard/Download/$fileName"
        val downloadFile = java.io.File(downloadPath)
        if (downloadFile.exists() && downloadFile.canRead()) {
            return downloadPath
        }

        // 2. Fallback to app private folder (no permission needed, reliable on all versions)
        val privateFile = java.io.File(context.getExternalFilesDir(null), fileName)
        if (privateFile.exists() && privateFile.canRead()) {
            return privateFile.absolutePath
        }

        return null
    }

    /**
     * Parsed import data with conflict information.
     */
    data class ParsedImport(
        val settings: ExportedSettings,
        val jsonString: String,
        val conflictingProviders: List<String>,
    ) {
        val hasConflicts: Boolean get() = conflictingProviders.isNotEmpty()
        val hasProviders: Boolean get() = settings.providers.isNotEmpty()
        val hasEpgSources: Boolean get() = settings.epgSources.isNotEmpty()
        val hasFavorites: Boolean get() =
            settings.providerFavorites.isNotEmpty() ||
                settings.providerFavoriteCategories.isNotEmpty()
    }

    /**
     * Export all settings to a JSON string.
     */
    suspend fun exportToJson(): String =
        withContext(Dispatchers.IO) {
            val appSettings = AppSettings(context)
            val providerRepo = ProviderRepository(context)
            val settingsDb = SettingsDatabase.getInstance(context)

            val global =
                GlobalSettings(
                    themeId = appSettings.themeId,
                    uiScale = appSettings.uiScale,
                    isDevMode = appSettings.isDevMode,
                    epgAutoRefreshEnabled = appSettings.epgAutoRefreshEnabled,
                    cellularLiveMultiplier = appSettings.cellularLiveMultiplier,
                    cellularVodMultiplier = appSettings.cellularVodMultiplier,
                )

            val allProviders = providerRepo.getAllProvidersList()

            val providers =
                allProviders.map { entity ->
                    ExportedProvider(
                        name = entity.name,
                        url = entity.url,
                        username = entity.username,
                        type = entity.type,
                        config = entity.config,
                        providerSettings = entity.providerSettings,
                        isActive = entity.isActive,
                    )
                }

            val epgSources =
                settingsDb.epgSourceDao().getAllSourcesOnce().map { source ->
                    ExportedEpgSource(
                        url = source.url,
                        label = source.label,
                        timezoneOffsetHours = source.timezoneOffsetHours,
                        enabled = source.enabled,
                    )
                }

            // Export favorites per provider
            val providerFavorites =
                allProviders.mapNotNull { entity ->
                    val cachePrefs =
                        context.getSharedPreferences(
                            "media_cache_${entity.id}",
                            Context.MODE_PRIVATE,
                        )
                    val favJson = cachePrefs.getString(KEY_FAVORITES, null) ?: return@mapNotNull null
                    val favorites =
                        try {
                            json.decodeFromString<List<FavoriteItem>>(favJson)
                        } catch (e: Exception) {
                            return@mapNotNull null
                        }
                    if (favorites.isEmpty()) return@mapNotNull null
                    ProviderFavorites(
                        providerName = entity.name,
                        providerUrl = entity.url,
                        favorites =
                            favorites.map { fav ->
                                ExportedFavorite(
                                    itemId = fav.itemId,
                                    itemName = fav.itemName,
                                    categoryId = fav.categoryId,
                                    contentType = fav.contentType,
                                )
                            },
                    )
                }

            // Export favorite categories per provider
            val providerFavoriteCategories =
                allProviders.mapNotNull { entity ->
                    val cachePrefs =
                        context.getSharedPreferences(
                            "media_cache_${entity.id}",
                            Context.MODE_PRIVATE,
                        )
                    val favCatJson = cachePrefs.getString(KEY_FAVORITE_CATEGORIES, null) ?: return@mapNotNull null
                    val favCats =
                        try {
                            json.decodeFromString<List<FavoriteCategoryItem>>(favCatJson)
                        } catch (e: Exception) {
                            return@mapNotNull null
                        }
                    if (favCats.isEmpty()) return@mapNotNull null
                    ProviderFavoriteCategories(
                        providerName = entity.name,
                        providerUrl = entity.url,
                        favoriteCategories =
                            favCats.map { fav ->
                                ExportedFavoriteCategory(
                                    categoryId = fav.categoryId,
                                    categoryName = fav.categoryName,
                                    contentType = fav.contentType,
                                )
                            },
                    )
                }

            val exported =
                ExportedSettings(
                    global = global,
                    providers = providers,
                    epgSources = epgSources,
                    providerFavorites = providerFavorites,
                    providerFavoriteCategories = providerFavoriteCategories,
                )

            json.encodeToString(exported)
        }

    /**
     * Export settings to a URI (file) via the Storage Access Framework.
     * @return true if export succeeded
     */
    suspend fun exportToUri(uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val jsonString = exportToJson()
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(jsonString.toByteArray(Charsets.UTF_8))
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                false
            }
        }

    /**
     * Parse an import file from a direct file path and detect provider conflicts.
     */
    suspend fun parseImportPath(path: String): kotlin.Result<ParsedImport> =
        withContext(Dispatchers.IO) {
            try {
                val file = java.io.File(path)
                if (!file.exists()) return@withContext kotlin.Result.failure(Exception("File does not exist: $path"))
                if (!file.canRead()) return@withContext kotlin.Result.failure(Exception("Permission denied: Cannot read $path"))

                val jsonString = file.readText(Charsets.UTF_8)
                val exported = json.decodeFromString<ExportedSettings>(jsonString)
                val providerRepo = ProviderRepository(context)
                val existingNames = providerRepo.getAllProvidersList().map { it.name }.toSet()

                val conflicts =
                    exported.providers
                        .filter { ep -> ep.name in existingNames }
                        .map { it.name }

                kotlin.Result.success(ParsedImport(exported, jsonString, conflicts))
            } catch (e: kotlinx.serialization.SerializationException) {
                kotlin.Result.failure(Exception("Invalid settings file format"))
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }

    /**
     * Parse an import file and detect provider conflicts.
     * Call this before importFromJson() to check if user confirmation is needed.
     */
    suspend fun parseImportUri(uri: Uri): kotlin.Result<ParsedImport> =
        withContext(Dispatchers.IO) {
            try {
                val jsonString =
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    } ?: return@withContext kotlin.Result.failure(Exception("Could not read file"))

                val exported = json.decodeFromString<ExportedSettings>(jsonString)
                val providerRepo = ProviderRepository(context)
                val existingNames = providerRepo.getAllProvidersList().map { it.name }.toSet()

                val conflicts =
                    exported.providers
                        .filter { ep -> ep.name in existingNames }
                        .map { it.name }

                kotlin.Result.success(ParsedImport(exported, jsonString, conflicts))
            } catch (e: kotlinx.serialization.SerializationException) {
                kotlin.Result.failure(Exception("Invalid settings file format"))
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }

    /**
     * Import settings from a URI (file) via the Storage Access Framework.
     * @return A summary of what was imported, or null on error.
     */
    suspend fun importFromUri(
        uri: Uri,
        conflictResolution: ConflictResolution = ConflictResolution.SKIP,
        options: ImportOptions = ImportOptions(),
    ): ImportResult =
        withContext(Dispatchers.IO) {
            try {
                val jsonString =
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    } ?: return@withContext ImportResult(error = context.getString(R.string.settings_export_error_read_file))

                importFromJson(jsonString, conflictResolution, options)
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                ImportResult(error = e.message ?: context.getString(R.string.settings_export_error_import_failed))
            }
        }

    /**
     * Import from a pre-parsed import (avoids re-reading the file).
     */
    suspend fun importFromParsed(
        parsed: ParsedImport,
        conflictResolution: ConflictResolution = ConflictResolution.SKIP,
        options: ImportOptions = ImportOptions(),
    ): ImportResult = importFromJson(parsed.jsonString, conflictResolution, options)

    /**
     * Import settings from a JSON string.
     */
    suspend fun importFromJson(
        jsonString: String,
        conflictResolution: ConflictResolution = ConflictResolution.SKIP,
        options: ImportOptions = ImportOptions(),
    ): ImportResult =
        withContext(Dispatchers.IO) {
            try {
                val exported = json.decodeFromString<ExportedSettings>(jsonString)

                // Apply global settings
                if (options.importGlobalSettings) {
                    val appSettings = AppSettings(context)
                    appSettings.themeId = exported.global.themeId
                    appSettings.uiScale = exported.global.uiScale
                    appSettings.isDevMode = exported.global.isDevMode
                    appSettings.epgAutoRefreshEnabled = exported.global.epgAutoRefreshEnabled
                    appSettings.cellularLiveMultiplier = exported.global.cellularLiveMultiplier
                    appSettings.cellularVodMultiplier = exported.global.cellularVodMultiplier
                }

                // Import providers
                val providerRepo = ProviderRepository(context)
                val existingProviders = providerRepo.getAllProvidersList()
                val existingByName = existingProviders.associateBy { it.name }
                var providersAdded = 0
                var providersUpdated = 0
                var providersSkipped = 0

                if (options.importProviders) {
                    for (ep in exported.providers) {
                        val existing = existingByName[ep.name]
                        if (existing != null) {
                            when (conflictResolution) {
                                ConflictResolution.SKIP -> {
                                    providersSkipped++
                                }
                                ConflictResolution.OVERWRITE -> {
                                    // Update existing provider's URL, username, type, config, settings
                                    providerRepo.updateProvider(
                                        id = existing.id,
                                        name = ep.name,
                                        url = ep.url,
                                        username = ep.username,
                                        password = "", // Passwords not exported
                                        type = ep.type,
                                        config = ep.config,
                                    )
                                    if (ep.providerSettings != "{}") {
                                        try {
                                            val settings =
                                                json.decodeFromString<org.njarasoa.fijerena.core.network.provider.ProviderSettings>(
                                                    ep.providerSettings,
                                                )
                                            providerRepo.updateProviderSettings(existing.id, settings)
                                        } catch (e: Exception) {
                                            android.util.Log.e("SettingsExportManager", "Failed to update provider settings", e)
                                        }
                                    }
                                    providersUpdated++
                                }
                                ConflictResolution.DUPLICATE -> {
                                    // Add as new provider with "(imported)" suffix
                                    addNewProvider(providerRepo, ep.copy(name = "${ep.name} (imported)"))
                                    providersAdded++
                                }
                            }
                            continue
                        }
                        addNewProvider(providerRepo, ep)
                        providersAdded++
                    }

                    // If we had an active provider in the export and none is currently active, activate the matching one
                    val activeExport = exported.providers.find { it.isActive }
                    if (activeExport != null) {
                        val currentActive = providerRepo.getActiveProvider()
                        if (currentActive == null) {
                            // Re-fetch only if providers were actually added/updated
                            val updatedProviders =
                                if (providersAdded > 0 || providersUpdated > 0) {
                                    providerRepo.getAllProvidersList()
                                } else {
                                    existingProviders
                                }
                            val updatedByNameUrl = updatedProviders.associateBy { "${it.name}\u0000${it.url}" }
                            val updatedByName = updatedProviders.associateBy { it.name }
                            val matching =
                                updatedByNameUrl["${activeExport.name}\u0000${activeExport.url}"]
                                    ?: updatedByName["${activeExport.name} (imported)"]
                            if (matching != null) {
                                providerRepo.setActiveProvider(matching.id)
                            }
                        }
                    }
                }

                // Import EPG sources (merge: add new, skip existing by URL)
                var sourcesAdded = 0
                var sourcesSkipped = 0

                if (options.importEpgSources) {
                    val settingsDb = SettingsDatabase.getInstance(context)
                    val sourceDao = settingsDb.epgSourceDao()
                    val existingSources = sourceDao.getAllSourcesOnce()
                    val existingUrls = existingSources.map { it.url }.toSet()

                    for (es in exported.epgSources) {
                        val exists = es.url in existingUrls
                        if (exists) {
                            sourcesSkipped++
                            continue
                        }
                        sourceDao.insertSource(
                            EpgSourceEntity(
                                url = es.url,
                                label = es.label,
                                timezoneOffsetHours = es.timezoneOffsetHours,
                                enabled = es.enabled,
                            ),
                        )
                        sourcesAdded++
                    }
                }

                // Import favorites per provider (match by name + URL)
                var favoritesRestored = 0

                // Re-fetch providers once for all favorites sections (may have new providers from import)
                val allProvidersForFavorites =
                    if (providersAdded > 0 || providersUpdated > 0) {
                        providerRepo.getAllProvidersList()
                    } else {
                        existingProviders
                    }
                val providersByNameUrl = allProvidersForFavorites.associateBy { "${it.name}\u0000${it.url}" }
                val providersByName = allProvidersForFavorites.associateBy { it.name }

                if (options.importFavorites && exported.providerFavorites.isNotEmpty()) {
                    for (pf in exported.providerFavorites) {
                        val matchingProvider =
                            providersByNameUrl["${pf.providerName}\u0000${pf.providerUrl}"]
                                ?: providersByName[pf.providerName]
                                ?: continue
                        val cachePrefs =
                            context.getSharedPreferences(
                                "media_cache_${matchingProvider.id}",
                                Context.MODE_PRIVATE,
                            )
                        // Merge with existing favorites (don't overwrite)
                        val existingJson = cachePrefs.getString(KEY_FAVORITES, null)
                        val existingFavorites =
                            if (existingJson != null) {
                                try {
                                    json.decodeFromString<List<FavoriteItem>>(existingJson)
                                } catch (e: Exception) {
                                    emptyList()
                                }
                            } else {
                                emptyList()
                            }
                        val existingKeys = existingFavorites.mapTo(HashSet()) { Pair(it.itemId, it.contentType) }
                        val newFavorites =
                            pf.favorites
                                .filter { Pair(it.itemId, it.contentType) !in existingKeys }
                                .map { fav ->
                                    FavoriteItem(
                                        itemId = fav.itemId,
                                        itemName = fav.itemName,
                                        categoryId = fav.categoryId,
                                        contentType = fav.contentType,
                                    )
                                }
                        if (newFavorites.isNotEmpty()) {
                            val merged = existingFavorites + newFavorites
                            cachePrefs.edit { putString(KEY_FAVORITES, json.encodeToString(merged)) }
                            favoritesRestored += newFavorites.size
                        }
                    }
                }

                // Import favorite categories per provider
                var favoriteCategoriesRestored = 0

                if (options.importFavorites && exported.providerFavoriteCategories.isNotEmpty()) {
                    for (pfc in exported.providerFavoriteCategories) {
                        val matchingProvider =
                            providersByNameUrl["${pfc.providerName}\u0000${pfc.providerUrl}"]
                                ?: providersByName[pfc.providerName]
                                ?: continue
                        val cachePrefs =
                            context.getSharedPreferences(
                                "media_cache_${matchingProvider.id}",
                                Context.MODE_PRIVATE,
                            )
                        val existingJson = cachePrefs.getString(KEY_FAVORITE_CATEGORIES, null)
                        val existingFavCats =
                            if (existingJson != null) {
                                try {
                                    json.decodeFromString<List<FavoriteCategoryItem>>(existingJson)
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            } else {
                                emptyList()
                            }
                        val existingKeys = existingFavCats.mapTo(HashSet()) { Pair(it.categoryId, it.contentType) }
                        val newFavCats =
                            pfc.favoriteCategories
                                .filter { Pair(it.categoryId, it.contentType) !in existingKeys }
                                .map { fav ->
                                    FavoriteCategoryItem(
                                        categoryId = fav.categoryId,
                                        categoryName = fav.categoryName,
                                        contentType = fav.contentType,
                                    )
                                }
                        if (newFavCats.isNotEmpty()) {
                            val merged = existingFavCats + newFavCats
                            cachePrefs.edit { putString(KEY_FAVORITE_CATEGORIES, json.encodeToString(merged)) }
                            favoriteCategoriesRestored += newFavCats.size
                        }
                    }
                }

                // No-op (Log.d removed)
                ImportResult(
                    providersAdded = providersAdded,
                    providersUpdated = providersUpdated,
                    providersSkipped = providersSkipped,
                    epgSourcesAdded = sourcesAdded,
                    epgSourcesSkipped = sourcesSkipped,
                    favoritesRestored = favoritesRestored,
                    favoriteCategoriesRestored = favoriteCategoriesRestored,
                )
            } catch (e: kotlinx.serialization.SerializationException) {
                Log.e(TAG, "Invalid settings file format", e)
                ImportResult(error = context.getString(R.string.settings_export_error_invalid_format))
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                ImportResult(error = e.message ?: context.getString(R.string.settings_export_error_import_failed))
            }
        }

    private suspend fun addNewProvider(
        providerRepo: ProviderRepository,
        ep: ExportedProvider,
    ) {
        val newId =
            providerRepo.addProvider(
                name = ep.name,
                url = ep.url,
                username = ep.username,
                password = "",
                type = ep.type,
                config = ep.config,
            )
        if (ep.providerSettings != "{}") {
            try {
                val settings = json.decodeFromString<org.njarasoa.fijerena.core.network.provider.ProviderSettings>(ep.providerSettings)
                providerRepo.updateProviderSettings(newId, settings)
            } catch (e: Exception) {
                android.util.Log.e("SettingsExportManager", "Failed to update provider settings", e)
            }
        }
    }

    data class ImportResult(
        val providersAdded: Int = 0,
        val providersUpdated: Int = 0,
        val providersSkipped: Int = 0,
        val epgSourcesAdded: Int = 0,
        val epgSourcesSkipped: Int = 0,
        val favoritesRestored: Int = 0,
        val favoriteCategoriesRestored: Int = 0,
        val error: String? = null,
    ) {
        val isSuccess: Boolean get() = error == null

        fun toSummary(context: Context): String {
            if (error != null) return context.getString(R.string.settings_export_summary_import_failed_format, error)
            val parts = mutableListOf<String>()

            // Providers
            if (providersAdded > 0 || providersUpdated > 0 || providersSkipped > 0) {
                val p = mutableListOf<String>()
                if (providersAdded > 0) p.add(context.getString(R.string.settings_export_item_added_format, providersAdded))
                if (providersUpdated > 0) p.add(context.getString(R.string.settings_export_item_overwritten_format, providersUpdated))
                if (providersSkipped > 0) p.add(context.getString(R.string.settings_export_item_skipped_format, providersSkipped))
                parts.add(context.getString(R.string.settings_export_summary_providers_format, p.joinToString(", ")))
            }

            // EPG Sources
            if (epgSourcesAdded > 0 || epgSourcesSkipped > 0) {
                val e = mutableListOf<String>()
                if (epgSourcesAdded > 0) e.add(context.getString(R.string.settings_export_item_added_format, epgSourcesAdded))
                if (epgSourcesSkipped > 0) e.add(context.getString(R.string.settings_export_item_skipped_format, epgSourcesSkipped))
                parts.add(context.getString(R.string.settings_export_summary_epg_sources_format, e.joinToString(", ")))
            }

            // Favorites
            val favTotal = favoritesRestored + favoriteCategoriesRestored
            if (favTotal > 0) {
                parts.add(context.getString(R.string.settings_export_summary_favorites_format, favTotal))
            }

            if (parts.isEmpty()) parts.add(context.getString(R.string.settings_export_summary_updated))
            val summary = parts.joinToString(". ") + "."
            return if (providersAdded > 0) context.getString(R.string.settings_export_summary_passwords_reentry_format, summary) else summary
        }
    }
}
