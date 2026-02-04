package org.njarasoa.fijerena.feature.movie

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.njarasoa.fijerena.core.player.model.VodInfo
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMovieDetailsScreen(
    movieId: Int,
    movieName: String,
    categoryId: String,
    onPlayMovie: (movieId: Int, movieName: String, extension: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember {
        val accountManager = AccountManager(context.applicationContext)
        XtreamRepository(accountManager, context.applicationContext)
    }

    var vodInfo by remember { mutableStateOf<VodInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Load movie info on launch
    LaunchedEffect(movieId) {
        isLoading = true
        error = null

        when (val sessionResult = repository.restoreSession()) {
            is Result.Success -> {
                when (val infoResult = repository.getVodInfo(movieId)) {
                    is Result.Success -> {
                        vodInfo = infoResult.data
                        isLoading = false
                    }
                    is Result.Error -> {
                        error = infoResult.message ?: "Failed to load movie info"
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
                title = { Text("Movie Details") },
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
                vodInfo != null -> {
                    MovieDetailsContent(
                        vodInfo = vodInfo!!,
                        movieId = movieId,
                        movieName = movieName,
                        onPlayMovie = onPlayMovie
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieDetailsContent(
    vodInfo: VodInfo,
    movieId: Int,
    movieName: String,
    onPlayMovie: (movieId: Int, movieName: String, extension: String) -> Unit
) {
    val movieInfo = vodInfo.info
    val extension = vodInfo.movieData?.containerExtension ?: "mp4"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = movieInfo?.name ?: movieName,
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Movie metadata
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            movieInfo?.genre?.let { genre ->
                Text(
                    text = genre,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            movieInfo?.rating?.let { rating ->
                Text(
                    text = "★ $rating",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            movieInfo?.duration?.let { duration ->
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Release date
        movieInfo?.releaseDate?.let { releaseDate ->
            Text(
                text = "Released: $releaseDate",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Plot/Description
        movieInfo?.plot?.let { plot ->
            Text(
                text = plot,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cast
        movieInfo?.cast?.let { cast ->
            Text(
                text = "Cast: $cast",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Director
        movieInfo?.director?.let { director ->
            Text(
                text = "Director: $director",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Play button
        Button(
            onClick = {
                onPlayMovie(movieId, movieInfo?.name ?: movieName, extension)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("▶ Play Movie")
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
            Text("Loading movie details...")
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
                text = "Error Loading Movie",
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
        val seconds = duration.toLongOrNull() ?: return duration
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    } catch (e: Exception) {
        duration
    }
}
