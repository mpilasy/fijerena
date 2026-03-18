@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.ui.player.utils.formatBitrate
import org.njarasoa.fijerena.ui.player.utils.formatTime
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaWarning
import org.njarasoa.fijerena.ui.theme.TvDimensions

enum class QuadrantPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

private fun getQuadrantAlignment(position: QuadrantPosition): Alignment {
    return when (position) {
        QuadrantPosition.TOP_LEFT -> Alignment.TopStart
        QuadrantPosition.TOP_RIGHT -> Alignment.TopEnd
        QuadrantPosition.BOTTOM_LEFT -> Alignment.BottomStart
        QuadrantPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun StatsOverlay(
    playbackState: PlaybackState,
    metadata: PlayerMetadata,
    onHide: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current

    var quadrantPosition by remember { mutableStateOf(QuadrantPosition.BOTTOM_RIGHT) }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Request focus when overlay appears to capture all key events
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Get current track info
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

    // Collect dropped frames from service
    val serviceDroppedFrames by StreamingPlaybackService.getInstance()?.droppedFrames?.collectAsStateWithLifecycle(0L) ?: remember { mutableStateOf(0L) }
    val serviceTotalFrames by StreamingPlaybackService.getInstance()?.totalFrames?.collectAsStateWithLifecycle(0L) ?: remember { mutableStateOf(0L) }

    // Collect stream stats from service
    val serviceRetryCount by StreamingPlaybackService.getInstance()?.streamRetryCount?.collectAsStateWithLifecycle(0) ?: remember { mutableStateOf(0) }
    val serviceStartTimeMs by StreamingPlaybackService.getInstance()?.streamStartTimeMs?.collectAsStateWithLifecycle(0L) ?: remember { mutableStateOf(0L) }
    val serviceRebufferCount by StreamingPlaybackService.getInstance()?.rebufferCount?.collectAsStateWithLifecycle(0) ?: remember { mutableStateOf(0) }
    val serviceRebufferTimeMs by StreamingPlaybackService.getInstance()?.totalRebufferTimeMs?.collectAsStateWithLifecycle(0L) ?: remember { mutableStateOf(0L) }
    val serviceBandwidth by StreamingPlaybackService.getInstance()?.bandwidthEstimate?.collectAsStateWithLifecycle(0L) ?: remember { mutableStateOf(0L) }
    val serviceQualitySwitches by StreamingPlaybackService.getInstance()?.qualitySwitchCount?.collectAsStateWithLifecycle(0) ?: remember { mutableStateOf(0) }
    val serviceMeasuredFps by StreamingPlaybackService.getInstance()?.measuredFps?.collectAsStateWithLifecycle(0f) ?: remember { mutableStateOf(0f) }
    var streamElapsed by remember { mutableStateOf("0:00") }

    // Audio DSP stats
    val audioDspStats by StreamingPlaybackService.getInstance()?.audioDspStats?.collectAsStateWithLifecycle(
        org.njarasoa.fijerena.core.player.model.AudioDspStats()
    ) ?: remember { mutableStateOf(org.njarasoa.fijerena.core.player.model.AudioDspStats()) }

    // Update stats periodically
    LaunchedEffect(Unit) {
        while (true) {
            StreamingPlaybackService.getInstance()?.getPlayer()?.let { p ->
                // Update buffered position
                bufferedPosition = p.bufferedPosition

                // Get dropped frames from analytics
                droppedFrames = serviceDroppedFrames

                // Calculate buffer health (percentage of buffer vs target)
                val currentPos = p.currentPosition
                val buffered = p.bufferedPosition
                bufferHealth = if (buffered > currentPos) {
                    ((buffered - currentPos) / 1000).toInt().coerceIn(0, 100)
                } else {
                    0
                }

                val tracks = p.currentTracks
                var currentVideoBitrate = 0
                var currentAudioBitrate = 0

                // Fallback resolution from videoSize
                if (videoResolution == "N/A" || videoResolution == "0 × 0") {
                    val size = p.videoSize
                    if (size.width > 0 && size.height > 0) {
                        videoResolution = "${size.width} × ${size.height}"
                    }
                }

                for (i in 0 until tracks.groups.size) {
                    val group = tracks.groups[i]
                    if (group.isSelected) {
                        for (j in 0 until group.length) {
                            if (group.isTrackSelected(j)) {
                                val format = group.getTrackFormat(j)
                                if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                                    videoCodec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: "Unknown"
                                    videoResolution = "${format.width} × ${format.height}"
                                    
                                    val mFps = serviceMeasuredFps
                                    videoFrameRate = if (format.frameRate > 0) {
                                        "${format.frameRate.toInt()} fps"
                                    } else if (mFps > 0) {
                                        String.format("%.1f fps (measured)", mFps)
                                    } else {
                                        "N/A"
                                    }
                                    
                                    currentVideoBitrate = format.bitrate
                                    videoBitrate = if (currentVideoBitrate > 0) formatBitrate(currentVideoBitrate) else "Unknown"
                                    currentVideoBitrate = format.bitrate
                                }
                                if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                                    audioCodec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: "Unknown"
                                    audioSampleRate = if (format.sampleRate > 0) "${format.sampleRate / 1000}kHz" else "N/A"
                                    audioChannels = if (format.channelCount > 0) {
                                        when (format.channelCount) {
                                            1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"
                                            else -> "${format.channelCount}ch"
                                        }
                                    } else "N/A"
                                    currentAudioBitrate = format.bitrate
                                    audioBitrate = if (currentAudioBitrate > 0) formatBitrate(currentAudioBitrate) else "Unknown"
                                    currentAudioBitrate = format.bitrate
                                }
                                break // Found the selected track in this group
                            }
                        }
                    }
                }

                // If bitrate is still unknown for video but we have a bandwidth estimate, use a portion of it as a guess
                val bw = serviceBandwidth
                if (currentVideoBitrate <= 0 && bw > 0) {
                    val estimatedBitrate = (bw * 0.9).toInt()
                    videoBitrate = "~" + formatBitrate(estimatedBitrate)
                }

                // Use bandwidth estimate for network speed
                networkSpeed = if (bw > 0) formatBitrate(bw.toInt()) else {
                    val totalBitrate = (if (currentVideoBitrate > 0) currentVideoBitrate else 0) + 
                                     (if (currentAudioBitrate > 0) currentAudioBitrate else 0)
                    if (totalBitrate > 0) formatBitrate(totalBitrate) else "N/A"
                }
            }

            // Update stream elapsed time
            val startTime = serviceStartTimeMs
            if (startTime > 0L) {
                val elapsedSec = (android.os.SystemClock.elapsedRealtime() - startTime) / 1000
                val hours = elapsedSec / 3600
                val minutes = (elapsedSec % 3600) / 60
                val seconds = elapsedSec % 60
                streamElapsed = if (hours > 0) {
                    String.format("%d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format("%d:%02d", minutes, seconds)
                }
            }

            delay(CinemaAnimation.statsUpdateMs)
        }
    }

    // Calculate overlay size (55% width × 100% height)
    val overlayWidth = (configuration.screenWidthDp * 0.55).dp
    val overlayHeight = (configuration.screenHeightDp).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(TvDimensions.safeMarginVertical)
    ) {
        Box(
            modifier = Modifier
                .width(overlayWidth)
                .fillMaxHeight()
                .align(getQuadrantAlignment(quadrantPosition))
                .background(
                    CinemaGlassBackground,
                    shape = RoundedCornerShape(CinemaCornerRadius.medium)
                )
                .then(
                    if (isFocused) Modifier.border(
                        TvDimensions.borderFocusedStats,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(CinemaCornerRadius.medium)
                    ) else Modifier
                )
                .onFocusChanged { isFocused = it.isFocused }
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionCenter, Key.Enter -> {
                                // Single press closes overlay
                                onHide()
                                true
                            }
                            Key.Back -> {
                                // Close overlay (not the stream)
                                onHide()
                                true
                            }
                            Key.DirectionUp -> {
                                // Move to top
                                quadrantPosition = when (quadrantPosition) {
                                    QuadrantPosition.BOTTOM_LEFT -> QuadrantPosition.TOP_LEFT
                                    QuadrantPosition.BOTTOM_RIGHT -> QuadrantPosition.TOP_RIGHT
                                    else -> quadrantPosition
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                // Move to bottom
                                quadrantPosition = when (quadrantPosition) {
                                    QuadrantPosition.TOP_LEFT -> QuadrantPosition.BOTTOM_LEFT
                                    QuadrantPosition.TOP_RIGHT -> QuadrantPosition.BOTTOM_RIGHT
                                    else -> quadrantPosition
                                }
                                true
                            }
                            Key.DirectionLeft -> {
                                // Move to left
                                quadrantPosition = when (quadrantPosition) {
                                    QuadrantPosition.TOP_RIGHT -> QuadrantPosition.TOP_LEFT
                                    QuadrantPosition.BOTTOM_RIGHT -> QuadrantPosition.BOTTOM_LEFT
                                    else -> quadrantPosition
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                // Move to right
                                quadrantPosition = when (quadrantPosition) {
                                    QuadrantPosition.TOP_LEFT -> QuadrantPosition.TOP_RIGHT
                                    QuadrantPosition.BOTTOM_LEFT -> QuadrantPosition.BOTTOM_RIGHT
                                    else -> quadrantPosition
                                }
                                true
                            }
                            else -> true  // Consume all other keys when stats are visible
                        }
                    } else {
                        true  // Consume all key events
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Text(
                    text = "📊 Stats for Nerds",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

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

                // Two-column layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Video stats
                        SectionHeader("VIDEO")
                        CompactStatRow("Codec", videoCodec)
                        CompactStatRow("Res", videoResolution)
                        CompactStatRow("FPS", videoFrameRate)
                        CompactStatRow("Bitrate", videoBitrate)

                        // Audio stats
                        SectionHeader("AUDIO")
                        CompactStatRow("Codec", audioCodec)
                        CompactStatRow("Rate", audioSampleRate)
                        CompactStatRow("Ch", audioChannels)
                        CompactStatRow("Bitrate", audioBitrate)

                        // Network stats
                        SectionHeader("NETWORK")
                        CompactStatRow("Speed", networkSpeed)
                        val bwEstimate = serviceBandwidth
                        CompactStatRow("Bandwidth", if (bwEstimate > 0) formatBitrate(bwEstimate.toInt()) else "N/A")
                        CompactStatRow("Buffer", "${bufferHealth}s")
                        CompactStatRow("Buffered", formatTime(bufferedPosition))
                        val rebuffers = serviceRebufferCount
                        val rebufferTimeMs = serviceRebufferTimeMs
                        val rebufferColor = when {
                            rebuffers == 0 -> CinemaSuccess
                            rebuffers <= 3 -> CinemaWarning
                            else -> CinemaError
                        }
                        CompactStatRowColored("Rebuffers", "$rebuffers", rebufferColor)
                        if (rebufferTimeMs > 0) {
                            CompactStatRowColored("Rebuf Time", "${rebufferTimeMs / 1000}.${(rebufferTimeMs % 1000) / 100}s", rebufferColor)
                        }
                        val qSwitches = serviceQualitySwitches
                        if (qSwitches > 0) {
                            CompactStatRow("ABR Switches", "$qSwitches")
                        }
                    }

                    // Right Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Playback stats
                        SectionHeader("PLAYBACK")
                        CompactStatRow("Pos", formatTime(position))
                        CompactStatRow("Dur", if (duration > 0) formatTime(duration) else "Live")

                        // Performance metrics with color coding
                        SectionHeader("PERFORMANCE")
                        val totalFrames = serviceTotalFrames
                        val dropRate = if (totalFrames > 0) {
                            (droppedFrames.toFloat() / totalFrames * 100)
                        } else 0f

                        val dropColor = when {
                            dropRate < 0.5f -> CinemaSuccess // Green - Good
                            dropRate < 2.0f -> CinemaWarning // Yellow - Warning
                            else -> CinemaError // Red - Poor
                        }

                        CompactStatRowColored(
                            "Dropped",
                            "$droppedFrames / $totalFrames",
                            dropColor
                        )
                        if (totalFrames > 0) {
                            CompactStatRowColored(
                                "Drop Rate",
                                String.format("%.2f%%", dropRate),
                                dropColor
                            )
                        }

                        // Stream info
                        SectionHeader("STREAM")
                        CompactStatRow("Type", if (metadata.isLive) "Live" else "VOD")
                        CompactStatRow("Retries", "$serviceRetryCount")
                        CompactStatRow("Uptime", streamElapsed)
                        CompactStatRow("URL", metadata.streamUrl.substringAfterLast("/").take(20))

                        SectionHeader("DEVICE")
                        CompactStatRow("Model", android.os.Build.MODEL.take(15))
                        CompactStatRow("API", "${android.os.Build.VERSION.SDK_INT}")

                        // Audio DSP
                        SectionHeader("AUDIO DSP")
                        val nightModeColor = if (audioDspStats.nightModeEnabled) CinemaSuccess else CinemaTextPrimary
                        CompactStatRowColored("Night Mode", if (audioDspStats.nightModeEnabled) "ON" else "OFF", nightModeColor)

                        val nmActive = remember { StreamingPlaybackService.getInstance()?.nightModeManager?.enabled ?: false }
                        val isHalActive = remember { StreamingPlaybackService.getInstance()?.nightModeManager?.isActuallyActive ?: false }
                        val sessionId = remember {
                            val p = StreamingPlaybackService.getInstance()?.getPlayer()
                            if (p is androidx.media3.exoplayer.ExoPlayer) p.audioSessionId else 0
                        }
                        CompactStatRow("Audio Session", "$sessionId")
                        CompactStatRow("DSP Active", if (nmActive && sessionId != 0) "YES" else "NO")
                        if (nmActive) {
                            CompactStatRow("NM Engine", if (isHalActive) "HAL (System)" else "APP (Internal)")
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (isFocused) {
                    Column {
                        Text(
                            text = "D-pad to move • Double-tap center to hide",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow),
                            fontWeight = FontWeight.Medium
                        )
                        val caps = remember { org.njarasoa.fijerena.core.player.device.DeviceDetector.detect() }
                        Text(
                            text = "Build: ${org.njarasoa.fijerena.BuildConfig.BUILD_TIME} (${org.njarasoa.fijerena.BuildConfig.GIT_HASH})",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = CinemaTextSecondary.copy(alpha = 0.3f),
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Text(
                            text = "Device: ${android.os.Build.MODEL} | Type: ${caps.deviceType}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = CinemaTextSecondary.copy(alpha = 0.3f)
                        )
                    }
                }
            }
            }
        }
    }

    // Auto-request focus when overlay becomes visible
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun CompactStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = CinemaTextSecondary,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = CinemaTextPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CompactStatRowColored(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = CinemaTextSecondary,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        ),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}
