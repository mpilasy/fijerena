package org.njarasoa.fijerena.core.network

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.tmdb.TmdbSeasonResponse
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesEntity
import org.njarasoa.fijerena.core.player.api.XtreamResponse
import org.njarasoa.fijerena.core.player.domain.SeriesId
import org.njarasoa.fijerena.core.player.model.Episode
import org.njarasoa.fijerena.core.player.model.EpisodeInfo
import org.njarasoa.fijerena.core.player.model.SeriesDetails
import org.njarasoa.fijerena.core.player.model.SeriesInfo

/**
 * Fetching a show's episode list again shouldn't repeat TMDB work already done. See
 * docs/plans/archive/20261002_catalog-sync-cache-churn-plan.md, Phase 3.
 */
class XtreamSeriesTmdbCallsTest {
    private val repository = mockk<XtreamRepository>(relaxed = true)
    private val tmdb = mockk<TmdbApiService>(relaxed = true)
    private val provider = XtreamMediaProvider(providerId = 9L, repository = repository, tmdb = tmdb)

    private val day = 24 * 3600 * 1000L

    private fun row(detailFetchedAt: Long) =
        XtreamSeriesEntity(
            seriesId = 30874,
            providerId = 9L,
            name = "Law & Order (1990)",
            categoryId = "20",
            contentRating = "TV-14",
            tmdbId = "549",
            detailFetchedAt = detailFetchedAt,
        )

    // Season 1 has a synopsis stored from an earlier visit, season 2 has none anywhere.
    private val fromXtream =
        SeriesInfo(
            info = SeriesDetails(name = "Law & Order", plot = JsonPrimitive("In the criminal justice system…"), releaseDate = "1990-09-13"),
            episodes =
                mapOf(
                    "1" to
                        listOf(
                            Episode(
                                id = "101",
                                episodeNum = 1,
                                title = "Prescription for Death",
                                containerExtension = "mkv",
                                info = EpisodeInfo(),
                                season = 1,
                            ),
                        ),
                    "2" to
                        listOf(
                            Episode(
                                id = "201",
                                episodeNum = 1,
                                title = "Confession",
                                containerExtension = "mkv",
                                info = EpisodeInfo(),
                                season = 2,
                            ),
                        ),
                ),
        )

    init {
        every { tmdb.hasApiKey() } returns true
        coEvery { tmdb.getSeason(any(), any()) } returns TmdbSeasonResponse()
        coEvery { repository.getSeriesInfo(30874) } returns XtreamResponse.Ok(fromXtream)
        coEvery { repository.getPersistedEpisodePlots(30874) } returns mapOf("101" to "A doctor is suspected…")
    }

    @Test
    fun storedSynopsesAreReadBeforeTheNewListReplacesThem() =
        runTest {
            coEvery { repository.getCachedSeriesEntity(30874) } returns row(System.currentTimeMillis() - day)

            provider.getSeriesDetail(SeriesId("30874"))

            coVerifyOrder {
                repository.getPersistedEpisodePlots(30874)
                repository.getSeriesInfo(30874)
            }
        }

    @Test
    fun onlySeasonsStillMissingASynopsisAreAskedFor() =
        runTest {
            coEvery { repository.getCachedSeriesEntity(30874) } returns row(System.currentTimeMillis() - day)

            val detail = provider.getSeriesDetail(SeriesId("30874")).getOrThrow()

            coVerify(exactly = 1) { tmdb.getSeason(549, 2) }
            coVerify(exactly = 0) { tmdb.getSeason(549, 1) }
            assertEquals(
                "A doctor is suspected…",
                detail.episodes
                    .getValue("1")
                    .single()
                    .metadata.plot,
            )
        }

    @Test
    fun freshTmdbDetailsAreNotFetchedAgain() =
        runTest {
            coEvery { repository.getCachedSeriesEntity(30874) } returns row(System.currentTimeMillis() - day)

            provider.getSeriesDetail(SeriesId("30874"))

            coVerify(exactly = 0) { tmdb.getTvDetails(any()) }
        }

    @Test
    fun staleTmdbDetailsAreFetchedAgain() =
        runTest {
            coEvery { repository.getCachedSeriesEntity(30874) } returns row(System.currentTimeMillis() - 8 * day)

            provider.getSeriesDetail(SeriesId("30874"))

            coVerify(exactly = 1) { tmdb.getTvDetails(549) }
        }
}
