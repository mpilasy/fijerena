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
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.theme.MobileDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMovieDetailsScreen(
    movieId: String,
    movieName: String,
    categoryId: String,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
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

    var movieDetail by remember { mutableStateOf<MovieDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var resumePositionMs by remember { mutableStateOf(0L) }
    var resumeDurationMs by remember { mutableStateOf(0L) }

    // Load movie info on launch
    LaunchedEffect(movieId) {
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
                movieDetail != null -> {
                    MovieDetailsContent(
                        movieDetail = movieDetail!!,
                        movieId = movieId,
                        movieName = movieName,
                        resumePositionMs = resumePositionMs,
                        resumeDurationMs = resumeDurationMs,
                        onPlayMovie = onPlayMovie
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
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit
) {
    val extension = movieDetail.extension ?: "mp4"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(CinemaSpacing.md)
    ) {
        // Cover image
        CinemaThumbnail(
            url = movieDetail.coverUrl,
            fallbackLetter = movieDetail.name.firstOrNull(),
            contentType = ThumbnailContentType.MOVIE,
            modifier = Modifier
                .fillMaxWidth()
                .height(MobileDimensions.posterHeightLarge)
        )

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // Title
        Text(
            text = movieDetail.name,
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // Movie metadata - genre on its own line
        movieDetail.metadata?.genre?.let { genre ->
            Text(
                text = genre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Rating and duration on same row
        val hasRating = movieDetail.metadata?.rating != null
        val hasDuration = movieDetail.metadata?.duration != null
        if (hasRating || hasDuration) {
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                movieDetail.metadata?.rating?.let { rating ->
                    Text(
                        text = "★ $rating",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                movieDetail.metadata?.duration?.let { duration ->
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                }
                // "Ends at" based on remaining duration
                val endsAtText = remember(movieDetail.metadata?.duration, resumePositionMs) {
                    computeEndsAt(movieDetail.metadata?.duration, resumePositionMs)
                }
                if (endsAtText != null) {
                    Text(
                        text = "Ends at $endsAtText",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(CinemaSpacing.sm))

        // Release date
        movieDetail.metadata?.releaseDate?.let { releaseDate ->
            Text(
                text = "Released: $releaseDate",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium)
            )
        }

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // Plot/Description
        movieDetail.metadata?.plot?.let { plot ->
            Text(
                text = plot,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // Cast
        movieDetail.metadata?.cast?.let { cast ->
            Text(
                text = "Cast: $cast",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(CinemaSpacing.sm))

        // Director
        movieDetail.metadata?.director?.let { director ->
            Text(
                text = "Director: $director",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium)
            )
        }

        // Technical stream info (labeled rows)
        val hasVideoInfo = movieDetail.videoInfo != null &&
            (movieDetail.videoInfo!!.width != null || movieDetail.videoInfo!!.codecName != null)
        if (hasVideoInfo || movieDetail.audioTracks.isNotEmpty() || movieDetail.subtitleTracks.isNotEmpty() || movieDetail.extension != null) {
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
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
                    MobileTechInfoRow(label = "Video:", value = videoText)
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
                    MobileTechInfoRow(label = "Audio:", value = audioTexts.joinToString("\n"))
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
                    MobileTechInfoRow(label = "Subtitle:", value = subTexts.joinToString("\n"))
                }
            }
            movieDetail.extension?.let { ext ->
                MobileTechInfoRow(label = "Container:", value = ext.uppercase())
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("▶ Resume from $resumeTimeText")
            }
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            OutlinedButton(
                onClick = {
                    onPlayMovie(movieId, movieDetail.name, extension, true)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start from Beginning")
            }
        } else {
            Button(
                onClick = {
                    onPlayMovie(movieId, movieDetail.name, extension, false)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("▶ Play Movie")
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
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md)
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
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
            modifier = Modifier.padding(CinemaSpacing.xl)
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

@Composable
private fun MobileTechInfoRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium)
        )
    }
}

private fun channelLabel(channels: Int): String {
    return when (channels) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${channels}ch"
    }
}

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

private fun computeEndsAt(duration: String?, resumePositionMs: Long): String? {
    if (duration == null) return null
    val totalSeconds = duration.toLongOrNull() ?: return null
    if (totalSeconds <= 0) return null
    val totalMs = totalSeconds * 1000
    val remainingMs = if (resumePositionMs > 0) (totalMs - resumePositionMs).coerceAtLeast(0) else totalMs
    val calendar = java.util.Calendar.getInstance()
    calendar.add(java.util.Calendar.MILLISECOND, remainingMs.toInt())
    return java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(calendar.time)
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
