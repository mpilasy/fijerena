package org.njarasoa.fijerena.core.network.xtream.manager

import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity

/**
 * Recomputes the `excluded` flag on categories/streams/series from the current
 * [CategoryFilters], purely via local DB reads/writes (no network, no session).
 *
 * Kept independent of [XtreamContentManager] so it can be called directly from a
 * settings-save flow (e.g. `ProviderRepository`) without constructing a session manager.
 */
object XtreamCategoryExclusionSync {
    private const val SQLITE_DELETE_BATCH_SIZE = 900

    suspend fun recompute(
        categoryDao: XtreamCategoryDao,
        streamDao: XtreamStreamDao,
        seriesDao: XtreamSeriesDao,
        providerId: Long,
        filters: CategoryFilters,
    ) {
        for (type in listOf(XtreamCategoryEntity.TYPE_LIVE, XtreamCategoryEntity.TYPE_VOD, XtreamCategoryEntity.TYPE_SERIES)) {
            val categories = categoryDao.getAllCategoriesIncludingExcluded(providerId, type)
            val toExclude = categories.filterNot { filters.shouldShowCategory(it.categoryName) }.map { it.categoryId }
            val toInclude = categories.filter { filters.shouldShowCategory(it.categoryName) }.map { it.categoryId }
            toExclude.chunked(SQLITE_DELETE_BATCH_SIZE).forEach { categoryDao.setExcluded(providerId, type, it, excluded = true) }
            toInclude.chunked(SQLITE_DELETE_BATCH_SIZE).forEach { categoryDao.setExcluded(providerId, type, it, excluded = false) }
        }
        streamDao.syncExcludedFromCategories(providerId, XtreamStreamEntity.TYPE_LIVE)
        streamDao.syncExcludedFromCategories(providerId, XtreamStreamEntity.TYPE_VOD)
        seriesDao.syncExcludedFromCategories(providerId)
    }
}
