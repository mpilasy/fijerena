package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface XtreamCategoryDao {
    @Query("SELECT * FROM xtream_categories WHERE providerId = :providerId AND type = :type ORDER BY categoryName ASC")
    fun getCategories(providerId: Long, type: String): List<XtreamCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(categories: List<XtreamCategoryEntity>)

    @Query("DELETE FROM xtream_categories WHERE providerId = :providerId AND type = :type")
    fun deleteAll(providerId: Long, type: String)

    @Query("SELECT categoryId FROM xtream_categories WHERE providerId = :providerId AND type = :type")
    fun getCategoryIds(providerId: Long, type: String): List<String>

    @Query("SELECT categoryId, contentHash FROM xtream_categories WHERE providerId = :providerId AND type = :type")
    fun getCategoryHashes(
        providerId: Long, 
        type: String
    ): Map<@MapColumn(columnName = "categoryId") String, @MapColumn(columnName = "contentHash") Int>

    @Query("DELETE FROM xtream_categories WHERE providerId = :providerId AND type = :type AND categoryId IN (:ids)")
    fun deleteByIds(providerId: Long, type: String, ids: List<String>)

    @Query("SELECT c.* FROM xtream_categories c LEFT JOIN xtream_category_vectors v ON c.categoryId = v.categoryId AND c.providerId = v.providerId AND c.type = v.type WHERE v.categoryId IS NULL LIMIT :limit")
    fun getCategoriesMissingEmbeddings(limit: Int): List<XtreamCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertVector(vector: XtreamCategoryVectorEntity)

    @Query("SELECT c.*, v.embedding FROM xtream_categories c INNER JOIN xtream_category_vectors v ON c.categoryId = v.categoryId AND c.providerId = v.providerId AND c.type = v.type WHERE c.providerId = :providerId")
    fun getCategoriesWithEmbeddings(providerId: Long): List<XtreamCategoryWithVector>
}
