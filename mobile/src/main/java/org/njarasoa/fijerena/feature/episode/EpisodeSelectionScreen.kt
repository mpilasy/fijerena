package org.njarasoa.fijerena.feature.episode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.SeasonInfo
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
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
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var mediaRepository by remember { mutableStateOf<MediaRepository?>(null) }

    var seriesDetail by remember { mutableStateOf<SeriesDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var selectedEpisode by remember { mutableStateOf<DomainEpisodeItem?>(null) }
    var isFavorite by remember { mutableStateOf(false) }

    // Handle back press: dismiss detail panel first, then navigate back
    BackHandler(enabled = selectedEpisode != null) {
        selectedEpisode = null
    }

    // Load series info on launch or refresh
    LaunchedEffect(seriesId, refreshTrigger) {
        if (!isRefreshing) isLoading = true
        error = null

        // Initialize repository asynchronously (avoids runBlocking on main thread)
        val repo =
            mediaRepository ?: run {
                val appContext = context.applicationContext
                val providerRepo = ProviderRepository(appContext)
                val entity = providerRepo.getActiveProvider()
                val r =
                    if (entity != null) {
                        val resolvedRepo = MediaRepository(appContext, entity.id)
                        val password = providerRepo.getPassword(entity.id) ?: ""
                        val provider = MediaProviderFactory.create(entity, appContext, password)
                        provider.connect()
                        resolvedRepo.setProvider(provider)
                        resolvedRepo
                    } else {
                        MediaRepository(appContext, 0L)
                    }
                mediaRepository = r
                isFavorite = r.isFavorite(seriesId, ContentType.TV_SHOWS)
                r
            }

        val result = repo.getSeriesDetail(seriesId)
        val defaultError = context.getString(R.string.series_error_load_failed)
        result.fold(
            onSuccess = { detail ->
                seriesDetail = detail
                isLoading = false
                isRefreshing = false
            },
            onFailure = { e ->
                error = e.message ?: defaultError
                isLoading = false
                isRefreshing = false
            },
        )
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
                    CinemaIconButton(onClick = {
                        mediaRepository?.let { repo ->
                            if (isFavorite) {
                                repo.removeFavorite(seriesId, ContentType.TV_SHOWS)
                            } else {
                                repo.addFavorite(seriesId, seriesName, categoryId, ContentType.TV_SHOWS)
                            }
                            isFavorite = !isFavorite
                        }
                    },
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
                imageUrl = selectedEpisode?.thumbnailUrl ?: seriesDetail?.coverUrl,
            )
            when {
                isLoading -> {
                    LoadingScreen()
                }
                error != null -> {
                    ErrorScreen(
                        message = error ?: stringResource(R.string.common_error),
                        onBack = onBack,
                    )
                }
                seriesDetail != null && selectedEpisode != null -> {
                    val detail = seriesDetail!!
                    val flatEpisodes =
                        remember(detail) {
                            flattenEpisodes(detail)
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
                        mediaRepository = mediaRepository!!,
                        previousEpisode = previousEpisode,
                        nextEpisode = nextEpisode,
                        onNavigate = { next -> selectedEpisode = next },
                        onPlay = { episodeId, episodeTitle, extension, startFromBeginning ->
                            onEpisodeSelected(episodeId, episodeTitle, extension, startFromBeginning)
                        },
                    )
                }
                seriesDetail != null -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            refreshTrigger++
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        EpisodeListContent(
                            seriesDetail = seriesDetail!!,
                            mediaRepository = mediaRepository!!,
                            onEpisodeSelected = { episode ->
                                selectedEpisode = episode
                            },
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
    mediaRepository: MediaRepository,
    onEpisodeSelected: (DomainEpisodeItem) -> Unit,
) {
    val context = LocalContext.current
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
                            name = context.getString(R.string.series_season_name_format, num),
                            episodeCount =
                                seriesDetail.episodes[num.toString()]?.size ?: 0,
                        )
                    }
            }
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

    // Accordion: only one season expanded at a time (first season by default)
    var expandedSeasons by remember(seriesDetail) {
        mutableStateOf(
            if (hasMultipleSeasons && sortedSeasons.isNotEmpty()) setOf(sortedSeasons.first().seasonNumber) else emptySet(),
        )
    }
    // Set by the manual season-header toggle below, so the auto-expand effect doesn't
    // clobber a choice the user already made while the playback-position lookups were in flight.
    var hasManuallyToggledSeasons by remember(seriesDetail) { mutableStateOf(false) }

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
            val episodes = sortedEpisodesBySeason[seasonKey] ?: continue
            for (episode in episodes) {
                val watched = allWatched[episode.id]
                if (watched == null || !watched.isCompleted) {
                    if (!hasManuallyToggledSeasons) {
                        expandedSeasons = setOf(season.seasonNumber)
                    }
                    return@LaunchedEffect
                }
            }
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
                    seriesDetail.metadata.rating?.let { context.getString(R.string.series_rating_format, it) },
                    seriesDetail.metadata.contentRating,
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
                        Text(
                            text = plot,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
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

        // Episode count
        Text(
            text = stringResource(R.string.series_total_episodes_format, totalEpisodes),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )

        // Season-grouped episodes list
        LazyColumn(
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
                            onClick = {
                                onEpisodeSelected(episode)
                            },
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
    previousEpisode: DomainEpisodeItem?,
    nextEpisode: DomainEpisodeItem?,
    onNavigate: (DomainEpisodeItem) -> Unit,
    onPlay: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
) {
    val extension = episode.extension ?: "mp4"

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
                        text = "★ $it",
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
            val resumeTimeText = formatMillis(resumePositionMs)
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
    onClick: () -> Unit,
) {
    CinemaCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(CinemaSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            // Episode thumbnail
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
                // Episode number
                Text(
                    text = stringResource(R.string.series_episode_number_short, episode.episodeNumber),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )

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

private fun flattenEpisodes(detail: SeriesDetail): List<DomainEpisodeItem> {
    val seasonNumbers =
        if (detail.seasons.isNotEmpty()) {
            detail.seasons.map { it.seasonNumber }.sorted()
        } else {
            detail.episodes.keys.mapNotNull { it.toIntOrNull() }.sorted()
        }

    // ⚡ Bolt: Use pre-sized ArrayList to avoid intermediate allocations from flatMap
    val totalSize = detail.episodes.values.sumOf { it.size }
    val result = ArrayList<DomainEpisodeItem>(totalSize)
    for (num in seasonNumbers) {
        val eps = detail.episodes[num.toString()]
        if (eps != null) {
            result.addAll(eps.sortedBy { it.episodeNumber })
        }
    }
    return result
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
