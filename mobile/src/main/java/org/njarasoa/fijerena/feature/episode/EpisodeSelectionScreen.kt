package org.njarasoa.fijerena.feature.episode

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.player.model.Episode
import org.njarasoa.fijerena.core.player.model.SeriesInfo

data class EpisodeItem(
    val seasonNumber: String,
    val episode: Episode
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEpisodeSelectionScreen(
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

    // Load series info on launch
    LaunchedEffect(seriesId) {
        isLoading = true
        error = null

        when (val sessionResult = repository.restoreSession()) {
            is Result.Success -> {
                when (val infoResult = repository.getSeriesInfo(seriesId)) {
                    is Result.Success -> {
                        seriesInfo = infoResult.data
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
                seriesInfo != null -> {
                    EpisodeListContent(
                        seriesInfo = seriesInfo!!,
                        onEpisodeSelected = onEpisodeSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeListContent(
    seriesInfo: SeriesInfo,
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String) -> Unit
) {
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
        modifier = Modifier.fillMaxSize()
    ) {
        // Series info
        seriesInfo.info?.plot?.let { plot ->
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Season and Episode number
            Text(
                text = "S${seasonNumber.padStart(2, '0')} E${episode.episodeNum.toString().padStart(2, '0')}",
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
            episode.info?.duration?.let { duration ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
