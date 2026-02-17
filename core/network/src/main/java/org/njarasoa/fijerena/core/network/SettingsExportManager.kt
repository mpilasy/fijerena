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
        private const val EXPORT_VERSION = 1
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
        val epgSources: List<ExportedEpgSource> = emptyList()
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

        val exported = ExportedSettings(
            global = global,
            providers = providers,
            epgSources = epgSources
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
     * Import settings from a URI (file) via the Storage Access Framework.
     * Merges providers (adds new ones, skips duplicates by name+url).
     * EPG sources are merged (adds new ones, skips duplicates by URL).
     * Global settings are overwritten.
     *
     * @return A summary of what was imported, or null on error.
     */
    suspend fun importFromUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            } ?: return@withContext ImportResult(error = "Could not read file")

            importFromJson(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            ImportResult(error = e.message ?: "Import failed")
        }
    }

    /**
     * Import settings from a JSON string.
     */
    suspend fun importFromJson(jsonString: String): ImportResult = withContext(Dispatchers.IO) {
        try {
            val exported = json.decodeFromString<ExportedSettings>(jsonString)

            // Apply global settings
            val appSettings = AppSettings(context)
            appSettings.themeId = exported.global.themeId
            appSettings.uiScale = exported.global.uiScale
            appSettings.isDevMode = exported.global.isDevMode
            appSettings.epgAutoRefreshEnabled = exported.global.epgAutoRefreshEnabled
            appSettings.cellularLiveMultiplier = exported.global.cellularLiveMultiplier
            appSettings.cellularVodMultiplier = exported.global.cellularVodMultiplier

            // Import providers (merge: add new, skip existing by name+url)
            val providerRepo = ProviderRepository(context)
            val existingProviders = providerRepo.getAllProvidersList()
            var providersAdded = 0
            var providersSkipped = 0

            for (ep in exported.providers) {
                val exists = existingProviders.any { it.name == ep.name && it.url == ep.url }
                if (exists) {
                    providersSkipped++
                    continue
                }
                providerRepo.addProvider(
                    name = ep.name,
                    url = ep.url,
                    username = ep.username,
                    password = "",
                    type = ep.type,
                    config = ep.config
                )
                // Update the provider settings for the newly added provider
                val allProviders = providerRepo.getAllProvidersList()
                val newProvider = allProviders.find { it.name == ep.name && it.url == ep.url }
                if (newProvider != null && ep.providerSettings != "{}") {
                    try {
                        val settings = json.decodeFromString<org.njarasoa.fijerena.core.network.provider.ProviderSettings>(ep.providerSettings)
                        providerRepo.updateProviderSettings(newProvider.id, settings)
                    } catch (_: Exception) { }
                }
                providersAdded++
            }

            // If we had an active provider in the export and none is currently active, activate the matching one
            val activeExport = exported.providers.find { it.isActive }
            if (activeExport != null) {
                val currentActive = providerRepo.getActiveProvider()
                if (currentActive == null) {
                    val allProviders = providerRepo.getAllProvidersList()
                    val matching = allProviders.find { it.name == activeExport.name && it.url == activeExport.url }
                    if (matching != null) {
                        providerRepo.setActiveProvider(matching.id)
                    }
                }
            }

            // Import EPG sources (merge: add new, skip existing by URL)
            val epgDb = EpgIndexDatabase.getInstance(context)
            val sourceDao = epgDb.epgSourceDao()
            val existingSources = sourceDao.getAllSourcesOnce()
            var sourcesAdded = 0
            var sourcesSkipped = 0

            for (es in exported.epgSources) {
                val exists = existingSources.any { it.url == es.url }
                if (exists) {
                    sourcesSkipped++
                    continue
                }
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

            Log.d(TAG, "Import complete: $providersAdded providers added, $sourcesAdded EPG sources added")

            ImportResult(
                providersAdded = providersAdded,
                providersSkipped = providersSkipped,
                epgSourcesAdded = sourcesAdded,
                epgSourcesSkipped = sourcesSkipped
            )
        } catch (e: kotlinx.serialization.SerializationException) {
            Log.e(TAG, "Invalid settings file format", e)
            ImportResult(error = "Invalid settings file format")
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            ImportResult(error = e.message ?: "Import failed")
        }
    }

    data class ImportResult(
        val providersAdded: Int = 0,
        val providersSkipped: Int = 0,
        val epgSourcesAdded: Int = 0,
        val epgSourcesSkipped: Int = 0,
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = error == null

        fun toSummary(): String {
            if (error != null) return "Import failed: $error"
            val parts = mutableListOf<String>()
            if (providersAdded > 0) parts.add("$providersAdded provider(s) added")
            if (providersSkipped > 0) parts.add("$providersSkipped provider(s) already existed")
            if (epgSourcesAdded > 0) parts.add("$epgSourcesAdded EPG source(s) added")
            if (epgSourcesSkipped > 0) parts.add("$epgSourcesSkipped EPG source(s) already existed")
            if (parts.isEmpty()) parts.add("Settings updated")
            return parts.joinToString(". ") + "."
        }
    }
}
