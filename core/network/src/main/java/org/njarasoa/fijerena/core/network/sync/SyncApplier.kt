package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import androidx.room.withTransaction
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.CategoryFiltersSerializer
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsTombstoneEntity
import org.njarasoa.fijerena.core.network.provider.SettingsVersionEntity
import org.njarasoa.fijerena.core.network.sync.SyncMerge.Presence
import org.njarasoa.fijerena.core.network.sync.SyncMerge.Resolution
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.SyncTombstoneEntity
import org.njarasoa.fijerena.core.network.xtream.db.SyncVersionEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * Applies records received from another device: looks up what this device knows about each key,
 * asks [SyncMerge] what to do, and does it — without queueing the change to be sent back. See
 * `docs/plans/20260929_live-sync-plan.md` → Applying remote records.
 *
 * Each applied record also becomes this device's version of its key ([SyncVersionEntity] /
 * [SettingsVersionEntity], not pending), and every received clock value is taken into both sync
 * clocks, so this device's next change comes after it.
 *
 * Database writes run in one transaction per record with the clock's `applying` flag set, which
 * silences the sync triggers. Provider and profile deletions reuse the repositories' full cleanup
 * instead (it spans both databases and SharedPreferences); the tombstone and version it records
 * are then corrected to the received clock value.
 */
class SyncApplier(
    private val context: Context,
) {
    private val settingsDb = SettingsDatabase.getInstance(context)
    private val xtreamDb = XtreamDatabase.getInstance(context)
    private val sync = settingsDb.settingsSyncDao()
    private val versions = xtreamDb.syncVersionDao()
    private val providers = ProviderRepository(context)

    data class Result(
        val applied: Int,
        val skipped: Int,
        /** Records that wait for their provider or profile — retry them after the next pull. */
        val deferred: List<SyncRecord>,
        /** Providers whose favourites or history changed: refresh their `MediaRepository`. */
        val userDataChangedProviderIds: Set<Long>,
        /**
         * Another device deleted the profile this device is using. The record is deferred; switch
         * this device to another profile, then apply it again.
         */
        val activeProfileDeleted: Boolean,
    )

    // The order records are applied in: what others depend on first.
    private val kindOrder =
        listOf(
            SyncKind.PROFILE,
            SyncKind.PROVIDER,
            SyncKind.EPG_SOURCE,
            SyncKind.SETTING,
            SyncKind.PROVIDER_LOGIN,
            SyncKind.CATEGORY_FILTERS,
            SyncKind.WATCH_CLEAR,
            SyncKind.FAVORITE_STREAM,
            SyncKind.FAVORITE_CATEGORY,
            SyncKind.WATCH,
        )

    suspend fun apply(records: List<SyncRecord>): Result {
        var applied = 0
        var skipped = 0
        val deferred = mutableListOf<SyncRecord>()
        val changedProviders = mutableSetOf<Long>()
        var activeProfileDeleted = false

        for (record in records.sortedBy { kindOrder.indexOf(it.key.kind).let { i -> if (i < 0) Int.MAX_VALUE else i } }) {
            if (record.key.kind !in kindOrder) {
                skipped++ // a kind from a newer app version
                continue
            }
            when (val outcome = applyOne(record)) {
                Outcome.Applied -> applied++
                is Outcome.AppliedUserData -> {
                    applied++
                    changedProviders += outcome.providerId
                }
                Outcome.Skipped -> skipped++
                Outcome.Deferred -> deferred += record
                Outcome.ActiveProfileDeleted -> {
                    deferred += record
                    activeProfileDeleted = true
                }
            }
        }
        records.maxOfOrNull { it.hlc }?.let { newest ->
            sync.receive(newest)
            versions.receive(newest)
        }
        return Result(applied, skipped, deferred, changedProviders, activeProfileDeleted)
    }

    private sealed interface Outcome {
        data object Applied : Outcome

        data class AppliedUserData(val providerId: Long) : Outcome

        data object Skipped : Outcome

        data object Deferred : Outcome

        data object ActiveProfileDeleted : Outcome
    }

    private suspend fun applyOne(record: SyncRecord): Outcome {
        val key = record.key
        return when (key.kind) {
            SyncKind.WATCH, SyncKind.WATCH_CLEAR, SyncKind.FAVORITE_STREAM, SyncKind.FAVORITE_CATEGORY -> applyUserData(record)
            SyncKind.PROFILE -> applyProfile(record)
            SyncKind.PROVIDER -> applyProvider(record)
            SyncKind.EPG_SOURCE -> applyEpgSource(record)
            SyncKind.SETTING -> applySetting(record)
            SyncKind.PROVIDER_LOGIN, SyncKind.CATEGORY_FILTERS -> applyProviderScopedSetting(record)
            else -> Outcome.Skipped
        }
    }

    // --- Facts about this device ---

    private suspend fun providerPresence(providerKey: String): Presence =
        when {
            sync.providerByKey(providerKey) != null -> Presence.KNOWN
            sync.getTombstone(SyncKind.PROVIDER, providerKey) != null -> Presence.DELETED
            else -> Presence.UNKNOWN
        }

    private suspend fun profilePresence(profileKey: String): Presence =
        when {
            profileKey == SyncKind.SHARED -> Presence.NOT_APPLICABLE
            settingsDb.profileDao().exists(profileKey) -> Presence.KNOWN
            sync.getTombstone(SyncKind.PROFILE, profileKey) != null -> Presence.DELETED
            else -> Presence.UNKNOWN
        }

    private suspend fun settingsVersion(
        kind: String,
        profileKey: String,
        itemKey: String,
    ) = sync.get(kind, profileKey, itemKey)?.hlc

    private suspend fun markSettingsVersion(
        kind: String,
        profileKey: String,
        itemKey: String,
        hlc: Long,
    ) = sync.upsertVersion(SettingsVersionEntity(kind, profileKey, itemKey, hlc, pending = false))

    /** Runs [block] in one `providers.db` transaction with the sync triggers silenced. */
    private suspend fun <T> inSettingsApply(block: suspend () -> T): T =
        settingsDb.withTransaction {
            sync.setApplying(true)
            val result = block()
            sync.setApplying(false)
            result
        }

    // --- Favourites and watch history (xtream_v2.db) ---

    private suspend fun applyUserData(record: SyncRecord): Outcome {
        val key = record.key
        val provider = sync.providerByKey(key.providerKey)
        val local =
            if (provider == null) {
                SyncMerge.Local(provider = providerPresence(key.providerKey), profile = profilePresence(key.profileKey))
            } else {
                SyncMerge.Local(
                    version = versions.get(provider.id, key.profileKey, key.kind, key.itemId, key.contentType)?.hlc,
                    tombstone = xtreamDb.syncTombstoneDao().get(provider.id, key.profileKey, key.kind, key.itemId, key.contentType)?.deletedAt,
                    watchClearedAt =
                        if (key.kind == SyncKind.WATCH) {
                            xtreamDb.syncTombstoneDao().get(provider.id, key.profileKey, SyncKind.WATCH_CLEAR, "", "")?.deletedAt
                        } else {
                            null
                        },
                    provider = Presence.KNOWN,
                    profile = profilePresence(key.profileKey),
                    providerKeepsUserData = provider.type == "JELLYFIN",
                )
            }
        val resolution = SyncMerge.resolve(record, local)
        if (resolution !is Resolution.Upsert && resolution !is Resolution.Delete && resolution !is Resolution.ClearWatch) {
            return skippedOrDeferred(resolution)
        }
        val providerId = provider!!.id
        xtreamDb.withTransaction {
            versions.setApplying(true)
            when (resolution) {
                Resolution.Upsert ->
                    if (key.kind == SyncKind.WATCH) {
                        val watch = SyncPayloads.decode<SyncPayloads.Watch>(record.payload)
                        xtreamDb.watchStateDao().restoreAll(listOf(watch.toEntity(providerId, key.profileKey, key.itemId, key.contentType)))
                    } else {
                        val favorite = SyncPayloads.decode<SyncPayloads.Favorite>(record.payload)
                        xtreamDb.favoriteStateDao().restoreAllClearingTombstones(
                            listOf(
                                FavoriteStateEntity(
                                    providerId, key.profileKey, key.itemId, key.contentType, favoriteKind(key.kind),
                                    favorite.name, favorite.parentCategoryId, favorite.createdAt,
                                ),
                            ),
                        )
                    }
                Resolution.Delete -> {
                    xtreamDb.favoriteStateDao().delete(providerId, key.profileKey, key.itemId, key.contentType, favoriteKind(key.kind))
                    xtreamDb.syncTombstoneDao().upsert(
                        SyncTombstoneEntity(providerId, key.profileKey, key.kind, key.itemId, key.contentType, record.hlc),
                    )
                }
                is Resolution.ClearWatch -> {
                    versions.deleteWatchOlderThan(providerId, key.profileKey, resolution.before)
                    versions.deleteWatchVersionsOlderThan(providerId, key.profileKey, resolution.before)
                    xtreamDb.syncTombstoneDao().upsert(
                        SyncTombstoneEntity(providerId, key.profileKey, SyncKind.WATCH_CLEAR, "", "", resolution.before),
                    )
                }
                else -> Unit
            }
            versions.upsert(SyncVersionEntity(providerId, key.profileKey, key.kind, key.itemId, key.contentType, record.hlc, pending = false))
            versions.setApplying(false)
        }
        return Outcome.AppliedUserData(providerId)
    }

    private fun favoriteKind(kind: String) = if (kind == SyncKind.FAVORITE_CATEGORY) FavoriteKind.CATEGORY else FavoriteKind.STREAM

    private fun skippedOrDeferred(resolution: Resolution): Outcome = if (resolution is Resolution.Defer) Outcome.Deferred else Outcome.Skipped

    // --- Profiles ---

    private suspend fun applyProfile(record: SyncRecord): Outcome {
        val id = record.key.itemId
        val local =
            SyncMerge.Local(
                version = settingsVersion(SyncKind.PROFILE, SyncKind.SHARED, id),
                tombstone = sync.getTombstone(SyncKind.PROFILE, id)?.deletedAt,
            )
        return when (val resolution = SyncMerge.resolve(record, local)) {
            Resolution.Upsert -> {
                val remote = SyncPayloads.decode<SyncPayloads.Profile>(record.payload)
                inSettingsApply {
                    val dao = settingsDb.profileDao()
                    if (dao.exists(id)) {
                        dao.update(id, remote.name, remote.colorIndex)
                    } else {
                        dao.insert(ProfileEntity(id, remote.name, remote.createdAt, remote.colorIndex))
                    }
                    markSettingsVersion(SyncKind.PROFILE, SyncKind.SHARED, id, record.hlc)
                }
                Outcome.Applied
            }
            Resolution.Delete -> {
                when (ProfileRepository(context).deleteProfile(id)) {
                    ProfileRepository.DeleteBlocked.ACTIVE -> return Outcome.ActiveProfileDeleted
                    // Another device can't have deleted this one's last profile without having
                    // another; ignore rather than leave nobody to be.
                    ProfileRepository.DeleteBlocked.LAST -> return Outcome.Skipped
                    ProfileRepository.DeleteBlocked.NONE -> Unit
                }
                recordReceivedDeletion(SyncKind.PROFILE, id, record.hlc)
                Outcome.Applied
            }
            else -> skippedOrDeferred(resolution)
        }
    }

    // --- Providers ---

    private suspend fun applyProvider(record: SyncRecord): Outcome {
        val providerKey = record.key.providerKey
        val local =
            SyncMerge.Local(
                version = settingsVersion(SyncKind.PROVIDER, SyncKind.SHARED, providerKey),
                tombstone = sync.getTombstone(SyncKind.PROVIDER, providerKey)?.deletedAt,
            )
        return when (val resolution = SyncMerge.resolve(record, local)) {
            Resolution.Upsert -> {
                val remote = SyncPayloads.decode<SyncPayloads.Provider>(record.payload)
                inSettingsApply {
                    providers.applyRemoteProvider(providerKey, remote)
                    markSettingsVersion(SyncKind.PROVIDER, SyncKind.SHARED, providerKey, record.hlc)
                }
                Outcome.Applied
            }
            Resolution.Delete -> {
                sync.providerByKey(providerKey)?.let { providers.deleteProvider(it.id) }
                recordReceivedDeletion(SyncKind.PROVIDER, providerKey, record.hlc)
                Outcome.Applied
            }
            else -> skippedOrDeferred(resolution)
        }
    }

    /**
     * After a repository deletion (which records its own tombstone on this device's clock and
     * queues it), or for something this device never had: the deletion is the received one, at
     * its clock value, and nothing is left to send.
     */
    private suspend fun recordReceivedDeletion(
        kind: String,
        itemKey: String,
        hlc: Long,
    ) = inSettingsApply {
        sync.upsertTombstone(SettingsTombstoneEntity(kind, itemKey, hlc))
        markSettingsVersion(kind, SyncKind.SHARED, itemKey, hlc)
    }

    // --- EPG sources ---

    private suspend fun applyEpgSource(record: SyncRecord): Outcome {
        val sourceKey = record.key.itemId
        val payload = if (record.deleted) null else SyncPayloads.decode<SyncPayloads.EpgSource>(record.payload)
        val local =
            SyncMerge.Local(
                version = settingsVersion(SyncKind.EPG_SOURCE, SyncKind.SHARED, sourceKey),
                tombstone = sync.getTombstone(SyncKind.EPG_SOURCE, sourceKey)?.deletedAt,
                provider = payload?.let { providerPresence(it.providerKey) } ?: Presence.NOT_APPLICABLE,
            )
        return when (val resolution = SyncMerge.resolve(record, local)) {
            Resolution.Upsert -> {
                val remote = payload!!
                val providerId = sync.providerByKey(remote.providerKey)!!.id
                inSettingsApply {
                    val dao = settingsDb.epgSourceDao()
                    val existing = sync.sourceByKey(sourceKey)
                    if (existing != null) {
                        dao.updateSource(
                            existing.copy(
                                url = remote.url,
                                label = remote.label,
                                timezoneOffsetHours = remote.timezoneOffsetHours,
                                enabled = remote.enabled,
                                providerId = providerId,
                            ),
                        )
                    } else {
                        dao.insertSource(
                            EpgSourceEntity(
                                url = remote.url,
                                label = remote.label,
                                timezoneOffsetHours = remote.timezoneOffsetHours,
                                enabled = remote.enabled,
                                providerId = providerId,
                                sourceKey = sourceKey,
                            ),
                        )
                    }
                    markSettingsVersion(SyncKind.EPG_SOURCE, SyncKind.SHARED, sourceKey, record.hlc)
                }
                Outcome.Applied
            }
            Resolution.Delete -> {
                val existing = sync.sourceByKey(sourceKey)
                inSettingsApply {
                    existing?.let { settingsDb.epgSourceDao().deleteSource(it.id) }
                    sync.upsertTombstone(SettingsTombstoneEntity(SyncKind.EPG_SOURCE, sourceKey, record.hlc))
                    markSettingsVersion(SyncKind.EPG_SOURCE, SyncKind.SHARED, sourceKey, record.hlc)
                }
                existing?.let { EpgIndexDatabase.getInstance(context).epgIndexDao().deleteBySourceId(it.id) }
                Outcome.Applied
            }
            else -> skippedOrDeferred(resolution)
        }
    }

    // --- Settings, logins, category filters ---

    private suspend fun applySetting(record: SyncRecord): Outcome {
        val key = record.key
        val local =
            SyncMerge.Local(
                version = settingsVersion(SyncKind.SETTING, key.profileKey, key.itemId),
                profile = profilePresence(key.profileKey),
            )
        return when (val resolution = SyncMerge.resolve(record, local)) {
            Resolution.Upsert -> {
                val remote = SyncPayloads.decode<SyncPayloads.Setting>(record.payload)
                AppSettings(context).applyRemoteSetting(key.itemId, key.profileKey, remote.value)
                inSettingsApply { markSettingsVersion(SyncKind.SETTING, key.profileKey, key.itemId, record.hlc) }
                Outcome.Applied
            }
            else -> skippedOrDeferred(resolution)
        }
    }

    private suspend fun applyProviderScopedSetting(record: SyncRecord): Outcome {
        val key = record.key
        val local =
            SyncMerge.Local(
                version = settingsVersion(key.kind, key.profileKey, key.providerKey),
                provider = providerPresence(key.providerKey),
                profile = profilePresence(key.profileKey),
            )
        return when (val resolution = SyncMerge.resolve(record, local)) {
            Resolution.Upsert, Resolution.Delete -> {
                val providerId = sync.providerByKey(key.providerKey)!!.id
                inSettingsApply {
                    if (key.kind == SyncKind.PROVIDER_LOGIN) {
                        val login = if (record.deleted) null else SyncPayloads.decode<SyncPayloads.Login>(record.payload)
                        providers.applyRemoteLogin(providerId, key.profileKey, login)
                    } else {
                        val filters =
                            if (record.deleted) {
                                CategoryFilters()
                            } else {
                                SyncPayloads.json.decodeFromString(CategoryFiltersSerializer, requireNotNull(record.payload))
                            }
                        providers.applyRemoteCategoryFilters(providerId, key.profileKey, filters)
                    }
                    markSettingsVersion(key.kind, key.profileKey, key.providerKey, record.hlc)
                }
                Outcome.Applied
            }
            else -> skippedOrDeferred(resolution)
        }
    }
}
