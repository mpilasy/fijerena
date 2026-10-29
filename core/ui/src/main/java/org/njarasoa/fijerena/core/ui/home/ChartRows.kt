package org.njarasoa.fijerena.core.ui.home

import android.content.Context
import androidx.annotation.StringRes
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.tmdb.TmdbChart
import org.njarasoa.fijerena.core.network.tmdb.TmdbChartPages
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
 * Admin switch is off, without a TMDB key, or for a child (CH-D6); a chart with no match has no row.
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

    /** Whether chart rows may show at all on this device for the profile in use. */
    fun enabled(context: Context): Boolean {
        val settings = AppSettings(context.applicationContext)
        return settings.showTmdbCharts &&
            !settings.isChildProfile(settings.activeProfileId) &&
            TmdbApiService.getInstance(context).hasApiKey()
    }

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
        return TmdbChart
            .of(contentType)
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
        val sources =
            merged.inProfileOrder.map { source ->
                suspend { titles: List<TmdbRecommendation> ->
                    source.repo.findTmdbTitles(titles, chart.contentType).map { it?.copy(providerId = source.providerId) }
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

    /** The row's title. */
    @StringRes
    fun titleRes(chart: TmdbChart): Int =
        when (chart) {
            TmdbChart.TRENDING_MOVIES -> R.string.chart_trending_movies
            TmdbChart.TRENDING_SHOWS -> R.string.chart_trending_shows
            TmdbChart.POPULAR_MOVIES -> R.string.chart_popular_movies
            TmdbChart.POPULAR_SHOWS -> R.string.chart_popular_shows
            TmdbChart.TOP_RATED_MOVIES -> R.string.chart_top_rated_movies
            TmdbChart.TOP_RATED_SHOWS -> R.string.chart_top_rated_shows
        }
}
