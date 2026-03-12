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
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess
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

                // Estimate network speed from bitrate
                val tracks = p.currentTracks
                var totalBitrate = 0

                // Get video track
                for (i in 0 until tracks.groups.size) {
                    val group = tracks.groups[i]
                    if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && group.length > 0) {
                        val format = group.getTrackFormat(0)
                        videoCodec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: "Unknown"
                        videoResolution = "${format.width} × ${format.height}"
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
                                1 -> "Mono"
                                2 -> "Stereo"
                                6 -> "5.1"
                                8 -> "7.1"
                                else -> "${format.channelCount}ch"
                            }
                        } else "N/A"
                        audioBitrate = formatBitrate(format.bitrate)
                        if (format.bitrate > 0) totalBitrate += format.bitrate
                    }
                }

                networkSpeed = if (totalBitrate > 0) formatBitrate(totalBitrate) else "N/A"
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

    // Calculate overlay size (35% width × 50% height)
    val overlayWidth = (configuration.screenWidthDp * 0.35).dp
    val overlayHeight = (configuration.screenHeightDp * 0.50).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Box(
            modifier = Modifier
                .width(overlayWidth)
                .height(overlayHeight)
                .align(getQuadrantAlignment(quadrantPosition))
                .background(Color.Black.copy(alpha = CinemaAlpha.glass), shape = RoundedCornerShape(CinemaCornerRadius.medium))
                .then(
                    if (isFocused) Modifier.border(
                        TvDimensions.borderFocusedStats,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(CinemaCornerRadius.medium)
                    ) else Modifier
                )
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
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
                    .padding(20.dp)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
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
                        verticalArrangement = Arrangement.spacedBy(2.dp)
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

                        // Device info
                        SectionHeader("DEVICE")
                        CompactStatRow("Model", android.os.Build.MODEL.take(15))
                        CompactStatRow("API", "${android.os.Build.VERSION.SDK_INT}")

                        // AI Audio DSP
                        SectionHeader("AI AUDIO DSP")
                        val nightModeColor = if (audioDspStats.nightModeEnabled) CinemaSuccess else Color.White
                        CompactStatRowColored("Night Mode", if (audioDspStats.nightModeEnabled) "ON" else "OFF", nightModeColor)

                        val cvStatus = when {
                            audioDspStats.clearVoiceAutoDisabled -> "DISABLED (slow)"
                            audioDspStats.clearVoiceEnabled -> "ON (${(audioDspStats.clearVoiceStrength * 100).toInt()}%)"
                            else -> "OFF"
                        }
                        val cvColor = when {
                            audioDspStats.clearVoiceAutoDisabled -> CinemaError
                            audioDspStats.clearVoiceEnabled -> CinemaSuccess
                            else -> Color.White
                        }
                        CompactStatRowColored("Clear Voice", cvStatus, cvColor)
                        if (audioDspStats.clearVoiceEnabled || audioDspStats.aiFramesProcessed > 0) {
                            CompactStatRow("AI Latency", "${audioDspStats.aiLastInferenceMs}ms (avg ${String.format("%.1f", audioDspStats.aiAvgInferenceMs)}ms)")
                            val skipColor = when {
                                audioDspStats.aiFramesSkipped == 0L -> CinemaSuccess
                                audioDspStats.aiFramesSkipped < 10 -> CinemaWarning
                                else -> CinemaError
                            }
                            CompactStatRowColored("AI Frames", "${audioDspStats.aiFramesProcessed} ok / ${audioDspStats.aiFramesSkipped} skip", skipColor)
                        }
                        if (audioDspStats.voiceZoomAvailable) {
                            val vzColor = if (audioDspStats.voiceZoomEnabled) CinemaSuccess else Color.White
                            CompactStatRowColored("Voice Zoom", if (audioDspStats.voiceZoomEnabled) "ON" else "OFF", vzColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (isFocused) {
                    Text(
                        text = "D-pad to move • Double-tap center to hide",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = Color.White.copy(alpha = CinemaAlpha.textLow),
                        fontWeight = FontWeight.Medium
                    )
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
            color = Color.White.copy(alpha = CinemaAlpha.textMedium),
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = Color.White,
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
            color = Color.White.copy(alpha = CinemaAlpha.textMedium),
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
        modifier = Modifier.padding(top = 4.dp)
    )
}
