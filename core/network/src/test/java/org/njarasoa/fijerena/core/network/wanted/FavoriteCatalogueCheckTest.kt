package org.njarasoa.fijerena.core.network.wanted

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [FavoriteCatalogueCheck] flags the favourites their source no longer lists, offers a copy with
 * the same TMDB id (its own source first), records the TMDB id of the ones still there, skips a
 * source whose catalogue is empty, and never deletes a favourite. Against a real in-memory
 * `xtream_v2.db`. See the wanted titles plan, Phase 2.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FavoriteCatalogueCheckTest {
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
                XtreamCategoryEntity("films", A, "Films", type = XtreamStreamEntity.TYPE_VOD),
                XtreamCategoryEntity("films", B, "Films", type = XtreamStreamEntity.TYPE_VOD),
                XtreamCategoryEntity("news", A, "News", type = XtreamStreamEntity.TYPE_LIVE),
            ),
        )
        db.streamDao().insertAll(
            listOf(
                stream(A, 1, XtreamStreamEntity.TYPE_VOD, "films", "EN - Dune", tmdbId = "438631"),
                stream(A, 2, XtreamStreamEntity.TYPE_VOD, "films", "FR - Dune", tmdbId = "438631"),
                stream(B, 3, XtreamStreamEntity.TYPE_VOD, "films", "Dune", tmdbId = "438631"),
                stream(A, 10, XtreamStreamEntity.TYPE_LIVE, "news", "UK: BBC News"),
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a film still there gets its TMDB id and is not flagged`() {
        db.favoriteStateDao().upsert(favorite(A, "1", ContentType.MOVIES))

        val gone = FavoriteCatalogueCheck.run(db, PROFILE, listOf(A))

        assertEquals(emptyList<GoneFavorite>(), gone)
        assertEquals("438631", db.favoriteStateDao().get(A, PROFILE, "1", ContentType.MOVIES, FavoriteKind.STREAM)!!.tmdbId)
    }

    @Test
    fun `a gone film is flagged with a copy from its own source first, and kept`() {
        db.favoriteStateDao().upsert(favorite(A, "99", ContentType.MOVIES, tmdbId = "438631"))

        val gone = FavoriteCatalogueCheck.run(db, PROFILE, listOf(B, A)).single()

        assertEquals(A to "99", gone.providerId to gone.itemId)
        assertEquals(A, gone.copy!!.providerId)
        assertEquals(1, db.favoriteStateDao().getAll(A, PROFILE).size)
    }

    @Test
    fun `a gone film without a TMDB id has no copy`() {
        db.favoriteStateDao().upsert(favorite(A, "99", ContentType.MOVIES))

        assertEquals(null, FavoriteCatalogueCheck.run(db, PROFILE, listOf(A)).single().copy)
    }

    @Test
    fun `a gone channel is flagged without a copy`() {
        db.favoriteStateDao().upsert(favorite(A, "10", ContentType.LIVE_TV))
        db.favoriteStateDao().upsert(favorite(A, "11", ContentType.LIVE_TV))

        val gone = FavoriteCatalogueCheck.run(db, PROFILE, listOf(A))

        assertEquals(listOf(GoneFavorite(A, "11", ContentType.LIVE_TV, null)), gone)
    }

    @Test
    fun `an empty catalogue flags nothing`() {
        db.favoriteStateDao().upsert(favorite(B, "50", ContentType.LIVE_TV))
        db.favoriteStateDao().upsert(favorite(B, "51", ContentType.TV_SHOWS))

        assertEquals(emptyList<GoneFavorite>(), FavoriteCatalogueCheck.run(db, PROFILE, listOf(B)))
    }

    @Test
    fun `a gone show is flagged`() {
        db.seriesDao().insertAll(listOf(XtreamSeriesEntity(seriesId = 7, providerId = A, name = "The Bear", categoryId = "shows")))
        db.favoriteStateDao().upsert(favorite(A, "8", ContentType.TV_SHOWS))

        assertEquals(listOf("8"), FavoriteCatalogueCheck.run(db, PROFILE, listOf(A)).map { it.itemId })
    }

    private fun stream(
        providerId: Long,
        id: Int,
        type: String,
        categoryId: String,
        name: String,
        tmdbId: String? = null,
    ) = XtreamStreamEntity(id, providerId, type, id, name, "movie", categoryId = categoryId, tmdbId = tmdbId)

    private fun favorite(
        providerId: Long,
        itemId: String,
        contentType: String,
        tmdbId: String? = null,
    ) = FavoriteStateEntity(providerId, PROFILE, itemId, contentType, FavoriteKind.STREAM, "name", "c", 1, tmdbId)

    private companion object {
        const val A = 1L
        const val B = 2L
        const val PROFILE = "p"
    }
}
