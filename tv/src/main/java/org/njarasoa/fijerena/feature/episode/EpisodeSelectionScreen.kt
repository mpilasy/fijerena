@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.episode

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.resumeProgress
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.domain.SeasonInfo
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.domain.episodeIdMatchingName
import org.njarasoa.fijerena.core.player.domain.episodeOwnTitle
import org.njarasoa.fijerena.core.player.domain.firstSeasonWithUnwatchedEpisode
import org.njarasoa.fijerena.core.player.domain.flattenedEpisodes
import org.njarasoa.fijerena.core.player.domain.resumeAnchorEpisodeId
import org.njarasoa.fijerena.core.player.domain.seasonNumberContaining
import org.njarasoa.fijerena.core.player.domain.seriesYearRange
import org.njarasoa.fijerena.core.player.domain.sortedSeasons
import org.njarasoa.fijerena.core.player.model.computeEndsAt
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.player.model.hasMeaningfulDuration
import org.njarasoa.fijerena.core.player.model.parseDurationToSeconds
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaBadge
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.RatingBadge
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.WatchedBadge
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity
import org.njarasoa.fijerena.core.ui.utils.openExternalUrl
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModelFactory
import org.njarasoa.fijerena.feature.category.components.tvLongPress
import org.njarasoa.fijerena.ui.components.DetailsMoreMenu
import org.njarasoa.fijerena.ui.components.KeepHeroInView
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.TvDetailHero
import org.njarasoa.fijerena.ui.components.TvDetailRow
import org.njarasoa.fijerena.ui.components.TvErrorState
import org.njarasoa.fijerena.ui.components.TvSectionTabs
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.input.CurrentIndicatorEdge
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.currentIndicator
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.components.ratingOutOfTen
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import kotlin.math.roundToInt
import org.njarasoa.fijerena.core.player.domain.EpisodeItem as DomainEpisodeItem

/**
 * Episode selection screen for TV shows.
 */
@Composable
fun EpisodeSelectionScreen(
    seriesId: String,
    seriesName: String,
    categoryId: String,
    initialEpisodeId: String? = null,
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onCategorySelected: (categoryId: String) -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit = {},
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val viewModel: SeriesDetailsViewModel =
        viewModel(
            factory =
                remember(seriesId, categoryId) {
                    SeriesDetailsViewModelFactory(context.applicationContext, seriesId, categoryId, seriesName)
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val relatedTitles by viewModel.relatedTitles.collectAsStateWithLifecycle()
    val tmdbTitle by viewModel.tmdbTitle.collectAsStateWithLifecycle()
    val logoUrl by viewModel.logoUrl.collectAsStateWithLifecycle()
    val backdropUrl by viewModel.backdropUrl.collectAsStateWithLifecycle()
    val alternateStreams by viewModel.alternateStreams.collectAsStateWithLifecycle()

    // Retained across a background refresh so the episode list stays on screen with just a
    // spinning refresh icon, instead of the whole thing dropping to a full-screen loading state
    // and losing scroll position and the expanded season. Mirrors the mobile screen, which
    // already did this.
    var lastSuccess by remember { mutableStateOf<SeriesDetailsViewModel.UiState.Success?>(null) }
    (uiState as? SeriesDetailsViewModel.UiState.Success)?.let { lastSuccess = it }
    val isRefreshing = uiState is SeriesDetailsViewModel.UiState.Loading && lastSuccess != null

    val state = uiState
    val shown = lastSuccess
    when {
        state is SeriesDetailsViewModel.UiState.Error -> {
            TvErrorState(
                message = state.message,
                onRetry = { viewModel.loadSeriesInfo() },
                title = stringResource(R.string.series_error_loading),
                onBack = onBack,
                backLabel = stringResource(R.string.series_back_to_list),
            )
        }

        shown != null -> {
            EpisodeListContent(
                seriesDetail = shown.seriesDetail,
                relatedTitles = relatedTitles,
                tmdbTitle = tmdbTitle,
                logoUrl = logoUrl,
                backdropUrl = backdropUrl,
                alternateStreams = alternateStreams,
                seriesName = shown.streamName,
                categoryId = shown.categoryId,
                mediaRepository = viewModel.mediaRepository!!,
                initialEpisodeId = initialEpisodeId,
                isFavorite = shown.isFavorite,
                categoryName = shown.categoryName,
                isRefreshing = isRefreshing,
                onToggleFavorite = { viewModel.toggleFavorite(shown.streamName) },
                onEpisodeSelected = onEpisodeSelected,
                onCategorySelected = { onCategorySelected(shown.categoryId) },
                onRefresh = { viewModel.refreshSeriesInfo() },
                onBack = onBack,
                onRelatedTitleSelected = onRelatedTitleSelected,
                onAlternateStreamSelected = { viewModel.switchToAlternateStream(it) },
            )
        }

        else -> {
            LoadingScreen()
        }
    }
}

// internal, not private: exercised directly by EpisodeSelectionScreenTest (androidTest) with a
// fake SeriesDetail/MediaRepository, bypassing the ViewModel/DI it would otherwise need — see
// docs/plans/archive/20260908_episode-selection-fragility-plan.md.
@Composable
internal fun EpisodeListContent(
    seriesDetail: SeriesDetail,
    relatedTitles: RelatedTitles,
    tmdbTitle: String?,
    logoUrl: String?,
    backdropUrl: String?,
    alternateStreams: List<MediaItem>,
    seriesName: String,
    categoryId: String,
    mediaRepository: MediaRepository,
    initialEpisodeId: String? = null,
    isFavorite: Boolean,
    categoryName: String?,
    isRefreshing: Boolean,
    onToggleFavorite: () -> Unit,
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onCategorySelected: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit,
    onAlternateStreamSelected: (MediaItem) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
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
    val listState = rememberLazyListState()
    val scale = LocalUiScale.current
    val episodeCardStyle = episodeCardStyle()
    val watchedToggleScope = rememberCoroutineScope()

    // Selected episode for detail panel — only set by an explicit tap (including on the
    // Continue Watching resume episode below); arriving here never auto-opens it.
    var selectedEpisode by remember { mutableStateOf<DomainEpisodeItem?>(null) }

    // Phase 5 tab shell (docs/plans/archive/20260902_tv-detail-hero-ui-plan.md), same shape as
    // MovieDetailsScreen's Phase 4 one: one FocusRequester, attached by the outer TvSectionTabs
    // to whichever tab is currently selected, serves both directions — D-pad Down from the
    // action row into the tab row, and Back from inside the open section back to the tab row
    // instead of out of the screen. Declared here, ahead of the BackHandlers below that read it.
    val tabRowFocusRequester = remember { FocusRequester() }
    // D-pad Up from the topmost focusable row of a section back to the tab row — same fix, same
    // reason as `downToTabRow` below (default geometry search picks whichever tab pill sits
    // closest on the X axis to the section's content, not the actually-open tab; confirmed live,
    // Details' left-aligned stream-name picker landing on Episodes instead of Details). Applied
    // per-section only to each section's topmost focusable, not the whole section, so D-pad Up
    // between rows already inside a section is untouched.
    val upToTabRow =
        Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                tabRowFocusRequester.requestFocus()
                true
            } else {
                false
            }
        }
    // True while focus is anywhere inside the selected tab's section content — read by the
    // LazyColumn's onPreviewKeyEvent below to decide what Back does. Set true only by genuine
    // focus-in events, and false only by the explicit "a tab just regained focus" event
    // (onTabSelected) — see MovieDetailsScreen's identical flag for why a passive focus-loss
    // observation isn't trusted here.
    var focusInSection by remember { mutableStateOf(false) }

    // Back from inside a section: to the tab row. The row lives in the LazyColumn, so deep in the
    // episode list it isn't composed and focusing it does nothing — Back was then swallowed for
    // good (seen on a Shield, Law & Order season 3). Scroll it back in first, then focus it.
    val backScope = rememberCoroutineScope()
    val focusTabRow: () -> Unit = {
        backScope.launch {
            listState.scrollToItem(TABS_ITEM_INDEX)
            withFrameNanos { }
            if (!tabRowFocusRequester.requestFocusWithRetry()) onBack()
        }
    }

    // Closing the detail panel (Back, or the panel's own onBack) remembers which episode was open so
    // the effect below can hand focus back to its row: the panel replaces the whole LazyColumn, so
    // closing it leaves nothing focused otherwise.
    var reopenedEpisodeId by remember { mutableStateOf<String?>(null) }
    val reopenedEpisodeFocusRequester = remember { FocusRequester() }
    val closeEpisodePanel: () -> Unit = {
        reopenedEpisodeId = selectedEpisode?.id
        selectedEpisode = null
    }

    // Handle back press: dismiss detail panel first, then navigate back. Two handlers, not one
    // with a branch inside — the base-list case (selectedEpisode == null) needs its own explicit
    // BackHandler too. Fallback only in practice — the real fix is the LazyColumn's
    // onPreviewKeyEvent below (see its comment); confirmed on a real Shield that this
    // BackHandler alone never fires on the first press while a focused TV Button has focus.
    BackHandler(enabled = selectedEpisode != null) {
        closeEpisodePanel()
    }
    // Fallback for any state where the LazyColumn's onPreviewKeyEvent below isn't in the tree
    // yet — same focusInSection branch as that handler, and the same "an unconditional onBack()
    // here overrides it" finding from MovieDetailsScreen's identical fallback.
    BackHandler(enabled = selectedEpisode == null) {
        if (focusInSection) {
            focusTabRow()
        } else {
            onBack()
        }
    }

    // Back from what the Details tab's Category button or a Similar card opened lands on that
    // control, its tab section (the item after the tab row) scrolled in first. Play and the episode
    // panel need none of this: Back from the player lands on the resume card (effect below). While
    // this composition is such a return, the resume card's own scroll/focus effects stand down, or
    // the anchor arriving later would yank focus off the control.
    val returnFocus = rememberNavReturnFocus()
    NavReturnFocusEffect(returnFocus, fallback = tabRowFocusRequester) {
        listState.scrollToItem(TABS_ITEM_INDEX + 1)
    }
    val relatedReturnId = returnFocus.key?.takeIf { it.startsWith(RETURN_RELATED_PREFIX) }?.removePrefix(RETURN_RELATED_PREFIX)

    var targetRotation by remember { mutableStateOf(0f) }

    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = CinemaAnimation.fadeInDurationMs, easing = LinearEasing),
        label = "refresh_rotation",
    )

    // A `while (isRefreshing)` guard would never observe the parameter changing. The effect key
    // does that job: a false value cancels the loop.
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            while (true) {
                targetRotation = (targetRotation + 360f) % 3600f
                kotlinx.coroutines.delay(CinemaAnimation.loadingDebounceMs)
            }
        }
    }

    val sortedSeasons =
        remember(seriesDetail) {
            seriesDetail.sortedSeasons { num -> resources.getString(R.string.series_season_name_format, num) }
        }

    // Pre-sort episodes per season — avoid re-sorting inside LazyColumn on every recomposition
    val sortedEpisodesBySeason =
        remember(seriesDetail) {
            seriesDetail.episodes.mapValues { (_, eps) -> eps.sortedBy { it.episodeNumber } }
        }
    val totalEpisodes =
        remember(seriesDetail) {
            seriesDetail.episodes.values.sumOf { it.size }
        }
    val hasMultipleSeasons = sortedSeasons.size > 1

    // Tabbed sections (Phase 5, docs/plans/archive/20260902_tv-detail-hero-ui-plan.md): built from what this
    // series actually has, never a fixed list — same rule MovieDetailsScreen's Phase 4 tabs
    // follow. No separate Seasons tab: picking a season there did nothing but jump straight to
    // Episodes with that season selected — exactly what the season pills atop the Episodes tab
    // already do directly, one D-pad press instead of two.
    val hasCast = !seriesDetail.metadata.cast.isNullOrBlank()
    val hasMoreLikeThis = relatedTitles.moreLikeThis.isNotEmpty()
    val tabs =
        remember(hasCast, hasMoreLikeThis) {
            buildList {
                add(SeriesDetailTab.EPISODES)
                if (hasMoreLikeThis) add(SeriesDetailTab.MORE_LIKE_THIS)
                if (hasCast) add(SeriesDetailTab.CAST)
                add(SeriesDetailTab.DETAILS)
            }
        }
    // Episodes, not the first tab: the episode list is this screen's primary content, so it
    // stays the default view exactly as it was before tabs existed. rememberSaveable's init
    // lambda runs once — tabs order is stable for a given series, same assumption
    // MovieDetailsScreen's selectedTabIndex already makes.
    var selectedTabIndex by rememberSaveable { mutableStateOf(tabs.indexOf(SeriesDetailTab.EPISODES).coerceAtLeast(0)) }
    val safeTabIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex)
    val tabLabels = tabs.map { seriesDetailTabLabel(it) }

    // Episode/season this screen is anchored on, and whether that season was the user's own
    // pick — see EpisodeResumeState.kt. One season visible at a time, switched via tabs (D-pad
    // left/right moves focus between them, which selects immediately — see SeasonTab below)
    // instead of an accordion; resume season wins on first load, same priority the accordion
    // used to give it.
    val initialResumeSeason = initialEpisodeId?.let { seriesDetail.seasonNumberContaining(it) }
    val resumeState =
        rememberEpisodeResumeState(
            seriesId = seriesDetail.id,
            initialEpisodeId = initialEpisodeId,
            initialSeason = initialResumeSeason ?: sortedSeasons.firstOrNull()?.seasonNumber,
            // Only a real resume season counts as "manual" — not the plain "first season"
            // fallback used when there's no resume episode at all.
            initialManualSeason = initialResumeSeason != null,
        )

    val playButtonFocusRequester = remember { FocusRequester() }

    // "More" menu (Refresh info), as on the movie hero. A Dialog window: closing it hands focus
    // back to the More button.
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
            title = tmdbTitle ?: seriesDetail.name.ifEmpty { seriesName },
            refreshLabel = stringResource(R.string.series_refresh_info),
            onRefresh = onRefresh,
            onDismiss = { showMoreMenu = false },
        )
    }

    // Focus requester for the stream name row, so switching to an alternate stream can keep
    // focus there instead of it falling back to the window root (see streamSwitchSignal below).
    val streamNameFocusRequester = remember { FocusRequester() }
    // True once the row actually reports itself focused — lets the reassertion loop below stop
    // as soon as it has actually won, instead of guessing how many frames that takes.
    var streamRowFocused by remember { mutableStateOf(false) }
    // Bumped in onSelect, independent of resumeEpisodeId: selecting a dropdown item destroys
    // that focused node, and Compose has nothing left to restore to, so focus falls to the
    // window root and D-pad input goes nowhere until this claims it back for the row. Also
    // doubles as "a switch has happened this screen instance" — see the resumeEpisodeId effect
    // below, which reads it as a sticky flag, not a one-shot: switchToAlternateStream reloads
    // seriesDetail from cache and then from the network, so the resume anchor can land on this
    // screen more than once for a single switch (and the network leg can take seconds, per
    // loadSeriesDetail's comment on the Law & Order case), so a single-shot flag consumed by the
    // first of those firings left the later one free to steal focus back to Play.
    // rememberSaveable, not remember: this composable is disposed when Play navigates to the
    // player and recomposed fresh on return, same disposal that hit resumeState above — a plain
    // remember forgot the switch across that trip and stole focus back to Play/the resume card.
    // See docs/plans/archive/20260908_episode-selection-fragility-plan.md.
    var streamSwitchSignal by rememberSaveable { mutableStateOf(0) }

    // D-pad focus target for the resume episode card — requested below once it's on screen, so
    // OK is immediately playable without the user having to navigate to it first.
    val resumeCardFocusRequester = remember { FocusRequester() }
    // True until the screen has placed its first focus — see the focus effect below.
    var awaitingFirstFocus by remember { mutableStateOf(true) }

    // D-pad focus target for the (inner) season-pill row, the last focusable item above the
    // first episode card: Compose's default directional search from there lands on the first
    // episode card, not the tab row (the row's small height loses out to the much taller card
    // right below it in the nearest-candidate heuristic — focusGroup()/enter alone doesn't
    // override that, since the row is never chosen as a search candidate to begin with).
    // SeasonTabs attaches this to whichever tab is currently selected.
    val seasonTabsFocusRequester = remember { FocusRequester() }

    // The episodes header (UX overhaul Part II Phase 6, F-E-1/F-E-2): the sticky row above the
    // episode cards. Once the hero has scrolled away it also carries a compact line — show title ·
    // season · Play next — so an episode deep in the list still has its context on screen. Up from
    // the first episode lands in this header: the selected season tab, else Play next, else (hero
    // still on screen, one season) the section tab row. (Left from an episode opens the TV
    // navigation rail: the episode rows are the screen's left edge.)
    val compactHeaderShown by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val compactPlayFocusRequester = remember { FocusRequester() }
    val scrollToTabRow: () -> Unit = {
        backScope.launch {
            listState.scrollToItem(TABS_ITEM_INDEX)
            tabRowFocusRequester.requestFocusWithRetry()
        }
    }
    val focusEpisodesHeader: () -> Unit = {
        when {
            hasMultipleSeasons -> seasonTabsFocusRequester.requestFocus()
            compactHeaderShown -> compactPlayFocusRequester.requestFocus()
            else -> scrollToTabRow()
        }
    }

    // Scroll to the resume episode, so "highlighted" also means visible without the user having
    // to scroll to find it — but only when it's actually the reason this season is selected. A
    // manual tab focus/click must never yank the list back to the resume spot (or anywhere
    // else); it stays exactly where the user left it.
    // Items above the episodes: the hero and the section tabs, then the pinned episodes header
    // (season tabs, compact title line) and the gap under it.
    val headerItemCount = 4
    LaunchedEffect(resumeState.resumeEpisodeId) {
        if (returnFocus.isReturn) return@LaunchedEffect
        val targetId = resumeState.resumeEpisodeId ?: return@LaunchedEffect
        if (seriesDetail.seasonNumberContaining(targetId) != resumeState.selectedSeason) return@LaunchedEffect
        val seasonEpisodes = sortedEpisodesBySeason[resumeState.selectedSeason?.toString()] ?: return@LaunchedEffect
        val episodeIndex = seasonEpisodes.indexOfFirst { it.id == targetId }
        if (episodeIndex < 0) return@LaunchedEffect
        // A jump, not an animation: while a slow scroll ran, nothing held focus and the system
        // put it on a season tab, pulling the list back up (seen on a Shield).
        listState.scrollToItem(headerItemCount + episodeIndex)
    }

    // Per-episode resume fraction, drives the progress bar on each card. Re-read on every
    // entry into this screen — including coming back from the player — so a just-watched
    // episode's bar is current.
    var episodeProgress by remember(seriesDetail) { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var episodePlaybackPositions by remember(seriesDetail) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var watchedEpisodeIds by remember(seriesDetail) { mutableStateOf<Set<String>>(emptySet()) }

    // Re-reads progress/watched (including TMDB siblings) for every episode of this series.
    // Called after a manual mark, not just on initial load — a single-episode optimistic patch
    // would miss a sibling that the mark just made completed, and wouldn't restore a resume bar
    // an unmark brings back. Doesn't touch resumeEpisodeId/selectedSeasonNumber: a manual toggle
    // should not re-anchor or re-select a season out from under the user.
    suspend fun refreshEpisodeWatchState() {
        val allEpisodeIds = mutableListOf<String>()
        for (episodes in seriesDetail.episodes.values) {
            for (ep in episodes) {
                allEpisodeIds.add(ep.id)
            }
        }
        val allWatched = mediaRepository.getPlaybackPositionsSuspend(allEpisodeIds, ContentType.TV_SHOWS)
        episodeProgress =
            buildMap {
                for ((id, watched) in allWatched) {
                    watched.resumeProgress()?.let { put(id, it) }
                }
            }
        episodePlaybackPositions =
            buildMap {
                for ((id, watched) in allWatched) {
                    watched.resumeProgress()?.let { put(id, watched.playbackPosition) }
                }
            }
        watchedEpisodeIds =
            buildSet {
                for ((id, watched) in allWatched) {
                    if (watched.isCompleted) add(id)
                }
                addAll(mediaRepository.getSiblingCompletedEpisodeIds(seriesDetail.id))
            }
    }

    // Playback positions for every episode: the progress bars and watched checks above, plus
    // auto-expanding the season holding the next unwatched/in-progress episode.
    LaunchedEffect(seriesDetail) {
        // Avoid flatten().map to prevent intermediate list allocations
        val allEpisodeIds = mutableListOf<String>()
        for (episodes in seriesDetail.episodes.values) {
            for (ep in episodes) {
                allEpisodeIds.add(ep.id)
            }
        }

        val allWatched = mediaRepository.getPlaybackPositionsSuspend(allEpisodeIds, ContentType.TV_SHOWS)

        episodeProgress =
            buildMap {
                for ((id, watched) in allWatched) {
                    watched.resumeProgress()?.let { put(id, it) }
                }
            }
        episodePlaybackPositions =
            buildMap {
                for ((id, watched) in allWatched) {
                    watched.resumeProgress()?.let { put(id, watched.playbackPosition) }
                }
            }
        watchedEpisodeIds =
            buildSet {
                for ((id, watched) in allWatched) {
                    if (watched.isCompleted) add(id)
                }
                // TMDB dedup (Phase 5): completing one language/quality variant of an episode
                // completes them all. Checks only — never adds a resume bar or moves the anchor
                // below, same as the movies side of this.
                addAll(mediaRepository.getSiblingCompletedEpisodeIds(seriesDetail.id))
            }

        // Anchor on the episode worth watching next. The route (or a play from this screen)
        // names the episode last played; entering from the series list names none, so fall back
        // to the newest playback timestamp. Either way the anchor moves on when that episode is
        // already finished — re-evaluated on every entry, so backing out of an episode the user
        // just completed lands on the following one.
        // An episode from another copy of this show (played via an alternate stream) isn't in
        // this one's list: find the same season and episode here, rather than falling back to
        // S1E1.
        val lastPlayed = resumeState.resumeEpisodeId ?: allWatched.maxByOrNull { it.value.timestamp }?.key
        val lastPlayedHere =
            if (lastPlayed != null && seriesDetail.seasonNumberContaining(lastPlayed) == null) {
                mediaRepository
                    .getPlaybackPositionSuspend(lastPlayed, ContentType.TV_SHOWS)
                    ?.itemName
                    ?.let { seriesDetail.episodeIdMatchingName(it) }
                    ?: lastPlayed
            } else {
                lastPlayed
            }
        seriesDetail
            .resumeAnchorEpisodeId(
                sortedSeasons = sortedSeasons,
                lastPlayedEpisodeId = lastPlayedHere,
                isCompleted = { allWatched[it]?.isCompleted == true },
            )?.let { resumeState.applyAnchor(episodeId = it, season = null) }

        if (!hasMultipleSeasons) {
            // Only one season, so no "which season" guess needed — still must seed
            // selectedSeason or the episode list below stays keyed off null forever.
            sortedSeasons.firstOrNull()?.let { resumeState.applyAnchor(episodeId = null, season = it.seasonNumber) }
            return@LaunchedEffect
        }

        // The derived anchor's season beats the "first season with anything unwatched" guess:
        // a viewer mid-season 13 doesn't want season 1 opened because they skipped an episode.
        val targetSeason =
            resumeState.resumeEpisodeId?.let { seriesDetail.seasonNumberContaining(it) }
                ?: firstSeasonWithUnwatchedEpisode(
                    sortedSeasons = sortedSeasons,
                    episodesBySeason = sortedEpisodesBySeason,
                    isCompleted = { allWatched[it]?.isCompleted == true },
                )
        if (targetSeason != null) {
            resumeState.applyAnchor(episodeId = null, season = targetSeason)
        }
    }

    // Flat ordered list of all episodes across seasons (for prev/next navigation)
    val flatEpisodes =
        remember(seriesDetail, sortedSeasons) {
            seriesDetail.flattenedEpisodes(sortedSeasons)
        }

    val anchorEpisode =
        remember(flatEpisodes, resumeState.resumeEpisodeId) {
            flatEpisodes.firstOrNull { it.id == resumeState.resumeEpisodeId } ?: flatEpisodes.firstOrNull()
        }
    val anchorResumePosMs = anchorEpisode?.id?.let { episodePlaybackPositions[it] } ?: 0L
    val hasResume = anchorResumePosMs > 0L

    // The hero's Play and the compact header's Play next: one label, one action. The label names
    // the episode by its code and title ("▶ Play: S01E04 · Getting Into Cirque du Soleil", F-E-5);
    // the code is left out when the provider numbers everything 0, and the numbers alone stand in
    // without a title.
    val anchorOwnTitle = anchorEpisode?.let { episodeOwnTitle(it.title) }?.takeIf { it.isNotBlank() }?.let(::shortEpisodeTitle)
    val anchorTitle =
        if (anchorOwnTitle != null && anchorEpisode.episodeNumber > 0) {
            stringResource(
                R.string.series_episode_code_title_format,
                anchorEpisode.seasonNumber ?: 1,
                anchorEpisode.episodeNumber,
                anchorOwnTitle,
            )
        } else {
            anchorOwnTitle
        }
    val playNextLabel =
        when {
            anchorEpisode == null -> {
                stringResource(R.string.series_play_episode_action)
            }

            hasResume && anchorTitle != null -> {
                stringResource(R.string.series_resume_next_format, anchorTitle, formatTime(anchorResumePosMs))
            }

            hasResume -> {
                stringResource(
                    R.string.series_resume_episode_time_format,
                    anchorEpisode.seasonNumber ?: 1,
                    anchorEpisode.episodeNumber,
                    formatTime(anchorResumePosMs),
                )
            }

            anchorTitle != null -> {
                stringResource(R.string.series_play_next_format, anchorTitle)
            }

            else -> {
                stringResource(R.string.series_play_episode_format, anchorEpisode.seasonNumber ?: 1, anchorEpisode.episodeNumber)
            }
        }
    val playAnchor: (startFromBeginning: Boolean) -> Unit = { fromStart ->
        anchorEpisode?.let { ep ->
            resumeState.setResumeEpisode(ep.id, season = null)
            onEpisodeSelected(ep.id, ep.title, ep.extension ?: "mp4", fromStart)
        }
    }

    // Request focus on Play/Resume button when screen loads or anchor arrives — unless the user
    // has switched to an alternate stream at some point on this screen, in which case focus
    // stays on the stream name row so the D-pad doesn't silently land on Play.
    //
    // Skipped whenever there's a resume episode: the LaunchedEffect below this one scrolls the
    // list down to it (animateScrollToItem), and on return from the player that target scroll
    // offset is already restored (listState is itself saveable) before this effect's first
    // frame — so the hero item, Play button included, never gets composed at all, or gets
    // disposed moments after this claims it once the list settles. Either way focus doesn't
    // stay claimed, and Compose's own recovery then hands it to whatever's nearest once the
    // hero disposes — which was always the *first* season tab (Season 1), not necessarily the
    // one actually selected. Every SeasonTab treats receiving focus as the user switching to it
    // (see SeasonTab's focus-follow-select), so that stray handoff silently overwrote
    // selectedSeasonNumber back to season 1 — the bug behind "back from a random season lands
    // on season 1 episode 1". Focusing the resume card instead targets the item the list is
    // actually resting on, so nothing scrolls it away out from under the claim.
    //
    // Opened fresh on an episode further down (from Continue Watching, say), the card isn't
    // composed until the scroll effect above has brought it into view, so asking it first failed
    // silently and focus drifted to the first season tab — selecting season 1. Wait for the card
    // to be laid out; if it never is (2 s), take Play instead. While waiting, nothing holds focus
    // and the system hands it to the first season tab (seen on a Shield): [awaitingFirstFocus]
    // stops that stray focus from selecting its season.
    LaunchedEffect(resumeState.resumeEpisodeId) {
        if (streamSwitchSignal != 0 || returnFocus.isReturn) {
            awaitingFirstFocus = false
        } else {
            val target = resumeState.resumeEpisodeId
            val cardShown =
                target != null &&
                    withTimeoutOrNull(RESUME_CARD_WAIT_MS) {
                        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == target } }.first { it }
                    } != null
            if (cardShown) {
                resumeCardFocusRequester.requestFocusWithRetry(fallback = playButtonFocusRequester)
            } else {
                playButtonFocusRequester.requestFocusWithRetry()
            }
            awaitingFirstFocus = false
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
    }

    // After the episode panel closes, put focus back on the row that was open. The list was out of
    // the tree while the panel showed, so wait a frame for it, scroll the row into view if the
    // restored offset left it out, then request. If the row isn't in this season's list (stepped to
    // another season with Next/Previous), fall back to the tab row.
    LaunchedEffect(selectedEpisode) {
        val targetId = reopenedEpisodeId
        if (selectedEpisode == null && targetId != null) {
            withFrameNanos { }
            val index = sortedEpisodesBySeason[resumeState.selectedSeason?.toString()]?.indexOfFirst { it.id == targetId } ?: -1
            if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.key == targetId }) {
                listState.scrollToItem(headerItemCount + index)
                withFrameNanos { }
            }
            if (index >= 0) {
                reopenedEpisodeFocusRequester.requestFocusWithRetry(fallback = tabRowFocusRequester)
            } else {
                tabRowFocusRequester.requestFocusWithRetry()
            }
            reopenedEpisodeId = null
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                // Same Back interception as the list below, for the detail panel: its focused
                // Buttons swallow the first Back before the BackHandler sees it, so close here,
                // top-down, before any descendant gets the key.
                .then(
                    if (selectedEpisode != null) {
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                                closeEpisodePanel()
                                true
                            } else {
                                false
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
    ) {
        if (selectedEpisode != null) {
            val current = selectedEpisode!!
            val currentIdx =
                remember(flatEpisodes, current.id) {
                    flatEpisodes.indexOfFirst { it.id == current.id }
                }
            val previousEpisode = flatEpisodes.getOrNull(currentIdx - 1)
            val nextEpisode = flatEpisodes.getOrNull(currentIdx + 1)
            EpisodeDetailPanel(
                episode = current,
                seriesDetail = seriesDetail,
                seriesName = seriesName,
                categoryId = categoryId,
                providerName = providerName,
                backdropUrl = backdropUrl,
                mediaRepository = mediaRepository,
                previousEpisode = previousEpisode,
                nextEpisode = nextEpisode,
                onNavigate = { next -> selectedEpisode = next },
                onPlay = { episodeId, episodeTitle, extension, startFromBeginning ->
                    resumeState.setResumeEpisode(episodeId, season = null)
                    onEpisodeSelected(episodeId, episodeTitle, extension, startFromBeginning)
                },
                onBack = closeEpisodePanel,
            )
        } else {
            KeepHeroInView(scrollToTop = { listState.animateScrollToItem(0) }) { heroModifier ->
                LazyColumn(
                    state = listState,
                    // No horizontal margin here: the hero backdrop below must run edge to edge. Every
                    // other item applies Spacing.tvSafeMarginHorizontal to itself instead.
                    contentPadding = PaddingValues(bottom = Spacing.tvSafeMarginVertical.scaled(scale)),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
                    // Confirmed on a real Shield (logcat, MovieDetailsScreen's identical bug): the
                    // first Back press while a focused TV Button has focus reaches Compose's key
                    // dispatch fine, but something between there and the BackHandler(selectedEpisode
                    // == null) above marks it handled — that BackHandler never fires on the first
                    // press, only the second. Intercept here instead: onPreviewKeyEvent runs
                    // top-down, before any descendant (including the focused Button) gets a look, so
                    // this always wins the race. Matches the same pattern TvDpadEscape.kt uses.
                    modifier =
                        Modifier.fillMaxSize().testTag("episode_list").onPreviewKeyEvent { event ->
                            if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                                // Phase 5: Back out of an open tab section goes to the tab row, not
                                // out of the screen — same interception point as the screen-exit
                                // case, since this handler already runs before any descendant.
                                if (focusInSection) {
                                    focusTabRow()
                                } else {
                                    onBack()
                                }
                                true
                            } else {
                                false
                            }
                        },
                ) {
                    item(key = "series_hero", contentType = "hero") {
                        val seriesTitleText = tmdbTitle ?: seriesDetail.name.ifEmpty { seriesName }
                        val presentLabel = stringResource(R.string.series_present)
                        val yearRange = seriesDetail.seriesYearRange(presentLabel)
                        val countText =
                            if (sortedSeasons.size > 1) {
                                stringResource(R.string.series_seasons_and_episodes_format, sortedSeasons.size, totalEpisodes)
                            } else {
                                stringResource(R.string.series_total_episodes_format, totalEpisodes)
                            }
                        val metaLine =
                            listOfNotNull(
                                yearRange,
                                seriesDetail.metadata.rating?.let { ratingOutOfTen(it) },
                                seriesDetail.metadata.contentRating,
                                countText,
                                seriesDetail.metadata.genre,
                            )

                        TvDetailHero(
                            title = seriesTitleText,
                            backdropUrl = backdropUrl,
                            logoUrl = logoUrl,
                            titleFallback = {
                                Text(
                                    text = seriesTitleText,
                                    style = MaterialTheme.typography.displayLarge,
                                    color = CinemaTextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            metaLine = metaLine,
                            plot = seriesDetail.metadata.plot,
                            modifier = heroModifier,
                            sideSlot = anchorEpisode?.let { ep -> { NextUpCard(episode = ep, progress = episodeProgress[ep.id] ?: 0f) } },
                        ) {
                            // Phase 5: D-pad Down from any action button lands on the (outer) tab
                            // row below, not wherever default geometry search prefers — same fix as
                            // MovieDetailsScreen's Phase 4 downToTabRow, its focusProperties { down =
                            // ... } having never taken for this exact transition either.
                            val downToTabRow =
                                Modifier.onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                        tabRowFocusRequester.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }
                            // D-pad Up from the hero action row — these buttons are the topmost
                            // focusable in the whole screen, nothing above them to send focus to, so
                            // the LazyColumn never scrolls back up on its own. bringIntoView only
                            // guarantees the *focused* button stays visible, not the title/plot above
                            // it, so once a tall plot or tab switch has scrolled the list down, the
                            // top of the hero can sit clipped above the viewport with no way back —
                            // the exact "can't scroll up to see the whole picture" report. Force it
                            // explicitly instead of relying on focus search finding nothing to do.
                            val upScrollToTop =
                                Modifier.onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                        watchedToggleScope.launch { listState.animateScrollToItem(0) }
                                        true
                                    } else {
                                        false
                                    }
                                }
                            CinemaPrimaryButton(
                                onClick = { playAnchor(false) },
                                text = playNextLabel,
                                modifier =
                                    Modifier
                                        .testTag(
                                            "hero_play_button",
                                        ).focusRequester(playButtonFocusRequester)
                                        .then(downToTabRow)
                                        .then(upScrollToTop)
                                        .leftToRail(),
                            )
                            if (hasResume) {
                                CinemaIconButton(
                                    onClick = { playAnchor(true) },
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
                            }
                            CinemaIconButton(
                                onClick = onToggleFavorite,
                                modifier = downToTabRow.then(upScrollToTop),
                                icon = {
                                    Icon(
                                        imageVector = if (isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                                        contentDescription =
                                            if (isFavorite) {
                                                stringResource(
                                                    R.string.favorite_remove,
                                                )
                                            } else {
                                                stringResource(R.string.favorite_add)
                                            },
                                        tint = if (isFavorite) CinemaAccent else CinemaTextPrimary,
                                        modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                                    )
                                },
                            )
                            seriesDetail.metadata.trailerUrl?.let { trailer ->
                                CinemaIconButton(
                                    onClick = { openExternalUrl(context, trailer) },
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
                            // Refresh info is maintenance, not something to watch: it lives behind
                            // "More" at the end of the row, as on the movie hero (Phase 6). While a
                            // refresh runs the button shows a spinning Refresh glyph instead.
                            TvIconAction(
                                onClick = { showMoreMenu = true },
                                icon = if (isRefreshing) CinemaIcons.Refresh else CinemaIcons.MoreVert,
                                label = stringResource(R.string.details_action_more),
                                modifier = downToTabRow.then(upScrollToTop).focusRequester(moreButtonFocusRequester),
                                iconModifier = if (isRefreshing) Modifier.rotate(rotation) else Modifier,
                            )
                        }
                    }

                    item(key = "tabs") {
                        TvSectionTabs(
                            tabs = tabLabels,
                            selectedIndex = safeTabIndex,
                            onTabSelected = {
                                selectedTabIndex = it
                                // A tab regaining focus — whether from Left/Right, the initial Down
                                // from the action row, or our own Back-triggered
                                // tabRowFocusRequester.requestFocus() above — means focus is on the
                                // tab row, not in a section.
                                focusInSection = false
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                                    .padding(top = Spacing.xl.scaled(scale))
                                    // D-pad Down from the Episodes tab straight into the season pill
                                    // that's actually selected, bypassing this Row's own default
                                    // focus-group entry: SeasonTabs' `onEnter` is the exact class of
                                    // "default geometry search into a tab row" this codebase has
                                    // already found unreliable (see SeasonTabs' own comment on why
                                    // it's wired explicitly for D-pad left/right). An unreliable entry
                                    // landing on the wrong pill isn't just a focus glitch here — each
                                    // SeasonTab focus-follow-selects, so landing on Season 1 even for
                                    // one frame permanently overwrites the resume anchor's season.
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown &&
                                            event.key == Key.DirectionDown &&
                                            tabs.getOrNull(safeTabIndex) == SeriesDetailTab.EPISODES &&
                                            hasMultipleSeasons
                                        ) {
                                            seasonTabsFocusRequester.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                            entryFocusRequester = tabRowFocusRequester,
                            firstTabModifier = Modifier.leftToRail(),
                        )
                    }

                    // Only the selected tab's section composes below — a perf win as well as a look
                    // change, same rationale as MovieDetailsScreen's Phase 4 tabs. Episodes is the
                    // one exception to "one wrapped item per tab": it keeps emitting its existing
                    // stickyHeader (season pills) + itemsIndexed (episode cards) shape directly into
                    // this LazyColumn, instead of nesting them inside a single Box item, so episode
                    // cards stay individually lazy rather than measuring all at once.
                    when (tabs.getOrNull(safeTabIndex)) {
                        SeriesDetailTab.EPISODES -> {
                            // The episodes header — pinned in place as the episode list scrolls under it
                            // (stickyHeader, not a plain item), so it reads as the control for what's
                            // below rather than scrolling away with it, same as the mobile row: season
                            // tabs when there are several (D-pad left/right moves focus between them,
                            // which selects immediately — see SeasonTab), and once the hero is off
                            // screen a compact "title · season · Play next" line above them (Phase 6).
                            // Up from the first episode comes back here.
                            stickyHeader(key = "season_tabs", contentType = "header") {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            // Opaque, top padding included: cards scroll in underneath.
                                            .background(MaterialTheme.colorScheme.background)
                                            .padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                                            .padding(top = Spacing.md.scaled(scale))
                                            .onFocusChanged { if (it.hasFocus) focusInSection = true },
                                ) {
                                    if (compactHeaderShown) {
                                        CompactEpisodesHeader(
                                            title =
                                                listOfNotNull(
                                                    tmdbTitle ?: seriesDetail.name.ifEmpty { seriesName },
                                                    resumeState.selectedSeason?.let { stringResource(R.string.series_season_label, it) },
                                                ).joinToString(" · "),
                                            playLabel = playNextLabel,
                                            onPlay = { playAnchor(false) },
                                            playModifier =
                                                Modifier
                                                    .focusRequester(compactPlayFocusRequester)
                                                    .onPreviewKeyEvent { event ->
                                                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                                        when {
                                                            event.key == Key.DirectionUp -> {
                                                                scrollToTabRow()
                                                                true
                                                            }

                                                            event.key == Key.DirectionDown && hasMultipleSeasons -> {
                                                                seasonTabsFocusRequester.requestFocus()
                                                                true
                                                            }

                                                            else -> {
                                                                false
                                                            }
                                                        }
                                                    }.leftToRail(),
                                        )
                                    }
                                    if (hasMultipleSeasons) {
                                        Box(
                                            // Up from a season tab: Play next when the compact line is
                                            // showing, else the section tab row (on screen with the hero).
                                            modifier =
                                                Modifier.onPreviewKeyEvent { event ->
                                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                                        if (compactHeaderShown) {
                                                            compactPlayFocusRequester.requestFocus()
                                                        } else {
                                                            tabRowFocusRequester.requestFocus()
                                                        }
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                },
                                        ) {
                                            SeasonTabs(
                                                seasons = sortedSeasons,
                                                selectedSeason = resumeState.selectedSeason,
                                                onSeasonSelected = {
                                                    // EpisodeResumeState.selectSeason is itself guarded to
                                                    // a no-op when `it` is already selected: focus-follow-
                                                    // select (see SeasonTab) fires this the instant D-pad
                                                    // focus *enters* the row, landing on the already-
                                                    // selected tab — a no-op season change that used to
                                                    // still flip hasManuallySelectedSeason and write
                                                    // selectedSeasonNumber to its own value. That write was
                                                    // enough to recompose this row out from under the very
                                                    // focus-search transaction bringing it in, which is why
                                                    // the first Up/Down into the tabs always missed them and
                                                    // landed on the episode list instead. Skipping the no-op
                                                    // leaves that transaction undisturbed; a real season
                                                    // change (left/right to a different tab) still goes
                                                    // through normally.
                                                    if (!awaitingFirstFocus) resumeState.selectSeason(it)
                                                },
                                                entryFocusRequester = seasonTabsFocusRequester,
                                            )
                                        }
                                    }
                                }
                            }
                            // Gap before the episode cards, as its own plain (non-sticky) item —
                            // inside the stickyHeader above, it would pin along with the header and
                            // show cards through its unbacked height as they scroll past.
                            item(key = "season_tabs_gap", contentType = "spacer") {
                                Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                            }

                            val currentSeasonEpisodes = sortedEpisodesBySeason[resumeState.selectedSeason?.toString()] ?: emptyList()
                            itemsIndexed(currentSeasonEpisodes, key = {
                                _,
                                episode,
                                ->
                                episode.id
                            }, contentType = { _, _ -> "episode" }) { index, episode ->
                                val isContinueWatching = episode.id == resumeState.resumeEpisodeId
                                EpisodeCard(
                                    episode = episode,
                                    cardStyle = episodeCardStyle,
                                    isContinueWatching = isContinueWatching,
                                    watchProgress = episodeProgress[episode.id] ?: 0f,
                                    isWatched = episode.id in watchedEpisodeIds,
                                    focusRequester = if (isContinueWatching) resumeCardFocusRequester else null,
                                    modifier =
                                        Modifier
                                            .onPreviewKeyEvent { event ->
                                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                                when {
                                                    // Up from the first episode goes back to the episodes
                                                    // header in one press (Phase 6, F-E-2) — an explicit
                                                    // request, since plain focus search into the season
                                                    // tabs grabbed the wrong pill and, via its
                                                    // focus-follow-select, changed season. Left is the
                                                    // rail's (leftToRail below): every episode row starts
                                                    // at the screen's left edge.
                                                    index == 0 && event.key == Key.DirectionUp -> {
                                                        focusEpisodesHeader()
                                                        true
                                                    }

                                                    // Right stays put: arrows move focus, they don't change
                                                    // season — that is the season tabs' job, one Left away.
                                                    event.key == Key.DirectionRight -> {
                                                        true
                                                    }

                                                    else -> {
                                                        false
                                                    }
                                                }
                                            }.padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                                            .onFocusChanged { if (it.hasFocus) focusInSection = true }
                                            .then(
                                                if (episode.id == reopenedEpisodeId) {
                                                    Modifier.focusRequester(reopenedEpisodeFocusRequester)
                                                } else {
                                                    Modifier
                                                },
                                            ).testTag("episode_${episode.id}")
                                            .leftToRail(),
                                    onClick = {
                                        selectedEpisode = episode
                                    },
                                    onLongPress = {
                                        // Manual watched/unwatched mark (Phase 6,
                                        // docs/plans/archive/20260828_watch-state-durable-storage-plan.md). Optimistic:
                                        // flips this episode's own badge immediately rather than
                                        // waiting on the write; the full re-read after it lands is what
                                        // catches a TMDB sibling this mark just completed too (Phase 5)
                                        // and restores the resume bar on an unmark — a single-item
                                        // patch would miss both.
                                        val nowWatched = episode.id !in watchedEpisodeIds
                                        watchedEpisodeIds =
                                            if (nowWatched) watchedEpisodeIds + episode.id else watchedEpisodeIds - episode.id
                                        watchedToggleScope.launch {
                                            mediaRepository.setWatched(episode.id, ContentType.TV_SHOWS, nowWatched)
                                            refreshEpisodeWatchState()
                                        }
                                    },
                                )
                            }
                        }

                        SeriesDetailTab.CAST -> {
                            item(key = "tab-section-cast") {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                                            .padding(top = Spacing.md.scaled(scale))
                                            .focusRestorer()
                                            .onFocusChanged { if (it.hasFocus) focusInSection = true },
                                ) {
                                    SeriesCastTabContent(cast = seriesDetail.metadata.cast.orEmpty())
                                }
                            }
                        }

                        SeriesDetailTab.DETAILS -> {
                            item(key = "tab-section-details") {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                                            .padding(top = Spacing.md.scaled(scale))
                                            .focusRestorer()
                                            .onFocusChanged { if (it.hasFocus) focusInSection = true },
                                ) {
                                    SeriesDetailsTabContent(
                                        seriesDetail = seriesDetail,
                                        seriesName = seriesName,
                                        providerName = providerName,
                                        categoryName = categoryName,
                                        alternateStreams = alternateStreams,
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
                                        topFocusModifier = upToTabRow,
                                        categoryButtonFocusRequester = returnFocus.requesterFor(RETURN_CATEGORY),
                                    )
                                }
                            }
                        }

                        SeriesDetailTab.MORE_LIKE_THIS -> {
                            item(key = "tab-section-similar") {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.md.scaled(scale))
                                            .focusRestorer()
                                            .then(upToTabRow)
                                            .onFocusChanged { if (it.hasFocus) focusInSection = true },
                                ) {
                                    // No heading: the More Like This tab above already says what this row is.
                                    RelatedTitlesRow(
                                        title = null,
                                        items = relatedTitles.moreLikeThis,
                                        onItemClick = { item ->
                                            returnFocus.leaveFrom(RETURN_RELATED_PREFIX + item.id)
                                            onRelatedTitleSelected(item)
                                        },
                                        focusTargetId = relatedReturnId,
                                        focusTargetModifier =
                                            Modifier.navReturnFocusTarget(returnFocus, RETURN_RELATED_PREFIX + relatedReturnId),
                                        modifier = Modifier.padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale)),
                                    )
                                }
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

/** Direction the viewer stepped through episodes in, so focus can stay on that button. */
private enum class EpisodeStep { PREVIOUS, NEXT }

/** Phase 5 tab shell (docs/plans/archive/20260902_tv-detail-hero-ui-plan.md) — mirrors MovieDetailTab. */
private enum class SeriesDetailTab { EPISODES, MORE_LIKE_THIS, CAST, DETAILS }

@Composable
private fun seriesDetailTabLabel(tab: SeriesDetailTab): String =
    when (tab) {
        SeriesDetailTab.EPISODES -> stringResource(R.string.series_episodes_header)
        SeriesDetailTab.CAST -> stringResource(R.string.details_tab_cast)
        SeriesDetailTab.DETAILS -> stringResource(R.string.details_tab_details)
        SeriesDetailTab.MORE_LIKE_THIS -> stringResource(R.string.details_more_like_this)
    }

/** Cast tab: one comma-string split into plain chips — no data behind a real cast/crew model yet. */
@Composable
private fun SeriesCastTabContent(cast: String) {
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
 * Details tab: the full synopsis, then diagnostics rather than headline facts, one label/value row each — the stream-name
 * picker, TMDB id, director and the source last — then the category button. Cast lives in its own
 * tab ([SeriesCastTabContent]), not repeated here.
 */
@Composable
private fun SeriesDetailsTabContent(
    seriesDetail: SeriesDetail,
    seriesName: String,
    providerName: String,
    categoryName: String?,
    alternateStreams: List<MediaItem>,
    streamNameFocusRequester: FocusRequester,
    onStreamSelected: (MediaItem) -> Unit,
    onStreamFocusedChanged: (Boolean) -> Unit,
    onCategorySelected: () -> Unit,
    // D-pad Up from this section's topmost focusable row back to the tab row (see `upToTabRow`
    // at the call site). The picker below is normally that row, but it renders as plain
    // non-focusable Text when there are no alternate streams to switch between — in that case
    // the Category button, if present, becomes the actual top instead.
    topFocusModifier: Modifier = Modifier,
    categoryButtonFocusRequester: FocusRequester? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // The hero cuts the synopsis to three lines; this is where it can be read in full.
        seriesDetail.metadata.plot?.takeIf { it.isNotBlank() }?.let { plot ->
            Text(text = plot, style = MaterialTheme.typography.bodyMedium, color = CinemaTextPrimary)
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        TvDetailRow(label = stringResource(R.string.details_label_stream_name)) {
            StreamNamePicker(
                currentName = seriesName,
                alternates = alternateStreams,
                onSelect = onStreamSelected,
                focusRequester = streamNameFocusRequester,
                onFocusedChanged = onStreamFocusedChanged,
                modifier = topFocusModifier.leftToRail(),
            )
        }
        // The TMDB id is for developers only, as on the phone.
        val context = LocalContext.current
        val isDevMode = remember { AppSettings(context.applicationContext).isDevMode }
        if (isDevMode) {
            TvDetailRow(
                label = stringResource(R.string.details_label_tmdb),
                value = seriesDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none),
            )
        }
        seriesDetail.metadata.director?.let { TvDetailRow(label = stringResource(R.string.details_label_director), value = it) }
        TvDetailRow(label = stringResource(R.string.details_label_source), value = providerName)

        if (categoryName != null) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            CinemaSecondaryButton(
                onClick = onCategorySelected,
                text = stringResource(R.string.details_category_format, categoryName),
                // Only the actual top of the section forwards Up to the tab row — with no
                // alternates, the picker above is plain Text and this button is it instead.
                modifier =
                    (if (alternateStreams.isEmpty()) topFocusModifier else Modifier)
                        .then(categoryButtonFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                        .leftToRail(),
            )
        }
    }
}

/**
 * The hero's top-right "Next Up" note (Phase 5, docs/plans/archive/20260902_tv-detail-hero-ui-plan.md): a
 * re-presentation of the same continue-watching state the episode list's own resume card and
 * progress bars already show, not new behaviour. Plain text, not a card (UX overhaul Part II
 * Phase 6, F-E-6): the glass card with a thumbnail it used to be looked like a button and wasn't
 * one. It stays non-focusable — Play, one press away, names the same episode and plays it — so it
 * is drawn as text (shadowed, to stay legible over the backdrop) and nothing about it says "press".
 */
@Composable
private fun NextUpCard(
    episode: DomainEpisodeItem,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val shadow = remember { Shadow(color = Color.Black.copy(alpha = CinemaAlpha.textHigh), blurRadius = NEXT_UP_SHADOW_BLUR) }
    Column(
        modifier = modifier.width(TvDimensions.heroSideSlotWidth.scaled(scale)),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = stringResource(R.string.series_next_up_title),
            style = typography.labelMedium.copy(shadow = shadow),
            color = CinemaTextSecondary,
        )
        Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
        val label =
            listOfNotNull(
                episode.seasonNumber?.let { stringResource(R.string.series_season_label, it) },
                stringResource(R.string.series_episode_label, episode.episodeNumber),
            ).joinToString(" · ")
        Text(text = label, style = typography.titleSmall.copy(shadow = shadow), color = CinemaTextPrimary)
        val ownTitle = episodeOwnTitle(episode.title)
        if (ownTitle.isNotBlank()) {
            Text(
                text = ownTitle,
                style = typography.bodySmall.copy(shadow = shadow),
                color = CinemaTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        }
        if (progress > 0f) {
            // Remaining time, not elapsed — "26m remaining" answers "is this worth starting
            // now", which elapsed time doesn't.
            val remainingText =
                remember(episode.metadata.duration, progress) {
                    val totalSecs = episode.metadata.duration?.let { parseDurationToSeconds(it) } ?: return@remember null
                    val remainingSecs = (totalSecs * (1f - progress)).toLong().coerceAtLeast(0)
                    if (remainingSecs <= 0) null else formatDuration(remainingSecs.toString())
                }
            remainingText?.let {
                Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                Text(
                    text = stringResource(R.string.series_remaining_format, it),
                    style = typography.labelSmall.copy(shadow = shadow),
                    color = CinemaTextSecondary,
                )
            }
        }
    }
}

/**
 * The compact line of the episodes header, shown once the hero has scrolled away (UX overhaul
 * Part II Phase 6, F-E-1): "show title · season" and Play next, so an episode deep in the list
 * keeps its context and the next episode stays one press away.
 */
@Composable
private fun CompactEpisodesHeader(
    title: String,
    playLabel: String,
    onPlay: () -> Unit,
    playModifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    val titleStyle = MaterialTheme.typography.titleMedium
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale)),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = titleStyle.copy(fontSize = titleStyle.fontSize.scaled(scale)),
            color = CinemaTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        CinemaSecondaryButton(onClick = onPlay, text = playLabel, modifier = playModifier)
    }
}

/** An episode title short enough for a button label: a long provider title is cut with an ellipsis. */
private fun shortEpisodeTitle(title: String): String =
    if (title.length <= PLAY_LABEL_TITLE_MAX) title else title.take(PLAY_LABEL_TITLE_MAX - 1).trimEnd() + "…"

private const val PLAY_LABEL_TITLE_MAX = 40
private const val NEXT_UP_SHADOW_BLUR = 8f

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
    onSelect: (MediaItem) -> Unit,
    focusRequester: FocusRequester,
    onFocusedChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val textStyle = MaterialTheme.typography.bodyMedium
    val textColor = CinemaTextPrimary

    if (alternates.isEmpty()) {
        Text(
            text = currentName,
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
    Box(modifier = modifier.bringIntoViewRequester(bringIntoViewRequester)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .testTag("stream_name_picker")
                    .background(
                        color = if (isFocused) CinemaAccent.copy(alpha = CinemaAlpha.tint) else Color.Transparent,
                        shape = RoundedCornerShape(CornerRadius.medium),
                    ).then(
                        if (isFocused) {
                            Modifier.border(
                                width = TvFocusTokens.focusBorderWidth,
                                color = CinemaAccentLight,
                                shape = RoundedCornerShape(CornerRadius.medium),
                            )
                        } else {
                            Modifier
                        },
                    ).focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused
                        onFocusedChanged(it.isFocused)
                    }.clickable {
                        coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                        expanded = true
                    }.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        ) {
            Text(
                text = currentName,
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
private fun EpisodeDetailPanel(
    episode: DomainEpisodeItem,
    seriesDetail: SeriesDetail,
    seriesName: String,
    categoryId: String,
    providerName: String,
    backdropUrl: String?,
    mediaRepository: MediaRepository,
    previousEpisode: DomainEpisodeItem?,
    nextEpisode: DomainEpisodeItem?,
    onNavigate: (DomainEpisodeItem) -> Unit,
    onPlay: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val extension = episode.extension ?: "mp4"
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography

    val playButtonFocusRequester = remember { FocusRequester() }

    // Load resume position. Keyed on the episode so stepping to another one clears the previous
    // episode's value immediately rather than showing its resume time until the lookup lands —
    // and the lookup assigns unconditionally, so an episode with nothing to resume resets it
    // instead of leaving the last one's position behind.
    var resumePositionMs by remember(episode.id) { mutableStateOf(0L) }

    LaunchedEffect(episode.id) {
        val watched = mediaRepository.getPlaybackPositionSuspend(episode.id, ContentType.TV_SHOWS)
        resumePositionMs = watched?.resumeProgress()?.let { watched.playbackPosition } ?: 0L
    }

    // Which button moved us here, so stepping through episodes doesn't drop focus back onto
    // Play every time — pressing Next repeatedly would otherwise mean navigating back down to
    // the Next button after each episode. Reset when the panel closes, so opening an episode
    // from the list still starts on Play.
    var arrivedVia by remember { mutableStateOf<EpisodeStep?>(null) }
    val previousButtonFocusRequester = remember { FocusRequester() }
    val nextButtonFocusRequester = remember { FocusRequester() }

    // Request focus on Play/Resume button when screen loads, resume data arrives, or episode
    // changes — or back onto the step button just used, when there is still an episode that way.
    LaunchedEffect(episode.id, resumePositionMs) {
        val target =
            when {
                arrivedVia == EpisodeStep.PREVIOUS && previousEpisode != null -> previousButtonFocusRequester
                arrivedVia == EpisodeStep.NEXT && nextEpisode != null -> nextButtonFocusRequester
                else -> playButtonFocusRequester
            }
        target.requestFocusWithRetry(fallback = playButtonFocusRequester)
    }

    val hasResume = resumePositionMs > 0L
    val seasonEpisodeLabel =
        listOfNotNull(
            episode.seasonNumber?.let { stringResource(R.string.series_season_label, it) },
            stringResource(R.string.series_episode_label, episode.episodeNumber),
        ).joinToString(" · ")
    val contentRating = episode.metadata.contentRating ?: seriesDetail.metadata.contentRating
    val endsAtContext = LocalContext.current
    val endsAtText =
        remember(episode.metadata.duration, resumePositionMs) {
            computeEndsAt(endsAtContext, episode.metadata.duration, resumePositionMs)
        }
    val metaLine =
        listOfNotNull(
            seasonEpisodeLabel.ifBlank { null },
            (episode.metadata.rating ?: seriesDetail.metadata.rating)?.let { ratingOutOfTen(it) },
            contentRating,
            episode.metadata.duration
                ?.takeIf(::hasMeaningfulDuration)
                ?.let { formatDuration(it) },
            endsAtText?.let { stringResource(R.string.movie_ends_at_format, it) },
            seriesDetail.metadata.genre,
        )

    val panelScroll = rememberScrollState()
    KeepHeroInView(scrollToTop = { panelScroll.animateScrollTo(0) }) { heroModifier ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.MediaNext -> {
                                nextEpisode?.let {
                                    arrivedVia = EpisodeStep.NEXT
                                    onNavigate(it)
                                    true
                                } ?: false
                            }

                            Key.MediaPrevious -> {
                                previousEpisode?.let {
                                    arrivedVia = EpisodeStep.PREVIOUS
                                    onNavigate(it)
                                    true
                                } ?: false
                            }

                            else -> {
                                false
                            }
                        }
                    }.verticalScroll(panelScroll)
                    .focusable(),
        ) {
            // Full-bleed, edge to edge — the series' own backdrop, since an episode has no backdrop
            // art of its own (docs/plans/archive/20260902_tv-detail-hero-ui-plan.md Phase 5: "no new screen").
            // The provider's title often only repeats the show and the number ("EN - Show - S01E22").
            val heroTitle = episodeOwnTitle(episode.title).ifBlank { stringResource(R.string.series_episode_label, episode.episodeNumber) }
            TvDetailHero(
                title = heroTitle,
                backdropUrl = backdropUrl,
                logoUrl = null,
                titleFallback = {
                    Text(
                        text = heroTitle,
                        style = MaterialTheme.typography.displayLarge,
                        color = CinemaTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                metaLine = metaLine,
                plot = episode.metadata.plot,
                modifier = heroModifier,
            ) {
                if (hasResume) {
                    val resumeTimeText = formatTime(resumePositionMs)
                    CinemaPrimaryButton(
                        onClick = { onPlay(episode.id, episode.title, extension, false) },
                        text = stringResource(R.string.movie_resume_from_format, resumeTimeText),
                        modifier = Modifier.focusRequester(playButtonFocusRequester).leftToRail(),
                    )
                    CinemaIconButton(
                        onClick = { onPlay(episode.id, episode.title, extension, true) },
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
                        onClick = { onPlay(episode.id, episode.title, extension, false) },
                        text = stringResource(R.string.series_play_episode_action),
                        modifier = Modifier.focusRequester(playButtonFocusRequester).leftToRail(),
                    )
                }
                // Step to the adjacent episode without leaving this screen. These used to be a text
                // hint for the remote's transport keys, which the Shield and Bravia remotes don't
                // have — so they read as buttons that did nothing. The key handler above still works
                // for remotes that do have them.
                previousEpisode?.let {
                    CinemaIconButton(
                        onClick = {
                            arrivedVia = EpisodeStep.PREVIOUS
                            onNavigate(it)
                        },
                        icon = {
                            Icon(
                                imageVector = CinemaIcons.SkipPrevious,
                                contentDescription = stringResource(R.string.player_prev_episode),
                                tint = CinemaTextPrimary,
                                modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                            )
                        },
                        modifier = Modifier.focusRequester(previousButtonFocusRequester),
                    )
                }
                nextEpisode?.let {
                    CinemaIconButton(
                        onClick = {
                            arrivedVia = EpisodeStep.NEXT
                            onNavigate(it)
                        },
                        icon = {
                            Icon(
                                imageVector = CinemaIcons.SkipNext,
                                contentDescription = stringResource(R.string.player_next_episode),
                                tint = CinemaTextPrimary,
                                modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                            )
                        },
                        modifier = Modifier.focusRequester(nextButtonFocusRequester),
                    )
                }
                // The trailer is the show's, not this episode's — Xtream and Jellyfin only ever
                // carry one per series.
                seriesDetail.metadata.trailerUrl?.let { trailer ->
                    val trailerContext = LocalContext.current
                    CinemaIconButton(
                        onClick = { openExternalUrl(trailerContext, trailer) },
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

            // Diagnostics, not headline facts — provider name, tmdb id, cast/director, container,
            // air date, bitrate. Plain column, no GlassPanel, same shape as the series list's own
            // details block below its hero.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                        .padding(top = Spacing.xl.scaled(scale), bottom = Spacing.tvSafeMarginVertical.scaled(scale)),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.details_tmdb_format,
                            episode.metadata.tmdbId ?: seriesDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none),
                        ),
                    style = typography.bodyMedium,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                )

                val cast = episode.metadata.cast ?: seriesDetail.metadata.cast
                cast?.let {
                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                    Text(
                        text = stringResource(R.string.movie_cast_format, it),
                        style = typography.bodyMedium,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val director = episode.metadata.director ?: seriesDetail.metadata.director
                director?.let {
                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    Text(
                        text = stringResource(R.string.movie_director_format, it),
                        style = typography.bodyMedium,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    )
                }

                episode.extension?.takeIf { it.isNotBlank() }?.let { ext ->
                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    Text(
                        text = "${stringResource(R.string.tech_container_label)} ${ext.uppercase()}",
                        style = typography.bodyMedium,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    )
                }

                episode.metadata.airDate?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    Text(
                        text = stringResource(R.string.series_aired_format, it),
                        style = typography.bodyMedium,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    )
                }

                episode.metadata.bitrate?.takeIf { it > 0 }?.let {
                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    Text(
                        text = stringResource(R.string.series_bitrate_format, it),
                        style = typography.bodyMedium,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    )
                }

                // The source last and labelled: alone at the top it read as a heading (TV UI audit, #12).
                Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                Text(
                    text = "${stringResource(R.string.details_label_source)} $providerName",
                    style = typography.bodyMedium,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                )
            }
        }
    }
}

/**
 * One row of season pills. D-pad left/right moves focus between them via Compose's default
 * focus search inside this Row — [SeasonTab] selects as soon as it receives focus, so that
 * movement alone switches the visible season, the TV equivalent of the mobile swipe.
 *
 * Drawn as smaller filled chips, not tabs: right under the section tabs (Episodes / Cast /
 * Details), two rows of the same tab look read as two equal levels (TV UI audit, #13).
 */
@Composable
private fun SeasonTabs(
    seasons: List<SeasonInfo>,
    selectedSeason: Int?,
    onSeasonSelected: (Int) -> Unit,
    entryFocusRequester: FocusRequester,
) {
    val scale = LocalUiScale.current
    // One FocusRequester per tab, explicitly wired to its left/right neighbor below — confirmed
    // on a real Shield and in the emulator that Compose's default geometry-based focus search is
    // unreliable for this row: D-pad down from the Category chip above landed on the *last* tab
    // (nearest horizontally, not first in reading order), and D-pad left/right between tabs
    // sometimes didn't move focus at all. Explicit wiring makes both deterministic.
    val focusRequesters = remember(seasons) { seasons.map { FocusRequester() } }
    val selectedIndex =
        remember(seasons, selectedSeason) {
            seasons.indexOfFirst { it.seasonNumber == selectedSeason }.coerceAtLeast(0)
        }
    // Scrolls sideways: a long-running show (Law & Order: 20+ seasons) otherwise squeezed its last
    // tabs to a sliver, "Season 12" drawn one letter per line. A focused tab scrolls itself into
    // view; the selected one is scrolled to as well, for a season picked without focus (the resume
    // season on open). Scrolled directly rather than with bringIntoView, which would also scroll
    // the episode list up to this row — away from the resume episode it was just brought to.
    val tabScroll = rememberScrollState()
    val tabStarts = remember(seasons) { IntArray(seasons.size) }
    LaunchedEffect(selectedIndex) {
        withFrameNanos { }
        tabStarts.getOrNull(selectedIndex)?.let { tabScroll.animateScrollTo(it) }
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // Opaque: this row is pinned via stickyHeader, so episode cards scroll in
                // underneath it and need to actually be hidden, not show through.
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = Spacing.sm.scaled(scale))
                .horizontalScroll(tabScroll)
                // Entering this row from outside (D-pad down from above, up from below) always
                // lands on the selected tab, not whichever one the default search prefers.
                // `onEnter` is only consulted once this Row is itself a focus-search candidate,
                // which requires `focusGroup()` — without it the row isn't a group at all and
                // `onEnter` silently never fires, leaving Up/Down to fall through to the default
                // (and per the original bug report, unreliable) leaf search.
                .focusGroup()
                .focusProperties {
                    onEnter = { focusRequesters.getOrNull(selectedIndex)?.requestFocus() }
                },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        seasons.forEachIndexed { index, season ->
            // The first chip is at the screen's left edge: Left from it opens the TV navigation rail.
            Box(
                modifier =
                    Modifier
                        .onPlaced { tabStarts[index] = it.positionInParent().x.roundToInt() }
                        .then(if (index == 0) Modifier.leftToRail() else Modifier),
            ) {
                SeasonTab(
                    season = season,
                    isSelected = season.seasonNumber == selectedSeason,
                    onSelected = { onSeasonSelected(season.seasonNumber) },
                    focusRequester = focusRequesters[index],
                    previousTabFocusRequester = focusRequesters.getOrNull(index - 1),
                    nextTabFocusRequester = focusRequesters.getOrNull(index + 1),
                    // Second requester on the same node, alongside `focusRequester` above — only the
                    // selected tab gets it, and it moves with selection as `index` changes across
                    // recompositions. This is what the Category chip's explicit `down` (see caller)
                    // actually resolves to.
                    entryFocusRequester = if (index == selectedIndex) entryFocusRequester else null,
                )
            }
        }
    }
}

@Composable
private fun SeasonTab(
    season: SeasonInfo,
    isSelected: Boolean,
    onSelected: () -> Unit,
    focusRequester: FocusRequester,
    previousTabFocusRequester: FocusRequester?,
    nextTabFocusRequester: FocusRequester?,
    entryFocusRequester: FocusRequester?,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) TvFocusTokens.focusedScaleSubtle else TvFocusTokens.defaultScale,
        animationSpec = tween(durationMillis = CinemaAnimation.focusDurationMs),
        label = "season_tab_focus_scale",
    )

    // Focus-follow-select, not a separate OK press: the point of a tab row is to be as immediate
    // as the mobile swipe it stands in for. Deferred to a LaunchedEffect rather than called
    // straight from onFocusChanged below: onSelected mutates state as far up as
    // EpisodeListContent's EpisodeResumeState, and doing that synchronously from inside the
    // focus-change callback risks recomposing this row out from under Compose's own
    // focus-transfer machinery mid-transaction. A LaunchedEffect runs after that transaction
    // settles.
    LaunchedEffect(isFocused) {
        if (isFocused) onSelected()
    }

    // P5: the selected chip keeps the resting look and is marked by a bottom accent bar plus
    // accent text; only focus lifts the container.
    val containerColor = if (isFocused) TvFocusTokens.focusedContainer else TvFocusTokens.restingContainer

    val textColor =
        when {
            isSelected -> TvFocusTokens.currentText
            isFocused -> CinemaTextPrimary
            else -> CinemaTextSecondary
        }

    Box(
        modifier =
            Modifier
                .focusRequester(focusRequester)
                .then(entryFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .focusProperties {
                    previousTabFocusRequester?.let { left = it }
                    nextTabFocusRequester?.let { right = it }
                }.graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                }.currentIndicator(
                    active = isSelected,
                    shape = SeasonChipShape,
                    edge = CurrentIndicatorEdge.Bottom,
                ).background(
                    color = containerColor,
                    shape = SeasonChipShape,
                ).then(
                    if (isFocused) {
                        Modifier.border(
                            width = TvFocusTokens.focusBorderWidth,
                            color = CinemaAccentLight,
                            shape = SeasonChipShape,
                        )
                    } else {
                        Modifier
                    },
                )
                // No separate .focusable(): .clickable() below already creates a focus target,
                // and stacking a second one on the same node was the actual cause of tabs being
                // unreachable by D-pad Up/Down from outside the row (Left/Right worked because
                // those go through the explicit FocusRequester wiring above, bypassing search).
                .onFocusChanged { isFocused = it.isFocused }
                .clickable { onSelected() }
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.series_season_label, season.seasonNumber),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
        )
    }
}

private val SeasonChipShape = RoundedCornerShape(percent = 50)

/**
 * Episode card styling, built once per list composition instead of once per card.
 * `CardDefaults.colors`/`.border` are `@Composable`, so they cannot be wrapped in `remember` —
 * hoisting the calls out of the item body is what stops a full style set being allocated per
 * visible card per recomposition. A data class so a fresh instance still compares equal and lets
 * cards skip.
 */
@Immutable
private data class EpisodeCardStyle(
    val colors: CardColors,
    val cardScale: CardScale,
    val glow: CardGlow,
    val shape: CardShape,
    val border: CardBorder,
)

@Composable
private fun episodeCardStyle(): EpisodeCardStyle =
    EpisodeCardStyle(
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurface,
                focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
            ),
        cardScale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleContent,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        glow =
            CardDefaults.glow(
                focusedGlow =
                    Glow(
                        elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                        elevation = TvFocusTokens.focusShadowElevation,
                    ),
            ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CornerRadius.medium)),
        border = CardDefaults.border(),
    )

@Composable
private fun EpisodeCard(
    episode: DomainEpisodeItem,
    cardStyle: EpisodeCardStyle,
    isContinueWatching: Boolean = false,
    watchProgress: Float = 0f,
    isWatched: Boolean = false,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
) {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val cardScaledStyles =
        remember(scale, typography) {
            object {
                val titleMedium = typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
                val bodyMedium = typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale))
                val labelSmall = typography.labelSmall.copy(fontSize = typography.labelSmall.fontSize.scaled(scale))
            }
        }
    Card(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .height(TvDimensions.episodeCardHeight.scaled(scale))
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .tvLongPress(onLongPress),
        colors = cardStyle.colors,
        shape = cardStyle.shape,
        scale = cardStyle.cardScale,
        border = cardStyle.border,
        glow = cardStyle.glow,
    ) {
        // Box, not a Column with a weighted row: the card is pinned to cardHeight and its content
        // already fills that, so a footer that consumes layout height gets pushed past the card's
        // clip bounds — it lays out (and shows up in semantics) but never paints.
        // Continue watching is the P5 "current" mark (bar + title colour), not an outline (F-E-3).
        Box(modifier = Modifier.fillMaxSize().currentIndicator(isContinueWatching)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(Spacing.md.scaled(scale)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Episode thumbnail, with the watched check overlaid on it. The resume bar is a
                // card-width footer below this row instead — see the note there.
                Box(
                    modifier =
                        Modifier.size(
                            width = TvDimensions.posterWidth.scaled(scale),
                            height = TvDimensions.posterHeight.scaled(scale),
                        ),
                ) {
                    CinemaThumbnail(
                        url = episode.thumbnailUrl,
                        fallbackLetter = episode.title.firstOrNull(),
                        contentType = ThumbnailContentType.TV_SHOW,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (isWatched) {
                        WatchedBadge(
                            size = TvDimensions.iconMedium.scaled(scale),
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(Spacing.xxs.scaled(scale)),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))

                Text(
                    text = stringResource(R.string.series_episode_number_short, episode.episodeNumber),
                    style = cardScaledStyles.titleMedium,
                    color = CinemaAccentLight,
                    // At least the old column width, growing for E1992 rather than wrapping it
                    // onto two lines (Phase 6, F-E-4).
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.widthIn(min = Spacing.xxl.scaled(scale)),
                )

                Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    if (isContinueWatching) {
                        Text(
                            text = stringResource(R.string.series_continue_watching_badge),
                            style = cardScaledStyles.labelSmall,
                            color = CinemaAccent,
                        )
                    }
                    // Nothing when the provider's title only repeats the show and the number.
                    val ownTitle = remember(episode.title) { episodeOwnTitle(episode.title) }
                    if (ownTitle.isNotBlank()) {
                        Text(
                            text = ownTitle,
                            style = cardScaledStyles.titleMedium,
                            color = if (isContinueWatching) TvFocusTokens.currentText else CinemaTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    episode.metadata.plot?.let { plotText ->
                        Text(
                            text = plotText,
                            style = cardScaledStyles.bodyMedium,
                            color = CinemaTextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                episode.metadata.duration?.takeIf(::hasMeaningfulDuration)?.let { duration ->
                    Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                    Text(
                        // "1h 33m", as everywhere else — not the provider's "01:33:44" (TV UI audit, X10).
                        text = formatDuration(duration),
                        style = cardScaledStyles.bodyMedium,
                        color = CinemaTextSecondary,
                    )
                }
            }

            // Resume progress, card-width along the bottom edge — same placement as the stream row in
            // StreamList, so a half-watched episode and a half-watched film read the same. Poster-width
            // was too short to be legible.
            if (watchProgress > 0f) {
                LinearProgressIndicator(
                    progress = { watchProgress.coerceIn(0f, 1f) },
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            // Kept clear of the card's rounded bottom edge, which is drawn over the card's
                            // bottom edge — without this inset a bar of the same thickness as the
                            // stroke is completely hidden underneath it.
                            .padding(bottom = TvDimensions.borderFocused.scaled(scale))
                            .height(TvDimensions.resumeBarHeight.scaled(scale)),
                    color = CinemaAccent,
                    trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.focusedTint),
                )
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
                text = stringResource(R.string.series_loading_episodes),
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

/** The section tab row's index in the episode list: right after the hero. */
private const val TABS_ITEM_INDEX = 1

// Keys for the controls that navigate away from series details — see rememberNavReturnFocus.
private const val RETURN_CATEGORY = "category"
private const val RETURN_RELATED_PREFIX = "related:"

/** How long the screen waits for the resume episode's card to scroll into view before focusing Play instead. */
private const val RESUME_CARD_WAIT_MS = 2_000L
