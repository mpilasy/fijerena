package org.njarasoa.fijerena.core.network.profile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.njarasoa.fijerena.core.network.provider.SettingsTombstoneEntity
import org.njarasoa.fijerena.core.network.sync.SyncKind

@Dao
interface ProfileDao {
    /** Oldest first, so `default` leads and new profiles append in creation order. */
    @Query("SELECT * FROM profiles ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM profiles WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Insert
    suspend fun insert(profile: ProfileEntity)

    @Query("UPDATE profiles SET name = :name, colorIndex = :colorIndex WHERE id = :id")
    suspend fun update(
        id: String,
        name: String,
        colorIndex: Int,
    )

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: SettingsTombstoneEntity)

    /**
     * Deletes the profile and records it for live sync, in one transaction — see
     * [SettingsTombstoneEntity]. [deletedAt] 0 (the default) means "now on the sync clock".
     */
    @Transaction
    suspend fun deleteRecordingTombstone(
        id: String,
        deletedAt: Long = 0,
    ) {
        delete(id)
        insertTombstone(SettingsTombstoneEntity(SyncKind.PROFILE, id, deletedAt))
    }
}
