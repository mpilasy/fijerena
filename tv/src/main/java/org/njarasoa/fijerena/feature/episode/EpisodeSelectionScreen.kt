@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.episode

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.SeasonInfo
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

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
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }
    var mediaRepository by remember { mutableStateOf<MediaRepository?>(null) }

    var seriesDetail by remember { mutableStateOf<SeriesDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var isFavorite by remember { mutableStateOf(false) }

    fun refresh() {
        refreshTrigger++
    }

    // Load series info on launch
    LaunchedEffect(seriesId, refreshTrigger) {
        isLoading = true
        error = null

        // Initialize repository asynchronously (avoids runBlocking on main thread)
        val repo =
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                mediaRepository ?: run {
                    val appContext = context.applicationContext
                    val providerRepo = ProviderRepository(appContext)
                    val entity = providerRepo.getActiveProvider()
                    val r = MediaRepository(appContext, entity?.id ?: 0L)
                    if (entity != null) {
                        val password = providerRepo.getPassword(entity.id) ?: ""
                        val provider = MediaProviderFactory.create(entity, appContext, password)
                        provider.connect()
                        r.setProvider(provider)
                    }
                    mediaRepository = r
                    isFavorite = r.isFavorite(seriesId, ContentType.TV_SHOWS)
                    r
                }
            }

        val result = repo.getSeriesDetail(seriesId)
        result.fold(
            onSuccess = { detail ->
                seriesDetail = detail
                isLoading = false
            },
            onFailure = { e ->
                error = e.message ?: "Failed to load series info"
                isLoading = false
            },
        )
    }

    // Provide UI scale for all child composables
    CompositionLocalProvider(LocalUiScale provides uiScale) {
        when {
            isLoading -> {
                LoadingScreen()
            }
            error != null -> {
                ErrorScreen(
                    message = error ?: "Unknown error",
                    onBack = onBack,
                )
            }
            seriesDetail != null -> {
                EpisodeListContent(
                    seriesDetail = seriesDetail!!,
                    seriesName = seriesName,
                    categoryId = categoryId,
                    mediaRepository = mediaRepository!!,
                    isFavorite = isFavorite,
                    onToggleFavorite = {
                        val repo = mediaRepository ?: return@EpisodeListContent
                        if (isFavorite) {
                            repo.removeFavorite(seriesId, ContentType.TV_SHOWS)
                        } else {
                            repo.addFavorite(seriesId, seriesName, categoryId, ContentType.TV_SHOWS)
                        }
                        isFavorite = !isFavorite
                    },
                    onEpisodeSelected = onEpisodeSelected,
                    onRefresh = { refresh() },
                    onBack = onBack,
                )
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
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val providerName by remember { mutableStateOf(appSettings.providerName) }
    val listState = rememberLazyListState()
    val scale = LocalUiScale.current
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

    // Selected episode for detail panel
    var selectedEpisode by remember { mutableStateOf<DomainEpisodeItem?>(null) }

    // Handle back press: dismiss detail panel first, then navigate back
    BackHandler(enabled = selectedEpisode != null) {
        selectedEpisode = null
    }

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

    // Use API seasons if available, otherwise derive from episode map keys
    val sortedSeasons =
        remember(seriesDetail) {
            val apiSeasons = seriesDetail.seasons.sortedBy { it.seasonNumber }
            if (apiSeasons.isNotEmpty()) {
                apiSeasons
            } else {
                seriesDetail.episodes.keys
                    .mapNotNull { key -> key.toIntOrNull() }
                    .sorted()
                    .map { num ->
                        SeasonInfo(
                            seasonNumber = num,
                            name = "Season $num",
                            episodeCount =
                                seriesDetail.episodes[num.toString()]?.size ?: 0,
                        )
                    }
            }
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

    // Accordion: only one season expanded at a time (first season by default)
    var expandedSeasons by remember(seriesDetail) {
        mutableStateOf(
            if (hasMultipleSeasons && sortedSeasons.isNotEmpty()) setOf(sortedSeasons.first().seasonNumber) else emptySet(),
        )
    }

    // Auto-expand season with next unwatched/in-progress episode
    LaunchedEffect(seriesDetail) {
        if (!hasMultipleSeasons) return@LaunchedEffect

        // ⚡ Bolt: Avoid flatten().map to prevent intermediate list allocations
        val allEpisodeIds = mutableListOf<String>()
        for (episodes in seriesDetail.episodes.values) {
            for (ep in episodes) {
                allEpisodeIds.add(ep.id)
            }
        }

        val allWatched = mediaRepository.getPlaybackPositionsSuspend(allEpisodeIds, ContentType.TV_SHOWS)

        for (season in sortedSeasons) {
            val seasonKey = season.seasonNumber.toString()
            val episodes =
                seriesDetail.episodes[seasonKey]
                    ?.sortedBy { it.episodeNumber }
                    ?: continue
            for (episode in episodes) {
                val watched = allWatched[episode.id]
                if (watched == null || !watched.isCompleted) {
                    expandedSeasons = setOf(season.seasonNumber)
                    return@LaunchedEffect
                }
            }
        }
    }

    // Flat ordered list of all episodes across seasons (for prev/next navigation)
    val flatEpisodes =
        remember(sortedSeasons, sortedEpisodesBySeason) {
            // ⚡ Bolt: Avoid flatMap to prevent intermediate list allocations
            val result = mutableListOf<DomainEpisodeItem>()
            for (season in sortedSeasons) {
                val episodes = sortedEpisodesBySeason[season.seasonNumber.toString()]
                if (episodes != null) {
                    result.addAll(episodes)
                }
            }
            result

        }

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
                                    imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                    tint = if (isFavorite) CinemaAccent else CinemaTextPrimary
                                )
                            }
                        )
                        // Refresh button
                        CinemaIconButton(
                            onClick = {
                                isRefreshing = true
                                onRefresh()
                                isRefreshing = false
                            },
                            enabled = !isRefreshing,
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh series info",
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
                        text = "$totalEpisodes episodes",
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
                                seriesDetail.metadata.rating?.let { "Rating: $it" },
                                seriesDetail.metadata.cast?.let { "Cast: $it" },
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
                val seasonLabel = episode.seasonNumber?.let { "Season $it" } ?: ""
                val episodeLabel = "Episode ${episode.episodeNumber}"
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
                                text = "Ends at $endsAtText",
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
                            val resumeTimeText = formatMillis(resumePositionMs)
                            CinemaPrimaryButton(
                                onClick = {
                                    onPlay(episode.id, episode.title, extension, false)
                                },
                                text = "▶ Resume from $resumeTimeText",
                                modifier = Modifier.focusRequester(playButtonFocusRequester),
                            )
                            CinemaSecondaryButton(
                                onClick = {
                                    onPlay(episode.id, episode.title, extension, true)
                                },
                                text = "Start from Beginning",
                            )
                        } else {
                            CinemaPrimaryButton(
                                onClick = {
                                    onPlay(episode.id, episode.title, extension, false)
                                },
                                text = "▶ Play Episode",
                                modifier = Modifier.focusRequester(playButtonFocusRequester),
                            )
                        }
                    }

                    // Prev/Next episode hint (remote media keys)
                    if (previousEpisode != null || nextEpisode != null) {
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        val hint =
                            buildString {
                                if (previousEpisode != null) append("⏮ Previous")
                                if (previousEpisode != null && nextEpisode != null) append("   ")
                                if (nextEpisode != null) append("Next ⏭")
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
                            text = "Cast: $it",
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
                            text = "Director: $it",
                            style = detailScaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                    }

                    // Air date
                    episode.metadata.airDate?.takeIf { it.isNotBlank() }?.let {
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                        Text(
                            text = "Aired: $it",
                            style = detailScaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                    }

                    // Bitrate
                    episode.metadata.bitrate?.takeIf { it > 0 }?.let {
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                        Text(
                            text = "Bitrate: $it kbps",
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
            imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = CinemaAccentLight,
            modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
        )
        // Season cover thumbnail (if available)
        season.coverUrl?.let { url ->
            CinemaThumbnail(
                url = url,
                fallbackLetter = null,
                contentType = ThumbnailContentType.TV_SHOW,
                modifier =
                    Modifier.size(
                        width = TvDimensions.posterHeight.scaled(scale),
                        height = TvDimensions.posterHeight.scaled(scale),
                    ),
            )
        }
        Text(
            text = "Season ${season.seasonNumber}",
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontSize =
                        MaterialTheme.typography.headlineSmall.fontSize
                            .scaled(scale),
                ),
            color = CinemaAccentLight,
        )
        Text(
            text = "$episodeCount episodes",
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

@Composable
private fun EpisodeCard(
    episode: DomainEpisodeItem,
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
            }
        }
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TvDimensions.cardHeight.scaled(scale)),
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurface,
                focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
            ),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleContent,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        glow =
            CardDefaults.glow(
                focusedGlow =
                    androidx.tv.material3.Glow(
                        elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                        elevation = TvFocusTokens.focusShadowElevation,
                    ),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.md.scaled(scale)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Episode thumbnail
            CinemaThumbnail(
                url = episode.thumbnailUrl,
                fallbackLetter = episode.title.firstOrNull(),
                contentType = ThumbnailContentType.TV_SHOW,
                modifier =
                    Modifier.size(
                        width = TvDimensions.posterWidth.scaled(scale),
                        height = TvDimensions.posterHeight.scaled(scale),
                    ),
            )
            Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))

            // Episode number
            Text(
                text = "E${episode.episodeNumber}",
                style = cardScaledStyles.titleMedium,
                color = CinemaAccentLight,
                modifier = Modifier.width(Spacing.xxl.scaled(scale)),
            )

            Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))

            // Episode title and plot
            Column(
                modifier = Modifier.weight(1f),
            ) {
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
                text = "Loading episodes...",
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
                text = "Error Loading Episodes",
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
                text = "Back to Series List",
            )
        }
    }
}

/**
 * Parses a duration string that can be either raw seconds ("7200") or h:mm:ss / m:ss format ("1:23:45").
 * Returns total seconds, or null if unparseable.
 */
private fun parseDurationToSeconds(duration: String): Long? {
    duration.toLongOrNull()?.let { return it }
    val parts = duration.split(":")
    return when (parts.size) {
        3 -> {
            val h = parts[0].toLongOrNull() ?: return null
            val m = parts[1].toLongOrNull() ?: return null
            val s = parts[2].toLongOrNull() ?: return null
            h * 3600 + m * 60 + s
        }
        2 -> {
            val m = parts[0].toLongOrNull() ?: return null
            val s = parts[1].toLongOrNull() ?: return null
            m * 60 + s
        }
        else -> null
    }
}

/**
 * Computes "Ends at" time based on duration and optional resume position.
 */
private fun computeEndsAt(
    context: android.content.Context,
    duration: String?,
    resumePositionMs: Long,
): String? {
    if (duration == null) return null
    val totalSeconds = parseDurationToSeconds(duration) ?: return null
    if (totalSeconds <= 0) return null
    val totalMs = totalSeconds * 1000
    val remainingMs = if (resumePositionMs > 0) (totalMs - resumePositionMs).coerceAtLeast(0) else totalMs
    val calendar = java.util.Calendar.getInstance()
    calendar.add(java.util.Calendar.MILLISECOND, remainingMs.toInt())
    return org.njarasoa.fijerena.core.player.model.TimeFormat
        .formatClockTime(context, calendar.time)
}

/**
 * Formats milliseconds to "1:23:45" or "23:45" style.
 */
private fun formatMillis(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

/**
 * Formats duration string to human-readable "2h 0m" form.
 */
private fun formatDuration(duration: String): String {
    val seconds = parseDurationToSeconds(duration) ?: return duration
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}
