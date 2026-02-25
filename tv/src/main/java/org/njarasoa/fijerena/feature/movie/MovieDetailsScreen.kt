@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.movie

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled
import java.util.Date

@Composable
fun MovieDetailsScreen(
    movieId: String,
    movieName: String,
    categoryId: String,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: MovieDetailsViewModel = viewModel(
        factory = MovieDetailsViewModelFactory(
            context = LocalContext.current,
            movieId = movieId,
            categoryId = categoryId
        )
    )
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }

    val uiState by viewModel.uiState.collectAsState()

    // Provide UI scale for all child composables
    CompositionLocalProvider(LocalUiScale provides uiScale) {
        when (val state = uiState) {
            is MovieDetailsViewModel.UiState.Loading -> {
                LoadingScreen()
            }
            is MovieDetailsViewModel.UiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onBack = onBack
                )
            }
            is MovieDetailsViewModel.UiState.Success -> {
                MovieDetailsContent(
                    movieDetail = state.movieDetail,
                    movieId = movieId,
                    movieName = movieName,
                    resumePositionMs = state.resumePositionMs,
                    resumeDurationMs = state.resumeDurationMs,
                    isFavorite = state.isFavorite,
                    onPlayMovie = onPlayMovie,
                    onToggleFavorite = { viewModel.toggleFavorite(state.movieDetail.name.ifEmpty { movieName }) },
                    onRefresh = { viewModel.loadMovieInfo() },
                    onBack = onBack
                )
            }
        }
    }
}

@Composable
private fun MovieDetailsContent(
    movieDetail: MovieDetail,
    movieId: String,
    movieName: String,
    resumePositionMs: Long,
    resumeDurationMs: Long,
    isFavorite: Boolean,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val providerName by remember { mutableStateOf(appSettings.providerName) }
    val extension = movieDetail.extension ?: "mp4"
    val scale = LocalUiScale.current

    // Focus requester for Play button
    val playButtonFocusRequester = remember { FocusRequester() }

    // Request focus on Play button when screen loads
    LaunchedEffect(Unit) {
        playButtonFocusRequester.requestFocus()
    }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = Spacing.tvSafeMarginHorizontal,
                    vertical = Spacing.tvSafeMarginVertical
                )
        ) {
            // Top Bar: Back | Provider Name | Favorite | Refresh
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xxl.scaled(scale)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale))
                ) {
                    CinemaIconButton(
                        onClick = onBack,
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    )
                    Column {
                        Text(
                            text = movieDetail.name.ifEmpty { movieName },
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontSize = MaterialTheme.typography.displaySmall.fontSize.scaled(scale)
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = providerName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                            ),
                            color = CinemaAccent
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
                ) {
                    CinemaIconButton(
                        onClick = onToggleFavorite,
                        icon = {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (isFavorite) "Remove Favorite" else "Add Favorite",
                                tint = if (isFavorite) CinemaAccent else CinemaTextSecondary
                            )
                        }
                    )
                    CinemaIconButton(
                        onClick = onRefresh,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Info"
                            )
                        }
                    )
                }
            }

            // Movie content: poster + metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl.scaled(scale))
            ) {
                // Cover image
                CinemaThumbnail(
                    url = movieDetail.coverUrl,
                    fallbackLetter = movieDetail.name.firstOrNull(),
                    contentType = ThumbnailContentType.MOVIE,
                    modifier = Modifier
                        .width(TvDimensions.posterWidth.scaled(scale))
                        .height(TvDimensions.posterHeightLarge.scaled(scale))
                )

                // Metadata in glass panel
                GlassPanel(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(Spacing.lg.scaled(scale))) {
                        // Metadata header row: rating | year | duration | ends at
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            movieDetail.metadata.rating?.let { rating ->
                                Text(
                                    text = "★ $rating",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                                    ),
                                    color = CinemaAccent
                                )
                            }
                            movieDetail.metadata.year?.let { year ->
                                Text(
                                    text = "$year",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                                    ),
                                    color = CinemaTextSecondary
                                )
                            }
                            movieDetail.metadata.duration?.let { duration ->
                                Text(
                                    text = formatDuration(duration),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                                    ),
                                    color = CinemaTextSecondary
                                )
                            }
                            // "Ends at" based on remaining duration
                            val endsAtContext = LocalContext.current
                            val endsAtText = remember(movieDetail.metadata.duration, resumePositionMs) {
                                computeEndsAt(endsAtContext, movieDetail.metadata.duration, resumePositionMs)
                            }
                            if (endsAtText != null) {
                                Text(
                                    text = "Ends at $endsAtText",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                                    ),
                                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textMedium)
                                )
                            }
                        }

                        // Genre tags
                        movieDetail.metadata.genre?.let { genre ->
                            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                            Text(
                                text = genre,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                                ),
                                color = CinemaAccent
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))

                        // Play / Resume buttons
                        val hasResume = resumePositionMs > 0L
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasResume) {
                                val resumeTimeText = formatMillis(resumePositionMs)
                                CinemaPrimaryButton(
                                    onClick = {
                                        onPlayMovie(movieId, movieDetail.name.ifEmpty { movieName }, extension, false)
                                    },
                                    text = "▶ Resume from $resumeTimeText",
                                    modifier = Modifier
                                        .focusRequester(playButtonFocusRequester)
                                )
                                CinemaSecondaryButton(
                                    onClick = {
                                        onPlayMovie(movieId, movieDetail.name.ifEmpty { movieName }, extension, true)
                                    },
                                    text = "Start from Beginning"
                                )
                            } else {
                                CinemaPrimaryButton(
                                    onClick = {
                                        onPlayMovie(movieId, movieDetail.name.ifEmpty { movieName }, extension, false)
                                    },
                                    text = "▶ Play Movie",
                                    modifier = Modifier
                                        .focusRequester(playButtonFocusRequester)
                                )
                            }
                        }

                        // Plot/Description
                        movieDetail.metadata.plot?.let { plot ->
                            Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
                            Text(
                                text = plot,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize.scaled(scale)
                                ),
                                color = CinemaTextPrimary,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        // Cast, Director
                        movieDetail.metadata.cast?.let { cast ->
                            Text(
                                text = "Cast: $cast",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                                ),
                                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                        }
                        movieDetail.metadata.director?.let { director ->
                            Text(
                                text = "Director: $director",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                                ),
                                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Technical details row
            MovieTechnicalInfo(movieDetail = movieDetail, scale = scale)
        }
    }
}

@Composable
private fun MovieTechnicalInfo(
    movieDetail: MovieDetail,
    scale: Float
) {
    if (movieDetail.videoInfo == null && movieDetail.audioTracks.isEmpty() && movieDetail.subtitleTracks.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xl.scaled(scale)),
        verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale))
    ) {
        Text(
            text = "Technical Details",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = MaterialTheme.typography.headlineSmall.fontSize.scaled(scale)
            ),
            color = CinemaTextPrimary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg.scaled(scale))
        ) {
            // Video Tech Info
            movieDetail.videoInfo?.let { video ->
                TechnicalInfoCard(
                    title = "Video",
                    items = listOfNotNull(
                        video.displayTitle?.let { "Format" to it },
                        video.width?.let { w -> video.height?.let { h -> "Resolution" to "${w}x${h}" } },
                        video.codecName?.let { "Codec" to it.uppercase() },
                        video.bitrate?.let { "Bitrate" to String.format("%.1f Mbps", it / 1_000_000f) },
                        video.videoRange?.let { "Range" to it }
                    ),
                    modifier = Modifier.weight(1f),
                    scale = scale
                )
            }

            // Audio Tech Info
            if (movieDetail.audioTracks.isNotEmpty()) {
                val primaryAudio = movieDetail.audioTracks.firstOrNull { it.isDefault } ?: movieDetail.audioTracks.first()
                TechnicalInfoCard(
                    title = "Audio",
                    items = listOfNotNull(
                        primaryAudio.displayTitle?.let { "Source" to it },
                        primaryAudio.codecName?.let { "Codec" to it.uppercase() },
                        primaryAudio.channels?.let { "Channels" to when(it) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "${it}ch" } },
                        "Track Count" to "${movieDetail.audioTracks.size}"
                    ),
                    modifier = Modifier.weight(1f),
                    scale = scale
                )
            }

            // Subtitles Tech Info
            if (movieDetail.subtitleTracks.isNotEmpty()) {
                TechnicalInfoCard(
                    title = "Subtitles",
                    items = listOf(
                        "Available" to "${movieDetail.subtitleTracks.size} tracks",
                        "Languages" to movieDetail.subtitleTracks.mapNotNull { it.language }.distinct().joinToString(", ").ifEmpty { "Multiple" }
                    ),
                    modifier = Modifier.weight(1f),
                    scale = scale
                )
            }
        }
    }
}

@Composable
private fun TechnicalInfoCard(
    title: String,
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    scale: Float
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        color = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.tint),
        shape = RoundedCornerShape(CornerRadius.small.scaled(scale))
    ) {
        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)
                ),
                color = CinemaAccent,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextSecondary
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextPrimary,
                        fontWeight = FontWeight.Medium
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
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(TvDimensions.progressIndicator),
                color = CinemaAccent
            )
            Text(
                text = "Loading movie info...",
                style = MaterialTheme.typography.titleLarge,
                color = CinemaTextSecondary
            )
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier = Modifier.padding(Spacing.xl)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.displayMedium,
                color = CinemaError
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextSecondary,
                textAlign = TextAlign.Center
            )
            CinemaPrimaryButton(
                onClick = onBack,
                text = "Back"
            )
        }
    }
}

private fun formatDuration(duration: String): String {
    val mins = duration.toIntOrNull() ?: return duration
    val h = mins / 60
    val m = mins % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatMillis(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

private fun computeEndsAt(context: android.content.Context, durationStr: String?, resumeMs: Long): String? {
    val totalMins = durationStr?.toIntOrNull() ?: return null
    val totalMs = totalMins * 60L * 1000L
    val remainingMs = totalMs - resumeMs
    if (remainingMs <= 0) return null

    val endTime = System.currentTimeMillis() + remainingMs
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(endTime))
}
