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
}
