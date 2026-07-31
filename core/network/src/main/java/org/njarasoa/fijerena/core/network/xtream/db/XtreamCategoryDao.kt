package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface XtreamCategoryDao {
    @Query("SELECT * FROM xtream_categories WHERE providerId = :providerId AND type = :type AND excluded = 0 ORDER BY categoryName ASC")
    fun getCategories(
        providerId: Long,
        type: String,
    ): List<XtreamCategoryEntity>

    @Query("SELECT * FROM xtream_categories WHERE providerId = :providerId AND type = :type ORDER BY categoryName ASC")
    fun getAllCategoriesIncludingExcluded(
        providerId: Long,
        type: String,
    ): List<XtreamCategoryEntity>

    @Query("UPDATE xtream_categories SET excluded = :excluded WHERE providerId = :providerId AND type = :type AND categoryId IN (:ids)")
    fun setExcluded(
        providerId: Long,
        type: String,
        ids: List<String>,
        excluded: Boolean,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(categories: List<XtreamCategoryEntity>)

    @Query("DELETE FROM xtream_categories WHERE providerId = :providerId AND type = :type")
    fun deleteAll(
        providerId: Long,
        type: String,
    )

    @Query("SELECT categoryId FROM xtream_categories WHERE providerId = :providerId AND type = :type")
    fun getCategoryIds(
        providerId: Long,
        type: String,
    ): List<String>

    @Query("SELECT categoryId, contentHash FROM xtream_categories WHERE providerId = :providerId AND type = :type")
    fun getCategoryHashes(
        providerId: Long,
        type: String,
    ): Map<
        @MapColumn(columnName = "categoryId")
        String,
        @MapColumn(columnName = "contentHash")
        Int,
    >

    @Query("DELETE FROM xtream_categories WHERE providerId = :providerId AND type = :type AND categoryId IN (:ids)")
    fun deleteByIds(
        providerId: Long,
        type: String,
        ids: List<String>,
    )

    @Query("SELECT COUNT(*) FROM xtream_categories WHERE providerId = :providerId AND type = :type")
    fun countCategories(
        providerId: Long,
        type: String,
    ): Int
}
