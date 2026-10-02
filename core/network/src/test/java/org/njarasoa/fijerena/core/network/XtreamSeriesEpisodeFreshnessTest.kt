package org.njarasoa.fijerena.core.network

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesEntity
import org.njarasoa.fijerena.core.player.api.XtreamResponse
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.domain.SeriesId

/**
 * A stored episode list is reused until the catalogue sync says the series changed (it clears
 * `episodesFetchedAt`) or 30 days pass. See docs/plans/20261002_catalog-sync-cache-churn-plan.md,
 * Phase 2.
 */
class XtreamSeriesEpisodeFreshnessTest {
    private val repository = mockk<XtreamRepository>(relaxed = true)
    private val tmdb = mockk<TmdbApiService>(relaxed = true)
    private val provider = XtreamMediaProvider(providerId = 9L, repository = repository, tmdb = tmdb)

    private val day = 24 * 3600 * 1000L

    private fun row(episodesFetchedAt: Long?) =
        XtreamSeriesEntity(
            seriesId = 30874,
            providerId = 9L,
            name = "Law & Order (1990)",
            categoryId = "20",
            episodesFetchedAt = episodesFetchedAt,
        )

    private val stored =
        SeriesDetail(
            id = "30874",
            name = "Law & Order (1990)",
            episodes = mapOf("1" to listOf(EpisodeItem(id = "1", episodeNumber = 1, title = "Prescription for Death", seasonNumber = 1))),
        )

    init {
        coEvery { repository.getCachedSeriesDetail(30874) } returns stored
        coEvery { repository.getSeriesInfo(any()) } returns XtreamResponse.Unavailable(30874, "get_series_info")
    }

    @Test
    fun aListFetchedThreeWeeksAgoIsServedFromDisk() =
        runTest {
            coEvery { repository.getCachedSeriesEntity(30874) } returns row(System.currentTimeMillis() - 21 * day)

            val result = provider.getSeriesDetail(SeriesId("30874"))

            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { repository.getSeriesInfo(any()) }
        }

    @Test
    fun aListOlderThanThirtyDaysIsFetchedAgain() =
        runTest {
            coEvery { repository.getCachedSeriesEntity(30874) } returns row(System.currentTimeMillis() - 31 * day)

            provider.getSeriesDetail(SeriesId("30874"))

            coVerify { repository.getSeriesInfo(30874) }
        }

    @Test
    fun aSeriesTheSyncMarkedChangedIsFetchedAgainEvenIfHeldInMemory() =
        runTest {
            coEvery { repository.getCachedSeriesEntity(30874) } returns row(System.currentTimeMillis() - day)
            provider.getSeriesDetail(SeriesId("30874")) // now held in memory
            coEvery { repository.getCachedSeriesEntity(30874) } returns row(null)

            provider.getSeriesDetail(SeriesId("30874"))

            coVerify { repository.getSeriesInfo(30874) }
        }
}
