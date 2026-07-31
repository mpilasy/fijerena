@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.movie

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusableNoScale
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

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
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }
    var mediaRepository by remember { mutableStateOf<MediaRepository?>(null) }

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

        // Initialize repository asynchronously (avoids runBlocking on main thread)
        val appContext = context.applicationContext
        val providerRepo = ProviderRepository(appContext)
        val entity = providerRepo.getActiveProvider()
        val repo = MediaRepository(appContext, entity?.id ?: 0L)
        if (entity != null) {
            val password = providerRepo.getPassword(entity.id) ?: ""
            val provider = MediaProviderFactory.create(entity, appContext, password)
            provider.connect()
            repo.setProvider(provider)
        }
        mediaRepository = repo

        val result = repo.getMovieDetail(movieId)
        val defaultError = context.getString(R.string.movie_error_loading)
        result.fold(
            onSuccess = { detail ->
                movieDetail = detail
                isLoading = false
            },
            onFailure = { e ->
                error = e.message ?: defaultError
                isLoading = false
            },
        )

        // Load resume position
        val watched = repo.getPlaybackPositionSuspend(movieId, ContentType.MOVIES)
        if (watched != null && !watched.isCompleted && watched.playbackPosition > 0 && watched.duration > 0) {
            val progress = (watched.playbackPosition.toFloat() / watched.duration.toFloat()) * 100f
            if (progress in 2.0..95.0) {
                resumePositionMs = watched.playbackPosition
                resumeDurationMs = watched.duration
            }
        }
    }

    // Provide UI scale for all child composables
    CompositionLocalProvider(LocalUiScale provides uiScale) {
        when {
            isLoading -> {
                LoadingScreen()
            }
            error != null -> {
                ErrorScreen(
                    message = error ?: "Unknown error",
                    onBack = onBack,
                )
            }
            movieDetail != null -> {
                MovieDetailsContent(
                    movieDetail = movieDetail!!,
                    movieId = movieId,
                    movieName = movieName,
                    categoryId = categoryId,
                    mediaRepository = mediaRepository!!,
                    resumePositionMs = resumePositionMs,
                    resumeDurationMs = resumeDurationMs,
                    onPlayMovie = onPlayMovie,
                    onRefresh = { refresh() },
                    onBack = onBack,
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
    categoryId: String,
    mediaRepository: MediaRepository,
    resumePositionMs: Long,
    resumeDurationMs: Long,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val providerName by remember { mutableStateOf(appSettings.providerName) }
    val extension = movieDetail.extension ?: "mp4"
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val scaledStyles =
        remember(scale, typography) {
            object {
                val displaySmall = typography.displaySmall.copy(fontSize = typography.displaySmall.fontSize.scaled(scale))
                val titleSmall = typography.titleSmall.copy(fontSize = typography.titleSmall.fontSize.scaled(scale))
                val titleMedium = typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
                val bodyMedium = typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale))
                val bodyLarge = typography.bodyLarge.copy(fontSize = typography.bodyLarge.fontSize.scaled(scale))
                val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
            }
        }

    // Favorite state
    var isFavorite by remember { mutableStateOf(mediaRepository.isFavorite(movieId, ContentType.MOVIES)) }

    // Focus requester for Play button
    val playButtonFocusRequester = remember { FocusRequester() }

    // Request focus on Play/Resume button when screen loads or resume data arrives
    LaunchedEffect(resumePositionMs) {
        try {
            playButtonFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    // Track refresh state for animation
    var isRefreshing by remember { mutableStateOf(false) }
    var targetRotation by remember { mutableStateOf(0f) }

    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = CinemaAnimation.fadeInDurationMs, easing = LinearEasing),
        label = "refresh_rotation",
    )

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            while (isRefreshing) {
                targetRotation = (targetRotation + 360f) % 3600f
                kotlinx.coroutines.delay(CinemaAnimation.loadingDebounceMs)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    AmbientBackdrop(modifier = Modifier.fillMaxSize(), imageUrl = movieDetail.coverUrl)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .focusable()
                .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
                ) {
                    Text(
                        text = movieDetail.name.ifEmpty { movieName },
                        style = scaledStyles.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Favorite button
                    CinemaIconButton(
                        onClick = {
                            if (isFavorite) {
                                mediaRepository.removeFavorite(movieId, ContentType.MOVIES)
                            } else {
                                mediaRepository.addFavorite(movieId, movieDetail.name.ifEmpty { movieName }, categoryId, ContentType.MOVIES)
                            }
                            isFavorite = !isFavorite
                        },
                        icon = {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = if (isFavorite) stringResource(R.string.favorite_remove) else stringResource(R.string.favorite_add),
                                tint = if (isFavorite) CinemaAccent else CinemaTextPrimary,
                                modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                            )
                        }
                    )
                    // Refresh button
                    CinemaIconButton(
                        onClick = {
                            isRefreshing = true
                            onRefresh()
                            isRefreshing = false
                        },
                        enabled = !isRefreshing,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.movie_refresh_info),
                                modifier =
                                    Modifier
                                        .size(TvDimensions.iconSmall.scaled(scale))
                                        .rotate(rotation),
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
            Text(
                text = providerName,
                style = scaledStyles.titleSmall,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

        // Movie content: poster + metadata
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xl.scaled(scale)),
        ) {
            // Cover image
            CinemaThumbnail(
                url = movieDetail.coverUrl,
                fallbackLetter = movieDetail.name.firstOrNull(),
                contentType = ThumbnailContentType.MOVIE,
                modifier =
                    Modifier
                        .width(TvDimensions.posterWidth.scaled(scale))
                        .height(TvDimensions.posterHeightLarge.scaled(scale)),
            )

            // Metadata in glass panel
            GlassPanel(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(Spacing.lg.scaled(scale))) {
                    // Metadata header row: rating | year | duration | ends at
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        movieDetail.metadata.rating?.let { rating ->
                            Text(
                                text = "★ $rating",
                                style = scaledStyles.titleMedium,
                                color = CinemaAccent,
                            )
                        }
                        movieDetail.metadata.year?.let { year ->
                            Text(
                                text = "$year",
                                style = scaledStyles.titleMedium,
                                color = CinemaTextSecondary,
                            )
                        }
                        movieDetail.metadata.duration?.let { duration ->
                            Text(
                                text = formatDuration(duration, context),
                                style = scaledStyles.titleMedium,
                                color = CinemaTextSecondary,
                            )
                        }
                        // "Ends at" based on remaining duration
                        val endsAtContext = LocalContext.current
                        val endsAtText =
                            remember(movieDetail.metadata.duration, resumePositionMs) {
                                computeEndsAt(endsAtContext, movieDetail.metadata.duration, resumePositionMs)
                            }
                        if (endsAtText != null) {
                            Text(
                                text = stringResource(R.string.movie_ends_at_format, endsAtText),
                                style = scaledStyles.titleMedium,
                                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textMedium),
                            )
                        }
                    }

                    // Genre tags
                    movieDetail.metadata.genre?.let { genre ->
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        Text(
                            text = genre,
                            style = scaledStyles.bodyMedium,
                            color = CinemaAccent,
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))

                    // Play / Resume buttons
                    val hasResume = resumePositionMs > 0L
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasResume) {
                            val resumeTimeText = formatMillis(resumePositionMs)
                            CinemaPrimaryButton(
                                onClick = {
                                    onPlayMovie(movieId, movieDetail.name.ifEmpty { movieName }, extension, false)
                                },
                                text = stringResource(R.string.movie_resume_from_format, resumeTimeText),
                                modifier =
                                    Modifier
                                        .focusRequester(playButtonFocusRequester),
                            )
                            CinemaSecondaryButton(
                                onClick = {
                                    onPlayMovie(movieId, movieDetail.name.ifEmpty { movieName }, extension, true)
                                },
                                text = stringResource(R.string.movie_start_beginning),
                            )
                        } else {
                            CinemaPrimaryButton(
                                onClick = {
                                    onPlayMovie(movieId, movieDetail.name.ifEmpty { movieName }, extension, false)
                                },
                                text = stringResource(R.string.movie_play_action),
                                modifier =
                                    Modifier
                                        .focusRequester(playButtonFocusRequester),
                            )
                        }
                    }

                    // Plot/Description
                    movieDetail.metadata.plot?.let { plot ->
                        Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
                        Text(
                            text = plot,
                            style = scaledStyles.bodyLarge,
                            color = CinemaTextPrimary,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                    // Cast, Director
                    movieDetail.metadata.cast?.let { cast ->
                        Text(
                            text = stringResource(R.string.movie_cast_format, cast),
                            style = scaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    }
                    movieDetail.metadata.director?.let { director ->
                        Text(
                            text = stringResource(R.string.movie_director_format, director),
                            style = scaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    }

                    // Technical stream info (Jellyfin-style labeled rows)
                    val hasVideoInfo =
                        movieDetail.videoInfo != null &&
                            (movieDetail.videoInfo!!.width != null || movieDetail.videoInfo!!.codecName != null)
                    if (hasVideoInfo ||
                        movieDetail.audioTracks.isNotEmpty() ||
                        movieDetail.subtitleTracks.isNotEmpty() ||
                        movieDetail.extension != null
                    ) {
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        movieDetail.videoInfo?.let { video ->
                            val videoText =
                                video.displayTitle ?: run {
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
                                            parts.add("$w×$h")
                                        }
                                    }
                                    parts.joinToString(" · ")
                                }
                            if (videoText.isNotBlank()) {
                                TechInfoRow(label = stringResource(R.string.tech_video_label), value = videoText)
                            }
                        }
                        if (movieDetail.audioTracks.isNotEmpty()) {
                            val audioTexts =
                                movieDetail.audioTracks.mapNotNull { audio ->
                                    val text =
                                        audio.displayTitle ?: run {
                                            val parts = mutableListOf<String>()
                                            audio.language?.let { lang -> if (lang.isNotBlank()) parts.add(lang) }
                                            audio.codecName?.let { codec -> parts.add(codec.uppercase()) }
                                            audio.channels?.let { ch -> parts.add(channelLabel(ch, context)) }
                                            if (audio.isDefault) parts.add(stringResource(R.string.tech_default_label))
                                            parts.joinToString(" · ")
                                        }
                                    text.ifBlank { null }
                                }
                            if (audioTexts.isNotEmpty()) {
                                TechInfoRow(label = stringResource(R.string.tech_audio_label), value = audioTexts.joinToString("\n"))
                            }
                        }
                        if (movieDetail.subtitleTracks.isNotEmpty()) {
                            val subTexts =
                                movieDetail.subtitleTracks.mapNotNull { sub ->
                                    val text =
                                        sub.displayTitle ?: run {
                                            val parts = mutableListOf<String>()
                                            sub.language?.let { lang -> if (lang.isNotBlank()) parts.add(lang) }
                                            sub.codecName?.let { codec -> parts.add(codec.uppercase()) }
                                            if (sub.isDefault) parts.add(stringResource(R.string.tech_default_label))
                                            parts.joinToString(" · ")
                                        }
                                    text.ifBlank { null }
                                }
                            if (subTexts.isNotEmpty()) {
                                TechInfoRow(label = stringResource(R.string.tech_subtitle_label), value = subTexts.joinToString("\n"))
                            }
                        }
                        movieDetail.extension?.let { ext ->
                            TechInfoRow(label = stringResource(R.string.tech_container_label), value = ext.uppercase())
                        }
                    }
                } // GlassPanel Column
            } // GlassPanel
        } // Outer Row (poster + metadata)
    }
    }
}

@Composable
private fun LoadingScreen() {
    val scale = LocalUiScale.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(TvDimensions.progressIndicator.scaled(scale)),
                color = CinemaAccent,
            )
            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
            Text(
                text = stringResource(R.string.movie_loading_details),
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize =
                            MaterialTheme.typography.titleLarge.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary,
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onBack: () -> Unit,
) {
    val scale = LocalUiScale.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.xl.scaled(scale)),
        ) {
            Text(
                text = stringResource(R.string.movie_error_loading),
                style =
                    MaterialTheme.typography.displayMedium.copy(
                        fontSize =
                            MaterialTheme.typography.displayMedium.fontSize
                                .scaled(scale),
                    ),
                color = CinemaError,
            )
            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
            Text(
                text = message,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontSize =
                            MaterialTheme.typography.bodyLarge.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary,
            )
            Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
            CinemaSecondaryButton(
                onClick = onBack,
                text = stringResource(R.string.movie_back_to_movies),
            )
        }
    }
}

@Composable
private fun TechInfoRow(
    label: String,
    value: String,
) {
    val scale = LocalUiScale.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize =
                        MaterialTheme.typography.bodyMedium.fontSize
                            .scaled(scale),
                ),
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )
        Text(
            text = value,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize =
                        MaterialTheme.typography.bodyMedium.fontSize
                            .scaled(scale),
                ),
            color = CinemaTextPrimary,
        )
    }
}

/**
 * Returns a human-readable channel layout label (e.g., "5.1", "7.1", "Stereo")
 */
private fun channelLabel(channels: Int, context: android.content.Context): String =
    when (channels) {
        1 -> context.getString(R.string.audio_channel_mono)
        2 -> context.getString(R.string.audio_channel_stereo)
        6 -> context.getString(R.string.audio_channel_5_1)
        8 -> context.getString(R.string.audio_channel_7_1)
        else -> context.getString(R.string.audio_channel_custom, channels)
    }

/**
 * Returns a human-readable resolution label (e.g., "4K", "1080p", "720p")
 */
private fun resolutionLabel(
    width: Int,
    height: Int,
): String =
    when {
        width >= 3840 || height >= 2160 -> "4K"
        width >= 2560 || height >= 1440 -> "1440p"
        width >= 1920 || height >= 1080 -> "1080p"
        width >= 1280 || height >= 720 -> "720p"
        width >= 854 || height >= 480 -> "480p"
        else -> "${height}p"
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
private fun formatDuration(duration: String, context: android.content.Context): String {
    val seconds = parseDurationToSeconds(duration) ?: return duration
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

