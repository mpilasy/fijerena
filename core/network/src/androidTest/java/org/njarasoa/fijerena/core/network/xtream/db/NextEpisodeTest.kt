package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** [XtreamEpisodeDao.getNextEpisode]: what Continue Watching offers after a finished episode. */
@RunWith(AndroidJUnit4::class)
class NextEpisodeTest {
    private lateinit var db: XtreamDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, XtreamDatabase::class.java).build()
        db.episodeDao().insertAll(
            listOf(
                ep("s1e2", 1, 2),
                ep("s1e1", 1, 1),
                ep("s2e1", 2, 1),
                ep("s1e10", 1, 10),
                // Another show, and another provider's copy of this one: never offered.
                ep("other", 1, 3, seriesId = 99),
                ep("elsewhere", 1, 3, providerId = 2L),
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun nextInTheSameSeasonIsByEpisodeNumber() =
        runBlocking {
            assertEquals("s1e2", db.episodeDao().getNextEpisode(P, SHOW, 1, 1)?.id)
            assertEquals("s1e10", db.episodeDao().getNextEpisode(P, SHOW, 1, 2)?.id)
        }

    @Test
    fun afterASeasonComesTheNextSeason() =
        runBlocking { assertEquals("s2e1", db.episodeDao().getNextEpisode(P, SHOW, 1, 10)?.id) }

    @Test
    fun nothingAfterTheLastEpisode() =
        runBlocking { assertNull(db.episodeDao().getNextEpisode(P, SHOW, 2, 1)) }

    @Test
    fun episodesWithoutASeasonCountAsSeasonZero() =
        runBlocking {
            db.episodeDao().insertAll(listOf(ep("special", null, 1)))
            assertEquals("s1e1", db.episodeDao().getNextEpisode(P, SHOW, 0, 1)?.id)
            assertEquals("special", db.episodeDao().getEpisode(P, "special")?.id)
        }

    private fun ep(
        id: String,
        season: Int?,
        number: Int,
        seriesId: Int = SHOW,
        providerId: Long = P,
    ) = XtreamEpisodeEntity(id = id, seriesId = seriesId, providerId = providerId, season = season, episodeNum = number, title = id, containerExtension = "mkv")

    private companion object {
        const val P = 1L
        const val SHOW = 7
    }
}
