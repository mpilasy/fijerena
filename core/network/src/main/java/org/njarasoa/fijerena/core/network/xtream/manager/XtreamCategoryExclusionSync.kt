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

    /**
     * By default only categories whose flag actually changes are written, and only their streams
     * and series: a filter edit or a profile switch then touches a few thousand rows instead of the
     * whole catalogue (~3 s for 280k rows on a TV emulator). [fullStreamSync] re-derives every
     * stream and series flag from its category instead — for the end of a content sync, where
     * rows were just rewritten. See docs/plans/20260930_profile-scoped-settings-plan.md.
     */
    suspend fun recompute(
        categoryDao: XtreamCategoryDao,
        streamDao: XtreamStreamDao,
        seriesDao: XtreamSeriesDao,
        providerId: Long,
        filters: CategoryFilters,
        fullStreamSync: Boolean = false,
    ) {
        for (type in listOf(XtreamCategoryEntity.TYPE_LIVE, XtreamCategoryEntity.TYPE_VOD, XtreamCategoryEntity.TYPE_SERIES)) {
            val categories = categoryDao.getAllCategoriesIncludingExcluded(providerId, type)
            // A category needs writing when its stored flag disagrees with the filters.
            val changed = categories.filter { it.excluded == filters.shouldShowCategory(it.categoryName) }
            for ((excluded, group) in changed.groupBy { !it.excluded }) {
                group.map { it.categoryId }.chunked(SQLITE_DELETE_BATCH_SIZE).forEach { ids ->
                    categoryDao.setExcluded(providerId, type, ids, excluded)
                    if (!fullStreamSync) {
                        if (type == XtreamCategoryEntity.TYPE_SERIES) {
                            seriesDao.setExcludedForCategories(providerId, ids, excluded)
                        } else {
                            streamDao.setExcludedForCategories(providerId, type, ids, excluded)
                        }
                    }
                }
            }
        }
        if (fullStreamSync) {
            streamDao.syncExcludedFromCategories(providerId, XtreamStreamEntity.TYPE_LIVE)
            streamDao.syncExcludedFromCategories(providerId, XtreamStreamEntity.TYPE_VOD)
            seriesDao.syncExcludedFromCategories(providerId)
        }
    }
}
