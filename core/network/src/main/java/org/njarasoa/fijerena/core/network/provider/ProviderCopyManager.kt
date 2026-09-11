package org.njarasoa.fijerena.core.network.provider

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.R
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * On-device provider-to-provider copy: connection/credentials, [ProviderSettings], favorites and
 * watch history, moved directly between two [ProviderEntity] rows via Room/EncryptedSharedPreferences.
 * No JSON, no file I/O — that's [org.njarasoa.fijerena.core.network.SettingsExportManager]'s job
 * (cross-device backup), which this does not touch.
 *
 * Favorites and watch history copy with "existing wins" merge semantics: a row already present on
 * the target (by its natural key) is left alone, only genuinely new rows are added. Connection and
 * settings copies are plain overwrites of the target — there's nothing to merge there.
 */
class ProviderCopyManager(
    private val context: Context,
) {
    data class CopyOptions(
        /** URL/username/password/type/config. Off by default: overwrites a working login. */
        val copyConnection: Boolean = false,
        val copyProviderSettings: Boolean = true,
        val copyFavorites: Boolean = true,
        val copyWatchHistory: Boolean = true,
    )

    data class CopyResult(
        val connectionCopied: Boolean = false,
        val settingsCopied: Boolean = false,
        val favoritesCopied: Int = 0,
        val watchStateCopied: Int = 0,
    ) {
        fun toSummary(context: Context): String {
            val parts = mutableListOf<String>()
            if (connectionCopied) parts.add(context.getString(R.string.provider_copy_result_connection))
            if (settingsCopied) parts.add(context.getString(R.string.provider_copy_result_settings))
            if (favoritesCopied > 0) parts.add(context.getString(R.string.provider_copy_result_favorites_format, favoritesCopied))
            if (watchStateCopied > 0) parts.add(context.getString(R.string.provider_copy_result_watch_state_format, watchStateCopied))
            if (parts.isEmpty()) return context.getString(R.string.provider_copy_result_nothing)
            return parts.joinToString(", ") + "."
        }
    }

    private val providerRepo = ProviderRepository(context)

    /**
     * Copies [options]-selected data from [sourceId] into [targetId]. Both providers must already
     * exist; [targetId]'s own `name` is never touched — this repoints/merges data into it, it
     * doesn't rename it. EPG sources are out of scope.
     */
    suspend fun copyProviderData(
        sourceId: Long,
        targetId: Long,
        options: CopyOptions,
    ): CopyResult =
        withContext(Dispatchers.IO) {
            if (sourceId == targetId) return@withContext CopyResult()
            val db = SettingsDatabase.getInstance(context)
            val source = db.providerDao().getProviderById(sourceId) ?: return@withContext CopyResult()
            val target = db.providerDao().getProviderById(targetId) ?: return@withContext CopyResult()

            var connectionCopied = false
            var settingsCopied = false
            var favoritesCopied = 0
            var watchStateCopied = 0

            if (options.copyConnection) {
                providerRepo.updateProvider(
                    id = target.id,
                    name = target.name,
                    url = source.url,
                    username = source.username,
                    password = providerRepo.getPassword(source.id) ?: "",
                    type = source.type,
                    config = source.config,
                )
                connectionCopied = true
            }

            if (options.copyProviderSettings) {
                providerRepo.updateProviderSettings(target.id, providerRepo.getProviderSettings(source.id))
                settingsCopied = true
            }

            val xtreamDb = XtreamDatabase.getInstance(context)

            if (options.copyFavorites) {
                val favoriteStateDao = xtreamDb.favoriteStateDao()
                val existingKeys =
                    favoriteStateDao
                        .getAll(target.id)
                        .mapTo(HashSet()) { Triple(it.itemId, it.contentType, it.kind) }
                val newRows =
                    favoriteStateDao
                        .getAll(source.id)
                        .filter { Triple(it.itemId, it.contentType, it.kind) !in existingKeys }
                        .map { it.copy(providerId = target.id) }
                if (newRows.isNotEmpty()) {
                    favoriteStateDao.restoreAll(newRows)
                    favoritesCopied = newRows.size
                }
            }

            if (options.copyWatchHistory) {
                val watchStateDao = xtreamDb.watchStateDao()
                val existingKeys =
                    watchStateDao
                        .getAll(target.id)
                        .mapTo(HashSet()) { it.itemId to it.contentType }
                val newRows =
                    watchStateDao
                        .getAll(source.id)
                        .filter { (it.itemId to it.contentType) !in existingKeys }
                        .map { it.copy(providerId = target.id) }
                if (newRows.isNotEmpty()) {
                    watchStateDao.restoreAll(newRows)
                    watchStateCopied = newRows.size
                }
            }

            CopyResult(
                connectionCopied = connectionCopied,
                settingsCopied = settingsCopied,
                favoritesCopied = favoritesCopied,
                watchStateCopied = watchStateCopied,
            )
        }

    /**
     * Clones [sourceId] into a brand new provider row named [newName]: same connection/credentials
     * and [ProviderSettings], plus its favorites/watch history. The new provider is created
     * inactive so duplicating doesn't switch the user away from whatever is currently active.
     * Returns the new provider's id, or null if [sourceId] doesn't exist.
     */
    suspend fun duplicateProvider(
        sourceId: Long,
        newName: String,
    ): Long? =
        withContext(Dispatchers.IO) {
            val source = SettingsDatabase.getInstance(context).providerDao().getProviderById(sourceId) ?: return@withContext null
            val newId =
                providerRepo.addProvider(
                    name = newName,
                    url = source.url,
                    username = source.username,
                    password = providerRepo.getPassword(source.id) ?: "",
                    type = source.type,
                    config = source.config,
                    initialSettings = providerRepo.getProviderSettings(source.id),
                    activate = false,
                )
            copyProviderData(
                sourceId = sourceId,
                targetId = newId,
                options = CopyOptions(copyConnection = false, copyProviderSettings = false, copyFavorites = true, copyWatchHistory = true),
            )
            newId
        }
}
