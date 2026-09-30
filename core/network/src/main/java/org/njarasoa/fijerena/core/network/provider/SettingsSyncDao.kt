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
