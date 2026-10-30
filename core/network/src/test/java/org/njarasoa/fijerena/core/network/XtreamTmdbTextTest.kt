package org.njarasoa.fijerena.core.network

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.tmdb.TmdbNamed
import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendation
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.network.xtream.manager.buildCachedSeriesDetail
import org.njarasoa.fijerena.core.player.api.XtreamResponse
import org.njarasoa.fijerena.core.player.domain.MediaMetadata
import org.njarasoa.fijerena.core.player.domain.SeriesId
import org.njarasoa.fijerena.core.player.domain.TitleFacts
import org.njarasoa.fijerena.core.player.model.MovieInfo
import org.njarasoa.fijerena.core.player.model.VodInfo

/**
 * UX-D4: a film or show page shows TMDB's plot and genres in the app language when TMDB has them,
 * and the source's text (often in another language) only when TMDB has none.
 */
class XtreamTmdbTextTest {
    private val repository = mockk<XtreamRepository>(relaxed = true)
    private val tmdb = mockk<TmdbApiService>(relaxed = true)
    private val provider = XtreamMediaProvider(providerId = 9L, repository = repository, tmdb = tmdb)

    private val greekPlot = "Ένας νεαρός λιοντάρι..."
    private val frenchPlot = "Un jeune lion..."

    private val tmdbFrench =
        TmdbRecommendation(id = 8587, overview = frenchPlot, genres = listOf(TmdbNamed("Animation"), TmdbNamed("Familial")))

    init {
        every { tmdb.hasApiKey() } returns true
        every { tmdb.textLanguage() } returns "fr-FR"
    }

    private fun vod(plot: String = greekPlot) =
        VodInfo(info = MovieInfo(name = "GR - The Lion King", plot = JsonPrimitive(plot), genre = "Κινούμενα σχέδια", tmdbId = "8587"))

    private fun movieRow(
        facts: TitleFacts?,
        fetchedAt: Long = System.currentTimeMillis(),
    ) = XtreamStreamEntity(
        streamId = 500,
        providerId = 9L,
        type = XtreamStreamEntity.TYPE_VOD,
        num = 1,
        name = "GR - The Lion King",
        streamType = "movie",
        categoryId = "1",
        description = greekPlot,
        genre = "Κινούμενα σχέδια",
        tmdbId = "8587",
        detailFetchedAt = fetchedAt,
        facts = facts?.toJson(),
    )

    @Test
    fun tmdbPlotAndGenresWinOverTheSourceText() =
        runTest {
            coEvery { repository.getCachedMovieDetail(500) } returns null
            coEvery { repository.getVodInfo(500) } returns XtreamResponse.Ok(vod())
            coEvery { tmdb.getMovieDetails(8587) } returns tmdbFrench

            val metadata = provider.getMovieDetail("500").getOrThrow().metadata

            assertEquals(frenchPlot, metadata.plot)
            assertEquals("Animation, Familial", metadata.genre)
        }

    @Test
    fun theSourceTextStaysWhenTmdbHasNone() =
        runTest {
            coEvery { repository.getCachedMovieDetail(500) } returns null
            coEvery { repository.getVodInfo(500) } returns XtreamResponse.Ok(vod())
            coEvery { tmdb.getMovieDetails(8587) } returns TmdbRecommendation(id = 8587, overview = "")

            val metadata = provider.getMovieDetail("500").getOrThrow().metadata

            assertEquals(greekPlot, metadata.plot)
            assertEquals("Κινούμενα σχέδια", metadata.genre)
        }

    @Test
    fun theTmdbTextIsStoredWithTheFacts() =
        runTest {
            coEvery { repository.getCachedMovieDetail(500) } returns null
            coEvery { repository.getVodInfo(500) } returns XtreamResponse.Ok(vod())
            coEvery { tmdb.getMovieDetails(8587) } returns tmdbFrench
            val stored = slot<String>()
            coEvery { repository.saveMovieDetailCache(500, any(), any(), any(), any(), any(), capture(stored)) } returns Unit

            provider.getMovieDetail("500")

            val facts = TitleFacts.fromJson(stored.captured)
            assertEquals(frenchPlot, facts.overview)
            assertEquals(listOf("Animation", "Familial"), facts.genres)
            assertEquals("fr-FR", facts.textLanguage)
        }

    @Test
    fun aCachedFilmShowsTheStoredTmdbText() =
        runTest {
            val facts = TitleFacts(overview = frenchPlot, genres = listOf("Animation"), textLanguage = "fr-FR")
            coEvery { repository.getCachedMovieDetail(500) } returns movieRow(facts)

            val metadata = provider.getMovieDetail("500").getOrThrow().metadata

            assertEquals(frenchPlot, metadata.plot)
            assertEquals("Animation", metadata.genre)
            coVerify(exactly = 0) { repository.getVodInfo(any()) }
        }

    @Test
    fun aCachedFilmWithNoTmdbTextIsFetchedAgain() =
        runTest {
            // A row stored before the TMDB text was kept: the old source plot must not stay a week.
            coEvery { repository.getCachedMovieDetail(500) } returns movieRow(facts = null)
            coEvery { repository.getVodInfo(500) } returns XtreamResponse.Ok(vod())
            coEvery { tmdb.getMovieDetails(8587) } returns tmdbFrench

            val metadata = provider.getMovieDetail("500").getOrThrow().metadata

            assertEquals(frenchPlot, metadata.plot)
            coVerify(exactly = 1) { repository.getVodInfo(500) }
        }

    @Test
    fun aCachedFilmInAnotherLanguageIsFetchedAgain() =
        runTest {
            val english = TitleFacts(overview = "A young lion...", textLanguage = "en-US")
            coEvery { repository.getCachedMovieDetail(500) } returns movieRow(english)
            coEvery { repository.getVodInfo(500) } returns XtreamResponse.Ok(vod())
            coEvery { tmdb.getMovieDetails(8587) } returns tmdbFrench

            assertEquals(
                frenchPlot,
                provider
                    .getMovieDetail("500")
                    .getOrThrow()
                    .metadata.plot,
            )
        }

    @Test
    fun withNoTmdbKeyACachedFilmIsServedAsItIs() =
        runTest {
            every { tmdb.hasApiKey() } returns false
            coEvery { repository.getCachedMovieDetail(500) } returns movieRow(facts = null)

            assertEquals(
                greekPlot,
                provider
                    .getMovieDetail("500")
                    .getOrThrow()
                    .metadata.plot,
            )
            coVerify(exactly = 0) { repository.getVodInfo(any()) }
        }

    private val seriesRow =
        XtreamSeriesEntity(
            seriesId = 77,
            providerId = 9L,
            name = "GR - Bluey",
            categoryId = "2",
            plot = greekPlot,
            genre = "Παιδικά",
            tmdbId = "82728",
            contentRating = "TV-Y",
            detailFetchedAt = System.currentTimeMillis(),
            episodesFetchedAt = System.currentTimeMillis(),
        )

    private val episodeRow =
        XtreamEpisodeEntity(
            id = "7701",
            providerId = 9L,
            seriesId = 77,
            season = 1,
            episodeNum = 1,
            title = "Magic Xylophone",
            containerExtension = "mkv",
        )

    @Test
    fun aStoredShowGetsTheTmdbTextWithoutAskingTheSource() =
        runTest {
            coEvery { repository.getCachedSeriesEntity(77) } returns seriesRow
            coEvery { repository.getCachedSeriesDetail(77) } returns buildCachedSeriesDetail(seriesRow, listOf(episodeRow))
            coEvery { tmdb.getTvDetails(82728) } returns
                TmdbRecommendation(id = 82728, overview = frenchPlot, genres = listOf(TmdbNamed("Animation")))

            val metadata = provider.getSeriesDetail(SeriesId("77")).getOrThrow().metadata

            assertEquals(frenchPlot, metadata.plot)
            assertEquals("Animation", metadata.genre)
            coVerify(exactly = 0) { repository.getSeriesInfo(any()) }
            coVerify(exactly = 1) { repository.saveSeriesDetailCache(77, "TV-Y", "82728", any(), any(), any()) }
        }

    @Test
    fun aStoredShowWithTmdbTextInTheAppLanguageAsksNothing() =
        runTest {
            val stored = seriesRow.copy(facts = TitleFacts(overview = frenchPlot, textLanguage = "fr-FR").toJson())
            coEvery { repository.getCachedSeriesEntity(77) } returns stored
            coEvery { repository.getCachedSeriesDetail(77) } returns buildCachedSeriesDetail(stored, listOf(episodeRow))

            assertEquals(
                frenchPlot,
                provider
                    .getSeriesDetail(SeriesId("77"))
                    .getOrThrow()
                    .metadata.plot,
            )
            coVerify(exactly = 0) { tmdb.getTvDetails(any()) }
        }

    @Test
    fun preferTmdbTextKeepsTheSourceTextWhenTmdbHasNone() {
        val source = MediaMetadata(plot = greekPlot, genre = "Δράμα")

        assertEquals(source, source.preferTmdbText())
        assertEquals(
            MediaMetadata(plot = frenchPlot, genre = "Drame", facts = TitleFacts(overview = frenchPlot, genres = listOf("Drame"))),
            source.copy(facts = TitleFacts(overview = frenchPlot, genres = listOf("Drame"))).preferTmdbText(),
        )
    }
}
