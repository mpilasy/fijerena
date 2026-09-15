@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.movie

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.MediaItem
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.model.channelLabel
import org.njarasoa.fijerena.core.player.model.computeEndsAt
import org.njarasoa.fijerena.core.player.model.extractYear
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.player.model.hasMeaningfulDuration
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.player.model.resolutionLabel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaBadge
import org.njarasoa.fijerena.core.ui.components.ScoreChip
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.utils.openExternalUrl
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModelFactory
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.TvDetailHero
import org.njarasoa.fijerena.ui.components.TvSectionTabs
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusableNoScale
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity

/**
 * Movie details screen for VOD content.
 *
 * Features:
 * - Displays movie information (title, plot, cast, genre, rating, duration)
 * - Large Play button
 * - D-pad friendly navigation
 * - Loads movie data from MediaRepository
 */
@Composable
fun MovieDetailsScreen(
    movieId: String,
    movieName: String,
    categoryId: String,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onCategorySelected: (categoryId: String) -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit = {},
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }
    val viewModel: MovieDetailsViewModel =
        viewModel(
            factory =
                remember(movieId, categoryId) {
                    MovieDetailsViewModelFactory(context.applicationContext, movieId, categoryId, movieName)
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val relatedTitles by viewModel.relatedTitles.collectAsStateWithLifecycle()
    val tmdbTitle by viewModel.tmdbTitle.collectAsStateWithLifecycle()
    val logoUrl by viewModel.logoUrl.collectAsStateWithLifecycle()
    val backdropUrl by viewModel.backdropUrl.collectAsStateWithLifecycle()
    val alternateStreams by viewModel.alternateStreams.collectAsStateWithLifecycle()

    // Provide UI scale for all child composables
    CompositionLocalProvider(LocalUiScale provides uiScale) {
        when (val state = uiState) {
            is MovieDetailsViewModel.UiState.Loading -> {
                LoadingScreen()
            }
            is MovieDetailsViewModel.UiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onBack = onBack,
                )
            }
            is MovieDetailsViewModel.UiState.Success -> {
                MovieDetailsContent(
                    movieDetail = state.movieDetail,
                    relatedTitles = relatedTitles,
                    tmdbTitle = tmdbTitle,
                    logoUrl = logoUrl,
                    backdropUrl = backdropUrl,
                    alternateStreams = alternateStreams,
                    movieId = state.movieDetail.id,
                    movieName = state.streamName,
                    isFavorite = state.isFavorite,
                    isWatched = state.isWatched,
                    resumePositionMs = state.resumePositionMs,
                    resumeDurationMs = state.resumeDurationMs,
                    categoryName = state.categoryName,
                    onPlayMovie = onPlayMovie,
                    onCategorySelected = { onCategorySelected(state.categoryId) },
                    onToggleFavorite = { viewModel.toggleFavorite(state.streamName) },
                    onToggleWatched = { viewModel.toggleWatched() },
                    onRefresh = { viewModel.refreshMovieInfo() },
                    onBack = onBack,
                    onRelatedTitleSelected = onRelatedTitleSelected,
                    onAlternateStreamSelected = { viewModel.switchToAlternateStream(it) },
                )
            }
        }
    }
}

@Composable
private fun MovieDetailsContent(
    movieDetail: MovieDetail,
    relatedTitles: RelatedTitles,
    tmdbTitle: String?,
    logoUrl: String?,
    backdropUrl: String?,
    alternateStreams: List<MediaItem>,
    movieId: String,
    movieName: String,
    isFavorite: Boolean,
    isWatched: Boolean,
    resumePositionMs: Long,
    resumeDurationMs: Long,
    categoryName: String?,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onCategorySelected: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleWatched: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit,
    onAlternateStreamSelected: (MediaItem) -> Unit,
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    var providerName by remember { mutableStateOf(appSettings.providerName) }
    // AppSettings.providerName is the legacy single-provider key and is never written once a
    // provider lives in Room — it stays "My Provider" (its hardcoded default) for every provider
    // added since. Same fix as TwoColumnLayout.kt's category grid: read the actual active
    // provider's name from the DB and use that instead.
    LaunchedEffect(Unit) {
        val repo =
            org.njarasoa.fijerena.core.network.provider
                .ProviderRepository(context.applicationContext)
        repo.getActiveProvider()?.let { providerName = it.name }
    }
    val extension = movieDetail.extension ?: "mp4"
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    // Only the leftover "diagnostics" block below the hero (provider name, stream picker,
    // TMDB id, cast, director) still needs its own styles — the hero draws its own text
    // directly off MaterialTheme.typography, matching TvDetailHero's contract.
    val scaledStyles =
        remember(scale, typography) {
            object {
                val titleSmall = typography.titleSmall.copy(fontSize = typography.titleSmall.fontSize.scaled(scale))
                val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
            }
        }

    // Focus requester for Play button
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
    // docs/plans/episode-selection-fragility-plan.md.
    var streamSwitchSignal by rememberSaveable { mutableStateOf(0) }

    // Phase 4 tab shell (docs/plans/tv-detail-hero-ui-plan.md): one FocusRequester, attached by
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

    // Tabbed sections (Phase 4, docs/plans/tv-detail-hero-ui-plan.md): built from what this movie
    // actually has, never a fixed list. Only the selected tab's content composes below — the old
    // single-column screen composed cast, tech rows and up to three related rows on every visit,
    // whether on screen or not.
    val hasCast = !movieDetail.metadata.cast.isNullOrBlank()
    val hasSimilar = relatedTitles.moreLikeThis.isNotEmpty()
    val hasCollection = relatedTitles.collection.isNotEmpty()
    val tabs =
        remember(hasCast, hasSimilar, hasCollection) {
            buildList {
                if (hasCast) add(MovieDetailTab.CAST)
                add(MovieDetailTab.DETAILS)
                if (hasSimilar) add(MovieDetailTab.SIMILAR)
                if (hasCollection) add(MovieDetailTab.COLLECTION)
            }
        }
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val safeTabIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex)
    val tabLabels = tabs.map { movieDetailTabLabel(it) }

    // Request focus on Play/Resume button when screen loads or resume data arrives — unless the
    // user has switched to an alternate stream at some point on this screen, in which case focus
    // stays on the stream name row so the D-pad doesn't silently land on Play.
    LaunchedEffect(resumePositionMs) {
        if (streamSwitchSignal == 0) {
            try {
                playButtonFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
            }
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
            try {
                streamNameFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
            }
            withFrameNanos { }
            attempts++
        }
    }

    val refreshScope = rememberCoroutineScope()

    // Track refresh state for animation
    var isRefreshing by remember { mutableStateOf(false) }
    var targetRotation by remember { mutableStateOf(0f) }

    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = CinemaAnimation.fadeInDurationMs, easing = LinearEasing),
        label = "refresh_rotation",
    )

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            while (isRefreshing) {
                targetRotation = (targetRotation + 360f) % 3600f
                kotlinx.coroutines.delay(CinemaAnimation.loadingDebounceMs)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Lazy, not Column(verticalScroll): a scrolling Column measures every child, so the Similar
    // Titles row paid its full layout cost while sitting entirely off-screen — 85ms of a 215ms
    // measure pass on every rebuild of this screen, which is why backing out of the player was
    // slow. EpisodeSelectionScreen already builds these same rows as LazyColumn items.
    val movieListState = rememberLazyListState()
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
        modifier = Modifier.fillMaxSize().focusable().onPreviewKeyEvent { event ->
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
            val titleText = tmdbTitle ?: movieDetail.name.ifEmpty { movieName }
            val year = extractYear(movieDetail.metadata.year, movieDetail.metadata.releaseDate, movieDetail.name.ifBlank { movieName })
            val endsAtContext = LocalContext.current
            val endsAtText =
                remember(movieDetail.metadata.duration, resumePositionMs) {
                    computeEndsAt(endsAtContext, movieDetail.metadata.duration, resumePositionMs)
                }
            val metaLine =
                listOfNotNull(
                    year?.toString(),
                    movieDetail.metadata.contentRating,
                    movieDetail.metadata.duration?.takeIf(::hasMeaningfulDuration)?.let { formatDuration(it) },
                    endsAtText?.let { stringResource(R.string.movie_ends_at_format, it) },
                    movieDetail.metadata.genre,
                )
            val communityRatingLabel = stringResource(R.string.details_community_rating)
            val hasResume = resumePositionMs > 0L

            TvDetailHero(
                title = titleText,
                backdropUrl = backdropUrl,
                logoUrl = logoUrl,
                titleFallback = {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.displayLarge,
                        color = CinemaTextPrimary,
                    )
                },
                metaLine = metaLine,
                scoreChips =
                    movieDetail.metadata.rating?.let { rating ->
                        { ScoreChip(value = formatRating(rating), label = communityRatingLabel) }
                    },
                plot = movieDetail.metadata.plot,
            ) {
                // Phase 4: D-pad Down from any action button lands on the tab row below, not
                // wherever default geometry search prefers. focusProperties { down = ... } was
                // tried for the equivalent transition on EpisodeSelectionScreen's season tabs and
                // never took there (see its comment on the category button) — intercepting the
                // key and requesting focus directly is the proven fix, reused here.
                val downToTabRow =
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            tabRowFocusRequester.requestFocus()
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
                        modifier = Modifier.focusRequester(playButtonFocusRequester).then(downToTabRow).then(upScrollToTop),
                    )
                    CinemaIconButton(
                        onClick = { onPlayMovie(movieId, movieDetail.name.ifEmpty { movieName }, extension, true) },
                        modifier = downToTabRow.then(upScrollToTop),
                        icon = {
                            Icon(
                                imageVector = CinemaIcons.Replay,
                                contentDescription = stringResource(R.string.movie_start_beginning),
                                tint = CinemaTextPrimary,
                                modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                            )
                        },
                    )
                } else {
                    CinemaPrimaryButton(
                        onClick = { onPlayMovie(movieId, movieDetail.name.ifEmpty { movieName }, extension, false) },
                        text = stringResource(R.string.movie_play_action),
                        modifier = Modifier.focusRequester(playButtonFocusRequester).then(downToTabRow).then(upScrollToTop),
                    )
                }
                CinemaIconButton(
                    onClick = onToggleFavorite,
                    modifier = downToTabRow.then(upScrollToTop),
                    icon = {
                        Icon(
                            imageVector = if (isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                            contentDescription = if (isFavorite) stringResource(R.string.favorite_remove) else stringResource(R.string.favorite_add),
                            tint = if (isFavorite) CinemaAccent else CinemaTextPrimary,
                            modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                        )
                    },
                )
                // Watched button (Phase 6, docs/plans/watch-state-durable-storage-plan.md)
                CinemaIconButton(
                    onClick = onToggleWatched,
                    modifier = downToTabRow.then(upScrollToTop),
                    icon = {
                        Icon(
                            imageVector = if (isWatched) CinemaIcons.CheckCircle else CinemaIcons.RadioButtonUnchecked,
                            contentDescription = if (isWatched) stringResource(R.string.watched_unmark) else stringResource(R.string.watched_mark),
                            tint = if (isWatched) CinemaAccent else CinemaTextPrimary,
                            modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                        )
                    },
                )
                CinemaIconButton(
                    onClick = {
                        refreshScope.launch {
                            isRefreshing = true
                            onRefresh()
                            kotlinx.coroutines.delay(CinemaAnimation.loadingDebounceMs)
                            isRefreshing = false
                        }
                    },
                    enabled = !isRefreshing,
                    modifier = downToTabRow.then(upScrollToTop),
                    icon = {
                        Icon(
                            imageVector = CinemaIcons.Refresh,
                            contentDescription = stringResource(R.string.movie_refresh_info),
                            modifier =
                                Modifier
                                    .size(TvDimensions.iconSmall.scaled(scale))
                                    .rotate(rotation),
                        )
                    },
                )
                movieDetail.metadata.trailerUrl?.let { trailer ->
                    val trailerContext = LocalContext.current
                    CinemaIconButton(
                        onClick = { openExternalUrl(trailerContext, trailer) },
                        modifier = downToTabRow.then(upScrollToTop),
                        icon = {
                            Icon(
                                imageVector = CinemaIcons.Movie,
                                contentDescription = stringResource(R.string.details_watch_trailer_description),
                                tint = CinemaTextPrimary,
                                modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                            )
                        },
                    )
                }
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
                        .padding(top = Spacing.xl.scaled(scale)),
                entryFocusRequester = tabRowFocusRequester,
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
                    MovieDetailTab.CAST ->
                        CastTabContent(cast = movieDetail.metadata.cast.orEmpty())
                    MovieDetailTab.DETAILS ->
                        DetailsTabContent(
                            movieDetail = movieDetail,
                            movieName = movieName,
                            providerName = providerName,
                            categoryName = categoryName,
                            alternateStreams = alternateStreams,
                            streamNameFocusRequester = streamNameFocusRequester,
                            onStreamSelected = {
                                streamSwitchSignal++
                                onAlternateStreamSelected(it)
                            },
                            onStreamFocusedChanged = { streamRowFocused = it },
                            onCategorySelected = onCategorySelected,
                            titleSmallStyle = scaledStyles.titleSmall,
                            bodySmallStyle = scaledStyles.bodySmall,
                        )
                    MovieDetailTab.SIMILAR ->
                        RelatedTitlesRow(
                            title = stringResource(R.string.details_more_like_this),
                            items = relatedTitles.moreLikeThis,
                            onItemClick = onRelatedTitleSelected,
                        )
                    MovieDetailTab.COLLECTION ->
                        RelatedTitlesRow(
                            title = relatedTitles.collectionName ?: stringResource(R.string.details_collection_fallback),
                            items = relatedTitles.collection,
                            onItemClick = onRelatedTitleSelected,
                        )
                    null -> Unit
                }
            }
        }
    }
    }
}

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
            CircularProgressIndicator(
                modifier = Modifier.size(TvDimensions.progressIndicator.scaled(scale)),
                color = CinemaAccent,
            )
            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
            Text(
                text = stringResource(R.string.movie_loading_details),
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize =
                            MaterialTheme.typography.titleLarge.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary,
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onBack: () -> Unit,
) {
    val scale = LocalUiScale.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.xl.scaled(scale)),
        ) {
            Text(
                text = stringResource(R.string.movie_error_loading),
                style =
                    MaterialTheme.typography.displayMedium.copy(
                        fontSize =
                            MaterialTheme.typography.displayMedium.fontSize
                                .scaled(scale),
                    ),
                color = CinemaError,
            )
            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
            Text(
                text = message,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontSize =
                            MaterialTheme.typography.bodyLarge.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary,
            )
            Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
            CinemaSecondaryButton(
                onClick = onBack,
                text = stringResource(R.string.movie_back_to_movies),
            )
        }
    }
}

/** Section tabs built from what a movie actually has (docs/plans/tv-detail-hero-ui-plan.md
 * Phase 4) — [DETAILS] is the only one always present. */
private enum class MovieDetailTab { CAST, DETAILS, SIMILAR, COLLECTION }

@Composable
private fun movieDetailTabLabel(tab: MovieDetailTab): String =
    when (tab) {
        MovieDetailTab.CAST -> stringResource(R.string.details_tab_cast)
        MovieDetailTab.DETAILS -> stringResource(R.string.details_tab_details)
        MovieDetailTab.SIMILAR -> stringResource(R.string.details_tab_similar)
        MovieDetailTab.COLLECTION -> stringResource(R.string.details_collection_fallback)
    }

/** Cast tab: one comma-string split into plain chips — no data behind a real cast/crew model yet. */
@Composable
private fun CastTabContent(cast: String) {
    val scale = LocalUiScale.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
    ) {
        cast.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { member ->
            CinemaBadge(text = member, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Details tab: everything that was diagnostics rather than headline facts on the old header —
 * provider name, release date, director, technical stream info, then the stream-name picker (with
 * its hard-won stream-switch focus dance, moved here unchanged) and TMDB id right below the
 * container row, and finally the category button. Cast lives in its own tab now (see
 * [CastTabContent]), not repeated here.
 */
@Composable
private fun DetailsTabContent(
    movieDetail: MovieDetail,
    movieName: String,
    providerName: String,
    categoryName: String?,
    alternateStreams: List<MediaItem>,
    streamNameFocusRequester: FocusRequester,
    onStreamSelected: (MediaItem) -> Unit,
    onStreamFocusedChanged: (Boolean) -> Unit,
    onCategorySelected: () -> Unit,
    titleSmallStyle: TextStyle,
    bodySmallStyle: TextStyle,
) {
    val scale = LocalUiScale.current
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = providerName,
            style = titleSmallStyle,
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )
        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // Release date / year
        val displayRelease = movieDetail.metadata.releaseDate
            ?: extractYear(movieDetail.metadata.year, null, movieDetail.name.ifBlank { movieName })?.toString()
        displayRelease?.let { releaseInfo ->
            Text(
                text = stringResource(R.string.movie_released_format, releaseInfo),
                style = bodySmallStyle,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
            Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
        }

        movieDetail.metadata.director?.let { director ->
            Text(
                text = stringResource(R.string.movie_director_format, director),
                style = bodySmallStyle,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
            Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
        }
        // Technical stream info (Jellyfin-style labeled rows)
        val hasVideoInfo =
            movieDetail.videoInfo != null &&
                (movieDetail.videoInfo!!.width != null || movieDetail.videoInfo!!.codecName != null)
        if (hasVideoInfo ||
            movieDetail.audioTracks.isNotEmpty() ||
            movieDetail.subtitleTracks.isNotEmpty() ||
            movieDetail.extension != null
        ) {
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
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
                    TechInfoRow(label = stringResource(R.string.tech_video_label), value = videoText)
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
                                            mono = context.getString(R.string.audio_channel_mono),
                                            stereo = context.getString(R.string.audio_channel_stereo),
                                            surround51 = context.getString(R.string.audio_channel_5_1),
                                            surround71 = context.getString(R.string.audio_channel_7_1),
                                            custom = { context.getString(R.string.audio_channel_custom, it) },
                                        ),
                                    )
                                }
                                if (audio.isDefault) parts.add(stringResource(R.string.tech_default_label))
                                parts.joinToString(" · ")
                            }
                        text.ifBlank { null }
                    }
                if (audioTexts.isNotEmpty()) {
                    TechInfoRow(label = stringResource(R.string.tech_audio_label), value = audioTexts.joinToString("\n"))
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
                    TechInfoRow(label = stringResource(R.string.tech_subtitle_label), value = subTexts.joinToString("\n"))
                }
            }
            movieDetail.extension?.let { ext ->
                TechInfoRow(label = stringResource(R.string.tech_container_label), value = ext.uppercase())
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

        // The provider's own (often raw) stream name, now that the headline is TMDB's title. A
        // dropdown when the local catalogue holds other instances of the same TMDB title.
        StreamNamePicker(
            // The catalogue's raw name, not movieDetail.name — some providers' detail API
            // returns a cleaned-up name inconsistent with the raw name alternates are listed
            // under, so use the same source as alternates.
            currentName = movieName,
            alternates = alternateStreams,
            onSelect = onStreamSelected,
            textStyle = bodySmallStyle,
            focusRequester = streamNameFocusRequester,
            onFocusedChanged = onStreamFocusedChanged,
        )
        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
        Text(
            text = stringResource(R.string.details_tmdb_format, movieDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none)),
            style = bodySmallStyle,
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )

        // Category this movie belongs to — OK opens its stream list
        if (categoryName != null) {
            Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
            CinemaSecondaryButton(
                onClick = onCategorySelected,
                text = stringResource(R.string.details_category_format, categoryName),
            )
        }
    }
}

/**
 * The "Stream name: X" row. Plain text when [alternates] is empty — most titles have no other
 * cached instance. Becomes a tappable dropdown once there is at least one other local catalogue
 * entry sharing the same TMDB id, letting the user switch to that instance's detail screen.
 */
@Composable
private fun StreamNamePicker(
    currentName: String,
    alternates: List<MediaItem>,
    onSelect: (MediaItem) -> Unit,
    textStyle: TextStyle,
    focusRequester: FocusRequester,
    onFocusedChanged: (Boolean) -> Unit = {},
) {
    val textColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)

    if (alternates.isEmpty()) {
        Text(
            text = stringResource(R.string.details_stream_name_format, currentName),
            style = textStyle,
            color = textColor,
        )
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
                        color = if (isFocused) CinemaAccent.copy(alpha = CinemaAlpha.tint) else Color.Transparent,
                        shape = RoundedCornerShape(CornerRadius.medium),
                    )
                    .then(
                        if (isFocused) {
                            Modifier.border(
                                width = TvFocusTokens.focusBorderWidth,
                                color = CinemaAccentLight,
                                shape = RoundedCornerShape(CornerRadius.medium),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused
                        onFocusedChanged(it.isFocused)
                    }
                    .clickable {
                        coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                        expanded = true
                    }
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        ) {
            Text(
                text = stringResource(R.string.details_stream_name_format, currentName),
                style = textStyle,
                color = textColor,
            )
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
                        text = { Text(currentName, color = CinemaAccent) },
                        leadingIcon = { Icon(CinemaIcons.CheckCircle, contentDescription = null, tint = CinemaAccent) },
                        onClick = { expanded = false },
                    )
                    alternates.forEach { alternate ->
                        DropdownMenuItem(
                            text = { Text(alternate.name, color = CinemaTextPrimary) },
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

@Composable
private fun TechInfoRow(
    label: String,
    value: String,
) {
    val scale = LocalUiScale.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize =
                        MaterialTheme.typography.bodyMedium.fontSize
                            .scaled(scale),
                ),
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )
        Text(
            text = value,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize =
                        MaterialTheme.typography.bodyMedium.fontSize
                            .scaled(scale),
                ),
            color = CinemaTextPrimary,
        )
    }
}


