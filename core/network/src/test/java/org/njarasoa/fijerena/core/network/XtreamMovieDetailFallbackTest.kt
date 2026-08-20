package org.njarasoa.fijerena.core.network

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.player.api.XtreamResponse

/**
 * A movie needs no metadata to play — the stream URL is built from its id, with the extension
 * defaulting to mp4. So a provider that answers `get_vod_info` with nothing must not cost the
 * viewer the film itself, only its synopsis.
 */
class XtreamMovieDetailFallbackTest {
    private val repository = mockk<XtreamRepository>(relaxed = true)
    private val tmdb = mockk<TmdbApiService>(relaxed = true)
    private val provider = XtreamMediaProvider(providerId = 9L, repository = repository, tmdb = tmdb)

    private val catalogueRow =
        XtreamStreamEntity(
            streamId = 149880,
            providerId = 9L,
            type = XtreamStreamEntity.TYPE_VOD,
            num = 1,
            name = "BlacKkKlansman (2018)",
            streamType = "movie",
            categoryId = "171",
        )

    @Test
    fun anEmptyDetailResponseStillOpensTheMovie() =
        runTest {
            coEvery { repository.getVodInfo(149880) } returns XtreamResponse.Unavailable(149880, "get_vod_info")
            coEvery { repository.getCachedMovieDetail(149880) } returns catalogueRow

            val result = provider.getMovieDetail("149880")

            assertTrue("expected the catalogue row to stand in, got $result", result.isSuccess)
            assertEquals("BlacKkKlansman (2018)", result.getOrThrow().name)
        }

    @Test
    fun withNoLocalRowEitherThereIsNothingToShow() =
        runTest {
            coEvery { repository.getVodInfo(149880) } returns XtreamResponse.Unavailable(149880, "get_vod_info")
            coEvery { repository.getCachedMovieDetail(149880) } returns null

            val result = provider.getMovieDetail("149880")

            assertTrue(result.isFailure)
        }

    @Test
    fun aCallThatNeverCompletedIsReportedAsIs() =
        runTest {
            // Nothing else will work either, playback included — standing in a stale row here
            // would only move the failure to the moment the viewer presses play.
            coEvery { repository.getVodInfo(149880) } returns XtreamResponse.Failed(java.net.UnknownHostException("no dns"))
            coEvery { repository.getCachedMovieDetail(149880) } returns catalogueRow

            val result = provider.getMovieDetail("149880")

            assertTrue(result.isFailure)
        }
}
