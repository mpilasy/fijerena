package org.njarasoa.fijerena.core.network.provider

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import org.njarasoa.fijerena.core.network.xtream.db.SyncClockEntity

/**
 * The change queue of `providers.db`: queueing for values that live outside it (see
 * [org.njarasoa.fijerena.core.network.sync.SettingsSyncQueue]), and reads for the sync client.
 */
@Dao
interface SettingsSyncDao {
    @Query(
        "UPDATE sync_clock SET hlc = MAX(CAST(ROUND((julianday('now') - 2440587.5) * 86400000) AS INTEGER), hlc + 1) " +
            "WHERE id = ${SyncClockEntity.SINGLE_ROW}",
    )
    suspend fun tick()

    @Query(
        "INSERT OR REPLACE INTO sync_outbox (kind, profileId, itemKey, hlc) " +
            "VALUES (:kind, :profileId, :itemKey, (SELECT hlc FROM sync_clock WHERE id = ${SyncClockEntity.SINGLE_ROW}))",
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

    @Query("SELECT providerKey FROM providers WHERE id = :providerId")
    suspend fun providerKey(providerId: Long): String?

    /** Oldest change first — the order the sync client sends them in. */
    @Query("SELECT * FROM sync_outbox ORDER BY hlc ASC LIMIT :limit")
    suspend fun getBatch(limit: Int): List<SettingsOutboxEntity>

    @Query("SELECT hlc FROM sync_clock WHERE id = ${SyncClockEntity.SINGLE_ROW}")
    suspend fun clock(): Long

    /** Entries keyed by a deleted provider (its logins, filters): its own tombstone covers them. */
    @Query("DELETE FROM sync_outbox WHERE itemKey = :providerKey AND kind != 'provider'")
    suspend fun deleteForProviderKey(providerKey: String)

    /** Entries of a deleted profile (its logins, filters, dev mode): its own tombstone covers them. */
    @Query("DELETE FROM sync_outbox WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)
}
