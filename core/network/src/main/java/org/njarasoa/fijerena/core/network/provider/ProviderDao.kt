package org.njarasoa.fijerena.core.network.provider

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY isActive DESC, name COLLATE NOCASE ASC")
    fun getAllProviders(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY isActive DESC, name COLLATE NOCASE ASC")
    suspend fun getAllProvidersList(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProvider(): ProviderEntity?

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun getProviderById(id: Long): ProviderEntity?

    @Query("SELECT COUNT(*) FROM providers")
    suspend fun getProviderCount(): Int

    @Insert
    suspend fun insertProvider(provider: ProviderEntity): Long

    @Update
    suspend fun updateProvider(provider: ProviderEntity)

    @Delete
    suspend fun deleteProvider(provider: ProviderEntity)

    @Query("UPDATE providers SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE providers SET isActive = 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun activateProvider(
        id: Long,
        timestamp: Long = System.currentTimeMillis(),
    )

    // inserted/updated/deleted are nullable and COALESCE onto the existing value when null: a
    // failed sync (delta unknown) must leave the last real delta in place rather than overwrite
    // it with zeros, which would read identically to a sync that genuinely found no changes.
    @Query(
        """
        UPDATE providers SET lastSyncedAtMs = :timestamp, lastSyncDurationMs = :durationMs, lastSyncError = :error,
            lastSyncInserted = COALESCE(:inserted, lastSyncInserted),
            lastSyncUpdated = COALESCE(:updated, lastSyncUpdated),
            lastSyncDeleted = COALESCE(:deleted, lastSyncDeleted)
        WHERE id = :id
        """,
    )
    suspend fun updateSyncStats(
        id: Long,
        timestamp: Long,
        durationMs: Long,
        error: String?,
        inserted: Int? = null,
        updated: Int? = null,
        deleted: Int? = null,
    )
}
