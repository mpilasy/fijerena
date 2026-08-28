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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeFavoriteStateDao
import org.njarasoa.fijerena.core.network.fixtures.FakeWatchStateDao
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamDao
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities

/**
 * Manual watched/unwatched marks (Phase 6, docs/plans/watch-state-durable-storage-plan.md). Movies/
 * TV Shows also clear a completed TMDB sibling group on unwatched, which needs a real Xtream
 * catalogue join this module's plain-JVM tests can't exercise — covered here with LIVE_TV, which
 * skips that branch entirely and lets these tests check the single-row semantics in isolation.
 */
class MediaRepositorySetWatchedTest {
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
        // Unless a test says otherwise, the episode isn't in the local catalogue - setWatched
        // must degrade to no seriesId rather than crash.
        coEvery { episodeDao.getSeriesIdForEpisode(any(), any()) } returns null
        repository = MediaRepository(context, 1L, watchStateDao = watchStateDao, favoriteStateDao = FakeFavoriteStateDao(), streamDao = streamDao, episodeDao = episodeDao)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `marking watched with no existing row creates one that stays out of Recent`() =
        runBlocking {
            repository.setWatched("ch1", ContentType.LIVE_TV, watched = true)

            val row = watchStateDao.getItem(1L, "ch1", ContentType.LIVE_TV)!!
            assertTrue("a manual mark must complete the row", row.isCompleted)
            assertEquals(0L, row.positionMs)
            assertEquals(0L, row.durationMs)
            assertNull("lastPlayedAt null is what keeps a manual mark out of the Recent row", row.lastPlayedAt)
        }

    @Test
    fun `marking watched on an existing row leaves position and lastPlayedAt alone`() =
        runBlocking {
            watchStateDao.upsertProgress(
                providerId = 1L,
                itemId = "ep1",
                contentType = ContentType.TV_SHOWS,
                itemName = "Episode 1",
                categoryId = "cat1",
                positionMs = 500_000L,
                durationMs = 2_500_000L,
                isCompleted = false,
                now = 1_000L,
                seriesId = "s1",
                episodeId = "ep1",
                seriesName = "Show",
                episodeExtension = "mkv",
                audioTrackIndex = null,
                subtitleTrackIndex = null,
            )

            repository.setWatched("ep1", ContentType.TV_SHOWS, watched = true)

            val row = watchStateDao.getItem(1L, "ep1", ContentType.TV_SHOWS)!!
            assertTrue(row.isCompleted)
            assertEquals("a manual mark must not discard a stored resume point", 500_000L, row.positionMs)
            assertEquals(1_000L, row.lastPlayedAt)
        }

    @Test
    fun `unmarking a row clears completion but keeps its position`() =
        runBlocking {
            watchStateDao.seed(
                WatchStateEntity(
                    providerId = 1L,
                    itemId = "ch1",
                    contentType = ContentType.LIVE_TV,
                    itemName = "Channel",
                    categoryId = "cat1",
                    positionMs = 0L,
                    durationMs = 0L,
                    isCompleted = true,
                    updatedAt = 1_000L,
                    lastPlayedAt = 1_000L,
                ),
            )

            repository.setWatched("ch1", ContentType.LIVE_TV, watched = false)

            val row = watchStateDao.getItem(1L, "ch1", ContentType.LIVE_TV)!!
            assertFalse("must not be completed after unmarking", row.isCompleted)
        }

    @Test
    fun `unmarking a row that was never watched is a harmless no-op`() =
        runBlocking {
            repository.setWatched("never-seen", ContentType.LIVE_TV, watched = false)

            assertEquals(null, watchStateDao.getItem(1L, "never-seen", ContentType.LIVE_TV))
        }

    @Test
    fun `marking a never-played episode watched resolves its seriesId from the catalogue`() =
        runBlocking {
            // "ep9" has no watch_state row at all yet - the exact case markWatched's INSERT path
            // has no seriesId parameter for, since MediaRepository.setWatched's own signature
            // carries none either. Without the catalogue lookup this row would insert with
            // seriesId = null and silently drop out of getSeriesCompletedCounts's rollup.
            coEvery { episodeDao.getSeriesIdForEpisode(1L, "ep9") } returns 42

            repository.setWatched("ep9", ContentType.TV_SHOWS, watched = true)

            val row = watchStateDao.getItem(1L, "ep9", ContentType.TV_SHOWS)!!
            assertTrue(row.isCompleted)
            assertEquals("42", row.seriesId)
            assertEquals("a manual mark on an unplayed episode must still be findable by its own id", "ep9", row.episodeId)
        }

    @Test
    fun `setWatched no-ops for a server-backed provider`() =
        runBlocking {
            // Jellyfin owns this state server-side and has no MediaProvider capability to accept
            // a manual mark through this app — writing local watch_state for it would be dead
            // data, since getPlaybackPositionSuspend never reads local state back for it.
            val provider = mockk<MediaProvider>(relaxed = true)
            every { provider.capabilities } returns
                ProviderCapabilities(
                    supportedContentTypes = setOf(ContentType.MOVIES),
                    supportsEpg = false,
                    supportsSearch = false,
                    supportsAuthentication = false,
                    supportsProgressSync = false,
                    supportsServerUserData = true,
                )
            repository.setProvider(provider)

            repository.setWatched("movie1", ContentType.MOVIES, watched = true)

            assertEquals(null, watchStateDao.getItem(1L, "movie1", ContentType.MOVIES))
        }

    @Test
    fun `progress upsert cannot un-complete a row a manual mark or earlier progress already completed`() =
        runBlocking {
            repository.setWatched("ep1", ContentType.TV_SHOWS, watched = true)

            // A brief re-watch reporting far below the completion threshold must not clear the check.
            repository.savePlaybackPosition("ep1", "Episode 1", "cat1", ContentType.TV_SHOWS, 10_000L, 2_500_000L)
            repository.awaitPendingWrites()

            val row = watchStateDao.getItem(1L, "ep1", ContentType.TV_SHOWS)!!
            assertTrue("isCompleted is sticky since Phase 6 — only setWatched(false) may clear it", row.isCompleted)
        }
}
