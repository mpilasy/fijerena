package org.njarasoa.fijerena.core.network.xmltv.epgindex

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgSourceDao {

    @Query("SELECT * FROM epg_source ORDER BY added_at_ms ASC")
    fun getAllSources(): Flow<List<EpgSourceEntity>>

    @Query("SELECT * FROM epg_source WHERE enabled = 1 ORDER BY added_at_ms ASC")
    suspend fun getEnabledSources(): List<EpgSourceEntity>

    @Query("SELECT * FROM epg_source WHERE id = :id")
    suspend fun getSourceById(id: Long): EpgSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: EpgSourceEntity): Long

    @Update
    suspend fun updateSource(source: EpgSourceEntity)

    @Query("DELETE FROM epg_source WHERE id = :id")
    suspend fun deleteSource(id: Long)

    @Query("DELETE FROM epg_source")
    suspend fun deleteAllSources()

    @Query("UPDATE epg_source SET last_ingested_at_ms = :timestamp, last_error = NULL WHERE id = :id")
    suspend fun markIngested(id: Long, timestamp: Long)

    @Query("UPDATE epg_source SET last_error = :error WHERE id = :id")
    suspend fun markError(id: Long, error: String)

    @Query("SELECT COUNT(*) FROM epg_source")
    suspend fun getSourceCount(): Int
}
