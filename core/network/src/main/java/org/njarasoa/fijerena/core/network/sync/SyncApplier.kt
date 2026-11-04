package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.CategoryFiltersSerializer
import org.njarasoa.fijerena.core.network.provider.EpgSourceAliasEntity
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsTombstoneEntity
import org.njarasoa.fijerena.core.network.provider.SettingsVersionEntity
import org.njarasoa.fijerena.core.network.provider.isAutomaticGuide
import org.njarasoa.fijerena.core.network.sync.SyncMerge.Presence
import org.njarasoa.fijerena.core.network.sync.SyncMerge.Resolution
import org.njarasoa.fijerena.core.network.xmltv.EpgOffsetShift
import org.njarasoa.fijerena.core.network.xmltv.EpgRefreshSchedule
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.FollowedSportEntity
import org.njarasoa.fijerena.core.network.xtream.db.SyncTombstoneEntity
import org.njarasoa.fijerena.core.network.xtream.db.SyncVersionEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.manager.AutoXmltvSources
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService

/** The playback on this device right now — `core:player` publishes it process-wide. */
@androidx.annotation.OptIn(UnstableApi::class)
private fun playingSessionNow(): String? = StreamingPlaybackService.nowPlaying.value?.sessionId

/**
 * Applies records received from another device: looks up what this device knows about each key,
 * asks [SyncMerge] what to do, and does it — without queueing the change to be sent back. See
 * the live sync plan → Applying remote records.
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
    /** This device's server id — a remote command is obeyed only when keyed to it. */
    private val thisDeviceId: () -> String? = { SyncAccountStore(context).link?.deviceId },
    /** The playback on right now (`NowPlayingSnapshot.sessionId`), null when nothing plays. */
    private val playingSessionId: () -> String? = ::playingSessionNow,
) {
    private companion object {
        const val TAG = "SyncApplier"

        /** See [applyGuarded]. */
        val reportedFailures: MutableSet<String> =
            java.util.concurrent.ConcurrentHashMap
                .newKeySet()
    }

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
         * Providers whose record, login or category filters were applied (or which were deleted):
         * drop their cached `MediaRepository` so the next use builds one with the new values.
         */
        val providerChangedIds: Set<Long>,
        /**
         * Another device deleted the profile this device is using. The record is deferred; switch
         * this device to another profile, then apply it again.
         */
        val activeProfileDeleted: Boolean,
        /**
         * Another device deleted a provider the profile in use here had: no screen may stay on it,
         * so the UI starts over on the profile's remaining sources. One the profile didn't have
         * changes nothing on screen (its cached repository goes with [providerChangedIds]).
         */
        val providerDeleted: Boolean = false,
        /** A Wanted title was applied: the Wanted list and what is available are worked out again. */
        val wantedChanged: Boolean = false,
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
            SyncKind.WANTED,
            SyncKind.FOLLOWED_SPORT,
            SyncKind.WATCH,
            SyncKind.NOW_PLAYING,
            SyncKind.REMOTE_COMMAND,
            SyncKind.PIN_REQUEST,
            SyncKind.PIN_ANSWER,
        )

    suspend fun apply(records: List<SyncRecord>): Result {
        var applied = 0
        var skipped = 0
        val deferred = mutableListOf<SyncRecord>()
        val changedProviders = mutableSetOf<Long>()
        val changedProviderConfigs = mutableSetOf<Long>()
        var activeProfileDeleted = false
        var providerDeleted = false
        var wantedChanged = false

        for (record in records.sortedBy { kindOrder.indexOf(it.key.kind).let { i -> if (i < 0) Int.MAX_VALUE else i } }) {
            if (record.key.kind !in kindOrder) {
                skipped++ // a kind from a newer app version
                continue
            }
            when (val outcome = applyGuarded(record)) {
                Outcome.Applied -> {
                    applied++
                    wantedChanged = wantedChanged || record.key.kind == SyncKind.WANTED
                }

                is Outcome.AppliedUserData -> {
                    applied++
                    changedProviders += outcome.providerId
                }

                is Outcome.AppliedProvider -> {
                    applied++
                    changedProviderConfigs += outcome.providerId
                    providerDeleted = providerDeleted || outcome.deleted
                }

                Outcome.Skipped -> {
                    skipped++
                }

                Outcome.Deferred -> {
                    deferred += record
                }

                Outcome.ActiveProfileDeleted -> {
                    deferred += record
                    activeProfileDeleted = true
                }
            }
        }
        if (applied > 0) {
            // A child is never Admin: one device may make a profile a child while another makes
            // it Admin, and the two flags come in separately, in any order.
            ProfileRepository(context).clearChildAdmins()
            // Two devices may each turn off a different Admin at once, or delete one: keep one.
            ProfileRepository(context).ensureAnAdmin()
        }
        // Volatile records are no step of the clocks: they write nothing to either database.
        records.filterNot { it.key.kind in SyncKind.VOLATILE }.maxOfOrNull { it.hlc }?.let { newest ->
            sync.receive(newest)
            versions.receive(newest)
        }
        return Result(
            applied,
            skipped,
            deferred,
            changedProviders,
            changedProviderConfigs,
            activeProfileDeleted,
            providerDeleted,
            wantedChanged,
        )
    }

    private sealed interface Outcome {
        data object Applied : Outcome

        data class AppliedUserData(
            val providerId: Long,
        ) : Outcome

        /** A provider's record, login or category filters: see [Result.providerChangedIds]. */
        data class AppliedProvider(
            val providerId: Long,
            /** It was deleted, and the profile in use had it. */
            val deleted: Boolean = false,
        ) : Outcome

        data object Skipped : Outcome

        data object Deferred : Outcome

        data object ActiveProfileDeleted : Outcome
    }

    /**
     * [applyOne], but a record that throws — a payload shape from another app version, or a
     * provider deleted locally mid-apply — waits with the deferred ones instead of failing the
     * pass. An exception would abort the whole pull before the page's cursor was saved, so
     * every later pass would refetch the same record and fail on it again: sync stuck for good on
     * that device, with no error shown. Deferred rather than skipped so a newer app version can
     * still apply it. See the rock solid stability resilience plan → F-08.
     */
    private suspend fun applyGuarded(record: SyncRecord): Outcome =
        try {
            applyOne(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't apply a ${record.key.kind} record; it waits for a later pass", e)
            // Once per kind and exception type per process: a record retried every pass must not
            // flood the crash log.
            if (reportedFailures.add("${record.key.kind}/${e.javaClass.name}")) CrashLog.record("sync apply ${record.key.kind}", e)
            Outcome.Deferred
        }

    private suspend fun applyOne(record: SyncRecord): Outcome {
        val key = record.key
        return when (key.kind) {
            SyncKind.WATCH, SyncKind.WATCH_CLEAR, SyncKind.FAVORITE_STREAM, SyncKind.FAVORITE_CATEGORY -> applyUserData(record)
            SyncKind.WANTED -> applyWanted(record)
            SyncKind.FOLLOWED_SPORT -> applyFollowedSport(record)
            SyncKind.PROFILE -> applyProfile(record)
            SyncKind.PROVIDER -> applyProvider(record)
            SyncKind.EPG_SOURCE -> applyEpgSource(record)
            SyncKind.SETTING -> applySetting(record)
            SyncKind.PROVIDER_LOGIN -> applyOldLogin(record)
            SyncKind.CATEGORY_FILTERS -> applyProviderScopedSetting(record)
            SyncKind.NOW_PLAYING -> applyNowPlaying(record)
            SyncKind.REMOTE_COMMAND -> applyRemoteCommand(record)
            SyncKind.PIN_REQUEST -> applyPinRequest(record)
            SyncKind.PIN_ANSWER -> applyPinAnswer(record)
            else -> Outcome.Skipped
        }
    }

    /** Into [NowPlayingStore] only — no version, no tombstone (none is ever sent). Keyed by device id. */
    private fun applyNowPlaying(record: SyncRecord): Outcome =
        if (record.deleted) {
            Outcome.Skipped
        } else {
            val entry = NowPlayingStore.Entry(SyncPayloads.decode(record.payload), record.hlc, System.currentTimeMillis())
            NowPlayingStore.receive(record.key.itemId, entry)
            Outcome.Applied
        }

    /**
     * To [RemoteCommands] only, and only a command keyed to this device for the session playing
     * right now — see [RemoteCommands.receive]. Commands for other devices are never even decoded.
     * Nothing is written anywhere: no version, no tombstone.
     */
    private fun applyRemoteCommand(record: SyncRecord): Outcome {
        val forThisDevice = !record.deleted && record.key.itemId.isNotEmpty() && record.key.itemId == thisDeviceId()
        // A payload this version can't read (a newer sender) is volatile and can never matter:
        // skip it rather than defer it for retry.
        val fired =
            try {
                forThisDevice && RemoteCommands.receive(SyncPayloads.decode(record.payload), playingSessionId())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        return if (fired) Outcome.Applied else Outcome.Skipped
    }

    /**
     * To [PinRequests] only: a request from another device (one keyed to this device is its own,
     * read back). Like a remote command, a payload this version can't read is skipped, not deferred.
     */
    private fun applyPinRequest(record: SyncRecord): Outcome {
        val fromOther = !record.deleted && record.key.itemId.isNotEmpty() && record.key.itemId != thisDeviceId()
        val taken =
            try {
                fromOther && PinRequests.receiveRequest(record.key.itemId, SyncPayloads.decode(record.payload), record.hlc)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        return if (taken) Outcome.Applied else Outcome.Skipped
    }

    /** To [PinRequests] only, and only an answer keyed to this device: see [PinRequests.receiveAnswer]. */
    private fun applyPinAnswer(record: SyncRecord): Outcome {
        val forThisDevice = !record.deleted && record.key.itemId.isNotEmpty() && record.key.itemId == thisDeviceId()
        val fired =
            try {
                forThisDevice && PinRequests.receiveAnswer(SyncPayloads.decode(record.payload))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        return if (fired) Outcome.Applied else Outcome.Skipped
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
                    tombstone =
                        xtreamDb
                            .syncTombstoneDao()
                            .get(
                                provider.id,
                                key.profileKey,
                                key.kind,
                                key.itemId,
                                key.contentType,
                            )?.deletedAt,
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
        // Null only if the provider was deleted here between the lookup and now.
        val providerId = provider?.id ?: return Outcome.Deferred
        xtreamDb.withTransaction {
            versions.setApplying(true)
            when (resolution) {
                Resolution.Upsert -> {
                    if (key.kind == SyncKind.WATCH) {
                        val watch = SyncPayloads.decode<SyncPayloads.Watch>(record.payload)
                        xtreamDb.watchStateDao().restoreAll(listOf(watch.toEntity(providerId, key.profileKey, key.itemId, key.contentType)))
                    } else {
                        val favorite = SyncPayloads.decode<SyncPayloads.Favorite>(record.payload)
                        xtreamDb.favoriteStateDao().restoreAllClearingTombstones(
                            listOf(
                                FavoriteStateEntity(
                                    providerId,
                                    key.profileKey,
                                    key.itemId,
                                    key.contentType,
                                    favoriteKind(key.kind),
                                    favorite.name,
                                    favorite.parentCategoryId,
                                    favorite.createdAt,
                                    favorite.tmdbId,
                                ),
                            ),
                        )
                    }
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
            }
            versions.upsert(
                SyncVersionEntity(providerId, key.profileKey, key.kind, key.itemId, key.contentType, record.hlc, pending = false),
            )
            versions.setApplying(false)
        }
        return Outcome.AppliedUserData(providerId)
    }

    /** A Wanted title (xtream_v2.db, no provider: see [SyncKind.WANTED_PROVIDER_ID]). */
    private suspend fun applyWanted(record: SyncRecord): Outcome {
        val key = record.key
        val noProvider = SyncKind.WANTED_PROVIDER_ID
        val local =
            SyncMerge.Local(
                version = versions.get(noProvider, key.profileKey, key.kind, key.itemId, key.contentType)?.hlc,
                tombstone = xtreamDb.syncTombstoneDao().get(noProvider, key.profileKey, key.kind, key.itemId, key.contentType)?.deletedAt,
                profile = profilePresence(key.profileKey),
            )
        val resolution = SyncMerge.resolve(record, local)
        if (resolution !is Resolution.Upsert && resolution !is Resolution.Delete) return skippedOrDeferred(resolution)
        val wanted = xtreamDb.wantedTitleDao()
        xtreamDb.withTransaction {
            versions.setApplying(true)
            if (resolution is Resolution.Upsert) {
                val payload = SyncPayloads.decode<SyncPayloads.Wanted>(record.payload)
                // The release data is this device's own (refreshed from TMDB here): kept.
                val local = wanted.get(key.profileKey, key.contentType, key.itemId)
                val received = payload.toEntity(key.profileKey, key.contentType, key.itemId)
                wanted.upsertClearingTombstone(
                    local?.let {
                        received.copy(
                            releaseDate = it.releaseDate,
                            digitalDate = it.digitalDate,
                            inCinemas = it.inCinemas,
                            showStatus = it.showStatus,
                            releaseCheckedAt = it.releaseCheckedAt,
                        )
                    } ?: received,
                )
            } else {
                wanted.delete(key.profileKey, key.contentType, key.itemId)
                xtreamDb.syncTombstoneDao().upsert(
                    SyncTombstoneEntity(noProvider, key.profileKey, key.kind, key.itemId, key.contentType, record.hlc),
                )
            }
            versions.upsert(
                SyncVersionEntity(noProvider, key.profileKey, key.kind, key.itemId, key.contentType, record.hlc, pending = false),
            )
            versions.setApplying(false)
        }
        return Outcome.Applied
    }

    /**
     * A followed team or league (xtream_v2.db, no provider: see [SyncKind.FOLLOWED_SPORT_PROVIDER_ID]).
     * Screens observe the table, so nothing else is told. A content type that is not
     * `<kind>:<source>` is skipped.
     */
    private suspend fun applyFollowedSport(record: SyncRecord): Outcome {
        val key = record.key
        val (kind, source) = FollowedSportEntity.parseSyncContentType(key.contentType) ?: return Outcome.Skipped
        val noProvider = SyncKind.FOLLOWED_SPORT_PROVIDER_ID
        val local =
            SyncMerge.Local(
                version = versions.get(noProvider, key.profileKey, key.kind, key.itemId, key.contentType)?.hlc,
                tombstone = xtreamDb.syncTombstoneDao().get(noProvider, key.profileKey, key.kind, key.itemId, key.contentType)?.deletedAt,
                profile = profilePresence(key.profileKey),
            )
        val resolution = SyncMerge.resolve(record, local)
        if (resolution !is Resolution.Upsert && resolution !is Resolution.Delete) return skippedOrDeferred(resolution)
        val followed = xtreamDb.followedSportDao()
        xtreamDb.withTransaction {
            versions.setApplying(true)
            if (resolution is Resolution.Upsert) {
                val payload = SyncPayloads.decode<SyncPayloads.FollowedSport>(record.payload)
                followed.upsertClearingTombstone(payload.toEntity(key.profileKey, kind, source, key.itemId))
            } else {
                followed.delete(key.profileKey, kind, source, key.itemId)
                xtreamDb.syncTombstoneDao().upsert(
                    SyncTombstoneEntity(noProvider, key.profileKey, key.kind, key.itemId, key.contentType, record.hlc),
                )
            }
            versions.upsert(
                SyncVersionEntity(noProvider, key.profileKey, key.kind, key.itemId, key.contentType, record.hlc, pending = false),
            )
            versions.setApplying(false)
        }
        return Outcome.Applied
    }

    private fun favoriteKind(kind: String) = if (kind == SyncKind.FAVORITE_CATEGORY) FavoriteKind.CATEGORY else FavoriteKind.STREAM

    private fun skippedOrDeferred(resolution: Resolution): Outcome =
        if (resolution is Resolution.Defer) Outcome.Deferred else Outcome.Skipped

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

            else -> {
                skippedOrDeferred(resolution)
            }
        }
    }

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
                val id =
                    inSettingsApply {
                        adoptMatchingProvider(providerKey, remote)
                        val id = providers.applyRemoteProvider(providerKey, remote)
                        markSettingsVersion(SyncKind.PROVIDER, SyncKind.SHARED, providerKey, record.hlc)
                        id
                    }
                Outcome.AppliedProvider(id)
            }

            Resolution.Delete -> {
                val existing = sync.providerByKey(providerKey)
                // Read before the deletion takes it out of the profile's sources.
                val settings = AppSettings(context)
                val inUse = settings.profileSourceKeys(settings.activeProfileId)?.contains(providerKey) == true
                existing?.let { providers.deleteProvider(it.id, fromRemote = true) }
                recordReceivedDeletion(SyncKind.PROVIDER, providerKey, record.hlc)
                if (existing != null) Outcome.AppliedProvider(existing.id, deleted = inUse) else Outcome.Applied
            }

            else -> {
                skippedOrDeferred(resolution)
            }
        }
    }

    /**
     * First sync of a device that already had this provider: the same one set up on both before
     * they were linked has two keys. The local copy takes the other device's key — only if the
     * server has never seen the local key, so a provider already shared stays itself — rather than
     * syncing as a duplicate. Matched on type, URL and username, like the settings import.
     */
    private suspend fun adoptMatchingProvider(
        providerKey: String,
        remote: SyncPayloads.Provider,
    ) {
        if (sync.providerByKey(providerKey) != null) return
        val match =
            sync.allProviders().firstOrNull {
                it.type == remote.type &&
                    normalizedUrl(it.url) == normalizedUrl(remote.url) &&
                    it.username == remote.username &&
                    !sync.knownToServer(SyncKind.PROVIDER, it.providerKey)
            } ?: return
        sync.adoptProviderKey(match.providerKey, providerKey)
    }

    private fun normalizedUrl(url: String) = url.trim().trimEnd('/').lowercase()

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

    /** Where a received guide source record goes on this device (the one guide source per URL plan, Design S). */
    private sealed interface GuideTarget {
        /** The guide with the record's key. */
        data class Own(
            val guide: EpgSourceEntity,
        ) : GuideTarget

        /**
         * The record's key is an alias of [guide]: an older build's copy for media source
         * [providerId], or the same guide under another key when it is null.
         */
        data class Alias(
            val guide: EpgSourceEntity,
            val providerId: Long?,
        ) : GuideTarget

        /** The same guide, added here before the devices were linked: the server never had its key. */
        data class Adopt(
            val guide: EpgSourceEntity,
        ) : GuideTarget

        /** The same guide under a key the server knows: the record's key becomes its alias. */
        data class NewAlias(
            val guide: EpgSourceEntity,
            val providerId: Long?,
        ) : GuideTarget

        data object None : GuideTarget
    }

    /** The key of the guide a record's key is another name for, whose version also counts. */
    private val GuideTarget.otherKey: String?
        get() =
            when (this) {
                is GuideTarget.Alias -> guide.sourceKey
                is GuideTarget.NewAlias -> guide.sourceKey
                else -> null
            }

    /**
     * In this order: the guide with [sourceKey]; the guide [sourceKey] is an alias of; for a
     * record ([remote] read by [linked]), the same guide here — the source's automatic guide
     * for an automatic record, else a hand-added guide at the same URL (`GuideUrls.matchKey`).
     */
    private suspend fun guideTarget(
        sourceKey: String,
        remote: SyncPayloads.EpgSource?,
        remoteGuide: EpgSourceEntity?,
        linked: List<ProviderEntity>,
    ): GuideTarget {
        val own = sync.sourceByKey(sourceKey)
        val alias = if (own == null) settingsDb.epgSourceDao().getAlias(sourceKey) else null
        val aliased = alias?.let { settingsDb.epgSourceDao().getSourceById(it.sourceId) }
        val same =
            if (own == null && aliased == null && remoteGuide != null && linked.isNotEmpty()) {
                if (isAutomaticGuide(remoteGuide, linked)) {
                    sync.autoGuideOf(linked.first().id)
                } else {
                    sync.guidesAtUrl(remoteGuide.url).firstOrNull()
                }
            } else {
                null
            }
        return when {
            own != null -> GuideTarget.Own(own)

            aliased != null -> GuideTarget.Alias(aliased, alias.providerId)

            same == null -> GuideTarget.None

            !sync.knownToServer(SyncKind.EPG_SOURCE, same.sourceKey) -> GuideTarget.Adopt(same)

            // A record of one source is an older build's copy for it; one of several is the guide.
            else -> GuideTarget.NewAlias(same, linked.first().id.takeIf { remote?.keys?.size == 1 })
        }
    }

    /** Unknown when one of [keys] is not here yet, deleted when all are gone, else known. */
    private suspend fun providersPresence(keys: List<String>): Presence {
        val all = keys.map { providerPresence(it) }
        return when {
            Presence.UNKNOWN in all -> Presence.UNKNOWN
            all.all { it == Presence.DELETED } -> Presence.DELETED
            else -> Presence.KNOWN
        }
    }

    /**
     * A guide source record: one guide (a URL) with the media sources that read it. Its key can
     * also be an alias of a guide here: an older build's copy for one source, or the same guide
     * added on two devices. See the one guide source per URL plan → Design S.
     */
    private suspend fun applyEpgSource(record: SyncRecord): Outcome {
        val sourceKey = record.key.itemId
        val payload = if (record.deleted) null else SyncPayloads.decode<SyncPayloads.EpgSource>(record.payload)
        // The record's sources that this device has; a deleted one is left out.
        val linked = payload?.keys.orEmpty().mapNotNull { sync.providerByKey(it) }
        val remoteGuide = payload?.let { receivedGuide(sourceKey, it, linked.firstOrNull()) }
        val target = guideTarget(sourceKey, payload, remoteGuide, linked)
        val local =
            SyncMerge.Local(
                version =
                    listOfNotNull(
                        settingsVersion(SyncKind.EPG_SOURCE, SyncKind.SHARED, sourceKey),
                        target.otherKey?.let { settingsVersion(SyncKind.EPG_SOURCE, SyncKind.SHARED, it) },
                    ).maxOrNull(),
                tombstone = sync.getTombstone(SyncKind.EPG_SOURCE, sourceKey)?.deletedAt,
                provider = payload?.let { providersPresence(it.keys) } ?: Presence.NOT_APPLICABLE,
            )
        return when (val resolution = SyncMerge.resolve(record, local)) {
            Resolution.Upsert -> {
                if (linked.isEmpty()) return Outcome.Deferred
                val guideId =
                    inSettingsApply {
                        val (id, requeue) =
                            upsertGuide(
                                sourceKey,
                                remoteGuide!!,
                                payload.providerKeys.isNotEmpty(),
                                linked.map { it.id },
                                target,
                            )
                        markSettingsVersion(SyncKind.EPG_SOURCE, SyncKind.SHARED, sourceKey, record.hlc)
                        // The guide here has more sources than the record: the other devices get them.
                        if (requeue) sync.queue(SyncKind.EPG_SOURCE, SyncKind.SHARED, sourceKey)
                        id
                    }
                // A changed offset moves the times already in the guide index, after the transaction.
                EpgOffsetShift.reconcile(guideId, settingsDb.epgSourceDao(), EpgIndexer.getInstance(context))
                Outcome.Applied
            }

            Resolution.Delete -> {
                val deleted =
                    inSettingsApply {
                        val gone = deleteGuide(target)
                        sync.upsertTombstone(SettingsTombstoneEntity(SyncKind.EPG_SOURCE, sourceKey, record.hlc))
                        markSettingsVersion(SyncKind.EPG_SOURCE, SyncKind.SHARED, sourceKey, record.hlc)
                        gone
                    }
                deleted?.let { EpgIndexDatabase.getInstance(context).epgIndexDao().deleteBySourceId(it) }
                Outcome.Applied
            }

            else -> {
                skippedOrDeferred(resolution)
            }
        }
    }

    /**
     * The guide [payload] describes, as this device stores it. An automatic Xtream guide source
     * arrives with its login (older builds store it so): kept without it here, like every
     * automatic source on this version.
     */
    private fun receivedGuide(
        sourceKey: String,
        payload: SyncPayloads.EpgSource,
        first: ProviderEntity?,
    ): EpgSourceEntity =
        EpgSourceEntity(
            url = if (first?.type == "XTREAM") AutoXmltvSources.withoutLogin(payload.url, payload.label, first.url) else payload.url,
            label = payload.label,
            timezoneOffsetHours = payload.timezoneOffsetHours,
            enabled = payload.enabled,
            sourceKey = sourceKey,
            // None (an older version's record) or one this version can't use: not set.
            refreshIntervalHours = payload.refreshIntervalHours?.takeIf { EpgRefreshSchedule.isValidInterval(it) },
        )

    /**
     * Writes [remote] to [target] with media sources [providerIds]. [fullList]: the record names
     * every source of the guide, so they replace the links; a record from an older build names one
     * source only, which is added. Returns the guide's id, and whether it must be sent again.
     */
    private suspend fun upsertGuide(
        sourceKey: String,
        remote: EpgSourceEntity,
        fullList: Boolean,
        providerIds: List<Long>,
        target: GuideTarget,
    ): Pair<Long, Boolean> {
        val dao = settingsDb.epgSourceDao()
        val addLinks: suspend (Long, List<Long>) -> Unit = { id, ids -> dao.setLinks(id, dao.getProviderIds(id) + ids) }
        val setLinks: suspend (Long) -> Unit = { id -> if (fullList) dao.setLinks(id, providerIds) else addLinks(id, providerIds) }
        return when (target) {
            is GuideTarget.Own -> {
                writeFields(target.guide, remote)
                setLinks(target.guide.id)
                target.guide.id to false
            }

            // An older build's copy for one source: the guide keeps its other sources.
            is GuideTarget.Alias -> {
                writeFields(target.guide, remote)
                if (target.providerId != null) addLinks(target.guide.id, listOf(target.providerId)) else setLinks(target.guide.id)
                target.guide.id to false
            }

            // First sync: the same guide added on both devices before linking takes this key and
            // keeps the sources of both.
            is GuideTarget.Adopt -> {
                val id = target.guide.id
                sync.adoptSourceKey(target.guide.sourceKey, sourceKey)
                writeFields(target.guide.copy(sourceKey = sourceKey), remote)
                addLinks(id, providerIds)
                id to (dao.getProviderIds(id).toSet() != providerIds.toSet())
            }

            is GuideTarget.NewAlias -> {
                dao.insertAlias(EpgSourceAliasEntity(sourceKey, target.guide.id, target.providerId))
                writeFields(target.guide, remote)
                addLinks(target.guide.id, providerIds)
                target.guide.id to false
            }

            GuideTarget.None -> {
                dao.insertGuide(remote, providerIds) to false
            }
        }
    }

    /** The synced fields of [remote] on [existing]; an interval of null keeps the one here. */
    private suspend fun writeFields(
        existing: EpgSourceEntity,
        remote: EpgSourceEntity,
    ) = settingsDb.epgSourceDao().updateSource(
        existing.copy(
            url = remote.url,
            label = remote.label,
            timezoneOffsetHours = remote.timezoneOffsetHours,
            enabled = remote.enabled,
            refreshIntervalHours = remote.refreshIntervalHours ?: existing.refreshIntervalHours,
        ),
    )

    /**
     * A received deletion of [target]: the guide for its own key or for a guide alias; for an
     * older build's copy, only that source's link, and the guide when no source reads it then.
     * Returns the id of the guide deleted, whose index rows go too.
     */
    private suspend fun deleteGuide(target: GuideTarget): Long? {
        val dao = settingsDb.epgSourceDao()
        val id =
            when (target) {
                is GuideTarget.Own -> {
                    target.guide.id
                }

                is GuideTarget.Alias -> {
                    if (target.providerId == null) {
                        target.guide.id
                    } else {
                        dao.unlink(target.guide.id, target.providerId)
                        target.guide.id.takeIf { dao.getProviderIds(it).isEmpty() }
                    }
                }

                else -> {
                    null
                }
            }
        id?.let { dao.deleteGuide(it) }
        return id
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
                if (key.itemId == AppSettings.KEY_CHILD_PROFILE) providers.childProfileChanged(key.profileKey)
                if (key.itemId in AppSettings.PARENTAL_KEYS) providers.parentalListsChanged()
                inSettingsApply { markSettingsVersion(SyncKind.SETTING, key.profileKey, key.itemId, record.hlc) }
                Outcome.Applied
            }

            else -> {
                skippedOrDeferred(resolution)
            }
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
                val providerId = sync.providerByKey(key.providerKey)?.id ?: return Outcome.Deferred
                inSettingsApply {
                    val filters =
                        if (record.deleted) {
                            CategoryFilters()
                        } else {
                            SyncPayloads.json.decodeFromString(CategoryFiltersSerializer, requireNotNull(record.payload))
                        }
                    providers.applyRemoteCategoryFilters(providerId, key.profileKey, filters)
                    markSettingsVersion(key.kind, key.profileKey, key.providerKey, record.hlc)
                }
                Outcome.AppliedProvider(providerId)
            }

            else -> {
                skippedOrDeferred(resolution)
            }
        }
    }

    /**
     * A profile's own Jellyfin login from a device on an older build (JF-D2): the login travels
     * with the source now, so this one becomes the source's only while the source has none. A
     * deletion, or a login for a source that has one, is only marked as seen. One taken is sent
     * on with the source, so every device of the household ends with the same login.
     */
    private suspend fun applyOldLogin(record: SyncRecord): Outcome {
        val key = record.key
        val local =
            SyncMerge.Local(
                version = settingsVersion(key.kind, key.profileKey, key.providerKey),
                provider = providerPresence(key.providerKey),
                profile = profilePresence(key.profileKey),
            )
        return when (val resolution = SyncMerge.resolve(record, local)) {
            Resolution.Upsert, Resolution.Delete -> {
                val provider = sync.providerByKey(key.providerKey) ?: return Outcome.Deferred
                val login = if (record.deleted) null else SyncPayloads.decode<SyncPayloads.Login>(record.payload)
                val taken = login?.takeIf { it.username.isNotBlank() && provider.type == "JELLYFIN" && !providers.hasLogin(provider) }
                inSettingsApply {
                    taken?.let { providers.applyRemoteLogin(provider.id, it) }
                    markSettingsVersion(key.kind, key.profileKey, key.providerKey, record.hlc)
                }
                if (taken != null) {
                    SettingsSyncQueue.provider(context, provider.id)
                    Outcome.AppliedProvider(provider.id)
                } else {
                    Outcome.Applied
                }
            }

            else -> {
                skippedOrDeferred(resolution)
            }
        }
    }
}
