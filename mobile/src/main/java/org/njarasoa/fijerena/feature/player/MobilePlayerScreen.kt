package org.njarasoa.fijerena.feature.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.ui.theme.CinemaWarning
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mobile player screen with touch controls, audio/subtitle/quality selectors,
 * favorites, playback resume, and Stats for Nerds overlay.
 */
@Composable
fun MobilePlayerScreen(
    streamId: String,
    streamName: String,
    categoryId: String,
    contentType: String,
    onBack: () -> Unit,
    episodeId: String? = null,
    episodeExtension: String? = null,
    seriesId: String? = null,
    seriesName: String? = null,
    startFromBeginning: Boolean = false,
    viewModel: PlaybackViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Unlock orientation for video playback, restore portrait on exit
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val appSettings = remember { AppSettings(context.applicationContext) }
    val mediaRepository = remember {
        val appContext = context.applicationContext
        val providerRepo = ProviderRepository(appContext)
        val repo = MediaRepository(appContext, 0L)
        kotlinx.coroutines.runBlocking {
            val entity = providerRepo.getActiveProvider()
            if (entity != null) {
                val resolvedRepo = MediaRepository(appContext, entity.id)
                val password = providerRepo.getPassword(entity.id) ?: ""
                val provider = MediaProviderFactory.create(entity, appContext, password)
                resolvedRepo.setProvider(provider)
                resolvedRepo
            } else repo
        }
    }

    // Channel switching state (Live TV only)
    var currentStreamId by remember { mutableStateOf(streamId) }
    var currentStreamName by remember { mutableStateOf(streamName) }
    var streamList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var currentStreamIndex by remember { mutableStateOf(0) }
    var showChannelToast by remember { mutableStateOf(false) }

    var streamUrl by remember { mutableStateOf<String?>(null) }
    var streamHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var showStats by remember { mutableStateOf(false) }
    var hasStartedPlaying by remember { mutableStateOf(false) }

    // Live position polling for smooth VOD timer updates
    var livePosition by remember { mutableLongStateOf(0L) }
    var liveDuration by remember { mutableLongStateOf(0L) }

    val playbackState = viewModel.playbackState.collectAsState().value

    LaunchedEffect(playbackState) {
        if (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Paused) {
            while (true) {
                StreamingPlaybackService.getInstance()?.getPlayer()?.let { player ->
                    livePosition = player.currentPosition
                    liveDuration = player.duration.coerceAtLeast(0L)
                }
                delay(500L)
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // Load stream list for channel switching (Live TV only)
    LaunchedEffect(categoryId, contentType) {
        if (contentType == "LIVE_TV") {
            val result = mediaRepository.getItems(categoryId, contentType)
            result.fold(
                onSuccess = { items ->
                    streamList = items
                    currentStreamIndex = items.indexOfFirst { it.id == streamId }
                    if (currentStreamIndex == -1) currentStreamIndex = 0
                },
                onFailure = { /* Keep empty list, disable channel switching */ }
            )
        }
    }

    // Channel switching functions
    fun switchToNextChannel() {
        if (streamList.isEmpty()) return
        val nextIndex = (currentStreamIndex + 1) % streamList.size
        val nextStream = streamList[nextIndex]
        currentStreamIndex = nextIndex
        currentStreamId = nextStream.id
        currentStreamName = nextStream.name
        showChannelToast = true
    }

    fun switchToPreviousChannel() {
        if (streamList.isEmpty()) return
        val prevIndex = if (currentStreamIndex == 0) streamList.size - 1 else currentStreamIndex - 1
        val prevStream = streamList[prevIndex]
        currentStreamIndex = prevIndex
        currentStreamId = prevStream.id
        currentStreamName = prevStream.name
        showChannelToast = true
    }

    // Auto-hide channel toast
    LaunchedEffect(showChannelToast) {
        if (showChannelToast) {
            delay(CinemaAnimation.toastDismissMs)
            showChannelToast = false
        }
    }

    // Selector dialogs
    var showAudioTrackSelector by remember { mutableStateOf(false) }
    var showSubtitleSelector by remember { mutableStateOf(false) }
    var showQualitySelector by remember { mutableStateOf(false) }

    // Favorites (async for server-backed providers)
    var isFavorite by remember { mutableStateOf(false) }
    LaunchedEffect(currentStreamId, contentType) {
        isFavorite = mediaRepository.isFavoriteSuspend(currentStreamId, contentType)
    }

    val currentMetadata = viewModel.currentMetadata.collectAsState().value

    // Track when video first starts playing so we stop showing the center spinner
    LaunchedEffect(playbackState) {
        if (playbackState is PlaybackState.Playing) {
            hasStartedPlaying = true
        }
    }

    // Save playback position periodically for VOD content
    LaunchedEffect(Unit) {
        if (contentType != "LIVE_TV") {
            while (true) {
                delay(5000L)
                val ps = viewModel.playbackState.value
                val pos = when (ps) {
                    is PlaybackState.Playing -> ps.position
                    is PlaybackState.Paused -> ps.position
                    else -> null
                }
                val dur = when (ps) {
                    is PlaybackState.Playing -> ps.duration
                    is PlaybackState.Paused -> ps.duration
                    else -> null
                }
                if (pos != null && dur != null && dur > 0) {
                    mediaRepository.savePlaybackPosition(currentStreamId, currentStreamName, categoryId, contentType, pos, dur)
                    mediaRepository.onPlaybackProgress(currentStreamId, pos, dur)
                }
            }
        }
    }

    // Auto-hide controls after 5 seconds
    LaunchedEffect(showControls) {
        if (showControls && !showStats) {
            delay(CinemaAnimation.controlsAutoHideMobileMs)
            showControls = false
        }
    }

    // Configure player buffer profile based on content type
    LaunchedEffect(contentType) {
        val playerContentType = when (contentType) {
            "LIVE_TV" -> org.njarasoa.fijerena.core.player.config.PlayerConfigFactory.ContentType.LIVE_TV
            "MOVIES", "TV_SHOWS" -> org.njarasoa.fijerena.core.player.config.PlayerConfigFactory.ContentType.VOD
            else -> org.njarasoa.fijerena.core.player.config.PlayerConfigFactory.ContentType.VOD
        }
        StreamingPlaybackService.getInstance()?.setContentType(playerContentType)
    }

    // Load stream URL
    LaunchedEffect(currentStreamId, episodeId) {
        isLoading = true
        error = null
        val result = mediaRepository.resolvePlayableStream(
            itemId = currentStreamId,
            contentType = contentType,
            episodeId = episodeId,
            extension = episodeExtension
        )
        result.fold(
            onSuccess = { playable ->
                streamUrl = playable.uri
                streamHeaders = playable.headers
                isLoading = false
            },
            onFailure = { e ->
                error = e.message ?: "Failed to load stream"
                isLoading = false
            }
        )
    }

    // Fetch saved position for VOD resume (async for server-backed providers)
    var savedPosition by remember { mutableStateOf<org.njarasoa.fijerena.core.network.WatchedItem?>(null) }
    var positionLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(currentStreamId, contentType) {
        positionLoaded = false
        savedPosition = if (!startFromBeginning && contentType != "LIVE_TV" && appSettings.autoResumeEnabled) {
            mediaRepository.getPlaybackPositionSuspend(currentStreamId, contentType)
        } else null
        positionLoaded = true
    }

    // Start playback when URL is ready or channel changes
    LaunchedEffect(streamUrl, currentStreamId, currentStreamName, positionLoaded) {
        if (!positionLoaded) return@LaunchedEffect
        streamUrl?.let { url ->
            val watchHistoryStreamId = if (contentType == "TV_SHOWS" && seriesId != null) seriesId else currentStreamId
            val watchHistoryStreamName = if (contentType == "TV_SHOWS" && seriesName != null) seriesName else currentStreamName
            mediaRepository.saveLastPlayedItem(categoryId, watchHistoryStreamId, watchHistoryStreamName, contentType)

            // Determine resume position
            val resumePosition = savedPosition?.let { saved ->
                val progressPercent = if (saved.duration > 0) {
                    (saved.playbackPosition.toFloat() / saved.duration.toFloat()) * 100f
                } else 0f
                // Only resume if 2-95% watched
                if (progressPercent in 2.0..95.0 && !saved.isCompleted) {
                    saved.playbackPosition
                } else 0L
            } ?: 0L

            val metadata = PlayerMetadata(
                title = currentStreamName,
                channelName = appSettings.providerName,
                streamUrl = url,
                isLive = contentType == "LIVE_TV",
                headers = streamHeaders
            )
            viewModel.playStream(metadata, resumePosition)
        }
    }

    when {
        isLoading -> {
            LoadingScreen()
        }
        error != null -> {
            ErrorScreen(message = error ?: "Unknown error", onBack = onBack)
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable {
                        if (showStats) {
                            showStats = false
                        } else {
                            showControls = !showControls
                        }
                    }
                    .then(
                        if (contentType == "LIVE_TV" && streamList.size > 1) {
                            Modifier.pointerInput(streamList) {
                                var totalDrag = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { totalDrag = 0f },
                                    onVerticalDrag = { _, dragAmount ->
                                        totalDrag += dragAmount
                                    },
                                    onDragEnd = {
                                        val threshold = 100f
                                        if (totalDrag < -threshold) {
                                            // Swipe up = next channel
                                            switchToNextChannel()
                                        } else if (totalDrag > threshold) {
                                            // Swipe down = previous channel
                                            switchToPreviousChannel()
                                        }
                                    }
                                )
                            }
                        } else Modifier
                    )
            ) {
                // Video surface
                val playerView = remember {
                    PlayerView(context).apply {
                        useController = false
                        keepScreenOn = true
                    }
                }

                AndroidView(
                    factory = { playerView },
                    modifier = Modifier.fillMaxSize()
                )

                // Bind player to view
                DisposableEffect(Unit) {
                    val service = StreamingPlaybackService.getInstance()
                    playerView.player = service?.getPlayer()

                    onDispose {
                        // Save final position before leaving
                        if (contentType != "LIVE_TV") {
                            val pos = when (val ps = viewModel.playbackState.value) {
                                is PlaybackState.Playing -> ps.position
                                is PlaybackState.Paused -> ps.position
                                else -> null
                            }
                            val dur = when (val ps = viewModel.playbackState.value) {
                                is PlaybackState.Playing -> ps.duration
                                is PlaybackState.Paused -> ps.duration
                                else -> null
                            }
                            if (pos != null && dur != null && dur > 0) {
                                mediaRepository.savePlaybackPosition(currentStreamId, currentStreamName, categoryId, contentType, pos, dur)
                            }
                        }
                        // Stop playback when leaving the player screen
                        viewModel.stop()
                        playerView.player = null
                    }
                }

                // Loading/Error overlays
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (playbackState) {
                        PlaybackState.Buffering -> {
                            if (!hasStartedPlaying) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                        is PlaybackState.Error -> {
                            ErrorOverlay(
                                error = playbackState,
                                onRetry = { viewModel.playStream(currentMetadata) },
                                onBack = onBack
                            )
                        }
                        else -> { /* Playing or paused */ }
                    }
                }

                // Touch controls overlay
                AnimatedVisibility(
                    visible = showControls && !showStats && (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Paused),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ControlsOverlay(
                        playbackState = playbackState,
                        metadata = currentMetadata,
                        viewModel = viewModel,
                        isLive = contentType == "LIVE_TV",
                        isDeveloperMode = appSettings.isDevMode,
                        isFavorite = isFavorite,
                        livePosition = livePosition,
                        liveDuration = liveDuration,
                        onPlayPause = {
                            if (playbackState is PlaybackState.Paused) {
                                viewModel.resume()
                            } else {
                                viewModel.pause()
                            }
                        },
                        onBack = {
                            // Save position before stopping (stop sets state to Idle)
                            if (contentType != "LIVE_TV") {
                                val ps = viewModel.playbackState.value
                                val pos = when (ps) {
                                    is PlaybackState.Playing -> ps.position
                                    is PlaybackState.Paused -> ps.position
                                    else -> null
                                }
                                val dur = when (ps) {
                                    is PlaybackState.Playing -> ps.duration
                                    is PlaybackState.Paused -> ps.duration
                                    else -> null
                                }
                                if (pos != null && dur != null && dur > 0) {
                                    mediaRepository.savePlaybackPosition(currentStreamId, currentStreamName, categoryId, contentType, pos, dur)
                                    coroutineScope.launch {
                                        mediaRepository.onPlaybackProgress(currentStreamId, pos, dur)
                                    }
                                }
                            }
                            viewModel.stop()
                            onBack()
                        },
                        onStats = { showStats = true },
                        onAudioTrack = { showAudioTrackSelector = true },
                        onSubtitle = { showSubtitleSelector = true },
                        onQuality = { showQualitySelector = true },
                        onToggleFavorite = {
                            coroutineScope.launch {
                                if (isFavorite) {
                                    if (mediaRepository.removeFavoriteSuspend(currentStreamId, contentType)) {
                                        isFavorite = false
                                    }
                                } else {
                                    if (mediaRepository.addFavoriteSuspend(currentStreamId, currentStreamName, categoryId, contentType)) {
                                        isFavorite = true
                                    }
                                }
                            }
                        }
                    )
                }

                // Stats overlay
                AnimatedVisibility(
                    visible = showStats,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    MobileStatsOverlay(
                        playbackState = playbackState,
                        metadata = currentMetadata,
                        onClose = { showStats = false }
                    )
                }

                // Channel switch toast (Live TV only)
                AnimatedVisibility(
                    visible = showChannelToast,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    ChannelToast(channelName = currentStreamName)
                }
            }

            // Selector dialogs (outside the clickable Box)
            if (showAudioTrackSelector) {
                AudioTrackSelectorDialog(
                    viewModel = viewModel,
                    onDismiss = { showAudioTrackSelector = false }
                )
            }

            if (showSubtitleSelector) {
                SubtitleSelectorDialog(
                    viewModel = viewModel,
                    onDismiss = { showSubtitleSelector = false }
                )
            }

            if (showQualitySelector) {
                QualitySelectorDialog(
                    viewModel = viewModel,
                    onDismiss = { showQualitySelector = false }
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
            CircularProgressIndicator(
                modifier = Modifier.size(MobileDimensions.iconXLarge),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Loading stream...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Playback Error",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun ErrorOverlay(
    error: PlaybackState.Error,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.padding(32.dp),
        color = Color.Black.copy(alpha = CinemaAlpha.overlayMedium),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Playback Error",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Red
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onRetry) {
                    Text("Retry")
                }
                Button(onClick = onBack) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
private fun ControlsOverlay(
    playbackState: PlaybackState,
    metadata: PlayerMetadata,
    viewModel: PlaybackViewModel,
    isLive: Boolean,
    isDeveloperMode: Boolean,
    isFavorite: Boolean,
    livePosition: Long,
    liveDuration: Long,
    onPlayPause: () -> Unit,
    onBack: () -> Unit,
    onStats: () -> Unit,
    onAudioTrack: () -> Unit,
    onSubtitle: () -> Unit,
    onQuality: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val audioTrackCount = remember { viewModel.getAudioTracks().size }
    val subtitleTrackCount = remember { viewModel.getSubtitleTracks().size }
    val qualityCount = remember { viewModel.getVideoQualities().size }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = CinemaAlpha.tint))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { }
    ) {
        // Top bar with title
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title
            Text(
                text = metadata.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                maxLines = 1
            )
        }

        // Center play/pause button
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .align(Alignment.Center)
                .size(MobileDimensions.iconPlayContainer)
        ) {
            Icon(
                imageVector = if (playbackState is PlaybackState.Paused) {
                    Icons.Default.PlayArrow
                } else {
                    Icons.Default.Pause
                },
                contentDescription = if (playbackState is PlaybackState.Paused) "Play" else "Pause",
                tint = Color.White,
                modifier = Modifier.size(MobileDimensions.iconPlayIcon)
            )
        }

        // Bottom section: progress + controls (scrollable for landscape)
        GlassPanel(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm)
        ) {
            // VOD progress bar and time info
            if (!isLive) {
                val position = livePosition
                val duration = liveDuration

                if (duration > 0) {
                    // Seek position state for dragging
                    var isSeeking by remember { mutableStateOf(false) }
                    var seekPosition by remember { mutableStateOf(0f) }

                    Slider(
                        value = if (isSeeking) seekPosition else position.toFloat() / duration.toFloat(),
                        onValueChange = { newValue ->
                            isSeeking = true
                            seekPosition = newValue
                        },
                        onValueChangeFinished = {
                            val newPositionMs = (seekPosition * duration).toLong()
                            viewModel.seekTo(newPositionMs)
                            isSeeking = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = CinemaAlpha.tint)
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(position),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }

                    // Remaining time and estimated end time
                    val remainingTime = duration - position
                    val estimatedEndTimeMillis = System.currentTimeMillis() + remainingTime
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Remaining: ${formatTime(remainingTime)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Ends at ${org.njarasoa.fijerena.core.ui.theme.TimeFormat.formatClockTime(Date(estimatedEndTimeMillis))}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                // Live indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(MobileDimensions.liveDotSize)
                            .background(Color.Red, shape = MaterialTheme.shapes.small)
                    )
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }

            // Control buttons row (horizontally scrollable icons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Audio track selector (only if multiple tracks)
                if (audioTrackCount > 1) {
                    IconButton(onClick = onAudioTrack) {
                        Icon(Icons.Filled.VolumeUp, "Audio", tint = Color.White)
                    }
                }

                // Subtitle selector (only if subtitles available)
                if (subtitleTrackCount > 0) {
                    IconButton(onClick = onSubtitle) {
                        Icon(Icons.Filled.Subtitles, "Subtitles", tint = Color.White)
                    }
                }

                // Quality selector (only if multiple qualities)
                if (qualityCount > 1) {
                    IconButton(onClick = onQuality) {
                        Icon(Icons.Filled.Tune, "Quality", tint = Color.White)
                    }
                }

                // Favorite toggle
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove Favorite" else "Add Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White
                    )
                }

                // Stats (dev mode only)
                if (isDeveloperMode) {
                    IconButton(onClick = onStats) {
                        Icon(Icons.Filled.BarChart, "Stats", tint = Color.White)
                    }
                }
            }
        }
        }
    }
}

// --- Audio Track Selector Dialog ---

@Composable
private fun AudioTrackSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val audioTracks = remember { viewModel.getAudioTracks() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Audio Track") },
        text = {
            if (audioTracks.isEmpty()) {
                Text("No audio tracks available")
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    audioTracks.forEachIndexed { _, track ->
                        Surface(
                            onClick = {
                                viewModel.selectAudioTrack(track.groupIndex, track.trackIndex)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (track.isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(CinemaCornerRadius.small)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = track.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (track.isSelected) {
                                        Text(
                                            text = "Active",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = "${track.channelCount}ch - ${track.sampleRate / 1000}kHz",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// --- Subtitle Selector Dialog ---

@Composable
private fun SubtitleSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val subtitleTracks = remember { viewModel.getSubtitleTracks() }
    val hasActiveSubtitle = subtitleTracks.any { it.isSelected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Subtitles") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // "Off" option
                Surface(
                    onClick = {
                        viewModel.disableSubtitles()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (!hasActiveSubtitle)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(CinemaCornerRadius.small)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Off",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (!hasActiveSubtitle) FontWeight.Bold else FontWeight.Normal
                        )
                        if (!hasActiveSubtitle) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                subtitleTracks.forEachIndexed { _, track ->
                    Surface(
                        onClick = {
                            viewModel.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (track.isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(CinemaCornerRadius.small)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = track.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (track.isSelected) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = track.mimeType.substringAfterLast("/").uppercase(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// --- Quality Selector Dialog ---

@Composable
private fun QualitySelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val videoQualities = remember { viewModel.getVideoQualities() }
    val hasManualSelection = videoQualities.any { it.isSelected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Quality") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // "Auto" option
                Surface(
                    onClick = {
                        viewModel.enableAutoQuality()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (!hasManualSelection)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(CinemaCornerRadius.small)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Auto (Adaptive)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (!hasManualSelection) FontWeight.Bold else FontWeight.Normal
                            )
                            if (!hasManualSelection) {
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = "Adjust quality based on network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                videoQualities.forEachIndexed { _, quality ->
                    Surface(
                        onClick = {
                            viewModel.selectVideoQuality(quality.groupIndex, quality.trackIndex)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (quality.isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(CinemaCornerRadius.small)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = quality.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (quality.isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (quality.isSelected) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = "${quality.width}x${quality.height} - ${quality.frameRate.toInt()}fps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// --- Stats Overlay ---

@Composable
private fun MobileStatsOverlay(
    playbackState: PlaybackState,
    metadata: PlayerMetadata,
    onClose: () -> Unit
) {
    var videoCodec by remember { mutableStateOf("N/A") }
    var videoResolution by remember { mutableStateOf("N/A") }
    var videoFrameRate by remember { mutableStateOf("N/A") }
    var videoBitrate by remember { mutableStateOf("N/A") }

    var audioCodec by remember { mutableStateOf("N/A") }
    var audioSampleRate by remember { mutableStateOf("N/A") }
    var audioChannels by remember { mutableStateOf("N/A") }
    var audioBitrate by remember { mutableStateOf("N/A") }

    var bufferedPosition by remember { mutableStateOf(0L) }
    var droppedFrames by remember { mutableStateOf(0L) }
    var networkSpeed by remember { mutableStateOf("N/A") }
    var bufferHealth by remember { mutableStateOf(0) }

    val serviceDroppedFrames = StreamingPlaybackService.getInstance()?.droppedFrames?.collectAsState()
    val serviceTotalFrames = StreamingPlaybackService.getInstance()?.totalFrames?.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            StreamingPlaybackService.getInstance()?.getPlayer()?.let { p ->
                bufferedPosition = p.bufferedPosition
                droppedFrames = serviceDroppedFrames?.value ?: 0L

                val currentPos = p.currentPosition
                val buffered = p.bufferedPosition
                bufferHealth = if (buffered > currentPos) {
                    ((buffered - currentPos) / 1000).toInt().coerceIn(0, 100)
                } else 0

                val tracks = p.currentTracks
                var totalBitrate = 0

                for (i in 0 until tracks.groups.size) {
                    val group = tracks.groups[i]
                    if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && group.length > 0) {
                        val format = group.getTrackFormat(0)
                        videoCodec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: "Unknown"
                        videoResolution = "${format.width} x ${format.height}"
                        videoFrameRate = if (format.frameRate > 0) "${format.frameRate.toInt()} fps" else "N/A"
                        videoBitrate = formatBitrate(format.bitrate)
                        if (format.bitrate > 0) totalBitrate += format.bitrate
                    }
                    if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && group.length > 0) {
                        val format = group.getTrackFormat(0)
                        audioCodec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: "Unknown"
                        audioSampleRate = if (format.sampleRate > 0) "${format.sampleRate / 1000}kHz" else "N/A"
                        audioChannels = if (format.channelCount > 0) {
                            when (format.channelCount) {
                                1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"
                                else -> "${format.channelCount}ch"
                            }
                        } else "N/A"
                        audioBitrate = formatBitrate(format.bitrate)
                        if (format.bitrate > 0) totalBitrate += format.bitrate
                    }
                }
                networkSpeed = if (totalBitrate > 0) formatBitrate(totalBitrate) else "N/A"
            }
            delay(CinemaAnimation.statsUpdateMs)
        }
    }

    val position = when (playbackState) {
        is PlaybackState.Playing -> playbackState.position
        is PlaybackState.Paused -> playbackState.position
        else -> 0L
    }
    val duration = when (playbackState) {
        is PlaybackState.Playing -> playbackState.duration
        is PlaybackState.Paused -> playbackState.duration
        else -> 0L
    }
    val totalFrames = serviceTotalFrames?.value ?: 0L
    val dropRate = if (totalFrames > 0) (droppedFrames.toFloat() / totalFrames * 100) else 0f
    val dropColor = when {
        dropRate < 0.5f -> CinemaSuccess
        dropRate < 2.0f -> CinemaWarning
        else -> CinemaError
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = CinemaAlpha.scrim))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() }
    ) {
        GlassPanel(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(CinemaSpacing.md)
                .widthIn(max = MobileDimensions.statsOverlayMaxWidth)
        ) {
            Column(
                modifier = Modifier
                    .padding(CinemaSpacing.md)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stats for Nerds",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(MobileDimensions.iconLarge)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = CinemaAlpha.textMedium),
                            modifier = Modifier.size(MobileDimensions.iconSmall)
                        )
                    }
                }

                SectionHeader("VIDEO")
                StatRow("Codec", videoCodec)
                StatRow("Resolution", videoResolution)
                StatRow("Frame Rate", videoFrameRate)
                StatRow("Bitrate", videoBitrate)

                SectionHeader("AUDIO")
                StatRow("Codec", audioCodec)
                StatRow("Sample Rate", audioSampleRate)
                StatRow("Channels", audioChannels)
                StatRow("Bitrate", audioBitrate)

                SectionHeader("NETWORK")
                StatRow("Speed", networkSpeed)
                StatRow("Buffer", "${bufferHealth}s")
                StatRow("Buffered", formatTime(bufferedPosition))

                SectionHeader("PLAYBACK")
                StatRow("Position", formatTime(position))
                StatRow("Duration", if (duration > 0) formatTime(duration) else "Live")

                SectionHeader("PERFORMANCE")
                StatRowColored("Dropped", "$droppedFrames / $totalFrames", dropColor)
                if (totalFrames > 0) {
                    StatRowColored("Drop Rate", String.format("%.2f%%", dropRate), dropColor)
                }

                SectionHeader("STREAM")
                StatRow("Type", if (metadata.isLive) "Live" else "VOD")
                StatRow("URL", metadata.streamUrl.substringAfterLast("/").take(25))

                SectionHeader("DEVICE")
                StatRow("Model", android.os.Build.MODEL)
                StatRow("API", "${android.os.Build.VERSION.SDK_INT}")
            }
        }
    }
}

// --- Shared Composables ---

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = Color.White.copy(alpha = CinemaAlpha.textMedium)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatRowColored(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = Color.White.copy(alpha = CinemaAlpha.textMedium)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

@Composable
private fun ChannelToast(channelName: String) {
    GlassPanel(
        modifier = Modifier.padding(top = CinemaSpacing.xl)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = CinemaSpacing.lg,
                vertical = CinemaSpacing.sm
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = channelName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatBitrate(bitrate: Int): String {
    return if (bitrate > 0) {
        val kbps = bitrate / 1000
        if (kbps > 1000) {
            String.format("%.1f Mbps", kbps / 1000f)
        } else {
            "$kbps Kbps"
        }
    } else {
        "Unknown"
    }
}
