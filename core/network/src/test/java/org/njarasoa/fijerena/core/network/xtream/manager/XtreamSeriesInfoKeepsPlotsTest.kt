package org.njarasoa.fijerena.core.network.xtream.manager

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeEntity
import org.njarasoa.fijerena.core.player.api.XtreamApiService
import org.njarasoa.fijerena.core.player.api.XtreamResponse
import org.njarasoa.fijerena.core.player.model.Episode
import org.njarasoa.fijerena.core.player.model.EpisodeInfo
import org.njarasoa.fijerena.core.player.model.SeriesInfo

/**
 * A fetch of a show's episode list that does not ask TMDB (the new episode check after a catalogue
 * sync) keeps the summaries TMDB filled in. It replaced the stored rows with Xtream's, which have
 * none, and the series screen then showed no summaries.
 */
class XtreamSeriesInfoKeepsPlotsTest {
    @Test
    fun `a refetched episode list keeps the stored TMDB summaries`() {
        val episodeDao = mockk<XtreamEpisodeDao>(relaxed = true)
        val database = mockk<XtreamDatabase>(relaxed = true)
        every { database.episodeDao() } returns episodeDao
        every { database.runInTransaction(any<Runnable>()) } answers { firstArg<Runnable>().run() }
        val api = mockk<XtreamApiService>()
        val session = mockk<XtreamSessionManager>()
        every { session.apiService } returns api
        val manager =
            XtreamContentManager(
                session,
                database,
                FakeSharedPreferences(),
                ProviderSettings(),
                mockk(relaxed = true),
                P,
            ) { mockk(relaxed = true) }

        every { episodeDao.getEpisodes(P, SERIES) } returns
            listOf(
                XtreamEpisodeEntity("101", SERIES, P, 6, 19, "Precedent Nixin'", "mkv", plot = "Carrie asks Deacon", plotFetchedAt = 42L),
            )
        coEvery { api.getSeriesInfo(SERIES) } returns
            XtreamResponse.Ok(
                SeriesInfo(
                    episodes =
                        mapOf(
                            "6" to
                                listOf(
                                    Episode(
                                        id = "101",
                                        episodeNum = 19,
                                        title = "Precedent Nixin'",
                                        containerExtension = "mkv",
                                        info = EpisodeInfo(),
                                        season = 6,
                                    ),
                                ),
                        ),
                ),
            )
        val stored = slot<List<XtreamEpisodeEntity>>()
        every { episodeDao.insertAll(capture(stored)) } returns Unit

        runBlocking { manager.getSeriesInfo(SERIES) }

        assertEquals("Carrie asks Deacon", stored.captured.single().plot)
        assertEquals(42L, stored.captured.single().plotFetchedAt)
    }

    private companion object {
        const val P = 5L
        const val SERIES = 1998
    }
}
