package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.tmdb.TmdbChart
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.ui.home.ChartRows

/** The Charts list of Movies and TV Shows (tmdb charts plan, CH-D2): each chart under its heading. */
class ChartListRowsTest {
    private fun film(
        id: String,
        source: Long,
    ) = MediaItem(id = id, name = "Film $id", mediaType = MediaType.MOVIE, categoryId = "7", providerId = source)

    private fun rows(rows: List<ChartRows.Row>) = CategoryViewModel.chartListRows(rows) { it.name }

    @Test
    fun `each chart shows its heading, then its titles, in chart order`() {
        val list =
            rows(
                listOf(
                    ChartRows.Row(TmdbChart.TRENDING_MOVIES, listOf(film("1", 1), film("2", 2))),
                    ChartRows.Row(TmdbChart.POPULAR_MOVIES, listOf(film("3", 1))),
                ),
            )

        assertEquals(listOf("TRENDING_MOVIES", "Film 1", "Film 2", "POPULAR_MOVIES", "Film 3"), list.map { it.name })
        assertEquals(listOf(true, false, false, true, false), list.map { CategoryViewModel.isChartHeader(it) })
    }

    @Test
    fun `a title of an earlier chart is not listed again, and an emptied chart has no heading`() {
        val list =
            rows(
                listOf(
                    ChartRows.Row(TmdbChart.TRENDING_MOVIES, listOf(film("1", 1))),
                    ChartRows.Row(TmdbChart.POPULAR_MOVIES, listOf(film("1", 1))),
                    // The same id on another source is another title.
                    ChartRows.Row(TmdbChart.TOP_RATED_MOVIES, listOf(film("1", 1), film("1", 2))),
                ),
            )

        assertEquals(listOf("TRENDING_MOVIES", "Film 1", "TOP_RATED_MOVIES", "Film 1"), list.map { it.name })
        assertEquals(2L, list.last().providerId)
    }

    @Test
    fun `a title is never a heading`() {
        assertFalse(CategoryViewModel.isChartHeader(film("chart_header_x", 1)))
        assertTrue(rows(emptyList()).isEmpty())
    }
}
