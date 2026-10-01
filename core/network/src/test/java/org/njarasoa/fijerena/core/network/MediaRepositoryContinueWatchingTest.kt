package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeWatchStateDao
import org.njarasoa.fijerena.core.network.fixtures.WatchHistoryFixtures.episode
import org.njarasoa.fijerena.core.network.fixtures.WatchHistoryFixtures.movie
import org.njarasoa.fijerena.core.network.fixtures.toWatchStateEntity
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeEntity
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContinueWatchingItem
import org.njarasoa.fijerena.core.player.domain.EpisodeId
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.domain.SeriesId

/**
 * The home screen's Continue Watching shelf: a show stays on it after an episode ends, offering
 * the next one ("Up next"), and leaves after its last episode. Movies show only while mid-watch.
 */
class MediaRepositoryContinueWatchingTest {
    private lateinit var context: Context
    private lateinit var watchStateDao: FakeWatchStateDao
    private lateinit var episodeDao: XtreamEpisodeDao

    @Before
    fun setup() {
        clearAllMocks()
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().postDelayed(any(), any()) } returns true
        every { anyConstructed<Handler>().removeCallbacks(any()) } returns Unit
        every { anyConstructed<Handler>().post(any()) } returns true

        context = mockk(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        // Thumbnails are looked up in the catalogue; there is none here.
        mockkObject(XtreamDatabase.Companion)
        every { XtreamDatabase.getInstance(any()) } returns mockk(relaxed = true)

        watchStateDao = FakeWatchStateDao()
        episodeDao = mockk(relaxed = true)
        // Show 7: S1E1, S1E2, then S2E1. Show 8's episodes aren't cached.
        coEvery { episodeDao.getEpisode(P, any()) } returns null
        coEvery { episodeDao.getNextEpisode(P, any(), any(), any()) } returns null
        listOf(ep("e1", 1, 1), ep("e2", 1, 2), ep("e3", 2, 1)).forEach { e -> coEvery { episodeDao.getEpisode(P, e.id) } returns e }
        coEvery { episodeDao.getNextEpisode(P, 7, 1, 1) } returns ep("e2", 1, 2)
        coEvery { episodeDao.getNextEpisode(P, 7, 1, 2) } returns ep("e3", 2, 1)
    }

    @After
    fun tearDown() = unmockkAll()

    /** First argument is newest. [provider] stands in for the panel, for shows this device never stored. */
    private fun shelf(
        vararg history: WatchedItem,
        provider: MediaProvider? = null,
    ): List<ContinueWatchingItem> {
        history.forEachIndexed { index, item -> watchStateDao.seed(item.toWatchStateEntity(providerId = P, at = 1_000_000L - index)) }
        val repo = MediaRepository(context, P, ProfileEntity.DEFAULT_ID, watchStateDao = watchStateDao, episodeDao = episodeDao)
        provider?.let { repo.setProvider(it) }
        return runBlocking { repo.getContinueWatchingItems() }
    }

    /** A provider listing show 8: S1E1 (x1), S1E2 (x2), and S2E1 (x3) under a season key only. */
    private fun panel(): MediaProvider {
        val provider = mockk<MediaProvider>(relaxed = true)
        coEvery { provider.getSeriesDetail(SeriesId("8")) } returns
            kotlin.Result.success(
                SeriesDetail(
                    id = "8",
                    name = "Show 8",
                    episodes =
                        mapOf(
                            "2" to listOf(EpisodeItem(id = "x3", episodeNumber = 1, title = "Show 8 S2E1")),
                            "1" to listOf(EpisodeItem("x2", 2, "Show 8 S1E2", seasonNumber = 1), EpisodeItem("x1", 1, "Show 8 S1E1", seasonNumber = 1)),
                        ),
                ),
            )
        return provider
    }

    @Test
    fun `a show this device never stored gets its next episode from the provider`() {
        val card = shelf(episode("x2", "8", position = 100, duration = 100, isCompleted = true), provider = panel()).single()
        assertEquals(true, card.upNext)
        assertEquals("Show 8 S2E1", card.subtitle)
        assertEquals(BrowseTarget.Series(SeriesId("8"), EpisodeId("x3")), card.target)
    }

    @Test
    fun `the provider's last episode ends the show there too`() {
        assertEquals(emptyList<ContinueWatchingItem>(), shelf(episode("x3", "8", position = 100, duration = 100, isCompleted = true), provider = panel()))
    }

    @Test
    fun `a finished episode offers the next one`() {
        val card = shelf(episode("e1", "7", position = 2_400_000, duration = 2_400_000, isCompleted = true)).single()
        assertEquals(true, card.upNext)
        assertEquals("Episode e2", card.subtitle)
        assertEquals(BrowseTarget.Series(SeriesId("7"), EpisodeId("e2")), card.target)
    }

    @Test
    fun `the next episode can be in the next season`() {
        val card = shelf(episode("e2", "7", position = 2_390_000, duration = 2_400_000)).single()
        assertEquals(true, card.upNext)
        assertEquals("Episode e3", card.subtitle)
    }

    @Test
    fun `a show leaves after its last episode`() {
        assertEquals(emptyList<ContinueWatchingItem>(), shelf(episode("e3", "7", position = 100, duration = 100, isCompleted = true)))
    }

    @Test
    fun `a show whose episodes are unknown everywhere leaves once finished`() {
        assertEquals(emptyList<ContinueWatchingItem>(), shelf(episode("x1", "8", isCompleted = true, duration = 100, position = 100)))
    }

    @Test
    fun `a mid-watch episode resumes, as before`() {
        val card = shelf(episode("e2", "7", position = 1_200_000, duration = 2_400_000)).single()
        assertEquals(false, card.upNext)
        assertEquals(0.5f, card.progress)
        assertEquals(BrowseTarget.Series(SeriesId("7"), EpisodeId("e2")), card.target)
    }

    @Test
    fun `a barely started episode is offered as up next itself`() {
        val card = shelf(episode("e2", "7", position = 10_000, duration = 2_400_000)).single()
        assertEquals(true, card.upNext)
        assertEquals(BrowseTarget.Series(SeriesId("7"), EpisodeId("e2")), card.target)
    }

    @Test
    fun `the most recent episode decides, not an older half-watched one`() {
        val card =
            shelf(
                episode("e2", "7", position = 2_400_000, duration = 2_400_000, isCompleted = true),
                episode("e1", "7", position = 1_200_000, duration = 2_400_000),
            ).single()
        assertEquals(true, card.upNext)
        assertEquals("Episode e3", card.subtitle)
    }

    @Test
    fun `a finished movie still leaves the shelf`() {
        assertEquals(emptyList<ContinueWatchingItem>(), shelf(movie("m1", position = 100, duration = 100, isCompleted = true)))
    }

    @Test
    fun `shows and movies stay in recency order`() {
        val cards =
            shelf(
                movie("m1", position = 500, duration = 1000),
                episode("e1", "7", position = 2_400_000, duration = 2_400_000, isCompleted = true),
                movie("m2", position = 300, duration = 1000),
            )
        assertEquals(listOf("m1", "7", "m2"), cards.map { it.id })
    }

    private fun ep(
        id: String,
        season: Int,
        number: Int,
    ) = XtreamEpisodeEntity(id = id, seriesId = 7, providerId = P, season = season, episodeNum = number, title = "Episode $id", containerExtension = "mkv")

    private companion object {
        const val P = 1L
    }
}
