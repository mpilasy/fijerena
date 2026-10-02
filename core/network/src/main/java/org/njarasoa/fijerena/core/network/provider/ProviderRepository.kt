@file:Suppress("DEPRECATION")

package org.njarasoa.fijerena.core.network.provider
import android.content.Context
import androidx.room.withTransaction
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.sync.SettingsSyncQueue
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
    companion object {
        /**
         * Rows per commit when deleting catalogue data — see [deleteInBatches]. Measured on a copy
         * of a real 256 MB `xtream_v2.db` deleting a 285k-row provider: WAL peak 70 MB unbounded,
         * 31 MB at 5000, 19 MB at 2000, 13 MB at 1000.
         */
        private const val CATALOG_DELETE_BATCH = 1_000

        /** Every `xtream_v2.db` table keyed by `providerId` that a provider deletion empties. */
        private val CATALOG_TABLES =
            listOf("xtream_streams", "xtream_series", "xtream_episodes", "xtream_categories", "favorite_state", "xtream_epg_cache")

        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_JELLYFIN_TOKEN = "jellyfin_token"
        private const val KEY_JELLYFIN_USER_ID = "jellyfin_user_id"

        /**
         * The encrypted credentials file for [providerId] as used by [profileId]. The Default
         * profile keeps `provider_creds_<id>`, where credentials always lived, so nothing moves on
         * upgrade; any other profile gets its own file — only ever for Jellyfin, see [getLogin].
         */
        fun credsFileName(
            providerId: Long,
            profileId: String,
        ): String =
            if (profileId == ProfileEntity.DEFAULT_ID) {
                "provider_creds_$providerId"
            } else {
                "provider_creds_${providerId}_profile_$profileId"
            }
    }

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

    // Keyed by file name: one provider can have several — see [credsFileName].
    private val encryptedPrefsCache = java.util.concurrent.ConcurrentHashMap<String, android.content.SharedPreferences>()
    // Keyed by (providerId, profileId): the category filters in each entry are that profile's.
    private val settingsCache = java.util.concurrent.ConcurrentHashMap<Pair<Long, String>, ProviderSettings>()
    private val filtersStore = CategoryFiltersStore(context)

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
     * [rememberForProfile] records it as the active profile's last picked provider (synced); the
     * settings import turns it off, as an imported provider isn't a pick.
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
        rememberForProfile: Boolean = activate,
    ): Long {
        if (activate) dao.deactivateAll()
        // Category filters are per profile and live in [filtersStore], not in the provider's JSON.
        val settingsJson = json.encodeToString(initialSettings.copy(categoryFilters = CategoryFilters()))
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
        // The shared credentials file is the Default profile's Jellyfin login; once Default has been
        // deleted there is nobody to keep a Jellyfin password there for. Every other type's login
        // is shared by all profiles and always lives there.
        if (type != "JELLYFIN" || db.profileDao().exists(ProfileEntity.DEFAULT_ID)) {
            savePassword(id, password)
        }
        // The row's login is the Default profile's. When someone else adds a Jellyfin server it
        // is also theirs, or they'd be asked to sign in to the server they just signed in to.
        val loginProfile = loginProfileId(type)
        if (loginProfile != ProfileEntity.DEFAULT_ID) {
            getProviderPrefs(id, loginProfile).edit {
                putString(KEY_USERNAME, username)
                    .putString(KEY_PASSWORD, password)
            }
            org.njarasoa.fijerena.core.network.CredentialStoreHealth.clear(context)
            SettingsSyncQueue.providerLogin(context, id, loginProfile)
        }
        if (initialSettings.categoryFilters != CategoryFilters()) {
            filtersStore.set(id, activeProfileId(), initialSettings.categoryFilters)
        }
        // Adding a provider moves the device to it, so it is the profile's pick — see [pickProvider].
        if (rememberForProfile) AppSettings(context).setLastProviderKey(activeProfileId(), entity.providerKey)
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
        val effectiveType = type ?: existing.type
        val loginProfile = loginProfileId(effectiveType)
        // A non-Default profile's Jellyfin login is its own: the row's username and the shared
        // password belong to the Default profile and stay untouched.
        val ownLogin = loginProfile != ProfileEntity.DEFAULT_ID
        dao.updateProvider(
            existing.copy(
                name = name,
                url = url,
                username = if (ownLogin) existing.username else username,
                type = effectiveType,
                config = config ?: existing.config,
            ),
        )
        if (ownLogin) {
            getProviderPrefs(id, loginProfile).edit { putString(KEY_USERNAME, username) }
        }
        getProviderPrefs(id, loginProfile).edit { putString(KEY_PASSWORD, password) }
        org.njarasoa.fijerena.core.network.CredentialStoreHealth.clear(context)
        // The password lives outside the row, so no trigger sees it change. A Jellyfin login is
        // its profile's own record; any other provider's login is part of the provider record.
        if (effectiveType == "JELLYFIN") {
            SettingsSyncQueue.providerLogin(context, id, loginProfile)
        } else {
            SettingsSyncQueue.provider(context, id)
        }
        // If Jellyfin credentials changed, discard the cached session token so the
        // provider re-authenticates with the new username/password on next use.
        if (effectiveType == "JELLYFIN") {
            getProviderPrefs(id, loginProfile).edit {
                remove(KEY_JELLYFIN_TOKEN)
                    .remove(KEY_JELLYFIN_USER_ID)
            }
        }
        // Clear cached provider instance since credentials may have changed
        MediaProviderFactory.clearCache(id)
    }

    /**
     * Delete a provider and clean up its encrypted prefs and cache. The deletion is recorded for
     * live sync; its favourite and history tombstones go with it, the provider's own covers them.
     */
    suspend fun deleteProvider(
        id: Long,
        fromRemote: Boolean = false,
    ) {
        val entity = dao.getProviderById(id)
        if (entity != null) {
            dao.deleteProviderRecordingTombstone(entity)
            db.settingsSyncDao().deleteForProviderKey(entity.providerKey)
            deleteProviderEpgSources(id, recordTombstones = !fromRemote)
            clearProviderPassword(id)
            clearProviderCache(id)
            clearProviderWatchState(id)
            clearProviderCatalog(id)
            settingsCache.keys.removeAll { it.first == id }
            filtersStore.removeProvider(id)
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
        // Off the caller's dispatcher: ProviderViewModel.deleteProvider calls in from a bare
        // viewModelScope.launch { }, i.e. Main, and these are blocking database writes.
        withContext(Dispatchers.IO) {
            val db = XtreamDatabase.getInstance(context)
            CATALOG_TABLES.forEach { table -> deleteInBatches(db, table, "providerId = $providerId") }
            db.invalidationTracker.refreshAsync()
        }
    }

    /**
     * Deletes [table]'s rows matching [where], [CATALOG_DELETE_BATCH] at a time, each batch its own
     * commit. `xtream_v2.db` runs `auto_vacuum = FULL`, so each commit moves and frees its pages
     * there and then: one unbounded `DELETE` of a whole provider's catalogue piles all of that page
     * movement into one transaction's WAL. Bounded commits keep the WAL small, and the file shrinks
     * as it goes — which is also why no `VACUUM` follows any more (see
     * docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-35). [where] is built from
     * provider ids only, never from user input.
     */
    private fun deleteInBatches(
        db: XtreamDatabase,
        table: String,
        where: String,
    ): Int {
        val sdb = db.openHelper.writableDatabase
        var total = 0
        do {
            val deleted = sdb.delete(table, "rowid IN (SELECT rowid FROM `$table` WHERE $where LIMIT $CATALOG_DELETE_BATCH)", null)
            total += deleted
        } while (deleted >= CATALOG_DELETE_BATCH)
        return total
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

                    val orphaned = "providerId NOT IN (${validProviderIds.joinToString()})"
                    val rowsRemoved = (CATALOG_TABLES + "watch_state").sumOf { table -> deleteInBatches(db, table, orphaned) }
                    if (rowsRemoved > 0) db.invalidationTracker.refreshAsync()

                    cleanupOrphanedPrefs(validProviderIds.toSet())

                    val sourceDao = this@ProviderRepository.db.epgSourceDao()
                    val allSourceProviderIds = sourceDao.getAllSourcesOnce().map { it.providerId }.toSet()
                    val orphanSourceProviderIds = allSourceProviderIds - validProviderIds.toSet()
                    for (orphanId in orphanSourceProviderIds) {
                        deleteProviderEpgSources(orphanId)
                    }

                    // Only when asked for (Settings → Shrink Database): auto_vacuum = FULL already
                    // returns freed pages to the file as rows go, so a VACUUM here only defragments,
                    // and in WAL mode it rewrites the whole database into the WAL first — a spike as
                    // big as the database itself (measured: 258 MB) that a low-storage TV may not
                    // have room for. See docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-35.
                    if (forceVacuum) {
                        try {
                            val sdb = db.openHelper.writableDatabase
                            sdb.execSQL("VACUUM")
                            sdb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
                        } catch (e: CancellationException) {
                            throw e
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
                            // substringBefore: a non-Default profile's file is
                            // media_cache_<id>_profile_<profileId> (MediaRepository.profileCacheName).
                            val idStr = name.removePrefix(prefix).removeSuffix(".xml").substringBefore('_')
                            val id = idStr.toLongOrNull()
                            if (id != null && id !in validProviderIds) {
                                file.delete()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { // cancellation-ok: non-suspend
            android.util.Log.w("ProviderRepository", "Failed cleaning up orphaned prefs", e)
        }
    }

    /**
     * EPG sources belong to a single provider, and their indexed channels/programmes live in a
     * separate database ([EpgIndexDatabase]), so no SQL cascade is possible - delete both by hand.
     */
    /**
     * EPG source records carry no provider tag, so the server's provider cascade doesn't reach
     * them: a deletion made here must queue a tombstone per source ([recordTombstones]). Applying
     * another device's provider deletion must not — that device already sent them, and queueing
     * them again pushed every one back. See
     * docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-11.
     */
    private suspend fun deleteProviderEpgSources(
        id: Long,
        recordTombstones: Boolean = true,
    ) {
        val sourceDao = db.epgSourceDao()
        val sourceIds = sourceDao.getSourceIdsForProvider(id)
        if (sourceIds.isNotEmpty()) {
            EpgIndexDatabase.getInstance(context).epgIndexDao().deleteBySourceIds(sourceIds)
        }
        if (recordTombstones) {
            sourceDao.deleteSourcesForProvider(id)
        } else {
            db.withTransaction {
                db.settingsSyncDao().setApplying(true)
                sourceDao.deleteSourcesForProvider(id)
                db.settingsSyncDao().setApplying(false)
            }
        }
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
     * The user picked [id]: makes it active and remembers it as the active profile's provider,
     * synced to the profile's other devices. Automatic changes (fallback after a delete, import,
     * profile switch) use [setActiveProvider] so they aren't remembered or sent.
     */
    suspend fun pickProvider(id: Long) {
        setActiveProvider(id)
        dao.getProviderById(id)?.let { AppSettings(context).setLastProviderKey(activeProfileId(), it.providerKey) }
    }

    /**
     * Moves this device to the provider [profileId] last picked, if it still exists here and isn't
     * already active. Otherwise the device stays where it is. Returns whether it moved.
     */
    suspend fun activateLastProvider(profileId: String): Boolean {
        val key = AppSettings(context).lastProviderKey(profileId) ?: return false
        val provider = db.settingsSyncDao().providerByKey(key) ?: return false
        if (provider.isActive) return false
        setActiveProvider(provider.id)
        return true
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
     * Get the stored password for a provider — the Default profile's, for a Jellyfin provider.
     * Use [getLogin] wherever the profile this device uses matters.
     */
    fun getPassword(providerId: Long): String? = getProviderPrefs(providerId, ProfileEntity.DEFAULT_ID).getString(KEY_PASSWORD, null)

    /** A username and password to connect with. Empty strings when there is none yet. */
    data class Login(
        val username: String,
        val password: String,
    )

    /**
     * The login this device's profile uses for [entity]. Jellyfin keeps favourites and history per
     * Jellyfin user, so each profile signs in as its own; every other provider has one login
     * shared by all profiles (docs/plans/20260929_live-sync-plan.md → User profiles). The Default
     * profile's login is the one providers always had: `providers.username` plus
     * `provider_creds_<id>`.
     */
    fun getLogin(entity: ProviderEntity): Login {
        val profileId = loginProfileId(entity.type)
        val prefs = getProviderPrefs(entity.id, profileId)
        val username =
            if (profileId == ProfileEntity.DEFAULT_ID) entity.username else prefs.getString(KEY_USERNAME, null).orEmpty()
        return Login(username, prefs.getString(KEY_PASSWORD, null).orEmpty())
    }

    /**
     * Whether this device's profile can connect to [entity] without signing in first: always,
     * except a Jellyfin server this profile has neither a username nor a saved session for.
     */
    fun hasLogin(entity: ProviderEntity): Boolean {
        val profileId = loginProfileId(entity.type)
        return profileId == ProfileEntity.DEFAULT_ID ||
            getLogin(entity).username.isNotBlank() ||
            getProviderPrefs(entity.id, profileId).getString(KEY_JELLYFIN_TOKEN, null) != null
    }

    /**
     * Persist a Jellyfin session token (from Quick Connect or normal auth) for this device's
     * profile, so the provider can restore it on next launch without re-authenticating.
     */
    fun saveJellyfinSession(
        providerId: Long,
        token: String,
        userId: String,
    ) {
        getProviderPrefs(providerId, loginProfileId("JELLYFIN")).edit {
            putString(KEY_JELLYFIN_TOKEN, token)
                .putString(KEY_JELLYFIN_USER_ID, userId)
        }
    }

    /**
     * Quick Connect sign-in to an existing Jellyfin provider, for this device's profile: the
     * username it reported plus the session, and no password — Quick Connect never has one.
     */
    suspend fun saveQuickConnectLogin(
        providerId: Long,
        username: String,
        token: String,
        userId: String,
    ) {
        val profileId = loginProfileId("JELLYFIN")
        if (profileId == ProfileEntity.DEFAULT_ID) {
            dao.getProviderById(providerId)?.let { dao.updateProvider(it.copy(username = username)) }
        } else {
            getProviderPrefs(providerId, profileId).edit { putString(KEY_USERNAME, username) }
        }
        getProviderPrefs(providerId, profileId).edit { remove(KEY_PASSWORD) }
        SettingsSyncQueue.providerLogin(context, providerId, profileId)
        saveJellyfinSession(providerId, token, userId)
        MediaProviderFactory.clearCache(providerId)
    }

    /**
     * Deleting the Default profile: its Jellyfin logins are the provider-level ones —
     * `providers.username` and the password/session in `provider_creds_<id>` — so they are cleared
     * here, on Jellyfin providers only. Every other provider's login in that same file is shared by
     * all profiles and stays.
     */
    suspend fun clearDefaultJellyfinLogins() {
        dao.getAllProvidersList().filter { it.type == "JELLYFIN" }.forEach { provider ->
            dao.updateProvider(provider.copy(username = ""))
            getProviderPrefs(provider.id, ProfileEntity.DEFAULT_ID).edit {
                remove(KEY_PASSWORD)
                    .remove(KEY_JELLYFIN_TOKEN)
                    .remove(KEY_JELLYFIN_USER_ID)
            }
            SettingsSyncQueue.providerLogin(context, provider.id, ProfileEntity.DEFAULT_ID)
            MediaProviderFactory.clearCache(provider.id)
        }
    }

    // --- Live sync: changes received from another device (phase 5) ---
    // These write without queueing: the caller (SyncApplier) holds the sync clock's `applying` flag
    // for the database writes, and prefs writes here simply skip SettingsSyncQueue.

    /** [profileId]'s own Jellyfin login on [entity] as it is sent, or null if it has none. */
    internal fun syncedLogin(
        entity: ProviderEntity,
        profileId: String,
    ): org.njarasoa.fijerena.core.network.sync.SyncPayloads.Login? {
        val prefs = getProviderPrefs(entity.id, profileId)
        val username = if (profileId == ProfileEntity.DEFAULT_ID) entity.username else prefs.getString(KEY_USERNAME, null).orEmpty()
        if (username.isBlank()) return null
        return org.njarasoa.fijerena.core.network.sync.SyncPayloads.Login(username, prefs.getString(KEY_PASSWORD, null))
    }

    /** Profiles with a Jellyfin login of their own on [providerId] — Default's is the row's. */
    internal fun profilesWithOwnLogin(providerId: Long): List<String> {
        val prefix = "${credsFileName(providerId, ProfileEntity.DEFAULT_ID)}_profile_"
        return java.io
            .File(context.applicationInfo.dataDir, "shared_prefs")
            .listFiles()
            ?.map { it.name.removeSuffix(".xml") }
            ?.filter { it.startsWith(prefix) }
            ?.map { it.removePrefix(prefix) }
            .orEmpty()
    }

    /** Adds or updates the provider named [providerKey]; returns its local id. Never activates it. */
    internal suspend fun applyRemoteProvider(
        providerKey: String,
        remote: org.njarasoa.fijerena.core.network.sync.SyncPayloads.Provider,
    ): Long {
        val existing = db.settingsSyncDao().providerByKey(providerKey)
        val id =
            if (existing != null) {
                dao.updateProvider(
                    existing.copy(
                        name = remote.name,
                        url = remote.url,
                        username = remote.username,
                        type = remote.type,
                        config = remote.config,
                        providerSettings = remote.providerSettings,
                    ),
                )
                existing.id
            } else {
                dao.insertProvider(
                    ProviderEntity(
                        name = remote.name,
                        url = remote.url,
                        username = remote.username,
                        type = remote.type,
                        config = remote.config,
                        providerSettings = remote.providerSettings,
                        isActive = false,
                        providerKey = providerKey,
                    ),
                )
            }
        remote.password?.let { savePassword(id, it) }
        settingsCache.keys.removeAll { it.first == id }
        MediaProviderFactory.clearCache(id)
        return id
    }

    /**
     * Writes (or, when [login] is null, removes) a profile's Jellyfin login. Drops the session so
     * the next use signs in with it — sessions are per device and never synced.
     */
    internal suspend fun applyRemoteLogin(
        providerId: Long,
        profileId: String,
        login: org.njarasoa.fijerena.core.network.sync.SyncPayloads.Login?,
    ) {
        if (profileId == ProfileEntity.DEFAULT_ID) {
            dao.getProviderById(providerId)?.let { dao.updateProvider(it.copy(username = login?.username.orEmpty())) }
        }
        getProviderPrefs(providerId, profileId).edit {
            if (profileId != ProfileEntity.DEFAULT_ID) {
                if (login == null) remove(KEY_USERNAME) else putString(KEY_USERNAME, login.username)
            }
            if (login?.password == null) remove(KEY_PASSWORD) else putString(KEY_PASSWORD, login.password)
            remove(KEY_JELLYFIN_TOKEN).remove(KEY_JELLYFIN_USER_ID)
        }
        if (login?.password != null) org.njarasoa.fijerena.core.network.CredentialStoreHealth.clear(context)
        MediaProviderFactory.clearCache(providerId)
    }

    /** A profile's category filters on a provider; re-applied to Xtream's flags if it is this device's profile. */
    internal suspend fun applyRemoteCategoryFilters(
        providerId: Long,
        profileId: String,
        filters: CategoryFilters,
    ) {
        filtersStore.set(providerId, profileId, filters, queueForSync = false)
        settingsCache.keys.removeAll { it.first == providerId }
        val entity = dao.getProviderById(providerId) ?: return
        if (profileId == activeProfileId() && entity.type == "XTREAM") {
            withContext(Dispatchers.IO) {
                val database = XtreamDatabase.getInstance(context)
                org.njarasoa.fijerena.core.network.xtream.manager.XtreamCategoryExclusionSync.recompute(
                    database.categoryDao(),
                    providerId,
                    filters,
                )
            }
            MediaProviderFactory.clearCache(providerId)
        }
    }

    /** Only Jellyfin logins are per profile; see [getLogin]. */
    private fun loginProfileId(type: String): String =
        if (type == "JELLYFIN") AppSettings(context).activeProfileId else ProfileEntity.DEFAULT_ID

    // --- Provider Settings ---

    private val json = Json { ignoreUnknownKeys = true }

    private fun activeProfileId(): String = AppSettings(context).activeProfileId

    /**
     * Get the settings for a provider, with the active profile's category filters.
     * Returns default settings if provider not found or settings are invalid.
     */
    suspend fun getProviderSettings(providerId: Long): ProviderSettings {
        val profileId = activeProfileId()
        settingsCache[providerId to profileId]?.let { return it }
        val entity = dao.getProviderById(providerId) ?: return ProviderSettings.DEFAULT
        val stored = parseProviderSettings(entity.providerSettings)
        val settings = stored.copy(categoryFilters = filtersStore.get(providerId, profileId, stored.categoryFilters))
        settingsCache[providerId to profileId] = settings
        return settings
    }

    /**
     * Update the settings for a provider. The category filters are saved as the active profile's;
     * everything else is the provider's, shared by all profiles.
     */
    suspend fun updateProviderSettings(
        providerId: Long,
        settings: ProviderSettings,
    ) {
        val entity = dao.getProviderById(providerId) ?: return
        val profileId = activeProfileId()
        // Leave whatever filters the JSON still holds: until migrateCategoryFiltersToProfiles has
        // run they are the other profiles' fallback.
        val stored = parseProviderSettings(entity.providerSettings)
        val settingsJson = json.encodeToString(settings.copy(categoryFilters = stored.categoryFilters))
        dao.updateProvider(entity.copy(providerSettings = settingsJson))
        filtersStore.set(providerId, profileId, settings.categoryFilters)
        settingsCache.keys.removeAll { it.first == providerId }
        settingsCache[providerId to profileId] = settings
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
        } catch (_: Exception) { // cancellation-ok: non-suspend
            ProviderSettings.DEFAULT
        }
    }

    /**
     * Xtream bakes category filters into the shared catalogue's `excluded` flags, so on a profile
     * switch they're recomputed for every Xtream provider whose filters differ between [fromProfileId]
     * and [toProfileId] — local only, no network. Identical filters (the usual case) cost nothing.
     */
    suspend fun applyCategoryFiltersForSwitch(
        fromProfileId: String,
        toProfileId: String,
    ) {
        dao.getAllProvidersList().filter { it.type == "XTREAM" }.forEach { entity ->
            val fallback = parseProviderSettings(entity.providerSettings).categoryFilters
            val newFilters = filtersStore.get(entity.id, toProfileId, fallback)
            if (filtersStore.get(entity.id, fromProfileId, fallback) == newFilters) return@forEach
            val started = android.os.SystemClock.elapsedRealtime()
            withContext(Dispatchers.IO) {
                val database = XtreamDatabase.getInstance(context)
                org.njarasoa.fijerena.core.network.xtream.manager.XtreamCategoryExclusionSync.recompute(
                    database.categoryDao(),
                    entity.id,
                    newFilters,
                )
            }
            android.util.Log.i("ProfileSwitch", "filters for provider ${entity.id} (${entity.name}): ${android.os.SystemClock.elapsedRealtime() - started} ms")
            // Same as a filter edit: the cached provider and EPG matcher hold category lists and
            // excluded flags from before.
            MediaProviderFactory.clearCache(entity.id)
        }
    }

    /** Every profile's category filters of [fromProviderId] replace [toProviderId]'s. */
    fun copyCategoryFilters(
        fromProviderId: Long,
        toProviderId: Long,
    ) {
        filtersStore.copyProvider(fromProviderId, toProviderId)
        settingsCache.keys.removeAll { it.first == toProviderId }
    }

    /**
     * One-time upgrade to per-profile category filters: each provider's filters, still in its
     * settings JSON (legacy `prefixes` shape included — the decoder normalises it), are copied to
     * every profile that has none of its own, then removed from the JSON. Safe to call on every
     * app start: a no-op once migrated. See docs/plans/20260930_profile-scoped-settings-plan.md.
     */
    suspend fun migrateCategoryFiltersToProfiles() {
        val profileIds = db.profileDao().getAll().map { it.id }
        dao.getAllProvidersList().forEach { entity ->
            val stored = parseProviderSettings(entity.providerSettings)
            if (stored.categoryFilters == CategoryFilters()) return@forEach
            profileIds
                .filterNot { filtersStore.has(entity.id, it) }
                .forEach { filtersStore.set(entity.id, it, stored.categoryFilters) }
            dao.updateProvider(entity.copy(providerSettings = json.encodeToString(stored.copy(categoryFilters = CategoryFilters()))))
        }
        settingsCache.clear()
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
        getProviderPrefs(providerId, ProfileEntity.DEFAULT_ID).edit {
            putString(KEY_PASSWORD, password)
        }
        org.njarasoa.fijerena.core.network.CredentialStoreHealth.clear(context)
    }

    /** Every profile's credentials for the provider: the shared file and each profile's own. */
    private fun clearProviderPassword(providerId: Long) {
        try {
            getProviderPrefs(providerId, ProfileEntity.DEFAULT_ID).edit { clear() }
            encryptedPrefsCache.remove(credsFileName(providerId, ProfileEntity.DEFAULT_ID))
            val profilePrefix = credsFileName(providerId, "")
            java.io
                .File(context.applicationInfo.dataDir, "shared_prefs")
                .listFiles()
                ?.map { it.name.removeSuffix(".xml") }
                ?.filter { it.startsWith(profilePrefix) }
                ?.forEach { name ->
                    encryptedPrefsCache.remove(name)
                    context.deleteSharedPreferences(name)
                }
        } catch (_: Exception) { // cancellation-ok: non-suspend
            // Ignore errors clearing prefs for deleted provider
        }
    }

    private fun clearProviderCache(providerId: Long) {
        try {
            val cacheName = "xtream_cache_$providerId"
            context
                .getSharedPreferences(cacheName, Context.MODE_PRIVATE)
                .edit { clear() }
        } catch (_: Exception) { // cancellation-ok: non-suspend
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
     *
     * Every profile's rows and prefs go with the provider: `watch_state` across all profiles, and
     * each non-Default profile's own `media_cache_<id>_profile_<profileId>` file alongside the
     * shared one (docs/plans/20260929_live-sync-plan.md → User profiles).
     */
    private suspend fun clearProviderWatchState(providerId: Long) {
        XtreamDatabase.getInstance(context).watchStateDao().deleteAllProfiles(providerId)
        XtreamDatabase.getInstance(context).syncTombstoneDao().deleteForProvider(providerId)
        XtreamDatabase.getInstance(context).syncVersionDao().deleteForProvider(providerId)
        try {
            context
                .getSharedPreferences("media_cache_$providerId", Context.MODE_PRIVATE)
                .edit { clear() }
            val profilePrefix = MediaRepository.profileCacheName(providerId, "")
            java.io
                .File(context.applicationInfo.dataDir, "shared_prefs")
                .listFiles()
                ?.map { it.name.removeSuffix(".xml") }
                ?.filter { it.startsWith(profilePrefix) }
                ?.forEach { context.deleteSharedPreferences(it) }
        } catch (_: Exception) { // cancellation-ok: no suspend call in try
            // Ignore errors clearing cache for deleted provider
        }
    }

    @Suppress("DEPRECATION")
    private fun getProviderPrefs(
        providerId: Long,
        profileId: String,
    ): android.content.SharedPreferences =
        encryptedPrefsCache.computeIfAbsent(credsFileName(providerId, profileId)) { fileName ->
            val prefs =
                try {
                    EncryptedSharedPreferences.create(
                        context,
                        fileName,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                    )
                } catch (e: Exception) { // cancellation-ok: non-suspend
                    org.njarasoa.fijerena.core.network.CredentialStoreHealth.markLost(context, fileName, e)
                    context.deleteSharedPreferences(fileName)
                    try {
                        EncryptedSharedPreferences.create(
                            context,
                            fileName,
                            masterKey,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                        )
                    } catch (_: Exception) { // cancellation-ok: non-suspend
                        // Never a plaintext file — see CredentialStoreHealth.InMemoryPrefs.
                        org.njarasoa.fijerena.core.network.CredentialStoreHealth.InMemoryPrefs()
                    }
                }
            prefs
        }
}
