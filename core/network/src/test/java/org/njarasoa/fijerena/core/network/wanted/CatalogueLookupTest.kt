package org.njarasoa.fijerena.core.network.wanted

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendation
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [CatalogueLookup] finds a Wanted film or show by TMDB id first, then by title and year (the
 * original title too), in the sources' order, and never in a category the profile has hidden.
 * Against a real in-memory `xtream_v2.db`. See the wanted titles plan, Phase 2.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogueLookupTest {
    private lateinit var db: XtreamDatabase
    private lateinit var lookup: CatalogueLookup

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, XtreamDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        db.categoryDao().insertAll(
            listOf(
                XtreamCategoryEntity("films", A, "Films", type = VOD),
                XtreamCategoryEntity("hidden", A, "Adult", type = VOD, excluded = true),
                XtreamCategoryEntity("films", B, "Films", type = VOD),
                XtreamCategoryEntity("shows", A, "Shows", type = XtreamCategoryEntity.TYPE_SERIES),
            ),
        )
        db.streamDao().insertAll(
            listOf(
                movie(A, 1, "films", "EN - Dune (2021)", tmdbId = "438631"),
                movie(B, 2, "films", "FR - Dune", tmdbId = "438631"),
                movie(A, 3, "films", "EN - Le Fabuleux Destin d'Amélie Poulain", releaseDate = "2001-04-25"),
                movie(A, 4, "hidden", "EN - Hidden Film", tmdbId = "999"),
                movie(A, 5, "films", "EN - Dune: Part Two (2024)"),
            ),
        )
        db.seriesDao().insertAll(
            listOf(XtreamSeriesEntity(seriesId = 7, providerId = A, name = "EN - The Bear (2022)", categoryId = "shows")),
        )
        lookup = CatalogueLookup(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `finds a film by TMDB id in every source, in the order given`() {
        val hits = lookup.find(listOf(B, A), ContentType.MOVIES, "438631", listOf("Dune"), 2021)

        assertEquals(listOf(B to "2", A to "1"), hits.map { it.providerId to it.itemId })
    }

    @Test
    fun `falls back to the original title and year when no item has the id`() {
        val hits = lookup.find(listOf(A), ContentType.MOVIES, "194", listOf("Amelie", "Le Fabuleux Destin d'Amélie Poulain"), 2001)

        assertEquals(listOf("3"), hits.map { it.itemId })
    }

    @Test
    fun `a title match never takes a longer title`() {
        val hits = lookup.find(listOf(A), ContentType.MOVIES, "000", listOf("Dune: Part"), 2024)

        assertEquals(emptyList<String>(), hits.map { it.itemId })
    }

    @Test
    fun `a title match never takes a row with another TMDB id`() {
        val hits = lookup.find(listOf(A), ContentType.MOVIES, "000", listOf("Dune"), 2021)

        assertEquals(emptyList<String>(), hits.map { it.itemId })
    }

    @Test
    fun `an item in a hidden category is not found`() {
        assertEquals(emptyList<CatalogueHit>(), lookup.find(listOf(A), ContentType.MOVIES, "999", listOf("Hidden Film"), null))
    }

    @Test
    fun `finds a show by title`() {
        val hits = lookup.find(listOf(A), ContentType.TV_SHOWS, "136315", listOf("The Bear"), 2022)

        assertEquals(listOf("7"), hits.map { it.itemId })
    }

    @Test
    fun `a no-source card that another source has becomes that source's card`() {
        val inB = TmdbOnlyItem.of(TmdbRecommendation(id = 438631, title = "Dune"), MediaType.MOVIE)
        val nowhere = TmdbOnlyItem.of(TmdbRecommendation(id = 1, title = "Nowhere"), MediaType.MOVIE)

        val related = lookup.inSources(RelatedTitles(moreLikeThis = listOf(inB, nowhere)), listOf(B, A), ContentType.MOVIES)

        assertEquals(listOf(B to "2", 0L to nowhere.id), related.moreLikeThis.map { it.providerId to it.id })
        assertEquals(null, TmdbOnlyItem.titleOf(related.moreLikeThis.first(), ContentType.MOVIES))
    }

    @Test
    fun `the FTS query holds only letters, digits and prefix stars`() {
        assertEquals("dune* part* two*", CatalogueLookup.ftsQuery("Dune: Part Two \"*(2024)"))
        assertEquals(null, CatalogueLookup.ftsQuery("***"))
    }

    private fun movie(
        providerId: Long,
        id: Int,
        categoryId: String,
        name: String,
        tmdbId: String? = null,
        releaseDate: String? = null,
    ) = XtreamStreamEntity(id, providerId, VOD, id, name, "movie", categoryId = categoryId, tmdbId = tmdbId, releaseDate = releaseDate)

    private companion object {
        const val A = 1L
        const val B = 2L
        const val VOD = XtreamStreamEntity.TYPE_VOD
    }
}
