package org.njarasoa.fijerena.feature.episode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.domain.EpisodeItem as DomainEpisodeItem
import org.njarasoa.fijerena.core.player.domain.SeasonInfo
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.EpisodeSelectionViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpisodeSelectionViewModelFactory
import org.njarasoa.fijerena.ui.theme.MobileDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEpisodeSelectionScreen(
    seriesId: String,
    seriesName: String,
    categoryId: String,
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: EpisodeSelectionViewModel = viewModel(
        factory = EpisodeSelectionViewModelFactory(
            context = LocalContext.current.applicationContext,
            seriesId = seriesId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var selectedEpisode by remember { mutableStateOf<DomainEpisodeItem?>(null) }

    val mediaRepository = viewModel.getRepository()

    // Handle back press: dismiss detail panel first, then navigate back
    BackHandler(enabled = selectedEpisode != null) {
        selectedEpisode = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(seriesName) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedEpisode != null) {
                            selectedEpisode = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is EpisodeSelectionViewModel.UiState.Loading -> {
                    LoadingScreen()
                }
                is EpisodeSelectionViewModel.UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onBack = onBack
                    )
                }
                is EpisodeSelectionViewModel.UiState.Success -> {
                    val seriesDetail = state.seriesDetail
                    if (selectedEpisode != null && mediaRepository != null) {
                        EpisodeDetailContent(
                            episode = selectedEpisode!!,
                            seriesDetail = seriesDetail,
                            categoryId = categoryId,
                            mediaRepository = mediaRepository,
                            onPlay = { episodeId, episodeTitle, extension, startFromBeginning ->
                                onEpisodeSelected(episodeId, episodeTitle, extension, startFromBeginning)
                            }
                        )
                    } else if (mediaRepository != null) {
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                viewModel.loadSeriesDetail(isRefresh = true)
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            EpisodeListContent(
                                seriesDetail = seriesDetail,
                                mediaRepository = mediaRepository,
                                onEpisodeSelected = { episode ->
                                    selectedEpisode = episode
                                }
                            )
                        }
                    } else {
                        LoadingScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeListContent(
    seriesDetail: SeriesDetail,
    mediaRepository: MediaRepository,
    onEpisodeSelected: (DomainEpisodeItem) -> Unit
) {
    // Use API seasons if available, otherwise derive from episode map keys
    val sortedSeasons = remember(seriesDetail) {
        val apiSeasons = seriesDetail.seasons.sortedBy { it.seasonNumber }
        if (apiSeasons.isNotEmpty()) apiSeasons
        else seriesDetail.episodes.keys
            .mapNotNull { key -> key.toIntOrNull() }
            .sorted()
            .map { num -> SeasonInfo(seasonNumber = num, name = "Season $num", episodeCount = seriesDetail.episodes[num.toString()]?.size ?: 0) }
    }
    val totalEpisodes = remember(seriesDetail) {
        seriesDetail.episodes.values.sumOf { it.size }
    }
    val hasMultipleSeasons = sortedSeasons.size > 1

    // Accordion: only one season expanded at a time (first season by default)
    var expandedSeasons by remember(seriesDetail) {
        mutableStateOf(
            if (hasMultipleSeasons && sortedSeasons.isNotEmpty()) setOf(sortedSeasons.first().seasonNumber) else emptySet()
        )
    }

    // Auto-expand season with next unwatched/in-progress episode
    LaunchedEffect(seriesDetail) {
        if (!hasMultipleSeasons) return@LaunchedEffect
        for (season in sortedSeasons) {
            val seasonKey = season.seasonNumber.toString()
            val episodes = seriesDetail.episodes[seasonKey]
                ?.sortedBy { it.episodeNumber }
                ?: continue
            for (episode in episodes) {
                val watched = mediaRepository.getPlaybackPositionSuspend(episode.id, "TV_SHOWS")
                if (watched == null || !watched.isCompleted) {
                    expandedSeasons = setOf(season.seasonNumber)
                    return@LaunchedEffect
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Series info header
        val hasPlot = seriesDetail.metadata.plot != null
        val metadataParts = remember(seriesDetail) {
            listOfNotNull(
                seriesDetail.metadata.genre,
                seriesDetail.metadata.rating?.let { "Rating: $it" }
            )
        }
        if (hasPlot || metadataParts.isNotEmpty()) {
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CinemaSpacing.md)
            ) {
                Column(modifier = Modifier.padding(CinemaSpacing.md)) {
                    seriesDetail.metadata.plot?.let { plot ->
                        Text(
                            text = plot,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (metadataParts.isNotEmpty()) {
                        if (hasPlot) Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                        Text(
                            text = metadataParts.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Episode count
        Text(
            text = "$totalEpisodes episodes",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
        )

        // Season-grouped episodes list
        LazyColumn(
            contentPadding = PaddingValues(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
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
                                    emptySet()
                                } else {
                                    setOf(season.seasonNumber)
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
                                onEpisodeSelected(episode)
                            }
                        )
                    }
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
    onPlay: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit
) {
    val extension = episode.extension ?: "mp4"

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(CinemaSpacing.md)
    ) {
        // Episode thumbnail
        CinemaThumbnail(
            url = episode.thumbnailUrl,
            fallbackLetter = episode.title.firstOrNull(),
            contentType = ThumbnailContentType.TV_SHOW,
            modifier = Modifier
                .fillMaxWidth()
                .height(MobileDimensions.posterHeightLarge)
        )

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // Episode title
        Text(
            text = episode.title,
            style = MaterialTheme.typography.headlineLarge
        )

        // Season / Episode label
        val seasonLabel = episode.seasonNumber?.let { "S${it.toString().padStart(2, '0')}" } ?: ""
        val episodeLabel = "E${episode.episodeNumber.toString().padStart(2, '0')}"
        val subLabel = listOfNotNull(
            seasonLabel.ifEmpty { null },
            episodeLabel
        ).joinToString(" ")
        Text(
            text = subLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // Genre (from series)
        seriesDetail.metadata.genre?.let { genre ->
            Text(
                text = genre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Rating and duration on same row
        val rating = episode.metadata.rating ?: seriesDetail.metadata.rating
        val hasDuration = episode.metadata.duration != null
        if (rating != null || hasDuration) {
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rating?.let {
                    Text(
                        text = "★ $it",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                episode.metadata.duration?.let { duration ->
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(CinemaSpacing.lg))

        // Play / Resume buttons + Favorite
        val hasResume = resumePositionMs > 0L
        if (hasResume) {
            val resumeTimeText = formatMillis(resumePositionMs)
            Button(
                onClick = {
                    onPlay(episode.id, episode.title, extension, false)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("▶ Resume from $resumeTimeText")
            }
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            OutlinedButton(
                onClick = {
                    onPlay(episode.id, episode.title, extension, true)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start from Beginning")
            }
        } else {
            Button(
                onClick = {
                    onPlay(episode.id, episode.title, extension, false)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("▶ Play Episode")
            }
        }

        // Plot/Description
        episode.metadata.plot?.let { plot ->
            Spacer(modifier = Modifier.height(CinemaSpacing.lg))
            Text(
                text = plot,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // Cast (episode-level, fallback to series)
        val cast = episode.metadata.cast ?: seriesDetail.metadata.cast
        cast?.let {
            Text(
                text = "Cast: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
        }

        // Director (episode-level, fallback to series)
        val director = episode.metadata.director ?: seriesDetail.metadata.director
        director?.let {
            Text(
                text = "Director: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium)
            )
        }
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
            .clickable { onToggle() }
            .padding(vertical = CinemaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Season ${season.seasonNumber}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "$episodeCount episodes",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CinemaSpacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            // Episode thumbnail
            CinemaThumbnail(
                url = episode.thumbnailUrl,
                fallbackLetter = episode.title.firstOrNull(),
                contentType = ThumbnailContentType.TV_SHOW,
                modifier = Modifier.size(
                    width = MobileDimensions.posterWidth,
                    height = MobileDimensions.posterHeight
                )
            )

            Spacer(modifier = Modifier.width(CinemaSpacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                // Episode number
                Text(
                    text = "E${episode.episodeNumber.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(CinemaSpacing.xxs))

                // Episode title
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Episode plot/summary
                episode.metadata.plot?.let { plot ->
                    Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Duration
                episode.metadata.duration?.let { duration ->
                    Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                    )
                }
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md)
        ) {
            CircularProgressIndicator()
            Text("Loading episodes...")
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
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
            modifier = Modifier.padding(CinemaSpacing.xl)
        ) {
            Text(
                text = "Error Loading Episodes",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge
            )
            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

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
