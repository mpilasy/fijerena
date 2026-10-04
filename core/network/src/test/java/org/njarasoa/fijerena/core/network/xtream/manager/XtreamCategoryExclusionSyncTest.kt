package org.njarasoa.fijerena.core.network.xtream.manager

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.CategoryMatcher
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity

/**
 * Only categories carry the flag, and only those whose flag changes are written — see
 * docs/plans/archive/20261001_fast-profile-switch-plan.md.
 */
class XtreamCategoryExclusionSyncTest {
    private val categoryDao = mockk<XtreamCategoryDao>(relaxed = true)
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
    fun `writes only the categories whose flag changes`() {
        XtreamCategoryExclusionSync.recompute(categoryDao, PROVIDER, CategoryFilters(rules = listOf(CategoryMatcher("Adult"))))

        // Greek News stays visible; Greek Series becomes visible; Adult stays hidden.
        verify(exactly = 0) { categoryDao.setExcluded(PROVIDER, XtreamCategoryEntity.TYPE_LIVE, any(), any()) }
        verify { categoryDao.setExcluded(PROVIDER, XtreamCategoryEntity.TYPE_SERIES, listOf("9"), false) }
    }

    @Test
    fun `a newly hidden category is flagged`() {
        XtreamCategoryExclusionSync.recompute(categoryDao, PROVIDER, hideAdultAndGreek)

        verify { categoryDao.setExcluded(PROVIDER, XtreamCategoryEntity.TYPE_LIVE, listOf("2"), true) }
        verify(exactly = 0) { categoryDao.setExcluded(PROVIDER, XtreamCategoryEntity.TYPE_SERIES, any(), any()) }
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
