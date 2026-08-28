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
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

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

        // 4: added providerWatchState (docs/plans/watch-state-durable-storage-plan.md, Phase 4). A
        // backup taken before version 4 has no watch state in it at all — SettingsExportManager
        // never exported watch_history_v3 — so there is no older shape to migrate on import;
        // providerWatchState simply decodes to its empty default on an old file.
        // 5: favourites moved from the favorites_v2 / favorite_categories blobs to the
        // favorite_state table (docs/plans/favorites-durable-storage-plan.md). The on-the-wire
        // shape did not change — providerFavorites still carries the same fields, matched by
        // provider name+URL — so a version 4 file restores onto this build unchanged, and a
        // version 5 file restores onto an older build unchanged. Only the storage read from and
        // written to differs.
        private const val EXPORT_VERSION = 5
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
        val providerWatchState: List<ProviderWatchState> = emptyList(),
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
        // EPG sources belong to a provider. Provider ids aren't stable across devices, so the
        // owner is recorded by name + URL, matching how [ProviderFavorites] identifies providers.
        val providerName: String = "",
        val providerUrl: String = "",
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
     * A provider's `watch_state` rows, identified by provider name+URL rather than the raw
     * `providerId` a row carries on the source device — that id is `autoGenerate`d and is not
     * necessarily what the matched provider is called on the target device, so it is remapped at
     * import time rather than trusted from the file. Same shape [ProviderFavorites] already uses.
     */
    @Serializable
    data class ProviderWatchState(
        val providerName: String,
        val providerUrl: String,
        val rows: List<ExportedWatchState> = emptyList(),
    )

    @Serializable
    data class ExportedWatchState(
        val itemId: String,
        val contentType: String,
        val itemName: String,
        val categoryId: String,
        val positionMs: Long,
        val durationMs: Long,
        val isCompleted: Boolean,
        val updatedAt: Long,
        val lastPlayedAt: Long? = null,
        val seriesId: String? = null,
        val episodeId: String? = null,
        val seriesName: String? = null,
        val episodeExtension: String? = null,
        val audioTrackIndex: Int? = null,
        val subtitleTrackIndex: Int? = null,
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
        // Watch state rides the favorites checkbox (see importFromJson), so its presence counts
        // toward whether that checkbox is worth showing at all.
        val hasFavorites: Boolean get() =
            settings.providerFavorites.isNotEmpty() ||
                settings.providerFavoriteCategories.isNotEmpty() ||
                settings.providerWatchState.isNotEmpty()
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

            val providersById = allProviders.associateBy { it.id }
            val epgSources =
                settingsDb.epgSourceDao().getAllSourcesOnce().mapNotNull { source ->
                    val owner = providersById[source.providerId] ?: return@mapNotNull null
                    ExportedEpgSource(
                        url = source.url,
                        label = source.label,
                        timezoneOffsetHours = source.timezoneOffsetHours,
                        enabled = source.enabled,
                        providerName = owner.name,
                        providerUrl = owner.url,
                    )
                }

            // Export favorites per provider. Rows since docs/plans/favorites-durable-storage-plan.md;
            // the on-disk shape is unchanged, so old backups still import and new ones still restore
            // onto a build that predates the table.
            val favoriteStateDao = XtreamDatabase.getInstance(context).favoriteStateDao()
            val providerFavorites =
                allProviders.mapNotNull { entity ->
                    val favorites =
                        favoriteStateDao.getAll(entity.id).filter { it.kind == FavoriteKind.STREAM }
                    if (favorites.isEmpty()) return@mapNotNull null
                    ProviderFavorites(
                        providerName = entity.name,
                        providerUrl = entity.url,
                        favorites =
                            favorites.map { fav ->
                                ExportedFavorite(
                                    itemId = fav.itemId,
                                    itemName = fav.name,
                                    categoryId = fav.parentCategoryId ?: "",
                                    contentType = fav.contentType,
                                )
                            },
                    )
                }

            // Export favorite categories per provider
            val providerFavoriteCategories =
                allProviders.mapNotNull { entity ->
                    val favCats =
                        favoriteStateDao.getAll(entity.id).filter { it.kind == FavoriteKind.CATEGORY }
                    if (favCats.isEmpty()) return@mapNotNull null
                    ProviderFavoriteCategories(
                        providerName = entity.name,
                        providerUrl = entity.url,
                        favoriteCategories =
                            favCats.map { fav ->
                                ExportedFavoriteCategory(
                                    categoryId = fav.itemId,
                                    categoryName = fav.name,
                                    contentType = fav.contentType,
                                )
                            },
                    )
                }

            // Export watch state per provider (Phase 4, docs/plans/watch-state-durable-storage-plan.md).
            // Not Xtream-specific: watch_state also carries SMB/Local/Remote M3U rows.
            val watchStateDao = XtreamDatabase.getInstance(context).watchStateDao()
            val providerWatchState =
                allProviders.mapNotNull { entity ->
                    val rows = watchStateDao.getAll(entity.id)
                    if (rows.isEmpty()) return@mapNotNull null
                    ProviderWatchState(
                        providerName = entity.name,
                        providerUrl = entity.url,
                        rows =
                            rows.map { row ->
                                ExportedWatchState(
                                    itemId = row.itemId,
                                    contentType = row.contentType,
                                    itemName = row.itemName,
                                    categoryId = row.categoryId,
                                    positionMs = row.positionMs,
                                    durationMs = row.durationMs,
                                    isCompleted = row.isCompleted,
                                    updatedAt = row.updatedAt,
                                    lastPlayedAt = row.lastPlayedAt,
                                    seriesId = row.seriesId,
                                    episodeId = row.episodeId,
                                    seriesName = row.seriesName,
                                    episodeExtension = row.episodeExtension,
                                    audioTrackIndex = row.audioTrackIndex,
                                    subtitleTrackIndex = row.subtitleTrackIndex,
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
                    providerWatchState = providerWatchState,
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

                // Re-fetch providers once for every provider-scoped section below (EPG sources,
                // favorites) - the import may have added new providers with new ids.
                val allProvidersForFavorites =
                    if (providersAdded > 0 || providersUpdated > 0) {
                        providerRepo.getAllProvidersList()
                    } else {
                        existingProviders
                    }
                val providersByNameUrl = allProvidersForFavorites.associateBy { "${it.name}\u0000${it.url}" }
                val providersByName = allProvidersForFavorites.associateBy { it.name }

                // Import EPG sources (merge: add new, skip existing by URL within the same provider)
                var sourcesAdded = 0
                var sourcesSkipped = 0

                if (options.importEpgSources) {
                    val settingsDb = SettingsDatabase.getInstance(context)
                    val sourceDao = settingsDb.epgSourceDao()
                    val existingKeys =
                        sourceDao.getAllSourcesOnce().mapTo(HashSet()) { it.providerId to it.url }

                    for (es in exported.epgSources) {
                        // EPG belongs to a provider - skip sources whose owner isn't on this device.
                        val owner =
                            allProvidersForFavorites.firstOrNull {
                                it.name == es.providerName && it.url == es.providerUrl
                            } ?: providersByName[es.providerName]
                        if (owner == null || (owner.id to es.url) in existingKeys) {
                            sourcesSkipped++
                            continue
                        }
                        sourceDao.insertSource(
                            EpgSourceEntity(
                                url = es.url,
                                label = es.label,
                                timezoneOffsetHours = es.timezoneOffsetHours,
                                enabled = es.enabled,
                                providerId = owner.id,
                            ),
                        )
                        sourcesAdded++
                    }
                }

                // Import favorites per provider (match by name + URL)
                var favoritesRestored = 0

                if (options.importFavorites && exported.providerFavorites.isNotEmpty()) {
                    val favoriteStateDao = XtreamDatabase.getInstance(context).favoriteStateDao()
                    for (pf in exported.providerFavorites) {
                        val matchingProvider =
                            providersByNameUrl["${pf.providerName}\u0000${pf.providerUrl}"]
                                ?: providersByName[pf.providerName]
                                ?: continue
                        // providerId is never taken from the file — ProviderEntity.id is
                        // autoGenerate, so the id the backup was taken under means nothing here.
                        // Existing rows win: restoring must not renumber a favourite the user
                        // already has, so anything already present is skipped rather than replaced.
                        val existingKeys =
                            favoriteStateDao
                                .getAll(matchingProvider.id)
                                .asSequence()
                                .filter { it.kind == FavoriteKind.STREAM }
                                .mapTo(HashSet()) { it.itemId to it.contentType }
                        val now = System.currentTimeMillis()
                        val newRows =
                            pf.favorites
                                .filter { (it.itemId to it.contentType) !in existingKeys }
                                .map { fav ->
                                    FavoriteStateEntity(
                                        providerId = matchingProvider.id,
                                        itemId = fav.itemId,
                                        contentType = fav.contentType,
                                        kind = FavoriteKind.STREAM,
                                        name = fav.itemName,
                                        parentCategoryId = fav.categoryId,
                                        createdAt = now,
                                    )
                                }
                        if (newRows.isNotEmpty()) {
                            favoriteStateDao.restoreAll(newRows)
                            favoritesRestored += newRows.size
                        }
                    }
                }

                // Import favorite categories per provider
                var favoriteCategoriesRestored = 0

                if (options.importFavorites && exported.providerFavoriteCategories.isNotEmpty()) {
                    val favoriteStateDao = XtreamDatabase.getInstance(context).favoriteStateDao()
                    for (pfc in exported.providerFavoriteCategories) {
                        val matchingProvider =
                            providersByNameUrl["${pfc.providerName}\u0000${pfc.providerUrl}"]
                                ?: providersByName[pfc.providerName]
                                ?: continue
                        val existingKeys =
                            favoriteStateDao
                                .getAll(matchingProvider.id)
                                .asSequence()
                                .filter { it.kind == FavoriteKind.CATEGORY }
                                .mapTo(HashSet()) { it.itemId to it.contentType }
                        val now = System.currentTimeMillis()
                        val newRows =
                            pfc.favoriteCategories
                                .filter { (it.categoryId to it.contentType) !in existingKeys }
                                .map { fav ->
                                    FavoriteStateEntity(
                                        providerId = matchingProvider.id,
                                        itemId = fav.categoryId,
                                        contentType = fav.contentType,
                                        kind = FavoriteKind.CATEGORY,
                                        name = fav.categoryName,
                                        parentCategoryId = null,
                                        createdAt = now,
                                    )
                                }
                        if (newRows.isNotEmpty()) {
                            favoriteStateDao.restoreAll(newRows)
                            favoriteCategoriesRestored += newRows.size
                        }
                    }
                }

                // Import watch state per provider (Phase 4, docs/plans/watch-state-durable-storage-plan.md).
                // Rides the same toggle as favorites rather than a checkbox of its own — both are
                // per-provider local state restored the same way. providerId is never read from
                // the file: each row is rebuilt under matchingProvider.id, since ProviderEntity.id
                // is autoGenerate and the id the backup was taken under means nothing here.
                // restoreAll REPLACEs by primary key, so importing the same backup twice is
                // harmless — no existing-row merge needed like favorites, which append.
                var watchStateRestored = 0

                if (options.importFavorites && exported.providerWatchState.isNotEmpty()) {
                    val watchStateDao = XtreamDatabase.getInstance(context).watchStateDao()
                    for (pws in exported.providerWatchState) {
                        val matchingProvider =
                            providersByNameUrl["${pws.providerName}\u0000${pws.providerUrl}"]
                                ?: providersByName[pws.providerName]
                                ?: continue
                        val entities =
                            pws.rows.map { row ->
                                WatchStateEntity(
                                    providerId = matchingProvider.id,
                                    itemId = row.itemId,
                                    contentType = row.contentType,
                                    itemName = row.itemName,
                                    categoryId = row.categoryId,
                                    positionMs = row.positionMs,
                                    durationMs = row.durationMs,
                                    isCompleted = row.isCompleted,
                                    updatedAt = row.updatedAt,
                                    lastPlayedAt = row.lastPlayedAt,
                                    seriesId = row.seriesId,
                                    episodeId = row.episodeId,
                                    seriesName = row.seriesName,
                                    episodeExtension = row.episodeExtension,
                                    audioTrackIndex = row.audioTrackIndex,
                                    subtitleTrackIndex = row.subtitleTrackIndex,
                                )
                            }
                        if (entities.isNotEmpty()) {
                            watchStateDao.restoreAll(entities)
                            watchStateRestored += entities.size
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
                    watchStateRestored = watchStateRestored,
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
        val watchStateRestored: Int = 0,
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

            // Watch state — counted separately from favorites even though it rides the same
            // import toggle, so the summary doesn't claim more "favorites" than were actually favorited.
            if (watchStateRestored > 0) {
                parts.add(context.getString(R.string.settings_export_summary_watch_state_format, watchStateRestored))
            }

            if (parts.isEmpty()) parts.add(context.getString(R.string.settings_export_summary_updated))
            val summary = parts.joinToString(". ") + "."
            return if (providersAdded > 0) context.getString(R.string.settings_export_summary_passwords_reentry_format, summary) else summary
        }
    }
}
