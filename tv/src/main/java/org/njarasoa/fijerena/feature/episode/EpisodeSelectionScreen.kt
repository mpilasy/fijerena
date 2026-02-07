@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.episode

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import org.njarasoa.fijerena.core.player.domain.SeasonInfo
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
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
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusableNoScale
import org.njarasoa.fijerena.core.player.domain.EpisodeItem as DomainEpisodeItem

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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val mediaRepository = remember {
        val appContext = context.applicationContext
        kotlinx.coroutines.runBlocking {
            val providerRepo = ProviderRepository(appContext)
            val entity = providerRepo.getActiveProvider()
            val repo = MediaRepository(appContext, entity?.id ?: 0L)
            if (entity != null) {
                val password = providerRepo.getPassword(entity.id) ?: ""
                val provider = MediaProviderFactory.create(entity, appContext, password)
                provider.connect() // Authenticate before making API calls
                repo.setProvider(provider)
            }
            repo
        }
    }

    var seriesDetail by remember { mutableStateOf<SeriesDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    fun refresh() {
        refreshTrigger++
    }

    // Load series info on launch
    LaunchedEffect(seriesId, refreshTrigger) {
        isLoading = true
        error = null

        val result = mediaRepository.getSeriesDetail(seriesId)
        result.fold(
            onSuccess = { detail ->
                seriesDetail = detail
                isLoading = false
            },
            onFailure = { e ->
                error = e.message ?: "Failed to load series info"
                isLoading = false
            }
        )
    }

    when {
        isLoading -> {
            LoadingScreen()
        }
        error != null -> {
            ErrorScreen(
                message = error ?: "Unknown error",
                onBack = onBack
            )
        }
        seriesDetail != null -> {
            EpisodeListContent(
                seriesDetail = seriesDetail!!,
                seriesName = seriesName,
                mediaRepository = mediaRepository,
                onEpisodeSelected = onEpisodeSelected,
                onRefresh = { refresh() },
                onBack = onBack
            )
        }
    }
}

@Composable
private fun EpisodeListContent(
    seriesDetail: SeriesDetail,
    seriesName: String,
    mediaRepository: MediaRepository,
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val providerName by remember { mutableStateOf(appSettings.providerName) }
    val listState = rememberLazyListState()

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
        label = "refresh_rotation"
    )

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            while (isRefreshing) {
                targetRotation += 360f
                kotlinx.coroutines.delay(CinemaAnimation.loadingDebounceMs)
            }
        }
    }

    // Sort seasons and build grouped episode data
    val sortedSeasons = remember(seriesDetail) {
        seriesDetail.seasons.sortedBy { it.seasonNumber }
    }
    val totalEpisodes = remember(seriesDetail) {
        seriesDetail.episodes.values.sumOf { it.size }
    }
    val hasMultipleSeasons = sortedSeasons.size > 1

    // Track which seasons are expanded (all expanded by default for multi-season shows)
    var expandedSeasons by remember(seriesDetail) {
        mutableStateOf(
            if (hasMultipleSeasons) sortedSeasons.map { it.seasonNumber }.toSet() else emptySet()
        )
    }

    if (selectedEpisode != null) {
        // Show episode detail panel
        EpisodeDetailPanel(
            episode = selectedEpisode!!,
            seriesDetail = seriesDetail,
            seriesName = seriesName,
            providerName = providerName,
            mediaRepository = mediaRepository,
            onPlay = { episodeId, episodeTitle, extension, startFromBeginning ->
                onEpisodeSelected(episodeId, episodeTitle, extension, startFromBeginning)
            },
            onBack = { selectedEpisode = null }
        )
    } else {
        // Show episode list
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical)
        ) {
            // Header with series info and back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            text = seriesName,
                            style = MaterialTheme.typography.displaySmall,
                            color = CinemaTextPrimary
                        )
                        // Always show refresh button
                        IconButton(
                            onClick = {
                                isRefreshing = true
                                onRefresh()
                                isRefreshing = false
                            },
                            enabled = !isRefreshing,
                            modifier = Modifier.size(TvDimensions.iconMedium)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh series info",
                                tint = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                modifier = Modifier
                                    .size(TvDimensions.iconSmall)
                                    .rotate(rotation)
                            )
                        }
                    }
                    // Show episode count
                    Text(
                        text = "$totalEpisodes episodes",
                        style = MaterialTheme.typography.labelSmall,
                        color = CinemaTextSecondary
                    )
                    seriesDetail.metadata.plot?.let { plot ->
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = plot,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Series metadata: genre, rating, cast
                    val metadataParts = remember(seriesDetail) {
                        listOfNotNull(
                            seriesDetail.metadata.genre,
                            seriesDetail.metadata.rating?.let { "Rating: $it" },
                            seriesDetail.metadata.cast?.let { "Cast: $it" }
                        )
                    }
                    if (metadataParts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = metadataParts.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = CinemaAccentLight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(Spacing.md))
                Text(
                    text = providerName,
                    style = MaterialTheme.typography.titleSmall,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            // Season-grouped episodes list
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxSize()
            ) {
                sortedSeasons.forEach { season ->
                    val seasonKey = season.seasonNumber.toString()
                    val seasonEpisodes = seriesDetail.episodes[seasonKey]
                        ?.sortedBy { it.episodeNumber }
                        ?: emptyList()
                    val isExpanded = !hasMultipleSeasons || season.seasonNumber in expandedSeasons

                    // Season header (skip if only 1 season)
                    if (hasMultipleSeasons) {
                        item(key = "season_header_$seasonKey") {
                            SeasonHeader(
                                season = season,
                                episodeCount = seasonEpisodes.size,
                                isExpanded = isExpanded,
                                onToggle = {
                                    expandedSeasons = if (isExpanded) {
                                        expandedSeasons - season.seasonNumber
                                    } else {
                                        expandedSeasons + season.seasonNumber
                                    }
                                }
                            )
                        }
                    }

                    if (isExpanded) {
                        items(seasonEpisodes, key = { it.id }) { episode ->
                            EpisodeCard(
                                episode = episode,
                                onClick = {
                                    selectedEpisode = episode
                                }
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
    providerName: String,
    mediaRepository: MediaRepository,
    onPlay: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onBack: () -> Unit
) {
    val extension = episode.extension ?: "mp4"

    // Focus requester for Play button
    val playButtonFocusRequester = remember { FocusRequester() }

    // Load resume position
    var resumePositionMs by remember { mutableStateOf(0L) }

    LaunchedEffect(episode.id) {
        val watched = mediaRepository.getPlaybackPositionSuspend(episode.id, "TV_SHOWS")
        if (watched != null && !watched.isCompleted && watched.playbackPosition > 0 && watched.duration > 0) {
            val progress = (watched.playbackPosition.toFloat() / watched.duration.toFloat()) * 100f
            if (progress in 2.0..95.0) {
                resumePositionMs = watched.playbackPosition
            }
        }
    }

    // Request focus on Play button when screen loads
    LaunchedEffect(Unit) {
        playButtonFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .focusable()
            .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical)
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = seriesName,
                    style = MaterialTheme.typography.displaySmall,
                    color = CinemaTextPrimary
                )
                // Season / episode label
                val seasonLabel = episode.seasonNumber?.let { "Season $it" } ?: ""
                val episodeLabel = "Episode ${episode.episodeNumber}"
                val subLabel = listOfNotNull(
                    seasonLabel.ifEmpty { null },
                    episodeLabel
                ).joinToString(" · ")
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaAccentLight
                )
            }
            Spacer(modifier = Modifier.width(Spacing.md))
            Text(
                text = providerName,
                style = MaterialTheme.typography.titleSmall,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        // Episode content: thumbnail + metadata
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            // Episode thumbnail
            CinemaThumbnail(
                url = episode.thumbnailUrl,
                fallbackLetter = episode.title.firstOrNull(),
                contentType = ThumbnailContentType.TV_SHOW,
                modifier = Modifier
                    .width(TvDimensions.posterWidth)
                    .height(TvDimensions.posterHeightLarge)
            )

            // Metadata in glass panel
            GlassPanel(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    // Episode title
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = CinemaTextPrimary
                    )

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    // Metadata row: rating | duration | ends at
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Prefer episode rating, fallback to series rating
                        val rating = episode.metadata.rating ?: seriesDetail.metadata.rating
                        rating?.let {
                            Text(
                                text = "★ $it",
                                style = MaterialTheme.typography.titleMedium,
                                color = CinemaAccent
                            )
                        }
                        episode.metadata.duration?.let { duration ->
                            Text(
                                text = formatDuration(duration),
                                style = MaterialTheme.typography.titleMedium,
                                color = CinemaTextSecondary
                            )
                        }
                        // "Ends at" based on remaining duration
                        val endsAtContext = LocalContext.current
                        val endsAtText = remember(episode.metadata.duration, resumePositionMs) {
                            computeEndsAt(endsAtContext, episode.metadata.duration, resumePositionMs)
                        }
                        if (endsAtText != null) {
                            Text(
                                text = "Ends at $endsAtText",
                                style = MaterialTheme.typography.titleMedium,
                                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textMedium)
                            )
                        }
                    }

                    // Genre (from series)
                    seriesDetail.metadata.genre?.let { genre ->
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = genre,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CinemaAccent
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.lg))

                    // Play / Resume buttons
                    val hasResume = resumePositionMs > 0L
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasResume) {
                            val resumeTimeText = formatMillis(resumePositionMs)
                            CinemaPrimaryButton(
                                onClick = {
                                    onPlay(episode.id, episode.title, extension, false)
                                },
                                text = "▶ Resume from $resumeTimeText",
                                modifier = Modifier.focusRequester(playButtonFocusRequester)
                            )
                            CinemaSecondaryButton(
                                onClick = {
                                    onPlay(episode.id, episode.title, extension, true)
                                },
                                text = "Start from Beginning"
                            )
                        } else {
                            CinemaPrimaryButton(
                                onClick = {
                                    onPlay(episode.id, episode.title, extension, false)
                                },
                                text = "▶ Play Episode",
                                modifier = Modifier.focusRequester(playButtonFocusRequester)
                            )
                        }
                    }

                    // Plot/Description
                    episode.metadata.plot?.let { plot ->
                        Spacer(modifier = Modifier.height(Spacing.lg))
                        Text(
                            text = plot,
                            style = MaterialTheme.typography.bodyLarge,
                            color = CinemaTextPrimary,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // Cast (episode-level, fallback to series)
                    val cast = episode.metadata.cast ?: seriesDetail.metadata.cast
                    cast?.let {
                        Text(
                            text = "Cast: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                    }

                    // Director (episode-level, fallback to series)
                    val director = episode.metadata.director ?: seriesDetail.metadata.director
                    director?.let {
                        Text(
                            text = "Director: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
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
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusableNoScale()
            .clickable { onToggle() }
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = CinemaAccentLight,
            modifier = Modifier.size(TvDimensions.iconSmall)
        )
        // Season cover thumbnail (if available)
        season.coverUrl?.let { url ->
            CinemaThumbnail(
                url = url,
                fallbackLetter = null,
                contentType = ThumbnailContentType.TV_SHOW,
                modifier = Modifier.size(
                    width = TvDimensions.posterHeight,
                    height = TvDimensions.posterHeight
                )
            )
        }
        Text(
            text = "Season ${season.seasonNumber}",
            style = MaterialTheme.typography.headlineSmall,
            color = CinemaAccentLight
        )
        Text(
            text = "$episodeCount episodes",
            style = MaterialTheme.typography.labelMedium,
            color = CinemaTextSecondary
        )
    }
}

@Composable
private fun EpisodeCard(
    episode: DomainEpisodeItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(TvDimensions.cardHeight),
        colors = CardDefaults.colors(
            containerColor = CinemaSurface,
            focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.tint)
        ),
        scale = CardDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScaleContent,
            pressedScale = TvFocusTokens.pressedScaleSubtle
        ),
        glow = CardDefaults.glow(
            focusedGlow = androidx.tv.material3.Glow(
                elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                elevation = TvFocusTokens.focusShadowElevation
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode thumbnail
            CinemaThumbnail(
                url = episode.thumbnailUrl,
                fallbackLetter = episode.title.firstOrNull(),
                contentType = ThumbnailContentType.TV_SHOW,
                modifier = Modifier.size(
                    width = TvDimensions.posterWidth,
                    height = TvDimensions.posterHeight
                )
            )
            Spacer(modifier = Modifier.width(Spacing.sm))

            // Episode number
            Text(
                text = "E${episode.episodeNumber}",
                style = MaterialTheme.typography.titleMedium,
                color = CinemaAccentLight,
                modifier = Modifier.width(Spacing.xxl)
            )

            Spacer(modifier = Modifier.width(Spacing.sm))

            // Episode title and plot
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                episode.metadata.plot?.let { plot ->
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.bodySmall,
                        color = CinemaTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Duration
            episode.metadata.duration?.let { duration ->
                Spacer(modifier = Modifier.width(Spacing.md))
                Text(
                    text = duration,
                    style = MaterialTheme.typography.labelMedium,
                    color = CinemaTextSecondary
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(TvDimensions.progressIndicator),
                color = CinemaAccent
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = "Loading episodes...",
                style = MaterialTheme.typography.titleLarge,
                color = CinemaTextSecondary
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.xl)
        ) {
            Text(
                text = "Error Loading Episodes",
                style = MaterialTheme.typography.displayMedium,
                color = CinemaError
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextSecondary
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            CinemaSecondaryButton(
                onClick = onBack,
                text = "Back to Series List"
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
private fun computeEndsAt(context: android.content.Context, duration: String?, resumePositionMs: Long): String? {
    if (duration == null) return null
    val totalSeconds = parseDurationToSeconds(duration) ?: return null
    if (totalSeconds <= 0) return null
    val totalMs = totalSeconds * 1000
    val remainingMs = if (resumePositionMs > 0) (totalMs - resumePositionMs).coerceAtLeast(0) else totalMs
    val calendar = java.util.Calendar.getInstance()
    calendar.add(java.util.Calendar.MILLISECOND, remainingMs.toInt())
    return org.njarasoa.fijerena.core.player.model.TimeFormat.formatClockTime(context, calendar.time)
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
