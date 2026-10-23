package org.njarasoa.fijerena.core.network.xtream.manager

import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.ParentalControlsRepository
import org.njarasoa.fijerena.core.network.provider.ParentalRules
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamDao

/**
 * Recomputes the `excluded` flag on categories from the current [CategoryFilters], purely via
 * local DB reads/writes (no network, no session).
 *
 * Only categories carry the flag: a stream or series is hidden when its category is, which the
 * queries check (see XtreamStreamDao / XtreamSeriesDao). So a profile switch or a filter edit
 * writes a few hundred category rows, never the catalogue. See
 * the fast profile switch plan.
 *
 * For a child profile it also sets the `allowed` flag on the films and series that a Parental
 * controls exception lets through from a hidden category (docs/plans/20261020_parental-controls-plan.md).
 * This also writes a few hundred rows at most.
 *
 * Kept independent of [XtreamContentManager] so it can be called directly from a
 * settings-save flow (e.g. `ProviderRepository`) without constructing a session manager.
 */
object XtreamCategoryExclusionSync {
    private const val SQLITE_BATCH_SIZE = 900

    /** Writes only the categories whose stored flag disagrees with [filters], then the `allowed` items again. */
    fun recompute(
        categoryDao: XtreamCategoryDao,
        streamDao: XtreamStreamDao,
        seriesDao: XtreamSeriesDao,
        providerId: Long,
        filters: CategoryFilters,
    ) {
        val liftable = mutableMapOf<String, Set<String>>()
        for (type in listOf(XtreamCategoryEntity.TYPE_LIVE, XtreamCategoryEntity.TYPE_VOD, XtreamCategoryEntity.TYPE_SERIES)) {
            val categories = categoryDao.getAllCategoriesIncludingExcluded(providerId, type)
            val films = type == XtreamCategoryEntity.TYPE_VOD
            val changed =
                categories.filter {
                    it.excluded == filters.shouldShowCategory(it.categoryName, films = films)
                }
            for ((excluded, group) in changed.groupBy { !it.excluded }) {
                group.map { it.categoryId }.chunked(SQLITE_BATCH_SIZE).forEach { ids ->
                    categoryDao.setExcluded(providerId, type, ids, excluded)
                }
            }
            val child = filters.child
            if (child != null && type != XtreamCategoryEntity.TYPE_LIVE) {
                liftable[type] = categories.filter { exceptionsReach(filters, child, it.categoryName, films) }.map { it.categoryId }.toSet()
            }
        }
        streamDao.clearAllowed(providerId)
        seriesDao.clearAllowed(providerId)
        val child = filters.child ?: return
        val films = mutableSetOf<Int>()
        val series = mutableSetOf<Int>()
        val vodCategories = liftable[XtreamCategoryEntity.TYPE_VOD].orEmpty()
        val seriesCategories = liftable[XtreamCategoryEntity.TYPE_SERIES].orEmpty()
        for (entry in child.exceptions) {
            val query = ParentalControlsRepository.ftsQuery(entry) ?: continue
            if (vodCategories.isNotEmpty()) {
                streamDao
                    .matchNames(providerId, query)
                    .filter { it.type == XtreamCategoryEntity.TYPE_VOD && it.categoryId in vodCategories && child.allowsTitle(it.name) }
                    .mapTo(films) { it.id }
            }
            if (seriesCategories.isNotEmpty()) {
                seriesDao
                    .matchNames(providerId, query)
                    .filter { it.categoryId in seriesCategories && child.allowsTitle(it.name) }
                    .mapTo(series) { it.id }
            }
        }
        films.chunked(SQLITE_BATCH_SIZE).forEach { streamDao.setAllowed(providerId, XtreamCategoryEntity.TYPE_VOD, it) }
        series.chunked(SQLITE_BATCH_SIZE).forEach { seriesDao.setAllowed(providerId, it) }
    }

    /**
     * Whether an exception can show a title of this category: [child]'s lists hide the category,
     * it is not an adult one, and the profile's own content filters show it (they apply on top).
     */
    fun exceptionsReach(
        filters: CategoryFilters,
        child: ParentalRules,
        categoryName: String,
        films: Boolean,
    ): Boolean =
        !child.showsCategory(categoryName, films) &&
            !child.isAdultCategory(categoryName) &&
            filters.copy(child = null).shouldShowCategory(categoryName, films)
}
