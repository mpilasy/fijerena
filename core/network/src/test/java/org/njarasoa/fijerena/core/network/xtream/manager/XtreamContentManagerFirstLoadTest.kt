package org.njarasoa.fijerena.core.network.xtream.manager

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.ParentalRules
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.player.api.XtreamApiService
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamStream

/**
 * A first load (nothing stored yet) applies the Parental controls exceptions as the recompute does:
 * a child sees an allowed film of a hidden category, and the row is stored with `allowed` set
 * (docs/plans/20261020_parental-controls-plan.md, Phase 4).
 */
class XtreamContentManagerFirstLoadTest {
    private val streamDao = mockk<XtreamStreamDao>(relaxed = true)
    private val categoryDao = mockk<XtreamCategoryDao>(relaxed = true)
    private val api = mockk<XtreamApiService>()

    private fun manager(filters: CategoryFilters): XtreamContentManager {
        val database = mockk<XtreamDatabase>(relaxed = true)
        every { database.streamDao() } returns streamDao
        every { database.categoryDao() } returns categoryDao
        val session = mockk<XtreamSessionManager>()
        every { session.apiService } returns api
        return XtreamContentManager(session, database, FakeSharedPreferences(), ProviderSettings(), mockk(relaxed = true), P) { filters }
    }

    @Test
    fun `a child sees the films an exception allows in a hidden category`() {
        every { streamDao.getAllStreams(P, VOD) } returns emptyList()
        coEvery { api.getVodCategories() } returns
            listOf(XtreamCategory("kids", "Kids"), XtreamCategory("anime", "Anime"), XtreamCategory("xxx", "XXX"))
        coEvery { api.getVodStreams() } returns
            listOf(
                XtreamStream(name = "Frozen", streamId = 1, categoryId = "kids"),
                XtreamStream(name = "Naruto the Movie", streamId = 2, categoryId = "anime"),
                XtreamStream(name = "Bleach", streamId = 3, categoryId = "anime"),
                XtreamStream(name = "Naruto XXX", streamId = 4, categoryId = "xxx"),
            )
        val stored = slot<List<XtreamStreamEntity>>()
        every { streamDao.insertAll(capture(stored)) } returns Unit

        val result = runBlocking { manager(CategoryFilters(child = ParentalRules(exceptions = listOf("naruto")))).getAllStreams(VOD) }

        assertEquals(listOf(1, 2), (result as Result.Success).data.map { it.streamId })
        assertEquals(listOf(2), stored.captured.filter { it.allowed }.map { it.streamId })
    }

    @Test
    fun `a first category load shows a hidden category with an allowed item`() {
        every { categoryDao.getCategories(P, XtreamCategoryEntity.TYPE_VOD) } returnsMany
            listOf(
                emptyList(),
                listOf(
                    XtreamCategoryEntity("anime", P, "Anime", type = XtreamCategoryEntity.TYPE_VOD, excluded = true),
                    XtreamCategoryEntity("kids", P, "Kids", type = XtreamCategoryEntity.TYPE_VOD),
                ),
            )
        coEvery { api.getVodCategories() } returns
            listOf(XtreamCategory("kids", "Kids"), XtreamCategory("anime", "Anime"), XtreamCategory("drama", "Drama"))

        val result = runBlocking { manager(CategoryFilters(child = ParentalRules())).getVodCategories() }

        assertEquals(listOf("kids", "anime"), (result as Result.Success).data.map { it.categoryId })
    }

    private companion object {
        const val P = 5L
        const val VOD = XtreamStreamEntity.TYPE_VOD
    }
}
