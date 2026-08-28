package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeWatchStateDao
import org.njarasoa.fijerena.core.network.fixtures.WatchHistoryFixtures.anonymousEpisode
import org.njarasoa.fijerena.core.network.fixtures.WatchHistoryFixtures.channel
import org.njarasoa.fijerena.core.network.fixtures.WatchHistoryFixtures.episode
import org.njarasoa.fijerena.core.network.fixtures.WatchHistoryFixtures.movie
import org.njarasoa.fijerena.core.network.fixtures.WatchHistoryFixtures.seriesUnknownEpisode
import org.njarasoa.fijerena.core.network.fixtures.toWatchStateEntity
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.EpisodeId
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.SeriesId

/**
 * Ordering, filtering and dedup rules of the merged "Recent" list — the single row that
 * replaced the separate Continue Watching and Last Watched categories.
 *
 * Reads `watch_state` (via [FakeWatchStateDao]) rather than the blob since Phase 3 of
 * docs/plans/watch-state-durable-storage-plan.md. [repositoryWith] seeds rows with decreasing
 * `lastPlayedAt` in argument order, standing in for the blob's old "index 0 is newest" convention
 * that the real writers no longer produce directly — recency now comes from a clock, not a list
 * position — so relative order across fixture calls is explicit rather than incidental.
 */
class MediaRepositoryRecentItemsTest {
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var watchStateDao: FakeWatchStateDao

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
        sharedPreferences = mockk(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns mockk(relaxed = true)

        watchStateDao = FakeWatchStateDao()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** First argument is newest: seeded with the largest `lastPlayedAt`, decreasing from there. */
    private fun repositoryWith(vararg history: WatchedItem): MediaRepository {
        val base = 1_000_000L
        history.forEachIndexed { index, item ->
            watchStateDao.seed(item.toWatchStateEntity(providerId = 1L, at = base - index))
        }
        return MediaRepository(context, 1L, watchStateDao = watchStateDao)
    }

    private fun MediaRepository.fetchRecent(contentType: String): List<MediaItem> =
        runBlocking { getRecentItemsFromWatchState(contentType) }

    @Test
    fun ordersInProgressBeforeRest() {
        val repository =
            repositoryWith(
                movie("finished", position = 100L, duration = 100L, isCompleted = true),
                movie("half", position = 50L, duration = 100L),
                movie("untouched"),
                movie("started", position = 10L, duration = 100L),
            )

        assertEquals(
            listOf("half", "started", "finished", "untouched"),
            repository.fetchRecent(ContentType.MOVIES).map { it.id },
        )
    }

    @Test
    fun liveTvIsPureRecency() {
        val repository = repositoryWith(channel("bbc"), channel("cnn"), channel("arte"))

        assertEquals(
            listOf("bbc", "cnn", "arte"),
            repository.fetchRecent(ContentType.LIVE_TV).map { it.id },
        )
    }

    @Test
    fun tvShowsCollapseToOneCardPerSeries() {
        val repository =
            repositoryWith(
                episode("s1e3", seriesId = "s1", position = 30L, duration = 100L),
                episode("s1e2", seriesId = "s1", position = 100L, duration = 100L, isCompleted = true),
                episode("s1e1", seriesId = "s1", position = 100L, duration = 100L, isCompleted = true),
                episode("s2e1", seriesId = "s2", position = 100L, duration = 100L, isCompleted = true),
            )

        val recent = repository.fetchRecent(ContentType.TV_SHOWS)

        assertEquals(listOf("s1", "s2"), recent.map { it.id })
        assertEquals(BrowseTarget.Series(SeriesId("s1"), resumeEpisodeId = EpisodeId("s1e3")), recent.first().target)
    }

    @Test
    fun tvShowsSeriesCardCarriesResumeMetadata() {
        val repository = repositoryWith(episode("s1e3", seriesId = "s1", position = 30L, duration = 100L))

        val card = repository.fetchRecent(ContentType.TV_SHOWS).single()

        assertEquals("s1", card.id)
        assertEquals("Series s1", card.name)
        assertEquals(BrowseTarget.Series(SeriesId("s1"), resumeEpisodeId = EpisodeId("s1e3")), card.target)
    }

    @Test
    fun anEpisodeThatKnowsItselfButNotItsShowStaysAnEpisodeCard() {
        val repository = repositoryWith(seriesUnknownEpisode("orphan", position = 30L, duration = 100L))

        val card = repository.fetchRecent(ContentType.TV_SHOWS).single()

        assertEquals("orphan", card.id)
        assertEquals(BrowseTarget.Episode(episodeId = EpisodeId("orphan")), card.target)
    }

    @Test
    fun aRowWithNoIdentityAtAllStillPlaysAsAnEpisode() {
        // Six rows on the test phone are in this shape. itemId is the episode's stream id — the
        // player is the only thing that writes these — so the card must route there, not to a
        // series the provider has no such id for.
        val repository = repositoryWith(anonymousEpisode("242136", position = 30L, duration = 100L))

        val card = repository.fetchRecent(ContentType.TV_SHOWS).single()

        assertEquals("242136", card.id)
        assertEquals(BrowseTarget.Episode(episodeId = EpisodeId("242136")), card.target)
    }

    @Test
    fun respectsResumeBandBoundaries() {
        val repository =
            repositoryWith(
                movie("barelyStarted", position = 1L, duration = 100L),
                movie("almostDone", position = 96L, duration = 100L),
                movie("lowerBound", position = 2L, duration = 100L),
                movie("upperBound", position = 95L, duration = 100L),
            )

        assertEquals(
            listOf("lowerBound", "upperBound", "barelyStarted", "almostDone"),
            repository.fetchRecent(ContentType.MOVIES).map { it.id },
        )
    }

    @Test
    fun excludesOtherContentTypes() {
        val repository =
            repositoryWith(
                movie("film", position = 50L, duration = 100L),
                episode("s1e1", seriesId = "s1"),
                channel("bbc"),
            )

        assertEquals(listOf("film"), repository.fetchRecent(ContentType.MOVIES).map { it.id })
        assertEquals(listOf("s1"), repository.fetchRecent(ContentType.TV_SHOWS).map { it.id })
        assertEquals(listOf("bbc"), repository.fetchRecent(ContentType.LIVE_TV).map { it.id })
    }
}
