package org.njarasoa.fijerena.feature.episode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaBadge
import org.njarasoa.fijerena.core.ui.components.CinemaProgressBar
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.RetryWhenOnline
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.TitleLogoOrText
import org.njarasoa.fijerena.core.ui.components.rememberChildProfile
import org.njarasoa.fijerena.core.ui.history.LocalShowSourceBadges
import org.njarasoa.fijerena.core.ui.history.rememberSourceName
import org.njarasoa.fijerena.core.ui.person.rememberEpisodeCredits
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.trailer.rememberPlayTrailer
import org.njarasoa.fijerena.core.ui.utils.displayDate
import org.njarasoa.fijerena.core.ui.utils.titleFactRows
import org.njarasoa.fijerena.core.ui.viewmodels.PlayedSeries
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModelFactory
import org.njarasoa.fijerena.core.ui.wanted.rememberWishlist
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.CinemaTabRow
import org.njarasoa.fijerena.ui.components.ExpandablePlot
import org.njarasoa.fijerena.ui.components.MetaBadge
import org.njarasoa.fijerena.ui.components.MetaLine
import org.njarasoa.fijerena.ui.components.MetaText
import org.njarasoa.fijerena.ui.components.MobileCategoryLinkRow
import org.njarasoa.fijerena.ui.components.MobileCreditsTabContent
import org.njarasoa.fijerena.ui.components.MobileDetailHero
import org.njarasoa.fijerena.ui.components.MobileDetailRow
import org.njarasoa.fijerena.ui.components.OtherCopiesList
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.SavedCopyNote
import org.njarasoa.fijerena.ui.components.WishlistRemovePrompt
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.buttons.DetailIconAction
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.components.cards.cinemaCardHairlineBorder
import org.njarasoa.fijerena.ui.components.detailBackBacking
import org.njarasoa.fijerena.ui.components.detailTopBarColors
import org.njarasoa.fijerena.ui.components.mobileClockTopBarInsets
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

    // Retained across a background refresh so the list stays visible with just a pull-spinner
    // (PullToRefreshBox below) instead of flashing to a full-screen loading state.
    var lastSuccess by remember { mutableStateOf<SeriesDetailsViewModel.UiState.Success?>(null) }
    if (uiState is SeriesDetailsViewModel.UiState.Success) {
        lastSuccess = uiState as SeriesDetailsViewModel.UiState.Success
    }
    val displaySeriesDetail = lastSuccess?.seriesDetail
    val isRefreshing = uiState is SeriesDetailsViewModel.UiState.Loading && lastSuccess != null

    // The episode whose page opened a person page from its cast: Back from that page shows the
    // episode's page again (the navigation round trip drops every plain remember).
    var personReturnEpisodeId by rememberSaveable { mutableStateOf<String?>(null) }

    // Selected episode for detail panel — only set by an explicit tap (including on the
    // Continue Watching resume episode below); arriving here never auto-opens it, except on the
    // way back from a person page opened from the episode's page.
    var selectedEpisode by remember {
        mutableStateOf(
            personReturnEpisodeId?.let { id ->
                displaySeriesDetail?.episodes?.values?.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == id } }
            },
        )
    }
    // Used once: a later return (from the player) opens on the list again.
    LaunchedEffect(Unit) { personReturnEpisodeId = null }
    val closeEpisode: () -> Unit = {
        selectedEpisode = null
        personReturnEpisodeId = null
    }

    // Episode/season this screen is anchored on, and whether that season was the user's own
    // pick — see EpisodeResumeState.kt. Hoisted above both EpisodeListContent and
    // EpisodeDetailContent (both disposed by the other's toggle, and by navigating to the
    // player) — a plain remember, or state scoped to just one of the two content composables,
    // would drop it and land the user back on season 1 episode 1 at the top of the list.
    // initialSeason is best-effort: seriesDetail is usually already cached and available by
    // this first composition, but on a genuinely cold load it isn't yet, and this is a
    // rememberSaveable initial value — evaluated once, never revisited once seriesDetail
    // arrives. EpisodeListContent's own anchor effect (keyed on seriesDetail) corrects it
    // shortly after in that rare case, same as it does for a series with no resume episode at
    // all; the only cost is one frame showing season 1 first instead of the resume season.
    val resumeState =
        rememberEpisodeResumeState(
            seriesId = seriesId,
            initialEpisodeId = initialEpisodeId,
            initialSeason = initialEpisodeId?.let { lastSuccess?.seriesDetail?.seasonNumberContaining(it) } ?: initialSeason,
        )

    BackHandler(enabled = selectedEpisode != null) {
        closeEpisode()
    }

    // The episode list opens on the backdrop under a clear top bar (TV visual refresh, Phase 5);
    // the bar turns solid with the title once the backdrop has scrolled up under it, and stays
    // solid on an episode's own page and while there is no list to show (loading, error).
    var heroCovered by remember { mutableStateOf(false) }
    val showsList = displaySeriesDetail != null && selectedEpisode == null
    val solidBar = heroCovered || !showsList

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = mobileClockTopBarInsets(),
                colors = detailTopBarColors(solidBar),
                // TMDB's clean title once it resolves, the provider's raw stream name until then
                title = {
                    if (solidBar) {
                        CinemaText(
                            tmdbTitle ?: cleanTitle(lastSuccess?.streamName ?: seriesName),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            role = TextRole.Title,
                            tone = TextTone.Inherit,
                        )
                    }
                },
                navigationIcon = {
                    CinemaIconButton(
                        modifier = Modifier.detailBackBacking(solidBar),
                        onClick = {
                            if (selectedEpisode != null) {
                                closeEpisode()
                            } else {
                                onBack()
                            }
                        },
                        icon = {
                            Icon(CinemaIcons.ArrowBack, stringResource(R.string.common_back), tint = CinemaTheme.colors.textPrimary)
                        },
                    )
                },
                // Favourite is in the icon row under the Play/Resume button (see EpisodeListContent),
                // the Plex/Netflix "actions under the poster" layout, not a top-bar icon.
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(CinemaTheme.colors.background)
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
                    RetryWhenOnline { viewModel.loadSeriesInfo() }
                    ErrorScreen(
                        message = errorState.message,
                        onBack = onBack,
                        otherCopies = {
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
                    PullToRefreshBox(
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
                            categoryName = lastSuccess?.categoryName,
                            logoUrl = logoUrl,
                            backdropUrl = backdropUrl,
                            onHeroCovered = { heroCovered = it },
                            isFavorite = isFavorite,
                            onToggleFavorite = { viewModel.toggleFavorite(lastSuccess?.streamName ?: seriesName) },
                            onPlayEpisode = { episodeId, episodeTitle, extension, startFromBeginning ->
                                resumeState.setResumeEpisode(episodeId, season = null)
                                onEpisodeSelected(episodeId, episodeTitle, extension, startFromBeginning, viewModel.playedSeries())
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
    // The source's name, a Source row in Expert mode.
    sourceName: String?,
    seriesName: String,
    mediaRepository: MediaRepository,
    resumeState: EpisodeResumeState,
    // The episode the route opened the screen on (Continue Watching, an actor's episode).
    initialEpisodeId: String?,
    categoryName: String?,
    logoUrl: String?,
    backdropUrl: String?,
    onHeroCovered: (Boolean) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayEpisode: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
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

    // The anchor a season or show mark just moved Play to (markEpisodesWatched below): the list
    // doesn't scroll to it, staying where the user pressed the mark.
    var anchorMovedByMark by remember { mutableStateOf<String?>(null) }

    // Scroll to the resume episode, but only when it's actually the reason this season is
    // selected — a manual tab tap or swipe must never yank the list back to the resume spot (or
    // anywhere else); it stays exactly where the user left it.
    LaunchedEffect(resumeState.resumeEpisodeId) {
        val targetId = resumeState.resumeEpisodeId ?: return@LaunchedEffect
        if (targetId == anchorMovedByMark) return@LaunchedEffect
        if (seriesDetail.seasonNumberContaining(targetId) != resumeState.selectedSeason) return@LaunchedEffect
        val seasonEpisodes = sortedEpisodesBySeason[resumeState.selectedSeason?.toString()] ?: return@LaunchedEffect
        val episodeIndex = seasonEpisodes.indexOfFirst { it.id == targetId }
        if (episodeIndex < 0) return@LaunchedEffect
        val headerItemCount = 1 + if (hasMultipleSeasons) 1 else 0
        listState.animateScrollToItem(headerItemCount + episodeIndex)
    }

    var episodeProgress by remember(seriesDetail) { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var episodePlaybackPositions by remember(seriesDetail) { mutableStateOf<Map<String, Long>>(emptyMap()) }

    // The route's episode is the resume episode only once it has been played: an actor's episode
    // opens the screen on it, but it is not where the viewer stopped, so it gets no "Continue
    // watching" (the episode cast plan). Read again on each entry, a return from the player too.
    var routeEpisodePlayed by remember { mutableStateOf(false) }
    LaunchedEffect(initialEpisodeId) {
        routeEpisodePlayed = initialEpisodeId != null && wasPlayed(mediaRepository, initialEpisodeId)
    }
    var watchedEpisodeIds by remember(seriesDetail) { mutableStateOf<Set<String>>(emptySet()) }
    // The Wishlist icon and its ask-once prompt (wishlist UX plan, Phase 1): any watched episode
    // counts as watching the show. None for a child.
    val wishlistTitle = tmdbTitle ?: cleanTitle(seriesDetail.name.ifEmpty { seriesName })
    val wishlist =
        rememberWishlist(
            contentType = ContentType.TV_SHOWS,
            tmdbId = seriesDetail.metadata.tmdbId,
            title = wishlistTitle,
            year = extractYear(seriesDetail.metadata.year, seriesDetail.metadata.releaseDate, seriesDetail.name),
            watched = watchedEpisodeIds.isNotEmpty(),
        )
    if (wishlist?.askRemove == true) WishlistRemovePrompt(wishlistTitle, wishlist.answer)

    // Re-reads progress/watched (including TMDB siblings) for every episode of this series.
    // Called after a manual mark, not just on initial load — a single-episode optimistic patch
    // would miss a sibling that the mark just made completed, and wouldn't restore a resume bar
    // an unmark brings back. Returns this series' own rows, for markEpisodesWatched's re-anchor.
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
    // unwatched (the season show watched plan). Hidden for a source that keeps
    // watched state on its server (Jellyfin). The wording follows episodesWatchState: "unwatched"
    // only when every episode in scope is already watched.
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
                    markManyError = friendlyErrorMessage(it, context, AppSettings(context.applicationContext).isDevMode)
                }
            val allWatched = refreshEpisodeWatchState()
            // Play and the "Continue watching" badge move on to the first unwatched episode at once
            // (the watched followups plan → Phase 1); the selected season stays.
            // A single-episode toggle doesn't come through here, so it leaves the anchor alone.
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
    val openSeasonMenu: (Int) -> Unit = { season ->
        watchedToggleScope.launch {
            seasonMenu = season to mediaRepository.episodesWatchState(seriesDetail.id, season)
        }
    }
    markManyError?.let { message ->
        CinemaAlertDialog(
            onDismissRequest = { markManyError = null },
            title = {
                CinemaText(
                    stringResource(R.string.watched_mark_many_failed),
                    role = TextRole.Title,
                    tone = TextTone.Inherit,
                )
            },
            text = {
                CinemaText(
                    message,
                    role = TextRole.Body,
                    tone = TextTone.Inherit,
                )
            },
            confirmButton = {
                CinemaDialogActionButton(onClick = { markManyError = null }) {
                    CinemaText(
                        stringResource(R.string.common_ok),
                        role = TextRole.Label,
                        tone = TextTone.Inherit,
                    )
                }
            },
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
                // TMDB dedup (Phase 5): completing one language/quality variant of an episode
                // completes them all. Checks only — never adds a resume bar or moves the anchor
                // below, same as the movies side of this.
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
            // Only one season, so no "which season" guess needed — still must seed
            // selectedSeason or the episode list below stays keyed off null forever.
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

    // Segmented detail sections (the ui ux transitions flow uplift plan, Phase
    // 4 3c) — hoisted above the LazyColumn (not declared inside the hero item below) because both
    // the hero item's TabRow and the LazyColumn's own conditional stickyHeader/items need it.
    // Episodes is the always-present, always-first tab.
    // TMDB's credits when it has them (cast tab plan), else the source's cast names (CT-D7).
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
                // Horizontal swipe anywhere in the list switches season — the touch equivalent
                // of tapping a season tab. Vertical scrolling is untouched: this only fires on
                // a horizontal drag, same technique EpisodeDetailContent below uses for
                // prev/next episode. Gated on the Episodes detail-tab too — without it, a swipe
                // made while looking at Details/Cast/More Like This silently changed the season
                // selection in the background, invisible until switching back to Episodes.
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
                    onCovered = onHeroCovered,
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

                // Single dot-separated facts line, wrapping on a narrow phone (see
                // MobileMovieDetailsScreen for the same treatment): the content rating an
                // outlined badge, everything else plain text, in TV's order.
                val presentLabel = stringResource(R.string.series_present)
                val yearRange = seriesDetail.seriesYearRange(presentLabel)
                val countText =
                    if (sortedSeasons.size > 1) {
                        stringResource(R.string.series_seasons_and_episodes_format, sortedSeasons.size, totalEpisodes)
                    } else {
                        stringResource(R.string.series_total_episodes_format, totalEpisodes)
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

                // Two lines (phone UI audit, #3): what the button does with the episode's code
                // ("▶ Resume S02E10 · 19:04 left", "▶ Play S01E01"), then the episode's own title
                // under it. No code when the provider numbers episodes 0.
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
                        CinemaText(
                            actionText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            role = TextRole.Label,
                            tone = TextTone.Inherit,
                        )
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

                // Secondary actions row — only actions the app actually supports (no Cast/Shuffle).
                Spacer(modifier = Modifier.height(CinemaSpacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    DetailIconAction(
                        icon = if (isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                        label = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
                        onClick = onToggleFavorite,
                        tint = if (isFavorite) CinemaTheme.colors.accent else CinemaTheme.colors.textPrimary,
                    )
                    if (wishlist != null) {
                        DetailIconAction(
                            icon = if (wishlist.isOn) CinemaIcons.Bookmark else CinemaIcons.BookmarkBorder,
                            label = stringResource(if (wishlist.isOn) R.string.wanted_remove else R.string.wanted_add),
                            onClick = wishlist.toggle,
                            tint = if (wishlist.isOn) CinemaTheme.colors.accent else CinemaTheme.colors.textPrimary,
                        )
                    }
                    if (hasResume) {
                        DetailIconAction(
                            icon = CinemaIcons.Replay,
                            label = stringResource(R.string.movie_start_beginning),
                            onClick = {
                                anchorEpisode?.let { ep ->
                                    onPlayEpisode(ep.id, ep.title, ep.extension ?: "mp4", true)
                                }
                            },
                        )
                    }
                    seriesDetail.metadata.trailerUrl?.let { trailer ->
                        DetailIconAction(
                            icon = CinemaIcons.Movie,
                            label = stringResource(R.string.details_watch_trailer),
                            onClick = { playTrailer(trailer, seriesDetail.metadata.tmdbTrailerUrl) },
                        )
                    }
                    if (canMarkManyWatched) {
                        val allWatched = showAllWatched == true
                        DetailIconAction(
                            icon = if (allWatched) CinemaIcons.CheckCircleFilled else CinemaIcons.CheckCircleOutline,
                            label = stringResource(if (allWatched) R.string.watched_unmark_show else R.string.watched_mark_show),
                            onClick = { markEpisodesWatched(null, !allWatched) },
                            tint = if (allWatched) CinemaTheme.colors.accent else CinemaTheme.colors.textPrimary,
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

                // Segmented detail sections — tab state is hoisted above the LazyColumn (see
                // there); this item only reads it.
                Spacer(modifier = Modifier.height(CinemaSpacing.lg))
                CinemaTabRow(
                    selectedIndex = tabs.indexOf(selectedTab),
                    labels = tabs.map { seriesDetailTabLabel(it) },
                    onSelect = { pickedTab = tabs[it] },
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

        // Episodes tab only: season tabs pinned in place as the episode list scrolls under it
        // (stickyHeader, not a plain item), so it reads as the control for what's below rather
        // than scrolling away with it. A horizontal swipe anywhere in this list (see the
        // pointerInput above) does the same thing as tapping a tab.
        if (selectedTab == SeriesDetailTab.EPISODES) {
            if (hasMultipleSeasons) {
                stickyHeader(key = "season_tabs", contentType = "header") {
                    SeasonTabs(
                        seasons = sortedSeasons,
                        selectedSeason = resumeState.selectedSeason,
                        onSeasonSelected = { resumeState.selectSeason(it) },
                        onSeasonLongPress = if (canMarkManyWatched) openSeasonMenu else null,
                        seasonMenu = { season ->
                            val menu = seasonMenu
                            val allWatched = menu?.second == true
                            DropdownMenu(expanded = menu?.first == season, onDismissRequest = { seasonMenu = null }) {
                                DropdownMenuItem(
                                    text = {
                                        CinemaText(
                                            stringResource(
                                                if (allWatched) R.string.watched_unmark_season else R.string.watched_mark_season,
                                            ),
                                            role = TextRole.Label,
                                            tone = TextTone.Inherit,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (allWatched) CinemaIcons.CheckCircleOutline else CinemaIcons.CheckCircleFilled,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        seasonMenu = null
                                        markEpisodesWatched(season, !allWatched)
                                    },
                                )
                            }
                        },
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
                    onClick = {
                        onEpisodeSelected(episode)
                    },
                    onToggleWatched =
                        if (mediaRepository.supportsMarkWatched) {
                            {
                                // Manual watched/unwatched mark (Phase 6,
                                // the watch state durable storage plan). Optimistic: flips this
                                // episode's own badge immediately rather than waiting on the write;
                                // the full re-read after it lands is what catches a TMDB sibling this
                                // mark just completed too (Phase 5) and restores the resume bar on an
                                // unmark — a single-item patch would miss both.
                                val nowWatched = episode.id !in watchedEpisodeIds
                                watchedEpisodeIds =
                                    if (nowWatched) watchedEpisodeIds + episode.id else watchedEpisodeIds - episode.id
                                watchedToggleScope.launch {
                                    mediaRepository.setWatched(episode.id, ContentType.TV_SHOWS, nowWatched)
                                    refreshEpisodeWatchState()
                                }
                            }
                        } else {
                            null
                        },
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
 * Details tab: director, the stream-name picker (alternate cached instances of this
 * series), TMDB id, then the category button. Cast lives in its own tab (see
 * [CastChipsTabContent]); episodes live in the always-present Episodes tab.
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
            // The TMDB id is for developers only.
            val context = LocalContext.current
            val isDevMode = remember { AppSettings(context.applicationContext).isDevMode }
            if (isDevMode) {
                MobileDetailRow(
                    label = stringResource(R.string.details_label_tmdb),
                    value = seriesDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none),
                )
            }
            // Expert mode only, like the source badges.
            if (LocalShowSourceBadges.current) {
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
 * The value of the Details tab's "Stream name" row. Plain text when [alternates] is empty — most
 * titles have no other cached instance. Becomes a tappable dropdown once there is at least one
 * other local catalogue entry sharing the same TMDB id, letting the user switch to that instance's
 * detail screen.
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
    // The row often sits near the bottom of the visible screen; a Popup can't render below the
    // screen edge, so without this the menu flips far above the row to find room instead of
    // opening flush beneath it.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    Box(modifier = modifier.bringIntoViewRequester(bringIntoViewRequester)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.clickable {
                    coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                    expanded = true
                },
        ) {
            CinemaText(
                text = currentName,
                role = TextRole.Body,
                tone = TextTone.Primary,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = CinemaIcons.ArrowDropDown,
                contentDescription = stringResource(R.string.details_other_instances),
                tint = CinemaTheme.colors.accent,
                modifier = Modifier.size(MobileDimensions.iconSmall),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    CinemaText(
                        currentName,
                        role = TextRole.Body,
                        tone = TextTone.Accent,
                    )
                },
                leadingIcon = { Icon(CinemaIcons.CheckCircle, contentDescription = null, tint = CinemaTheme.colors.accent) },
                onClick = { expanded = false },
            )
            alternates.forEach { alternate ->
                DropdownMenuItem(
                    text = {
                        CinemaText(
                            alternate.name,
                            role = TextRole.Label,
                            keepCase = true,
                            tone = TextTone.Inherit,
                        )
                    },
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

    // Back from a person page opened from this cast: the page is new and its cast comes late, so the
    // restored scroll was cut to the short page first and it opened at its top (seen in the walk,
    // 2026-10-30). The place left is kept here, and the page goes back to it once it is that long:
    // again when TMDB's cards come.
    var personScroll by rememberSaveable(episode.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(personScroll != null, episodeCredits != null) {
        val target = personScroll ?: return@LaunchedEffect
        withFrameNanos { }
        withTimeoutOrNull(PERSON_RETURN_WAIT_MS) { snapshotFlow { scrollState.maxValue }.first { it >= target } }
        scrollState.scrollTo(target)
    }

    // Load resume position. Keyed on the episode so stepping to another one clears the previous
    // episode's value immediately rather than showing its resume time until the lookup lands —
    // and the lookup assigns unconditionally, so an episode with nothing to resume resets it
    // instead of leaving the last one's position behind.
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

        // Episode title — the provider's often only repeats the show and the number ("EN - Show - S01E22")
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

        // Single dot-separated meta row — same treatment as the series header above.
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
        if (hasResume) {
            val resumeTimeText = formatTime(resumePositionMs)
            CinemaButton(
                onClick = {
                    onPlay(episode.id, episode.title, extension, false)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                CinemaText(
                    stringResource(R.string.movie_resume_from_format, resumeTimeText),
                    role = TextRole.Label,
                    tone = TextTone.Inherit,
                )
            }
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            CinemaOutlinedButton(
                onClick = {
                    onPlay(episode.id, episode.title, extension, true)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                CinemaText(
                    stringResource(R.string.movie_start_beginning),
                    role = TextRole.Label,
                    tone = TextTone.Inherit,
                )
            }
        } else {
            CinemaButton(
                onClick = {
                    onPlay(episode.id, episode.title, extension, false)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                CinemaText(
                    stringResource(R.string.series_play_episode_action),
                    role = TextRole.Label,
                    tone = TextTone.Inherit,
                )
            }
        }

        // The trailer is the show's, not this episode's — Xtream and Jellyfin only ever
        // carry one per series.
        seriesDetail.metadata.trailerUrl?.let { trailer ->
            val playTrailer = rememberPlayTrailer()
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            CinemaOutlinedButton(
                onClick = { playTrailer(trailer, seriesDetail.metadata.tmdbTrailerUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                CinemaText(
                    stringResource(R.string.details_watch_trailer),
                    role = TextRole.Label,
                    tone = TextTone.Inherit,
                )
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

        // The episode's cast (the episode cast plan, EC-D1): its guest stars, then the show's cast, as
        // the Cast tab's cards; without TMDB's, the source's names as the tab's chips.
        val sourceCast = (episode.metadata.cast ?: seriesDetail.metadata.cast)?.takeIf { it.isNotBlank() }
        if (episodeCredits != null || sourceCast != null) {
            CinemaText(text = stringResource(R.string.details_tab_cast), role = TextRole.Heading)
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            // Each episode's own cards: a swipe to the next one starts it at the first actor.
            key(episode.id) {
                if (episodeCredits != null) {
                    MobileCreditsTabContent(
                        credits = episodeCredits,
                        section = ThumbnailContentType.TV_SHOW,
                        onPersonSelected = { personId, name, crew ->
                            personScroll = scrollState.value
                            onPersonSelected(personId, name, crew)
                        },
                    )
                } else {
                    CastChipsTabContent(cast = sourceCast.orEmpty())
                }
            }
            Spacer(modifier = Modifier.height(CinemaSpacing.md))
        }

        val director = episode.metadata.director ?: seriesDetail.metadata.director
        director?.let {
            CinemaText(
                text = stringResource(R.string.movie_director_format, it),
                role = TextRole.BodySmall,
                tone = TextTone.Secondary,
            )
        }

        // TMDB id, developers only: the episode's own when it has one, else the show's
        val context = LocalContext.current
        val isDevMode = remember { AppSettings(context.applicationContext).isDevMode }
        if (isDevMode) {
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
 * One row of season pills; tapping one switches [selectedSeason]. Horizontally scrollable so a
 * long-running show's season count never wraps the row. The row runs to the screen's edges (out
 * through the list's side padding) with the pills inset by that padding, so the first pill lines
 * up with the content and the last ones slide off the edge — plainly a row that scrolls — rather
 * than being cut short of it; the selected season is scrolled into view (phone UI audit, #3).
 */
@Composable
private fun SeasonTabs(
    seasons: List<SeasonInfo>,
    selectedSeason: Int?,
    onSeasonSelected: (Int) -> Unit,
    onSeasonLongPress: ((Int) -> Unit)? = null,
    seasonMenu: @Composable (season: Int) -> Unit = {},
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
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = CinemaSpacing.md),
        modifier =
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    // Out through the LazyColumn's side padding to the screen's edges.
                    val bleed = CinemaSpacing.md.roundToPx()
                    val placeable =
                        measurable.measure(
                            constraints.copy(
                                minWidth = constraints.maxWidth + bleed * 2,
                                maxWidth = constraints.maxWidth + bleed * 2,
                            ),
                        )
                    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
                }
                // Opaque: this row is pinned via stickyHeader, so episode cards scroll in
                // underneath it and need to actually be hidden, not show through.
                .background(CinemaTheme.colors.background)
                .padding(vertical = CinemaSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
    ) {
        items(seasons, key = { it.seasonNumber }) { season ->
            val isSelected = season.seasonNumber == selectedSeason
            // Long-press: the season's menu (mark it watched or unwatched), anchored on the chip.
            Box {
                CinemaText(
                    text = stringResource(R.string.series_season_name_format, season.seasonNumber),
                    role = TextRole.Heading,
                    emphasis = isSelected,
                    tone = if (isSelected) TextTone.Accent else TextTone.Secondary,
                    modifier =
                        Modifier
                            .background(
                                if (isSelected) CinemaTheme.colors.accent.copy(alpha = CinemaAlpha.textLow) else CinemaTheme.colors.clear,
                                CinemaTheme.shapes.card,
                            ).combinedClickable(
                                role = Role.Button,
                                onLongClick = onSeasonLongPress?.let { { it(season.seasonNumber) } },
                            ) { onSeasonSelected(season.seasonNumber) }
                            .padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm),
                )
                seasonMenu(season.seasonNumber)
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
    onClick: () -> Unit,
    onToggleWatched: (() -> Unit)? = null,
) {
    CinemaCard(
        onClick = onClick,
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
                // Episode number, with the watched check beside it — the 40dp thumbnail is too
                // short to carry the check as an overlay the way the TV card does.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs),
                ) {
                    CinemaText(
                        text = stringResource(R.string.series_episode_number_short, episode.episodeNumber),
                        role = TextRole.Label,
                        tone = TextTone.Secondary,
                    )
                    // Watched toggle (Phase 6, the watch state durable storage plan): the
                    // badge itself is the tap target, since CinemaCard's onClick already owns the
                    // rest of the row for playing the episode.
                    // No toggle where the source keeps watched state on its server: the check only shows.
                    if (onToggleWatched != null) {
                        CinemaIconButton(
                            onClick = onToggleWatched,
                            icon = {
                                Icon(
                                    imageVector = if (isWatched) CinemaIcons.CheckCircle else CinemaIcons.RadioButtonUnchecked,
                                    contentDescription =
                                        if (isWatched) stringResource(R.string.watched_unmark) else stringResource(R.string.watched_mark),
                                    tint = if (isWatched) CinemaTheme.colors.success else CinemaTheme.colors.textSecondary,
                                    modifier = Modifier.size(MobileDimensions.iconSmall),
                                )
                            },
                        )
                    } else if (isWatched) {
                        Icon(
                            imageVector = CinemaIcons.CheckCircle,
                            contentDescription = stringResource(R.string.content_watched_badge),
                            tint = CinemaTheme.colors.success,
                            modifier = Modifier.size(MobileDimensions.iconSmall),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(CinemaSpacing.xxs))

                // Episode title: nothing when the provider's only repeats the show and the number
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

        // Resume progress, card-width at the bottom edge — same placement as the stream card in
        // MobileCategoryListScreen, so a half-watched episode and a half-watched film read the
        // same. Poster-width was too short to be legible.
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

@Composable
private fun ErrorScreen(
    message: String,
    onBack: () -> Unit,
    otherCopies: @Composable () -> Unit = {},
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
            // Scrolls when the other copies run past the screen.
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(CinemaSpacing.xl),
        ) {
            CinemaText(
                text = stringResource(R.string.series_error_loading),
                role = TextRole.Display,
                tone = TextTone.Danger,
            )
            CinemaText(
                text = message,
                role = TextRole.Body,
                tone = TextTone.Inherit,
            )
            CinemaButton(kind = ButtonKind.Other, onClick = onBack) {
                CinemaText(
                    stringResource(R.string.common_back),
                    role = TextRole.Label,
                    tone = TextTone.Inherit,
                )
            }
            otherCopies()
        }
    }
}

private const val EPISODE_SWIPE_THRESHOLD_PX = 80f

/** How long the episode's page waits for its cast before it goes back to the place left for a person. */
private const val PERSON_RETURN_WAIT_MS = 2_000L
