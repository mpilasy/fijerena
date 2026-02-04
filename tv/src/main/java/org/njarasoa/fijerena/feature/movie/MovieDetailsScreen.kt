@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.movie

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * Movie details screen for VOD content.
 *
 * Features:
 * - Displays movie information (title, plot, cast, genre, rating, duration)
 * - Large Play button
 * - D-pad friendly navigation
 * - Loads movie data from MediaRepository
 */
@Composable
fun MovieDetailsScreen(
    movieId: String,
    movieName: String,
    categoryId: String,
    onPlayMovie: (movieId: String, movieName: String, extension: String) -> Unit,
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

    var movieDetail by remember { mutableStateOf<MovieDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    fun refresh() {
        refreshTrigger++
    }

    // Load movie info on launch
    LaunchedEffect(movieId, refreshTrigger) {
        isLoading = true
        error = null

        val result = mediaRepository.getMovieDetail(movieId)
        result.fold(
            onSuccess = { detail ->
                movieDetail = detail
                isLoading = false
            },
            onFailure = { e ->
                error = e.message ?: "Failed to load movie info"
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
        movieDetail != null -> {
            MovieDetailsContent(
                movieDetail = movieDetail!!,
                movieId = movieId,
                movieName = movieName,
                onPlayMovie = onPlayMovie,
                onRefresh = { refresh() },
                onBack = onBack
            )
        }
    }
}

@Composable
private fun MovieDetailsContent(
    movieDetail: MovieDetail,
    movieId: String,
    movieName: String,
    onPlayMovie: (movieId: String, movieName: String, extension: String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val providerName by remember { mutableStateOf(appSettings.providerName) }
    val extension = movieDetail.extension ?: "mp4"

    // Focus requester for Play button
    val playButtonFocusRequester = remember { FocusRequester() }

    // Request focus on Play button when screen loads
    LaunchedEffect(Unit) {
        playButtonFocusRequester.requestFocus()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical)
    ) {
        // Header with back button
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
                        text = movieDetail.name.ifEmpty { movieName },
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
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
                            contentDescription = "Refresh movie info",
                            tint = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(rotation)
                        )
                    }
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

        // Movie metadata
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // Genre, Rating, Duration
            movieDetail.metadata?.genre?.let { genre ->
                Text(
                    text = genre,
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaAccent
                )
            }
            movieDetail.metadata?.rating?.let { rating ->
                Text(
                    text = "★ $rating",
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaAccent
                )
            }
            movieDetail.metadata?.duration?.let { duration ->
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Release date
        movieDetail.metadata?.year?.let { year ->
            Text(
                text = "Released: $year",
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextSecondary
            )
            Spacer(modifier = Modifier.height(Spacing.md))
        }

        // Plot/Description
        movieDetail.metadata?.plot?.let { plot ->
            Text(
                text = plot,
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextPrimary,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
        }

        // Cast
        movieDetail.metadata?.cast?.let { cast ->
            Text(
                text = "Cast: $cast",
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
        }

        // Director
        movieDetail.metadata?.director?.let { director ->
            Text(
                text = "Director: $director",
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        // Play button
        CinemaPrimaryButton(
            onClick = {
                onPlayMovie(movieId, movieDetail.name.ifEmpty { movieName }, extension)
            },
            text = "▶ Play Movie",
            modifier = Modifier
                .width(TvDimensions.selectionListWidth)
                .height(TvDimensions.moviePosterHeight)
                .focusRequester(playButtonFocusRequester)
        )
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
                text = "Loading movie details...",
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
                text = "Error Loading Movie",
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
                text = "Back to Movies"
            )
        }
    }
}

/**
 * Formats duration string (e.g., "7200" seconds to "2h 0m")
 */
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
