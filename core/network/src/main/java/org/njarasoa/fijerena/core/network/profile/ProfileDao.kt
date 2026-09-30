package org.njarasoa.fijerena.core.network.profile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    /** Oldest first, so `default` leads and new profiles append in creation order. */
    @Query("SELECT * FROM profiles ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<ProfileEntity>

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
}
