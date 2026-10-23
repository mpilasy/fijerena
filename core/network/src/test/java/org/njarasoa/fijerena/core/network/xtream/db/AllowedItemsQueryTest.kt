package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A film or series with `allowed` = 1 (a Parental controls exception) shows also in an excluded
 * category, in every query that hides the items of excluded categories, and the search count
 * leaves it out. One test per query, against a real in-memory `xtream_v2.db` under Robolectric.
 * See docs/plans/20261020_parental-controls-plan.md, Phase 4.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AllowedItemsQueryTest {
    private lateinit var db: XtreamDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, XtreamDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        db.categoryDao().insertAll(
            listOf(
                XtreamCategoryEntity("shown", P, "Kids", type = XtreamCategoryEntity.TYPE_VOD),
                XtreamCategoryEntity("hidden", P, "Anime", type = XtreamCategoryEntity.TYPE_VOD, excluded = true),
                XtreamCategoryEntity("empty", P, "Drama", type = XtreamCategoryEntity.TYPE_VOD, excluded = true),
                XtreamCategoryEntity("s-shown", P, "Kids Series", type = XtreamCategoryEntity.TYPE_SERIES),
                XtreamCategoryEntity("s-hidden", P, "Anime Series", type = XtreamCategoryEntity.TYPE_SERIES, excluded = true),
            ),
        )
        db.streamDao().insertAll(
            listOf(
                movie(1, "shown", "King Kong"),
                movie(2, "hidden", "King Naruto", allowed = true),
                movie(3, "hidden", "King Bleach"),
                movie(4, "empty", "King Lear"),
            ),
        )
        db.seriesDao().insertAll(
            listOf(
                series(10, "s-shown", "Kings"),
                series(11, "s-hidden", "King Naruto Show", allowed = true),
                series(12, "s-hidden", "King Bleach Show"),
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getStreamsByCategory() {
        val streams = db.streamDao()
        assertEquals(listOf(2), streams.getStreamsByCategory(P, VOD, "hidden").map { it.streamId })
        assertEquals(listOf(1), streams.getStreamsByCategory(P, VOD, "shown").map { it.streamId })
        assertEquals(listOf<Int>(), streams.getStreamsByCategory(P, VOD, "empty").map { it.streamId })
    }

    @Test
    fun getAllStreams() {
        assertEquals(listOf(1, 2), db.streamDao().getAllStreams(P, VOD).map { it.streamId })
    }

    @Test
    fun getByTmdbIdOfStreams() {
        assertEquals(listOf(2), db.streamDao().getByTmdbId(P, VOD, "t1", excludeStreamId = 1).map { it.streamId })
    }

    @Test
    fun getSiblingCompletedStreamIds() {
        val ids =
            runBlocking {
                db.watchStateDao().markWatched(P, PROFILE, "1", ContentType.MOVIES, now = 1L)
                db.streamDao().getSiblingCompletedStreamIds(P, PROFILE, ContentType.MOVIES, VOD)
            }
        assertEquals(setOf("1", "2"), ids.toSet())
    }

    @Test
    fun searchStreamsByFts() {
        val streams = db.streamDao()
        assertEquals(setOf(1, 2), streams.searchByFts(P, VOD, "king*", includeExcluded = false).map { it.streamId }.toSet())
        assertEquals(setOf(1, 2, 3, 4), streams.searchByFts(P, VOD, "king*", includeExcluded = true).map { it.streamId }.toSet())
    }

    @Test
    fun countExcludedStreamsByFts() {
        assertEquals(2, db.streamDao().countExcludedByFts(P, VOD, "king*"))
    }

    @Test
    fun getSeriesByCategory() {
        assertEquals(listOf(11), db.seriesDao().getSeriesByCategory(P, "s-hidden").map { it.seriesId })
    }

    @Test
    fun getAllSeries() {
        assertEquals(
            setOf(10, 11),
            db
                .seriesDao()
                .getAllSeries(P)
                .map { it.seriesId }
                .toSet(),
        )
    }

    @Test
    fun getByTmdbIdOfSeries() {
        assertEquals(listOf(11), db.seriesDao().getByTmdbId(P, "s1", excludeSeriesId = 10).map { it.seriesId })
    }

    @Test
    fun searchSeriesByFts() {
        assertEquals(
            setOf(10, 11),
            db
                .seriesDao()
                .searchByFts(P, "king*", includeExcluded = false)
                .map { it.seriesId }
                .toSet(),
        )
    }

    @Test
    fun countExcludedSeriesByFts() {
        assertEquals(1, db.seriesDao().countExcludedByFts(P, "king*"))
    }

    @Test
    fun aHiddenCategoryWithAnAllowedItemShows() {
        val categories = db.categoryDao()
        assertEquals(listOf("hidden", "shown"), categories.getCategories(P, XtreamCategoryEntity.TYPE_VOD).map { it.categoryId })
        assertEquals(listOf("s-hidden", "s-shown"), categories.getCategories(P, XtreamCategoryEntity.TYPE_SERIES).map { it.categoryId })
        assertEquals(listOf("hidden"), categories.getAllowedCategoryIds(P, XtreamCategoryEntity.TYPE_VOD))
        assertEquals(listOf("s-hidden"), categories.getAllowedCategoryIds(P, XtreamCategoryEntity.TYPE_SERIES))
        assertEquals(listOf("2"), categories.getAllowedItemIds(P, XtreamCategoryEntity.TYPE_VOD))
        assertEquals(listOf("11"), categories.getAllowedItemIds(P, XtreamCategoryEntity.TYPE_SERIES))
        assertEquals(listOf<String>(), categories.getAllowedItemIds(P, XtreamCategoryEntity.TYPE_LIVE))
    }

    @Test
    fun clearingAndSettingTheFlag() {
        db.streamDao().clearAllowed(P)
        db.seriesDao().clearAllowed(P)
        assertEquals(listOf(1), db.streamDao().getAllStreams(P, VOD).map { it.streamId })
        assertEquals(listOf("shown"), db.categoryDao().getCategories(P, XtreamCategoryEntity.TYPE_VOD).map { it.categoryId })
        assertEquals(listOf(10), db.seriesDao().getAllSeries(P).map { it.seriesId })

        db.streamDao().setAllowed(P, VOD, listOf(3))
        db.seriesDao().setAllowed(P, listOf(12))
        assertEquals(listOf(3), db.streamDao().getStreamsByCategory(P, VOD, "hidden").map { it.streamId })
        assertEquals(listOf(12), db.seriesDao().getSeriesByCategory(P, "s-hidden").map { it.seriesId })
    }

    private fun movie(
        id: Int,
        categoryId: String,
        name: String,
        allowed: Boolean = false,
    ) = XtreamStreamEntity(id, P, VOD, id, name, "movie", categoryId = categoryId, tmdbId = "t1", allowed = allowed)

    private fun series(
        id: Int,
        categoryId: String,
        name: String,
        allowed: Boolean = false,
    ) = XtreamSeriesEntity(seriesId = id, providerId = P, name = name, categoryId = categoryId, tmdbId = "s1", allowed = allowed)

    private companion object {
        const val P = 42L
        const val PROFILE = "p"
        const val VOD = XtreamStreamEntity.TYPE_VOD
    }
}
