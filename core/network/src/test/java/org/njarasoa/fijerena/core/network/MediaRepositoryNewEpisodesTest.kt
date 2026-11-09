package org.njarasoa.fijerena.core.network

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.NewEpisodeEntity
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesEntity
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The facts a source gives the New Episodes read side (the new episode alerts plan), against a real
 * in-memory `xtream_v2.db`: who follows what from when, what the profile has seen, and the filters.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaRepositoryNewEpisodesTest {
    private lateinit var context: Context
    private lateinit var db: XtreamDatabase
    private lateinit var repository: MediaRepository
    private lateinit var settings: AppSettings

    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, XtreamDatabase::class.java).allowMainThreadQueries().build()
        settings = AppSettings(context)
        repository =
            MediaRepository(
                context,
                P,
                PROFILE,
                watchStateDao = db.watchStateDao(),
                streamDao = db.streamDao(),
                episodeDao = db.episodeDao(),
                favoriteStateDao = db.favoriteStateDao(),
                categoryDao = db.categoryDao(),
                xtreamDb = lazy { db },
            )
        val provider = mockk<XtreamMediaProvider>(relaxed = true)
        every { provider.capabilities } returns
            ProviderCapabilities(
                supportedContentTypes = setOf(ContentType.TV_SHOWS),
                supportsEpg = false,
                supportsSearch = false,
                supportsAuthentication = false,
                supportsProgressSync = false,
                supportsServerUserData = false,
            )
        repository.setProvider(provider)
        db.categoryDao().insertAll(
            listOf(
                XtreamCategoryEntity("toons", P, "Toons", type = XtreamCategoryEntity.TYPE_SERIES),
                XtreamCategoryEntity("horror", P, "Horror", type = XtreamCategoryEntity.TYPE_SERIES, excluded = true),
            ),
        )
        db.seriesDao().insertAll(
            listOf(
                XtreamSeriesEntity(7, P, name = "Toons", categoryId = "toons"),
                XtreamSeriesEntity(8, P, name = "Scary", categoryId = "horror"),
            ),
        )
    }

    @After
    fun tearDown() {
        repository.close()
        db.close()
    }

    private fun row(
        series: Int,
        id: String,
        foundAt: Long = now - day,
        season: Int? = 1,
        number: Int = 1,
    ) = NewEpisodeEntity(P, series, id, season, number, foundAt)

    private fun addFavorite(
        series: Int,
        createdAt: Long,
    ) = db.favoriteStateDao().upsert(
        FavoriteStateEntity(P, PROFILE, series.toString(), ContentType.TV_SHOWS, FavoriteKind.STREAM, "Show", createdAt = createdAt),
    )

    private suspend fun addWatch(
        series: Int,
        id: String,
        lastPlayedAt: Long?,
        positionMs: Long = 1_000L,
        completed: Boolean = false,
    ) = db.watchStateDao().restoreAll(
        listOf(
            WatchStateEntity(
                P,
                PROFILE,
                id,
                ContentType.TV_SHOWS,
                id,
                "c",
                positionMs,
                2_000L,
                completed,
                now,
                lastPlayedAt,
                seriesId = series.toString(),
                episodeId = id,
            ),
        ),
    )

    private fun episode(
        id: String,
        season: Int,
        number: Int,
    ) = XtreamEpisodeEntity(id, 7, P, season, number, id, "mp4")

    @Test
    fun `no rows means no facts`() =
        runBlocking {
            assertNull(repository.getNewEpisodeFacts(now))
        }

    @Test
    fun `a favourite follows from when it was added`() =
        runBlocking {
            addFavorite(7, now - 2 * day)
            db.newEpisodeDao().insertAll(listOf(row(7, "e1")))

            val facts = repository.getNewEpisodeFacts(now)!!

            assertEquals(now - 2 * day, facts.shows.single().followedSince)
        }

    @Test
    fun `a show played in the last 60 days follows from its first play`() =
        runBlocking {
            addWatch(7, "a", now - 20 * day)
            addWatch(7, "b", now - 2 * day)
            db.newEpisodeDao().insertAll(listOf(row(7, "e1")))

            assertEquals(
                now - 20 * day,
                repository
                    .getNewEpisodeFacts(now)!!
                    .shows
                    .single()
                    .followedSince,
            )
        }

    @Test
    fun `a show last played more than 60 days ago is not followed`() =
        runBlocking {
            addWatch(7, "a", now - 90 * day)
            db.newEpisodeDao().insertAll(listOf(row(7, "e1")))

            assertNull(
                repository
                    .getNewEpisodeFacts(now)!!
                    .shows
                    .single()
                    .followedSince,
            )
        }

    @Test
    fun `a started or finished episode is seen, a marked-then-unmarked one is not`() =
        runBlocking {
            addFavorite(7, now - 2 * day)
            addWatch(7, "started", now - day)
            addWatch(7, "finished", null, completed = true)
            addWatch(7, "unmarked", lastPlayedAt = null, positionMs = 0L, completed = false)
            db.newEpisodeDao().insertAll(listOf(row(7, "started"), row(7, "finished"), row(7, "unmarked"), row(7, "fresh")))

            val seen = repository.getNewEpisodeFacts(now)!!.seenEpisodeIds

            assertTrue("started" in seen)
            assertTrue("finished" in seen)
            assertTrue("unmarked" !in seen)
            assertTrue("fresh" !in seen)
        }

    @Test
    fun `a season whose stored episodes are all new is a new season`() =
        runBlocking {
            addFavorite(7, now - 2 * day)
            db.episodeDao().insertAll(listOf(episode("old", 1, 1), episode("s1new", 1, 2), episode("s2a", 2, 1)))
            db.newEpisodeDao().insertAll(listOf(row(7, "s1new", season = 1, number = 2), row(7, "s2a", season = 2)))

            assertEquals(
                setOf(2),
                repository
                    .getNewEpisodeFacts(now)!!
                    .shows
                    .single()
                    .newSeasons,
            )
        }

    @Test
    fun `a show in a category the content filters hide is left out`() =
        runBlocking {
            addFavorite(8, now - 2 * day)
            db.newEpisodeDao().insertAll(listOf(row(8, "e")))

            assertTrue(repository.getNewEpisodeFacts(now)!!.shows.isEmpty())
        }

    @Test
    fun `a child's profile keeps only what it may see`() =
        runBlocking {
            settings.setChildProfile(PROFILE, true)
            addFavorite(7, now - 2 * day)
            addFavorite(8, now - 2 * day)
            db.newEpisodeDao().insertAll(listOf(row(7, "visible"), row(8, "hidden")))

            assertEquals(listOf("7"), repository.getNewEpisodeFacts(now)!!.shows.map { it.item.id })
            settings.setChildProfile(PROFILE, false)
        }

    @Test
    fun `a find older than 30 days does not count`() =
        runBlocking {
            addFavorite(7, now - 2 * day)
            db.newEpisodeDao().insertAll(listOf(row(7, "old", foundAt = now - 31 * day)))

            assertNull(repository.getNewEpisodeFacts(now))
        }

    @Test
    fun `marking new episodes watched and its undo touch only those episodes`() =
        runBlocking {
            repository.setEpisodesWatched("7", listOf("e1", "e2"), watched = true)

            assertTrue(db.watchStateDao().getItem(P, PROFILE, "e1", ContentType.TV_SHOWS)!!.isCompleted)
            assertNull(db.watchStateDao().getItem(P, PROFILE, "e9", ContentType.TV_SHOWS))

            repository.setEpisodesWatched("7", listOf("e1", "e2"), watched = false)

            assertTrue(!db.watchStateDao().getItem(P, PROFILE, "e1", ContentType.TV_SHOWS)!!.isCompleted)
        }

    private companion object {
        const val P = 1L
        const val PROFILE = ProfileEntity.DEFAULT_ID
    }
}
