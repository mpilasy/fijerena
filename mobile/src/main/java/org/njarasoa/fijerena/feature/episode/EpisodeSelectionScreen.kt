package org.njarasoa.fijerena.feature.episode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.resumeProgress
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.SeasonInfo
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.domain.firstSeasonWithUnwatchedEpisode
import org.njarasoa.fijerena.core.player.domain.flattenedEpisodes
import org.njarasoa.fijerena.core.player.domain.resumeAnchorEpisodeId
import org.njarasoa.fijerena.core.player.domain.seasonNumberContaining
import org.njarasoa.fijerena.core.player.domain.seriesYearRange
import org.njarasoa.fijerena.core.player.domain.sortedSeasons
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModelFactory
import org.njarasoa.fijerena.core.player.model.computeEndsAt
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.player.model.hasMeaningfulDuration
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.RatingBadge
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.TitleLogoOrText
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.utils.openExternalUrl
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.buttons.DetailIconAction
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.components.cards.cinemaCardHairlineBorder
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.core.player.domain.EpisodeItem as DomainEpisodeItem
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEpisodeSelectionScreen(
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
    val alternateStreams by viewModel.alternateStreams.collectAsStateWithLifecycle()
    val isFavorite = (uiState as? SeriesDetailsViewModel.UiState.Success)?.isFavorite ?: false

    // Retained across a background refresh so the list stays visible with just a pull-spinner
    // (PullToRefreshBox below) instead of flashing to a full-screen loading state.
    var lastSuccess by remember { mutableStateOf<SeriesDetailsViewModel.UiState.Success?>(null) }
    if (uiState is SeriesDetailsViewModel.UiState.Success) {
        lastSuccess = uiState as SeriesDetailsViewModel.UiState.Success
    }
    val displaySeriesDetail = lastSuccess?.seriesDetail
    val isRefreshing = uiState is SeriesDetailsViewModel.UiState.Loading && lastSuccess != null

    // Selected episode for detail panel — only set by an explicit tap (including on the
    // Continue Watching resume episode below); arriving here never auto-opens it.
    var selectedEpisode by remember { mutableStateOf<DomainEpisodeItem?>(null) }

    // Episode to come back to: the route's Continue Watching argument at first, then whichever
    // episode was last sent to the player, or the one derived from watch history below.
    // Saveable and hoisted above the list, because both the detail panel and the player dispose
    // the list — plain remember would drop it and land the user back on season 1 at the top.
    var resumeEpisodeId by rememberSaveable { mutableStateOf(initialEpisodeId) }

    // Handle back press: dismiss detail panel first, then navigate back
    BackHandler(enabled = selectedEpisode != null) {
        selectedEpisode = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // TMDB's clean title once it resolves, the provider's raw stream name until then
                title = { Text(tmdbTitle ?: (lastSuccess?.streamName ?: seriesName)) },
                navigationIcon = {
                    CinemaIconButton(onClick = {
                        if (selectedEpisode != null) {
                            selectedEpisode = null
                        } else {
                            onBack()
                        }
                    },
                        icon = {
                            Icon(CinemaIcons.ArrowBack, stringResource(R.string.common_back), tint = CinemaTextPrimary)
                        }
                    )
                },
                // Favorite moved into the icon row under the Play/Resume button (see
                // EpisodeListContent) — matches the Plex/Netflix "actions under the poster" layout
                // instead of a top-bar icon.
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues),
        ) {
            val errorState = uiState as? SeriesDetailsViewModel.UiState.Error
            when {
                displaySeriesDetail == null && errorState == null -> {
                    LoadingScreen()
                }
                errorState != null -> {
                    ErrorScreen(
                        message = errorState.message,
                        onBack = onBack,
                    )
                }
                displaySeriesDetail != null && selectedEpisode != null -> {
                    val detail = displaySeriesDetail
                    val flatEpisodes =
                        remember(detail) {
                            val sorted = detail.sortedSeasons { num -> context.getString(R.string.series_season_name_format, num) }
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
                        onNavigate = { next -> selectedEpisode = next },
                        onPlay = { episodeId, episodeTitle, extension, startFromBeginning ->
                            resumeEpisodeId = episodeId
                            onEpisodeSelected(episodeId, episodeTitle, extension, startFromBeginning)
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
                            relatedTitles = relatedTitles,
                            tmdbTitle = tmdbTitle,
                            alternateStreams = alternateStreams,
                            seriesName = lastSuccess?.streamName ?: seriesName,
                            mediaRepository = viewModel.mediaRepository!!,
                            resumeEpisodeId = resumeEpisodeId,
                            onResumeEpisodeDerived = { resumeEpisodeId = it },
                            categoryName = lastSuccess?.categoryName,
                            logoUrl = logoUrl,
                            isFavorite = isFavorite,
                            onToggleFavorite = { viewModel.toggleFavorite(lastSuccess?.streamName ?: seriesName) },
                            onPlayEpisode = { episodeId, episodeTitle, extension, startFromBeginning ->
                                resumeEpisodeId = episodeId
                                onEpisodeSelected(episodeId, episodeTitle, extension, startFromBeginning)
                            },
                            onEpisodeSelected = { episode ->
                                selectedEpisode = episode
                            },
                            onCategorySelected = { onCategorySelected(lastSuccess?.categoryId ?: categoryId) },
                            onRelatedTitleSelected = onRelatedTitleSelected,
                            onAlternateStreamSelected = { viewModel.switchToAlternateStream(it) },
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
    relatedTitles: RelatedTitles,
    tmdbTitle: String?,
    alternateStreams: List<MediaItem>,
    seriesName: String,
    mediaRepository: MediaRepository,
    resumeEpisodeId: String? = null,
    onResumeEpisodeDerived: (String) -> Unit,
    categoryName: String?,
    logoUrl: String?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayEpisode: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onEpisodeSelected: (DomainEpisodeItem) -> Unit,
    onCategorySelected: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit,
    onAlternateStreamSelected: (MediaItem) -> Unit,
) {
    val context = LocalContext.current
    val watchedToggleScope = rememberCoroutineScope()
    val sortedSeasons =
        remember(seriesDetail) {
            seriesDetail.sortedSeasons { num -> context.getString(R.string.series_season_name_format, num) }
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

    val resumeSeasonNumber =
        remember(seriesDetail, resumeEpisodeId) {
            resumeEpisodeId?.let { seriesDetail.seasonNumberContaining(it) }
        }

    // One season visible at a time, switched via tabs or a horizontal swipe instead of an
    // accordion — resume season wins on first load, same priority the accordion used to give it.
    var selectedSeasonNumber by rememberSaveable(seriesDetail.id) {
        mutableStateOf(resumeSeasonNumber ?: sortedSeasons.firstOrNull()?.seasonNumber)
    }
    // Set by a manual tab tap or swipe, so the auto-select effect below doesn't clobber a season
    // the user already picked while the playback-position lookup was in flight. Seeded true when
    // a resume season is already known, for the same reason the accordion seeded it.
    var hasManuallySelectedSeason by remember(seriesDetail.id) { mutableStateOf(resumeSeasonNumber != null) }

    val currentSeasonIndex = sortedSeasons.indexOfFirst { it.seasonNumber == selectedSeasonNumber }
    val previousSeason = if (currentSeasonIndex > 0) sortedSeasons[currentSeasonIndex - 1] else null
    val nextSeason =
        if (currentSeasonIndex in sortedSeasons.indices && currentSeasonIndex < sortedSeasons.lastIndex) {
            sortedSeasons[currentSeasonIndex + 1]
        } else {
            null
        }

    val listState = rememberLazyListState()

    // Scroll to the resume episode, but only when it's actually the reason this season is
    // selected — a manual tab tap or swipe must never yank the list back to the resume spot (or
    // anywhere else); it stays exactly where the user left it.
    LaunchedEffect(resumeEpisodeId) {
        val targetId = resumeEpisodeId ?: return@LaunchedEffect
        if (seriesDetail.seasonNumberContaining(targetId) != selectedSeasonNumber) return@LaunchedEffect
        val seasonEpisodes = sortedEpisodesBySeason[selectedSeasonNumber?.toString()] ?: return@LaunchedEffect
        val episodeIndex = seasonEpisodes.indexOfFirst { it.id == targetId }
        if (episodeIndex < 0) return@LaunchedEffect
        val headerItemCount = 1 + if (hasMultipleSeasons) 1 else 0
        listState.animateScrollToItem(headerItemCount + episodeIndex)
    }

    var episodeProgress by remember(seriesDetail) { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var episodePlaybackPositions by remember(seriesDetail) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var watchedEpisodeIds by remember(seriesDetail) { mutableStateOf<Set<String>>(emptySet()) }

    // Re-reads progress/watched (including TMDB siblings) for every episode of this series.
    // Called after a manual mark, not just on initial load — a single-episode optimistic patch
    // would miss a sibling that the mark just made completed, and wouldn't restore a resume bar
    // an unmark brings back.
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
                    lastPlayedEpisodeId = resumeEpisodeId ?: allWatched.maxByOrNull { it.value.timestamp }?.key,
                    isCompleted = { allWatched[it]?.isCompleted == true },
                )?.also { if (it != resumeEpisodeId) onResumeEpisodeDerived(it) }

        if (!hasMultipleSeasons) return@LaunchedEffect

        val targetSeason =
            derivedAnchor?.let { seriesDetail.seasonNumberContaining(it) }
                ?: firstSeasonWithUnwatchedEpisode(
                    sortedSeasons = sortedSeasons,
                    episodesBySeason = sortedEpisodesBySeason,
                    isCompleted = { allWatched[it]?.isCompleted == true },
                )
        if (targetSeason != null && !hasManuallySelectedSeason) {
            selectedSeasonNumber = targetSeason
        }
    }

    val flatEpisodes =
        remember(seriesDetail, sortedSeasons) {
            seriesDetail.flattenedEpisodes(sortedSeasons)
        }

    val anchorEpisode =
        remember(flatEpisodes, resumeEpisodeId) {
            flatEpisodes.firstOrNull { it.id == resumeEpisodeId } ?: flatEpisodes.firstOrNull()
        }
    val anchorResumePosMs = anchorEpisode?.id?.let { episodePlaybackPositions[it] } ?: 0L
    val hasResume = anchorResumePosMs > 0L

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(CinemaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        modifier =
            Modifier
                .fillMaxSize()
                // Horizontal swipe anywhere in the list switches season — the touch equivalent
                // of tapping a season tab. Vertical scrolling is untouched: this only fires on
                // a horizontal drag, same technique EpisodeDetailContent below uses for
                // prev/next episode.
                .pointerInput(hasMultipleSeasons, previousSeason, nextSeason) {
                    if (!hasMultipleSeasons) return@pointerInput
                    var dragAmount = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragAmount = 0f },
                        onDragEnd = {
                            if (dragAmount > EPISODE_SWIPE_THRESHOLD_PX && previousSeason != null) {
                                hasManuallySelectedSeason = true
                                selectedSeasonNumber = previousSeason.seasonNumber
                            } else if (dragAmount < -EPISODE_SWIPE_THRESHOLD_PX && nextSeason != null) {
                                hasManuallySelectedSeason = true
                                selectedSeasonNumber = nextSeason.seasonNumber
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
                // Cover image, with the title logo overlaid on it (bottom-left, over a gradient
                // scrim so it reads regardless of what's under it) rather than as a separate
                // headline block below the poster.
                Box(modifier = Modifier.fillMaxWidth()) {
                    CinemaThumbnail(
                        url = seriesDetail.coverUrl,
                        fallbackLetter = seriesName.firstOrNull(),
                        contentType = ThumbnailContentType.TV_SHOW,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(MobileDimensions.posterHeightLarge),
                    )
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))),
                                ),
                    )
                    val seriesTitleText = tmdbTitle ?: seriesDetail.name.ifEmpty { seriesName }
                    TitleLogoOrText(
                        contentDescription = seriesTitleText,
                        logoUrl = logoUrl,
                        modifier = Modifier.align(Alignment.BottomStart).padding(CinemaSpacing.md),
                    ) {
                        Text(
                            text = seriesTitleText,
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                        )
                    }
                }

                // Genre
                seriesDetail.metadata.genre?.let { genre ->
                    Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Metadata row: content rating | rating | year | season count
                val presentLabel = stringResource(R.string.series_present)
                val yearRange = seriesDetail.seriesYearRange(presentLabel)

                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    seriesDetail.metadata.contentRating?.let { contentRating ->
                        Text(
                            text = contentRating,
                            style = MaterialTheme.typography.labelLarge,
                            modifier =
                                Modifier
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                        RoundedCornerShape(CinemaCornerRadius.small),
                                    ).padding(horizontal = CinemaSpacing.sm, vertical = CinemaSpacing.xs),
                        )
                    }
                    seriesDetail.metadata.rating?.let { rating ->
                        RatingBadge(
                            rating = rating,
                            textColor = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    yearRange?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    val countText =
                        if (sortedSeasons.size > 1) {
                            stringResource(R.string.series_seasons_and_episodes_format, sortedSeasons.size, totalEpisodes)
                        } else {
                            stringResource(R.string.series_total_episodes_format, totalEpisodes)
                        }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(modifier = Modifier.height(CinemaSpacing.lg))

                // Play / Resume button
                if (hasResume) {
                    val resumeButtonText =
                        if (anchorEpisode != null) {
                            stringResource(
                                R.string.series_resume_episode_time_format,
                                anchorEpisode.seasonNumber ?: 1,
                                anchorEpisode.episodeNumber,
                                formatTime(anchorResumePosMs),
                            )
                        } else {
                            stringResource(R.string.movie_resume_from_format, formatTime(anchorResumePosMs))
                        }
                    CinemaButton(
                        onClick = {
                            anchorEpisode?.let { ep ->
                                onPlayEpisode(ep.id, ep.title, ep.extension ?: "mp4", false)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(resumeButtonText)
                    }
                } else {
                    val playButtonText =
                        if (anchorEpisode != null) {
                            stringResource(
                                R.string.series_play_episode_format,
                                anchorEpisode.seasonNumber ?: 1,
                                anchorEpisode.episodeNumber,
                            )
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
                        Text(playButtonText)
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
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else CinemaTextPrimary,
                    )
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
                            onClick = { openExternalUrl(context, trailer) },
                        )
                    }
                }

                // Plot description
                seriesDetail.metadata.plot?.let { plot ->
                    Spacer(modifier = Modifier.height(CinemaSpacing.lg))
                    var plotExpanded by rememberSaveable(plot) { mutableStateOf(false) }
                    var plotOverflows by remember(plot) { mutableStateOf(false) }
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = if (plotExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { result ->
                            if (!plotExpanded) plotOverflows = result.hasVisualOverflow
                        },
                        modifier =
                            if (plotOverflows || plotExpanded) {
                                Modifier.clickable { plotExpanded = !plotExpanded }
                            } else {
                                Modifier
                            },
                    )
                }

                Spacer(modifier = Modifier.height(CinemaSpacing.md))

                // Cast
                seriesDetail.metadata.cast?.let { cast ->
                    Text(
                        text = stringResource(R.string.movie_cast_format, cast),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                }

                // Director
                seriesDetail.metadata.director?.let { director ->
                    Text(
                        text = stringResource(R.string.movie_director_format, director),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                }

                // StreamNamePicker
                StreamNamePicker(
                    currentName = seriesName,
                    alternates = alternateStreams,
                    onSelect = onAlternateStreamSelected,
                    modifier = Modifier.padding(vertical = CinemaSpacing.xs),
                )

                // TMDB ID
                Text(
                    text = stringResource(R.string.details_tmdb_format, seriesDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
                )

                // Category button
                if (categoryName != null) {
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                    CinemaOutlinedButton(
                        onClick = onCategorySelected,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.details_category_format, categoryName))
                    }
                }

                Spacer(modifier = Modifier.height(CinemaSpacing.lg))
                Text(
                    text = stringResource(R.string.series_episodes_header),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Season tabs — pinned in place as the episode list scrolls under it (stickyHeader, not
        // a plain item), so it reads as the control for what's below rather than scrolling away
        // with it. A horizontal swipe anywhere in this list (see the pointerInput above) does
        // the same thing as tapping a tab.
        if (hasMultipleSeasons) {
            stickyHeader(key = "season_tabs", contentType = "header") {
                SeasonTabs(
                    seasons = sortedSeasons,
                    selectedSeason = selectedSeasonNumber,
                    onSeasonSelected = {
                        hasManuallySelectedSeason = true
                        selectedSeasonNumber = it
                    },
                )
            }
        }

        val currentSeasonEpisodes = sortedEpisodesBySeason[selectedSeasonNumber?.toString()] ?: emptyList()
        items(currentSeasonEpisodes, key = { it.id }, contentType = { "episode" }) { episode ->
            EpisodeCard(
                episode = episode,
                isContinueWatching = episode.id == resumeEpisodeId,
                watchProgress = episodeProgress[episode.id] ?: 0f,
                isWatched = episode.id in watchedEpisodeIds,
                onClick = {
                    onEpisodeSelected(episode)
                },
                onToggleWatched = {
                    // Manual watched/unwatched mark (Phase 6,
                    // docs/plans/watch-state-durable-storage-plan.md). Optimistic: flips this
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
                },
            )
        }

        // Last rows of the list
        if (relatedTitles.moreLikeThis.isNotEmpty()) {
            item(key = "more-like-this", contentType = "related") {
                RelatedTitlesRow(
                    title = stringResource(R.string.details_more_like_this),
                    items = relatedTitles.moreLikeThis,
                    onItemClick = onRelatedTitleSelected,
                    modifier = Modifier.padding(top = CinemaSpacing.md),
                )
            }
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
    modifier: Modifier = Modifier,
) {
    val textStyle = MaterialTheme.typography.bodySmall
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium)

    if (alternates.isEmpty()) {
        Text(
            text = stringResource(R.string.details_stream_name_format, currentName),
            style = textStyle,
            color = textColor,
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
            Text(
                text = stringResource(R.string.details_stream_name_format, currentName),
                style = textStyle,
                color = textColor,
            )
            Icon(
                imageVector = CinemaIcons.ArrowDropDown,
                contentDescription = stringResource(R.string.details_other_instances),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(currentName, color = MaterialTheme.colorScheme.primary) },
                leadingIcon = { Icon(CinemaIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { expanded = false },
            )
            alternates.forEach { alternate ->
                DropdownMenuItem(
                    text = { Text(alternate.name) },
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
    onNavigate: (DomainEpisodeItem) -> Unit,
    onPlay: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
) {
    val extension = episode.extension ?: "mp4"

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
                }
                .verticalScroll(rememberScrollState())
                .padding(CinemaSpacing.md),
    ) {
        // Episode thumbnail
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

        // Episode title
        Text(
            text = episode.title,
            style = MaterialTheme.typography.headlineLarge,
        )

        // Season / Episode label
        val seasonLabel = episode.seasonNumber?.let { "S${it.toString().padStart(2, '0')}" } ?: ""
        val episodeLabel = "E${episode.episodeNumber.toString().padStart(2, '0')}"
        val subLabel =
            listOfNotNull(
                seasonLabel.ifEmpty { null },
                episodeLabel,
            ).joinToString(" ")
        Text(
            text = subLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // Genre (from series)
        seriesDetail.metadata.genre?.let { genre ->
            Text(
                text = genre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Rating, year and duration on same row
        val contentRating = episode.metadata.contentRating ?: seriesDetail.metadata.contentRating
        val rating = episode.metadata.rating ?: seriesDetail.metadata.rating
        val year = episode.metadata.year ?: episode.metadata.airDate?.take(4)?.toIntOrNull() ?: seriesDetail.metadata.year
        val hasDuration = episode.metadata.duration != null
        if (contentRating != null || rating != null || year != null || hasDuration) {
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            Row(
                // See the series header above — same overflow-clips-instead-of-wraps risk on a
                // narrow phone screen.
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                contentRating?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        modifier =
                            Modifier
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                    RoundedCornerShape(CinemaCornerRadius.small),
                                ).padding(horizontal = CinemaSpacing.sm, vertical = CinemaSpacing.xs),
                    )
                }
                rating?.let {
                    RatingBadge(
                        rating = it,
                        textColor = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                year?.let {
                    Text(
                        text = "$it",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                episode.metadata.duration?.takeIf(::hasMeaningfulDuration)?.let { duration ->
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                }
                // "Ends at" based on remaining duration
                val endsAtContext = LocalContext.current
                val endsAtText =
                    remember(episode.metadata.duration, resumePositionMs) {
                        computeEndsAt(endsAtContext, episode.metadata.duration, resumePositionMs)
                    }
                if (endsAtText != null) {
                    Text(
                        text = stringResource(R.string.movie_ends_at_format, endsAtText),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(CinemaSpacing.lg))

        // Play / Resume buttons + Favorite
        val hasResume = resumePositionMs > 0L
        if (hasResume) {
            val resumeTimeText = formatTime(resumePositionMs)
            CinemaButton(
                onClick = {
                    onPlay(episode.id, episode.title, extension, false)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.movie_resume_from_format, resumeTimeText))
            }
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            CinemaOutlinedButton(
                onClick = {
                    onPlay(episode.id, episode.title, extension, true)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.movie_start_beginning))
            }
        } else {
            CinemaButton(
                onClick = {
                    onPlay(episode.id, episode.title, extension, false)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.series_play_episode_action))
            }
        }

        // The trailer is the show's, not this episode's — Xtream and Jellyfin only ever
        // carry one per series.
        seriesDetail.metadata.trailerUrl?.let { trailer ->
            val trailerContext = LocalContext.current
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            CinemaOutlinedButton(
                onClick = { openExternalUrl(trailerContext, trailer) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.details_watch_trailer))
            }
        }

        // Plot/Description
        episode.metadata.plot?.let { plotText ->
            Spacer(modifier = Modifier.height(CinemaSpacing.lg))
            Text(
                text = plotText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // Cast (episode-level, fallback to series)
        val cast = episode.metadata.cast ?: seriesDetail.metadata.cast
        cast?.let {
            Text(
                text = stringResource(R.string.movie_cast_format, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
        }

        // Director (episode-level, fallback to series)
        val director = episode.metadata.director ?: seriesDetail.metadata.director
        director?.let {
            Text(
                text = stringResource(R.string.movie_director_format, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
            )
        }

        // TMDB id: the episode's own when it has one, else the show's
        Spacer(modifier = Modifier.height(CinemaSpacing.xs))
        Text(
            text =
                stringResource(
                    R.string.details_tmdb_format,
                    episode.metadata.tmdbId ?: seriesDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
        )

        // Container format
        episode.extension?.takeIf { it.isNotBlank() }?.let { ext ->
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
            Text(
                text = "${stringResource(R.string.tech_container_label)} ${ext.uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
            )
        }

        // Air date
        episode.metadata.airDate?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
            Text(
                text = stringResource(R.string.series_aired_format, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
            )
        }

        // Bitrate
        episode.metadata.bitrate?.takeIf { it > 0 }?.let {
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
            Text(
                text = stringResource(R.string.series_bitrate_format, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
            )
        }
    }
}

/**
 * One row of season pills; tapping one switches [selectedSeason]. Horizontally scrollable so a
 * long-running show's season count never wraps the row.
 */
@Composable
private fun SeasonTabs(
    seasons: List<SeasonInfo>,
    selectedSeason: Int?,
    onSeasonSelected: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // Opaque: this row is pinned via stickyHeader, so episode cards scroll in
                // underneath it and need to actually be hidden, not show through.
                .background(MaterialTheme.colorScheme.background)
                .horizontalScroll(rememberScrollState())
                .padding(vertical = CinemaSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
    ) {
        seasons.forEach { season ->
            val isSelected = season.seasonNumber == selectedSeason
            Text(
                text = stringResource(R.string.series_season_name_format, season.seasonNumber),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                    },
                modifier =
                    Modifier
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = CinemaAlpha.textLow) else Color.Transparent,
                            RoundedCornerShape(CinemaCornerRadius.medium),
                        ).clickable(role = Role.Button) { onSeasonSelected(season.seasonNumber) }
                        .padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm),
            )
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
    onToggleWatched: () -> Unit = {},
) {
    CinemaCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border =
            if (isContinueWatching) {
                BorderStroke(MobileDimensions.strokeWidth, MaterialTheme.colorScheme.primary)
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
                    Text(
                        text = stringResource(R.string.series_continue_watching_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                // Episode number, with the watched check beside it — the 40dp thumbnail is too
                // short to carry the check as an overlay the way the TV card does.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs),
                ) {
                    Text(
                        text = stringResource(R.string.series_episode_number_short, episode.episodeNumber),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    // Watched toggle (Phase 6, docs/plans/watch-state-durable-storage-plan.md): the
                    // badge itself is the tap target, since CinemaCard's onClick already owns the
                    // rest of the row for playing the episode.
                    CinemaIconButton(
                        onClick = onToggleWatched,
                        icon = {
                            Icon(
                                imageVector = if (isWatched) CinemaIcons.CheckCircle else CinemaIcons.RadioButtonUnchecked,
                                contentDescription =
                                    if (isWatched) stringResource(R.string.watched_unmark) else stringResource(R.string.watched_mark),
                                tint = if (isWatched) CinemaSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(MobileDimensions.iconSmall),
                            )
                        },
                    )
                }

                Spacer(modifier = Modifier.height(CinemaSpacing.xxs))

                // Episode title
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Episode plot/summary
                episode.metadata.plot?.let { plotText ->
                    Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
                    Text(
                        text = plotText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Duration
                episode.metadata.duration?.takeIf(::hasMeaningfulDuration)?.let { duration ->
                    Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    )
                }
            }
        }

        // Resume progress, card-width at the bottom edge — same placement as the stream card in
        // MobileCategoryListScreen, so a half-watched episode and a half-watched film read the
        // same. Poster-width was too short to be legible.
        if (watchProgress > 0f) {
            LinearProgressIndicator(
                progress = { watchProgress.coerceIn(0f, 1f) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // Clear the continue-watching border, which is drawn over the card edge.
                        .padding(bottom = MobileDimensions.strokeWidth)
                        .height(MobileDimensions.resumeBarHeight),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.focusedTint),
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
            CircularProgressIndicator()
            Text(stringResource(R.string.series_loading_episodes))
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
            modifier = Modifier.padding(CinemaSpacing.xl),
        ) {
            Text(
                text = stringResource(R.string.series_error_loading),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
            )
            CinemaButton(onClick = onBack) {
                Text(stringResource(R.string.common_back))
            }
        }
    }
}

private const val EPISODE_SWIPE_THRESHOLD_PX = 80f

