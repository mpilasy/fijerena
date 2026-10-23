package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface XtreamCategoryDao {
    /**
     * The categories the profile in use sees: those not excluded, and an excluded film or series
     * category that holds an allowed item (a Parental controls exception, see
     * XtreamCategoryExclusionSync). Such a category lists only its allowed items.
     */
    @Query(
        """
        SELECT * FROM xtream_categories WHERE providerId = :providerId AND type = :type AND (
            excluded = 0
            OR (type = 'VOD' AND categoryId IN (SELECT categoryId FROM xtream_streams WHERE providerId = :providerId AND type = 'VOD' AND allowed = 1))
            OR (type = 'SERIES' AND categoryId IN (SELECT categoryId FROM xtream_series WHERE providerId = :providerId AND allowed = 1))
        )
        ORDER BY categoryName ASC
    """,
    )
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

    /** The categories the content filters of the profile in use hide (see XtreamCategoryExclusionSync). */
    @Query("SELECT categoryId FROM xtream_categories WHERE providerId = :providerId AND type = :type AND excluded = 1")
    fun getExcludedCategoryIds(
        providerId: Long,
        type: String,
    ): List<String>

    /**
     * The films ([type] `VOD`) or series (`SERIES`) that a Parental controls exception shows in an
     * excluded category (see XtreamCategoryExclusionSync), by id. Empty for `LIVE`.
     */
    @Query(
        """
        SELECT CAST(streamId AS TEXT) FROM xtream_streams WHERE providerId = :providerId AND type = :type AND allowed = 1
        UNION ALL
        SELECT CAST(seriesId AS TEXT) FROM xtream_series WHERE providerId = :providerId AND :type = 'SERIES' AND allowed = 1
    """,
    )
    fun getAllowedItemIds(
        providerId: Long,
        type: String,
    ): List<String>

    /** The categories of [type] that hold an item of [getAllowedItemIds]: excluded ones that still show. */
    @Query(
        """
        SELECT categoryId FROM xtream_streams WHERE providerId = :providerId AND type = :type AND allowed = 1
        UNION
        SELECT categoryId FROM xtream_series WHERE providerId = :providerId AND :type = 'SERIES' AND allowed = 1
    """,
    )
    fun getAllowedCategoryIds(
        providerId: Long,
        type: String,
    ): List<String>

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
