package org.njarasoa.fijerena.core.network

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesEntity
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.domain.SeriesId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Marking a whole season or show watched / unwatched, per episode
 * (docs/plans/archive/20261011_season-show-watched-plan.md → Phase 1). Against a real in-memory
 * `xtream_v2.db` under Robolectric, so the watch-state upserts and the TMDB sibling queries run
 * as SQL rather than through fakes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaRepositorySetEpisodesWatchedTest {
    private lateinit var context: Context
    private lateinit var db: XtreamDatabase
    private lateinit var provider: MediaProvider
    private lateinit var repository: MediaRepository

    /** What the provider lists for show 7 right now; a test can add an episode later. */
    private var show7 = detail("7", "e1" to 1, "e2" to 1, "e3" to 2)

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, XtreamDatabase::class.java).allowMainThreadQueries().build()
        provider = mockk(relaxed = true)
        every { provider.capabilities } returns capabilities(serverUserData = false)
        coEvery { provider.getSeriesDetail(SeriesId("7")) } answers { kotlin.Result.success(show7) }
        coEvery { provider.getCachedSeriesDetail(SeriesId("7")) } answers { show7 }
        repository =
            MediaRepository(
                context,
                P,
                PROFILE,
                watchStateDao = db.watchStateDao(),
                streamDao = db.streamDao(),
                episodeDao = db.episodeDao(),
                favoriteStateDao = db.favoriteStateDao(),
                xtreamDb = lazy { db },
            )
        repository.setProvider(provider)
    }

    @After
    fun tearDown() {
        repository.close()
        db.close()
    }

    @Test
    fun `marking a season watched touches only that season`() =
        runBlocking {
            assertTrue(repository.setEpisodesWatched("7", season = 1, watched = true).isSuccess)

            assertTrue(row("e1")!!.isCompleted)
            assertTrue(row("e2")!!.isCompleted)
            assertNull("season 2 has no row", row("e3"))
            assertEquals(true, repository.episodesWatchState("7", season = 1))
            assertEquals(false, repository.episodesWatchState("7", season = 2))
            assertEquals(false, repository.episodesWatchState("7", season = null))
        }

    @Test
    fun `marking a show watched writes one row per episode, kept out of Recent`() =
        runBlocking {
            repository.setEpisodesWatched("7", season = null, watched = true)

            for (id in listOf("e1", "e2", "e3")) {
                val row = row(id)!!
                assertTrue(row.isCompleted)
                assertNull("a manual mark is not a play", row.lastPlayedAt)
                assertEquals("7", row.seriesId)
                assertEquals(id, row.episodeId)
            }
            assertEquals(true, repository.episodesWatchState("7", season = null))
        }

    @Test
    fun `an episode added after a show mark stays unwatched`() =
        runBlocking {
            repository.setEpisodesWatched("7", season = null, watched = true)
            show7 = detail("7", "e1" to 1, "e2" to 1, "e3" to 2, "e4" to 2)

            assertNull(row("e4"))
            assertEquals(false, repository.episodesWatchState("7", season = 2))
            assertEquals(true, repository.episodesWatchState("7", season = 1))
        }

    @Test
    fun `marking a show unwatched keeps resume positions`() =
        runBlocking {
            db.watchStateDao().upsertProgress(
                providerId = P,
                profileId = PROFILE,
                itemId = "e2",
                contentType = ContentType.TV_SHOWS,
                itemName = "Episode 2",
                categoryId = "c",
                positionMs = 500_000L,
                durationMs = 2_500_000L,
                isCompleted = false,
                now = 1_000L,
                seriesId = "7",
                episodeId = "e2",
                seriesName = "Show 7",
                episodeExtension = "mkv",
                audioTrackIndex = null,
                subtitleTrackIndex = null,
            )
            repository.setEpisodesWatched("7", season = null, watched = true)

            repository.setEpisodesWatched("7", season = null, watched = false)

            val e2 = row("e2")!!
            assertFalse(e2.isCompleted)
            assertEquals(500_000L, e2.positionMs)
            assertEquals("Recent keeps its play", 1_000L, e2.lastPlayedAt)
            assertFalse(row("e1")!!.isCompleted)
            assertEquals(false, repository.episodesWatchState("7", season = null))
        }

    @Test
    fun `marking a show unwatched also clears the show's own row from the old show toggle`() =
        runBlocking {
            db.watchStateDao().markWatched(P, PROFILE, "7", ContentType.TV_SHOWS, now = 1_000L)
            repository.setEpisodesWatched("7", season = null, watched = true)

            repository.setEpisodesWatched("7", season = null, watched = false)

            assertFalse(row("7")!!.isCompleted)
        }

    @Test
    fun `the read counts an episode completed in a TMDB sibling as watched`() =
        runBlocking {
            // Show 8 is another language of show 7 (same tmdbId); its S1E1 and S1E2 are done.
            db.seriesDao().insertAll(listOf(series(7, "100"), series(8, "100")))
            db.episodeDao().insertAll(
                listOf(
                    episode("e1", 7, 1, 1),
                    episode("e2", 7, 1, 2),
                    episode("e3", 7, 2, 1),
                    episode("x1", 8, 1, 1),
                    episode("x2", 8, 1, 2),
                ),
            )
            db.watchStateDao().markWatched(P, PROFILE, "x1", ContentType.TV_SHOWS, 1_000L, "8", "x1")
            db.watchStateDao().markWatched(P, PROFILE, "x2", ContentType.TV_SHOWS, 1_000L, "8", "x2")

            assertEquals(true, repository.episodesWatchState("7", season = 1))
            assertEquals(false, repository.episodesWatchState("7", season = null))

            // Unwatching the season clears the sibling's completion too, or the check would come
            // straight back.
            repository.setEpisodesWatched("7", season = 1, watched = false)

            assertFalse(row("x1")!!.isCompleted)
            assertEquals(false, repository.episodesWatchState("7", season = 1))
        }

    @Test
    fun `the read is null when no episodes are known`() =
        runBlocking {
            coEvery { provider.getCachedSeriesDetail(SeriesId("9")) } returns null

            assertNull(repository.episodesWatchState("9", season = null))
            assertNull("a season the show doesn't have", repository.episodesWatchState("7", season = 5))
        }

    @Test
    fun `a failed episode load marks nothing`() =
        runBlocking {
            coEvery { provider.getSeriesDetail(SeriesId("9")) } returns kotlin.Result.failure(Exception("offline"))

            assertTrue(repository.setEpisodesWatched("9", season = null, watched = true).isFailure)
            assertEquals(emptyList<WatchStateEntity>(), db.watchStateDao().getByContentType(P, PROFILE, ContentType.TV_SHOWS))
        }

    @Test
    fun `a server-backed source is left alone`() =
        runBlocking {
            every { provider.capabilities } returns capabilities(serverUserData = true)

            assertTrue(repository.setEpisodesWatched("7", season = null, watched = true).isSuccess)

            assertNull(row("e1"))
            assertNull(repository.episodesWatchState("7", season = null))
            coVerify(exactly = 0) { provider.getSeriesDetail(any()) }
        }

    private suspend fun row(id: String): WatchStateEntity? = db.watchStateDao().getItem(P, PROFILE, id, ContentType.TV_SHOWS)

    private companion object {
        const val P = 1L
        const val PROFILE = ProfileEntity.DEFAULT_ID

        fun capabilities(serverUserData: Boolean) =
            ProviderCapabilities(
                supportedContentTypes = setOf(ContentType.TV_SHOWS),
                supportsEpg = false,
                supportsSearch = false,
                supportsAuthentication = false,
                supportsProgressSync = false,
                supportsServerUserData = serverUserData,
            )

        /** Episodes as (id to season), numbered in order within each season. */
        fun detail(
            id: String,
            vararg episodes: Pair<String, Int>,
        ) = SeriesDetail(
            id = id,
            name = "Show $id",
            episodes =
                episodes
                    .groupBy({ it.second }, { it.first })
                    .map { (season, ids) ->
                        season.toString() to ids.mapIndexed { i, epId -> EpisodeItem(epId, i + 1, "Episode $epId", seasonNumber = season) }
                    }.toMap(),
        )

        fun series(
            id: Int,
            tmdbId: String,
        ) = XtreamSeriesEntity(seriesId = id, providerId = P, name = "Show $id", categoryId = "c", tmdbId = tmdbId)

        fun episode(
            id: String,
            seriesId: Int,
            season: Int,
            num: Int,
        ) = XtreamEpisodeEntity(
            id = id,
            seriesId = seriesId,
            providerId = P,
            season = season,
            episodeNum = num,
            title = id,
            containerExtension = "mkv",
        )
    }
}
