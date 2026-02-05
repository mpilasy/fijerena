package org.njarasoa.fijerena.feature.episode

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.EpisodeItem as DomainEpisodeItem
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius

data class DisplayEpisodeItem(
    val seasonNumber: String,
    val episode: DomainEpisodeItem
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEpisodeSelectionScreen(
    seriesId: String,
    seriesName: String,
    categoryId: String,
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val mediaRepository = remember {
        val appContext = context.applicationContext
        val providerRepo = ProviderRepository(appContext)
        kotlinx.coroutines.runBlocking {
            val entity = providerRepo.getActiveProvider()
            if (entity != null) {
                val resolvedRepo = MediaRepository(appContext, entity.id)
                val password = providerRepo.getPassword(entity.id) ?: ""
                val provider = MediaProviderFactory.create(entity, appContext, password)
                provider.connect() // Authenticate before making API calls
                resolvedRepo.setProvider(provider)
                resolvedRepo
            } else MediaRepository(appContext, 0L)
        }
    }

    var seriesDetail by remember { mutableStateOf<SeriesDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Load series info on launch or refresh
    LaunchedEffect(seriesId, refreshTrigger) {
        if (!isRefreshing) isLoading = true
        error = null
        val result = mediaRepository.getSeriesDetail(seriesId)
        result.fold(
            onSuccess = { detail ->
                seriesDetail = detail
                isLoading = false
                isRefreshing = false
            },
            onFailure = { e ->
                error = e.message ?: "Failed to load series info"
                isLoading = false
                isRefreshing = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(seriesName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            refreshTrigger++
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        EpisodeListContent(
                            seriesDetail = seriesDetail!!,
                            onEpisodeSelected = onEpisodeSelected
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
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String) -> Unit
) {
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
        modifier = Modifier.fillMaxSize()
    ) {
        // Series info
        seriesDetail.metadata.plot?.let { plot ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = plot,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Episode count
        Text(
            text = "${allEpisodes.size} episodes",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
        )

        // Episodes list
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Season and Episode number
            Text(
                text = "S${seasonNumber.padStart(2, '0')} E${episode.episodeNumber.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Episode title
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Episode info (duration, rating, etc.)
            episode.metadata.duration?.let { duration ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
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

private fun formatDuration(duration: String): String {
    return try {
        val minutes = duration.toIntOrNull() ?: return duration
        val hours = minutes / 60
        val mins = minutes % 60
        when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m"
            else -> "${duration}m"
        }
    } catch (e: Exception) {
        duration
    }
}
