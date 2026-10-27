package org.njarasoa.fijerena.feature.wanted

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.wanted.CatalogueHit
import org.njarasoa.fijerena.core.network.wanted.ReleaseStage
import org.njarasoa.fijerena.core.network.wanted.TmdbTitle
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.trailer.rememberPlayTrailer
import org.njarasoa.fijerena.core.ui.utils.displayDate
import org.njarasoa.fijerena.core.ui.utils.titleFactRows
import org.njarasoa.fijerena.core.ui.viewmodels.TmdbTitleViewModel
import org.njarasoa.fijerena.core.ui.wanted.label
import org.njarasoa.fijerena.feature.movie.CastChipsTabContent
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.CinemaTabRow
import org.njarasoa.fijerena.ui.components.ExpandablePlot
import org.njarasoa.fijerena.ui.components.MetaLine
import org.njarasoa.fijerena.ui.components.MetaText
import org.njarasoa.fijerena.ui.components.MobileDetailHero
import org.njarasoa.fijerena.ui.components.MobileDetailRow
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.DetailIconAction
import org.njarasoa.fijerena.ui.components.detailBackBacking
import org.njarasoa.fijerena.ui.components.detailTopBarColors
import org.njarasoa.fijerena.ui.components.mobileClockTopBarInsets
import org.njarasoa.fijerena.ui.components.ratingOutOfTen

/**
 * A film or show as TMDB knows it, which no source of the profile has (wanted titles plan → The
 * details page), laid out as a film page: TMDB's art, genres, facts and plot, a line saying no
 * source has it in place of Play, Add to / Remove from Wishlist, then the Details, Cast and More
 * like this tabs. When a source has the title (now, or after a sync
 * while the page is open), it opens that copy instead through [onOpenCopy].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileTmdbTitleScreen(
    title: TmdbTitle,
    onOpenCopy: (CatalogueHit) -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit,
) {
    val context = LocalContext.current
    val playTrailer = rememberPlayTrailer()
    val viewModel: TmdbTitleViewModel =
        viewModel(
            key = "tmdb_${title.contentType}_${title.tmdbId}",
            factory = remember(title) { TmdbTitleViewModel.Factory(context, title) },
        )
    val details by viewModel.details.collectAsStateWithLifecycle()
    val isWanted by viewModel.isWanted.collectAsStateWithLifecycle()
    val copy by viewModel.copy.collectAsStateWithLifecycle()
    val related by viewModel.related.collectAsStateWithLifecycle()

    LaunchedEffect(copy) {
        copy?.let {
            viewModel.openingCopy()
            onOpenCopy(it)
        }
    }

    // As the film page: a clear top bar over the backdrop, solid with the title once it has scrolled away.
    var heroCovered by remember { mutableStateOf(false) }
    val typeLabel =
        stringResource(if (title.contentType == ContentType.MOVIES) R.string.wanted_kind_movie else R.string.wanted_kind_show)
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = mobileClockTopBarInsets(),
                colors = detailTopBarColors(heroCovered),
                title = {
                    if (heroCovered) {
                        CinemaText(
                            text = title.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            role = TextRole.Title,
                            tone = TextTone.Inherit,
                        )
                    }
                },
                navigationIcon = {
                    CinemaIconButton(
                        modifier = Modifier.detailBackBacking(heroCovered),
                        onClick = onBack,
                        icon = {
                            Icon(CinemaIcons.ArrowBack, stringResource(R.string.common_back), tint = CinemaTheme.colors.textPrimary)
                        },
                    )
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(CinemaTheme.colors.background),
            contentPadding =
                PaddingValues(
                    start = CinemaSpacing.md,
                    end = CinemaSpacing.md,
                    bottom = paddingValues.calculateBottomPadding() + CinemaSpacing.md + LocalDockInset.current,
                ),
        ) {
            item(key = "detail") {
                Column {
                    MobileDetailHero(
                        title = title.title,
                        backdropUrl = details.backdropUrl,
                        posterUrl = TmdbApiService.posterUrl(title.posterPath, TmdbApiService.POSTER_SIZE_W342),
                        logoUrl = details.logoUrl,
                        thumbnailContentType = ThumbnailContentType.MOVIE,
                        bleed = CinemaSpacing.md,
                        onCovered = { heroCovered = it },
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    details.genres?.let { CinemaText(text = it, role = TextRole.Heading, tone = TextTone.Secondary) }
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                    MetaLine(
                        segments =
                            listOfNotNull<@Composable () -> Unit>(
                                // Where it is in its release first, unless it is simply out (wishlist UX plan, Phase 3).
                                details.release
                                    ?.takeIf { it.stage != ReleaseStage.OUT }
                                    ?.let { release -> { MetaText(release.label(LocalResources.current)) } },
                                title.year?.let { { MetaText(it.toString()) } },
                                { MetaText(typeLabel) },
                                details.rating?.let { { MetaText(ratingOutOfTen(it.toString())) } },
                                details.runtimeSeconds?.let { { MetaText(formatDuration(it)) } },
                            ),
                    )
                    // Where a film page has Play: why there is none.
                    Spacer(modifier = Modifier.height(CinemaSpacing.lg))
                    CinemaText(
                        text = stringResource(R.string.wanted_no_source_yet),
                        tone = TextTone.Secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DetailIconAction(
                            icon = if (isWanted) CinemaIcons.Bookmark else CinemaIcons.BookmarkBorder,
                            label = stringResource(if (isWanted) R.string.wanted_remove else R.string.wanted_add),
                            onClick = viewModel::toggleWanted,
                            tint = if (isWanted) CinemaTheme.colors.accent else CinemaTheme.colors.textPrimary,
                        )
                        details.trailerUrl?.let { trailer ->
                            DetailIconAction(
                                icon = CinemaIcons.Movie,
                                label = stringResource(R.string.details_watch_trailer),
                                onClick = { playTrailer(trailer) },
                            )
                        }
                    }
                    details.facts.tagline?.let {
                        Spacer(modifier = Modifier.height(CinemaSpacing.md))
                        CinemaText(text = it, tone = TextTone.Secondary)
                    }
                    details.overview?.takeIf { it.isNotBlank() }?.let { plot ->
                        Spacer(modifier = Modifier.height(CinemaSpacing.md))
                        ExpandablePlot(plot = plot, modifier = Modifier.fillMaxWidth())
                    }
                    TmdbTitleTabs(
                        details = details,
                        shownName = title.title,
                        isShow = title.contentType != ContentType.MOVIES,
                        related = related,
                        onRelatedTitleSelected = onRelatedTitleSelected,
                    )
                }
            }
        }
    }
}

private enum class TmdbTitleTab { DETAILS, CAST, MORE_LIKE_THIS }

/** The film page's tabs, from what TMDB has: Details, Cast, More like this. */
@Composable
private fun TmdbTitleTabs(
    details: TmdbTitleViewModel.Details,
    shownName: String,
    isShow: Boolean,
    related: RelatedTitles,
    onRelatedTitleSelected: (MediaItem) -> Unit,
) {
    val hasCast = details.cast.isNotEmpty()
    val hasMoreLikeThis = related.moreLikeThis.isNotEmpty() || related.collection.isNotEmpty()
    val tabs =
        remember(hasCast, hasMoreLikeThis) {
            buildList {
                add(TmdbTitleTab.DETAILS)
                if (hasCast) add(TmdbTitleTab.CAST)
                if (hasMoreLikeThis) add(TmdbTitleTab.MORE_LIKE_THIS)
            }
        }
    var pickedTab by rememberSaveable { mutableStateOf<TmdbTitleTab?>(null) }
    val selectedTab = pickedTab?.takeIf { it in tabs } ?: tabs.first()
    Spacer(modifier = Modifier.height(CinemaSpacing.lg))
    if (tabs.size > 1) {
        CinemaTabRow(
            selectedIndex = tabs.indexOf(selectedTab),
            labels =
                tabs.map {
                    when (it) {
                        TmdbTitleTab.DETAILS -> stringResource(R.string.details_tab_details)
                        TmdbTitleTab.CAST -> stringResource(R.string.details_tab_cast)
                        TmdbTitleTab.MORE_LIKE_THIS -> stringResource(R.string.details_more_like_this)
                    }
                },
            onSelect = { pickedTab = tabs[it] },
        )
        Spacer(modifier = Modifier.height(CinemaSpacing.md))
    }
    when (selectedTab) {
        TmdbTitleTab.DETAILS -> {
            Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                details.releaseDate?.let {
                    MobileDetailRow(
                        label = stringResource(R.string.details_label_released),
                        value = displayDate(it),
                    )
                }
                details.director?.let { MobileDetailRow(label = stringResource(R.string.details_label_director), value = it) }
                titleFactRows(details.facts, shownName, isShow).forEach { MobileDetailRow(label = it.label, value = it.value) }
            }
        }

        TmdbTitleTab.CAST -> {
            CastChipsTabContent(cast = details.cast.joinToString(", "))
        }

        TmdbTitleTab.MORE_LIKE_THIS -> {
            Column {
                if (related.collection.isNotEmpty()) {
                    RelatedTitlesRow(
                        title = related.collectionName ?: stringResource(R.string.details_collection_fallback),
                        items = related.collection,
                        onItemClick = onRelatedTitleSelected,
                    )
                }
                if (related.moreLikeThis.isNotEmpty()) {
                    RelatedTitlesRow(
                        title = stringResource(R.string.details_more_like_this),
                        items = related.moreLikeThis,
                        onItemClick = onRelatedTitleSelected,
                        modifier = if (related.collection.isNotEmpty()) Modifier.padding(top = CinemaSpacing.lg) else Modifier,
                    )
                }
            }
        }
    }
}
