@file:Suppress("DEPRECATION")

package org.njarasoa.fijerena.core.network.provider
import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.sync.SettingsSyncQueue
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.njarasoa.fijerena.core.player.diagnostics.Redact

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

        /**
         * What the orphan sweep may delete: downloaded catalogue only, everything the app fetches
         * again by itself. Never `favorite_state` or `watch_state`: the sweep decides what is
         * orphaned by comparing against `providers.db`, and whenever the two databases disagree
         * (providers.db reset or restored, a provider synced in between the sweep's read and its
         * delete) that inference is wrong and would delete every favourite and every history
         * row. Those go only with [deleteProvider]. See
         * the next level rock solid resilience plan → R-02.
         */
        internal val ORPHAN_SWEEP_TABLES =
            listOf("xtream_streams", "xtream_series", "xtream_episodes", "xtream_categories", "xtream_epg_cache")

        private val CACHE_PREFS_PREFIXES = listOf("media_cache_", "xtream_cache_")
        private const val CREDENTIALS_PREFS_PREFIX = "provider_creds_"

        /**
         * The SharedPreferences files (names with `.xml`) among [fileNames] that belong to no
         * provider in [validProviderIds]. Credential files only when [includeCredentials]: deleting
         * one by inference loses a password for good, so only the user's own Shrink Database does.
         */
        internal fun orphanedPrefsFiles(
            fileNames: List<String>,
            validProviderIds: Set<Long>,
            includeCredentials: Boolean,
        ): List<String> {
            val prefixes = if (includeCredentials) CACHE_PREFS_PREFIXES + CREDENTIALS_PREFS_PREFIX else CACHE_PREFS_PREFIXES
            return fileNames.filter { name ->
                name.endsWith(".xml") &&
                    prefixes.any { prefix ->
                        // substringBefore: a non-Default profile's file is
                        // media_cache_<id>_profile_<profileId> (MediaRepository.profileCacheName).
                        val id =
                            if (name.startsWith(prefix)) {
                                name
                                    .removePrefix(prefix)
                                    .removeSuffix(".xml")
                                    .substringBefore('_')
                                    .toLongOrNull()
                            } else {
                                null
                            }
                        id != null && id !in validProviderIds
                    }
            }
        }

        /**
         * Keyed by (providerId, profileId): the category filters in each entry are that profile's.
         * One for the whole process, shared by every instance: sync, Settings and the import each
         * build their own ProviderRepository, and with a cache per instance a write through one
         * left the others — `AppContainer.providerRepository` among them — serving the old
         * settings until a restart. See the next level rock solid resilience plan
         * → R-06 step 1.
         */
        private val settingsCache = java.util.concurrent.ConcurrentHashMap<Pair<Long, String>, ProviderSettings>()

        /**
         * Bumped on each invalidation of [settingsCache]. A read that misses the cache stores its
         * result only when no invalidation came while it read the database, so a slow read cannot
         * put old settings back after a write (thread races plan, 7).
         */
        private val settingsGeneration =
            java.util.concurrent.atomic
                .AtomicLong()

        private fun invalidateSettings(predicate: (Pair<Long, String>) -> Boolean) {
            settingsGeneration.incrementAndGet()
            settingsCache.keys.removeAll(predicate)
        }

        /**
         * The sources a profile with [keys] (its `providerKey`s, null for none stored) can use, in
         * [providers]' order: those of its keys this device has — a key with no source here is
         * skipped. Nothing else: there is no device or current source (multi-source plan, P3).
         */
        fun resolveProfileSources(
            providers: List<ProviderEntity>,
            keys: Set<String>?,
        ): List<ProviderEntity> = providers.filter { keys?.contains(it.providerKey) == true }

        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_JELLYFIN_TOKEN = "jellyfin_token"
        private const val KEY_JELLYFIN_USER_ID = "jellyfin_user_id"

        /** `extra_password_<username>`: an Xtream extra login's password, see [getSourceLogins]. */
        private const val KEY_EXTRA_PASSWORD_PREFIX = "extra_password_"

        /**
         * The encrypted credentials file for [providerId]: `provider_creds_<id>` holds the source's
         * own login, every type's. Older builds also kept a Jellyfin login per profile in
         * `provider_creds_<id>_profile_<profileId>`; [moveJellyfinLoginsToSources] empties those.
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

        /**
         * Whether a source of [type] can connect without an Expert signing it in first: always,
         * except a Jellyfin server with neither a username nor a saved session.
         */
        internal fun hasSourceLogin(
            type: String,
            username: String,
            hasSession: Boolean,
        ): Boolean = type != "JELLYFIN" || username.isNotBlank() || hasSession

        /** A profile that had its own Jellyfin login on a source before the upgrade. */
        internal data class LoginOwner(
            val profileId: String,
            val expert: Boolean,
            val createdAt: Long,
            /** Its last watch change on this device, 0 for none. */
            val lastUsedAt: Long,
        )

        /**
         * Which of [owners]' logins a Jellyfin source keeps on upgrade (JF-D1): the Expert profile
         * in use, else the oldest Expert, else the most recently used profile — the one in use
         * first, then the latest watch change. Null when nobody had one.
         */
        internal fun chooseHouseholdLogin(
            owners: List<LoginOwner>,
            activeProfileId: String,
        ): String? {
            val active = owners.firstOrNull { it.profileId == activeProfileId }
            val experts = owners.filter { it.expert }
            val chosen =
                when {
                    active != null && active.expert -> active
                    experts.isNotEmpty() -> experts.minWith(compareBy<LoginOwner>({ it.createdAt }, { it.profileId }))
                    active != null -> active
                    else -> owners.maxWithOrNull(compareBy<LoginOwner>({ it.lastUsedAt }, { it.profileId }))
                }
            return chosen?.profileId
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

    private val filtersStore = CategoryFiltersStore(context)

    fun getAllProviders(): Flow<List<ProviderEntity>> = dao.getAllProviders()

    /** Every source's badge short name by source id ([SourceShortNames]), following every change. */
    fun sourceShortNames(): Flow<Map<Long, String>> = dao.getAllProviders().map { SourceShortNames.of(shortNameSources(it)) }

    private fun shortNameSources(providers: List<ProviderEntity>) =
        providers.map { SourceShortNames.Source(it.id, it.name, parseProviderSettings(it.providerSettings).shortName) }

    /**
     * Saves [shortName] as [providerId]'s badge short name (blank: automatic), and nothing else, so
     * no category filter is worked out again. False, and nothing saved, when another source has it
     * or it is too long.
     */
    suspend fun setShortName(
        providerId: Long,
        shortName: String,
    ): Boolean {
        val entity = dao.getProviderById(providerId) ?: return false
        val all = dao.getAllProviders().first()
        if (!SourceShortNames.isFree(shortName, providerId, shortNameSources(all))) return false
        val stored = parseProviderSettings(entity.providerSettings)
        dao.updateProvider(entity.copy(providerSettings = json.encodeToString(stored.copy(shortName = shortName.trim()))))
        invalidateSettings { it.first == providerId }
        return true
    }

    suspend fun getAllProvidersList(): List<ProviderEntity> = dao.getAllProvidersList()

    suspend fun getProviderById(id: Long): ProviderEntity? = dao.getProviderById(id)

    suspend fun getProviderCount(): Int = dao.getProviderCount()

    /**
     * The sources [profileId] can use that this device has (see [resolveProfileSources]),
     * re-emitted when a source is added, changed or deleted, or the profile's sources change.
     */
    fun profileSources(profileId: String): Flow<List<ProviderEntity>> =
        combine(dao.getAllProviders(), AppSettings(context).observeProfileSourceKeys(profileId), ::resolveProfileSources)

    /**
     * [profileSources], re-emitted only when which sources the profile has changes (one turned on or off, added, deleted or renamed), not on every
     * write to a source's row (a sync's timestamps): what the merged lists reload on.
     */
    fun profileSourceSet(profileId: String): Flow<List<ProviderEntity>> =
        profileSources(profileId).distinctUntilChanged { old, new ->
            old.map { Triple(it.id, it.type, it.name) } == new.map { Triple(it.id, it.type, it.name) }
        }

    /** [profileSources] once, for the profile this device uses unless told otherwise. */
    suspend fun profileSourcesList(profileId: String = activeProfileId()): List<ProviderEntity> =
        resolveProfileSources(dao.getAllProvidersList(), AppSettings(context).profileSourceKeys(profileId))

    /**
     * See [AppSettings.seedProfileSourcesOnce]; at startup, and before a source is added. The
     * source the device was on is the one an older build marked `isActive`
     * ([ProviderDao.legacyActiveProviderKey]).
     */
    suspend fun seedProfileSourcesOnce() {
        val providers = dao.getAllProvidersList()
        AppSettings(context).seedProfileSourcesOnce(
            db.profileDao().getAll().map { it.id },
            providers.map { it.providerKey }.toSet(),
            dao.legacyActiveProviderKey(),
        )
    }

    /** See [AppSettings.movePerSourcePlaybackOnce]; at startup, after the profiles' sources are seeded. */
    suspend fun movePerSourcePlaybackOnce() {
        val providers = dao.getAllProvidersList()
        AppSettings(context).movePerSourcePlaybackOnce(providers.map { parseProviderSettings(it.providerSettings).watchHistorySize })
    }

    /** [providerId]'s guide sources, as they change (Edit Source's Guide sources row). */
    fun getGuideSources(providerId: Long): Flow<List<EpgSourceEntity>> = db.epgSourceDao().getSourcesForProvider(providerId)

    /**
     * Add a new provider. Stores the password in a per-provider encrypted prefs file. The new
     * source is on for the profile in use (multi-source plan, P1); nothing else changes.
     */
    suspend fun addProvider(
        name: String,
        url: String,
        username: String,
        password: String,
        type: String = "XTREAM",
        config: String = "",
        initialSettings: ProviderSettings = ProviderSettings.DEFAULT,
    ): Long {
        // Before the insert, so the upgrade gives profiles their old source, not this one.
        seedProfileSourcesOnce()
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
            )
        val id = dao.insertProvider(entity)
        // One login per source, Jellyfin included: every profile that has the source uses it.
        savePassword(id, password)
        if (initialSettings.categoryFilters != CategoryFilters()) {
            filtersStore.set(id, activeProfileId(), initialSettings.categoryFilters)
        }
        // A new source is on for the profile that added it only (multi-source plan, P1).
        AppSettings(context).addProfileSource(activeProfileId(), entity.providerKey)
        return id
    }

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
        dao.updateProvider(
            existing.copy(
                name = name,
                url = url,
                username = username,
                type = effectiveType,
                config = config ?: existing.config,
            ),
        )
        savePassword(id, password)
        // The password lives outside the row, so no trigger sees it change: the login is part of
        // the provider record, for every type.
        SettingsSyncQueue.provider(context, id)
        // If Jellyfin credentials changed, discard the cached session token so the
        // provider re-authenticates with the new username/password on next use.
        if (effectiveType == "JELLYFIN") {
            getProviderPrefs(id, ProfileEntity.DEFAULT_ID).edit {
                remove(KEY_JELLYFIN_TOKEN)
                    .remove(KEY_JELLYFIN_USER_ID)
            }
        }
        MediaProviderFactory.providerChanged(id)
    }

    /**
     * Delete a provider and clean up its encrypted prefs and cache. The deletion is recorded for
     * live sync; its favourite and history tombstones go with it, the provider's own covers them.
     * [fromRemote]: another device deleted it. Its key may stay in profiles' sources settings, which
     * skip a source this device doesn't have.
     */
    suspend fun deleteProvider(
        id: Long,
        fromRemote: Boolean = false,
    ) {
        val entity = dao.getProviderById(id)
        if (entity != null) {
            // Cleared at the end: a deletion interrupted below leaves orphaned catalogue rows for
            // the next start's sweep (sweepOrphanedCatalogData).
            AppSettings(context).orphanSweepPending = true
            dao.deleteProviderRecordingTombstone(entity)
            db.settingsSyncDao().deleteForProviderKey(entity.providerKey)
            deleteProviderEpgSources(id, recordTombstones = !fromRemote)
            clearProviderPassword(id)
            clearProviderCache(id)
            clearProviderWatchState(id)
            clearProviderCatalog(id)
            invalidateSettings { it.first == id }
            filtersStore.removeProvider(id)
            MediaProviderFactory.providerChanged(id)
            AppSettings(context).orphanSweepPending = false
        }
    }

    /**
     * `xtream_v2.db`'s catalog rows (streams, series, episodes, categories, favorites, the
     * per-stream EPG payload cache) are keyed by `providerId` but live in the same app-wide
     * database file as every other provider's rows, so no SQL cascade reaches them from
     * `providers.db` — same reasoning as [clearProviderWatchState], delete by hand.
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
     * as it goes — which is also why no `VACUUM` follows (see
     * the rock solid stability resilience plan → F-35). [where] is built from
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
     * Sweeps `xtream_v2.db` for downloaded catalogue rows whose `providerId` doesn't match any
     * provider that exists right now — drift from before [clearProviderCatalog] ran on every
     * deletion, or a deletion interrupted midway. Never runs if providers cannot be loaded.
     *
     * Deletes only [ORPHAN_SWEEP_TABLES] and cache SharedPreferences: never favourites or watch
     * history, never sync state. [userRequested] (Settings → Shrink Database) also removes orphaned
     * credential files and EPG sources, and VACUUMs. The provider list is read again for each step,
     * so a provider added meanwhile (by the user or live sync) loses at most a few catalogue rows
     * it fetches again. See the next level rock solid resilience plan → R-02.
     */
    suspend fun pruneOrphanedCatalogData(userRequested: Boolean = false): OrphanedDataPruneResult {
        val result =
            withContext(Dispatchers.IO) {
                val startMs = System.currentTimeMillis()
                if (validProviderIds().isEmpty()) {
                    OrphanedDataPruneResult(0L, 0L)
                } else {
                    val db = XtreamDatabase.getInstance(context)
                    val dbFile = context.getDatabasePath("xtream_v2.db")
                    val walFile = context.getDatabasePath("xtream_v2.db-wal")
                    val sizeBeforeBytes =
                        (if (dbFile.exists()) dbFile.length() else 0L) + (if (walFile.exists()) walFile.length() else 0L)

                    val rowsRemoved =
                        ORPHAN_SWEEP_TABLES.sumOf { table ->
                            deleteInBatches(db, table, "providerId NOT IN (${validProviderIds().joinToString()})")
                        }
                    if (rowsRemoved > 0) db.invalidationTracker.refreshAsync()

                    cleanupOrphanedPrefs(validProviderIds(), includeCredentials = userRequested)

                    if (userRequested) {
                        val sourceDao = this@ProviderRepository.db.epgSourceDao()
                        val allSourceProviderIds = sourceDao.getAllSourcesOnce().map { it.providerId }.toSet()
                        // No tombstones: an orphan source's provider is gone, and that provider's
                        // own tombstone already covers it on the other devices (see F-11).
                        for (orphanId in allSourceProviderIds - validProviderIds()) {
                            deleteProviderEpgSources(orphanId, recordTombstones = false)
                        }

                        // auto_vacuum = FULL already returns freed pages to the file as rows go, so
                        // a VACUUM here only defragments, and in WAL mode it rewrites the whole
                        // database into the WAL first — a spike as big as the database itself
                        // (measured: 258 MB) that a low-storage TV may not have room for. See
                        // the rock solid stability resilience plan → F-35.
                        try {
                            val sdb = db.openHelper.writableDatabase
                            sdb.execSQL("VACUUM")
                            sdb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.w("ProviderRepository", "VACUUM during orphan prune failed", e)
                            CrashLog.record("Shrink database: VACUUM failed", e)
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

    /**
     * [pruneOrphanedCatalogData] for the automatic callers — app start and the EPG worker — which
     * must never fail on its account: a full disk or a locked database is recorded and skipped.
     * [onlyIfPending] (app start) runs it only after an interrupted provider deletion: every other
     * start skipped a full scan of the catalogue tables competing with Home's first queries. See
     * the next level rock solid resilience plan → R-03, R-17.
     */
    suspend fun sweepOrphanedCatalogData(onlyIfPending: Boolean) {
        val appSettings = AppSettings(context)
        if (!onlyIfPending || appSettings.orphanSweepPending) {
            try {
                pruneOrphanedCatalogData(userRequested = false)
                appSettings.orphanSweepPending = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("ProviderRepository", "Orphan sweep failed; skipped", e)
                org.njarasoa.fijerena.core.player.diagnostics.CrashLog
                    .record("orphan sweep", e)
            }
        }
    }

    private suspend fun validProviderIds(): Set<Long> = dao.getAllProvidersList().map { it.id }.toSet()

    private fun cleanupOrphanedPrefs(
        validProviderIds: Set<Long>,
        includeCredentials: Boolean,
    ) {
        try {
            val prefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
            val names = prefsDir.listFiles()?.map { it.name }.orEmpty()
            orphanedPrefsFiles(names, validProviderIds, includeCredentials).forEach { java.io.File(prefsDir, it).delete() }
        } catch (e: Exception) {
            // cancellation-ok: non-suspend
            android.util.Log.w("ProviderRepository", "Failed cleaning up orphaned prefs", e)
        }
    }

    /**
     * EPG sources belong to a single provider, and their indexed channels/programmes live in a
     * separate database ([EpgIndexDatabase]), so no SQL cascade is possible - delete both by hand.
     *
     * EPG source records carry no provider tag, so the server's provider cascade doesn't reach
     * them: a deletion made here must queue a tombstone per source ([recordTombstones]). Applying
     * another device's provider deletion must not — that device already sent them, and queueing
     * them again pushed every one back. See
     * the rock solid stability resilience plan → F-11.
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

    suspend fun updateSyncStats(
        id: Long,
        timestamp: Long,
        durationMs: Long,
        error: String?,
        inserted: Int? = null,
        updated: Int? = null,
        deleted: Int? = null,
    ) {
        // Masked before it is kept: shown in Edit Source and Device info.
        dao.updateSyncStats(id, timestamp, durationMs, error?.let(Redact::text), inserted, updated, deleted)
    }

    /** The stored password of a source's login (an Xtream source's main one). */
    fun getPassword(providerId: Long): String? = getProviderPrefs(providerId, ProfileEntity.DEFAULT_ID).getString(KEY_PASSWORD, null)

    /** A username and password to connect with. Empty strings when there is none yet. */
    data class Login(
        val username: String,
        val password: String,
    )

    /**
     * The login of [entity], the same for every profile: `providers.username` plus the password in
     * `provider_creds_<id>`. A Jellyfin source too: every profile that has it shares its one
     * Jellyfin user, with that user's progress, watched marks and favourites.
     */
    fun getLogin(entity: ProviderEntity): Login =
        Login(entity.username, getProviderPrefs(entity.id, ProfileEntity.DEFAULT_ID).getString(KEY_PASSWORD, null).orEmpty())

    /**
     * An Xtream source's main login and its extra ones, in order. An extra login whose password
     * this device doesn't have (an import, a duplicate) comes with an empty password.
     */
    fun getSourceLogins(entity: ProviderEntity): SourceLogins {
        val prefs = getProviderPrefs(entity.id, ProfileEntity.DEFAULT_ID)
        val extras =
            parseProviderSettings(entity.providerSettings).extraLogins.map { username ->
                Login(username, prefs.getString(extraPasswordKey(username), null).orEmpty())
            }
        return SourceLogins(Login(entity.username, prefs.getString(KEY_PASSWORD, null).orEmpty()), extras)
    }

    /** The extra logins' passwords this device has, by username — for live sync. */
    fun getExtraPasswords(entity: ProviderEntity): Map<String, String> =
        getSourceLogins(entity).extras.filter { it.password.isNotEmpty() }.associate { it.username to it.password }

    /**
     * Stores [logins] as [providerId]'s: the main one in the row and `provider_creds_<id>`, the
     * extra usernames in the settings JSON, their passwords beside the main one. Queued for live
     * sync; the cached provider is dropped so the next use connects with the new main login (the
     * automatic guide source follows it on that login, see `reconcileAutoXmltvSource`).
     */
    suspend fun saveSourceLogins(
        providerId: Long,
        logins: SourceLogins,
    ) {
        val entity = dao.getProviderById(providerId) ?: return
        val stored = parseProviderSettings(entity.providerSettings)
        val settingsJson = json.encodeToString(stored.copy(extraLogins = logins.extras.map { it.username }))
        dao.updateProvider(entity.copy(username = logins.main.username, providerSettings = settingsJson))
        writeExtraPasswords(providerId, logins.main.password, logins.extras.associate { it.username to it.password })
        SettingsSyncQueue.provider(context, providerId)
        invalidateSettings { it.first == providerId }
        MediaProviderFactory.providerChanged(providerId)
    }

    /**
     * The main password plus exactly [extras] as the extra passwords: any other extra password
     * in the file is removed. A blank password isn't stored.
     */
    private fun writeExtraPasswords(
        providerId: Long,
        mainPassword: String,
        extras: Map<String, String>,
    ) {
        val prefs = getProviderPrefs(providerId, ProfileEntity.DEFAULT_ID)
        prefs.edit {
            putString(KEY_PASSWORD, mainPassword)
            prefs.all.keys
                .filter { it.startsWith(KEY_EXTRA_PASSWORD_PREFIX) }
                .forEach { remove(it) }
            extras.filterValues { it.isNotEmpty() }.forEach { (username, password) -> putString(extraPasswordKey(username), password) }
        }
        org.njarasoa.fijerena.core.network.CredentialStoreHealth
            .clear(context)
    }

    private fun extraPasswordKey(username: String) = KEY_EXTRA_PASSWORD_PREFIX + username

    /** Whether [entity] can connect without an Expert signing it in first ([hasSourceLogin]). */
    fun hasLogin(entity: ProviderEntity): Boolean =
        hasSourceLogin(
            entity.type,
            entity.username,
            entity.type == "JELLYFIN" && getProviderPrefs(entity.id, ProfileEntity.DEFAULT_ID).getString(KEY_JELLYFIN_TOKEN, null) != null,
        )

    /**
     * Persist a Jellyfin session token (from Quick Connect or normal auth) for the source on this
     * device, so the provider can restore it on next launch without re-authenticating.
     */
    fun saveJellyfinSession(
        providerId: Long,
        token: String,
        userId: String,
    ) {
        getProviderPrefs(providerId, ProfileEntity.DEFAULT_ID).edit {
            putString(KEY_JELLYFIN_TOKEN, token)
                .putString(KEY_JELLYFIN_USER_ID, userId)
        }
    }

    /**
     * Quick Connect sign-in to an existing Jellyfin source, for the whole household: the username
     * it reported plus the session, and no password — Quick Connect never has one.
     */
    suspend fun saveQuickConnectLogin(
        providerId: Long,
        username: String,
        token: String,
        userId: String,
    ) {
        dao.getProviderById(providerId)?.let { dao.updateProvider(it.copy(username = username)) }
        getProviderPrefs(providerId, ProfileEntity.DEFAULT_ID).edit { remove(KEY_PASSWORD) }
        SettingsSyncQueue.provider(context, providerId)
        saveJellyfinSession(providerId, token, userId)
        MediaProviderFactory.providerChanged(providerId)
    }

    /**
     * One-time upgrade to one Jellyfin login per source. Older builds kept one per profile: the
     * Default profile's in the row and `provider_creds_<id>`, every other profile's in its own
     * file. Each Jellyfin source keeps one of them as its login ([chooseHouseholdLogin], JF-D1),
     * with this device's session for it, and every per-profile file is then deleted: the decided
     * move, not an inference. Safe on every start: a no-op once no per-profile file is left.
     */
    suspend fun moveJellyfinLoginsToSources() {
        val sources =
            dao
                .getAllProvidersList()
                .filter { it.type == "JELLYFIN" }
                .associateWith { profilesWithOwnLogin(it.id) }
                .filterValues { it.isNotEmpty() }
        if (sources.isNotEmpty()) {
            val settings = AppSettings(context)
            val profiles = db.profileDao().getAll()
            val lastUsed =
                XtreamDatabase
                    .getInstance(context)
                    .watchStateDao()
                    .lastChangeByProfile()
                    .associate { it.profileId to it.lastAt }
            sources.forEach { (entity, own) ->
                val owners =
                    profiles
                        .filter { profile ->
                            if (profile.id == ProfileEntity.DEFAULT_ID) {
                                entity.username.isNotBlank()
                            } else {
                                profile.id in own &&
                                    getProviderPrefs(entity.id, profile.id).getString(KEY_USERNAME, null).orEmpty().isNotBlank()
                            }
                        }.map { LoginOwner(it.id, settings.devMode(it.id), it.createdAt, lastUsed[it.id] ?: 0L) }
                val chosen = chooseHouseholdLogin(owners, settings.activeProfileId)
                if (chosen != null && chosen != ProfileEntity.DEFAULT_ID) moveLoginToSource(entity, chosen)
                own.forEach { profileId ->
                    val name = credsFileName(entity.id, profileId)
                    encryptedPrefsCache.remove(name)
                    context.deleteSharedPreferences(name)
                }
                MediaProviderFactory.providerChanged(entity.id)
            }
        }
    }

    /** [profileId]'s own Jellyfin login on [entity], with this device's session, becomes the source's. */
    private suspend fun moveLoginToSource(
        entity: ProviderEntity,
        profileId: String,
    ) {
        val from = getProviderPrefs(entity.id, profileId)
        getProviderPrefs(entity.id, ProfileEntity.DEFAULT_ID).edit {
            listOf(KEY_PASSWORD, KEY_JELLYFIN_TOKEN, KEY_JELLYFIN_USER_ID).forEach { key ->
                val value = from.getString(key, null)
                if (value == null) remove(key) else putString(key, value)
            }
        }
        org.njarasoa.fijerena.core.network.CredentialStoreHealth
            .clear(context)
        dao.updateProvider(entity.copy(username = from.getString(KEY_USERNAME, null).orEmpty()))
        // The password lives outside the row: the household's other devices get it with the record.
        SettingsSyncQueue.provider(context, entity.id)
    }

    // --- Live sync: changes received from another device (phase 5) ---
    // These write without queueing: the caller (SyncApplier) holds the sync clock's `applying` flag
    // for the database writes, and prefs writes here simply skip SettingsSyncQueue.
    // They clear only the factory's provider: they run inside SyncApplier's transaction, so the
    // cached MediaRepository is dropped after the pass instead, once the change is committed
    // (SyncApplier.Result.providerChangedIds → SyncEngine.Listener.onProvidersChanged).

    /** Profiles with a Jellyfin login of their own on [providerId] — Default's is the row's. */
    private fun profilesWithOwnLogin(providerId: Long): List<String> {
        val prefix = "${credsFileName(providerId, ProfileEntity.DEFAULT_ID)}_profile_"
        return java.io
            .File(context.applicationInfo.dataDir, "shared_prefs")
            .listFiles()
            ?.map { it.name.removeSuffix(".xml") }
            ?.filter { it.startsWith(prefix) }
            ?.map { it.removePrefix(prefix) }
            .orEmpty()
    }

    /** Adds or updates the provider named [providerKey]; returns its local id. */
    internal suspend fun applyRemoteProvider(
        providerKey: String,
        remote: org.njarasoa.fijerena.core.network.sync.SyncPayloads.Provider,
    ): Long {
        val existing = db.settingsSyncDao().providerByKey(providerKey)
        val jellyfin = remote.type == "JELLYFIN"
        // A Jellyfin record with no username comes from an older build whose Default profile had
        // no login: it says nothing about the source's login, so this device's stays.
        val keepLogin = jellyfin && existing != null && remote.username.isBlank()
        val id =
            if (existing != null) {
                dao.updateProvider(
                    existing.copy(
                        name = remote.name,
                        url = remote.url,
                        username = if (keepLogin) existing.username else remote.username,
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
                        providerKey = providerKey,
                    ),
                )
            }
        if (!jellyfin) {
            remote.password?.let { savePassword(id, it) }
        } else if (!keepLogin &&
            (existing?.username != remote.username || (remote.password != null && remote.password != getPassword(id)))
        ) {
            // The household's Jellyfin login changed: this device signs in again with it. A new
            // user with no password (Quick Connect) drops the old one; the same user sent with
            // none keeps the password this device has (no change, so not here).
            writeJellyfinLogin(id, remote.password)
        }
        // Null: a record from a version without extra logins — keep what this device has.
        remote.extraPasswords?.takeIf { remote.type == "XTREAM" }?.let { extras ->
            val mainPassword = getPassword(id).orEmpty()
            writeExtraPasswords(id, mainPassword, extras.filterKeys { it in parseProviderSettings(remote.providerSettings).extraLogins })
        }
        invalidateSettings { it.first == id }
        MediaProviderFactory.clearCache(id)
        return id
    }

    /**
     * A profile's own Jellyfin login sent by an older build, taken as the source's login: the
     * caller checks the source has none ([hasLogin]). Drops the session so the next use signs in
     * with it — sessions are per device and never synced.
     */
    internal suspend fun applyRemoteLogin(
        providerId: Long,
        login: org.njarasoa.fijerena.core.network.sync.SyncPayloads.Login,
    ) {
        dao.getProviderById(providerId)?.let { dao.updateProvider(it.copy(username = login.username)) }
        writeJellyfinLogin(providerId, login.password)
        MediaProviderFactory.clearCache(providerId)
    }

    /** [password] (none: removed) as the Jellyfin source's, dropping this device's session. */
    private fun writeJellyfinLogin(
        providerId: Long,
        password: String?,
    ) {
        getProviderPrefs(providerId, ProfileEntity.DEFAULT_ID).edit {
            if (password == null) remove(KEY_PASSWORD) else putString(KEY_PASSWORD, password)
            remove(KEY_JELLYFIN_TOKEN).remove(KEY_JELLYFIN_USER_ID)
        }
        if (password != null) {
            org.njarasoa.fijerena.core.network.CredentialStoreHealth
                .clear(context)
        }
    }

    /** A profile's category filters on a provider; re-applied to Xtream's flags if it is this device's profile. */
    internal suspend fun applyRemoteCategoryFilters(
        providerId: Long,
        profileId: String,
        filters: CategoryFilters,
    ) {
        filtersStore.set(providerId, profileId, filters, queueForSync = false)
        invalidateSettings { it.first == providerId }
        val entity = dao.getProviderById(providerId) ?: return
        if (profileId == activeProfileId() && entity.type == "XTREAM") {
            withContext(Dispatchers.IO) {
                val database = XtreamDatabase.getInstance(context)
                org.njarasoa.fijerena.core.network.xtream.manager.XtreamCategoryExclusionSync.recompute(
                    database.categoryDao(),
                    database.streamDao(),
                    database.seriesDao(),
                    providerId,
                    filtersStore.get(providerId, profileId),
                )
            }
            MediaProviderFactory.clearCache(providerId)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun activeProfileId(): String = AppSettings(context).activeProfileId

    /**
     * Get the settings for a provider, with the active profile's category filters.
     * Returns default settings if provider not found or settings are invalid.
     */
    suspend fun getProviderSettings(providerId: Long): ProviderSettings {
        val profileId = activeProfileId()
        val key = providerId to profileId
        settingsCache[key]?.let { return it }
        val generation = settingsGeneration.get()
        val entity = dao.getProviderById(providerId) ?: return ProviderSettings.DEFAULT
        val stored = parseProviderSettings(entity.providerSettings)
        val settings = stored.copy(categoryFilters = filtersStore.get(providerId, profileId, stored.categoryFilters))
        if (settingsGeneration.get() == generation) {
            settingsCache[key] = settings
            // An invalidation between the check and the put: take the old settings out again.
            if (settingsGeneration.get() != generation) settingsCache.remove(key, settings)
        }
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
        // run they are the other profiles' fallback. The extra logins change only through
        // saveSourceLogins: a screen holding settings read before a login was added (here or by
        // live sync) must not drop it. The short name likewise changes only through setShortName.
        val stored = parseProviderSettings(entity.providerSettings)
        val settingsJson =
            json.encodeToString(
                settings.copy(categoryFilters = stored.categoryFilters, extraLogins = stored.extraLogins, shortName = stored.shortName),
            )
        dao.updateProvider(entity.copy(providerSettings = settingsJson))
        filtersStore.set(providerId, profileId, settings.categoryFilters)
        // Read back: a child profile's show only what Parental controls let through (CategoryFilters.child).
        val applied = filtersStore.get(providerId, profileId)
        invalidateSettings { it.first == providerId }
        settingsCache[providerId to profileId] = settings.copy(categoryFilters = applied)
        MediaProviderFactory.providerChanged(providerId)

        // Recompute category-filter exclusion flags immediately, purely locally (no network) —
        // lets a filter change take effect right away instead of waiting for the next sync.
        // Runs on IO: the DAO calls inside recompute() are synchronous, non-suspend Room queries.
        if (entity.type == "XTREAM") {
            withContext(Dispatchers.IO) {
                val database =
                    org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
                        .getInstance(context)
                org.njarasoa.fijerena.core.network.xtream.manager.XtreamCategoryExclusionSync.recompute(
                    database.categoryDao(),
                    database.streamDao(),
                    database.seriesDao(),
                    providerId,
                    applied,
                )
            }
        }
    }

    /** [profileId]'s category filters on [providerId] — any profile, not only the one in use. */
    suspend fun getCategoryFilters(
        providerId: Long,
        profileId: String,
    ): CategoryFilters {
        val entity = dao.getProviderById(providerId) ?: return CategoryFilters()
        return filtersStore.get(providerId, profileId, parseProviderSettings(entity.providerSettings).categoryFilters)
    }

    /**
     * Saves [profileId]'s category filters on [providerId] (from the profile's page). Only the
     * profile in use has them applied now — Xtream's `excluded` flags follow that profile alone;
     * another profile's apply when this device switches to it ([applyCategoryFiltersForSwitch]).
     */
    suspend fun setCategoryFilters(
        providerId: Long,
        profileId: String,
        filters: CategoryFilters,
    ) {
        val entity = dao.getProviderById(providerId) ?: return
        filtersStore.set(providerId, profileId, filters)
        invalidateSettings { it.first == providerId }
        if (profileId != activeProfileId()) return
        // Drop the cached provider and repository, and recompute the hidden-category flags locally.
        MediaProviderFactory.providerChanged(providerId)
        if (entity.type == "XTREAM") {
            withContext(Dispatchers.IO) {
                val database = XtreamDatabase.getInstance(context)
                org.njarasoa.fijerena.core.network.xtream.manager.XtreamCategoryExclusionSync.recompute(
                    database.categoryDao(),
                    database.streamDao(),
                    database.seriesDao(),
                    providerId,
                    filtersStore.get(providerId, profileId),
                )
            }
        }
    }

    /**
     * Stores "Provides a guide" ([ProviderSettings.providesGuide]) alone, leaving the rest of the
     * settings as stored; [byUser] marks it the viewer's choice, which detection then leaves alone.
     * Synced with the provider row. It doesn't change the source itself, so the cached provider stays.
     */
    suspend fun setProvidesGuide(
        providerId: Long,
        enabled: Boolean,
        byUser: Boolean,
    ) {
        val entity = dao.getProviderById(providerId)
        if (entity != null) {
            val stored = parseProviderSettings(entity.providerSettings)
            val updated = stored.copy(providesGuide = enabled, providesGuideSetByUser = stored.providesGuideSetByUser || byUser)
            dao.updateProvider(entity.copy(providerSettings = json.encodeToString(updated)))
            invalidateSettings { it.first == providerId }
        }
    }

    private fun parseProviderSettings(settingsJson: String): ProviderSettings {
        if (settingsJson.isBlank() || settingsJson == "{}") return ProviderSettings.DEFAULT
        return try {
            json.decodeFromString<ProviderSettings>(settingsJson)
        } catch (_: Exception) {
            // cancellation-ok: non-suspend
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
                    database.streamDao(),
                    database.seriesDao(),
                    entity.id,
                    newFilters,
                )
            }
            android.util.Log.i(
                "ProfileSwitch",
                "filters for provider ${entity.id} (${entity.name}): ${android.os.SystemClock.elapsedRealtime() - started} ms",
            )
            // Same as a filter edit: the cached provider and EPG matcher hold category lists and
            // excluded flags from before.
            MediaProviderFactory.clearCache(entity.id)
        }
    }

    /**
     * [profileId] became or stopped being a child profile ([AppSettings.isChildProfile]): its
     * filters now read with or without the adult categories, so if it is this device's profile
     * every Xtream source's hidden categories are recomputed — local only, no network.
     */
    suspend fun childProfileChanged(profileId: String) {
        invalidateSettings { it.second == profileId }
        if (profileId != activeProfileId()) return
        dao.getAllProvidersList().filter { it.type == "XTREAM" }.forEach { entity ->
            withContext(Dispatchers.IO) {
                val database = XtreamDatabase.getInstance(context)
                org.njarasoa.fijerena.core.network.xtream.manager.XtreamCategoryExclusionSync.recompute(
                    database.categoryDao(),
                    database.streamDao(),
                    database.seriesDao(),
                    entity.id,
                    filtersStore.get(entity.id, profileId, parseProviderSettings(entity.providerSettings).categoryFilters),
                )
            }
            MediaProviderFactory.clearCache(entity.id)
        }
    }

    /**
     * At startup: stores the Parental controls lists once ([AppSettings.seedParentalListsOnce]).
     * When it stores them, the hidden categories follow them now, not at the next switch.
     */
    suspend fun seedParentalListsOnce() {
        if (AppSettings(context).seedParentalListsOnce()) parentalListsChanged()
    }

    /** The admin changed a Parental controls list: stored, sent to the other devices, applied now. */
    suspend fun setParentalList(
        list: ParentalList,
        entries: List<String>,
    ) {
        AppSettings(context).setParentalList(list, entries)
        parentalListsChanged()
    }

    /**
     * A Parental controls list changed, here or on another device: when the profile in use is a
     * child's, every Xtream source's hidden categories are recomputed — local only, no network.
     */
    suspend fun parentalListsChanged() {
        val profileId = activeProfileId()
        if (AppSettings(context).isChildProfile(profileId)) childProfileChanged(profileId)
    }

    /** Every profile's category filters of [fromProviderId] replace [toProviderId]'s. */
    fun copyCategoryFilters(
        fromProviderId: Long,
        toProviderId: Long,
    ) {
        filtersStore.copyProvider(fromProviderId, toProviderId)
        invalidateSettings { it.first == toProviderId }
    }

    /**
     * One-time upgrade to per-profile category filters: each provider's filters, still in its
     * settings JSON (legacy `prefixes` shape included — the decoder normalises it), are copied to
     * every profile that has none of its own, then removed from the JSON. Safe to call on every
     * app start: a no-op once migrated. See the profile scoped settings plan.
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
        invalidateSettings { true }
    }

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

    private fun savePassword(
        providerId: Long,
        password: String,
    ) {
        getProviderPrefs(providerId, ProfileEntity.DEFAULT_ID).edit {
            putString(KEY_PASSWORD, password)
        }
        org.njarasoa.fijerena.core.network.CredentialStoreHealth
            .clear(context)
    }

    /** The provider's credentials: its own file and any per-profile one an older build left. */
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
        } catch (_: Exception) {
            // cancellation-ok: non-suspend
            // Ignore errors clearing prefs for deleted provider
        }
    }

    private fun clearProviderCache(providerId: Long) {
        try {
            val cacheName = "xtream_cache_$providerId"
            context
                .getSharedPreferences(cacheName, Context.MODE_PRIVATE)
                .edit { clear() }
        } catch (_: Exception) {
            // cancellation-ok: non-suspend
            // Ignore errors clearing cache for deleted provider
        }
    }

    /**
     * `watch_state` lives in `XtreamDatabase` (one app-wide file) while provider definitions live
     * in `SettingsDatabase`, so no SQL cascade reaches it — delete by hand. Not Xtream-specific:
     * `MediaRepository` backs SMB, Local and Remote M3U too, and all of them write rows keyed by
     * this `providerId`. `media_cache_$providerId` (favorites, favorite categories) is cleared in
     * the same pass, or that prefs file would leak on every deletion.
     * See the watch state durable storage plan.
     *
     * Every profile's rows and prefs go with the provider: `watch_state` across all profiles, and
     * each non-Default profile's own `media_cache_<id>_profile_<profileId>` file alongside the
     * shared one (the live sync plan → User profiles).
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
        } catch (_: Exception) {
            // cancellation-ok: no suspend call in try
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
                } catch (e: Exception) {
                    // cancellation-ok: non-suspend
                    org.njarasoa.fijerena.core.network.CredentialStoreHealth
                        .markLost(context, fileName, e)
                    context.deleteSharedPreferences(fileName)
                    try {
                        EncryptedSharedPreferences.create(
                            context,
                            fileName,
                            masterKey,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                        )
                    } catch (_: Exception) {
                        // cancellation-ok: non-suspend
                        // Never a plaintext file — see CredentialStoreHealth.InMemoryPrefs.
                        org.njarasoa.fijerena.core.network.CredentialStoreHealth
                            .InMemoryPrefs()
                    }
                }
            prefs
        }
}
