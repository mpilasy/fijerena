package org.njarasoa.fijerena.core.network.provider

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgPipelineStatsDao {

    @Query("SELECT * FROM epg_pipeline_stats WHERE id = 1")
    fun getLatestStats(): Flow<EpgPipelineStatsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: EpgPipelineStatsEntity)

    @Query("DELETE FROM epg_pipeline_stats")
    suspend fun clearStats()
}
