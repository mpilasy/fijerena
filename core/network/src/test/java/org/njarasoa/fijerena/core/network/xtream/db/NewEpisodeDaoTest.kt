package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.xtream.NewEpisodeCompare
import org.njarasoa.fijerena.core.network.xtream.followedShowIds
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The followed-shows queries and the pruning on a real in-memory `xtream_v2.db`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewEpisodeDaoTest {
    private lateinit var db: XtreamDatabase
    private lateinit var dao: NewEpisodeDao
    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, XtreamDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.newEpisodeDao()
    }

    @After
    fun tearDown() = db.close()

    private fun episode(
        id: String,
        seriesId: Int,
        number: Int,
    ) = XtreamEpisodeEntity(
        id = id,
        seriesId = seriesId,
        providerId = P,
        season = 1,
        episodeNum = number,
        title = id,
        containerExtension = "mp4",
    )

    private fun watch(
        itemId: String,
        seriesId: Int,
        lastPlayedAt: Long?,
        completed: Boolean = false,
        profile: String = "default",
    ) = WatchStateEntity(
        providerId = P,
        profileId = profile,
        itemId = itemId,
        contentType = ContentType.TV_SHOWS,
        itemName = itemId,
        categoryId = "c",
        positionMs = 0,
        durationMs = 0,
        isCompleted = completed,
        updatedAt = now,
        lastPlayedAt = lastPlayedAt,
        seriesId = seriesId.toString(),
        episodeId = itemId,
    )

    private fun series(id: Int) = XtreamSeriesEntity(seriesId = id, providerId = P, name = "Show $id", categoryId = "c")

    @Test
    fun `favourites and shows played in the last 60 days are followed, finished or not`() =
        runBlocking {
            db.favoriteStateDao().upsert(
                FavoriteStateEntity(P, "other", "10", ContentType.TV_SHOWS, FavoriteKind.STREAM, "Fav", createdAt = 1),
            )
            db.episodeDao().insertAll(
                listOf(episode("a", 20, 1), episode("b", 20, 2), episode("c", 21, 1), episode("d", 22, 1)),
            )
            db.watchStateDao().restoreAll(
                listOf(
                    // Watched recently, one episode left.
                    watch("a", 20, now - day, completed = true),
                    // Watched recently, every stored episode finished.
                    watch("c", 21, now - day, completed = true),
                    // Played more than 60 days ago.
                    watch("d", 22, now - 90 * day),
                    // Played recently, no stored list yet.
                    watch("e", 23, now - day),
                    // Only marked watched: no play, so it does not follow the show.
                    watch("f", 24, null, completed = true),
                ),
            )

            assertEquals(setOf(10, 20, 21, 23), followedShowIds(dao, P, now))
        }

    @Test
    fun `the record step uses the stored list and inserts once`() {
        val stored = listOf(episode("a", 7, 1))
        val incoming = listOf(episode("a", 7, 1), episode("b", 7, 2))

        NewEpisodeCompare.record(dao, P, 7, stored, incoming, now)
        NewEpisodeCompare.record(dao, P, 7, stored, incoming, now + 5)

        val rows = dao.getAll(P)
        assertEquals(listOf("b"), rows.map { it.episodeId })
        assertEquals(now, rows.single().foundAt)
    }

    @Test
    fun `prune removes old rows and rows of shows the source no longer lists`() {
        db.seriesDao().insertAll(listOf(series(1)))
        dao.insertAll(
            listOf(
                NewEpisodeEntity(P, 1, "new", 1, 1, now),
                NewEpisodeEntity(P, 1, "old", 1, 2, now - 40 * day),
                NewEpisodeEntity(P, 2, "gone", 1, 1, now),
            ),
        )

        dao.prune(P, now - 30 * day)

        assertEquals(listOf("new"), dao.getAll(P).map { it.episodeId })
    }

    @Test
    fun `the check states say what the check needs`() {
        db.seriesDao().insertAll(
            listOf(
                series(1).copy(lastModified = "500", episodesFetchedAt = null),
                series(2).copy(episodesFetchedAt = 9L),
            ),
        )
        db.episodeDao().insertAll(listOf(episode("a", 1, 1)))

        val states = dao.getCheckStates(P, listOf(1, 2, 3)).associateBy { it.seriesId }

        assertEquals(2, states.size)
        assertEquals("500", states.getValue(1).lastModified)
        assertTrue(states.getValue(1).hasEpisodes)
        assertEquals(9L, states.getValue(2).episodesFetchedAt)
        assertTrue(!states.getValue(2).hasEpisodes)
    }

    private companion object {
        const val P = 1L
    }
}
