package org.njarasoa.fijerena.feature.episode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.WatchedItem
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.resumeProgress
import org.njarasoa.fijerena.core.network.tmdb.TitleCredits
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.domain.SeasonInfo
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.domain.cleanTitle
import org.njarasoa.fijerena.core.player.domain.episodeOwnTitle
import org.njarasoa.fijerena.core.player.domain.firstSeasonWithUnwatchedEpisode
import org.njarasoa.fijerena.core.player.domain.flattenedEpisodes
import org.njarasoa.fijerena.core.player.domain.isWatchedToEnd
import org.njarasoa.fijerena.core.player.domain.resumeAnchorEpisodeId
import org.njarasoa.fijerena.core.player.domain.seasonNumberContaining
import org.njarasoa.fijerena.core.player.domain.seriesYearRange
import org.njarasoa.fijerena.core.player.domain.sortedSeasons
import org.njarasoa.fijerena.core.player.model.computeEndsAt
import org.njarasoa.fijerena.core.player.model.extractYear
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.player.model.hasMeaningfulDuration
import org.njarasoa.fijerena.core.player.model.parseDurationToSeconds
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaBadge
import org.njarasoa.fijerena.core.ui.components.CinemaIcon
import org.njarasoa.fijerena.core.ui.components.CinemaProgressBar
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.MenuAction
import org.njarasoa.fijerena.core.ui.components.PanelCorner
import org.njarasoa.fijerena.core.ui.components.PanelFill
import org.njarasoa.fijerena.core.ui.components.RetryWhenOnline
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.TitleLogoOrText
import org.njarasoa.fijerena.core.ui.components.panel
import org.njarasoa.fijerena.core.ui.components.rememberChildProfile
import org.njarasoa.fijerena.core.ui.history.LocalNerd
import org.njarasoa.fijerena.core.ui.history.rememberSourceName
import org.njarasoa.fijerena.core.ui.newepisodes.LocalNewEpisodes
import org.njarasoa.fijerena.core.ui.newepisodes.NewEpisodeMark
import org.njarasoa.fijerena.core.ui.person.rememberEpisodeCredits
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.trailer.rememberPlayTrailer
import org.njarasoa.fijerena.core.ui.utils.displayDate
import org.njarasoa.fijerena.core.ui.utils.seasonsAndEpisodesText
import org.njarasoa.fijerena.core.ui.utils.titleFactRows
import org.njarasoa.fijerena.core.ui.viewmodels.PlayedSeries
import org.njarasoa.fijerena.core.ui.viewmodels.SendItem
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModelFactory
import org.njarasoa.fijerena.core.ui.wanted.rememberWishlist
import org.njarasoa.fijerena.feature.settings.components.SettingsPickerDialog
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.CinemaActionMenu
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.CinemaPullToRefresh
import org.njarasoa.fijerena.ui.components.CinemaTabRow
import org.njarasoa.fijerena.ui.components.ExpandablePlot
import org.njarasoa.fijerena.ui.components.HeaderGround
import org.njarasoa.fijerena.ui.components.LocalSendToTv
import org.njarasoa.fijerena.ui.components.MetaBadge
import org.njarasoa.fijerena.ui.components.MetaLine
import org.njarasoa.fijerena.ui.components.MetaText
import org.njarasoa.fijerena.ui.components.MobileCategoryLinkRow
import org.njarasoa.fijerena.ui.components.MobileCreditsTabContent
import org.njarasoa.fijerena.ui.components.MobileDetailHero
import org.njarasoa.fijerena.ui.components.MobileDetailRow
import org.njarasoa.fijerena.ui.components.MobileErrorState
import org.njarasoa.fijerena.ui.components.MobileScreenHeader
import org.njarasoa.fijerena.ui.components.OtherCopiesList
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.SavedCopyNote
import org.njarasoa.fijerena.ui.components.WishlistRemovePrompt
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.buttons.DetailIconAction
import org.njarasoa.fijerena.ui.components.buttons.PlayButtonLabel
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.components.cards.cinemaCardHairlineBorder
import org.njarasoa.fijerena.ui.components.ratingOutOfTen
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.core.player.domain.EpisodeItem as DomainEpisodeItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEpisodeSelectionScreen(
    seriesId: String,
    seriesName: String,
    categoryId: String,
    initialEpisodeId: String? = null,
    onEpisodeSelected: (
        episodeId: String,
        episodeTitle: String,
        extension: String,
        startFromBeginning: Boolean,
        series: PlayedSeries,
    ) -> Unit,
    onCategorySelected: (categoryId: String) -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit = {},
    // The route's source.
    providerId: Long = 0L,
    // A cast card or a crew name of the Cast tab: opens the person page.
    onPersonSelected: (personId: String, name: String, crew: Boolean) -> Unit = { _, _, _ -> },
    // The season to open on (an actor's season or episode, or an episode the source lacks).
    initialSeason: Int? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val sendToTv = LocalSendToTv.current
    val viewModel: SeriesDetailsViewModel =
        viewModel(
            factory =
                remember(seriesId, categoryId, providerId) {
                    SeriesDetailsViewModelFactory(context.applicationContext, seriesId, categoryId, seriesName, providerId)
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val relatedTitles by viewModel.relatedTitles.collectAsStateWithLifecycle()
    val tmdbTitle by viewModel.tmdbTitle.collectAsStateWithLifecycle()
    val credits by viewModel.credits.collectAsStateWithLifecycle()
    val logoUrl by viewModel.logoUrl.collectAsStateWithLifecycle()
    val backdropUrl by viewModel.backdropUrl.collectAsStateWithLifecycle()
    val alternateStreams by viewModel.alternateStreams.collectAsStateWithLifecycle()
    val sourceNames by viewModel.sourceNames.collectAsStateWithLifecycle()
    val sourceName = rememberSourceName(providerId)
    val isFavorite = (uiState as? SeriesDetailsViewModel.UiState.Success)?.isFavorite ?: false

    // Kept across a background refresh, so the list stays with a pull spinner instead of a
    // full-screen loading state.
    var lastSuccess by remember { mutableStateOf<SeriesDetailsViewModel.UiState.Success?>(null) }
    if (uiState is SeriesDetailsViewModel.UiState.Success) {
        lastSuccess = uiState as SeriesDetailsViewModel.UiState.Success
    }
    val displaySeriesDetail = lastSuccess?.seriesDetail
    val isRefreshing = uiState is SeriesDetailsViewModel.UiState.Loading && lastSuccess != null

    // The episode whose page opened a person page from its cast: Back from that page shows the
    // episode's page again (the navigation round trip drops every plain remember).
    var personReturnEpisodeId by rememberSaveable { mutableStateOf<String?>(null) }

    // The episode of the detail panel: set by a tap (also on the Continue Watching resume episode),
    // on the way back from a person page opened from the episode's page, and for an actor's
    // episode.
    var selectedEpisode by remember {
        mutableStateOf(
            personReturnEpisodeId?.let { id ->
                displaySeriesDetail?.episodes?.values?.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == id } }
            },
        )
    }
    // Used once: a later return (from the player) opens on the list again.
    LaunchedEffect(Unit) { personReturnEpisodeId = null }
    // An actor's episode (the route names its season too): its page opens once the show is in, so
    // the episode picked is the one on screen. Once only: Back shows the list on it, and a return
    // from the player stays where the user was.
    var actorEpisodeOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(displaySeriesDetail) {
        if (actorEpisodeOpened || initialEpisodeId == null || initialSeason == null) return@LaunchedEffect
        val episode =
            displaySeriesDetail?.episodes?.values?.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == initialEpisodeId } }
                ?: return@LaunchedEffect
        actorEpisodeOpened = true
        selectedEpisode = episode
    }
    val closeEpisode: () -> Unit = {
        selectedEpisode = null
        personReturnEpisodeId = null
    }

    // The episode and season this screen is anchored on, and whether the season was the user's own
    // pick (see EpisodeResumeState.kt). Hoisted above EpisodeListContent and EpisodeDetailContent,
    // which each dispose on the other's toggle and on navigation to the player; a plain remember
    // there would land on season 1 episode 1. initialSeason is best effort: on a cold load
    // seriesDetail is not there yet, and a rememberSaveable initial value is read once.
    // EpisodeListContent's anchor effect (keyed on seriesDetail) corrects it, at the cost of one
    // frame of season 1.
    val resumeState =
        rememberEpisodeResumeState(
            seriesId = seriesId,
            initialEpisodeId = initialEpisodeId,
            initialSeason = initialEpisodeId?.let { lastSuccess?.seriesDetail?.seasonNumberContaining(it) } ?: initialSeason,
        )

    BackHandler(enabled = selectedEpisode != null) {
        closeEpisode()
    }

    // The episode list opens on the backdrop under a clear top bar (TV visual refresh, Phase 5).
    // The bar turns solid with the title once the backdrop scrolls up under it, and stays solid on
    // an episode's page and with no list (loading, error). The list also reports it covered once
    // scrolled past the backdrop, as when the page opens on an episode.
    var heroCovered by remember { mutableStateOf(false) }
    val showsList = displaySeriesDetail != null && selectedEpisode == null
    val solidBar = heroCovered || !showsList

    Scaffold(
        topBar = {
            MobileScreenHeader(
                // TMDB's clean title once it resolves, the provider's raw stream name until then.
                title = if (solidBar) tmdbTitle ?: cleanTitle(lastSuccess?.streamName ?: seriesName) else "",
                onBack = {
                    if (selectedEpisode != null) {
                        closeEpisode()
                    } else {
                        onBack()
                    }
                },
                ground = if (solidBar) HeaderGround.Covered else HeaderGround.Artwork,
                // Favourite is in the icon row under Play / Resume (see EpisodeListContent), not in
                // the top bar.
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .panel(PanelFill.Ground)
                    .padding(
                        top = if (showsList) CinemaSpacing.none else paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding(),
                    ),
        ) {
            val errorState = uiState as? SeriesDetailsViewModel.UiState.Error
            when {
                displaySeriesDetail == null && errorState == null -> {
                    LoadingScreen()
                }

                errorState != null -> {
                    MobileErrorState(
                        message = errorState.message,
                        onRetry = { viewModel.loadSeriesInfo() },
                        title = stringResource(R.string.series_error_loading),
                        onBack = onBack,
                        extra = {
                            OtherCopiesList(alternateStreams, sourceNames) {
                                if (viewModel.otherSourceOf(it) !=
                                    null
                                ) {
                                    onRelatedTitleSelected(it)
                                } else {
                                    viewModel.switchToAlternateStream(it)
                                }
                            }
                        },
                    )
                }

                displaySeriesDetail != null && selectedEpisode != null -> {
                    val detail = displaySeriesDetail
                    val flatEpisodes =
                        remember(detail) {
                            val sorted = detail.sortedSeasons { num -> resources.getString(R.string.series_season_name_format, num) }
                            detail.flattenedEpisodes(sorted)
                        }
                    val currentIdx =
                        remember(flatEpisodes, selectedEpisode!!.id) {
                            flatEpisodes.indexOfFirst { it.id == selectedEpisode!!.id }
                        }
                    val previousEpisode = flatEpisodes.getOrNull(currentIdx - 1)
                    val nextEpisode = flatEpisodes.getOrNull(currentIdx + 1)
                    EpisodeDetailContent(
                        episode = selectedEpisode!!,
                        seriesDetail = detail,
                        categoryId = categoryId,
                        mediaRepository = viewModel.mediaRepository!!,
                        previousEpisode = previousEpisode,
                        nextEpisode = nextEpisode,
                        showCredits = credits?.takeUnless { it.isEmpty },
                        onPersonSelected = { personId, name, crew ->
                            personReturnEpisodeId = selectedEpisode?.id
                            onPersonSelected(personId, name, crew)
                        },
                        onNavigate = { next -> selectedEpisode = next },
                        onPlay = { episodeId, episodeTitle, extension, startFromBeginning ->
                            resumeState.setResumeEpisode(episodeId, season = null)
                            onEpisodeSelected(episodeId, episodeTitle, extension, startFromBeginning, viewModel.playedSeries())
                        },
                    )
                }

                displaySeriesDetail != null -> {
                    CinemaPullToRefresh(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshSeriesInfo() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        EpisodeListContent(
                            seriesDetail = displaySeriesDetail,
                            onRetry = { viewModel.refreshSeriesInfo() },
                            relatedTitles = relatedTitles,
                            credits = credits,
                            onPersonSelected = onPersonSelected,
                            tmdbTitle = tmdbTitle,
                            alternateStreams = alternateStreams,
                            sourceNames = sourceNames,
                            sourceName = sourceName,
                            seriesName = lastSuccess?.streamName ?: seriesName,
                            mediaRepository = viewModel.mediaRepository!!,
                            resumeState = resumeState,
                            initialEpisodeId = initialEpisodeId,
                            // An actor's episode names its season too; a Continue Watching card does not.
                            openAtTop = initialEpisodeId != null && initialSeason == null,
                            categoryName = lastSuccess?.categoryName,
                            logoUrl = logoUrl,
                            backdropUrl = backdropUrl,
                            topBarHeight = paddingValues.calculateTopPadding(),
                            onHeroCovered = { heroCovered = it },
                            isFavorite = isFavorite,
                            onToggleFavorite = { viewModel.toggleFavorite(lastSuccess?.streamName ?: seriesName) },
                            onPlayEpisode = { episodeId, episodeTitle, extension, startFromBeginning ->
                                resumeState.setResumeEpisode(episodeId, season = null)
                                onEpisodeSelected(episodeId, episodeTitle, extension, startFromBeginning, viewModel.playedSeries())
                            },
                            onSendEpisode = { episode ->
                                sendToTv?.send(
                                    SendItem(
                                        providerId = providerId,
                                        streamId = episode.id,
                                        streamName = episode.title,
                                        categoryId = lastSuccess?.categoryId ?: categoryId,
                                        contentType = ContentType.TV_SHOWS,
                                        episodeId = episode.id,
                                        episodeExtension = episode.extension,
                                        seriesId = seriesId,
                                        seriesName = seriesName,
                                        title = seriesName,
                                        subtitle = episode.title,
                                    ),
                                )
                            },
                            onEpisodeSelected = { episode ->
                                selectedEpisode = episode
                            },
                            onCategorySelected = { onCategorySelected(lastSuccess?.categoryId ?: categoryId) },
                            onRelatedTitleSelected = onRelatedTitleSelected,
                            // A copy on another source opens on that source, as a related title does.
                            onAlternateStreamSelected = {
                                if (viewModel.otherSourceOf(it) != null) {
                                    onRelatedTitleSelected(it)
                                } else {
                                    viewModel.switchToAlternateStream(it)
                                }
                            },
                        )
                    }
                }

                else -> {
                    LoadingScreen()
                }
            }
        }
    }
}

@Composable
private fun EpisodeListContent(
    seriesDetail: SeriesDetail,
    onRetry: () -> Unit,
    relatedTitles: RelatedTitles,
    credits: TitleCredits?,
    onPersonSelected: (personId: String, name: String, crew: Boolean) -> Unit,
    tmdbTitle: String?,
    alternateStreams: List<MediaItem>,
    sourceNames: Map<Long, String>,
    // The source's name, a Source row for a Nerd.
    sourceName: String?,
    seriesName: String,
    mediaRepository: MediaRepository,
    resumeState: EpisodeResumeState,
    // The episode the route opened the screen on (Continue Watching, an actor's episode).
    initialEpisodeId: String?,
    // Continue Watching: the page stays at its top, where Play resumes that episode; the list does
    // not scroll to it. An actor's episode scrolls into view.
    openAtTop: Boolean,
    categoryName: String?,
    logoUrl: String?,
    backdropUrl: String?,
    // The list draws under the top bar (for the backdrop); the pinned season row sits below it.
    topBarHeight: Dp,
    onHeroCovered: (Boolean) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayEpisode: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onSendEpisode: ((DomainEpisodeItem) -> Unit)? = null,
    onEpisodeSelected: (DomainEpisodeItem) -> Unit,
    onCategorySelected: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit,
    onAlternateStreamSelected: (MediaItem) -> Unit,
) {
    val context = LocalContext.current
    val playTrailer = rememberPlayTrailer()
    val resources = LocalResources.current
    val watchedToggleScope = rememberCoroutineScope()
    val sortedSeasons =
        remember(seriesDetail) {
            seriesDetail.sortedSeasons { num -> resources.getString(R.string.series_season_name_format, num) }
        }
    val sortedEpisodesBySeason =
        remember(seriesDetail) {
            seriesDetail.episodes.mapValues { (_, eps) -> eps.sortedBy { it.episodeNumber } }
        }
    val totalEpisodes =
        remember(seriesDetail) {
            seriesDetail.episodes.values.sumOf { it.size }
        }
    val hasMultipleSeasons = sortedSeasons.size > 1

    val currentSeasonIndex = sortedSeasons.indexOfFirst { it.seasonNumber == resumeState.selectedSeason }
    val previousSeason = if (currentSeasonIndex > 0) sortedSeasons[currentSeasonIndex - 1] else null
    val nextSeason =
        if (currentSeasonIndex in sortedSeasons.indices && currentSeasonIndex < sortedSeasons.lastIndex) {
            sortedSeasons[currentSeasonIndex + 1]
        } else {
            null
        }

    val listState = rememberLazyListState()
    val topBarPx = with(LocalDensity.current) { topBarHeight.roundToPx() }

    // The bar turns solid once the backdrop is under it, or once the list has scrolled past it
    // (the backdrop item is then not composed, so it reports nothing).
    var heroItemCovered by remember { mutableStateOf(false) }
    val scrolledPastHero by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val heroCovered = heroItemCovered || scrolledPastHero
    LaunchedEffect(heroCovered) { onHeroCovered(heroCovered) }

    // How far the season row moves down so that, pinned, it sits below the top bar: none while it
    // is lower than the bar, then as much as keeps it at the bar's bottom edge, so it never jumps.
    // Read at layout time, so scrolling does not recompose.
    val seasonRowShift: () -> Int = {
        if (listState.firstVisibleItemIndex > 0) {
            topBarPx
        } else {
            val rowTop =
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == SEASON_TABS_KEY }
                    ?.offset
            if (rowTop == null) 0 else (topBarPx - rowTop).coerceIn(0, topBarPx)
        }
    }

    // The anchor a season or show mark just moved Play to (markEpisodesWatched below): the list
    // does not scroll to it and stays where the user pressed the mark.
    var anchorMovedByMark by remember { mutableStateOf<String?>(null) }

    // Scroll to the resume episode only when it is the reason this season is selected: a manual tab
    // tap or swipe must never move the list.
    LaunchedEffect(resumeState.resumeEpisodeId) {
        val targetId = resumeState.resumeEpisodeId ?: return@LaunchedEffect
        if (targetId == anchorMovedByMark) return@LaunchedEffect
        if (openAtTop && targetId == initialEpisodeId) return@LaunchedEffect
        if (seriesDetail.seasonNumberContaining(targetId) != resumeState.selectedSeason) return@LaunchedEffect
        val seasonEpisodes = sortedEpisodesBySeason[resumeState.selectedSeason?.toString()] ?: return@LaunchedEffect
        val episodeIndex = seasonEpisodes.indexOfFirst { it.id == targetId }
        if (episodeIndex < 0) return@LaunchedEffect
        val headerItemCount = 1 + if (hasMultipleSeasons) 1 else 0

        // The episode stops below the top bar and the pinned season row, not under them.
        fun seasonRowHeight() =
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == SEASON_TABS_KEY }
                ?.size
        val knownRowHeight = seasonRowHeight()
        listState.animateScrollToItem(headerItemCount + episodeIndex, -(topBarPx + (knownRowHeight ?: 0)))
        // The row was not laid out before the scroll: it is pinned now, so its height is known.
        if (knownRowHeight == null) seasonRowHeight()?.let { listState.animateScrollBy(-it.toFloat()) }
    }

    var episodeProgress by remember(seriesDetail) { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var episodePlaybackPositions by remember(seriesDetail) { mutableStateOf<Map<String, Long>>(emptyMap()) }

    // The route's episode is the resume episode only once played: an actor's episode opens the
    // screen on it, but it is not where the viewer stopped, so no "Continue watching" (the episode
    // cast plan). Read again on each entry, a return from the player too.
    var routeEpisodePlayed by remember { mutableStateOf(false) }
    LaunchedEffect(initialEpisodeId) {
        routeEpisodePlayed = initialEpisodeId != null && wasPlayed(mediaRepository, initialEpisodeId)
    }
    // This show's new episodes, for the New mark on their cards (the new episode alerts plan).
    val newEpisodeIds = LocalNewEpisodes.current.episodeIds(mediaRepository.getProviderId(), seriesDetail.id)
    var watchedEpisodeIds by remember(seriesDetail) { mutableStateOf<Set<String>>(emptySet()) }
    // The Wishlist icon and its ask-once prompt (wishlist UX plan, Phase 1): asked when the show
    // is watched to the end, its last episode watched (consistency plan, C-D4). None for a child.
    val wishlistTitle = tmdbTitle ?: cleanTitle(seriesDetail.name.ifEmpty { seriesName })
    val wishlist =
        rememberWishlist(
            contentType = ContentType.TV_SHOWS,
            tmdbId = seriesDetail.metadata.tmdbId,
            title = wishlistTitle,
            year = extractYear(seriesDetail.metadata.year, seriesDetail.metadata.releaseDate, seriesDetail.name),
            watched = seriesDetail.isWatchedToEnd(sortedSeasons, watchedEpisodeIds),
        )
    if (wishlist?.askRemove == true) WishlistRemovePrompt(wishlistTitle, wishlist.answer)

    // Reads progress and watched state again (TMDB siblings included) for every episode of this
    // series. Called after a manual mark too: a single-episode patch would miss a sibling the mark
    // completed, and would not restore a resume bar after an unmark. Returns this series' own rows,
    // for markEpisodesWatched's re-anchor.
    suspend fun refreshEpisodeWatchState(): Map<String, WatchedItem> {
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
        return allWatched
    }

    // Mark a whole season (long-press its chip) or the whole show (the action row) watched or
    // unwatched (the season show watched plan). Hidden for a source that keeps watched state on its
    // server (Jellyfin). The wording follows episodesWatchState: "unwatched" only when every
    // episode in scope is watched.
    val canMarkManyWatched = remember(mediaRepository) { mediaRepository.supportsMarkWatched }
    var showAllWatched by remember(seriesDetail) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(seriesDetail, watchedEpisodeIds, canMarkManyWatched) {
        if (canMarkManyWatched) showAllWatched = mediaRepository.episodesWatchState(seriesDetail.id, season = null)
    }
    var markManyError by remember { mutableStateOf<String?>(null) }
    val markEpisodesWatched: (Int?, Boolean) -> Unit = { season, watched ->
        watchedToggleScope.launch {
            mediaRepository
                .setEpisodesWatched(seriesDetail.id, season, watched)
                .onFailure {
                    markManyError = friendlyErrorMessage(it, context, AppSettings(context.applicationContext).isNerdMode)
                }
            val allWatched = refreshEpisodeWatchState()
            // Play and the "Continue watching" badge move on to the first unwatched episode at once
            // (the watched followups plan, Phase 1); the selected season stays. A single-episode
            // toggle does not come here, so it leaves the anchor alone.
            seriesDetail
                .resumeAnchorEpisodeId(
                    sortedSeasons = sortedSeasons,
                    lastPlayedEpisodeId = resumeState.resumeEpisodeId,
                    isCompleted = { allWatched[it]?.isCompleted == true },
                )?.takeIf { it != resumeState.resumeEpisodeId }
                ?.let {
                    anchorMovedByMark = it
                    resumeState.applyAnchor(episodeId = it, season = null)
                }
        }
    }
    // The season whose long-press menu is open, with whether all its episodes are watched.
    var seasonMenu by remember { mutableStateOf<Pair<Int, Boolean?>?>(null) }
    // The episode whose action menu is open (a long press on its row).
    var episodeMenu by remember { mutableStateOf<DomainEpisodeItem?>(null) }
    val openSeasonMenu: (Int) -> Unit = { season ->
        watchedToggleScope.launch {
            seasonMenu = season to mediaRepository.episodesWatchState(seriesDetail.id, season)
        }
    }
    episodeMenu?.let { episode ->
        val nowWatched = episode.id in watchedEpisodeIds
        CinemaActionMenu(
            title =
                episodeOwnTitle(episode.title).ifBlank {
                    stringResource(R.string.series_episode_number_short, episode.episodeNumber)
                },
            actions =
                listOf(
                    MenuAction(
                        icon = if (nowWatched) CinemaIcons.MarkUnwatched else CinemaIcons.MarkWatched,
                        label = stringResource(if (nowWatched) R.string.watched_unmark else R.string.watched_mark),
                        onClick = {
                            // Manual watched mark (the watch state durable storage plan, Phase 6).
                            // Optimistic: this episode's badge flips at once; the full read after
                            // the write catches a TMDB sibling the mark completed (Phase 5) and
                            // restores the resume bar on an unmark.
                            watchedEpisodeIds =
                                if (nowWatched) watchedEpisodeIds - episode.id else watchedEpisodeIds + episode.id
                            watchedToggleScope.launch {
                                mediaRepository.setWatched(episode.id, ContentType.TV_SHOWS, !nowWatched)
                                refreshEpisodeWatchState()
                            }
                        },
                    ),
                ) +
                    listOfNotNull(
                        onSendEpisode?.takeIf { LocalSendToTv.current?.available == true }?.let { send ->
                            MenuAction(CinemaIcons.Tv, stringResource(R.string.send_to_tv_action)) { send(episode) }
                        },
                    ),
            onDismiss = { episodeMenu = null },
        )
    }
    seasonMenu?.let { (season, allWatched) ->
        CinemaActionMenu(
            title = stringResource(R.string.series_season_name_format, season),
            actions =
                listOf(
                    MenuAction(
                        icon = if (allWatched == true) CinemaIcons.MarkUnwatched else CinemaIcons.MarkWatched,
                        label = stringResource(if (allWatched == true) R.string.watched_unmark_season else R.string.watched_mark_season),
                        onClick = { markEpisodesWatched(season, allWatched != true) },
                    ),
                ),
            onDismiss = { seasonMenu = null },
        )
    }
    markManyError?.let { message ->
        CinemaAlertDialog(
            onDismissRequest = { markManyError = null },
            title = stringResource(R.string.watched_mark_many_failed),
            text = {
                CinemaText(
                    message,
                    role = TextRole.Body,
                    tone = TextTone.Inherit,
                )
            },
            main = DialogAction(stringResource(R.string.common_ok)) { markManyError = null },
        )
    }

    LaunchedEffect(seriesDetail) {
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
                // TMDB dedup (Phase 5): completing one language or quality variant of an episode
                // completes them all. Checks only: never adds a resume bar or moves the anchor, as
                // for films.
                addAll(mediaRepository.getSiblingCompletedEpisodeIds(seriesDetail.id))
            }

        val derivedAnchor =
            seriesDetail
                .resumeAnchorEpisodeId(
                    sortedSeasons = sortedSeasons,
                    // A row only marked watched has no playback timestamp (0): a mark is not a play.
                    lastPlayedEpisodeId =
                        resumeState.resumeEpisodeId
                            ?: allWatched.entries
                                .filter { it.value.timestamp > 0L }
                                .maxByOrNull { it.value.timestamp }
                                ?.key,
                    isCompleted = { allWatched[it]?.isCompleted == true },
                )?.also { resumeState.applyAnchor(episodeId = it, season = null) }

        if (!hasMultipleSeasons) {
            // Only one season, so no guess, but selectedSeason must still be set or the list below
            // stays keyed on null.
            sortedSeasons.firstOrNull()?.let { resumeState.applyAnchor(episodeId = null, season = it.seasonNumber) }
            return@LaunchedEffect
        }

        val targetSeason =
            derivedAnchor?.let { seriesDetail.seasonNumberContaining(it) }
                ?: firstSeasonWithUnwatchedEpisode(
                    sortedSeasons = sortedSeasons,
                    episodesBySeason = sortedEpisodesBySeason,
                    isCompleted = { allWatched[it]?.isCompleted == true },
                )
        if (targetSeason != null) {
            resumeState.applyAnchor(episodeId = null, season = targetSeason)
        }
    }

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

    // Segmented detail sections (the ui ux transitions flow uplift plan, Phase 4 3c), hoisted above
    // the LazyColumn: the hero item's TabRow and the LazyColumn's stickyHeader / items both need
    // it. Episodes is always the first tab. TMDB's credits when it has them (cast tab plan), else
    // the source's cast names (CT-D7).
    val tmdbCredits = credits?.takeUnless { it.isEmpty }
    val hasCast = tmdbCredits != null || !seriesDetail.metadata.cast.isNullOrBlank()
    val hasMoreLikeThis = relatedTitles.moreLikeThis.isNotEmpty()
    val tabs =
        remember(hasCast, hasMoreLikeThis) {
            buildList {
                add(SeriesDetailTab.EPISODES)
                add(SeriesDetailTab.DETAILS)
                if (hasCast) add(SeriesDetailTab.CAST)
                // Last: it loads late, so it shifts no other tab when it appears.
                if (hasMoreLikeThis) add(SeriesDetailTab.MORE_LIKE_THIS)
            }
        }
    // Kept by tab, not index: a tab appearing or going never moves the selection.
    var pickedTab by rememberSaveable { mutableStateOf(SeriesDetailTab.EPISODES) }
    val selectedTab = pickedTab.takeIf { it in tabs } ?: SeriesDetailTab.EPISODES

    LazyColumn(
        state = listState,
        contentPadding =
            PaddingValues(
                start = CinemaSpacing.md,
                end = CinemaSpacing.md,
                bottom =
                    CinemaSpacing.md + LocalDockInset.current,
            ),
        verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        modifier =
            Modifier
                .fillMaxSize()
                // A horizontal swipe anywhere in the list switches season, as a season tab tap
                // does; vertical scrolling is untouched (EpisodeDetailContent uses the same for
                // previous / next episode). Only on the Episodes tab: elsewhere it changed the
                // season out of sight.
                .pointerInput(hasMultipleSeasons, previousSeason, nextSeason, selectedTab) {
                    if (!hasMultipleSeasons || selectedTab != SeriesDetailTab.EPISODES) return@pointerInput
                    var dragAmount = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragAmount = 0f },
                        onDragEnd = {
                            if (dragAmount > EPISODE_SWIPE_THRESHOLD_PX && previousSeason != null) {
                                resumeState.selectSeason(previousSeason.seasonNumber)
                            } else if (dragAmount < -EPISODE_SWIPE_THRESHOLD_PX && nextSeason != null) {
                                resumeState.selectSeason(nextSeason.seasonNumber)
                            }
                        },
                        onHorizontalDrag = { change, delta ->
                            dragAmount += delta
                            change.consume()
                        },
                    )
                },
    ) {
        item(key = "series_hero_header") {
            Column(modifier = Modifier.fillMaxWidth()) {
                val seriesTitleText = tmdbTitle ?: cleanTitle(seriesDetail.name.ifEmpty { seriesName })
                MobileDetailHero(
                    title = seriesTitleText,
                    backdropUrl = backdropUrl,
                    posterUrl = seriesDetail.coverUrl,
                    logoUrl = logoUrl,
                    thumbnailContentType = ThumbnailContentType.TV_SHOW,
                    bleed = CinemaSpacing.md,
                    onCovered = { heroItemCovered = it },
                )

                if (seriesDetail.isSavedCopy) SavedCopyNote(onRetry = onRetry)

                seriesDetail.metadata.genre?.let { genre ->
                    Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                    CinemaText(
                        text = genre,
                        role = TextRole.Heading,
                        tone = TextTone.Secondary,
                    )
                }

                // One dot-separated facts line that wraps on a narrow phone (as
                // MobileMovieDetailsScreen): the content rating as an outlined badge, the rest as
                // plain text, in TV's order.
                val presentLabel = stringResource(R.string.series_present)
                val yearRange = seriesDetail.seriesYearRange(presentLabel)
                val countText =
                    if (sortedSeasons.size > 1) {
                        seasonsAndEpisodesText(sortedSeasons.size, totalEpisodes)
                    } else {
                        pluralStringResource(R.plurals.series_total_episodes_format, totalEpisodes, totalEpisodes)
                    }
                val seriesMetaSegments =
                    listOfNotNull<@Composable () -> Unit>(
                        yearRange?.let { { MetaText(it) } },
                        seriesDetail.metadata.rating?.let { { MetaText(ratingOutOfTen(it)) } },
                        seriesDetail.metadata.contentRating?.let { { MetaBadge(it) } },
                        { MetaText(countText) },
                    )

                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                MetaLine(segments = seriesMetaSegments)

                Spacer(modifier = Modifier.height(CinemaSpacing.lg))

                // Two lines (phone UI audit, #3): what the button does with the episode's code (the
                // play icon, then "Resume S02E10 · 19:04 left" or "Play S01E01"), then the
                // episode's own title. No code when the provider numbers episodes 0.
                val anchorOwnTitle = anchorEpisode?.let { episodeOwnTitle(it.title) }?.takeIf { it.isNotBlank() }
                val anchorSeason = anchorEpisode?.seasonNumber ?: 1
                val anchorHasCode = anchorEpisode != null && anchorEpisode.episodeNumber > 0
                val actionText =
                    if (hasResume) {
                        // Time left when the episode's length is known, else where it stopped.
                        val remainingMs =
                            anchorEpisode
                                ?.metadata
                                ?.duration
                                ?.let(::parseDurationToSeconds)
                                ?.let { it * 1000 - anchorResumePosMs }
                                ?.takeIf { it > 0 }
                        when {
                            anchorHasCode && remainingMs != null -> {
                                stringResource(
                                    R.string.series_resume_code_left_format,
                                    anchorSeason,
                                    anchorEpisode.episodeNumber,
                                    formatTime(remainingMs),
                                )
                            }

                            anchorHasCode -> {
                                stringResource(
                                    R.string.series_resume_code_from_format,
                                    anchorSeason,
                                    anchorEpisode.episodeNumber,
                                    formatTime(anchorResumePosMs),
                                )
                            }

                            else -> {
                                stringResource(R.string.movie_resume_from_format, formatTime(anchorResumePosMs))
                            }
                        }
                    } else if (anchorHasCode) {
                        stringResource(R.string.series_play_code_format, anchorSeason, anchorEpisode.episodeNumber)
                    } else {
                        stringResource(R.string.series_play_episode_action)
                    }
                CinemaButton(
                    onClick = {
                        anchorEpisode?.let { ep ->
                            onPlayEpisode(ep.id, ep.title, ep.extension ?: "mp4", false)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlayButtonLabel(actionText)
                        }
                        anchorOwnTitle?.let {
                            CinemaText(
                                text = it,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                role = TextRole.BodySmall,
                                tone = TextTone.Inherit,
                            )
                        }
                    }
                }

                // Secondary actions: only what the app supports (no Cast or Shuffle).
                Spacer(modifier = Modifier.height(CinemaSpacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    if (hasResume) {
                        DetailIconAction(
                            icon = CinemaIcons.Replay,
                            label = stringResource(R.string.player_osd_start_over),
                            onClick = {
                                anchorEpisode?.let { ep ->
                                    onPlayEpisode(ep.id, ep.title, ep.extension ?: "mp4", true)
                                }
                            },
                        )
                    }
                    DetailIconAction(
                        icon = if (isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                        label = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
                        onClick = onToggleFavorite,
                        tone = if (isFavorite) TextTone.Accent else TextTone.Primary,
                    )
                    if (canMarkManyWatched) {
                        val allWatched = showAllWatched == true
                        DetailIconAction(
                            icon = if (allWatched) CinemaIcons.CheckCircleFilled else CinemaIcons.CheckCircleOutline,
                            label = stringResource(if (allWatched) R.string.watched_unmark_show else R.string.watched_mark_show),
                            onClick = { markEpisodesWatched(null, !allWatched) },
                            tone = if (allWatched) TextTone.Accent else TextTone.Primary,
                        )
                    }
                    if (wishlist != null) {
                        DetailIconAction(
                            icon = if (wishlist.isOn) CinemaIcons.Bookmark else CinemaIcons.BookmarkBorder,
                            label = stringResource(if (wishlist.isOn) R.string.wanted_remove else R.string.wanted_add),
                            onClick = wishlist.toggle,
                            tone = if (wishlist.isOn) TextTone.Accent else TextTone.Primary,
                        )
                    }
                    seriesDetail.metadata.trailerUrl?.let { trailer ->
                        DetailIconAction(
                            icon = CinemaIcons.Movie,
                            label = stringResource(R.string.details_watch_trailer),
                            onClick = { playTrailer(trailer, seriesDetail.metadata.tmdbTrailerUrl) },
                        )
                    }
                    if (anchorEpisode != null && onSendEpisode != null && LocalSendToTv.current?.available == true) {
                        DetailIconAction(
                            icon = CinemaIcons.Tv,
                            label = stringResource(R.string.send_to_tv_action),
                            onClick = { onSendEpisode(anchorEpisode) },
                        )
                    }
                }

                seriesDetail.metadata.facts.tagline?.let {
                    Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    CinemaText(text = it, tone = TextTone.Secondary)
                }
                seriesDetail.metadata.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                    Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    ExpandablePlot(plot = plot, modifier = Modifier.fillMaxWidth())
                }

                // Segmented detail sections: the tab state is hoisted above the LazyColumn; this
                // item only reads it.
                Spacer(modifier = Modifier.height(CinemaSpacing.lg))
                CinemaTabRow(
                    selectedIndex = tabs.indexOf(selectedTab),
                    labels = tabs.map { seriesDetailTabLabel(it) },
                    onSelect = { pickedTab = tabs[it] },
                    // Each tab as wide as its label: equal widths cut the longer ones ("Antsipi…").
                    scrollable = true,
                )

                if (selectedTab == SeriesDetailTab.DETAILS) {
                    Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    SeriesDetailsTabContent(
                        seriesDetail = seriesDetail,
                        seriesName = seriesName,
                        alternateStreams = alternateStreams,
                        sourceNames = sourceNames,
                        sourceName = sourceName,
                        categoryName = categoryName,
                        onAlternateStreamSelected = onAlternateStreamSelected,
                        onCategorySelected = onCategorySelected,
                    )
                } else if (selectedTab == SeriesDetailTab.CAST) {
                    Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    if (tmdbCredits != null) {
                        MobileCreditsTabContent(
                            credits = tmdbCredits,
                            section = ThumbnailContentType.TV_SHOW,
                            onPersonSelected = onPersonSelected,
                        )
                    } else {
                        CastChipsTabContent(cast = seriesDetail.metadata.cast.orEmpty())
                    }
                } else if (selectedTab == SeriesDetailTab.MORE_LIKE_THIS) {
                    Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    RelatedTitlesRow(
                        title = stringResource(R.string.details_more_like_this),
                        items = relatedTitles.moreLikeThis,
                        onItemClick = onRelatedTitleSelected,
                    )
                }
            }
        }

        // Episodes tab only: the season tabs pin in place as the list scrolls under them
        // (stickyHeader), so they read as the control for what is below. A horizontal swipe in the
        // list (see pointerInput above) does the same as a tab tap.
        if (selectedTab == SeriesDetailTab.EPISODES) {
            if (hasMultipleSeasons) {
                stickyHeader(key = SEASON_TABS_KEY, contentType = "header") {
                    SeasonTabs(
                        pinShift = seasonRowShift,
                        seasons = sortedSeasons,
                        selectedSeason = resumeState.selectedSeason,
                        onSeasonSelected = { resumeState.selectSeason(it) },
                        onSeasonLongPress = if (canMarkManyWatched) openSeasonMenu else null,
                    )
                }
            }

            val currentSeasonEpisodes = sortedEpisodesBySeason[resumeState.selectedSeason?.toString()] ?: emptyList()
            items(currentSeasonEpisodes, key = { it.id }, contentType = { "episode" }) { episode ->
                EpisodeCard(
                    episode = episode,
                    isContinueWatching =
                        episode.id == resumeState.resumeEpisodeId && (episode.id != initialEpisodeId || routeEpisodePlayed),
                    watchProgress = episodeProgress[episode.id] ?: 0f,
                    isWatched = episode.id in watchedEpisodeIds,
                    isNew = episode.id in newEpisodeIds,
                    onClick = {
                        onEpisodeSelected(episode)
                    },
                    // A long press opens the episode's action menu, as on every other row.
                    // Not where the source keeps watched state on its server (Jellyfin).
                    onLongClick = if (mediaRepository.supportsMarkWatched) ({ episodeMenu = episode }) else null,
                )
            }
        }
    }
}

/** Section tabs, in display order. */
private enum class SeriesDetailTab { EPISODES, DETAILS, CAST, MORE_LIKE_THIS }

@Composable
private fun seriesDetailTabLabel(tab: SeriesDetailTab): String =
    when (tab) {
        SeriesDetailTab.EPISODES -> stringResource(R.string.series_episodes_header)
        SeriesDetailTab.DETAILS -> stringResource(R.string.details_tab_details)
        SeriesDetailTab.CAST -> stringResource(R.string.details_tab_cast)
        SeriesDetailTab.MORE_LIKE_THIS -> stringResource(R.string.details_more_like_this)
    }

/**
 * Details tab: director, the stream-name picker (other cached copies of this series), TMDB id, then
 * the category button. Cast has its own tab ([CastChipsTabContent]); episodes are in the Episodes
 * tab.
 */
@Composable
private fun SeriesDetailsTabContent(
    seriesDetail: SeriesDetail,
    seriesName: String,
    alternateStreams: List<MediaItem>,
    sourceNames: Map<Long, String>,
    sourceName: String?,
    categoryName: String?,
    onAlternateStreamSelected: (MediaItem) -> Unit,
    onCategorySelected: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Label / value rows, every value starting at the same place (phone UI audit, #2).
        Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
            seriesDetail.metadata.director?.let { MobileDetailRow(label = stringResource(R.string.details_label_director), value = it) }
            titleFactRows(seriesDetail.metadata.facts, seriesDetail.name.ifBlank { seriesName }, isShow = true).forEach {
                MobileDetailRow(label = it.label, value = it.value)
            }
            // A child's page has no technical rows (UX-D5).
            if (!rememberChildProfile()) {
                MobileDetailRow(label = stringResource(R.string.details_label_stream_name)) {
                    StreamNamePicker(
                        currentName = seriesName,
                        alternates = alternateStreams,
                        sourceNames = sourceNames,
                        onSelect = onAlternateStreamSelected,
                    )
                }
            }
            // The TMDB id is for a Nerd only.
            if (LocalNerd.current) {
                MobileDetailRow(
                    label = stringResource(R.string.details_label_tmdb),
                    value = seriesDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none),
                )
            }
            // For a Nerd only, like the source badges.
            if (LocalNerd.current) {
                sourceName?.let {
                    MobileDetailRow(
                        label = stringResource(R.string.details_label_source),
                        value = it,
                    )
                }
            }
            categoryName?.let { MobileCategoryLinkRow(categoryName = it, onClick = onCategorySelected) }
        }
    }
}

/** Cast tab without TMDB's credits (CT-D7): the source's comma string split into plain chips, not tappable. */
@Composable
private fun CastChipsTabContent(cast: String) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
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
 * The value of the Details tab's "Stream name" row. Plain text when [alternates] is empty (most
 * titles). A dropdown when another catalogue entry has the same TMDB id, to open that copy's detail
 * screen.
 */
@Composable
private fun StreamNamePicker(
    currentName: String,
    alternates: List<MediaItem>,
    sourceNames: Map<Long, String>,
    onSelect: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // As the other rows' values (MobileDetailRow): the row already says "Stream name:".

    if (alternates.isEmpty()) {
        CinemaText(
            text = currentName,
            role = TextRole.Body,
            tone = TextTone.Primary,
            modifier = modifier,
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                // look-ok: a picker that opens its list, or a season tab, not a list row
                Modifier.clickable {
                    expanded = true
                },
        ) {
            CinemaText(
                text = currentName,
                role = TextRole.Body,
                tone = TextTone.Primary,
                modifier = Modifier.weight(1f, fill = false),
            )
            // The "opens a picker" mark of every row (Settings' ›).
            CinemaIcon(
                imageVector = CinemaIcons.KeyboardArrowRight,
                contentDescription = stringResource(R.string.details_other_instances),
                tone = TextTone.Accent,
                modifier = Modifier.size(MobileDimensions.iconSmall),
            )
        }
        if (expanded) {
            // The copies of this title: this one marked, each other one opens there; with several
            // sources, each copy's own.
            SettingsPickerDialog(
                title = stringResource(R.string.details_other_instances),
                options = listOf(currentName to null) + alternates.map { it.name to it },
                selected = null,
                onSelect = { alternate ->
                    expanded = false
                    alternate?.let(onSelect)
                },
                onDismiss = { expanded = false },
                // Each copy's source as the badge at the end of its name, as on every row.
                sourceName = { alternate -> alternate?.let { sourceNames[it.providerId] } },
                labelMaxLines = 2,
            )
        }
    }
}

@Composable
private fun EpisodeDetailContent(
    episode: DomainEpisodeItem,
    seriesDetail: SeriesDetail,
    categoryId: String,
    mediaRepository: MediaRepository,
    previousEpisode: DomainEpisodeItem?,
    nextEpisode: DomainEpisodeItem?,
    // The show's TMDB cast (the Cast tab's), after the episode's guest stars.
    showCredits: TitleCredits?,
    onPersonSelected: (personId: String, name: String, crew: Boolean) -> Unit,
    onNavigate: (DomainEpisodeItem) -> Unit,
    onPlay: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
) {
    val extension = episode.extension ?: "mp4"
    val episodeCredits = rememberEpisodeCredits(mediaRepository, seriesDetail.metadata.tmdbId, episode, showCredits)
    val scrollState = rememberScrollState()

    // Back from a person page opened from this cast: the page is new and its cast comes late, so
    // the restored scroll was cut to the short page and it opened at its top. The place left is
    // kept here, and the page goes back to it once it is that long, again when TMDB's cards come.
    var personScroll by rememberSaveable(episode.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(personScroll != null, episodeCredits != null) {
        val target = personScroll ?: return@LaunchedEffect
        withFrameNanos { }
        withTimeoutOrNull(PERSON_RETURN_WAIT_MS) { snapshotFlow { scrollState.maxValue }.first { it >= target } }
        scrollState.scrollTo(target)
    }

    // The resume position. Keyed on the episode, so a step to another one clears the old value at
    // once; the lookup always assigns, so an episode with nothing to resume resets it.
    var resumePositionMs by remember(episode.id) { mutableStateOf(0L) }

    LaunchedEffect(episode.id) {
        val watched = mediaRepository.getPlaybackPositionSuspend(episode.id, ContentType.TV_SHOWS)
        resumePositionMs = watched?.resumeProgress()?.let { watched.playbackPosition } ?: 0L
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(episode.id, previousEpisode, nextEpisode) {
                    var dragAmount = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragAmount = 0f },
                        onDragEnd = {
                            if (dragAmount > EPISODE_SWIPE_THRESHOLD_PX && previousEpisode != null) {
                                onNavigate(previousEpisode)
                            } else if (dragAmount < -EPISODE_SWIPE_THRESHOLD_PX && nextEpisode != null) {
                                onNavigate(nextEpisode)
                            }
                        },
                        onHorizontalDrag = { change, delta ->
                            dragAmount += delta
                            change.consume()
                        },
                    )
                }.verticalScroll(scrollState)
                // Ends with the bar's height so the last lines scroll clear of it.
                .padding(
                    start = CinemaSpacing.md,
                    top = CinemaSpacing.md,
                    end = CinemaSpacing.md,
                    bottom = CinemaSpacing.md + LocalDockInset.current,
                ),
    ) {
        CinemaThumbnail(
            url = episode.thumbnailUrl ?: seriesDetail.coverUrl,
            fallbackLetter = episode.title.firstOrNull(),
            contentType = ThumbnailContentType.TV_SHOW,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(MobileDimensions.posterHeightLarge),
        )

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // Episode title: the provider's often only repeats the show and the number ("EN - Show -
        // S01E22").
        CinemaText(
            text = episodeOwnTitle(episode.title).ifBlank { stringResource(R.string.series_episode_label, episode.episodeNumber) },
            role = TextRole.Display,
            tone = TextTone.Inherit,
        )

        val seasonLabel = episode.seasonNumber?.let { "S${it.toString().padStart(2, '0')}" } ?: ""
        val episodeLabel = "E${episode.episodeNumber.toString().padStart(2, '0')}"
        val subLabel =
            listOfNotNull(
                seasonLabel.ifEmpty { null },
                episodeLabel,
            ).joinToString(" ")
        CinemaText(
            text = subLabel,
            role = TextRole.Heading,
            tone = TextTone.Secondary,
        )

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        seriesDetail.metadata.genre?.let { genre ->
            CinemaText(
                text = genre,
                role = TextRole.Heading,
                tone = TextTone.Secondary,
            )
        }

        // One dot-separated meta row, as in the series header above.
        val contentRating = episode.metadata.contentRating ?: seriesDetail.metadata.contentRating
        val rating = episode.metadata.rating ?: seriesDetail.metadata.rating
        val year =
            episode.metadata.year ?: episode.metadata.airDate
                ?.take(4)
                ?.toIntOrNull() ?: seriesDetail.metadata.year
        val endsAtContext = LocalContext.current
        val endsAtText =
            remember(episode.metadata.duration, resumePositionMs) {
                computeEndsAt(endsAtContext, episode.metadata.duration, resumePositionMs)
            }
        val episodeMetaSegments =
            listOfNotNull<@Composable () -> Unit>(
                year?.let { { MetaText("$it") } },
                rating?.let { { MetaText(ratingOutOfTen(it)) } },
                contentRating?.let { { MetaBadge(it) } },
                episode.metadata.duration
                    ?.takeIf(::hasMeaningfulDuration)
                    ?.let { { MetaText(formatDuration(it)) } },
                endsAtText?.let { { MetaText(stringResource(R.string.movie_ends_at_format, it)) } },
            )
        if (episodeMetaSegments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            MetaLine(segments = episodeMetaSegments)
        }

        Spacer(modifier = Modifier.height(CinemaSpacing.lg))

        val hasResume = resumePositionMs > 0L
        CinemaButton(
            onClick = {
                onPlay(episode.id, episode.title, extension, false)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            PlayButtonLabel(
                if (hasResume) {
                    stringResource(R.string.movie_resume_from_format, formatTime(resumePositionMs))
                } else {
                    stringResource(R.string.series_play_episode_action)
                },
            )
        }

        // Start over and the trailer, as on the film and show pages: icon actions under Play, the
        // label in a tooltip. The trailer is the show's: Xtream and Jellyfin carry one per series.
        val trailer = seriesDetail.metadata.trailerUrl
        if (hasResume || trailer != null) {
            val playTrailer = rememberPlayTrailer()
            Spacer(modifier = Modifier.height(CinemaSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (hasResume) {
                    DetailIconAction(
                        icon = CinemaIcons.Replay,
                        label = stringResource(R.string.player_osd_start_over),
                        onClick = { onPlay(episode.id, episode.title, extension, true) },
                    )
                }
                trailer?.let {
                    DetailIconAction(
                        icon = CinemaIcons.Movie,
                        label = stringResource(R.string.details_watch_trailer),
                        onClick = { playTrailer(it, seriesDetail.metadata.tmdbTrailerUrl) },
                    )
                }
            }
        }

        episode.metadata.plot?.let { plotText ->
            Spacer(modifier = Modifier.height(CinemaSpacing.lg))
            CinemaText(
                text = plotText,
                role = TextRole.Body,
                tone = TextTone.Inherit,
            )
        }

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // The episode's cast (the episode cast plan, EC-D1): its guest stars and the show's cast, each
        // under its own heading, as the Cast tab's cards; without TMDB's, the source's names as the
        // tab's chips.
        val sourceCast = (episode.metadata.cast ?: seriesDetail.metadata.cast)?.takeIf { it.isNotBlank() }
        // Each episode's own cards: a swipe to the next one starts them at the first actor.
        key(episode.id) {
            if (episodeCredits != null) {
                listOfNotNull(
                    episodeCredits.guests?.let { R.string.episode_guest_stars to it },
                    episodeCredits.regulars?.let { R.string.episode_series_cast to it },
                ).forEach { (heading, credits) ->
                    CinemaText(text = stringResource(heading), role = TextRole.Heading)
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                    MobileCreditsTabContent(
                        credits = credits,
                        section = ThumbnailContentType.TV_SHOW,
                        onPersonSelected = { personId, name, crew ->
                            personScroll = scrollState.value
                            onPersonSelected(personId, name, crew)
                        },
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.md))
                }
            } else if (sourceCast != null) {
                CinemaText(text = stringResource(R.string.details_tab_cast), role = TextRole.Heading)
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                CastChipsTabContent(cast = sourceCast)
                Spacer(modifier = Modifier.height(CinemaSpacing.md))
            }
        }

        val director = episode.metadata.director ?: seriesDetail.metadata.director
        director?.let {
            CinemaText(
                text = stringResource(R.string.movie_director_format, it),
                role = TextRole.BodySmall,
                tone = TextTone.Secondary,
            )
        }

        // TMDB id, for a Nerd only: the episode's own, else the show's.
        if (LocalNerd.current) {
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
            CinemaText(
                text =
                    stringResource(
                        R.string.details_tmdb_format,
                        episode.metadata.tmdbId ?: seriesDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none),
                    ),
                role = TextRole.BodySmall,
                tone = TextTone.Secondary,
            )
        }

        // A child's page has no technical rows: container, bitrate (UX-D5).
        val child = rememberChildProfile()
        episode.extension?.takeIf { it.isNotBlank() && !child }?.let { ext ->
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
            CinemaText(
                text = "${stringResource(R.string.tech_container_label)} ${ext.uppercase()}",
                role = TextRole.BodySmall,
                tone = TextTone.Secondary,
            )
        }

        episode.metadata.airDate?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
            CinemaText(
                text = stringResource(R.string.series_aired_format, displayDate(it)),
                role = TextRole.BodySmall,
                tone = TextTone.Secondary,
            )
        }

        episode.metadata.bitrate?.takeIf { it > 0 && !child }?.let {
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
            CinemaText(
                text = stringResource(R.string.series_bitrate_format, it),
                role = TextRole.BodySmall,
                tone = TextTone.Secondary,
            )
        }
    }
}

/**
 * One row of season tabs (the current one marked by an accent bar, as the page's tabs); a tap
 * switches [selectedSeason]. It scrolls sideways, so many seasons never wrap. The row runs to the
 * screen's edges with the pills inset by the list's padding, so the first pill lines up with the
 * content and the last ones slide off the edge; the selected season is scrolled into view (phone UI
 * audit, #3).
 */
@Composable
private fun SeasonTabs(
    // How far down to draw the row (pixels), so that pinned it sits below the top bar.
    pinShift: () -> Int,
    seasons: List<SeasonInfo>,
    selectedSeason: Int?,
    onSeasonSelected: (Int) -> Unit,
    onSeasonLongPress: ((Int) -> Unit)? = null,
) {
    val rowState = rememberLazyListState()
    val selectedIndex = seasons.indexOfFirst { it.seasonNumber == selectedSeason }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) return@LaunchedEffect
        // After the row's first layout, so a season already on screen isn't scrolled to.
        val layout = snapshotFlow { rowState.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
        val shown = layout.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        val fullyShown =
            shown != null &&
                shown.offset >= layout.viewportStartOffset &&
                shown.offset + shown.size <= layout.viewportEndOffset
        if (!fullyShown) rowState.animateScrollToItem(selectedIndex)
    }
    val background = CinemaTheme.colors.background
    val accent = CinemaTheme.colors.accent
    val tabBar = MobileDimensions.tabIndicatorHeight
    val bleedStep = CinemaSpacing.md
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = CinemaSpacing.md),
        modifier =
            Modifier
                // Above the episode cards: moved down by the shift, it overlaps the items after it,
                // which the list draws later, so without this they covered the pinned row.
                .zIndex(1f)
                .offset { IntOffset(0, pinShift()) }
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    // Out through the LazyColumn's side padding to the screen's edges.
                    val bleed = bleedStep.roundToPx()
                    val placeable =
                        measurable.measure(
                            constraints.copy(
                                minWidth = constraints.maxWidth + bleed * 2,
                                maxWidth = constraints.maxWidth + bleed * 2,
                            ),
                        )
                    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
                }
                // The space the row moved down is filled too, so no episode card shows between
                // the top bar and the pinned row.
                .drawBehind {
                    val shift = pinShift().toFloat()
                    if (shift > 0f) drawRect(background, topLeft = Offset(0f, -shift), size = Size(size.width, shift))
                }
                // Opaque: the row is pinned (stickyHeader), so episode cards scroll under it and
                // must not show through.
                .panel(PanelFill.Ground)
                .padding(vertical = CinemaSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
    ) {
        items(seasons, key = { it.seasonNumber }) { season ->
            val isSelected = season.seasonNumber == selectedSeason
            // Long-press: the season's menu (mark it watched or unwatched).
            Box {
                // A tab as the page's tabs and TV's season tabs: the label in the Heading role
                // (TvInputDefaults.tabLabelRole), the current one in the text colour over an accent
                // bar. Accent text on an accent fill was hard to read.
                CinemaText(
                    text = stringResource(R.string.series_season_name_format, season.seasonNumber),
                    role = TextRole.Heading,
                    emphasis = isSelected,
                    tone = if (isSelected) TextTone.Primary else TextTone.Secondary,
                    modifier =
                        Modifier
                            .drawBehind {
                                if (isSelected) {
                                    val bar = tabBar.toPx()
                                    drawRect(accent, topLeft = Offset(0f, size.height - bar), size = Size(size.width, bar))
                                }
                            }
                            // look-ok: a picker that opens its list, or a season tab, not a list row
                            .combinedClickable(
                                role = Role.Button,
                                onLongClick = onSeasonLongPress?.let { { it(season.seasonNumber) } },
                            ) { onSeasonSelected(season.seasonNumber) }
                            .padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm),
                )
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: DomainEpisodeItem,
    isContinueWatching: Boolean = false,
    watchProgress: Float = 0f,
    isWatched: Boolean = false,
    isNew: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    CinemaCard(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClick?.let { stringResource(R.string.row_more_actions) },
        modifier = Modifier.fillMaxWidth(),
        border =
            if (isContinueWatching) {
                BorderStroke(MobileDimensions.strokeWidth, CinemaTheme.colors.accent)
            } else {
                cinemaCardHairlineBorder()
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(CinemaSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            CinemaThumbnail(
                url = episode.thumbnailUrl,
                fallbackLetter = episode.title.firstOrNull(),
                contentType = ThumbnailContentType.TV_SHOW,
                modifier =
                    Modifier.size(
                        width = MobileDimensions.posterWidth,
                        height = MobileDimensions.posterHeight,
                    ),
            )

            Spacer(modifier = Modifier.width(CinemaSpacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                if (isContinueWatching) {
                    CinemaText(
                        text = stringResource(R.string.series_continue_watching_badge),
                        role = TextRole.Caption,
                        tone = TextTone.Accent,
                    )
                }
                // Episode number, with the watched check beside it: the 40dp thumbnail is too short
                // for the check as an overlay (as the TV card has).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs),
                ) {
                    CinemaText(
                        text = stringResource(R.string.series_episode_number_short, episode.episodeNumber),
                        role = TextRole.Label,
                        tone = TextTone.Secondary,
                    )
                    if (isNew) NewEpisodeMark()
                    // The watched check only shows; the row's long press marks it (its action menu).
                    if (isWatched) {
                        CinemaIcon(
                            imageVector = CinemaIcons.CheckCircle,
                            contentDescription = stringResource(R.string.content_watched_badge),
                            tone = TextTone.Success,
                            modifier = Modifier.size(MobileDimensions.iconSmall),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(CinemaSpacing.xxs))

                // Episode title: nothing when the provider's only repeats the show and the number.
                val ownTitle = remember(episode.title) { episodeOwnTitle(episode.title) }
                if (ownTitle.isNotBlank()) {
                    CinemaText(
                        text = ownTitle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        role = TextRole.Heading,
                        tone = TextTone.Inherit,
                    )
                }

                episode.metadata.plot?.let { plotText ->
                    Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
                    CinemaText(
                        text = plotText,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        role = TextRole.BodySmall,
                        tone = TextTone.Secondary,
                    )
                }

                episode.metadata.duration?.takeIf(::hasMeaningfulDuration)?.let { duration ->
                    Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
                    CinemaText(
                        text = formatDuration(duration),
                        role = TextRole.BodySmall,
                        tone = TextTone.Secondary,
                    )
                }
            }
        }

        // Resume progress, card-width at the bottom edge, as the stream card in
        // MobileCategoryListScreen. Poster width was too short to read.
        if (watchProgress > 0f) {
            CinemaProgressBar(
                progress = { watchProgress.coerceIn(0f, 1f) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // Clear the continue-watching border, which is drawn over the card edge.
                        .padding(bottom = MobileDimensions.strokeWidth)
                        .height(MobileDimensions.resumeBarHeight),
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            CinemaSpinner()
            CinemaText(
                stringResource(R.string.series_loading_episodes),
                role = TextRole.Body,
                tone = TextTone.Inherit,
            )
        }
    }
}

private const val EPISODE_SWIPE_THRESHOLD_PX = 80f

private const val SEASON_TABS_KEY = "season_tabs"

/** How long the episode's page waits for its cast before it goes back to the place left for a person. */
private const val PERSON_RETURN_WAIT_MS = 2_000L
