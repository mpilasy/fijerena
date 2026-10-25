@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.movie

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AppSettings
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
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.history.LocalShowSourceBadges
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity
import org.njarasoa.fijerena.core.ui.trailer.rememberPlayTrailer
import org.njarasoa.fijerena.core.ui.utils.displayDate
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModelFactory
import org.njarasoa.fijerena.core.ui.wanted.WishlistControl
import org.njarasoa.fijerena.core.ui.wanted.rememberWishlist
import org.njarasoa.fijerena.ui.components.DetailsMoreMenu
import org.njarasoa.fijerena.ui.components.KeepHeroInView
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.TvDetailHero
import org.njarasoa.fijerena.ui.components.TvDetailRow
import org.njarasoa.fijerena.ui.components.TvErrorState
import org.njarasoa.fijerena.ui.components.TvOtherCopiesList
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
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusableNoScale
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.components.ratingOutOfTen
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
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
    // A child profile's (docs/plans/20261019_kids-home-plan.md, Phase 2): no way on to other titles
    // or the category, and no favourite to undo — it reached this film from its Home.
    val child = remember { appSettings.isChildProfile(appSettings.activeProfileId) }
    val relatedTitles by viewModel.relatedTitles.collectAsStateWithLifecycle()
    val tmdbTitle by viewModel.tmdbTitle.collectAsStateWithLifecycle()
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
                relatedTitles = if (child) RelatedTitles() else relatedTitles,
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
            )
        }
    }
}

@Composable
private fun MovieDetailsContent(
    wishlist: WishlistControl?,
    movieDetail: MovieDetail,
    relatedTitles: RelatedTitles,
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
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    var providerName by remember { mutableStateOf(appSettings.providerName) }
    // AppSettings.providerName is the legacy single-provider key and is never written once a
    // provider lives in Room — it stays "My Provider" (its hardcoded default) for every provider
    // added since. Same fix as TwoColumnLayout.kt's category grid: read the route's source's
    // name from the DB and use that instead.
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
    // Bumped in onSelect, independent of resumePositionMs: selecting a dropdown item destroys
    // that focused node, and Compose has nothing left to restore to, so focus falls to the
    // window root and D-pad input goes nowhere until this claims it back for the row. Also
    // doubles as "a switch has happened this screen instance" — see the resumePositionMs effect
    // below, which reads it as a sticky flag, not a one-shot: a single-shot flag consumed by the
    // first post-switch resumePositionMs change would leave a later one (e.g. a slow network
    // fetch resolving after a fast cache draw) free to steal focus back to Play. See the same
    // fix on EpisodeSelectionScreen's stream picker for the case that actually double-fires.
    // rememberSaveable, not remember: this composable is disposed when Play navigates to the
    // player and recomposed fresh on return, same as EpisodeSelectionScreen's identical signal —
    // a plain remember forgot the switch across that trip and stole focus back to Play. See
    // the episode selection fragility plan.
    var streamSwitchSignal by rememberSaveable { mutableStateOf(0) }

    // Phase 4 tab shell (the tv detail hero ui plan): one FocusRequester, attached by
    // TvSectionTabs to whichever tab is currently selected, serves both directions — D-pad Down
    // from the action row into the tab row, and Back from inside the open section back to the
    // tab row instead of out of the screen.
    val tabRowFocusRequester = remember { FocusRequester() }
    // True while focus is anywhere inside the selected tab's section content — read by the
    // LazyColumn's onPreviewKeyEvent below to decide what Back does.
    var focusInSection by remember { mutableStateOf(false) }

    // Fallback for any state where the LazyColumn's onPreviewKeyEvent below isn't in the tree
    // yet (e.g. very first composition) — the real fix, and the one actually exercised in
    // practice, is that onPreviewKeyEvent. Must apply the same focusInSection branch as that
    // handler: confirmed on the TV emulator that a Back press can reach *this* fallback instead
    // of onPreviewKeyEvent even when the latter is very much in the tree (not just the "very
    // first composition" case above) — an unconditional onBack() here exited the screen straight
    // past an open tab section, silently overriding the onPreviewKeyEvent branch below.
    BackHandler {
        if (focusInSection) {
            tabRowFocusRequester.requestFocus()
        } else {
            onBack()
        }
    }

    // Tabbed sections (Phase 4, the tv detail hero ui plan): built from what this movie
    // actually has, never a fixed list. Only the selected tab's content composes below — the old
    // single-column screen composed cast, tech rows and up to three related rows on every visit,
    // whether on screen or not.
    val hasCast = !movieDetail.metadata.cast.isNullOrBlank()
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
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val safeTabIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex)
    val tabLabels = tabs.map { movieDetailTabLabel(it) }

    // Back from what Start Over, the Category button or a related title opened lands on that
    // control. Play/Resume needs none of this: the effect below lands there anyway. The category
    // button and related cards live in the tab section (item 2), so it is scrolled in first.
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

    // Request focus on Play/Resume button when screen loads or resume data arrives — unless the
    // user has switched to an alternate stream at some point on this screen, in which case focus
    // stays on the stream name row so the D-pad doesn't silently land on Play — or Back is about
    // to hand focus to another control (above).
    LaunchedEffect(resumePositionMs) {
        if (streamSwitchSignal == 0 && returnFocus.key == null) {
            playButtonFocusRequester.requestFocusWithRetry()
        }
    }

    LaunchedEffect(streamSwitchSignal) {
        if (streamSwitchSignal == 0) return@LaunchedEffect
        // The dropdown's own dismissal falls back to focusing the window root, asynchronously,
        // on its own timeline that can outlast a fixed number of frames on real hardware (a
        // guessed frame count is what left focus dead on a real Shield after this looked fixed
        // in testing) — poll instead, and stop the instant the row actually reports focused.
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
        DetailsMoreMenu(
            title = tmdbTitle ?: cleanTitle(movieDetail.name.ifEmpty { movieName }),
            refreshLabel = stringResource(R.string.movie_refresh_info),
            onRefresh = onRefresh,
            onDismiss = { showMoreMenu = false },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(CinemaTheme.colors.background)) {
        // Lazy, not Column(verticalScroll): a scrolling Column measures every child, so the Similar
        // Titles row paid its full layout cost while sitting entirely off-screen — 85ms of a 215ms
        // measure pass on every rebuild of this screen, which is why backing out of the player was
        // slow. EpisodeSelectionScreen already builds these same rows as LazyColumn items.
        KeepHeroInView(scrollToTop = { movieListState.animateScrollToItem(0) }) { heroModifier ->
            LazyColumn(
                state = movieListState,
                // Confirmed on a real Shield (logcat): the first Back press while a focused TV Button
                // has focus reaches Compose's key dispatch fine (a non-consuming onPreviewKeyEvent here
                // logs it), but something between here and the BackHandler/OnBackPressedDispatcher
                // bridge marks it handled — BackHandler never fires on that first press, only the
                // second. Rather than chase the exact consumer, intercept here instead: onPreviewKeyEvent
                // runs top-down, before any descendant (including the focused Button) gets a look, so
                // this always wins the race. Matches the same pattern TvDpadEscape.kt uses for the same
                // class of problem.
                modifier =
                    Modifier.fillMaxSize().focusable().onPreviewKeyEvent { event ->
                        if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                            // Phase 4: Back out of an open tab section goes to the tab row, not out of the
                            // screen — same interception point as the screen-exit case below, since this
                            // handler already runs before any descendant (the tab row's own handling would
                            // never get a look otherwise).
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
                // No horizontal margin here: the hero backdrop below must run edge to edge. Every other
                // item applies Spacing.tvSafeMarginHorizontal to itself instead (see "details" below).
                contentPadding = PaddingValues(bottom = Spacing.tvSafeMarginVertical.scaled(scale)),
            ) {
                item(key = "hero") {
                    // TMDB's branded logo art when it has one, else TMDB's original title falling back
                    // to the provider's own stream name (when TMDB has no match, or the lookup hasn't
                    // come back yet).
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
                        // Phase 4: D-pad Down from any action button lands on the tab row below, not
                        // wherever default geometry search prefers. focusProperties { down = ... } was
                        // tried for the equivalent transition on EpisodeSelectionScreen's season tabs and
                        // never took there (see its comment on the category button) — intercepting the
                        // key and requesting focus directly is the proven fix, reused here.
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
                        // D-pad Up from the hero action row — same fix, same reason as
                        // EpisodeSelectionScreen's identical addition: these buttons are the topmost
                        // focusable in the screen, so default focus search has nowhere to go and the
                        // LazyColumn never scrolls back up once a tall plot has pushed the title above
                        // the viewport. Force it back to the top explicitly instead.
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
                                modifier =
                                    Modifier
                                        .focusRequester(
                                            playButtonFocusRequester,
                                        ).then(downToTabRow)
                                        .then(upScrollToTop)
                                        .leftToRail(),
                            )
                            CinemaIconButton(
                                onClick = playFromStart,
                                modifier =
                                    downToTabRow
                                        .then(upScrollToTop)
                                        .navReturnFocusTarget(returnFocus, RETURN_START_OVER),
                                icon = {
                                    Icon(
                                        imageVector = CinemaIcons.Replay,
                                        contentDescription = stringResource(R.string.movie_start_beginning),
                                        modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                                    )
                                },
                            )
                        } else {
                            CinemaPrimaryButton(
                                onClick = { onPlayMovie(movieId, movieDetail.name.ifEmpty { movieName }, extension, false) },
                                text = stringResource(R.string.movie_play_action),
                                modifier =
                                    Modifier
                                        .focusRequester(
                                            playButtonFocusRequester,
                                        ).then(downToTabRow)
                                        .then(upScrollToTop)
                                        .leftToRail(),
                            )
                        }
                        // Labelled, not bare icons (UX overhaul Part II Phase 6, F-MD-2): the icon carries
                        // the state (filled star, check), the word says what the button is.
                        if (onToggleFavorite != null) {
                            TvIconAction(
                                onClick = onToggleFavorite,
                                icon = if (isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                                iconTint = if (isFavorite) CinemaTheme.colors.accent else CinemaTheme.colors.textPrimary,
                                label = stringResource(R.string.details_action_favorite),
                                modifier = downToTabRow.then(upScrollToTop),
                            )
                        }
                        // Watched button (Phase 6, the watch state durable storage plan);
                        // none where the source keeps watched state on its server (Jellyfin).
                        if (onToggleWatched != null) {
                            TvIconAction(
                                onClick = onToggleWatched,
                                icon = if (isWatched) CinemaIcons.CheckCircleFilled else CinemaIcons.CheckCircleOutline,
                                iconTint = if (isWatched) CinemaTheme.colors.accent else CinemaTheme.colors.textPrimary,
                                label = stringResource(R.string.details_action_watched),
                                modifier = downToTabRow.then(upScrollToTop),
                            )
                        }
                        if (wishlist != null) {
                            TvIconAction(
                                onClick = wishlist.toggle,
                                icon = if (wishlist.isOn) CinemaIcons.Bookmark else CinemaIcons.BookmarkBorder,
                                iconTint = if (wishlist.isOn) CinemaTheme.colors.accent else CinemaTheme.colors.textPrimary,
                                label = stringResource(if (wishlist.isOn) R.string.wanted_remove else R.string.wanted_add),
                                modifier = downToTabRow.then(upScrollToTop),
                            )
                        }
                        movieDetail.metadata.trailerUrl?.let { trailer ->
                            val playTrailer = rememberPlayTrailer()
                            CinemaIconButton(
                                onClick = { playTrailer(trailer, movieDetail.metadata.tmdbTrailerUrl) },
                                modifier = downToTabRow.then(upScrollToTop),
                                icon = {
                                    Icon(
                                        imageVector = CinemaIcons.Movie,
                                        contentDescription = stringResource(R.string.details_watch_trailer_description),
                                        modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                                    )
                                },
                            )
                        }
                        // Refresh info is maintenance, not something to watch: it lives behind "More" at
                        // the end of the row, out of the path between Play and the tabs (F-MD-2, R5).
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
                            selectedTabIndex = it
                            // A tab regaining focus — whether from Left/Right, the initial Down from the
                            // action row, or our own Back-triggered tabRowFocusRequester.requestFocus()
                            // below — means focus is on the tab row, not in a section.
                            focusInSection = false
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                                .padding(top = Spacing.xl.scaled(scale))
                                // Up from any tab → Play (F-MD-3), not whichever action button sits
                                // above the tab by geometry (it was Mark as watched). A key intercept,
                                // like downToTabRow above: focusProperties on this modifier stops at
                                // TvSectionTabs' own focusGroup and never reaches the tabs. The hero is
                                // scrolled back to its top first, so Play lands with the title in view.
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

                // Keyed by the selected tab: switching tabs is a fresh item, so a tab whose content
                // scrolls (a related-titles row) resets that scroll instead of keeping the last tab's
                // position — "switching resets the section's own scroll" from the plan's focus rules.
                item(key = "tab-section-${tabs.getOrNull(safeTabIndex)}") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                                .padding(top = Spacing.md.scaled(scale))
                                .focusRestorer()
                                // Only the true transition is trusted: the physical Back key itself was
                                // found (on the TV emulator, not just real hardware — see the codebase's
                                // other "unconfirmed root cause" back-key notes) to fire a spurious false
                                // here moments before the key event reaches onPreviewKeyEvent below, which
                                // would otherwise read focusInSection as already false and exit the screen
                                // instead of returning to the tab row. The false transition is instead
                                // driven explicitly by onTabSelected (a tab regaining focus, including via
                                // this same Back path) — see TvSectionTabs' entryFocusRequester usage below.
                                .onFocusChanged { if (it.hasFocus) focusInSection = true },
                    ) {
                        when (tabs.getOrNull(safeTabIndex)) {
                            MovieDetailTab.CAST -> {
                                CastTabContent(cast = movieDetail.metadata.cast.orEmpty())
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

/** Section tabs built from what a movie actually has (the tv detail hero ui plan
 * Phase 4) — [DETAILS] is the only one always present. */
private enum class MovieDetailTab { DETAILS, CAST, MORE_LIKE_THIS }

@Composable
private fun movieDetailTabLabel(tab: MovieDetailTab): String =
    when (tab) {
        MovieDetailTab.CAST -> stringResource(R.string.details_tab_cast)
        MovieDetailTab.DETAILS -> stringResource(R.string.details_tab_details)
        MovieDetailTab.MORE_LIKE_THIS -> stringResource(R.string.details_more_like_this)
    }

/** Cast tab: one comma-string split into plain chips — no data behind a real cast/crew model yet. */
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
 * Details tab: full synopsis, then diagnostics rather than headline facts, one label/value row each — release date,
 * director, technical stream info, the stream-name picker, TMDB id and the source last — then the
 * category button. Cast lives in its own tab ([CastTabContent]), not repeated here.
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
        movieDetail.metadata.plot?.takeIf { it.isNotBlank() }?.let { plot ->
            CinemaText(text = plot)
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        val displayRelease =
            movieDetail.metadata.releaseDate
                ?: extractYear(movieDetail.metadata.year, null, movieDetail.name.ifBlank { movieName })?.toString()
        displayRelease?.let { TvDetailRow(label = stringResource(R.string.details_label_released), value = displayDate(it)) }
        movieDetail.metadata.director?.let { TvDetailRow(label = stringResource(R.string.details_label_director), value = it) }

        movieDetail.videoInfo?.let { video ->
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
        if (movieDetail.audioTracks.isNotEmpty()) {
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
        if (movieDetail.subtitleTracks.isNotEmpty()) {
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
        movieDetail.extension?.let { ext ->
            TvDetailRow(label = stringResource(R.string.tech_container_label), value = ext.uppercase())
        }

        // The provider's own (often raw) stream name, now that the headline is TMDB's title. A
        // dropdown when the local catalogue holds other instances of the same TMDB title.
        TvDetailRow(label = stringResource(R.string.details_label_stream_name)) {
            StreamNamePicker(
                // The catalogue's raw name, not movieDetail.name — some providers' detail API
                // returns a cleaned-up name inconsistent with the raw name alternates are listed
                // under, so use the same source as alternates.
                currentName = movieName,
                alternates = alternateStreams,
                sourceNames = sourceNames,
                onSelect = onStreamSelected,
                focusRequester = streamNameFocusRequester,
                onFocusedChanged = onStreamFocusedChanged,
            )
        }
        val context = LocalContext.current
        val isDevMode = remember { AppSettings(context.applicationContext).isDevMode }
        if (isDevMode) {
            TvDetailRow(
                label = stringResource(R.string.details_label_tmdb),
                value = movieDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none),
            )
        }
        // Expert mode only, like the source badges.
        if (LocalShowSourceBadges.current) TvDetailRow(label = stringResource(R.string.details_label_source), value = providerName)

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
 * is empty — most titles have no other cached instance. Becomes a tappable dropdown once there is
 * at least one other local catalogue entry sharing the same TMDB id, letting the user switch to
 * that instance's detail screen.
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
    val textColor = CinemaTheme.colors.textPrimary

    if (alternates.isEmpty()) {
        CinemaText(text = currentName, role = TextRole.Heading)
        return
    }

    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    // The row often sits near the bottom of the visible screen; a Popup can't render below the
    // screen edge, so without this the menu flips far above the row to find room instead of
    // opening flush beneath it.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    Box(modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .background(
                        color = if (isFocused) CinemaTheme.colors.accent.copy(alpha = CinemaAlpha.tint) else CinemaTheme.colors.clear,
                        shape = CinemaTheme.shapes.card,
                    ).then(
                        if (isFocused) {
                            Modifier.border(
                                width = TvFocusTokens.focusBorderWidth,
                                color = CinemaTheme.colors.accentSoft,
                                shape = CinemaTheme.shapes.card,
                            )
                        } else {
                            Modifier
                        },
                    ).focusRequester(focusRequester)
                    .leftToRail()
                    .onFocusChanged {
                        isFocused = it.isFocused
                        onFocusedChanged(it.isFocused)
                    }.clickable {
                        coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                        expanded = true
                    }.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        ) {
            CinemaText(text = currentName, role = TextRole.Heading)
            Icon(
                imageVector = CinemaIcons.ArrowDropDown,
                contentDescription = stringResource(R.string.details_other_instances),
                tint = textColor,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // The menu is a Popup, so it gets its own window and loses the scaled density.
            ProvideUiScaledDensity {
                Column {
                    DropdownMenuItem(
                        text = { CinemaText(currentName, role = TextRole.Heading, tone = TextTone.Accent) },
                        leadingIcon = { Icon(CinemaIcons.CheckCircle, contentDescription = null, tint = CinemaTheme.colors.accent) },
                        onClick = { expanded = false },
                    )
                    alternates.forEach { alternate ->
                        DropdownMenuItem(
                            text = { CinemaText(alternate.name, role = TextRole.Heading) },
                            // With several sources, each copy's own (it opens there).
                            leadingIcon = sourceNames[alternate.providerId]?.let { name -> { SourceBadge(name) } },
                            onClick = {
                                expanded = false
                                onSelect(alternate)
                            },
                        )
                    }
                }
            }
        }
    }
}
