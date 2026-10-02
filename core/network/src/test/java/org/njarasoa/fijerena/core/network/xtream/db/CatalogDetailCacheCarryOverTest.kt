package org.njarasoa.fijerena.core.network.xtream.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * A catalogue sync rewrites changed rows with REPLACE; these check that the detail-screen columns
 * survive that rewrite (docs/plans/20261002_catalog-sync-cache-churn-plan.md, Phase 1).
 */
class CatalogDetailCacheCarryOverTest {
    private fun stream(tmdbId: String? = null) =
        XtreamStreamEntity(
            streamId = 7,
            providerId = 1L,
            type = XtreamStreamEntity.TYPE_VOD,
            num = 3,
            name = "Movie",
            streamType = "movie",
            categoryId = "10",
            tmdbId = tmdbId,
        )

    private fun series(tmdbId: String? = null) =
        XtreamSeriesEntity(
            seriesId = 9,
            providerId = 1L,
            name = "Law & Order",
            categoryId = "20",
            tmdbId = tmdbId,
        )

    private val streamCache =
        XtreamStreamDetailCache(
            streamId = 7,
            contentRating = "R",
            tmdbId = "550",
            containerExtension = "mkv",
            detailFetchedAt = 1_000L,
            posterPath = "/poster.jpg",
        )

    private val seriesCache =
        XtreamSeriesDetailCache(
            seriesId = 9,
            contentRating = "TV-14",
            tmdbId = "549",
            detailFetchedAt = 2_000L,
            posterPath = "/series.jpg",
        )

    @Test
    fun `a changed movie keeps its detail columns`() {
        val merged = stream().withDetailCache(streamCache)

        assertEquals("R", merged.contentRating)
        assertEquals("550", merged.tmdbId)
        assertEquals("mkv", merged.containerExtension)
        assertEquals(1_000L, merged.detailFetchedAt)
        assertEquals("/poster.jpg", merged.posterPath)
    }

    @Test
    fun `the catalogue's own movie tmdbId wins over the cached one`() {
        assertEquals("600", stream(tmdbId = "600").withDetailCache(streamCache).tmdbId)
    }

    @Test
    fun `a new movie is written as the catalogue sent it`() {
        val row = stream()
        assertSame(row, row.withDetailCache(null))
    }

    @Test
    fun `a changed series keeps its detail columns`() {
        val merged = series().withDetailCache(seriesCache)

        assertEquals("TV-14", merged.contentRating)
        assertEquals("549", merged.tmdbId)
        assertEquals(2_000L, merged.detailFetchedAt)
        assertEquals("/series.jpg", merged.posterPath)
    }

    @Test
    fun `a changed series still has its episode list fetched again`() {
        assertNull(series().withDetailCache(seriesCache).episodesFetchedAt)
    }

    @Test
    fun `the catalogue's own series tmdbId wins over the cached one`() {
        assertEquals("700", series(tmdbId = "700").withDetailCache(seriesCache).tmdbId)
    }
}
