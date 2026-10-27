package org.njarasoa.fijerena.feature.wanted

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.wanted.CatalogueHit
import org.njarasoa.fijerena.core.network.wanted.ReleaseStage
import org.njarasoa.fijerena.core.network.wanted.TmdbTitle
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.trailer.rememberPlayTrailer
import org.njarasoa.fijerena.core.ui.utils.displayDate
import org.njarasoa.fijerena.core.ui.utils.titleFactRows
import org.njarasoa.fijerena.core.ui.viewmodels.TmdbTitleViewModel
import org.njarasoa.fijerena.core.ui.wanted.label
import org.njarasoa.fijerena.feature.movie.CastTabContent
import org.njarasoa.fijerena.ui.components.KeepHeroInView
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.TvDetailHero
import org.njarasoa.fijerena.ui.components.TvDetailRow
import org.njarasoa.fijerena.ui.components.TvSectionTabs
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.components.ratingOutOfTen
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * A film or show as TMDB knows it, which no source of the profile has (wanted titles plan → The
 * details page), laid out as a film page: TMDB's art, facts and plot, a line saying no source has
 * it in place of Play, Add to / Remove from Wishlist (focused on entry), then the Details, Cast and
 * More like this tabs. When a source has the title (now, or after a sync while the page is open),
 * it opens that copy instead through [onOpenCopy].
 *
 * Focus moves as on the film page: Down from the action to the tab row, Up from the tab row to the
 * action, and Back from inside a tab's section to the tab row before it leaves the page.
 */
@Composable
fun TmdbTitleScreen(
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

    val scale = LocalUiScale.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val actionFocus = remember { FocusRequester() }
    val tabRowFocus = remember { FocusRequester() }
    var focusInSection by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { actionFocus.requestFocusWithRetry() }

    // As the film page: Back out of an open section goes to the tab row, else out of the page.
    val back = {
        if (focusInSection) tabRowFocus.requestFocus() else onBack()
    }
    BackHandler { back() }

    val hasCast = details.cast.isNotEmpty()
    val hasMoreLikeThis = related.moreLikeThis.isNotEmpty() || related.collection.isNotEmpty()
    val tabs =
        remember(hasCast, hasMoreLikeThis) {
            buildList {
                add(TmdbTitleTab.DETAILS)
                if (hasCast) add(TmdbTitleTab.CAST)
                // Last: it loads late, so it shifts no other tab when it appears.
                if (hasMoreLikeThis) add(TmdbTitleTab.MORE_LIKE_THIS)
            }
        }
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val safeTabIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex)

    // Down from an action to the tab row, Up back to the top, as on the film page.
    val actionKeys =
        Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) {
                false
            } else if (event.key == Key.DirectionDown) {
                // The tab row may not be composed yet: scroll it in, then focus it.
                if (!tabRowFocus.requestFocus()) {
                    scope.launch {
                        listState.animateScrollToItem(1)
                        tabRowFocus.requestFocusWithRetry()
                    }
                }
                true
            } else if (event.key == Key.DirectionUp) {
                scope.launch { listState.animateScrollToItem(0) }
                true
            } else {
                false
            }
        }

    val typeLabel =
        stringResource(if (title.contentType == ContentType.MOVIES) R.string.wanted_kind_movie else R.string.wanted_kind_show)
    Box(modifier = Modifier.fillMaxSize().background(CinemaTheme.colors.background)) {
        KeepHeroInView(scrollToTop = { listState.animateScrollToItem(0) }) { heroModifier ->
            LazyColumn(
                state = listState,
                // Back on the root, before the focused control: see NAVIGATION_GUIDE → TV Back on Detail Screens.
                modifier =
                    Modifier.fillMaxSize().focusable().onPreviewKeyEvent { event ->
                        if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                            back()
                            true
                        } else {
                            false
                        }
                    },
                contentPadding = PaddingValues(bottom = Spacing.tvSafeMarginVertical.scaled(scale)),
            ) {
                item(key = "hero") {
                    TvDetailHero(
                        title = title.title,
                        backdropUrl =
                            details.backdropUrl ?: TmdbApiService.posterUrl(
                                title.posterPath,
                                TmdbApiService.POSTER_SIZE_ORIGINAL,
                            ),
                        logoUrl = details.logoUrl,
                        titleFallback = {
                            CinemaText(text = title.title, role = TextRole.Display, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        // Where it is in its release first, unless it is simply out (wishlist UX plan, Phase 3).
                        metaLine =
                            listOfNotNull(
                                details.release?.takeIf { it.stage != ReleaseStage.OUT }?.label(LocalResources.current),
                                title.year?.toString(),
                                typeLabel,
                                details.rating?.let { ratingOutOfTen(it.toString()) },
                                details.runtimeSeconds?.let { formatDuration(it) },
                                details.genres,
                            ),
                        plot = details.overview,
                        note = stringResource(R.string.wanted_no_source_yet),
                        modifier = heroModifier,
                    ) {
                        TvIconAction(
                            onClick = viewModel::toggleWanted,
                            icon = if (isWanted) CinemaIcons.Bookmark else CinemaIcons.BookmarkBorder,
                            label = stringResource(if (isWanted) R.string.wanted_remove else R.string.wanted_add),
                            modifier =
                                Modifier
                                    .focusRequester(actionFocus)
                                    .leftToRail()
                                    .then(actionKeys),
                        )
                        details.trailerUrl?.let { trailer ->
                            CinemaIconButton(
                                onClick = { playTrailer(trailer) },
                                modifier = actionKeys,
                                icon = {
                                    Icon(
                                        imageVector = CinemaIcons.Movie,
                                        contentDescription = stringResource(R.string.details_watch_trailer_description),
                                        modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                                    )
                                },
                            )
                        }
                    }
                }

                item(key = "tabs") {
                    TvSectionTabs(
                        tabs = tabs.map { tmdbTitleTabLabel(it) },
                        selectedIndex = safeTabIndex,
                        onTabSelected = {
                            selectedTabIndex = it
                            focusInSection = false
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                                .padding(top = Spacing.xl.scaled(scale))
                                // Up from any tab → the action, with the hero back at its top.
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                        scope.launch {
                                            listState.scrollToItem(0)
                                            actionFocus.requestFocusWithRetry()
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                },
                        entryFocusRequester = tabRowFocus,
                        firstTabModifier = Modifier.leftToRail(),
                    )
                }

                // Keyed by the tab: switching tabs resets the section's own scroll.
                item(key = "tab-section-${tabs.getOrNull(safeTabIndex)}") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                                .padding(top = Spacing.md.scaled(scale))
                                .focusRestorer()
                                // Only the true transition: the tab row sets it back to false (see the film page).
                                .onFocusChanged { if (it.hasFocus) focusInSection = true },
                    ) {
                        when (tabs.getOrNull(safeTabIndex)) {
                            TmdbTitleTab.DETAILS -> {
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    details.facts.tagline?.let { CinemaText(text = it, tone = TextTone.Secondary) }
                                    details.overview?.takeIf { it.isNotBlank() }?.let { plot ->
                                        CinemaText(text = plot)
                                        Spacer(modifier = Modifier.height(Spacing.sm))
                                    }
                                    details.releaseDate?.let {
                                        TvDetailRow(label = stringResource(R.string.details_label_released), value = displayDate(it))
                                    }
                                    details.director?.let {
                                        TvDetailRow(label = stringResource(R.string.details_label_director), value = it)
                                    }
                                    titleFactRows(details.facts, title.title, isShow = title.contentType != ContentType.MOVIES).forEach {
                                        TvDetailRow(label = it.label, value = it.value)
                                    }
                                }
                            }

                            TmdbTitleTab.CAST -> {
                                CastTabContent(cast = details.cast.joinToString(", "))
                            }

                            TmdbTitleTab.MORE_LIKE_THIS -> {
                                Column {
                                    RelatedTitlesRow(
                                        title = related.collectionName ?: stringResource(R.string.details_collection_fallback),
                                        items = related.collection,
                                        onItemClick = onRelatedTitleSelected,
                                    )
                                    RelatedTitlesRow(
                                        title =
                                            if (related.collection.isNotEmpty()) {
                                                stringResource(
                                                    R.string.details_more_like_this,
                                                )
                                            } else {
                                                null
                                            },
                                        items = related.moreLikeThis,
                                        onItemClick = onRelatedTitleSelected,
                                        modifier =
                                            if (related.collection.isNotEmpty()) {
                                                Modifier.padding(top = Spacing.lg.scaled(scale))
                                            } else {
                                                Modifier
                                            },
                                    )
                                }
                            }

                            null -> {
                                Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The film page's tabs, from what TMDB has; Details is always there. */
private enum class TmdbTitleTab { DETAILS, CAST, MORE_LIKE_THIS }

@Composable
private fun tmdbTitleTabLabel(tab: TmdbTitleTab): String =
    when (tab) {
        TmdbTitleTab.DETAILS -> stringResource(R.string.details_tab_details)
        TmdbTitleTab.CAST -> stringResource(R.string.details_tab_cast)
        TmdbTitleTab.MORE_LIKE_THIS -> stringResource(R.string.details_more_like_this)
    }
