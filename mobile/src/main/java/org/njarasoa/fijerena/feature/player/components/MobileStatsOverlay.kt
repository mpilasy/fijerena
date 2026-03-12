package org.njarasoa.fijerena.feature.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.feature.player.utils.formatBitrate
import org.njarasoa.fijerena.feature.player.utils.formatTime
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.ui.theme.CinemaWarning
import org.njarasoa.fijerena.ui.theme.MobileDimensions

@Composable
fun MobileStatsOverlay(
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

    LaunchedEffect(Unit) {
        while (true) {
            StreamingPlaybackService.getInstance()?.getPlayer()?.let { p ->
                bufferedPosition = p.bufferedPosition
                droppedFrames = serviceDroppedFrames

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
    val totalFrames = serviceTotalFrames
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
                val bwEstimate = serviceBandwidth
                StatRow("Bandwidth", if (bwEstimate > 0) formatBitrate(bwEstimate.toInt()) else "N/A")
                StatRow("Buffer", "${bufferHealth}s")
                StatRow("Buffered", formatTime(bufferedPosition))
                val rebuffers = serviceRebufferCount
                val rebufferTimeMs = serviceRebufferTimeMs
                val rebufferColor = when {
                    rebuffers == 0 -> CinemaSuccess
                    rebuffers <= 3 -> CinemaWarning
                    else -> CinemaError
                }
                StatRowColored("Rebuffers", "$rebuffers", rebufferColor)
                if (rebufferTimeMs > 0) {
                    StatRowColored("Rebuf Time", "${rebufferTimeMs / 1000}.${(rebufferTimeMs % 1000) / 100}s", rebufferColor)
                }
                val qSwitches = serviceQualitySwitches
                if (qSwitches > 0) {
                    StatRow("ABR Switches", "$qSwitches")
                }

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
                StatRow("Retries", "$serviceRetryCount")
                StatRow("Uptime", streamElapsed)
                StatRow("URL", metadata.streamUrl.substringAfterLast("/").take(25))

                SectionHeader("DEVICE")
                StatRow("Model", android.os.Build.MODEL)
                StatRow("API", "${android.os.Build.VERSION.SDK_INT}")

                SectionHeader("AI AUDIO DSP")
                val nightModeColor = if (audioDspStats.nightModeEnabled) CinemaSuccess else Color.White
                StatRowColored("Night Mode", if (audioDspStats.nightModeEnabled) "ON" else "OFF", nightModeColor)

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
                StatRowColored("Clear Voice", cvStatus, cvColor)
                if (audioDspStats.clearVoiceEnabled || audioDspStats.aiFramesProcessed > 0) {
                    StatRow("AI Latency", "${audioDspStats.aiLastInferenceMs}ms (avg ${String.format("%.1f", audioDspStats.aiAvgInferenceMs)}ms)")
                    val skipColor = when {
                        audioDspStats.aiFramesSkipped == 0L -> CinemaSuccess
                        audioDspStats.aiFramesSkipped < 10 -> CinemaWarning
                        else -> CinemaError
                    }
                    StatRowColored("AI Frames", "${audioDspStats.aiFramesProcessed} ok / ${audioDspStats.aiFramesSkipped} skip", skipColor)
                }
                if (audioDspStats.voiceZoomAvailable) {
                    val vzColor = if (audioDspStats.voiceZoomEnabled) CinemaSuccess else Color.White
                    StatRowColored("Voice Zoom", if (audioDspStats.voiceZoomEnabled) "ON" else "OFF", vzColor)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val typography = MaterialTheme.typography
    val sectionHeaderStyle = remember(typography) { typography.labelSmall.copy(fontSize = 11.sp) }
    Text(
        text = title,
        style = sectionHeaderStyle,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    val typography = MaterialTheme.typography
    val bodySmall12sp = remember(typography) { typography.bodySmall.copy(fontSize = 12.sp) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = bodySmall12sp,
            color = Color.White.copy(alpha = CinemaAlpha.textMedium)
        )
        Text(
            text = value,
            style = bodySmall12sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatRowColored(label: String, value: String, valueColor: Color) {
    val typography = MaterialTheme.typography
    val bodySmall12sp = remember(typography) { typography.bodySmall.copy(fontSize = 12.sp) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = bodySmall12sp,
            color = Color.White.copy(alpha = CinemaAlpha.textMedium)
        )
        Text(
            text = value,
            style = bodySmall12sp,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}
