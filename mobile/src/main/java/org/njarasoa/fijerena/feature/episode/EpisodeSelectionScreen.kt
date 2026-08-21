package org.njarasoa.fijerena.feature.episode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.resumeProgress
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.SeasonInfo
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.domain.defaultExpandedSeason
import org.njarasoa.fijerena.core.player.domain.episodeScrollIndex
import org.njarasoa.fijerena.core.player.domain.firstSeasonWithUnwatchedEpisode
import org.njarasoa.fijerena.core.player.domain.flattenedEpisodes
import org.njarasoa.fijerena.core.player.domain.resumeAnchorEpisodeId
import org.njarasoa.fijerena.core.player.domain.seasonNumberContaining
import org.njarasoa.fijerena.core.player.domain.sortedSeasons
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModelFactory
import org.njarasoa.fijerena.core.player.model.computeEndsAt
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.utils.openExternalUrl
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
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
    onSearchTitle: (query: String) -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: SeriesDetailsViewModel =
        viewModel(
            factory =
                remember(seriesId, categoryId) {
                    SeriesDetailsViewModelFactory(context.applicationContext, seriesId, categoryId)
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val relatedTitles by viewModel.relatedTitles.collectAsStateWithLifecycle()
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
                title = { Text(seriesName) },
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
                actions = {
                    CinemaIconButton(onClick = { viewModel.toggleFavorite(seriesName) },
                        icon = {
                            Icon(
                                imageVector = if (isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                                contentDescription = if (isFavorite) stringResource(R.string.favorite_remove) else stringResource(R.string.favorite_add),
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else CinemaTextPrimary,
                            )
                        }
                    )
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            AmbientBackdrop(
                modifier = Modifier.fillMaxSize(),
                imageUrl = selectedEpisode?.thumbnailUrl ?: displaySeriesDetail?.coverUrl,
            )
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
                            mediaRepository = viewModel.mediaRepository!!,
                            resumeEpisodeId = resumeEpisodeId,
                            onResumeEpisodeDerived = { resumeEpisodeId = it },
                            categoryName = lastSuccess?.categoryName,
                            onEpisodeSelected = { episode ->
                                selectedEpisode = episode
                            },
                            onCategorySelected = { onCategorySelected(categoryId) },
                            onSearchTitle = onSearchTitle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeListContent(
    seriesDetail: SeriesDetail,
    relatedTitles: RelatedTitles,
    mediaRepository: MediaRepository,
    resumeEpisodeId: String? = null,
    onResumeEpisodeDerived: (String) -> Unit,
    categoryName: String?,
    onEpisodeSelected: (DomainEpisodeItem) -> Unit,
    onCategorySelected: () -> Unit,
    onSearchTitle: (query: String) -> Unit,
) {
    val context = LocalContext.current
    val sortedSeasons =
        remember(seriesDetail) {
            seriesDetail.sortedSeasons { num -> context.getString(R.string.series_season_name_format, num) }
        }
    // Pre-sort episodes by season — avoids re-sorting on every recomposition of the LazyColumn
    val sortedEpisodesBySeason =
        remember(seriesDetail) {
            seriesDetail.episodes.mapValues { (_, eps) -> eps.sortedBy { it.episodeNumber } }
        }
    val totalEpisodes =
        remember(seriesDetail) {
            seriesDetail.episodes.values.sumOf { it.size }
        }
    val hasMultipleSeasons = sortedSeasons.size > 1

    // Season containing the Continue Watching resume episode, if any — takes priority over
    // both the "first season" and "next unwatched" defaults below, so backing out of its
    // detail panel lands on that season expanded, everything else collapsed.
    val resumeSeasonNumber =
        remember(seriesDetail, resumeEpisodeId) {
            resumeEpisodeId?.let { seriesDetail.seasonNumberContaining(it) }
        }

    // Accordion: only one season expanded at a time (first season by default)
    var expandedSeasons by remember(seriesDetail) {
        mutableStateOf(defaultExpandedSeason(resumeSeasonNumber, sortedSeasons))
    }
    // Set by the manual season-header toggle below, so the auto-expand effect doesn't
    // clobber a choice the user already made while the playback-position lookups were in flight.
    // Also seeded true when a resume season is already known, so the "next unwatched" guess
    // below never overrides the season the user actually asked to resume.
    var hasManuallyToggledSeasons by remember(seriesDetail) { mutableStateOf(resumeSeasonNumber != null) }

    val listState = rememberLazyListState()

    // Scroll the resume episode into view once its season is expanded, so "highlighted" also
    // means visible without the user having to scroll to find it.
    LaunchedEffect(resumeEpisodeId, resumeSeasonNumber, expandedSeasons) {
        val targetId = resumeEpisodeId ?: return@LaunchedEffect
        val index =
            episodeScrollIndex(
                sortedSeasons = sortedSeasons,
                episodesBySeason = sortedEpisodesBySeason,
                hasMultipleSeasons = hasMultipleSeasons,
                targetEpisodeId = targetId,
                isExpanded = { it in expandedSeasons },
            )
        if (index != null) {
            listState.animateScrollToItem(index)
        }
    }

    // Per-episode resume fraction, drives the progress bar on each card. Re-read on every
    // entry into this screen — including coming back from the player — so a just-watched
    // episode's bar is current.
    var episodeProgress by remember(seriesDetail) { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var watchedEpisodeIds by remember(seriesDetail) { mutableStateOf<Set<String>>(emptySet()) }

    // Playback positions for every episode: the progress bars and watched checks above, plus
    // auto-expanding the season holding the next unwatched/in-progress episode.
    LaunchedEffect(seriesDetail) {
        // ⚡ Bolt: Avoid flatten().map to prevent intermediate list allocations
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
        watchedEpisodeIds =
            buildSet {
                for ((id, watched) in allWatched) {
                    if (watched.isCompleted) add(id)
                }
            }

        // Anchor on the episode worth watching next. The route (or a play from this screen)
        // names the episode last played; entering from the series list names none, so fall back
        // to the newest playback timestamp. Either way the anchor moves on when that episode is
        // already finished — re-evaluated on every entry, so backing out of an episode the user
        // just completed lands on the following one.
        val derivedAnchor =
            seriesDetail
                .resumeAnchorEpisodeId(
                    sortedSeasons = sortedSeasons,
                    lastPlayedEpisodeId = resumeEpisodeId ?: allWatched.maxByOrNull { it.value.timestamp }?.key,
                    isCompleted = { allWatched[it]?.isCompleted == true },
                )?.also { if (it != resumeEpisodeId) onResumeEpisodeDerived(it) }

        if (!hasMultipleSeasons) return@LaunchedEffect

        // The derived anchor's season beats the "first season with anything unwatched" guess:
        // a viewer mid-season 13 doesn't want season 1 opened because they skipped an episode.
        val targetSeason =
            derivedAnchor?.let { seriesDetail.seasonNumberContaining(it) }
                ?: firstSeasonWithUnwatchedEpisode(
                    sortedSeasons = sortedSeasons,
                    episodesBySeason = sortedEpisodesBySeason,
                    isCompleted = { allWatched[it]?.isCompleted == true },
                )
        if (targetSeason != null && !hasManuallyToggledSeasons) {
            expandedSeasons = setOf(targetSeason)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Series info header
        val hasPlot = seriesDetail.metadata.plot != null
        val metadataParts =
            remember(seriesDetail) {
                listOfNotNull(
                    seriesDetail.metadata.genre,
                    seriesDetail.metadata.rating?.let { context.getString(R.string.series_rating_format, formatRating(it)) },
                    seriesDetail.metadata.contentRating,
                    seriesDetail.metadata.tmdbId?.let { context.getString(R.string.details_tmdb_format, it) },
                )
            }
        if (hasPlot || metadataParts.isNotEmpty()) {
            GlassPanel(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(CinemaSpacing.md),
            ) {
                Column(modifier = Modifier.padding(CinemaSpacing.md)) {
                    seriesDetail.metadata.plot?.let { plot ->
                        // Long synopses are clipped to three lines. Tap to read the rest, tap
                        // again to collapse — but only once we know it actually overflows, so a
                        // short plot has no invisible tap target that appears to do nothing.
                        var plotExpanded by rememberSaveable(plot) { mutableStateOf(false) }
                        var plotOverflows by remember(plot) { mutableStateOf(false) }
                        Text(
                            text = plot,
                            style = MaterialTheme.typography.bodyMedium,
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
                    if (metadataParts.isNotEmpty()) {
                        if (hasPlot) Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                        Text(
                            text = metadataParts.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Category this series belongs to — tap to browse it
        if (categoryName != null) {
            CinemaOutlinedButton(
                onClick = onCategorySelected,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CinemaSpacing.md),
            ) {
                Text(stringResource(R.string.details_category_format, categoryName))
            }
        }

        // Episode count
        Text(
            text = stringResource(R.string.series_total_episodes_format, totalEpisodes),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )

        // Season-grouped episodes list
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
            modifier = Modifier.fillMaxSize(),
        ) {
            sortedSeasons.forEach { season ->
                val seasonKey = season.seasonNumber.toString()
                val seasonEpisodes = sortedEpisodesBySeason[seasonKey] ?: emptyList()
                val isExpanded = !hasMultipleSeasons || season.seasonNumber in expandedSeasons

                // Season header (skip if only 1 season)
                if (hasMultipleSeasons) {
                    item(key = "season_header_$seasonKey", contentType = "header") {
                        SeasonHeader(
                            season = season,
                            episodeCount = seasonEpisodes.size,
                            isExpanded = isExpanded,
                            onToggle = {
                                hasManuallyToggledSeasons = true
                                expandedSeasons =
                                    if (isExpanded) {
                                        emptySet()
                                    } else {
                                        setOf(season.seasonNumber)
                                    }
                            },
                        )
                    }
                }

                if (isExpanded) {
                    items(seasonEpisodes, key = { it.id }, contentType = { "episode" }) { episode ->
                        EpisodeCard(
                            episode = episode,
                            isContinueWatching = episode.id == resumeEpisodeId,
                            watchProgress = episodeProgress[episode.id] ?: 0f,
                            isWatched = episode.id in watchedEpisodeIds,
                            onClick = {
                                onEpisodeSelected(episode)
                            },
                        )
                    }
                }
            }

            // Last rows of the list rather than below it: the list fills the screen, so anything
            // placed after it would sit off screen.
            if (relatedTitles.recommended.isNotEmpty()) {
                item(key = "recommended", contentType = "related") {
                    RelatedTitlesRow(
                        title = stringResource(R.string.details_more_like_this),
                        items = relatedTitles.recommended,
                        onItemClick = { onSearchTitle(it.name) },
                        modifier = Modifier.padding(top = CinemaSpacing.md),
                    )
                }
            }
            if (relatedTitles.similar.isNotEmpty()) {
                item(key = "similar", contentType = "related") {
                    RelatedTitlesRow(
                        title = stringResource(R.string.details_similar_titles),
                        items = relatedTitles.similar,
                        onItemClick = { onSearchTitle(it.name) },
                        modifier = Modifier.padding(top = CinemaSpacing.md),
                    )
                }
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

        // Rating and duration on same row
        val rating = episode.metadata.rating ?: seriesDetail.metadata.rating
        val hasDuration = episode.metadata.duration != null
        if (rating != null || hasDuration) {
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rating?.let {
                    Text(
                        text = "★ ${formatRating(it)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                episode.metadata.duration?.let { duration ->
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
        (episode.metadata.tmdbId ?: seriesDetail.metadata.tmdbId)?.let {
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
            Text(
                text = stringResource(R.string.details_tmdb_format, it),
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

@Composable
private fun SeasonHeader(
    season: SeasonInfo,
    episodeCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onToggle() }
                .padding(vertical = CinemaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
    ) {
        Icon(
            imageVector = if (isExpanded) CinemaIcons.KeyboardArrowUp else CinemaIcons.KeyboardArrowDown,
            contentDescription = if (isExpanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.series_season_name_format, season.seasonNumber),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.series_total_episodes_format, episodeCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
    }
}

@Composable
private fun EpisodeCard(
    episode: DomainEpisodeItem,
    isContinueWatching: Boolean = false,
    watchProgress: Float = 0f,
    isWatched: Boolean = false,
    onClick: () -> Unit,
) {
    CinemaCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border =
            if (isContinueWatching) {
                BorderStroke(MobileDimensions.strokeWidth, MaterialTheme.colorScheme.primary)
            } else {
                null
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
                    if (isWatched) {
                        Icon(
                            imageVector = CinemaIcons.CheckCircle,
                            contentDescription = stringResource(R.string.content_watched_badge),
                            tint = CinemaSuccess,
                            modifier = Modifier.size(MobileDimensions.iconSmall),
                        )
                    }
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
                episode.metadata.duration?.let { duration ->
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

