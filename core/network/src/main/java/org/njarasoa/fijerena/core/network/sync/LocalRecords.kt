package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.provider.CategoryFiltersSerializer
import org.njarasoa.fijerena.core.network.provider.CategoryFiltersStore
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsVersionEntity
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind
import org.njarasoa.fijerena.core.network.xtream.db.SyncVersionEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * Turns this device's pending versions into [SyncRecord]s to send, reading the current row, value
 * or tombstone of each key; and, on linking, queues everything that already exists. See
 * `docs/plans/archive/20260929_live-sync-plan.md` → Flow.
 */
class LocalRecords(
    private val context: Context,
) {
    private val settingsDb = SettingsDatabase.getInstance(context)
    private val xtreamDb = XtreamDatabase.getInstance(context)
    private val sync = settingsDb.settingsSyncDao()
    private val providers = ProviderRepository(context)

    /** A record ready to send, with what to mark sent once the server has it. */
    data class Outgoing(
        val record: SyncRecord,
        val markSent: suspend () -> Unit,
    )

    /**
     * The next batch of pending changes, oldest first, `providers.db` before `xtream_v2.db` so a
     * provider reaches the server before its favourites. Versions with nothing left to send (the
     * item vanished without a tombstone, or it belongs to a Jellyfin provider) are cleared here.
     */
    suspend fun pending(limit: Int): List<Outgoing> {
        val out = mutableListOf<Outgoing>()
        for (version in sync.getPending(limit)) {
            val record = settingsRecord(version)
            if (record == null) {
                sync.deleteVersion(version.kind, version.profileId, version.itemKey)
            } else {
                out += Outgoing(record) { sync.markSent(version.kind, version.profileId, version.itemKey, version.hlc) }
            }
        }
        if (out.size >= limit) return out
        val versions = xtreamDb.syncVersionDao()
        for (version in versions.getPending(limit - out.size)) {
            val record = userDataRecord(version)
            if (record == null) {
                versions.deleteVersion(version.providerId, version.profileId, version.kind, version.itemId, version.contentType)
            } else {
                out +=
                    Outgoing(record) {
                        versions.markSent(
                            version.providerId,
                            version.profileId,
                            version.kind,
                            version.itemId,
                            version.contentType,
                            version.hlc,
                        )
                    }
            }
        }
        return out
    }

    /**
     * Linking: queue every item that exists locally, each at its own last-change time (so that,
     * against another device's copy, the newer really wins), unless it already has a version —
     * received in the pull that precedes this, or changed since Phase 4.
     */
    suspend fun seedEverything() {
        with(xtreamDb.syncVersionDao()) {
            seedWatch()
            seedFavorites()
            seedTombstones()
        }
        sync.seedProviders()
        sync.seedProfiles()
        sync.seedEpgSources()
        sync.seedTombstones()
        val now = System.currentTimeMillis()
        val filters = CategoryFiltersStore(context)
        val profiles = settingsDb.profileDao().getAll().map { it.id }
        for (provider in settingsDb.providerDao().getAllProvidersList()) {
            for (profile in profiles) {
                if (filters.has(provider.id, profile)) sync.seed(SyncKind.CATEGORY_FILTERS, profile, provider.providerKey, now)
            }
            if (provider.type == "JELLYFIN") {
                (providers.profilesWithOwnLogin(provider.id) + ProfileEntity.DEFAULT_ID).distinct().forEach { profile ->
                    if (providers.syncedLogin(provider, profile) !=
                        null
                    ) {
                        sync.seed(SyncKind.PROVIDER_LOGIN, profile, provider.providerKey, now)
                    }
                }
            }
        }
        val settings = AppSettings(context)
        for (key in AppSettings.SYNCED_SETTING_KEYS) {
            if (key in AppSettings.PER_PROFILE_SETTING_KEYS) {
                profiles.filter { settings.syncedSetting(key, it) != null }.forEach { sync.seed(SyncKind.SETTING, it, key, now) }
            } else if (settings.syncedSetting(key, SyncKind.SHARED) != null) {
                sync.seed(SyncKind.SETTING, SyncKind.SHARED, key, now)
            }
        }
    }

    private suspend fun settingsRecord(v: SettingsVersionEntity): SyncRecord? =
        when (v.kind) {
            SyncKind.PROVIDER -> {
                val key = SyncKey(SyncKind.SHARED, v.itemKey, SyncKind.PROVIDER)
                val entity = sync.providerByKey(v.itemKey)
                if (entity == null) {
                    SyncRecord(key, v.hlc, deleted = true)
                } else {
                    SyncRecord(
                        key,
                        v.hlc,
                        payload =
                            SyncPayloads.encode(
                                SyncPayloads.Provider.of(entity, providers.getPassword(entity.id), providers.getExtraPasswords(entity)),
                            ),
                    )
                }
            }

            SyncKind.PROFILE -> {
                val key = SyncKey(SyncKind.SHARED, "", SyncKind.PROFILE, v.itemKey)
                val profile = settingsDb.profileDao().getAll().firstOrNull { it.id == v.itemKey }
                if (profile ==
                    null
                ) {
                    SyncRecord(key, v.hlc, deleted = true)
                } else {
                    SyncRecord(key, v.hlc, payload = SyncPayloads.encode(SyncPayloads.Profile.of(profile)))
                }
            }

            SyncKind.EPG_SOURCE -> {
                val key = SyncKey(SyncKind.SHARED, "", SyncKind.EPG_SOURCE, v.itemKey)
                val source = sync.sourceByKey(v.itemKey)
                val providerKey = source?.let { sync.providerKey(it.providerId) }
                when {
                    source == null -> SyncRecord(key, v.hlc, deleted = true)
                    providerKey == null -> null
                    else -> SyncRecord(key, v.hlc, payload = SyncPayloads.encode(SyncPayloads.EpgSource.of(source, providerKey)))
                }
            }

            SyncKind.PROVIDER_LOGIN -> {
                val entity = sync.providerByKey(v.itemKey)
                when {
                    entity == null || entity.type != "JELLYFIN" -> {
                        null
                    }

                    else -> {
                        val key = SyncKey(v.profileId, v.itemKey, SyncKind.PROVIDER_LOGIN)
                        val login = providers.syncedLogin(entity, v.profileId)
                        if (login ==
                            null
                        ) {
                            SyncRecord(key, v.hlc, deleted = true)
                        } else {
                            SyncRecord(key, v.hlc, payload = SyncPayloads.encode(login))
                        }
                    }
                }
            }

            SyncKind.CATEGORY_FILTERS -> {
                val entity = sync.providerByKey(v.itemKey) ?: return null
                val filters = CategoryFiltersStore(context).get(entity.id, v.profileId)
                SyncRecord(
                    SyncKey(v.profileId, v.itemKey, SyncKind.CATEGORY_FILTERS),
                    v.hlc,
                    payload = SyncPayloads.json.encodeToString(CategoryFiltersSerializer, filters),
                )
            }

            SyncKind.SETTING -> {
                val value = AppSettings(context).syncedSetting(v.itemKey, v.profileId) ?: return null
                SyncRecord(
                    SyncKey(v.profileId, "", SyncKind.SETTING, v.itemKey),
                    v.hlc,
                    payload = SyncPayloads.encode(SyncPayloads.Setting(value)),
                )
            }

            else -> {
                null
            }
        }

    private suspend fun userDataRecord(v: SyncVersionEntity): SyncRecord? {
        val provider = sync.providerById(v.providerId) ?: return null
        // Jellyfin keeps favourites and history itself; they never go through sync.
        if (provider.type == "JELLYFIN") return null
        val key = SyncKey(v.profileId, provider.providerKey, v.kind, v.itemId, v.contentType)
        return when (v.kind) {
            SyncKind.WATCH -> {
                val row = xtreamDb.watchStateDao().getItem(v.providerId, v.profileId, v.itemId, v.contentType) ?: return null
                SyncRecord(key, v.hlc, payload = SyncPayloads.encode(SyncPayloads.Watch.of(row)))
            }

            SyncKind.FAVORITE_STREAM, SyncKind.FAVORITE_CATEGORY -> {
                val favoriteKind = if (v.kind == SyncKind.FAVORITE_CATEGORY) FavoriteKind.CATEGORY else FavoriteKind.STREAM
                val row = xtreamDb.favoriteStateDao().get(v.providerId, v.profileId, v.itemId, v.contentType, favoriteKind)
                if (row ==
                    null
                ) {
                    SyncRecord(key, v.hlc, deleted = true)
                } else {
                    SyncRecord(key, v.hlc, payload = SyncPayloads.encode(SyncPayloads.Favorite.of(row)))
                }
            }

            SyncKind.WATCH_CLEAR -> {
                SyncRecord(key, v.hlc)
            }

            else -> {
                null
            }
        }
    }
}
