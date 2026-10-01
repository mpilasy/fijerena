package org.njarasoa.fijerena.core.network.xtream.manager

import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity

/**
 * Recomputes the `excluded` flag on categories from the current [CategoryFilters], purely via
 * local DB reads/writes (no network, no session).
 *
 * Only categories carry the flag: a stream or series is hidden when its category is, which the
 * queries check (see XtreamStreamDao / XtreamSeriesDao). So a profile switch or a filter edit
 * writes a few hundred category rows, never the catalogue. See
 * docs/plans/20261001_fast-profile-switch-plan.md.
 *
 * Kept independent of [XtreamContentManager] so it can be called directly from a
 * settings-save flow (e.g. `ProviderRepository`) without constructing a session manager.
 */
object XtreamCategoryExclusionSync {
    private const val SQLITE_BATCH_SIZE = 900

    /** Writes only the categories whose stored flag disagrees with [filters]. */
    fun recompute(
        categoryDao: XtreamCategoryDao,
        providerId: Long,
        filters: CategoryFilters,
    ) {
        for (type in listOf(XtreamCategoryEntity.TYPE_LIVE, XtreamCategoryEntity.TYPE_VOD, XtreamCategoryEntity.TYPE_SERIES)) {
            val categories = categoryDao.getAllCategoriesIncludingExcluded(providerId, type)
            val changed = categories.filter { it.excluded == filters.shouldShowCategory(it.categoryName) }
            for ((excluded, group) in changed.groupBy { !it.excluded }) {
                group.map { it.categoryId }.chunked(SQLITE_BATCH_SIZE).forEach { ids ->
                    categoryDao.setExcluded(providerId, type, ids, excluded)
                }
            }
        }
    }
}
