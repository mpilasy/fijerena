package org.njarasoa.fijerena.core.ui.home

import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.tmdb.TmdbChart
import org.njarasoa.fijerena.core.network.tmdb.TmdbChartPages
import org.njarasoa.fijerena.core.network.tmdb.TmdbPeople
import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendation
import org.njarasoa.fijerena.core.network.tmdb.chartWithMissing
import org.njarasoa.fijerena.core.network.tmdb.matchChart
import org.njarasoa.fijerena.core.network.wanted.TmdbOnlyItem
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncRunner
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.history.MergedLists

/**
 * The TMDB chart rows (docs/plans/20261029_tmdb-charts-plan.md): each chart of a section cut down
 * to the titles the profile's Xtream sources have, each card on its own source. Nothing when the
 * Admin switch is off or without a TMDB key; a chart with no match has no row. A child profile gets
 * the kids' charts instead (`TmdbChart.forKids`, the kids' charts plan), cut down to what the
 * child may see.
 *
 * The rows are kept per chart, profile and sources until the next catalogue sync, so going back to
 * Home costs nothing; the chart pages themselves are kept a day ([TmdbChartPages]).
 */
object ChartRows {
    /**
     * One chart's row: its title and its titles, in the chart's order. [showsMissing]: on a Trending
     * row (`TmdbChart.canShowMissing`), whether it also shows the titles no source has, as TMDB-only
     * cards (`TmdbOnlyItem`); the row's own switch flips it ([setShowMissing]).
     */
    data class Row(
        val chart: TmdbChart,
        val items: List<MediaItem>,
        val showsMissing: Boolean = false,
    )

    private data class Key(
        val chart: TmdbChart,
        val profileId: String,
        val sources: List<Long>,
        val catalogueUpdates: Long,
        val showsMissing: Boolean,
    )

    private val kept = mutableMapOf<Key, List<MediaItem>>()

    private val refreshCount = MutableStateFlow(0)

    /** Goes up on each [refresh]: the screens showing chart rows load them again. */
    val refreshes: StateFlow<Int> = refreshCount.asStateFlow()

    /**
     * Admin → Refresh Charts: forgets the chart pages and rows, and the people of the actor pages
     * ([TmdbPeople]), so they are read from TMDB again.
     */
    suspend fun refresh(context: Context) {
        TmdbChartPages.getInstance(context).clear()
        TmdbPeople.getInstance(context).clear()
        synchronized(kept) { kept.clear() }
        refreshCount.value++
    }

    /** Whether chart rows may show at all on this device. */
    fun enabled(context: Context): Boolean =
        AppSettings(context.applicationContext).showTmdbCharts && TmdbApiService.getInstance(context).hasApiKey()

    /**
     * [contentType]'s chart rows that have a title, in [TmdbChart.of]'s order. A Trending row shows
     * even with none, so its switch stays reachable.
     */
    suspend fun load(
        context: Context,
        merged: MergedLists,
        contentType: String,
    ): List<Row> {
        if (!enabled(context)) return emptyList()
        val settings = AppSettings(context.applicationContext)
        return TmdbChart
            .of(contentType, kids = settings.isChildProfile(settings.activeProfileId))
            .map { Row(it, row(context, merged, it), showsMissing = showsMissing(context, it)) }
            .filter { it.items.isNotEmpty() || it.chart.canShowMissing }
    }

    /** Whether [chart]'s row shows the titles no source has, for the profile in use. */
    fun showsMissing(
        context: Context,
        chart: TmdbChart,
    ): Boolean {
        val settings = AppSettings(context.applicationContext)
        return chart.canShowMissing && settings.chartsShowMissing(settings.activeProfileId)
    }

    /** The Trending rows' switch, for the profile in use: also the titles no source has, or not. */
    fun setShowMissing(
        context: Context,
        show: Boolean,
    ) {
        val settings = AppSettings(context.applicationContext)
        settings.setChartsShowMissing(settings.activeProfileId, show)
    }

    /** [chart]'s titles on the profile's sources; empty when charts are off or none matched. */
    suspend fun row(
        context: Context,
        merged: MergedLists,
        chart: TmdbChart,
    ): List<MediaItem> {
        if (!enabled(context)) return emptyList()
        val key =
            Key(
                chart = chart,
                profileId = AppSettings(context.applicationContext).activeProfileId,
                sources = merged.inProfileOrder.map { it.providerId },
                catalogueUpdates = ProviderSyncRunner.catalogueUpdates.value,
                showsMissing = showsMissing(context, chart),
            )
        synchronized(kept) { kept[key] }?.let { return it }
        // A child's: the categories are filtered already (the catalogue queries leave out the
        // child's hidden ones); the hidden titles of the parental rules are not, as on Search.
        val settings = AppSettings(context.applicationContext)
        val rules = if (settings.isChildProfile(key.profileId)) settings.parentalRules() else null
        val sources =
            merged.inProfileOrder.map { source ->
                suspend { titles: List<TmdbRecommendation> ->
                    source.repo.findTmdbTitles(titles, chart.contentType).map { item ->
                        item?.takeUnless { rules?.hidesTitle(it.name) == true }?.copy(providerId = source.providerId)
                    }
                }
            }
        val pages = TmdbChartPages.getInstance(context)
        val row =
            if (key.showsMissing) {
                val mediaType = if (chart.contentType == ContentType.TV_SHOWS) MediaType.SERIES else MediaType.MOVIE
                chartWithMissing(chart, pages, sources, missing = { TmdbOnlyItem.of(it, mediaType) })
            } else {
                matchChart(chart, pages, sources)
            }
        // An empty row is not kept: it may be a failed TMDB call, tried again on the next visit.
        if (row.isNotEmpty()) {
            synchronized(kept) {
                // Rows of an older sync, another profile or other sources are of no more use.
                kept.keys.removeAll {
                    it.catalogueUpdates != key.catalogueUpdates || it.profileId != key.profileId ||
                        it.sources != key.sources
                }
                kept[key] = row
            }
        }
        return row
    }

    /** The row's title; a kids' chart has the same, as a child sees no other. */
    @StringRes
    fun titleRes(chart: TmdbChart): Int =
        when (chart) {
            TmdbChart.TRENDING_MOVIES, TmdbChart.KIDS_TRENDING_MOVIES -> R.string.chart_trending_movies
            TmdbChart.TRENDING_SHOWS, TmdbChart.KIDS_TRENDING_SHOWS -> R.string.chart_trending_shows
            TmdbChart.POPULAR_MOVIES, TmdbChart.KIDS_POPULAR_MOVIES -> R.string.chart_popular_movies
            TmdbChart.POPULAR_SHOWS, TmdbChart.KIDS_POPULAR_SHOWS -> R.string.chart_popular_shows
            TmdbChart.TOP_RATED_MOVIES -> R.string.chart_top_rated_movies
            TmdbChart.TOP_RATED_SHOWS -> R.string.chart_top_rated_shows
        }
}
