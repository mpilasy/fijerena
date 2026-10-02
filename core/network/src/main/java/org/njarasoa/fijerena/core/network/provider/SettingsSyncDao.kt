package org.njarasoa.fijerena.core.network.provider

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import org.njarasoa.fijerena.core.network.xtream.db.SyncClockEntity

/**
 * The sync versions of `providers.db` ([SettingsVersionEntity]): queueing for values that live
 * outside it (see [org.njarasoa.fijerena.core.network.sync.SettingsSyncQueue]), and reads for the
 * sync client and the merge.
 */
@Dao
interface SettingsSyncDao {
    @Query(
        "UPDATE sync_clock SET hlc = MAX(CAST(ROUND((julianday('now') - 2440587.5) * 86400000) AS INTEGER), hlc + 1) " +
            "WHERE id = ${SyncClockEntity.SINGLE_ROW}",
    )
    suspend fun tick()

    @Query(
        "INSERT OR REPLACE INTO sync_version (kind, profileId, itemKey, hlc, pending) " +
            "VALUES (:kind, :profileId, :itemKey, (SELECT hlc FROM sync_clock WHERE id = ${SyncClockEntity.SINGLE_ROW}), 1)",
    )
    suspend fun insertAtClock(
        kind: String,
        profileId: String,
        itemKey: String,
    )

    /** The next tick of the sync clock, for a record with no version row ([org.njarasoa.fijerena.core.network.sync.VolatileRecords]). */
    @Transaction
    suspend fun nextClock(): Long {
        tick()
        return clock()
    }

    /** Queues one key at the next tick of the sync clock. */
    @Transaction
    suspend fun queue(
        kind: String,
        profileId: String,
        itemKey: String,
    ) {
        tick()
        insertAtClock(kind, profileId, itemKey)
    }

    /** Sent: no longer pending — unless it changed again meanwhile ([hlc] no longer matches). */
    @Query("UPDATE sync_version SET pending = 0 WHERE kind = :kind AND profileId = :profileId AND itemKey = :itemKey AND hlc = :hlc")
    suspend fun markSent(
        kind: String,
        profileId: String,
        itemKey: String,
        hlc: Long,
    )

    @Query("DELETE FROM sync_version WHERE kind = :kind AND profileId = :profileId AND itemKey = :itemKey")
    suspend fun deleteVersion(
        kind: String,
        profileId: String,
        itemKey: String,
    )

    @Query("SELECT EXISTS(SELECT 1 FROM sync_version WHERE pending = 1)")
    suspend fun hasPending(): Boolean

    /** Linking to a server: it has seen nothing from here yet. */
    @Query("UPDATE sync_version SET pending = 1")
    suspend fun markAllPending()

    // --- Seeding: on linking, everything that already exists is queued once, at its own time. ---

    @Query("INSERT OR IGNORE INTO sync_version SELECT 'provider', 'shared', providerKey, createdAt, 1 FROM providers")
    suspend fun seedProviders()

    @Query("INSERT OR IGNORE INTO sync_version SELECT 'profile', 'shared', id, createdAt, 1 FROM profiles")
    suspend fun seedProfiles()

    @Query("INSERT OR IGNORE INTO sync_version SELECT 'epg_source', 'shared', source_key, added_at_ms, 1 FROM epg_source")
    suspend fun seedEpgSources()

    @Query("INSERT OR IGNORE INTO sync_version SELECT kind, 'shared', itemKey, deletedAt, 1 FROM sync_tombstone")
    suspend fun seedTombstones()

    /** Seeding a value kept in SharedPreferences. */
    @Query("INSERT OR IGNORE INTO sync_version (kind, profileId, itemKey, hlc, pending) VALUES (:kind, :profileId, :itemKey, :hlc, 1)")
    suspend fun seed(
        kind: String,
        profileId: String,
        itemKey: String,
        hlc: Long,
    )

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun providerById(id: Long): ProviderEntity?

    // --- Applying records received from another device (live sync phase 5) ---

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsertVersion(version: SettingsVersionEntity)

    /** A received clock value: this device's next change must come after it. */
    @Query("UPDATE sync_clock SET hlc = MAX(hlc, :remote) WHERE id = ${SyncClockEntity.SINGLE_ROW}")
    suspend fun receive(remote: Long)

    /** While set, [SettingsSyncTriggers] queue nothing — see [SyncClockEntity.applying]. */
    @Query("UPDATE sync_clock SET applying = :applying WHERE id = ${SyncClockEntity.SINGLE_ROW}")
    suspend fun setApplying(applying: Boolean)

    @Query("SELECT * FROM sync_tombstone WHERE kind = :kind AND itemKey = :itemKey")
    suspend fun getTombstone(
        kind: String,
        itemKey: String,
    ): SettingsTombstoneEntity?

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsertTombstone(tombstone: SettingsTombstoneEntity)

    /** Whether the server has ever had this key from here or sent it here — a version that isn't pending. */
    @Query("SELECT EXISTS(SELECT 1 FROM sync_version WHERE kind = :kind AND itemKey = :itemKey AND pending = 0)")
    suspend fun knownToServer(
        kind: String,
        itemKey: String,
    ): Boolean

    @Query("SELECT * FROM providers")
    suspend fun allProviders(): List<ProviderEntity>

    @Query("SELECT * FROM epg_source WHERE provider_id = :providerId AND url = :url")
    suspend fun sourcesAt(
        providerId: Long,
        url: String,
    ): List<EpgSourceEntity>

    /** A local provider adopting the key another device gave the same provider (first sync). */
    @Transaction
    suspend fun adoptProviderKey(
        oldKey: String,
        newKey: String,
    ) {
        renameProviderKey(oldKey, newKey)
        renameVersionKeys(oldKey, newKey)
    }

    @Query("UPDATE providers SET providerKey = :newKey WHERE providerKey = :oldKey")
    suspend fun renameProviderKey(
        oldKey: String,
        newKey: String,
    )

    @Query(
        "UPDATE OR REPLACE sync_version SET itemKey = :newKey WHERE itemKey = :oldKey AND kind IN ('provider', 'provider_login', 'category_filters')",
    )
    suspend fun renameVersionKeys(
        oldKey: String,
        newKey: String,
    )

    /** A local EPG source adopting the key another device gave the same source (first sync). */
    @Transaction
    suspend fun adoptSourceKey(
        oldKey: String,
        newKey: String,
    ) {
        renameSourceKey(oldKey, newKey)
        renameSourceVersion(oldKey, newKey)
    }

    @Query("UPDATE epg_source SET source_key = :newKey WHERE source_key = :oldKey")
    suspend fun renameSourceKey(
        oldKey: String,
        newKey: String,
    )

    @Query("UPDATE OR REPLACE sync_version SET itemKey = :newKey WHERE itemKey = :oldKey AND kind = 'epg_source'")
    suspend fun renameSourceVersion(
        oldKey: String,
        newKey: String,
    )

    @Query("SELECT * FROM providers WHERE providerKey = :providerKey")
    suspend fun providerByKey(providerKey: String): ProviderEntity?

    @Query("SELECT * FROM epg_source WHERE source_key = :sourceKey")
    suspend fun sourceByKey(sourceKey: String): EpgSourceEntity?

    @Query("SELECT providerKey FROM providers WHERE id = :providerId")
    suspend fun providerKey(providerId: Long): String?

    /** Local changes not yet sent, oldest first — the order the sync client sends them in. */
    @Query("SELECT * FROM sync_version WHERE pending = 1 ORDER BY hlc ASC LIMIT :limit")
    suspend fun getPending(limit: Int): List<SettingsVersionEntity>

    @Query("SELECT * FROM sync_version WHERE kind = :kind AND profileId = :profileId AND itemKey = :itemKey")
    suspend fun get(
        kind: String,
        profileId: String,
        itemKey: String,
    ): SettingsVersionEntity?

    @Query("SELECT hlc FROM sync_clock WHERE id = ${SyncClockEntity.SINGLE_ROW}")
    suspend fun clock(): Long

    /** Entries keyed by a deleted provider (its logins, filters): its own tombstone covers them. */
    @Query("DELETE FROM sync_version WHERE itemKey = :providerKey AND kind != 'provider'")
    suspend fun deleteForProviderKey(providerKey: String)

    /** Entries of a deleted profile (its logins, filters, dev mode): its own tombstone covers them. */
    @Query("DELETE FROM sync_version WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)
}
