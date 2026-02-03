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
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.player.model.Episode
import org.njarasoa.fijerena.core.player.model.SeriesInfo
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.*

/**
 * Episode selection screen for TV shows.
 *
 * Features:
 * - Displays series information (title, plot)
 * - Lists all episodes grouped by season
 * - D-pad friendly navigation
 * - Loads episode data from XtreamRepository
 */
@Composable
fun EpisodeSelectionScreen(
    seriesId: Int,
    seriesName: String,
    categoryId: String,
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember {
        val accountManager = AccountManager(context.applicationContext)
        XtreamRepository(accountManager, context.applicationContext)
    }

    var seriesInfo by remember { mutableStateOf<SeriesInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var payloadSize by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    fun refresh() {
        refreshTrigger++
    }

    // Load series info on launch
    LaunchedEffect(seriesId, refreshTrigger) {
        isLoading = true
        error = null

        // Ensure session is restored first
        when (val sessionResult = repository.restoreSession()) {
            is Result.Success -> {
                when (val infoResult = repository.getSeriesInfo(seriesId)) {
                    is Result.Success -> {
                        seriesInfo = infoResult.data
                        payloadSize = repository.getPayloadSize("series_$seriesId")
                        println("EpisodeSelectionScreen: Payload size: $payloadSize")
                        isLoading = false
                    }
                    is Result.Error -> {
                        error = infoResult.message ?: "Failed to load series info"
                        isLoading = false
                    }
                }
            }
            is Result.Error -> {
                error = sessionResult.message ?: "Session expired. Please login again."
                isLoading = false
            }
        }
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
        seriesInfo != null -> {
            val fetchTime = repository.getFetchTimeFormatted("series_$seriesId")
            EpisodeListContent(
                seriesInfo = seriesInfo!!,
                seriesName = seriesName,
                payloadSize = payloadSize,
                fetchTime = fetchTime,
                onEpisodeSelected = onEpisodeSelected,
                onRefresh = { refresh() },
                onBack = onBack
            )
        }
    }
}

@Composable
private fun EpisodeListContent(
    seriesInfo: SeriesInfo,
    seriesName: String,
    payloadSize: String?,
    fetchTime: String?,
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
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "refresh_rotation"
    )

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            while (isRefreshing) {
                targetRotation += 360f
                kotlinx.coroutines.delay(600)
            }
        }
    }

    // Flatten episodes from all seasons into a single list
    val allEpisodes = remember(seriesInfo) {
        seriesInfo.episodes.flatMap { (seasonNumber, episodes) ->
            episodes.map { episode ->
                EpisodeItem(
                    seasonNumber = seasonNumber,
                    episode = episode
                )
            }
        }.sortedWith(
            compareBy<EpisodeItem> { it.seasonNumber.toIntOrNull() ?: 0 }
                .thenBy { it.episode.episodeNum }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xl)
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
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh series info",
                            tint = CinemaTextSecondary.copy(alpha = 0.87f),
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(rotation)
                        )
                    }
                }
                // Always show episode count, optionally show payload size and fetch time
                val totalEpisodes = allEpisodes.size
                val infoText = buildString {
                    if (payloadSize != null) {
                        append(payloadSize)
                        append(" • ")
                    }
                    if (fetchTime != null) {
                        append(fetchTime)
                        append(" • ")
                    }
                    append("$totalEpisodes episodes")
                }
                Text(
                    text = infoText,
                    style = MaterialTheme.typography.labelSmall,
                    color = CinemaTextSecondary
                )
                seriesInfo.info?.plot?.let { plot ->
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextSecondary.copy(alpha = 0.87f),
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
                    color = CinemaTextSecondary.copy(alpha = 0.87f)
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
                            episodeItem.episode.containerExtension
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
    episode: Episode,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, CinemaAccentLight)
            )
        ),
        colors = CardDefaults.colors(
            containerColor = CinemaSurface,
            focusedContainerColor = CinemaAccent.copy(alpha = 0.2f)
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
                modifier = Modifier.width(120.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Season $seasonNumber",
                    style = MaterialTheme.typography.labelMedium,
                    color = CinemaTextSecondary
                )
                Text(
                    text = "Episode ${episode.episodeNum}",
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
                episode.info?.overview?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = CinemaTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Duration
            episode.info?.duration?.let { duration ->
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
                modifier = Modifier.size(48.dp),
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

private data class EpisodeItem(
    val seasonNumber: String,
    val episode: Episode
)
