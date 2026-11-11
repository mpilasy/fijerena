@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.movie

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.tmdb.TitleCredits
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.domain.cleanTitle
import org.njarasoa.fijerena.core.player.model.channelLabel
import org.njarasoa.fijerena.core.player.model.computeEndsAt
import org.njarasoa.fijerena.core.player.model.extractYear
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.player.model.hasMeaningfulDuration
import org.njarasoa.fijerena.core.player.model.resolutionLabel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaBadge
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.MenuAction
import org.njarasoa.fijerena.core.ui.components.PanelFill
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.panel
import org.njarasoa.fijerena.core.ui.components.rememberChildProfile
import org.njarasoa.fijerena.core.ui.history.LocalNerd
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity
import org.njarasoa.fijerena.core.ui.trailer.rememberPlayTrailer
import org.njarasoa.fijerena.core.ui.utils.displayDate
import org.njarasoa.fijerena.core.ui.utils.titleFactRows
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModelFactory
import org.njarasoa.fijerena.core.ui.wanted.WishlistControl
import org.njarasoa.fijerena.core.ui.wanted.rememberWishlist
import org.njarasoa.fijerena.ui.components.KeepHeroInView
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.TvActionMenu
import org.njarasoa.fijerena.ui.components.TvCreditsTabContent
import org.njarasoa.fijerena.ui.components.TvDetailHero
import org.njarasoa.fijerena.ui.components.TvDetailRow
import org.njarasoa.fijerena.ui.components.TvErrorState
import org.njarasoa.fijerena.ui.components.TvIcon
import org.njarasoa.fijerena.ui.components.TvOtherCopiesList
import org.njarasoa.fijerena.ui.components.TvPickerDialog
import org.njarasoa.fijerena.ui.components.TvSectionTabs
import org.njarasoa.fijerena.ui.components.WishlistRemovePrompt
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.modifiers.pickerFocus
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusableNoScale
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.components.ratingOutOfTen
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun MovieDetailsScreen(
    movieId: String,
    movieName: String,
    categoryId: String,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean, categoryId: String) -> Unit,
    onCategorySelected: (categoryId: String) -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit = {},
    // The route's source.
    providerId: Long = 0L,
    // A cast card or a crew name of the Cast tab: opens the person page.
    onPersonSelected: (personId: String, name: String, crew: Boolean) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val viewModel: MovieDetailsViewModel =
        viewModel(
            factory =
                remember(movieId, categoryId, providerId) {
                    MovieDetailsViewModelFactory(context.applicationContext, movieId, categoryId, movieName, providerId)
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Back from the player: the resume time the player just saved.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshResumeState()
        onPauseOrDispose {}
    }
    // Child profile (the kids' Home plan, Phase 2): no Category button and no favourite undo.
    // More Like This stays, filtered.
    val child = remember { appSettings.isChildProfile(appSettings.activeProfileId) }
    val relatedTitles by viewModel.relatedTitles.collectAsStateWithLifecycle()
    val tmdbTitle by viewModel.tmdbTitle.collectAsStateWithLifecycle()
    val credits by viewModel.credits.collectAsStateWithLifecycle()
    val logoUrl by viewModel.logoUrl.collectAsStateWithLifecycle()
    val backdropUrl by viewModel.backdropUrl.collectAsStateWithLifecycle()
    val alternateStreams by viewModel.alternateStreams.collectAsStateWithLifecycle()
    val sourceNames by viewModel.sourceNames.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is MovieDetailsViewModel.UiState.Loading -> {
            LoadingScreen()
        }

        is MovieDetailsViewModel.UiState.Error -> {
            TvErrorState(
                message = state.message,
                onRetry = { viewModel.loadMovieInfo() },
                title = stringResource(R.string.movie_error_loading),
                onBack = onBack,
                backLabel = stringResource(R.string.movie_back_to_movies),
                extra = {
                    TvOtherCopiesList(alternateStreams, sourceNames) {
                        if (viewModel.otherSourceOf(it) != null) onRelatedTitleSelected(it) else viewModel.switchToAlternateStream(it)
                    }
                },
            )
        }

        is MovieDetailsViewModel.UiState.Success -> {
            // The Wishlist icon and its ask-once prompt (wishlist UX plan, Phase 1); none for a child.
            val wishlistTitle = tmdbTitle ?: cleanTitle(state.movieDetail.name.ifEmpty { state.streamName })
            val wishlist =
                rememberWishlist(
                    contentType = ContentType.MOVIES,
                    tmdbId = state.movieDetail.metadata.tmdbId,
                    title = wishlistTitle,
                    year = extractYear(state.movieDetail.metadata.year, state.movieDetail.metadata.releaseDate, state.movieDetail.name),
                    watched = state.isWatched,
                )
            if (wishlist?.askRemove == true) WishlistRemovePrompt(wishlistTitle, wishlist.answer)
            MovieDetailsContent(
                wishlist = wishlist,
                movieDetail = state.movieDetail,
                // A child's are filtered like its Search (relatedInSources → forChild).
                relatedTitles = relatedTitles,
                credits = credits,
                tmdbTitle = tmdbTitle,
                logoUrl = logoUrl,
                backdropUrl = backdropUrl,
                alternateStreams = alternateStreams,
                sourceNames = sourceNames,
                movieId = state.movieDetail.id,
                movieName = state.streamName,
                isFavorite = state.isFavorite,
                isWatched = state.isWatched,
                resumePositionMs = state.resumePositionMs,
                resumeDurationMs = state.resumeDurationMs,
                categoryName = state.categoryName.takeUnless { child },
                onPlayMovie = {
                    id,
                    name,
                    extension,
                    fromStart,
                    ->
                    onPlayMovie(id, name, extension, fromStart, viewModel.currentCategoryId)
                },
                onCategorySelected = { onCategorySelected(state.categoryId) },
                onToggleFavorite = if (child) null else ({ viewModel.toggleFavorite(state.streamName) }),
                onToggleWatched = if (viewModel.supportsWatchedMark) ({ viewModel.toggleWatched() }) else null,
                onRefresh = { viewModel.refreshMovieInfo() },
                onBack = onBack,
                onRelatedTitleSelected = onRelatedTitleSelected,
                // A copy on another source opens on that source, as a related title does.
                onAlternateStreamSelected = {
                    if (viewModel.otherSourceOf(it) != null) onRelatedTitleSelected(it) else viewModel.switchToAlternateStream(it)
                },
                providerId = providerId,
                onPersonSelected = onPersonSelected,
            )
        }
    }
}

@Composable
private fun MovieDetailsContent(
    wishlist: WishlistControl?,
    movieDetail: MovieDetail,
    relatedTitles: RelatedTitles,
    credits: TitleCredits?,
    tmdbTitle: String?,
    logoUrl: String?,
    backdropUrl: String?,
    alternateStreams: List<MediaItem>,
    sourceNames: Map<Long, String>,
    movieId: String,
    movieName: String,
    isFavorite: Boolean,
    isWatched: Boolean,
    resumePositionMs: Long,
    resumeDurationMs: Long,
    categoryName: String?,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onCategorySelected: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    onToggleWatched: (() -> Unit)?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit,
    onAlternateStreamSelected: (MediaItem) -> Unit,
    providerId: Long,
    onPersonSelected: (personId: String, name: String, crew: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    var providerName by remember { mutableStateOf(appSettings.providerName) }
    // AppSettings.providerName is the legacy single-provider key: it stays "My Provider" for every
    // Room provider. Read the route's source's name from the DB instead, as TwoColumnLayout.kt does.
    LaunchedEffect(providerId) {
        val repo =
            org.njarasoa.fijerena.core.network.provider
                .ProviderRepository(context.applicationContext)
        repo.getProviderById(providerId)?.let { providerName = it.name }
    }
    val extension = movieDetail.extension ?: "mp4"
    val scale = LocalUiScale.current

    val playButtonFocusRequester = remember { FocusRequester() }
    // Focus requester for the stream name row, so switching to an alternate stream can keep
    // focus there instead of it falling back to the window root (see streamSwitchSignal below).
    val streamNameFocusRequester = remember { FocusRequester() }
    // True once the row actually reports itself focused — lets the reassertion loop below stop
    // as soon as it has actually won, instead of guessing how many frames that takes.
    var streamRowFocused by remember { mutableStateOf(false) }
    // Bumped in onSelect: selecting a dropdown item destroys the focused node, so focus falls to the
    // window root and the D-pad does nothing until this claims it back for the row. The
    // resumePositionMs effect below also reads it as a sticky "a switch happened" flag: a later
    // resumePositionMs change (a slow network fetch after a fast cache draw) must not take focus back
    // to Play. rememberSaveable, because the screen is disposed while the player is open (see
    // EpisodeSelectionScreen's identical signal and the episode selection fragility plan).
    var streamSwitchSignal by rememberSaveable { mutableStateOf(0) }

    // One FocusRequester on the selected tab (the tv detail hero ui plan, Phase 4): D-pad Down from
    // the action row and Back from inside a section both go to it.
    val tabRowFocusRequester = remember { FocusRequester() }
    // True while focus is anywhere inside the selected tab's section content — read by the
    // LazyColumn's onPreviewKeyEvent below to decide what Back does.
    var focusInSection by remember { mutableStateOf(false) }

    // Fallback while the LazyColumn's onPreviewKeyEvent is not in the tree yet. It must apply the same
    // focusInSection branch: on the TV emulator a Back press can reach this fallback even with that
    // handler in the tree, and an unconditional onBack() here left the screen past an open section.
    BackHandler {
        if (focusInSection) {
            tabRowFocusRequester.requestFocus()
        } else {
            onBack()
        }
    }

    // Tabs are built from what this movie has (the tv detail hero ui plan, Phase 4), and only the
    // selected tab's content composes.
    // TMDB's credits when it has them (cast tab plan), else the source's cast names (CT-D7).
    val tmdbCredits = credits?.takeUnless { it.isEmpty }
    val hasCast = tmdbCredits != null || !movieDetail.metadata.cast.isNullOrBlank()
    val hasMoreLikeThis = relatedTitles.moreLikeThis.isNotEmpty() || relatedTitles.collection.isNotEmpty()
    val tabs =
        remember(hasCast, hasMoreLikeThis) {
            buildList {
                add(MovieDetailTab.DETAILS)
                if (hasCast) add(MovieDetailTab.CAST)
                // Last: it loads late, so it shifts no other tab when it appears.
                if (hasMoreLikeThis) add(MovieDetailTab.MORE_LIKE_THIS)
            }
        }
    // The tab picked, by name: a tab that comes late (Cast, when the credits arrive) moves the
    // others, and an index would then show another tab.
    var selectedTabName by rememberSaveable { mutableStateOf<String?>(null) }
    val safeTabIndex = tabs.indexOfFirst { it.name == selectedTabName }.takeIf { it >= 0 } ?: 0
    val tabLabels = tabs.map { movieDetailTabLabel(it) }

    // Back from what Start Over, the Category button or a related title opened lands on that control.
    // Play/Resume needs none: the effect below focuses it anyway. The category button and related cards
    // are in the tab section (item 2), so it is scrolled in first.
    val returnFocus = rememberNavReturnFocus()
    val movieListState = rememberLazyListState()
    NavReturnFocusEffect(returnFocus, fallback = playButtonFocusRequester) { key ->
        if (key != RETURN_START_OVER) movieListState.scrollToItem(TAB_SECTION_ITEM_INDEX)
    }
    val relatedReturnId = returnFocus.key?.takeIf { it.startsWith(RETURN_RELATED_PREFIX) }?.removePrefix(RETURN_RELATED_PREFIX)
    val playFromStart: () -> Unit = {
        returnFocus.leaveFrom(RETURN_START_OVER)
        onPlayMovie(movieId, movieDetail.name.ifEmpty { movieName }, extension, true)
    }

    // Focus Play when the screen loads or resume data arrives, unless the user switched to an alternate
    // stream on this screen (focus stays on the stream name row) or Back is about to focus another
    // control (above).
    LaunchedEffect(resumePositionMs) {
        if (streamSwitchSignal == 0 && returnFocus.key == null) {
            playButtonFocusRequester.requestFocusWithRetry()
        }
    }

    LaunchedEffect(streamSwitchSignal) {
        if (streamSwitchSignal == 0) return@LaunchedEffect
        // The dropdown's dismissal focuses the window root asynchronously, later than a fixed frame count
        // on real hardware. Poll, and stop as soon as the row reports focus.
        var attempts = 0
        while (!streamRowFocused && attempts < 90) {
            streamNameFocusRequester.requestFocus()
            withFrameNanos { }
            attempts++
        }
        // Never leave focus on the window root, where the D-pad moves nothing.
        if (!streamRowFocused) playButtonFocusRequester.requestFocusWithRetry()
    }

    val refreshScope = rememberCoroutineScope()

    // "More" menu (Refresh info). A Dialog window: closing it hands focus back to the More button.
    var showMoreMenu by remember { mutableStateOf(false) }
    var moreMenuOpened by remember { mutableStateOf(false) }
    val moreButtonFocusRequester = remember { FocusRequester() }
    LaunchedEffect(showMoreMenu) {
        if (showMoreMenu) {
            moreMenuOpened = true
        } else if (moreMenuOpened) {
            moreMenuOpened = false
            moreButtonFocusRequester.requestFocusWithRetry(fallback = playButtonFocusRequester)
        }
    }
    if (showMoreMenu) {
        TvActionMenu(
            title = tmdbTitle ?: cleanTitle(movieDetail.name.ifEmpty { movieName }),
            actions =
                listOf(
                    MenuAction(icon = CinemaIcons.Refresh, label = stringResource(R.string.movie_refresh_info), onClick = onRefresh),
                ),
            onDismiss = { showMoreMenu = false },
        )
    }

    Box(modifier = Modifier.fillMaxSize().panel(PanelFill.Ground)) {
        // Lazy, not Column(verticalScroll): a scrolling Column measures every child, and the off-screen
        // Similar Titles row took 85 ms of a 215 ms measure pass on each rebuild (slow Back from the player).
        KeepHeroInView(scrollToTop = { movieListState.animateScrollToItem(0) }) { heroModifier ->
            LazyColumn(
                state = movieListState,
                // On a Shield the first Back press while a TV Button has focus never reaches BackHandler; only the
                // second does. onPreviewKeyEvent runs top-down, before the focused Button, so it always wins. Same
                // pattern as TvDpadEscape.kt.
                modifier =
                    Modifier.fillMaxSize().focusable().onPreviewKeyEvent { event ->
                        if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                            // Back from an open tab section goes to the tab row, not out of the screen.
                            if (focusInSection) {
                                tabRowFocusRequester.requestFocus()
                            } else {
                                onBack()
                            }
                            true
                        } else {
                            false
                        }
                    },
                // No horizontal margin here: the hero backdrop must run edge to edge. Every other item applies
                // Spacing.tvSafeMarginHorizontal itself (see "details" below).
                contentPadding = PaddingValues(bottom = Spacing.tvSafeMarginVertical.scaled(scale)),
            ) {
                item(key = "hero") {
                    // TMDB's logo art when it has one, else TMDB's original title, else the provider's stream name (no
                    // TMDB match, or the lookup has not come back yet).
                    val titleText = tmdbTitle ?: cleanTitle(movieDetail.name.ifEmpty { movieName })
                    val year =
                        extractYear(movieDetail.metadata.year, movieDetail.metadata.releaseDate, movieDetail.name.ifBlank { movieName })
                    val endsAtContext = LocalContext.current
                    val endsAtText =
                        remember(movieDetail.metadata.duration, resumePositionMs) {
                            computeEndsAt(endsAtContext, movieDetail.metadata.duration, resumePositionMs)
                        }
                    val metaLine =
                        listOfNotNull(
                            year?.toString(),
                            movieDetail.metadata.rating?.let { ratingOutOfTen(it) },
                            movieDetail.metadata.contentRating,
                            movieDetail.metadata.duration
                                ?.takeIf(::hasMeaningfulDuration)
                                ?.let { formatDuration(it) },
                            endsAtText?.let { stringResource(R.string.movie_ends_at_format, it) },
                            movieDetail.metadata.genre,
                        )
                    val hasResume = resumePositionMs > 0L

                    TvDetailHero(
                        title = titleText,
                        backdropUrl = backdropUrl,
                        logoUrl = logoUrl,
                        titleFallback = {
                            CinemaText(text = titleText, role = TextRole.Display, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        metaLine = metaLine,
                        plot = movieDetail.metadata.plot,
                        note = if (movieDetail.isSavedCopy) stringResource(R.string.details_saved_copy_tv) else null,
                        modifier = heroModifier,
                    ) {
                        // D-pad Down from any action button goes to the tab row, not where geometry search prefers.
                        // focusProperties { down = ... } did not work for the same move on EpisodeSelectionScreen, so the
                        // key is intercepted and focus requested directly.
                        val downToTabRow =
                            Modifier.onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                    // The tab row may not be composed yet (a hero taller than the
                                    // screen): scroll it in, then focus it.
                                    if (!tabRowFocusRequester.requestFocus()) {
                                        refreshScope.launch {
                                            movieListState.animateScrollToItem(1)
                                            tabRowFocusRequester.requestFocusWithRetry()
                                        }
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                        // D-pad Up from the hero action row (the topmost focusables) scrolls the list to the top, as on
                        // EpisodeSelectionScreen: else a tall plot keeps the title above the viewport with no way back.
                        val upScrollToTop =
                            Modifier.onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                    refreshScope.launch { movieListState.animateScrollToItem(0) }
                                    true
                                } else {
                                    false
                                }
                            }
                        if (hasResume) {
                            val resumeTimeText = formatTime(resumePositionMs)
                            CinemaPrimaryButton(
                                onClick = { onPlayMovie(movieId, movieDetail.name.ifEmpty { movieName }, extension, false) },
                                text = stringResource(R.string.movie_resume_from_format, resumeTimeText),
                                icon = CinemaIcons.PlayArrow,
                                modifier =
                                    Modifier
                                        .focusRequester(
                                            playButtonFocusRequester,
                                        ).then(downToTabRow)
                                        .then(upScrollToTop)
                                        .leftToRail(),
                            )
                            TvIconAction(
                                onClick = playFromStart,
                                icon = CinemaIcons.Replay,
                                label = stringResource(R.string.player_osd_start_over),
                                modifier =
                                    downToTabRow
                                        .then(upScrollToTop)
                                        .navReturnFocusTarget(returnFocus, RETURN_START_OVER),
                            )
                        } else {
                            CinemaPrimaryButton(
                                onClick = { onPlayMovie(movieId, movieDetail.name.ifEmpty { movieName }, extension, false) },
                                text = stringResource(R.string.movie_play_action),
                                icon = CinemaIcons.PlayArrow,
                                modifier =
                                    Modifier
                                        .focusRequester(
                                            playButtonFocusRequester,
                                        ).then(downToTabRow)
                                        .then(upScrollToTop)
                                        .leftToRail(),
                            )
                        }
                        // The icon carries the state (filled star, check); the label says what the button is (UX overhaul
                        // Part II Phase 6, F-MD-2).
                        if (onToggleFavorite != null) {
                            TvIconAction(
                                onClick = onToggleFavorite,
                                icon = if (isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                                iconTone = if (isFavorite) TextTone.Accent else TextTone.Primary,
                                label = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
                                modifier = downToTabRow.then(upScrollToTop),
                            )
                        }
                        // Watched button (Phase 6, the watch state durable storage plan);
                        // none where the source keeps watched state on its server (Jellyfin).
                        if (onToggleWatched != null) {
                            TvIconAction(
                                onClick = onToggleWatched,
                                icon = if (isWatched) CinemaIcons.CheckCircleFilled else CinemaIcons.CheckCircleOutline,
                                iconTone = if (isWatched) TextTone.Accent else TextTone.Primary,
                                label = stringResource(if (isWatched) R.string.watched_unmark else R.string.watched_mark),
                                modifier = downToTabRow.then(upScrollToTop),
                            )
                        }
                        if (wishlist != null) {
                            TvIconAction(
                                onClick = wishlist.toggle,
                                icon = if (wishlist.isOn) CinemaIcons.Bookmark else CinemaIcons.BookmarkBorder,
                                iconTone = if (wishlist.isOn) TextTone.Accent else TextTone.Primary,
                                label = stringResource(if (wishlist.isOn) R.string.wanted_remove else R.string.wanted_add),
                                modifier = downToTabRow.then(upScrollToTop),
                            )
                        }
                        movieDetail.metadata.trailerUrl?.let { trailer ->
                            val playTrailer = rememberPlayTrailer()
                            TvIconAction(
                                onClick = { playTrailer(trailer, movieDetail.metadata.tmdbTrailerUrl) },
                                icon = CinemaIcons.Movie,
                                label = stringResource(R.string.details_watch_trailer_description),
                                modifier = downToTabRow.then(upScrollToTop),
                            )
                        }
                        // Refresh info is maintenance: it is behind "More" at the end of the row, out of the path between
                        // Play and the tabs (F-MD-2, R5).
                        TvIconAction(
                            onClick = { showMoreMenu = true },
                            icon = CinemaIcons.MoreVert,
                            label = stringResource(R.string.details_action_more),
                            modifier = downToTabRow.then(upScrollToTop).focusRequester(moreButtonFocusRequester),
                        )
                    }
                }

                item(key = "tabs") {
                    TvSectionTabs(
                        tabs = tabLabels,
                        selectedIndex = safeTabIndex,
                        onTabSelected = {
                            selectedTabName = tabs.getOrNull(it)?.name
                            // A tab that gets focus (Left/Right, Down from the action row, or our own Back request) means focus
                            // is on the tab row, not in a section.
                            focusInSection = false
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                                .padding(top = Spacing.xl.scaled(scale))
                                // Up from any tab goes to Play (F-MD-3), not to the action button above it by geometry. A key
                                // intercept, like downToTabRow: focusProperties here stops at TvSectionTabs' own focusGroup. The
                                // hero scrolls to its top first, so the title shows.
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                        refreshScope.launch {
                                            movieListState.scrollToItem(0)
                                            playButtonFocusRequester.requestFocusWithRetry()
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                },
                        entryFocusRequester = tabRowFocusRequester,
                        firstTabModifier = Modifier.leftToRail(),
                    )
                }

                // Keyed by the selected tab: a tab switch makes a new item, so a scrolling section (a related-titles
                // row) starts at its start (the plan's focus rules).
                item(key = "tab-section-${tabs.getOrNull(safeTabIndex)}") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                                .padding(top = Spacing.md.scaled(scale))
                                .focusRestorer()
                                // Only the true transition is trusted: the Back key fires a false focus change here just before the
                                // key reaches onPreviewKeyEvent, which would then leave the screen instead of going to the tab row.
                                // onTabSelected sets it false (a tab that gets focus, also by this Back path).
                                .onFocusChanged { if (it.hasFocus) focusInSection = true },
                    ) {
                        when (tabs.getOrNull(safeTabIndex)) {
                            MovieDetailTab.CAST -> {
                                if (tmdbCredits != null) {
                                    TvCreditsTabContent(
                                        credits = tmdbCredits,
                                        section = ThumbnailContentType.MOVIE,
                                        returnFocus = returnFocus,
                                        onPersonSelected = onPersonSelected,
                                    )
                                } else {
                                    CastTabContent(cast = movieDetail.metadata.cast.orEmpty())
                                }
                            }

                            MovieDetailTab.DETAILS -> {
                                DetailsTabContent(
                                    movieDetail = movieDetail,
                                    movieName = movieName,
                                    providerName = providerName,
                                    categoryName = categoryName,
                                    alternateStreams = alternateStreams,
                                    sourceNames = sourceNames,
                                    streamNameFocusRequester = streamNameFocusRequester,
                                    onStreamSelected = {
                                        streamSwitchSignal++
                                        onAlternateStreamSelected(it)
                                    },
                                    onStreamFocusedChanged = { streamRowFocused = it },
                                    onCategorySelected = {
                                        returnFocus.leaveFrom(RETURN_CATEGORY)
                                        onCategorySelected()
                                    },
                                    categoryButtonFocusRequester = returnFocus.requesterFor(RETURN_CATEGORY),
                                )
                            }

                            MovieDetailTab.MORE_LIKE_THIS -> {
                                Column {
                                    RelatedTitlesRow(
                                        title = relatedTitles.collectionName ?: stringResource(R.string.details_collection_fallback),
                                        items = relatedTitles.collection,
                                        onItemClick = { item ->
                                            returnFocus.leaveFrom(RETURN_RELATED_PREFIX + item.id)
                                            onRelatedTitleSelected(item)
                                        },
                                        focusTargetId = relatedReturnId,
                                        focusTargetModifier =
                                            Modifier.navReturnFocusTarget(
                                                returnFocus,
                                                RETURN_RELATED_PREFIX + relatedReturnId,
                                            ),
                                    )
                                    RelatedTitlesRow(
                                        title =
                                            if (relatedTitles.collection.isNotEmpty()) {
                                                stringResource(R.string.details_more_like_this)
                                            } else {
                                                null
                                            },
                                        items = relatedTitles.moreLikeThis,
                                        onItemClick = { item ->
                                            returnFocus.leaveFrom(RETURN_RELATED_PREFIX + item.id)
                                            onRelatedTitleSelected(item)
                                        },
                                        focusTargetId = relatedReturnId,
                                        focusTargetModifier =
                                            Modifier.navReturnFocusTarget(
                                                returnFocus,
                                                RETURN_RELATED_PREFIX + relatedReturnId,
                                            ),
                                        modifier =
                                            if (relatedTitles.collection.isNotEmpty()) {
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

// Keys for the controls that navigate away from movie details — see rememberNavReturnFocus.
private const val RETURN_START_OVER = "startOver"
private const val RETURN_CATEGORY = "category"
private const val RETURN_RELATED_PREFIX = "related:"

/** The selected tab's section in the details LazyColumn: after the hero (0) and the tab row (1). */
private const val TAB_SECTION_ITEM_INDEX = 2

@Composable
private fun LoadingScreen() {
    val scale = LocalUiScale.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CinemaSpinner(modifier = Modifier.size(TvDimensions.progressIndicator.scaled(scale)))
            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
            CinemaText(text = stringResource(R.string.movie_loading_details), role = TextRole.Title, tone = TextTone.Secondary)
        }
    }
}

/** Section tabs built from what a movie has (the tv detail hero ui plan, Phase 4); [DETAILS] is always there. */
private enum class MovieDetailTab { DETAILS, CAST, MORE_LIKE_THIS }

@Composable
private fun movieDetailTabLabel(tab: MovieDetailTab): String =
    when (tab) {
        MovieDetailTab.CAST -> stringResource(R.string.details_tab_cast)
        MovieDetailTab.DETAILS -> stringResource(R.string.details_tab_details)
        MovieDetailTab.MORE_LIKE_THIS -> stringResource(R.string.details_more_like_this)
    }

/** Cast tab without TMDB's credits (CT-D7): the source's comma string split into plain chips, not openable. */
@Composable
internal fun CastTabContent(cast: String) {
    val scale = LocalUiScale.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
    ) {
        cast.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { member ->
            CinemaBadge(
                text = member,
                role = TextRole.Body,
            )
        }
    }
}

/**
 * Details tab: full synopsis, then one label/value row each: release date, director, technical
 * stream info, the stream-name picker, TMDB id and the source last; then the category button. Cast
 * has its own tab ([CastTabContent]).
 */
@Composable
private fun DetailsTabContent(
    movieDetail: MovieDetail,
    movieName: String,
    providerName: String,
    categoryName: String?,
    alternateStreams: List<MediaItem>,
    sourceNames: Map<Long, String>,
    streamNameFocusRequester: FocusRequester,
    onStreamSelected: (MediaItem) -> Unit,
    onStreamFocusedChanged: (Boolean) -> Unit,
    onCategorySelected: () -> Unit,
    categoryButtonFocusRequester: FocusRequester? = null,
) {
    val resources = LocalResources.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        movieDetail.metadata.facts.tagline
            ?.let { CinemaText(text = it, tone = TextTone.Secondary) }
        movieDetail.metadata.plot?.takeIf { it.isNotBlank() }?.let { plot ->
            CinemaText(text = plot)
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        val displayRelease =
            movieDetail.metadata.releaseDate
                ?: extractYear(movieDetail.metadata.year, null, movieDetail.name.ifBlank { movieName })?.toString()
        displayRelease?.let { TvDetailRow(label = stringResource(R.string.details_label_released), value = displayDate(it)) }
        movieDetail.metadata.director?.let { TvDetailRow(label = stringResource(R.string.details_label_director), value = it) }
        titleFactRows(movieDetail.metadata.facts, movieDetail.name.ifBlank { movieName }, isShow = false).forEach {
            TvDetailRow(label = it.label, value = it.value)
        }

        // A child's page has no technical rows (UX-D5).
        val child = rememberChildProfile()
        movieDetail.videoInfo?.takeUnless { child }?.let { video ->
            val videoText =
                video.displayTitle ?: run {
                    val parts = mutableListOf<String>()
                    video.width?.let { w ->
                        video.height?.let { h ->
                            parts.add(resolutionLabel(w, h))
                        }
                    }
                    video.codecName?.let { codec -> parts.add(codec.uppercase()) }
                    video.videoRange?.let { range -> parts.add(range) }
                    video.width?.let { w ->
                        video.height?.let { h ->
                            parts.add("$w×$h")
                        }
                    }
                    parts.joinToString(" · ")
                }
            if (videoText.isNotBlank()) {
                TvDetailRow(label = stringResource(R.string.tech_video_label), value = videoText)
            }
        }
        if (movieDetail.audioTracks.isNotEmpty() && !child) {
            val audioTexts =
                movieDetail.audioTracks.mapNotNull { audio ->
                    val text =
                        audio.displayTitle ?: run {
                            val parts = mutableListOf<String>()
                            audio.language?.let { lang -> if (lang.isNotBlank()) parts.add(lang) }
                            audio.codecName?.let { codec -> parts.add(codec.uppercase()) }
                            audio.channels?.let { ch ->
                                parts.add(
                                    channelLabel(
                                        ch,
                                        mono = resources.getString(R.string.audio_channel_mono),
                                        stereo = resources.getString(R.string.audio_channel_stereo),
                                        surround51 = resources.getString(R.string.audio_channel_5_1),
                                        surround71 = resources.getString(R.string.audio_channel_7_1),
                                        custom = { resources.getString(R.string.audio_channel_custom, it) },
                                    ),
                                )
                            }
                            if (audio.isDefault) parts.add(stringResource(R.string.tech_default_label))
                            parts.joinToString(" · ")
                        }
                    text.ifBlank { null }
                }
            if (audioTexts.isNotEmpty()) {
                TvDetailRow(label = stringResource(R.string.tech_audio_label), value = audioTexts.joinToString("\n"))
            }
        }
        if (movieDetail.subtitleTracks.isNotEmpty() && !child) {
            val subTexts =
                movieDetail.subtitleTracks.mapNotNull { sub ->
                    val text =
                        sub.displayTitle ?: run {
                            val parts = mutableListOf<String>()
                            sub.language?.let { lang -> if (lang.isNotBlank()) parts.add(lang) }
                            sub.codecName?.let { codec -> parts.add(codec.uppercase()) }
                            if (sub.isDefault) parts.add(stringResource(R.string.tech_default_label))
                            parts.joinToString(" · ")
                        }
                    text.ifBlank { null }
                }
            if (subTexts.isNotEmpty()) {
                TvDetailRow(label = stringResource(R.string.tech_subtitle_label), value = subTexts.joinToString("\n"))
            }
        }
        movieDetail.extension?.takeUnless { child }?.let { ext ->
            TvDetailRow(label = stringResource(R.string.tech_container_label), value = ext.uppercase())
        }

        // The provider's own (often raw) stream name, as the headline is TMDB's title. A picker when the
        // catalogue holds other entries with the same TMDB title.
        if (!child) {
            TvDetailRow(label = stringResource(R.string.details_label_stream_name)) {
                StreamNamePicker(
                    // The catalogue's raw name, not movieDetail.name: some providers' detail API returns a cleaned name
                    // that does not match the raw name the alternates are listed under.
                    currentName = movieName,
                    alternates = alternateStreams,
                    sourceNames = sourceNames,
                    onSelect = onStreamSelected,
                    focusRequester = streamNameFocusRequester,
                    onFocusedChanged = onStreamFocusedChanged,
                )
            }
        }
        if (LocalNerd.current) {
            TvDetailRow(
                label = stringResource(R.string.details_label_tmdb),
                value = movieDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none),
            )
        }
        // For a Nerd only, like the source badges.
        if (LocalNerd.current) TvDetailRow(label = stringResource(R.string.details_label_source), value = providerName)

        if (categoryName != null) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            CinemaSecondaryButton(
                onClick = onCategorySelected,
                text = stringResource(R.string.details_category_format, categoryName),
                modifier = (categoryButtonFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier).leftToRail(),
            )
        }
    }
}

/**
 * The stream name, the value of the Details tab's "Stream name" row. Plain text when [alternates]
 * is empty. Otherwise a picker of the other catalogue entries with the same TMDB id, which opens
 * that entry's detail screen.
 */
@Composable
private fun StreamNamePicker(
    currentName: String,
    alternates: List<MediaItem>,
    sourceNames: Map<Long, String>,
    onSelect: (MediaItem) -> Unit,
    focusRequester: FocusRequester,
    onFocusedChanged: (Boolean) -> Unit = {},
) {
    if (alternates.isEmpty()) {
        // The same font and place as the other values of the Details tab (TvDetailRow).
        CinemaText(text = currentName)
        return
    }

    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    Box(modifier = Modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .pickerFocus(isFocused)
                    .focusRequester(focusRequester)
                    .leftToRail()
                    .onFocusChanged {
                        isFocused = it.isFocused
                        onFocusedChanged(it.isFocused)
                        // look-ok: a focusable picker that opens its list, not a list row
                    }.clickable {
                        expanded = true
                    },
        ) {
            // The value's own font and start, as the other rows; the focus outline hugs it.
            CinemaText(text = currentName)
            // The "opens a picker" mark of every row (Settings' ›).
            TvIcon(
                imageVector = CinemaIcons.KeyboardArrowRight,
                contentDescription = stringResource(R.string.details_other_instances),
                tone = TextTone.Primary,
            )
        }
        if (expanded) {
            // The copies of this title: this one marked, each other one opens there.
            TvPickerDialog(
                title = stringResource(R.string.details_other_instances),
                options = listOf(currentName to null) + alternates.map { it.name to it },
                selected = null,
                onSelect = { alternate -> alternate?.let(onSelect) },
                onDismiss = { expanded = false },
                // With several sources, each copy's own as the badge at the end of its name (it
                // opens there), as on every row.
                sourceName = { alternate -> alternate?.let { sourceNames[it.providerId] } },
                labelIsName = true,
                labelMaxLines = 2,
            )
        }
    }
}
