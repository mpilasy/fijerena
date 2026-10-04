package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Streams and series follow their category's `excluded` flag at query time; their own flag is
 * ignored. See docs/plans/archive/20261001_fast-profile-switch-plan.md.
 */
@RunWith(AndroidJUnit4::class)
class CategoryVisibilityTest {
    private lateinit var db: XtreamDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, XtreamDatabase::class.java).build()
        db.categoryDao().insertAll(
            listOf(
                XtreamCategoryEntity("shown", P, "News", type = XtreamCategoryEntity.TYPE_VOD),
                XtreamCategoryEntity("hidden", P, "Adult", type = XtreamCategoryEntity.TYPE_VOD, excluded = true),
                XtreamCategoryEntity("s-shown", P, "Drama", type = XtreamCategoryEntity.TYPE_SERIES),
                XtreamCategoryEntity("s-hidden", P, "Greek", type = XtreamCategoryEntity.TYPE_SERIES, excluded = true),
                // Same id as a hidden VOD category, but live: must not hide live streams.
                XtreamCategoryEntity("hidden", P, "Adult", type = XtreamCategoryEntity.TYPE_LIVE),
            ),
        )
        db.streamDao().insertAll(
            listOf(
                movie(1, "shown", name = "King Kong"),
                movie(2, "hidden", name = "King of Nothing"),
                // Its own stale flag says hidden; its category says shown — the category wins.
                movie(3, "shown", name = "Kingdom", excluded = true),
                // A category this device doesn't know: shown.
                movie(4, "unknown", name = "King Lear"),
                XtreamStreamEntity(5, P, XtreamStreamEntity.TYPE_LIVE, 5, "Live", "live", categoryId = "hidden"),
            ),
        )
        db.seriesDao().insertAll(listOf(series(10, "s-shown", "Kings"), series(11, "s-hidden", "King's Court")))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun streamsFollowTheirCategory() {
        val streams = db.streamDao()
        assertEquals(listOf(1, 3, 4), streams.getAllStreams(P, XtreamStreamEntity.TYPE_VOD).map { it.streamId })
        assertEquals(listOf<Int>(), streams.getStreamsByCategory(P, XtreamStreamEntity.TYPE_VOD, "hidden").map { it.streamId })
        assertEquals(listOf(1, 3), streams.getStreamsByCategory(P, XtreamStreamEntity.TYPE_VOD, "shown").map { it.streamId })
        assertEquals(listOf(5), streams.getAllStreams(P, XtreamStreamEntity.TYPE_LIVE).map { it.streamId })
    }

    @Test
    fun searchHidesFilteredStreamsAndCountsThem() {
        val streams = db.streamDao()
        assertEquals(
            setOf(1, 3, 4),
            streams
                .searchByFts(P, XtreamStreamEntity.TYPE_VOD, "king*", includeExcluded = false)
                .map {
                    it.streamId
                }.toSet(),
        )
        assertEquals(
            setOf(1, 2, 3, 4),
            streams
                .searchByFts(P, XtreamStreamEntity.TYPE_VOD, "king*", includeExcluded = true)
                .map {
                    it.streamId
                }.toSet(),
        )
        assertEquals(1, streams.countExcludedByFts(P, XtreamStreamEntity.TYPE_VOD, "king*"))
    }

    @Test
    fun seriesFollowTheirCategory() {
        val series = db.seriesDao()
        assertEquals(listOf(10), series.getAllSeries(P).map { it.seriesId })
        assertEquals(listOf(10), series.searchByFts(P, "king*", includeExcluded = false).map { it.seriesId })
        assertEquals(1, series.countExcludedByFts(P, "king*"))
    }

    @Test
    fun unhidingACategoryShowsItsStreamsWithoutTouchingThem() {
        db.categoryDao().setExcluded(P, XtreamCategoryEntity.TYPE_VOD, listOf("hidden"), false)
        assertEquals(listOf(1, 2, 3, 4), db.streamDao().getAllStreams(P, XtreamStreamEntity.TYPE_VOD).map { it.streamId })
    }

    private fun movie(
        id: Int,
        categoryId: String,
        name: String,
        excluded: Boolean = false,
    ) = XtreamStreamEntity(id, P, XtreamStreamEntity.TYPE_VOD, id, name, "movie", categoryId = categoryId, excluded = excluded)

    private fun series(
        id: Int,
        categoryId: String,
        name: String,
    ) = XtreamSeriesEntity(seriesId = id, providerId = P, name = name, categoryId = categoryId)

    private companion object {
        const val P = 42L
    }
}
