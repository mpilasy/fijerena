package org.njarasoa.fijerena.core.network.profile

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
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
        return id
    }

    suspend fun updateProfile(
        id: String,
        name: String,
        colorIndex: Int,
    ) {
        dao.update(id, name.trim(), colorIndex)
    }

    /** Why [deleteProfile] refused, or [NONE] when it went ahead. */
    enum class DeleteBlocked { NONE, ACTIVE, LAST }

    /**
     * Deletes a profile with its favourites, watch state, Recent Categories and bookmarks on every
     * provider. Refuses the profile this device is using — switch away first — and the last one
     * left, since the app always needs somebody to be.
     */
    suspend fun deleteProfile(id: String): DeleteBlocked =
        withContext(Dispatchers.IO) {
            when {
                id == AppSettings(context).activeProfileId -> DeleteBlocked.ACTIVE
                dao.getAll().size <= 1 -> DeleteBlocked.LAST
                else -> {
                    val xtreamDb = XtreamDatabase.getInstance(context)
                    xtreamDb.watchStateDao().deleteProfile(id)
                    xtreamDb.favoriteStateDao().deleteProfile(id)
                    deleteProfilePrefs(id)
                    dao.delete(id)
                    DeleteBlocked.NONE
                }
            }
        }

    /** Every `media_cache_<providerId>_profile_<id>.xml` — see `MediaRepository.profileCacheName`. */
    private fun deleteProfilePrefs(id: String) {
        val suffix = "_profile_$id"
        java.io
            .File(context.applicationInfo.dataDir, "shared_prefs")
            .listFiles()
            ?.map { it.name.removeSuffix(".xml") }
            ?.filter { it.startsWith("media_cache_") && it.endsWith(suffix) }
            ?.forEach { context.deleteSharedPreferences(it) }
    }
}
