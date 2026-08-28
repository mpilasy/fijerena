package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeWatchStateDao
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamDao
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaProvider

/**
 * TMDB dedup on the series-row watch bar (`getSeriesWatchProgress`). The gap these cover: the
 * per-episode watched check already spreads completion across sibling series sharing a `tmdbId`,
 * so an episode list showed episodes checked while the series row above it read 0%.
 *
 * The dedup join itself is SQL against a real Xtream catalogue, which this module's plain-JVM
 * tests can't run — same limitation `MediaRepositorySetWatchedTest` documents. What is covered
 * here is the union the repository performs over that query's result: which source wins, and that
 * neither can be lost. The SQL was verified separately against a 47,552-series catalogue pulled
 * from a device.
 */
class MediaRepositorySeriesProgressTest {
    private lateinit var context: Context
    private lateinit var watchStateDao: FakeWatchStateDao
    private lateinit var episodeDao: XtreamEpisodeDao
    private lateinit var streamDao: XtreamStreamDao
    private lateinit var repository: MediaRepository

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns mockk<SharedPreferences>(relaxed = true)
        watchStateDao = FakeWatchStateDao()
        episodeDao = mockk(relaxed = true)
        streamDao = mockk(relaxed = true)
        coEvery { episodeDao.getSiblingCompletedCountsBySeries(any()) } returns emptyMap()
        repository = MediaRepository(context, 1L, watchStateDao = watchStateDao, streamDao = streamDao, episodeDao = episodeDao)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** Series episode counts as the provider reports them, keyed by series id. */
    private fun withEpisodeCounts(counts: Map<String, Int>) {
        val provider = mockk<MediaProvider>(relaxed = true)
        coEvery { provider.getEpisodeCountsBySeries() } returns counts
        repository.setProvider(provider)
    }

    private fun completedEpisode(
        seriesId: String,
        episodeId: String,
    ) = WatchStateEntity(
        providerId = 1L,
        itemId = episodeId,
        contentType = ContentType.TV_SHOWS,
        itemName = episodeId,
        categoryId = "",
        positionMs = 0L,
        durationMs = 0L,
        isCompleted = true,
        updatedAt = 0L,
        lastPlayedAt = null,
        seriesId = seriesId,
        episodeId = episodeId,
    )

    @Test
    fun `sibling completion reaches a series with no watch_state rows of its own`() =
        runBlocking {
            // Watched under series 6548; 9279 is a language variant sharing its tmdbId and has no
            // rows of its own. Before dedup it reported nothing at all.
            watchStateDao.seed(completedEpisode(seriesId = "6548", episodeId = "343994"))
            coEvery { episodeDao.getSiblingCompletedCountsBySeries(1L) } returns mapOf(6548 to 1, 9279 to 1)
            withEpisodeCounts(mapOf("6548" to 40, "9279" to 40))

            val progress = repository.getSeriesWatchProgress()

            assertEquals(1f / 40f, progress["6548"]!!, 0.0001f)
            assertEquals(1f / 40f, progress["9279"]!!, 0.0001f)
        }

    @Test
    fun `series the dedup query cannot see keeps its direct count`() =
        runBlocking {
            // A series with no tmdbId is absent from the dedup result. Its own completions must
            // still count, or adding dedup would regress every untagged show to 0%.
            watchStateDao.seed(completedEpisode(seriesId = "777", episodeId = "e1"))
            watchStateDao.seed(completedEpisode(seriesId = "777", episodeId = "e2"))
            coEvery { episodeDao.getSiblingCompletedCountsBySeries(1L) } returns emptyMap()
            withEpisodeCounts(mapOf("777" to 8))

            val progress = repository.getSeriesWatchProgress()

            assertEquals(2f / 8f, progress["777"]!!, 0.0001f)
        }

    @Test
    fun `overlapping counts take the larger, never the sum`() =
        runBlocking {
            // Both sources describe the same completed episode. Adding them would report 2/10 for
            // one watched episode.
            watchStateDao.seed(completedEpisode(seriesId = "6548", episodeId = "343994"))
            coEvery { episodeDao.getSiblingCompletedCountsBySeries(1L) } returns mapOf(6548 to 1)
            withEpisodeCounts(mapOf("6548" to 10))

            val progress = repository.getSeriesWatchProgress()

            assertEquals(1f / 10f, progress["6548"]!!, 0.0001f)
        }

    @Test
    fun `direct count wins when it exceeds the deduped one`() =
        runBlocking {
            // The dedup query only reaches episodes still in the catalogue cache. If the cache lost
            // rows the direct count is the higher of the two and must survive.
            watchStateDao.seed(completedEpisode(seriesId = "6548", episodeId = "e1"))
            watchStateDao.seed(completedEpisode(seriesId = "6548", episodeId = "e2"))
            watchStateDao.seed(completedEpisode(seriesId = "6548", episodeId = "e3"))
            coEvery { episodeDao.getSiblingCompletedCountsBySeries(1L) } returns mapOf(6548 to 1)
            withEpisodeCounts(mapOf("6548" to 10))

            val progress = repository.getSeriesWatchProgress()

            assertEquals(3f / 10f, progress["6548"]!!, 0.0001f)
        }

    @Test
    fun `a series the provider cannot count is absent rather than wrong`() =
        runBlocking {
            watchStateDao.seed(completedEpisode(seriesId = "6548", episodeId = "343994"))
            coEvery { episodeDao.getSiblingCompletedCountsBySeries(1L) } returns mapOf(6548 to 1, 9279 to 1)
            // 9279's episodes were never cached, so it has no denominator.
            withEpisodeCounts(mapOf("6548" to 40))

            val progress = repository.getSeriesWatchProgress()

            assertEquals(1f / 40f, progress["6548"]!!, 0.0001f)
            assertNull(progress["9279"])
        }

    @Test
    fun `progress is clamped to fully watched`() =
        runBlocking {
            // A stale denominator smaller than the completed count must not render past 100%.
            coEvery { episodeDao.getSiblingCompletedCountsBySeries(1L) } returns mapOf(6548 to 12)
            withEpisodeCounts(mapOf("6548" to 10))

            val progress = repository.getSeriesWatchProgress()

            assertEquals(1f, progress["6548"]!!, 0.0001f)
        }

    @Test
    fun `no provider episode counts yields no progress at all`() =
        runBlocking {
            watchStateDao.seed(completedEpisode(seriesId = "6548", episodeId = "343994"))
            coEvery { episodeDao.getSiblingCompletedCountsBySeries(1L) } returns mapOf(6548 to 1)
            // Non-Xtream providers don't implement getEpisodeCountsBySeries.
            val provider = mockk<MediaProvider>(relaxed = true)
            coEvery { provider.getEpisodeCountsBySeries() } returns null
            repository.setProvider(provider)

            assertEquals(emptyMap<String, Float>(), repository.getSeriesWatchProgress())
        }
}
