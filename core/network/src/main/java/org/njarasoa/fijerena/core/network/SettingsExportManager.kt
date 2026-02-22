package org.njarasoa.fijerena.core.network

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSourceEntity

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
class SettingsExportManager(private val context: Context) {

    companion object {
        private const val TAG = "SettingsExportManager"
        private const val EXPORT_VERSION = 2
        private const val KEY_FAVORITES = "favorites_v2"
    }

    private val json = Json {
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
        val providerFavorites: List<ProviderFavorites> = emptyList()
    )

    @Serializable
    data class GlobalSettings(
        val themeId: String = "deep_night",
        val uiScale: Float = 1.0f,
        val isDevMode: Boolean = false,
        val epgAutoRefreshEnabled: Boolean = true,
        val cellularLiveMultiplier: Float = 1.0f,
        val cellularVodMultiplier: Float = 1.0f
    )

    @Serializable
    data class ExportedProvider(
        val name: String,
        val url: String,
        val username: String,
        val type: String,
        val config: String = "",
        val providerSettings: String = "{}",
        val isActive: Boolean = false
    )

    @Serializable
    data class ExportedEpgSource(
        val url: String,
        val label: String = "",
        val timezoneOffsetHours: Int = 0,
        val enabled: Boolean = true
    )

    @Serializable
    data class ProviderFavorites(
        val providerName: String,
        val providerUrl: String,
        val favorites: List<ExportedFavorite> = emptyList()
    )

    @Serializable
    data class ExportedFavorite(
        val itemId: String,
        val itemName: String,
        val categoryId: String,
        val contentType: String
    )

    /**
     * Controls which sections of the export file to import.
     */
    data class ImportOptions(
        val importProviders: Boolean = true,
        val importEpgSources: Boolean = true,
        val importGlobalSettings: Boolean = true,
        val importFavorites: Boolean = true
    )

    enum class ConflictResolution {
        OVERWRITE, DUPLICATE, SKIP
    }

    /**
     * Parsed import data with conflict information.
     */
    data class ParsedImport(
        val settings: ExportedSettings,
        val jsonString: String,
        val conflictingProviders: List<String>
    ) {
        val hasConflicts: Boolean get() = conflictingProviders.isNotEmpty()
        val hasProviders: Boolean get() = settings.providers.isNotEmpty()
        val hasEpgSources: Boolean get() = settings.epgSources.isNotEmpty()
        val hasFavorites: Boolean get() = settings.providerFavorites.isNotEmpty()
    }

    /**
     * Export all settings to a JSON string.
     */
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val appSettings = AppSettings(context)
        val providerRepo = ProviderRepository(context)
        val epgDb = EpgIndexDatabase.getInstance(context)

        val global = GlobalSettings(
            themeId = appSettings.themeId,
            uiScale = appSettings.uiScale,
            isDevMode = appSettings.isDevMode,
            epgAutoRefreshEnabled = appSettings.epgAutoRefreshEnabled,
            cellularLiveMultiplier = appSettings.cellularLiveMultiplier,
            cellularVodMultiplier = appSettings.cellularVodMultiplier
        )

        val providers = providerRepo.getAllProvidersList().map { entity ->
            ExportedProvider(
                name = entity.name,
                url = entity.url,
                username = entity.username,
                type = entity.type,
                config = entity.config,
                providerSettings = entity.providerSettings,
                isActive = entity.isActive
            )
        }

        val epgSources = epgDb.epgSourceDao().getAllSourcesOnce().map { source ->
            ExportedEpgSource(
                url = source.url,
                label = source.label,
                timezoneOffsetHours = source.timezoneOffsetHours,
                enabled = source.enabled
            )
        }

        // Export favorites per provider
        val allProviders = providerRepo.getAllProvidersList()
        val providerFavorites = allProviders.mapNotNull { entity ->
            val cachePrefs = context.getSharedPreferences(
                "media_cache_${entity.id}",
                Context.MODE_PRIVATE
            )
            val favJson = cachePrefs.getString(KEY_FAVORITES, null) ?: return@mapNotNull null
            val favorites = try {
                json.decodeFromString<List<FavoriteItem>>(favJson)
            } catch (e: Exception) {
                return@mapNotNull null
            }
            if (favorites.isEmpty()) return@mapNotNull null
            ProviderFavorites(
                providerName = entity.name,
                providerUrl = entity.url,
                favorites = favorites.map { fav ->
                    ExportedFavorite(
                        itemId = fav.itemId,
                        itemName = fav.itemName,
                        categoryId = fav.categoryId,
                        contentType = fav.contentType
                    )
                }
            )
        }

        val exported = ExportedSettings(
            global = global,
            providers = providers,
            epgSources = epgSources,
            providerFavorites = providerFavorites
        )

        json.encodeToString(exported)
    }

    /**
     * Export settings to a URI (file) via the Storage Access Framework.
     * @return true if export succeeded
     */
    suspend fun exportToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonString = exportToJson()
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(jsonString.toByteArray(Charsets.UTF_8))
            }
            Log.d(TAG, "Exported settings to $uri (${jsonString.length} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            false
        }
    }

    /**
     * Parse an import file from a direct file path and detect provider conflicts.
     */
    suspend fun parseImportPath(path: String): kotlin.Result<ParsedImport> = withContext(Dispatchers.IO) {
        try {
            val file = java.io.File(path)
            if (!file.exists()) return@withContext kotlin.Result.failure(Exception("File does not exist: $path"))
            if (!file.canRead()) return@withContext kotlin.Result.failure(Exception("Permission denied: Cannot read $path"))
            
            val jsonString = file.readText(Charsets.UTF_8)
            val exported = json.decodeFromString<ExportedSettings>(jsonString)
            val providerRepo = ProviderRepository(context)
            val existingProviders = providerRepo.getAllProvidersList()

            val conflicts = exported.providers
                .filter { ep -> existingProviders.any { it.name == ep.name } }
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
    suspend fun parseImportUri(uri: Uri): kotlin.Result<ParsedImport> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            } ?: return@withContext kotlin.Result.failure(Exception("Could not read file"))

            val exported = json.decodeFromString<ExportedSettings>(jsonString)
            val providerRepo = ProviderRepository(context)
            val existingProviders = providerRepo.getAllProvidersList()

            val conflicts = exported.providers
                .filter { ep -> existingProviders.any { it.name == ep.name } }
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
        options: ImportOptions = ImportOptions()
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            } ?: return@withContext ImportResult(error = "Could not read file")

            importFromJson(jsonString, conflictResolution, options)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            ImportResult(error = e.message ?: "Import failed")
        }
    }

    /**
     * Import from a pre-parsed import (avoids re-reading the file).
     */
    suspend fun importFromParsed(
        parsed: ParsedImport,
        conflictResolution: ConflictResolution = ConflictResolution.SKIP,
        options: ImportOptions = ImportOptions()
    ): ImportResult {
        return importFromJson(parsed.jsonString, conflictResolution, options)
    }

    /**
     * Import settings from a JSON string.
     */
    suspend fun importFromJson(
        jsonString: String,
        conflictResolution: ConflictResolution = ConflictResolution.SKIP,
        options: ImportOptions = ImportOptions()
    ): ImportResult = withContext(Dispatchers.IO) {
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
            var providersAdded = 0
            var providersUpdated = 0
            var providersSkipped = 0

            if (options.importProviders) {
                for (ep in exported.providers) {
                    val existing = existingProviders.find { it.name == ep.name }
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
                                    config = ep.config
                                )
                                if (ep.providerSettings != "{}") {
                                    try {
                                        val settings = json.decodeFromString<org.njarasoa.fijerena.core.network.provider.ProviderSettings>(ep.providerSettings)
                                        providerRepo.updateProviderSettings(existing.id, settings)
                                    } catch (_: Exception) { }
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
                        val allProviders = providerRepo.getAllProvidersList()
                        val matching = allProviders.find { it.name == activeExport.name && it.url == activeExport.url }
                            ?: allProviders.find { it.name == "${activeExport.name} (imported)" }
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
                val epgDb = EpgIndexDatabase.getInstance(context)
                val sourceDao = epgDb.epgSourceDao()
                val existingSources = sourceDao.getAllSourcesOnce()

                Log.d(TAG, "EPG import: ${exported.epgSources.size} sources in file, ${existingSources.size} existing in DB")
                for (es in exported.epgSources) {
                    val exists = existingSources.any { it.url == es.url }
                    if (exists) {
                        Log.d(TAG, "EPG source already exists, skipping: ${es.url}")
                        sourcesSkipped++
                        continue
                    }
                    Log.d(TAG, "EPG source inserting: ${es.url} (label=${es.label})")
                    sourceDao.insertSource(
                        EpgSourceEntity(
                            url = es.url,
                            label = es.label,
                            timezoneOffsetHours = es.timezoneOffsetHours,
                            enabled = es.enabled
                        )
                    )
                    sourcesAdded++
                }
            }

            // Import favorites per provider (match by name + URL)
            var favoritesRestored = 0

            if (options.importFavorites && exported.providerFavorites.isNotEmpty()) {
                val allProviders = providerRepo.getAllProvidersList()
                for (pf in exported.providerFavorites) {
                    val matchingProvider = allProviders.find { it.name == pf.providerName && it.url == pf.providerUrl }
                        ?: allProviders.find { it.name == pf.providerName }
                        ?: continue
                    val cachePrefs = context.getSharedPreferences(
                        "media_cache_${matchingProvider.id}",
                        Context.MODE_PRIVATE
                    )
                    // Merge with existing favorites (don't overwrite)
                    val existingJson = cachePrefs.getString(KEY_FAVORITES, null)
                    val existingFavorites = if (existingJson != null) {
                        try {
                            json.decodeFromString<List<FavoriteItem>>(existingJson)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                    val existingKeys = existingFavorites.map { "${it.itemId}::${it.contentType}" }.toSet()
                    val newFavorites = pf.favorites
                        .filter { "${it.itemId}::${it.contentType}" !in existingKeys }
                        .map { fav ->
                            FavoriteItem(
                                itemId = fav.itemId,
                                itemName = fav.itemName,
                                categoryId = fav.categoryId,
                                contentType = fav.contentType
                            )
                        }
                    if (newFavorites.isNotEmpty()) {
                        val merged = existingFavorites + newFavorites
                        cachePrefs.edit().putString(KEY_FAVORITES, json.encodeToString(merged)).apply()
                        favoritesRestored += newFavorites.size
                    }
                }
            }

            Log.d(TAG, "Import complete: $providersAdded added, $providersUpdated updated, $providersSkipped skipped, $sourcesAdded EPG sources added, $favoritesRestored favorites restored")

            ImportResult(
                providersAdded = providersAdded,
                providersUpdated = providersUpdated,
                providersSkipped = providersSkipped,
                epgSourcesAdded = sourcesAdded,
                epgSourcesSkipped = sourcesSkipped,
                favoritesRestored = favoritesRestored
            )
        } catch (e: kotlinx.serialization.SerializationException) {
            Log.e(TAG, "Invalid settings file format", e)
            ImportResult(error = "Invalid settings file format")
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            ImportResult(error = e.message ?: "Import failed")
        }
    }

    private suspend fun addNewProvider(providerRepo: ProviderRepository, ep: ExportedProvider) {
        providerRepo.addProvider(
            name = ep.name,
            url = ep.url,
            username = ep.username,
            password = "",
            type = ep.type,
            config = ep.config
        )
        if (ep.providerSettings != "{}") {
            val allProviders = providerRepo.getAllProvidersList()
            val newProvider = allProviders.find { it.name == ep.name && it.url == ep.url }
            if (newProvider != null) {
                try {
                    val settings = json.decodeFromString<org.njarasoa.fijerena.core.network.provider.ProviderSettings>(ep.providerSettings)
                    providerRepo.updateProviderSettings(newProvider.id, settings)
                } catch (_: Exception) { }
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
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = error == null

        fun toSummary(): String {
            if (error != null) return "Import failed: $error"
            val parts = mutableListOf<String>()
            if (providersAdded > 0) parts.add("$providersAdded provider(s) added")
            if (providersUpdated > 0) parts.add("$providersUpdated provider(s) overwritten")
            if (providersSkipped > 0) parts.add("$providersSkipped provider(s) skipped")
            if (epgSourcesAdded > 0) parts.add("$epgSourcesAdded EPG source(s) added")
            if (epgSourcesSkipped > 0) parts.add("$epgSourcesSkipped EPG source(s) already existed")
            if (favoritesRestored > 0) parts.add("$favoritesRestored favorite(s) restored")
            if (parts.isEmpty()) parts.add("Settings updated")
            if (providersAdded > 0) parts.add("Passwords must be re-entered in provider settings")
            return parts.joinToString(". ") + "."
        }
    }
}
