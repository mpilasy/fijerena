package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import java.io.ByteArrayOutputStream

/**
 * Repository for syncing settings to Google Drive's appDataFolder.
 * The appDataFolder is a hidden folder only accessible by this app.
 */
class DriveSettingsRepository(
    private val context: Context,
) {
    companion object {
        private const val TAG = "DriveSettingsRepository"
        private const val SETTINGS_FILE_NAME = "media_settings.json"
        private const val APP_NAME = "Fijerena"
        private const val MIME_TYPE_JSON = "application/json"
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

    /**
     * Data model for the synced settings file.
     * Contains all provider settings and metadata.
     */
    @Serializable
    data class SyncedSettings(
        val version: Int = 1,
        val lastModified: Long = System.currentTimeMillis(),
        val providers: Map<String, ProviderSyncData> = emptyMap(),
    )

    @Serializable
    data class ProviderSyncData(
        val providerId: Long,
        val providerName: String,
        val providerType: String,
        val settings: ProviderSettings,
    )

    private fun getDriveService(credential: GoogleAccountCredential): Drive =
        Drive
            .Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential,
            ).setApplicationName(APP_NAME)
            .build()

    /**
     * Download settings from Drive's appDataFolder.
     * Returns null if file doesn't exist or on error.
     */
    suspend fun downloadSettings(credential: GoogleAccountCredential): SyncedSettings? =
        withContext(Dispatchers.IO) {
            try {
                val driveService = getDriveService(credential)

                // Search for the settings file in appDataFolder
                val fileId = findSettingsFileId(driveService)
                if (fileId == null) {
                    return@withContext null
                }

                // Download file content
                val outputStream = ByteArrayOutputStream()
                driveService
                    .files()
                    .get(fileId)
                    .executeMediaAndDownloadTo(outputStream)

                val content = outputStream.toString("UTF-8")

                json.decodeFromString<SyncedSettings>(content)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download settings", e)
                null
            }
        }

    /**
     * Upload settings to Drive's appDataFolder.
     * Creates or updates the settings file.
     */
    suspend fun uploadSettings(
        credential: GoogleAccountCredential,
        settings: SyncedSettings,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val driveService = getDriveService(credential)
                val content = json.encodeToString(settings.copy(lastModified = System.currentTimeMillis()))
                val mediaContent = ByteArrayContent.fromString(MIME_TYPE_JSON, content)

                // Check if file already exists
                val existingFileId = findSettingsFileId(driveService)

                if (existingFileId != null) {
                    // Update existing file
                    driveService
                        .files()
                        .update(existingFileId, null, mediaContent)
                        .execute()
                } else {
                    // Create new file in appDataFolder
                    val fileMetadata =
                        File().apply {
                            name = SETTINGS_FILE_NAME
                            parents = listOf("appDataFolder")
                        }
                    driveService
                        .files()
                        .create(fileMetadata, mediaContent)
                        .setFields("id")
                        .execute()
                }

                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload settings", e)
                false
            }
        }

    /**
     * Delete settings file from Drive.
     */
    suspend fun deleteSettings(credential: GoogleAccountCredential): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val driveService = getDriveService(credential)
                val fileId = findSettingsFileId(driveService)
                if (fileId != null) {
                    driveService.files().delete(fileId).execute()
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete settings", e)
                false
            }
        }

    /**
     * Find the settings file ID in appDataFolder.
     */
    private fun findSettingsFileId(driveService: Drive): String? {
        val result =
            driveService
                .files()
                .list()
                .setSpaces("appDataFolder")
                .setQ("name = '$SETTINGS_FILE_NAME'")
                .setFields("files(id, name)")
                .setPageSize(1)
                .execute()

        return result.files?.firstOrNull()?.id
    }

    /**
     * Check if settings file exists in Drive.
     */
    suspend fun hasSettingsFile(credential: GoogleAccountCredential): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val driveService = getDriveService(credential)
                findSettingsFileId(driveService) != null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check settings file", e)
                false
            }
        }
}
