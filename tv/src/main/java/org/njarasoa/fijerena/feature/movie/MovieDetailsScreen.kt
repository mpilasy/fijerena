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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
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
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
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

    var movieDetail by remember { mutableStateOf<MovieDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var resumePositionMs by remember { mutableStateOf(0L) }
    var resumeDurationMs by remember { mutableStateOf(0L) }

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

        // Load resume position
        val watched = mediaRepository.getPlaybackPositionSuspend(movieId, "MOVIES")
        if (watched != null && !watched.isCompleted && watched.playbackPosition > 0 && watched.duration > 0) {
            val progress = (watched.playbackPosition.toFloat() / watched.duration.toFloat()) * 100f
            if (progress in 2.0..95.0) {
                resumePositionMs = watched.playbackPosition
                resumeDurationMs = watched.duration
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
        movieDetail != null -> {
            MovieDetailsContent(
                movieDetail = movieDetail!!,
                movieId = movieId,
                movieName = movieName,
                resumePositionMs = resumePositionMs,
                resumeDurationMs = resumeDurationMs,
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
    resumePositionMs: Long,
    resumeDurationMs: Long,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
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
                                .size(TvDimensions.iconSmall)
                                .rotate(rotation)
                        )
                    }
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

        // Movie content: poster + metadata
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            // Cover image
            CinemaThumbnail(
                url = movieDetail.coverUrl,
                fallbackLetter = movieDetail.name.firstOrNull(),
                contentType = ThumbnailContentType.MOVIE,
                modifier = Modifier
                    .width(TvDimensions.posterWidth)
                    .height(TvDimensions.posterHeightLarge)
            )

            // Metadata in glass panel
            GlassPanel(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(Spacing.lg)) {

        // Metadata header row: rating | year | duration | ends at
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            movieDetail.metadata?.rating?.let { rating ->
                Text(
                    text = "★ $rating",
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaAccent
                )
            }
            movieDetail.metadata?.year?.let { year ->
                Text(
                    text = "$year",
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextSecondary
                )
            }
            movieDetail.metadata?.duration?.let { duration ->
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextSecondary
                )
            }
            // "Ends at" based on remaining duration
            val endsAtContext = LocalContext.current
            val endsAtText = remember(movieDetail.metadata?.duration, resumePositionMs) {
                computeEndsAt(endsAtContext, movieDetail.metadata?.duration, resumePositionMs)
            }
            if (endsAtText != null) {
                Text(
                    text = "Ends at $endsAtText",
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textMedium)
                )
            }
        }

        // Genre tags
        movieDetail.metadata?.genre?.let { genre ->
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
        movieDetail.metadata?.plot?.let { plot ->
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

        // Cast, Director
        movieDetail.metadata?.cast?.let { cast ->
            Text(
                text = "Cast: $cast",
                style = MaterialTheme.typography.bodySmall,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
        }
        movieDetail.metadata?.director?.let { director ->
            Text(
                text = "Director: $director",
                style = MaterialTheme.typography.bodySmall,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
        }

        // Technical stream info (Jellyfin-style labeled rows)
        val hasVideoInfo = movieDetail.videoInfo != null &&
            (movieDetail.videoInfo!!.width != null || movieDetail.videoInfo!!.codecName != null)
        if (hasVideoInfo || movieDetail.audioTracks.isNotEmpty() || movieDetail.subtitleTracks.isNotEmpty() || movieDetail.extension != null) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            movieDetail.videoInfo?.let { video ->
                val videoText = video.displayTitle ?: run {
                    val parts = mutableListOf<String>()
                    video.width?.let { w ->
                        video.height?.let { h ->
                            parts.add(resolutionLabel(w, h))
                        }
                    }
                    video.codecName?.let { codec -> parts.add(codec.uppercase()) }
                    video.videoRange?.let { range -> parts.add(range) }
                    video.width?.let { w ->
                        video.height?.let { h ->
                            parts.add("${w}×${h}")
                        }
                    }
                    parts.joinToString(" · ")
                }
                if (videoText.isNotBlank()) {
                    TechInfoRow(label = "Video:", value = videoText)
                }
            }
            if (movieDetail.audioTracks.isNotEmpty()) {
                val audioTexts = movieDetail.audioTracks.mapNotNull { audio ->
                    val text = audio.displayTitle ?: run {
                        val parts = mutableListOf<String>()
                        audio.language?.let { lang -> if (lang.isNotBlank()) parts.add(lang) }
                        audio.codecName?.let { codec -> parts.add(codec.uppercase()) }
                        audio.channels?.let { ch -> parts.add(channelLabel(ch)) }
                        if (audio.isDefault) parts.add("Default")
                        parts.joinToString(" · ")
                    }
                    text.ifBlank { null }
                }
                if (audioTexts.isNotEmpty()) {
                    TechInfoRow(label = "Audio:", value = audioTexts.joinToString("\n"))
                }
            }
            if (movieDetail.subtitleTracks.isNotEmpty()) {
                val subTexts = movieDetail.subtitleTracks.mapNotNull { sub ->
                    val text = sub.displayTitle ?: run {
                        val parts = mutableListOf<String>()
                        sub.language?.let { lang -> if (lang.isNotBlank()) parts.add(lang) }
                        sub.codecName?.let { codec -> parts.add(codec.uppercase()) }
                        if (sub.isDefault) parts.add("Default")
                        parts.joinToString(" · ")
                    }
                    text.ifBlank { null }
                }
                if (subTexts.isNotEmpty()) {
                    TechInfoRow(label = "Subtitle:", value = subTexts.joinToString("\n"))
                }
            }
            movieDetail.extension?.let { ext ->
                TechInfoRow(label = "Container:", value = ext.uppercase())
            }
        }
            } // GlassPanel Column
            } // GlassPanel
        } // Outer Row (poster + metadata)
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

@Composable
private fun TechInfoRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = CinemaTextPrimary
        )
    }
}

/**
 * Returns a human-readable channel layout label (e.g., "5.1", "7.1", "Stereo")
 */
private fun channelLabel(channels: Int): String {
    return when (channels) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${channels}ch"
    }
}

/**
 * Returns a human-readable resolution label (e.g., "4K", "1080p", "720p")
 */
private fun resolutionLabel(width: Int, height: Int): String {
    return when {
        width >= 3840 || height >= 2160 -> "4K"
        width >= 2560 || height >= 1440 -> "1440p"
        width >= 1920 || height >= 1080 -> "1080p"
        width >= 1280 || height >= 720 -> "720p"
        width >= 854 || height >= 480 -> "480p"
        else -> "${height}p"
    }
}

/**
 * Parses a duration string that can be either raw seconds ("7200") or h:mm:ss / m:ss format ("1:23:45").
 * Returns total seconds, or null if unparseable.
 */
private fun parseDurationToSeconds(duration: String): Long? {
    // Try raw seconds first
    duration.toLongOrNull()?.let { return it }
    // Try h:mm:ss or m:ss format
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
 * Returns formatted time string like "10:30 PM" or null if duration unavailable.
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
 * Accepts raw seconds ("7200") or h:mm:ss / m:ss format ("1:23:45").
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
