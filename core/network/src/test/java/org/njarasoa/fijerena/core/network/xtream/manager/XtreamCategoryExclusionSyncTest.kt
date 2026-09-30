package org.njarasoa.fijerena.core.network.xtream.manager

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.CategoryMatcher
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamDao

/** Only categories whose flag changes are written — see docs/plans/20260930_profile-scoped-settings-plan.md. */
class XtreamCategoryExclusionSyncTest {
    private val categoryDao = mockk<XtreamCategoryDao>(relaxed = true)
    private val streamDao = mockk<XtreamStreamDao>(relaxed = true)
    private val seriesDao = mockk<XtreamSeriesDao>(relaxed = true)
    private val hideAdultAndGreek = CategoryFilters(rules = listOf(CategoryMatcher("Adult"), CategoryMatcher("Greek")))

    @Before
    fun setup() {
        // Stored state: Adult already hidden, Greek not yet, Comedy visible.
        every { categoryDao.getAllCategoriesIncludingExcluded(PROVIDER, XtreamCategoryEntity.TYPE_LIVE) } returns
            listOf(
                category("1", "Adult", XtreamCategoryEntity.TYPE_LIVE, excluded = true),
                category("2", "Greek News", XtreamCategoryEntity.TYPE_LIVE, excluded = false),
                category("3", "Comedy", XtreamCategoryEntity.TYPE_LIVE, excluded = false),
            )
        every { categoryDao.getAllCategoriesIncludingExcluded(PROVIDER, XtreamCategoryEntity.TYPE_VOD) } returns emptyList()
        every { categoryDao.getAllCategoriesIncludingExcluded(PROVIDER, XtreamCategoryEntity.TYPE_SERIES) } returns
            listOf(category("9", "Greek Series", XtreamCategoryEntity.TYPE_SERIES, excluded = true))
    }

    @Test
    fun `writes only the categories whose flag changes, and only their streams and series`() =
        runBlocking {
            XtreamCategoryExclusionSync.recompute(categoryDao, streamDao, seriesDao, PROVIDER, CategoryFilters(rules = listOf(CategoryMatcher("Adult"))))

            // Greek News stays visible; Greek Series becomes visible; Adult stays hidden.
            verify(exactly = 0) { categoryDao.setExcluded(PROVIDER, XtreamCategoryEntity.TYPE_LIVE, any(), any()) }
            verify(exactly = 0) { streamDao.setExcludedForCategories(any(), any(), any(), any()) }
            verify { categoryDao.setExcluded(PROVIDER, XtreamCategoryEntity.TYPE_SERIES, listOf("9"), false) }
            verify { seriesDao.setExcludedForCategories(PROVIDER, listOf("9"), false) }
            verify(exactly = 0) { streamDao.syncExcludedFromCategories(any(), any()) }
            verify(exactly = 0) { seriesDao.syncExcludedFromCategories(any()) }
        }

    @Test
    fun `a newly hidden category hides its streams`() =
        runBlocking {
            XtreamCategoryExclusionSync.recompute(categoryDao, streamDao, seriesDao, PROVIDER, hideAdultAndGreek)

            verify { categoryDao.setExcluded(PROVIDER, XtreamCategoryEntity.TYPE_LIVE, listOf("2"), true) }
            verify { streamDao.setExcludedForCategories(PROVIDER, XtreamCategoryEntity.TYPE_LIVE, listOf("2"), true) }
            verify(exactly = 0) { seriesDao.setExcludedForCategories(any(), any(), any()) }
        }

    @Test
    fun `full stream sync re-derives every stream and series flag`() =
        runBlocking {
            XtreamCategoryExclusionSync.recompute(categoryDao, streamDao, seriesDao, PROVIDER, hideAdultAndGreek, fullStreamSync = true)

            verify { categoryDao.setExcluded(PROVIDER, XtreamCategoryEntity.TYPE_LIVE, listOf("2"), true) }
            verify(exactly = 0) { streamDao.setExcludedForCategories(any(), any(), any(), any()) }
            verify { streamDao.syncExcludedFromCategories(PROVIDER, XtreamCategoryEntity.TYPE_LIVE) }
            verify { streamDao.syncExcludedFromCategories(PROVIDER, XtreamCategoryEntity.TYPE_VOD) }
            verify { seriesDao.syncExcludedFromCategories(PROVIDER) }
        }

    private fun category(
        id: String,
        name: String,
        type: String,
        excluded: Boolean,
    ) = XtreamCategoryEntity(id, PROVIDER, name, type = type, excluded = excluded)

    private companion object {
        const val PROVIDER = 4L
    }
}
