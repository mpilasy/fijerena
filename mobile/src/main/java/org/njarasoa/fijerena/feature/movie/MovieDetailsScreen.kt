package org.njarasoa.fijerena.feature.movie

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.MobileDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMovieDetailsScreen(
    movieId: String,
    movieName: String,
    categoryId: String,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var mediaRepository by remember { mutableStateOf<MediaRepository?>(null) }

    var movieDetail by remember { mutableStateOf<MovieDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var resumePositionMs by remember { mutableStateOf(0L) }
    var resumeDurationMs by remember { mutableStateOf(0L) }

    // Favorite state
    var isFavorite by remember { mutableStateOf(false) }

    // Initialize repository and load movie info asynchronously
    LaunchedEffect(movieId) {
        isLoading = true
        error = null

        val appContext = context.applicationContext
        val providerRepo = ProviderRepository(appContext)
        val entity = providerRepo.getActiveProvider()
        val repo =
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
        mediaRepository = repo

        isFavorite = repo.isFavorite(movieId, ContentType.MOVIES)

        val result = repo.getMovieDetail(movieId)
        result.fold(
            onSuccess = { detail ->
                movieDetail = detail
                isLoading = false
            },
            onFailure = { e ->
                error = e.message ?: context.getString(R.string.movie_error_loading)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.movie_details_title)) },
                navigationIcon = {
                    CinemaIconButton(onClick = onBack,
                        icon = {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = CinemaTextPrimary)
                        }
                    )
                },
                actions = {
                    CinemaIconButton(onClick = {
                        mediaRepository?.let { repo ->
                            if (isFavorite) {
                                repo.removeFavorite(movieId, ContentType.MOVIES)
                            } else {
                                repo.addFavorite(movieId, movieName, categoryId, ContentType.MOVIES)
                            }
                            isFavorite = !isFavorite
                        }
                    },
                        icon = {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
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
                movieDetail != null -> {
                    MovieDetailsContent(
                        movieDetail = movieDetail!!,
                        movieId = movieId,
                        movieName = movieName,
                        resumePositionMs = resumePositionMs,
                        resumeDurationMs = resumeDurationMs,
                        onPlayMovie = onPlayMovie,
                    )
                }
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
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
) {
    val extension = movieDetail.extension ?: "mp4"

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(CinemaSpacing.md),
    ) {
        // Cover image
        CinemaThumbnail(
            url = movieDetail.coverUrl,
            fallbackLetter = movieDetail.name.firstOrNull(),
            contentType = ThumbnailContentType.MOVIE,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(MobileDimensions.posterHeightLarge),
        )

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // Title
        Text(
            text = movieDetail.name,
            style = MaterialTheme.typography.headlineLarge,
        )

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // Movie metadata - genre on its own line
        movieDetail.metadata.genre?.let { genre ->
            Text(
                text = genre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Rating and duration on same row
        val hasRating = movieDetail.metadata.rating != null
        val hasDuration = movieDetail.metadata.duration != null
        if (hasRating || hasDuration) {
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                movieDetail.metadata.rating?.let { rating ->
                    Text(
                        text = "★ $rating",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                movieDetail.metadata.duration?.let { duration ->
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
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
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(CinemaSpacing.lg))

        // Play / Resume buttons
        val hasResume = resumePositionMs > 0L
        if (hasResume) {
            val resumeTimeText = formatMillis(resumePositionMs)
            Button(
                onClick = {
                    onPlayMovie(movieId, movieDetail.name, extension, false)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.movie_resume_from_format, resumeTimeText))
            }
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            OutlinedButton(
                onClick = {
                    onPlayMovie(movieId, movieDetail.name, extension, true)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.movie_start_beginning))
            }
        } else {
            Button(
                onClick = {
                    onPlayMovie(movieId, movieDetail.name, extension, false)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.movie_play_action))
            }
        }

        // Plot/Description
        movieDetail.metadata.plot?.let { plot ->
            Spacer(modifier = Modifier.height(CinemaSpacing.lg))
            Text(
                text = plot,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // Release date
        movieDetail.metadata.releaseDate?.let { releaseDate ->
            Text(
                text = stringResource(R.string.movie_released_format, releaseDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
            )
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
        }

        // Cast
        movieDetail.metadata.cast?.let { cast ->
            Text(
                text = stringResource(R.string.movie_cast_format, cast),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
        }

        // Director
        movieDetail.metadata.director?.let { director ->
            Text(
                text = stringResource(R.string.movie_director_format, director),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
            )
        }

        // Technical stream info (labeled rows)
        val hasVideoInfo =
            movieDetail.videoInfo != null &&
                (movieDetail.videoInfo!!.width != null || movieDetail.videoInfo!!.codecName != null)
        if (hasVideoInfo ||
            movieDetail.audioTracks.isNotEmpty() ||
            movieDetail.subtitleTracks.isNotEmpty() ||
            movieDetail.extension != null
        ) {
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
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
                    MobileTechInfoRow(label = stringResource(R.string.tech_video_label), value = videoText)
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
                                audio.channels?.let { ch -> parts.add(channelLabel(ch)) }
                                if (audio.isDefault) parts.add(stringResource(R.string.tech_default_label))
                                parts.joinToString(" · ")
                            }
                        text.ifBlank { null }
                    }
                if (audioTexts.isNotEmpty()) {
                    MobileTechInfoRow(label = stringResource(R.string.tech_audio_label), value = audioTexts.joinToString("\n"))
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
                    MobileTechInfoRow(label = stringResource(R.string.tech_subtitle_label), value = subTexts.joinToString("\n"))
                }
            }
            movieDetail.extension?.let { ext ->
                MobileTechInfoRow(label = stringResource(R.string.tech_container_label), value = ext.uppercase())
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
            Text("Loading movie details...")
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
                text = "Error Loading Movie",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun MobileTechInfoRow(
    label: String,
    value: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
        )
    }
}

private fun channelLabel(channels: Int): String =
    when (channels) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${channels}ch"
    }

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
