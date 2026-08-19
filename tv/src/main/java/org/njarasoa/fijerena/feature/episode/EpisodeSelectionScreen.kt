@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.episode

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.resumeProgress
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
import org.njarasoa.fijerena.core.player.model.computeEndsAt
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.WatchedBadge
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModelFactory
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.player.domain.EpisodeItem as DomainEpisodeItem
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

/**
 * Episode selection screen for TV shows.
 *
 * Features:
 * - Displays series information (title, plot)
 * - Lists all episodes grouped by season
 * - Inline episode detail panel when an episode is selected
 * - D-pad friendly navigation
 * - Loads episode data from MediaRepository
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
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }
    val viewModel: SeriesDetailsViewModel =
        viewModel(
            factory =
                remember(seriesId, categoryId) {
                    SeriesDetailsViewModelFactory(context.applicationContext, seriesId, categoryId)
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Retained across a background refresh so the episode list stays on screen with just a
    // spinning refresh icon, instead of the whole thing dropping to a full-screen loading state
    // and losing scroll position and the expanded season. Mirrors the mobile screen, which
    // already did this.
    var lastSuccess by remember { mutableStateOf<SeriesDetailsViewModel.UiState.Success?>(null) }
    (uiState as? SeriesDetailsViewModel.UiState.Success)?.let { lastSuccess = it }
    val isRefreshing = uiState is SeriesDetailsViewModel.UiState.Loading && lastSuccess != null

    // Provide UI scale for all child composables
    CompositionLocalProvider(LocalUiScale provides uiScale) {
        val state = uiState
        val shown = lastSuccess
        when {
            state is SeriesDetailsViewModel.UiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onBack = onBack,
                )
            }
            shown != null -> {
                EpisodeListContent(
                    seriesDetail = shown.seriesDetail,
                    seriesName = seriesName,
                    categoryId = categoryId,
                    mediaRepository = viewModel.mediaRepository!!,
                    initialEpisodeId = initialEpisodeId,
                    isFavorite = shown.isFavorite,
                    categoryName = shown.categoryName,
                    isRefreshing = isRefreshing,
                    onToggleFavorite = { viewModel.toggleFavorite(seriesName) },
                    onEpisodeSelected = onEpisodeSelected,
                    onCategorySelected = { onCategorySelected(categoryId) },
                    onRefresh = { viewModel.loadSeriesInfo() },
                    onBack = onBack,
                )
            }
            else -> {
                LoadingScreen()
            }
        }
    }
}

@Composable
private fun EpisodeListContent(
    seriesDetail: SeriesDetail,
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
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val providerName by remember { mutableStateOf(appSettings.providerName) }
    val listState = rememberLazyListState()
    val scale = LocalUiScale.current
    val episodeCardStyle = episodeCardStyle()
    val typography = MaterialTheme.typography
    val scaledStyles =
        remember(scale, typography) {
            object {
                val displaySmall = typography.displaySmall.copy(fontSize = typography.displaySmall.fontSize.scaled(scale))
                val labelSmall = typography.labelSmall.copy(fontSize = typography.labelSmall.fontSize.scaled(scale))
                val bodyMedium = typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale))
                val labelMedium = typography.labelMedium.copy(fontSize = typography.labelMedium.fontSize.scaled(scale))
                val titleSmall = typography.titleSmall.copy(fontSize = typography.titleSmall.fontSize.scaled(scale))
            }
        }

    // Selected episode for detail panel — only set by an explicit tap (including on the
    // Continue Watching resume episode below); arriving here never auto-opens it.
    var selectedEpisode by remember { mutableStateOf<DomainEpisodeItem?>(null) }

    // Handle back press: dismiss detail panel first, then navigate back
    BackHandler(enabled = selectedEpisode != null) {
        selectedEpisode = null
    }

    // Track refresh state for animation
    var targetRotation by remember { mutableStateOf(0f) }

    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = CinemaAnimation.fadeInDurationMs, easing = LinearEasing),
        label = "refresh_rotation",
    )

    // isRefreshing is now a parameter, so the old `while (isRefreshing)` guard would never
    // observe it changing. The effect key does that job: a false value cancels the loop.
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
            seriesDetail.sortedSeasons { num -> context.getString(R.string.series_season_name_format, num) }
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

    // Episode to come back to: the route's Continue Watching argument at first, then whichever
    // episode was last sent to the player from here. Saveable, because navigating to the player
    // disposes this composable — a plain remember would drop it and land the user back on
    // season 1 at the top of the list.
    var resumeEpisodeId by rememberSaveable { mutableStateOf(initialEpisodeId) }

    // Season containing the resume episode, if any — takes priority over both the
    // "first season" and "next unwatched" defaults below, so backing out of its detail panel
    // (or out of the player) lands on that season expanded, everything else collapsed.
    val resumeSeasonNumber =
        remember(seriesDetail, resumeEpisodeId) {
            resumeEpisodeId?.let { seriesDetail.seasonNumberContaining(it) }
        }

    // Accordion: only one season expanded at a time (first season by default)
    var expandedSeasons by remember(seriesDetail) {
        mutableStateOf(defaultExpandedSeason(resumeSeasonNumber, sortedSeasons))
    }
    // Set by the manual season-header toggle below, so the auto-expand effect doesn't
    // clobber a choice the user already made while the playback-position lookup was in flight.
    // Also seeded true when a resume season is already known, so the "next unwatched" guess
    // below never overrides the season the user actually asked to resume.
    var hasManuallyToggledSeasons by remember(seriesDetail) { mutableStateOf(resumeSeasonNumber != null) }

    // D-pad focus target for the resume episode card — requested below once it's on screen, so
    // OK is immediately playable without the user having to navigate to it first.
    val resumeCardFocusRequester = remember { FocusRequester() }

    // Scroll the resume episode into view once its season is expanded, so "highlighted" also
    // means visible without the user having to scroll to find it — then move D-pad focus onto
    // it directly.
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
            try {
                resumeCardFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // Card not yet composed this frame — falls back to default D-pad
                // focus, which still lands on the season containing it.
            }
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

        // Entering from the series list carries no episode argument, so derive the anchor from
        // watch history instead: the episode with the newest playback timestamp (or the one
        // after it, if that one is finished). Whatever the route or a play from this screen
        // already set wins — it's the more recent truth.
        if (resumeEpisodeId == null) {
            resumeEpisodeId =
                seriesDetail.resumeAnchorEpisodeId(
                    sortedSeasons = sortedSeasons,
                    lastPlayedEpisodeId = allWatched.maxByOrNull { it.value.timestamp }?.key,
                    isCompleted = { allWatched[it]?.isCompleted == true },
                )
        }

        if (!hasMultipleSeasons) return@LaunchedEffect

        // The derived anchor's season beats the "first season with anything unwatched" guess:
        // a viewer mid-season 13 doesn't want season 1 opened because they skipped an episode.
        val targetSeason =
            resumeEpisodeId?.let { seriesDetail.seasonNumberContaining(it) }
                ?: firstSeasonWithUnwatchedEpisode(
                    sortedSeasons = sortedSeasons,
                    episodesBySeason = sortedEpisodesBySeason,
                    isCompleted = { allWatched[it]?.isCompleted == true },
                )
        if (targetSeason != null && !hasManuallyToggledSeasons) {
            expandedSeasons = setOf(targetSeason)
        }
    }

    // Flat ordered list of all episodes across seasons (for prev/next navigation)
    val flatEpisodes =
        remember(seriesDetail, sortedSeasons) {
            seriesDetail.flattenedEpisodes(sortedSeasons)
        }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackdrop(
            modifier = Modifier.fillMaxSize(),
            imageUrl = selectedEpisode?.thumbnailUrl ?: seriesDetail.coverUrl,
        )
        if (selectedEpisode != null) {
            val current = selectedEpisode!!
            val currentIdx =
                remember(flatEpisodes, current.id) {
                    flatEpisodes.indexOfFirst { it.id == current.id }
                }
            val previousEpisode = flatEpisodes.getOrNull(currentIdx - 1)
            val nextEpisode = flatEpisodes.getOrNull(currentIdx + 1)
            // Show episode detail panel
            EpisodeDetailPanel(
                episode = current,
                seriesDetail = seriesDetail,
                seriesName = seriesName,
                categoryId = categoryId,
                providerName = providerName,
                mediaRepository = mediaRepository,
                previousEpisode = previousEpisode,
                nextEpisode = nextEpisode,
                onNavigate = { next -> selectedEpisode = next },
                onPlay = { episodeId, episodeTitle, extension, startFromBeginning ->
                    resumeEpisodeId = episodeId
                    onEpisodeSelected(episodeId, episodeTitle, extension, startFromBeginning)
                },
                onBack = { selectedEpisode = null },
            )
        } else {
            // Show episode list
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
            ) {
                // Header with series info and back button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
                        ) {
                            Text(
                                text = seriesName,
                                style = scaledStyles.displaySmall,
                                color = CinemaTextPrimary,
                            )
                            // Favorite button
                            CinemaIconButton(
                                onClick = onToggleFavorite,
                                icon = {
                                    Icon(
                                        imageVector = if (isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                                        contentDescription = if (isFavorite) stringResource(R.string.favorite_remove) else stringResource(R.string.favorite_add),
                                        tint = if (isFavorite) CinemaAccent else CinemaTextPrimary
                                    )
                                }
                            )
                            // Refresh button
                            CinemaIconButton(
                                onClick = { onRefresh() },
                                enabled = !isRefreshing,
                                icon = {
                                    Icon(
                                        imageVector = CinemaIcons.Refresh,
                                        contentDescription = stringResource(R.string.series_refresh_info),
                                        modifier =
                                            Modifier
                                                .size(TvDimensions.iconSmall.scaled(scale))
                                                .rotate(rotation),
                                    )
                                },
                            )
                        }
                        // Show episode count
                        Text(
                            text = stringResource(R.string.series_total_episodes_format, totalEpisodes),
                            style = scaledStyles.labelSmall,
                            color = CinemaTextSecondary,
                        )
                        seriesDetail.metadata.plot?.let { plot ->
                            Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                            Text(
                                text = plot,
                                style = scaledStyles.bodyMedium,
                                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // Series metadata: genre, rating, cast
                        val metadataParts =
                            remember(seriesDetail) {
                                listOfNotNull(
                                    seriesDetail.metadata.genre,
                                    seriesDetail.metadata.rating?.let { context.getString(R.string.series_rating_format, it) },
                                    seriesDetail.metadata.contentRating,
                                    seriesDetail.metadata.cast?.let { context.getString(R.string.movie_cast_format, it) },
                                )
                            }
                        if (metadataParts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                            Text(
                                text = metadataParts.joinToString(" · "),
                                style = scaledStyles.labelMedium,
                                color = CinemaAccentLight,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                    Text(
                        text = providerName,
                        style = scaledStyles.titleSmall,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    )
                }

                // Category this series belongs to — OK opens its series list
                if (categoryName != null) {
                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                    CinemaSecondaryButton(
                        onClick = onCategorySelected,
                        text = stringResource(R.string.details_category_format, categoryName),
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

                // Season-grouped episodes list
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = Spacing.xs.scaled(scale)),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
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
                                val isContinueWatching = episode.id == resumeEpisodeId
                                EpisodeCard(
                                    episode = episode,
                                    cardStyle = episodeCardStyle,
                                    isContinueWatching = isContinueWatching,
                                    watchProgress = episodeProgress[episode.id] ?: 0f,
                                    isWatched = episode.id in watchedEpisodeIds,
                                    focusRequester = if (isContinueWatching) resumeCardFocusRequester else null,
                                    onClick = {
                                        selectedEpisode = episode
                                    },
                                )
                            }
                        }
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
    val detailScaledStyles =
        remember(scale, typography) {
            object {
                val displaySmall = typography.displaySmall.copy(fontSize = typography.displaySmall.fontSize.scaled(scale))
                val titleMedium = typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
                val titleSmall = typography.titleSmall.copy(fontSize = typography.titleSmall.fontSize.scaled(scale))
                val headlineSmall = typography.headlineSmall.copy(fontSize = typography.headlineSmall.fontSize.scaled(scale))
                val bodyMedium = typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale))
                val bodyLarge = typography.bodyLarge.copy(fontSize = typography.bodyLarge.fontSize.scaled(scale))
                val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
            }
        }

    // Focus requester for Play button
    val playButtonFocusRequester = remember { FocusRequester() }

    // Load resume position
    var resumePositionMs by remember { mutableStateOf(0L) }

    LaunchedEffect(episode.id) {
        val watched = mediaRepository.getPlaybackPositionSuspend(episode.id, ContentType.TV_SHOWS)
        if (watched != null && !watched.isCompleted && watched.playbackPosition > 0 && watched.duration > 0) {
            val progress = (watched.playbackPosition.toFloat() / watched.duration.toFloat()) * 100f
            if (progress in 2.0..95.0) {
                resumePositionMs = watched.playbackPosition
            }
        }
    }

    // Request focus on Play/Resume button when screen loads, resume data arrives, or episode changes
    LaunchedEffect(episode.id, resumePositionMs) {
        try {
            playButtonFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.MediaNext -> nextEpisode?.let { onNavigate(it); true } ?: false
                        Key.MediaPrevious -> previousEpisode?.let { onNavigate(it); true } ?: false
                        else -> false
                    }
                }
                .verticalScroll(rememberScrollState())
                .focusable()
                .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = seriesName,
                    style = detailScaledStyles.displaySmall,
                    color = CinemaTextPrimary,
                )
                // Season / episode label
                val seasonLabel = episode.seasonNumber?.let { stringResource(R.string.series_season_label, it) } ?: ""
                val episodeLabel = stringResource(R.string.series_episode_label, episode.episodeNumber)
                val subLabel =
                    listOfNotNull(
                        seasonLabel.ifEmpty { null },
                        episodeLabel,
                    ).joinToString(" · ")
                Text(
                    text = subLabel,
                    style = detailScaledStyles.titleMedium,
                    color = CinemaAccentLight,
                )
            }
            Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
            Text(
                text = providerName,
                style = detailScaledStyles.titleSmall,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

        // Episode content: thumbnail + metadata
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xl.scaled(scale)),
        ) {
            // Episode thumbnail
            CinemaThumbnail(
                url = episode.thumbnailUrl ?: seriesDetail.coverUrl,
                fallbackLetter = episode.title.firstOrNull(),
                contentType = ThumbnailContentType.TV_SHOW,
                modifier =
                    Modifier
                        .width(TvDimensions.posterWidth.scaled(scale))
                        .height(TvDimensions.posterHeightLarge.scaled(scale)),
            )

            // Metadata in glass panel
            GlassPanel(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(Spacing.lg.scaled(scale))) {
                    // Episode title
                    Text(
                        text = episode.title,
                        style = detailScaledStyles.headlineSmall,
                        color = CinemaTextPrimary,
                    )

                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                    // Metadata row: rating | duration | ends at
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Prefer episode rating, fallback to series rating
                        val rating = episode.metadata.rating ?: seriesDetail.metadata.rating
                        rating?.let {
                            Text(
                                text = "★ $it",
                                style = detailScaledStyles.titleMedium,
                                color = CinemaAccent,
                            )
                        }
                        episode.metadata.duration?.let { duration ->
                            Text(
                                text = formatDuration(duration),
                                style = detailScaledStyles.titleMedium,
                                color = CinemaTextSecondary,
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
                                style = detailScaledStyles.titleMedium,
                                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textMedium),
                            )
                        }
                    }

                    // Genre (from series)
                    seriesDetail.metadata.genre?.let { genre ->
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        Text(
                            text = genre,
                            style = detailScaledStyles.bodyMedium,
                            color = CinemaAccent,
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))

                    // Play / Resume buttons + Favorite
                    val hasResume = resumePositionMs > 0L
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasResume) {
                            val resumeTimeText = formatTime(resumePositionMs)
                            CinemaPrimaryButton(
                                onClick = {
                                    onPlay(episode.id, episode.title, extension, false)
                                },
                                text = stringResource(R.string.movie_resume_from_format, resumeTimeText),
                                modifier = Modifier.focusRequester(playButtonFocusRequester),
                            )
                            CinemaSecondaryButton(
                                onClick = {
                                    onPlay(episode.id, episode.title, extension, true)
                                },
                                text = stringResource(R.string.movie_start_beginning),
                            )
                        } else {
                            CinemaPrimaryButton(
                                onClick = {
                                    onPlay(episode.id, episode.title, extension, false)
                                },
                                text = stringResource(R.string.series_play_episode_action),
                                modifier = Modifier.focusRequester(playButtonFocusRequester),
                            )
                        }
                    }

                    // Prev/Next episode hint (remote media keys)
                    if (previousEpisode != null || nextEpisode != null) {
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        val hint =
                            buildString {
                                if (previousEpisode != null) append(stringResource(R.string.player_prev_episode))
                                if (previousEpisode != null && nextEpisode != null) append("   ")
                                if (nextEpisode != null) append(stringResource(R.string.player_next_episode))
                            }
                        Text(
                            text = hint,
                            style = detailScaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textMedium),
                        )
                    }

                    // Plot/Description
                    episode.metadata.plot?.let { plotText ->
                        Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
                        Text(
                            text = plotText,
                            style = detailScaledStyles.bodyLarge,
                            color = CinemaTextPrimary,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                    // Cast (episode-level, fallback to series)
                    val cast = episode.metadata.cast ?: seriesDetail.metadata.cast
                    cast?.let {
                        Text(
                            text = stringResource(R.string.movie_cast_format, it),
                            style = detailScaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    }

                    // Director (episode-level, fallback to series)
                    val director = episode.metadata.director ?: seriesDetail.metadata.director
                    director?.let {
                        Text(
                            text = stringResource(R.string.movie_director_format, it),
                            style = detailScaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                    }

                    // Air date
                    episode.metadata.airDate?.takeIf { it.isNotBlank() }?.let {
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                        Text(
                            text = stringResource(R.string.series_aired_format, it),
                            style = detailScaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                    }

                    // Bitrate
                    episode.metadata.bitrate?.takeIf { it > 0 }?.let {
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                        Text(
                            text = stringResource(R.string.series_bitrate_format, it),
                            style = detailScaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                    }
                } // GlassPanel Column
            } // GlassPanel
        } // Outer Row (thumbnail + metadata)
    }
}

@Composable
private fun SeasonHeader(
    season: SeasonInfo,
    episodeCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val scale = LocalUiScale.current
    var isFocused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) TvFocusTokens.focusedScaleSubtle else TvFocusTokens.defaultScale,
        animationSpec = tween(durationMillis = CinemaAnimation.focusDurationMs),
        label = "season_header_focus_scale",
    )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                }
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
                .onFocusChanged { isFocused = it.isFocused }
                .clickable { onToggle() }
                .padding(horizontal = Spacing.sm.scaled(scale), vertical = Spacing.sm.scaled(scale)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
    ) {
        Icon(
            imageVector = if (isExpanded) CinemaIcons.KeyboardArrowUp else CinemaIcons.KeyboardArrowDown,
            contentDescription = if (isExpanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
            tint = CinemaAccentLight,
            modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
        )
        Text(
            text = stringResource(R.string.series_season_label, season.seasonNumber),
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontSize =
                        MaterialTheme.typography.headlineSmall.fontSize
                            .scaled(scale),
                ),
            color = CinemaAccentLight,
        )
        Text(
            text = stringResource(R.string.series_total_episodes_format, episodeCount),
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontSize =
                        MaterialTheme.typography.labelMedium.fontSize
                            .scaled(scale),
                ),
            color = CinemaTextSecondary,
        )
    }
}

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
    val continueWatchingBorder: CardBorder,
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
        continueWatchingBorder =
            CardDefaults.border(
                border =
                    Border(
                        border = BorderStroke(TvFocusTokens.focusBorderWidth, CinemaAccent),
                        shape = RoundedCornerShape(CornerRadius.medium),
                    ),
            ),
    )

@Composable
private fun EpisodeCard(
    episode: DomainEpisodeItem,
    cardStyle: EpisodeCardStyle,
    isContinueWatching: Boolean = false,
    watchProgress: Float = 0f,
    isWatched: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val cardScaledStyles =
        remember(scale, typography) {
            object {
                val titleMedium = typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
                val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
                val labelMedium = typography.labelMedium.copy(fontSize = typography.labelMedium.fontSize.scaled(scale))
                val labelSmall = typography.labelSmall.copy(fontSize = typography.labelSmall.fontSize.scaled(scale))
            }
        }
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TvDimensions.episodeCardHeight.scaled(scale))
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        colors = cardStyle.colors,
        shape = cardStyle.shape,
        scale = cardStyle.cardScale,
        border = if (isContinueWatching) cardStyle.continueWatchingBorder else cardStyle.border,
        glow = cardStyle.glow,
    ) {
        // Box, not a Column with a weighted row: the card is pinned to cardHeight and its content
        // already fills that, so a footer that consumes layout height gets pushed past the card's
        // clip bounds — it lays out (and shows up in semantics) but never paints.
        Box(modifier = Modifier.fillMaxSize()) {
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

            // Episode number
            Text(
                text = stringResource(R.string.series_episode_number_short, episode.episodeNumber),
                style = cardScaledStyles.titleMedium,
                color = CinemaAccentLight,
                modifier = Modifier.width(Spacing.xxl.scaled(scale)),
            )

            Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))

            // Episode title and plot
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
                Text(
                    text = episode.title,
                    style = cardScaledStyles.titleMedium,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.metadata.plot?.let { plotText ->
                    Text(
                        text = plotText,
                        style = cardScaledStyles.bodySmall,
                        color = CinemaTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Duration
            episode.metadata.duration?.let { duration ->
                Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                Text(
                    text = duration,
                    style = cardScaledStyles.labelMedium,
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
                        // Sit above the continue-watching border, which is drawn over the card's
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
                text = stringResource(R.string.series_error_loading),
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
                text = stringResource(R.string.series_back_to_list),
            )
        }
    }
}

