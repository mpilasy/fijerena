package org.njarasoa.fijerena.core.network.xtream.manager

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.CategoryMatcher
import org.njarasoa.fijerena.core.network.provider.ParentalRules
import org.njarasoa.fijerena.core.network.xtream.db.NameMatch
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamDao

/**
 * Only categories carry the flag, and only those whose flag changes are written — see
 * the fast profile switch plan.
 */
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
    fun `writes only the categories whose flag changes`() {
        XtreamCategoryExclusionSync.recompute(
            categoryDao,
            streamDao,
            seriesDao,
            PROVIDER,
            CategoryFilters(rules = listOf(CategoryMatcher("Adult"))),
        )

        // Greek News stays visible; Greek Series becomes visible; Adult stays hidden.
        verify(exactly = 0) { categoryDao.setExcluded(PROVIDER, XtreamCategoryEntity.TYPE_LIVE, any(), any()) }
        verify { categoryDao.setExcluded(PROVIDER, XtreamCategoryEntity.TYPE_SERIES, listOf("9"), false) }
    }

    @Test
    fun `a newly hidden category is flagged`() {
        XtreamCategoryExclusionSync.recompute(categoryDao, streamDao, seriesDao, PROVIDER, hideAdultAndGreek)

        verify { categoryDao.setExcluded(PROVIDER, XtreamCategoryEntity.TYPE_LIVE, listOf("2"), true) }
        verify(exactly = 0) { categoryDao.setExcluded(PROVIDER, XtreamCategoryEntity.TYPE_SERIES, any(), any()) }
    }

    @Test
    fun `an adult profile has no allowed items`() {
        XtreamCategoryExclusionSync.recompute(categoryDao, streamDao, seriesDao, PROVIDER, hideAdultAndGreek)

        verify { streamDao.clearAllowed(PROVIDER) }
        verify { seriesDao.clearAllowed(PROVIDER) }
        verify(exactly = 0) { streamDao.matchNames(any(), any()) }
        verify(exactly = 0) { streamDao.setAllowed(any(), any(), any()) }
        verify(exactly = 0) { seriesDao.setAllowed(any(), any()) }
    }

    @Test
    fun `an exception allows films and series of hidden categories that are not adult`() {
        every { categoryDao.getAllCategoriesIncludingExcluded(PROVIDER, XtreamCategoryEntity.TYPE_LIVE) } returns
            listOf(category("L1", "Anime TV", XtreamCategoryEntity.TYPE_LIVE, excluded = true))
        every { categoryDao.getAllCategoriesIncludingExcluded(PROVIDER, XtreamCategoryEntity.TYPE_VOD) } returns
            listOf(
                category("anime", "Anime", XtreamCategoryEntity.TYPE_VOD, excluded = true),
                category("kids", "Kids", XtreamCategoryEntity.TYPE_VOD, excluded = false),
                category("xxx", "XXX Adult", XtreamCategoryEntity.TYPE_VOD, excluded = true),
                category("greek", "Greek Anime", XtreamCategoryEntity.TYPE_VOD, excluded = true),
            )
        every { categoryDao.getAllCategoriesIncludingExcluded(PROVIDER, XtreamCategoryEntity.TYPE_SERIES) } returns
            listOf(category("s-anime", "Anime Series", XtreamCategoryEntity.TYPE_SERIES, excluded = true))
        every { streamDao.matchNames(PROVIDER, "\"naruto\"") } returns
            listOf(
                NameMatch(1, XtreamCategoryEntity.TYPE_VOD, "anime", "Naruto the Movie"),
                // Already shown: its category is a kids' one.
                NameMatch(2, XtreamCategoryEntity.TYPE_VOD, "kids", "Naruto Kids"),
                // Never from an adult category.
                NameMatch(3, XtreamCategoryEntity.TYPE_VOD, "xxx", "Naruto XXX"),
                // Hidden by the profile's own filters, which apply on top.
                NameMatch(4, XtreamCategoryEntity.TYPE_VOD, "greek", "Naruto"),
                // Never a channel.
                NameMatch(5, XtreamCategoryEntity.TYPE_LIVE, "L1", "Naruto 24/7"),
            )
        every { seriesDao.matchNames(PROVIDER, "\"naruto\"") } returns
            listOf(
                NameMatch(10, "SERIES", "s-anime", "Naruto Shippuden"),
                // Hidden titles win over exceptions.
                NameMatch(11, "SERIES", "s-anime", "Naruto Hentai"),
            )
        val films = slot<List<Int>>()
        val series = slot<List<Int>>()
        every { streamDao.setAllowed(PROVIDER, XtreamCategoryEntity.TYPE_VOD, capture(films)) } returns Unit
        every { seriesDao.setAllowed(PROVIDER, capture(series)) } returns Unit

        XtreamCategoryExclusionSync.recompute(
            categoryDao,
            streamDao,
            seriesDao,
            PROVIDER,
            CategoryFilters(rules = listOf(CategoryMatcher("Greek")), child = ParentalRules(exceptions = listOf("naruto"))),
        )

        verify { streamDao.clearAllowed(PROVIDER) }
        verify { seriesDao.clearAllowed(PROVIDER) }
        assertEquals(listOf(1), films.captured)
        assertEquals(listOf(10), series.captured)
    }

    @Test
    fun `the full-text match is checked again against the exception`() {
        every { categoryDao.getAllCategoriesIncludingExcluded(PROVIDER, XtreamCategoryEntity.TYPE_VOD) } returns
            listOf(category("anime", "Anime", XtreamCategoryEntity.TYPE_VOD, excluded = true))
        // The index matches words in any order; the exception is a phrase.
        every { streamDao.matchNames(PROVIDER, "\"one piece\"") } returns
            listOf(
                NameMatch(1, XtreamCategoryEntity.TYPE_VOD, "anime", "One Piece Film: Red"),
                NameMatch(2, XtreamCategoryEntity.TYPE_VOD, "anime", "Piece by One"),
            )

        XtreamCategoryExclusionSync.recompute(
            categoryDao,
            streamDao,
            seriesDao,
            PROVIDER,
            CategoryFilters(child = ParentalRules(exceptions = listOf("one piece"))),
        )

        verify { streamDao.setAllowed(PROVIDER, XtreamCategoryEntity.TYPE_VOD, listOf(1)) }
    }

    @Test
    fun `exceptions reach a hidden category that is not adult and that the own filters show`() {
        val child = ParentalRules()
        val filters = CategoryFilters(rules = listOf(CategoryMatcher("Greek")), child = child)

        assertEquals(true, XtreamCategoryExclusionSync.exceptionsReach(filters, child, "Anime", films = true))
        assertEquals(false, XtreamCategoryExclusionSync.exceptionsReach(filters, child, "Kids", films = true))
        assertEquals(false, XtreamCategoryExclusionSync.exceptionsReach(filters, child, "XXX Anime", films = true))
        assertEquals(false, XtreamCategoryExclusionSync.exceptionsReach(filters, child, "Greek Anime", films = true))
    }

    @Test
    fun `allowed items are written in batches`() {
        every { categoryDao.getAllCategoriesIncludingExcluded(PROVIDER, XtreamCategoryEntity.TYPE_VOD) } returns
            listOf(category("anime", "Anime", XtreamCategoryEntity.TYPE_VOD, excluded = true))
        every { streamDao.matchNames(PROVIDER, "\"pokemon\"") } returns
            (1..1000).map { NameMatch(it, XtreamCategoryEntity.TYPE_VOD, "anime", "Pokémon $it") }

        XtreamCategoryExclusionSync.recompute(
            categoryDao,
            streamDao,
            seriesDao,
            PROVIDER,
            CategoryFilters(child = ParentalRules(exceptions = listOf("pokemon"))),
        )

        verify { streamDao.setAllowed(PROVIDER, XtreamCategoryEntity.TYPE_VOD, (1..900).toList()) }
        verify { streamDao.setAllowed(PROVIDER, XtreamCategoryEntity.TYPE_VOD, (901..1000).toList()) }
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
