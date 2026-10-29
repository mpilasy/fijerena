package org.njarasoa.fijerena.core.network.tmdb

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** The chart pages cache and the chart rows (docs/plans/20261029_tmdb-charts-plan.md, Phases 1–2). */
class TmdbChartTest {
    private val dir: File = Files.createTempDirectory("charts").toFile()
    private val calls = mutableListOf<Pair<TmdbChart, Int>>()
    private var now = 1_000_000L
    private var failing = false

    /** A chart of [TOTAL_PAGES] pages, 20 titles each, named "Title <page>-<n>". */
    private val fetch: suspend (TmdbChart, Int) -> TmdbRecommendationsResponse = { chart, page ->
        calls += chart to page
        if (failing) throw IOException("offline")
        TmdbRecommendationsResponse(
            results = (1..20).map { TmdbRecommendation(id = page * 100 + it, title = "Title $page-$it") },
            totalPages = TOTAL_PAGES,
        )
    }

    private fun pages() = TmdbChartPages(dir, fetch, now = { now })

    private fun item(name: String) = MediaItem(id = name, name = name, mediaType = MediaType.VIDEO_FILE, categoryId = "c")

    /** A source holding the titles [has] answers. */
    private fun source(has: (String) -> Boolean): suspend (List<TmdbRecommendation>) -> List<MediaItem?> =
        { titles -> titles.map { t -> t.title?.takeIf(has)?.let(::item) } }

    @Test
    fun `a page read once is not read again the same day, also by a new instance`() =
        runTest {
            pages().page(TmdbChart.POPULAR_MOVIES, 1)
            pages().page(TmdbChart.POPULAR_MOVIES, 1)
            assertEquals(listOf(TmdbChart.POPULAR_MOVIES to 1), calls)
        }

    @Test
    fun `a day old, the chart is read again`() =
        runTest {
            val pages = pages()
            pages.page(TmdbChart.POPULAR_MOVIES, 1)
            now += 24 * 60 * 60 * 1000L
            pages.page(TmdbChart.POPULAR_MOVIES, 1)
            assertEquals(2, calls.size)
        }

    @Test
    fun `pages stored for another request are read again`() =
        runTest {
            File(dir, "top_rated_movies.json").writeText(
                """{"fetchedAtMs":$now,"request":"movie/top_rated","pages":{"1":{"titles":[],"totalPages":1}}}""",
            )
            val page = pages().page(TmdbChart.TOP_RATED_MOVIES, 1)
            assertEquals(20, page?.titles?.size)
            assertEquals(listOf(TmdbChart.TOP_RATED_MOVIES to 1), calls)
        }

    @Test
    fun `top rated asks for the best average among titles with many votes, out a year at least`() {
        val yearAgo =
            java.time.LocalDate
                .now()
                .minusYears(1)
                .toString()
        assertEquals(
            "discover/movie&primary_release_date.lte=$yearAgo&sort_by=vote_average.desc&vote_count.gte=5000",
            TmdbChart.TOP_RATED_MOVIES.request,
        )
        assertEquals(
            "discover/tv&first_air_date.lte=$yearAgo&sort_by=vote_average.desc&vote_count.gte=2000",
            TmdbChart.TOP_RATED_SHOWS.request,
        )
    }

    @Test
    fun `past the last page there is no page and no call`() =
        runTest {
            val pages = pages()
            pages.page(TmdbChart.TOP_RATED_SHOWS, 1)
            assertNull(pages.page(TmdbChart.TOP_RATED_SHOWS, TOTAL_PAGES + 1))
            assertEquals(1, calls.size)
        }

    @Test
    fun `a library with the whole first page costs one call`() =
        runTest {
            val row = matchChart(TmdbChart.POPULAR_MOVIES, pages(), listOf(source { true }))
            assertEquals(20, row.size)
            assertEquals(listOf(TmdbChart.POPULAR_MOVIES to 1), calls)
        }

    @Test
    fun `pages are read only until the row has 20 titles`() =
        runTest {
            // Five titles a page: pages 1 to 4.
            val row = matchChart(TmdbChart.POPULAR_MOVIES, pages(), listOf(source { it.substringAfter('-').toInt() <= 5 }))
            assertEquals(20, row.size)
            assertEquals((1..4).toList(), calls.map { it.second })
            assertEquals("Title 1-1", row.first().name)
        }

    @Test
    fun `the page limit stops a thin library`() =
        runTest {
            val row = matchChart(TmdbChart.POPULAR_MOVIES, pages(), listOf(source { it == "Title 2-3" }), maxPages = 3)
            assertEquals(listOf("Title 2-3"), row.map { it.name })
            assertEquals(3, calls.size)
        }

    @Test
    fun `a title on two sources shows once, from the first source, in chart order`() =
        runTest {
            val first = source { it == "Title 1-2" }
            val second: suspend (List<TmdbRecommendation>) -> List<MediaItem?> = { titles ->
                titles.map { t -> t.title?.takeIf { it == "Title 1-1" || it == "Title 1-2" }?.let { item("$it on second") } }
            }
            val row = matchChart(TmdbChart.POPULAR_MOVIES, pages(), listOf(first, second), maxPages = 1)
            assertEquals(listOf("Title 1-1 on second", "Title 1-2"), row.map { it.name })
        }

    @Test
    fun `no match gives an empty row, and a failed page keeps what matched`() =
        runTest {
            assertEquals(emptyList<MediaItem>(), matchChart(TmdbChart.POPULAR_SHOWS, pages(), listOf(source { false }), maxPages = 2))
            failing = true
            assertEquals(emptyList<MediaItem>(), matchChart(TmdbChart.TRENDING_SHOWS, pages(), listOf(source { true })))
        }

    @Test
    fun `with the missing titles, the first 20 of the chart in order, one page read`() =
        runTest {
            val row =
                chartWithMissing(
                    TmdbChart.TRENDING_MOVIES,
                    pages(),
                    listOf(source { it == "Title 1-2" }),
                    missing = { item("missing ${it.title}") },
                )
            assertEquals(20, row.size)
            assertEquals(listOf("missing Title 1-1", "Title 1-2", "missing Title 1-3"), row.take(3).map { it.name })
            assertEquals(listOf(TmdbChart.TRENDING_MOVIES to 1), calls)
        }

    @Test
    fun `only Trending can show the missing titles, and Popular counts votes`() {
        assertEquals(listOf(TmdbChart.TRENDING_MOVIES, TmdbChart.TRENDING_SHOWS), TmdbChart.entries.filter { it.canShowMissing })
        assertEquals("discover/movie&sort_by=popularity.desc&vote_count.gte=500", TmdbChart.POPULAR_MOVIES.request)
        val yearAgo =
            java.time.LocalDate
                .now()
                .minusYears(1)
                .toString()
        val fiveYearsAgo =
            java.time.LocalDate
                .now()
                .minusYears(5)
                .toString()
        assertEquals(
            "discover/tv&air_date.gte=$yearAgo&first_air_date.gte=$fiveYearsAgo&sort_by=popularity.desc" +
                "&vote_count.gte=200&without_genres=10763|10764|10766|10767",
            TmdbChart.POPULAR_SHOWS.request,
        )
    }

    private companion object {
        const val TOTAL_PAGES = 30
    }
}
