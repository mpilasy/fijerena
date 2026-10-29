package org.njarasoa.fijerena.core.network.profile

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.CategoryFiltersStore
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.sync.SyncKind
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Creating, editing and deleting profiles. See the live sync plan → User
 * profiles.
 */
class ProfileRepository(
    private val context: Context,
) {
    private val dao = SettingsDatabase.getInstance(context).profileDao()

    fun observeProfiles(): Flow<List<ProfileEntity>> = dao.observeAll()

    suspend fun count(): Int = dao.count()

    /**
     * Returns the new profile's id. A [child] profile ([AppSettings.isChildProfile]) gets only the
     * creator's sources that content filters work on (Xtream), unless that leaves none.
     */
    suspend fun addProfile(
        name: String,
        colorIndex: Int,
        child: Boolean = false,
    ): String {
        val id = UUID.randomUUID().toString()
        dao.insert(ProfileEntity(id = id, name = name.trim(), createdAt = System.currentTimeMillis(), colorIndex = colorIndex))
        // Starts with the creator's category filters, sources, colour theme and Look and Feel; dev
        // mode starts off (no flag stored).
        val settings = AppSettings(context)
        if (child) settings.setChildProfile(id, true)
        CategoryFiltersStore(context).copyProfile(settings.activeProfileId, id)
        settings.copyLook(settings.activeProfileId, id)
        settings.profileSourceKeys(settings.activeProfileId)?.let { keys ->
            settings.setProfileSourceKeys(id, if (child) filterableSourceKeys(keys) else keys)
        }
        return id
    }

    /**
     * Makes [id] a child profile or not. A child is never Expert, and loses the sources content
     * filters can't reach (Remote M3U, Jellyfin…) while it keeps at least one; turning it off
     * gives nothing back. The hidden categories follow at once if it is this device's profile.
     */
    suspend fun setChildProfile(
        id: String,
        child: Boolean,
    ) {
        val settings = AppSettings(context)
        if (settings.isChildProfile(id) == child) return
        settings.setChildProfile(id, child)
        if (child) {
            if (settings.devMode(id)) settings.setDevMode(id, false)
            settings.profileSourceKeys(id)?.let { keys ->
                val kept = filterableSourceKeys(keys)
                if (kept != keys) settings.setProfileSourceKeys(id, kept)
            }
        }
        ProviderRepository(context).childProfileChanged(id)
    }

    /**
     * The child lock on a switch of this device to [targetId] ([SwitchLock.of]): read from the
     * stored profiles and flags, not from a list a screen collects, which is empty until loaded.
     * Fails closed: profiles that can't be read refuse a switch away from a child. Always free
     * while the lock is off ([SwitchLock.ON]).
     */
    suspend fun needsAdminPinToSwitch(targetId: String): SwitchLock =
        if (!SwitchLock.ON) {
            SwitchLock.Free
        } else {
            withContext(Dispatchers.IO) {
                val settings = AppSettings(context)
                val profileIds =
                    try {
                        dao.getAll().map { it.id }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Profiles not read for the child lock", e)
                        null
                    }
                SwitchLock.of(settings.activeProfileId, targetId, profileIds, settings::isChildProfile) {
                    settings.devMode(it) && settings.pinHash(it) != null
                }
            }
        }

    /** [keys] without the sources content filters can't reach, or [keys] itself when none could stay. */
    private suspend fun filterableSourceKeys(keys: Set<String>): Set<String> {
        val providers = SettingsDatabase.getInstance(context).providerDao().getAllProvidersList()
        val xtream = providers.filter { it.type == "XTREAM" }.mapTo(HashSet()) { it.providerKey }
        // A key whose source hasn't reached this device yet stays: nothing says what it is.
        val known = providers.mapTo(HashSet()) { it.providerKey }
        val kept = keys.filter { it in xtream || it !in known }.toSet()
        return if (kept.any { it in xtream }) kept else keys
    }

    suspend fun updateProfile(
        id: String,
        name: String,
        colorIndex: Int,
    ) {
        dao.update(id, name.trim(), colorIndex)
    }

    /**
     * See [AppSettings.copyLegacyDevModeToProfiles], [AppSettings.copyLegacyLookToProfiles],
     * [AppSettings.moveLegacySearchHistoryToDefaultProfile] and
     * [AppSettings.seedProfileSourcesOnce], [seedProfileSourceRecordsOnce] and
     * [ProviderRepository.seedParentalListsOnce]; run once at startup.
     */
    suspend fun migrateLegacyProfileSettings() {
        val settings = AppSettings(context)
        val profileIds = dao.getAll().map { it.id }
        settings.copyLegacyDevModeToProfiles(profileIds)
        settings.copyLegacyLookToProfiles(profileIds)
        settings.moveLegacySearchHistoryToDefaultProfile()
        ProviderRepository(context).seedProfileSourcesOnce()
        ProviderRepository(context).movePerSourcePlaybackOnce()
        ProviderRepository(context).seedParentalListsOnce()
        seedProfileSourceRecordsOnce(settings)
        clearChildExperts()
        ensureAnExpert()
    }

    /**
     * A child profile is never Expert, as [setChildProfile] keeps it for a change made here: turns
     * Expert mode off on every child profile that has it. For what comes in from elsewhere (live
     * sync, an upgrade), whatever order the two flags came in. Synced like any change, so every
     * device ends with Expert off, not only this one. Run [ensureAnExpert] after it. Returns the
     * ids of the profiles it changed.
     */
    suspend fun clearChildExperts(): List<String> =
        withContext(Dispatchers.IO) {
            val settings = AppSettings(context)
            dao
                .getAll()
                .map { it.id }
                .filter { settings.isChildProfile(it) && settings.devMode(it) }
                .onEach { settings.setDevMode(it, false) }
        }

    /**
     * Keeps at least one profile with Expert mode (the admin, for now), whatever changed it — an
     * upgrade, a deletion, a sync from another device, a backup import: with none, the oldest
     * profile gets it, synced like any change (the same oldest profile on every device, so they
     * agree). Returns that profile's id, or null when there was an Expert already (or no profile).
     * The profile pages also refuse to turn off the last one; this is the backstop.
     */
    suspend fun ensureAnExpert(): String? =
        withContext(Dispatchers.IO) {
            val settings = AppSettings(context)
            val profiles = dao.getAll()
            if (profiles.isEmpty() || profiles.any { settings.devMode(it.id) }) return@withContext null
            // A child profile is never Expert: the oldest grown-up gets it, or with only children
            // the oldest stops being one.
            val oldest =
                profiles
                    .filterNot { settings.isChildProfile(it.id) }
                    .ifEmpty { profiles }
                    .minWith(compareBy<ProfileEntity>({ it.createdAt }, { it.id }))
            if (settings.isChildProfile(oldest.id)) setChildProfile(oldest.id, false)
            settings.setDevMode(oldest.id, true)
            oldest.id
        }

    /**
     * One-time upgrade: a device linked before a profile's sources synced per source has none of
     * those records (linking seeds them only once). Seeds one per profile and per source it has on
     * or knows, as this device has it — at the clock of the whole-list record it replaces, the way
     * linking seeds each item at its own last-change time, so a newer change made on another
     * device before this one upgraded still wins. A profile whose list never synced from here has
     * no such record and waits for linking.
     */
    private suspend fun seedProfileSourceRecordsOnce(settings: AppSettings) {
        if (settings.profileSourceRecordsQueued) return
        val database = SettingsDatabase.getInstance(context)
        val sync = database.settingsSyncDao()
        val providerKeys = database.providerDao().getAllProvidersList().map { it.providerKey }
        dao.getAll().forEach { profile ->
            val on = settings.profileSourceKeys(profile.id) ?: return@forEach
            val at = sync.settingVersion(profile.id, AppSettings.LEGACY_PROFILE_SOURCES_SETTING) ?: return@forEach
            (on + providerKeys).forEach { sync.seed(SyncKind.SETTING, profile.id, AppSettings.profileSourceSettingKey(it), at) }
        }
        settings.profileSourceRecordsQueued = true
    }

    /** Why [deleteProfile] refused, or [NONE] when it went ahead. */
    enum class DeleteBlocked { NONE, ACTIVE, LAST }

    /**
     * Deletes a profile with its favourites, watch state, Recent Categories and bookmarks on every
     * provider, and its developer-mode flag, PIN, colour theme and Look and Feel, search history, category filters and sources. Refuses the profile this device is using — switch away first — and the last one
     * left, since the app always needs somebody to be.
     */
    suspend fun deleteProfile(id: String): DeleteBlocked =
        withContext(Dispatchers.IO) {
            when {
                id == AppSettings(context).activeProfileId -> {
                    DeleteBlocked.ACTIVE
                }

                dao.getAll().size <= 1 -> {
                    DeleteBlocked.LAST
                }

                // Crash-safe by ordering, not by one transaction (it spans two databases and
                // SharedPreferences): each database's part is atomic, every step is an idempotent
                // delete, and the profile's own row goes last. Killed part-way, the profile is
                // still listed and deleting it again finishes the job; a deletion received from
                // another device is re-pulled and re-applied the same way. See
                // the rock solid stability resilience plan → F-14.
                else -> {
                    val xtreamDb = XtreamDatabase.getInstance(context)
                    xtreamDb.withTransaction {
                        xtreamDb.watchStateDao().deleteProfile(id)
                        xtreamDb.favoriteStateDao().deleteProfile(id)
                        xtreamDb.wantedTitleDao().deleteProfile(id)
                        // The profile's own tombstone (below) covers its favourite and history
                        // ones, and anything of it still waiting to be sent.
                        xtreamDb.syncTombstoneDao().deleteForProfile(id)
                        xtreamDb.syncVersionDao().deleteForProfile(id)
                    }
                    deleteProfilePrefs(id)
                    AppSettings(context).removeDevMode(id)
                    AppSettings(context).removeChildProfile(id)
                    AppSettings(context).removeAutoplayNextEpisode(id)
                    AppSettings(context).removeChartsShowMissing(id)
                    AppSettings(context).removePin(id)
                    AppSettings(context).removeLook(id)
                    AppSettings(context).removeProfileSearchHistory(id)
                    AppSettings(context).removeLastTab(id)
                    AppSettings(context).removeProfileSources(id)
                    CategoryFiltersStore(context).removeProfile(id)
                    if (id == ProfileEntity.DEFAULT_ID) clearDefaultProfileData()
                    ensureAnExpert()
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
     * The Default profile keeps its Recent Categories and bookmarks in the provider-level storage
     * every profile shares (`media_cache_<id>`), not in `_profile_` files — so [deleteProfilePrefs]
     * finds none of it. Cleared here instead, leaving what is genuinely shared (every source's
     * login, Jellyfin's included, and migration flags) in place.
     */
    private fun clearDefaultProfileData() {
        java.io
            .File(context.applicationInfo.dataDir, "shared_prefs")
            .listFiles()
            ?.map { it.name.removeSuffix(".xml") }
            ?.filter { SHARED_MEDIA_CACHE.matches(it) }
            ?.forEach { MediaRepository.clearDefaultProfile(context.getSharedPreferences(it, Context.MODE_PRIVATE)) }
    }

    private companion object {
        private const val TAG = "ProfileRepository"

        // media_cache_<providerId> exactly — not a media_cache_<id>_profile_<profileId> file.
        val SHARED_MEDIA_CACHE = Regex("media_cache_\\d+")
    }

    /**
     * Every `media_cache_<providerId>_profile_<id>.xml` (see `MediaRepository.profileCacheName`)
     * and any `provider_creds_<providerId>_profile_<id>.xml` an older build left (a Jellyfin login
     * of its own, see `ProviderRepository.credsFileName`).
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
