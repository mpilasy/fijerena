package org.njarasoa.fijerena.core.network.tmdb

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import java.io.File
import java.time.LocalDate

/** The TMDB charts Home shows, cut down to the catalogue (docs/plans/20261029_tmdb-charts-plan.md). */
enum class TmdbChart(
    val path: String,
    val contentType: String,
    private val parametersOf: () -> Map<String, String> = { emptyMap() },
) {
    TRENDING_MOVIES("trending/movie/week", ContentType.MOVIES),
    TRENDING_SHOWS("trending/tv/week", ContentType.TV_SHOWS),

    // Not `movie/popular` / `tv/popular`: those mix long-running daytime shows and short spikes of
    // titles nobody knows. TMDB's popularity among titles with enough votes (user, 2026-10-29). For
    // shows, only those first out in the last five years with an episode in the last one, and no
    // talk, news, soap or reality shows: decades-old shows still on air (Grey's Anatomy, The
    // Simpsons) and the late-night shows led the list otherwise.
    POPULAR_MOVIES("discover/movie", ContentType.MOVIES, { popular(minVotes = 500) }),
    POPULAR_SHOWS(
        "discover/tv",
        ContentType.TV_SHOWS,
        {
            popular(minVotes = 200) +
                mapOf(
                    "air_date.gte" to yearAgo(),
                    "first_air_date.gte" to LocalDate.now().minusYears(5).toString(),
                    // News, Reality, Soap, Talk.
                    "without_genres" to "10763|10764|10766|10767",
                )
        },
    ),

    // Not `movie/top_rated` / `tv/top_rated`: those rank by average alone, so a new title with a
    // few perfect votes comes before The Shawshank Redemption. The best average among titles with
    // many votes, out for a year at least: a new release's first voters are its fans, and even with
    // thousands of votes its average starts high (user, 2026-10-29).
    TOP_RATED_MOVIES("discover/movie", ContentType.MOVIES, { topRated(minVotes = 5000, releasedBefore = "primary_release_date.lte") }),
    TOP_RATED_SHOWS("discover/tv", ContentType.TV_SHOWS, { topRated(minVotes = 2000, releasedBefore = "first_air_date.lte") }),
    ;

    /** Whether its row can also show the titles no source has (user, 2026-10-29): Trending only. */
    val canShowMissing: Boolean get() = this == TRENDING_MOVIES || this == TRENDING_SHOWS

    /** The call's parameters, page aside (a Top Rated chart's date limit moves with the day). */
    val parameters: Map<String, String> get() = parametersOf()

    /** What the call asks, page aside: a stored page asked otherwise is of no use. */
    val request: String get() = path + parameters.toSortedMap().entries.joinToString("") { "&${it.key}=${it.value}" }

    companion object {
        /** [contentType]'s charts, in the order their rows show. */
        fun of(contentType: String): List<TmdbChart> = entries.filter { it.contentType == contentType }
    }
}

private fun popular(minVotes: Int) = mapOf("sort_by" to "popularity.desc", "vote_count.gte" to minVotes.toString())

private fun topRated(
    minVotes: Int,
    releasedBefore: String,
) = mapOf(
    "sort_by" to "vote_average.desc",
    "vote_count.gte" to minVotes.toString(),
    releasedBefore to yearAgo(),
)

private fun yearAgo() = LocalDate.now().minusYears(1).toString()

/** One page of a chart: its titles in TMDB's order, and how many pages the chart has. */
@Serializable
data class TmdbChartPage(
    val titles: List<TmdbRecommendation>,
    val totalPages: Int,
)

/**
 * The chart pages read, kept a day per chart for the device (CH design 5): a later Home load asks
 * TMDB only for a page it has not read yet. Kept in memory and in one file per chart under [dir].
 * A day old, a chart starts again from nothing.
 */
class TmdbChartPages(
    private val dir: File,
    private val fetchPage: suspend (TmdbChart, Int) -> TmdbRecommendationsResponse,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Serializable
    private data class Stored(
        val fetchedAtMs: Long,
        val pages: Map<Int, TmdbChartPage> = emptyMap(),
        // [TmdbChart.request] when the pages were read.
        val request: String = "",
    )

    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    private val locks = TmdbChart.entries.associateWith { Mutex() }
    private val memory = mutableMapOf<TmdbChart, Stored>()

    /**
     * Page [page] (from 1) of [chart]: the stored one, else TMDB's, stored. Null past the chart's
     * last page. A failed call throws, and nothing is stored.
     */
    suspend fun page(
        chart: TmdbChart,
        page: Int,
    ): TmdbChartPage? =
        locks.getValue(chart).withLock {
            val stored = current(chart)
            val lastPage =
                stored.pages.values
                    .firstOrNull()
                    ?.totalPages
            if (lastPage != null && page > lastPage) return@withLock null
            stored.pages[page]?.let { return@withLock it }
            val response = fetchPage(chart, page)
            if (page > response.totalPages) return@withLock null
            val fetched = TmdbChartPage(response.results, response.totalPages)
            val updated = stored.copy(pages = stored.pages + (page to fetched))
            memory[chart] = updated
            write(chart, updated)
            fetched
        }

    /** Forgets every chart page read (Admin → Refresh Charts): the next rows read TMDB again. */
    suspend fun clear() {
        for (chart in TmdbChart.entries) {
            locks.getValue(chart).withLock {
                memory.remove(chart)
                withContext(Dispatchers.IO) { file(chart).delete() }
            }
        }
    }

    /** [chart]'s stored pages, read from the file once; none when they are a day old or asked otherwise. */
    private suspend fun current(chart: TmdbChart): Stored {
        val stored = memory[chart] ?: read(chart)?.also { memory[chart] = it }
        if (stored != null && stored.request == chart.request && now() - stored.fetchedAtMs in 0 until MAX_AGE_MS) return stored
        return Stored(fetchedAtMs = now(), request = chart.request).also { memory[chart] = it }
    }

    private fun file(chart: TmdbChart) = File(dir, "${chart.name.lowercase()}.json")

    private suspend fun read(chart: TmdbChart): Stored? =
        withContext(Dispatchers.IO) {
            try {
                file(chart).takeIf { it.exists() }?.readText()?.let { json.decodeFromString<Stored>(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Chart ${chart.name} unreadable; read again from TMDB", e)
                null
            }
        }

    private suspend fun write(
        chart: TmdbChart,
        stored: Stored,
    ) = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            file(chart).writeText(json.encodeToString(Stored.serializer(), stored))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Kept in memory still; the next start reads TMDB again.
            Log.w(TAG, "Chart ${chart.name} not stored", e)
        }
    }

    companion object {
        private const val TAG = "TmdbChartPages"
        private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

        @Volatile
        private var instance: TmdbChartPages? = null

        /** The device's chart pages, in the app's cache directory, read with the app's TMDB key. */
        fun getInstance(context: Context): TmdbChartPages =
            instance ?: synchronized(this) {
                instance ?: run {
                    val tmdb = TmdbApiService.getInstance(context)
                    TmdbChartPages(File(context.applicationContext.cacheDir, "tmdb_charts"), tmdb::getChartPage)
                }.also { instance = it }
            }
    }
}

/**
 * [chart] cut down to the catalogue (CH design 2–4): page by page, each title from the first of
 * [sources] that has it (the profile's order), one card per title, TMDB's order kept. Reads the
 * next page only while fewer than [limit] titles matched, and stops at [maxPages], at the chart's
 * last page, or at the first page that can't be read (what matched so far stays).
 *
 * Each source answers a page with its title for each TMDB title, index-aligned, null where it has
 * none (`MediaRepository.findTmdbTitles`).
 */
suspend fun matchChart(
    chart: TmdbChart,
    pages: TmdbChartPages,
    sources: List<suspend (List<TmdbRecommendation>) -> List<MediaItem?>>,
    maxPages: Int = CHART_MAX_PAGES,
    limit: Int = CHART_ROW_SIZE,
): List<MediaItem> {
    if (sources.isEmpty()) return emptyList()
    val matches = mutableListOf<MediaItem>()
    val taken = mutableSetOf<String>()
    for (number in 1..maxPages) {
        val page =
            try {
                pages.page(chart, number)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("TmdbChart", "Chart ${chart.name} page $number: ${e.message}")
                null
            } ?: break
        if (matchInto(page.titles, sources, matches, taken, limit)) return matches
    }
    return matches
}

/**
 * [titles] cut down to the catalogue, as [matchChart] does with a chart's pages: each title from
 * the first of [sources] that has it, one card per title, the order of [titles] kept, [limit] at
 * most. One call of each source for all the titles.
 */
suspend fun matchTitles(
    titles: List<TmdbRecommendation>,
    sources: List<suspend (List<TmdbRecommendation>) -> List<MediaItem?>>,
    limit: Int = Int.MAX_VALUE,
): List<MediaItem> {
    if (sources.isEmpty() || titles.isEmpty()) return emptyList()
    return mutableListOf<MediaItem>().also { matchInto(titles, sources, it, mutableSetOf(), limit) }
}

/**
 * Adds to [matches] each of [titles] that one of [sources] has and whose name is not in [taken]
 * yet. True when [matches] has [limit] titles.
 */
private suspend fun matchInto(
    titles: List<TmdbRecommendation>,
    sources: List<suspend (List<TmdbRecommendation>) -> List<MediaItem?>>,
    matches: MutableList<MediaItem>,
    taken: MutableSet<String>,
    limit: Int,
): Boolean {
    val found = sources.map { source -> source(titles) }
    for ((index, result) in titles.withIndex()) {
        val key = result.displayTitle?.let { TitleMatcher.normalize(it).text }
        if (key.isNullOrBlank() || key in taken) continue
        val hit = found.firstNotNullOfOrNull { it.getOrNull(index) } ?: continue
        taken += key
        matches += hit
        if (matches.size == limit) return true
    }
    return false
}

/**
 * The first [limit] titles of [chart] as TMDB ranks them, each from the first of [sources] that has
 * it, else as a TMDB-only card ([missing]) that opens the title's TMDB page. For a row that also
 * shows what no source has. Empty when the first page can't be read.
 */
suspend fun chartWithMissing(
    chart: TmdbChart,
    pages: TmdbChartPages,
    sources: List<suspend (List<TmdbRecommendation>) -> List<MediaItem?>>,
    missing: (TmdbRecommendation) -> MediaItem,
    limit: Int = CHART_ROW_SIZE,
): List<MediaItem> {
    val page =
        try {
            pages.page(chart, 1)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("TmdbChart", "Chart ${chart.name} page 1: ${e.message}")
            null
        } ?: return emptyList()
    return titlesWithMissing(page.titles, sources, missing, limit)
}

/**
 * The first [limit] of [titles], in their order, each from the first of [sources] that has it,
 * else as a TMDB-only card ([missing]). One card per title name.
 */
suspend fun titlesWithMissing(
    titles: List<TmdbRecommendation>,
    sources: List<suspend (List<TmdbRecommendation>) -> List<MediaItem?>>,
    missing: (TmdbRecommendation) -> MediaItem,
    limit: Int = Int.MAX_VALUE,
): List<MediaItem> {
    val found = sources.map { source -> source(titles) }
    val taken = mutableSetOf<String>()
    val row = mutableListOf<MediaItem>()
    for ((index, result) in titles.withIndex()) {
        val key = result.displayTitle?.let { TitleMatcher.normalize(it).text }
        if (key.isNullOrBlank() || !taken.add(key)) continue
        row += found.firstNotNullOfOrNull { it.getOrNull(index) } ?: missing(result)
        if (row.size == limit) break
    }
    return row
}

/** CH-D3: the top 500 of a chart at most. */
const val CHART_MAX_PAGES = 25

/** A chart row's most titles. */
const val CHART_ROW_SIZE = 20
