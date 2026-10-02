package org.njarasoa.fijerena.core.network.profile

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.CategoryFiltersStore
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import java.util.UUID

/**
 * Creating, editing and deleting profiles. See `docs/plans/20260929_live-sync-plan.md` → User
 * profiles.
 */
class ProfileRepository(
    private val context: Context,
) {
    private val dao = SettingsDatabase.getInstance(context).profileDao()

    fun observeProfiles(): Flow<List<ProfileEntity>> = dao.observeAll()

    suspend fun count(): Int = dao.count()

    /** Returns the new profile's id. */
    suspend fun addProfile(
        name: String,
        colorIndex: Int,
    ): String {
        val id = UUID.randomUUID().toString()
        dao.insert(ProfileEntity(id = id, name = name.trim(), createdAt = System.currentTimeMillis(), colorIndex = colorIndex))
        // Starts with the creator's category filters; dev mode starts off (no flag stored).
        CategoryFiltersStore(context).copyProfile(AppSettings(context).activeProfileId, id)
        return id
    }

    suspend fun updateProfile(
        id: String,
        name: String,
        colorIndex: Int,
    ) {
        dao.update(id, name.trim(), colorIndex)
    }

    /**
     * See [AppSettings.copyLegacyDevModeToProfiles] and
     * [AppSettings.moveLegacySearchHistoryToDefaultProfile]; run once at startup.
     */
    suspend fun migrateLegacyProfileSettings() {
        val settings = AppSettings(context)
        settings.copyLegacyDevModeToProfiles(dao.getAll().map { it.id })
        settings.moveLegacySearchHistoryToDefaultProfile()
    }

    /** Why [deleteProfile] refused, or [NONE] when it went ahead. */
    enum class DeleteBlocked { NONE, ACTIVE, LAST }

    /**
     * Deletes a profile with its favourites, watch state, Recent Categories, bookmarks and Jellyfin
     * logins on every provider, and its developer-mode flag, search history and category filters. Refuses the profile this device is using — switch away first — and the last one
     * left, since the app always needs somebody to be.
     */
    suspend fun deleteProfile(id: String): DeleteBlocked =
        withContext(Dispatchers.IO) {
            when {
                id == AppSettings(context).activeProfileId -> DeleteBlocked.ACTIVE
                dao.getAll().size <= 1 -> DeleteBlocked.LAST
                // Crash-safe by ordering, not by one transaction (it spans two databases and
                // SharedPreferences): each database's part is atomic, every step is an idempotent
                // delete, and the profile's own row goes last. Killed part-way, the profile is
                // still listed and deleting it again finishes the job; a deletion received from
                // another device is re-pulled and re-applied the same way. See
                // docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-14.
                else -> {
                    val xtreamDb = XtreamDatabase.getInstance(context)
                    xtreamDb.withTransaction {
                        xtreamDb.watchStateDao().deleteProfile(id)
                        xtreamDb.favoriteStateDao().deleteProfile(id)
                        // The profile's own tombstone (below) covers its favourite and history
                        // ones, and anything of it still waiting to be sent.
                        xtreamDb.syncTombstoneDao().deleteForProfile(id)
                        xtreamDb.syncVersionDao().deleteForProfile(id)
                    }
                    deleteProfilePrefs(id)
                    AppSettings(context).removeDevMode(id)
                    AppSettings(context).removeProfileSearchHistory(id)
                    CategoryFiltersStore(context).removeProfile(id)
                    if (id == ProfileEntity.DEFAULT_ID) clearDefaultProfileData()
                    val settingsDb = SettingsDatabase.getInstance(context)
                    settingsDb.withTransaction {
                        dao.deleteRecordingTombstone(id)
                        settingsDb.settingsSyncDao().deleteForProfile(id)
                    }
                    DeleteBlocked.NONE
                }
            }
        }

    /**
     * The Default profile keeps its Jellyfin logins, Recent Categories and bookmarks in the
     * provider-level storage every profile shares (`providers.username`, `provider_creds_<id>`,
     * `media_cache_<id>`), not in `_profile_` files — so [deleteProfilePrefs] finds none of it.
     * Cleared here instead, leaving what is genuinely shared (other providers' logins, migration
     * flags) in place.
     */
    private suspend fun clearDefaultProfileData() {
        ProviderRepository(context).clearDefaultJellyfinLogins()
        java.io
            .File(context.applicationInfo.dataDir, "shared_prefs")
            .listFiles()
            ?.map { it.name.removeSuffix(".xml") }
            ?.filter { SHARED_MEDIA_CACHE.matches(it) }
            ?.forEach { MediaRepository.clearDefaultProfile(context.getSharedPreferences(it, Context.MODE_PRIVATE)) }
    }

    private companion object {
        // media_cache_<providerId> exactly — not a media_cache_<id>_profile_<profileId> file.
        val SHARED_MEDIA_CACHE = Regex("media_cache_\\d+")
    }

    /**
     * Every `media_cache_<providerId>_profile_<id>.xml` (see `MediaRepository.profileCacheName`)
     * and `provider_creds_<providerId>_profile_<id>.xml` — its Jellyfin logins, see
     * `ProviderRepository.credsFileName`.
     */
    private fun deleteProfilePrefs(id: String) {
        val suffix = "_profile_$id"
        java.io
            .File(context.applicationInfo.dataDir, "shared_prefs")
            .listFiles()
            ?.map { it.name.removeSuffix(".xml") }
            ?.filter { (it.startsWith("media_cache_") || it.startsWith("provider_creds_")) && it.endsWith(suffix) }
            ?.forEach { context.deleteSharedPreferences(it) }
    }
}
