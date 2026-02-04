@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.episode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.EpisodeItem as DomainEpisodeItem
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.*

/**
 * Episode selection screen for TV shows.
 *
 * Features:
 * - Displays series information (title, plot)
 * - Lists all episodes grouped by season
 * - D-pad friendly navigation
 * - Loads episode data from MediaRepository
 */
@Composable
fun EpisodeSelectionScreen(
    seriesId: String,
    seriesName: String,
    categoryId: String,
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String) -> Unit,
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
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val providerName by remember { mutableStateOf(appSettings.providerName) }
    val listState = rememberLazyListState()

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

    // Flatten episodes from all seasons into a single list
    val allEpisodes = remember(seriesDetail) {
        seriesDetail.episodes.flatMap { (seasonNumber, episodes) ->
            episodes.map { episode ->
                DisplayEpisodeItem(
                    seasonNumber = seasonNumber,
                    episode = episode
                )
            }
        }.sortedWith(
            compareBy<DisplayEpisodeItem> { it.seasonNumber.toIntOrNull() ?: 0 }
                .thenBy { it.episode.episodeNumber }
        )
    }

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
                                .size(24.dp)
                                .rotate(rotation)
                        )
                    }
                }
                // Show episode count
                val totalEpisodes = allEpisodes.size
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
            }
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = providerName,
                    style = MaterialTheme.typography.titleSmall,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                CinemaSecondaryButton(
                    onClick = onBack,
                    text = "Back"
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        // Episodes list
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxSize()
        ) {
            items(allEpisodes, key = { it.episode.id }) { episodeItem ->
                EpisodeCard(
                    seasonNumber = episodeItem.seasonNumber,
                    episode = episodeItem.episode,
                    onClick = {
                        onEpisodeSelected(
                            episodeItem.episode.id,
                            episodeItem.episode.title,
                            episodeItem.episode.extension ?: "mp4"
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    seasonNumber: String,
    episode: DomainEpisodeItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(TvDimensions.cardHeight),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(TvFocusTokens.focusBorderWidth, CinemaAccentLight)
            )
        ),
        colors = CardDefaults.colors(
            containerColor = CinemaSurface,
            focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.focusedTint)
        ),
        scale = CardDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScale,
            pressedScale = TvFocusTokens.pressedScale
        ),
        glow = CardDefaults.glow(
            focusedGlow = androidx.tv.material3.Glow(
                elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.focusedGlow),
                elevation = TvFocusTokens.glowElevation
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Season and episode number
            Column(
                modifier = Modifier.width(TvDimensions.epgTimeSlotWidth),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Season $seasonNumber",
                    style = MaterialTheme.typography.labelMedium,
                    color = CinemaTextSecondary
                )
                Text(
                    text = "Episode ${episode.episodeNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextPrimary
                )
            }

            Spacer(modifier = Modifier.width(Spacing.lg))

            // Episode title
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
                        maxLines = 1,
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

private data class DisplayEpisodeItem(
    val seasonNumber: String,
    val episode: DomainEpisodeItem
)
