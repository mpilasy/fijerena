package org.njarasoa.fijerena.feature.player.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaWarning
import org.njarasoa.fijerena.ui.theme.MobileDimensions

@Composable
fun MobileStatsOverlay(
    playbackState: PlaybackState,
    metadata: PlayerMetadata,
    onClose: () -> Unit,
) {
    // Handle Back button to close overlay
    BackHandler(enabled = true) {
        onClose()
    }

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

    val serviceDroppedFrames by StreamingPlaybackService.getInstance()?.droppedFrames?.collectAsStateWithLifecycle(0L)
        ?: remember { mutableStateOf(0L) }
    val serviceTotalFrames by StreamingPlaybackService.getInstance()?.totalFrames?.collectAsStateWithLifecycle(0L)
        ?: remember { mutableStateOf(0L) }

    // Collect stream stats from service
    val serviceRetryCount by StreamingPlaybackService.getInstance()?.streamRetryCount?.collectAsStateWithLifecycle(0)
        ?: remember { mutableStateOf(0) }
    val serviceStartTimeMs by StreamingPlaybackService.getInstance()?.streamStartTimeMs?.collectAsStateWithLifecycle(0L)
        ?: remember { mutableStateOf(0L) }
    val serviceRebufferCount by StreamingPlaybackService.getInstance()?.rebufferCount?.collectAsStateWithLifecycle(0)
        ?: remember { mutableStateOf(0) }
    val serviceRebufferTimeMs by StreamingPlaybackService.getInstance()?.totalRebufferTimeMs?.collectAsStateWithLifecycle(0L)
        ?: remember { mutableStateOf(0L) }
    val serviceBandwidth by StreamingPlaybackService.getInstance()?.bandwidthEstimate?.collectAsStateWithLifecycle(0L)
        ?: remember { mutableStateOf(0L) }
    val serviceQualitySwitches by StreamingPlaybackService.getInstance()?.qualitySwitchCount?.collectAsStateWithLifecycle(0)
        ?: remember { mutableStateOf(0) }
    val serviceMeasuredFps by StreamingPlaybackService.getInstance()?.measuredFps?.collectAsStateWithLifecycle(0f)
        ?: remember { mutableStateOf(0f) }
    var streamElapsed by remember { mutableStateOf("0:00") }

    // Audio DSP stats
    val audioDspStats by StreamingPlaybackService.getInstance()?.audioDspStats?.collectAsStateWithLifecycle(
        org.njarasoa.fijerena.core.player.model
            .AudioDspStats(),
    ) ?: remember {
        mutableStateOf(
            org.njarasoa.fijerena.core.player.model
                .AudioDspStats(),
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            StreamingPlaybackService.getInstance()?.getPlayer()?.let { p ->
                bufferedPosition = p.bufferedPosition
                droppedFrames = serviceDroppedFrames

                val currentPos = p.currentPosition
                val buffered = p.bufferedPosition
                bufferHealth =
                    if (buffered > currentPos) {
                        ((buffered - currentPos) / 1000).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }

                val tracks = p.currentTracks
                var currentVideoBitrate = 0
                var currentAudioBitrate = 0

                // Fallback resolution from videoSize
                if (videoResolution == "N/A" || videoResolution == "0 x 0") {
                    val size = p.videoSize
                    if (size.width > 0 && size.height > 0) {
                        videoResolution = "${size.width} x ${size.height}"
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
                                    videoResolution = "${format.width} x ${format.height}"

                                    val mFps = serviceMeasuredFps
                                    videoFrameRate =
                                        if (format.frameRate > 0) {
                                            "${format.frameRate.toInt()} fps"
                                        } else if (mFps > 0) {
                                            String.format(java.util.Locale.getDefault(), "%.1f fps (measured)", mFps)
                                        } else {
                                            "N/A"
                                        }

                                    currentVideoBitrate = format.bitrate
                                    videoBitrate = if (currentVideoBitrate > 0) formatBitrate(currentVideoBitrate) else "Unknown"
                                }
                                if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                                    audioCodec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: "Unknown"
                                    audioSampleRate = if (format.sampleRate > 0) "${format.sampleRate / 1000}kHz" else "N/A"
                                    audioChannels =
                                        if (format.channelCount > 0) {
                                            when (format.channelCount) {
                                                1 -> "Mono"
                                                2 -> "Stereo"
                                                6 -> "5.1"
                                                8 -> "7.1"
                                                else -> "${format.channelCount}ch"
                                            }
                                        } else {
                                            "N/A"
                                        }
                                    currentAudioBitrate = format.bitrate
                                    audioBitrate = if (currentAudioBitrate > 0) formatBitrate(currentAudioBitrate) else "Unknown"
                                }
                                break // Found the selected track in this group
                            }
                        }
                    }
                }

                // If bitrate is still unknown for video but we have a bandwidth estimate, use a portion of it as a guess for VOD/IPTV
                val bw = serviceBandwidth
                if (currentVideoBitrate <= 0 && bw > 0) {
                    // Estimate video bitrate as ~90% of current throughput if audio is unknown
                    val estimatedBitrate = (bw * 0.9).toInt()
                    videoBitrate = "~" + formatBitrate(estimatedBitrate)
                }

                // Use bandwidth estimate for network speed
                networkSpeed =
                    if (bw > 0) {
                        formatBitrate(bw.toInt())
                    } else {
                        val totalBitrate =
                            (if (currentVideoBitrate > 0) currentVideoBitrate else 0) +
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
                streamElapsed =
                    if (hours > 0) {
                        String.format("%d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        String.format("%d:%02d", minutes, seconds)
                    }
            }

            delay(CinemaAnimation.statsUpdateMs)
        }
    }

    val position =
        when (playbackState) {
            is PlaybackState.Playing -> playbackState.position
            is PlaybackState.Paused -> playbackState.position
            else -> 0L
        }
    val duration =
        when (playbackState) {
            is PlaybackState.Playing -> playbackState.duration
            is PlaybackState.Paused -> playbackState.duration
            else -> 0L
        }
    val totalFrames = serviceTotalFrames
    val dropRate = if (totalFrames > 0) (droppedFrames.toFloat() / totalFrames * 100) else 0f
    val dropColor =
        when {
            dropRate < 0.5f -> CinemaSuccess
            dropRate < 2.0f -> CinemaWarning
            else -> CinemaError
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize(),
        // Not focusable, no background scrim - allows gestures to pass to the stream underneath
    ) {
        GlassPanel(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(CinemaSpacing.md)
                    .widthIn(max = MobileDimensions.statsOverlayMaxWidth),
        ) {
            val typography = MaterialTheme.typography
            Column(
                modifier =
                    Modifier
                        .padding(CinemaSpacing.md)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Stats for Nerds",
                        style = typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(MobileDimensions.iconLarge),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                            modifier = Modifier.size(MobileDimensions.iconSmall),
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
                val rebufferColor =
                    when {
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

                SectionHeader("AUDIO DSP")
                val nmEnabled = audioDspStats.nightModeEnabled
                val nightModeColor = if (nmEnabled) CinemaSuccess else CinemaTextPrimary
                StatRowColored("Night Mode", if (nmEnabled) "ON" else "OFF", nightModeColor)

                if (nmEnabled) {
                    val nmActive = remember { StreamingPlaybackService.getInstance()?.nightModeManager?.enabled ?: false }
                    val isHalActive = remember { StreamingPlaybackService.getInstance()?.nightModeManager?.isActuallyActive ?: false }
                    val sessionId =
                        remember {
                            val p = StreamingPlaybackService.getInstance()?.getPlayer()
                            if (p is androidx.media3.exoplayer.ExoPlayer) p.audioSessionId else 0
                        }
                    StatRow("Audio Session", "$sessionId")
                    StatRow("DSP Active", if (nmActive && sessionId != 0) "YES" else "NO")
                    if (nmActive) {
                        StatRow("NM Engine", if (isHalActive) "HAL (System)" else "APP (Internal)")
                    }
                    val encodingName =
                        when (audioDspStats.nmEncoding) {
                            androidx.media3.common.C.ENCODING_PCM_16BIT -> "PCM_16BIT"
                            androidx.media3.common.C.ENCODING_PCM_FLOAT -> "PCM_FLOAT"
                            androidx.media3.common.C.ENCODING_PCM_24BIT -> "PCM_24BIT"
                            androidx.media3.common.C.ENCODING_PCM_32BIT -> "PCM_32BIT"
                            0 -> "NOT_SET"
                            else -> "UNKNOWN(${audioDspStats.nmEncoding})"
                        }
                    StatRow("NM Encoding", encodingName)
                    StatRow("NM Proc Enabled", "${audioDspStats.nmEnabled}")
                    StatRow("NM Calls", "${audioDspStats.nmCallCount}")
                }

                val caps =
                    remember {
                        org.njarasoa.fijerena.core.player.device.DeviceDetector
                            .detect()
                    }
                Text(
                    text = "Build: ${org.njarasoa.fijerena.BuildConfig.BUILD_TIME} (${org.njarasoa.fijerena.BuildConfig.GIT_HASH})",
                    style = typography.labelSmall,
                    color = CinemaTextPrimary.copy(alpha = 0.3f),
                    modifier = Modifier.padding(top = 12.dp),
                )

                Text(
                    text = "Type: ${caps.deviceType}",
                    style = typography.labelSmall,
                    color = CinemaTextPrimary.copy(alpha = 0.3f),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun StatRow(
    label: String,
    value: String,
) {
    val typography = MaterialTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = typography.bodySmall,
            color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
        )
        Text(
            text = value,
            style = typography.bodySmall,
            color = CinemaTextPrimary,
        )
    }
}

@Composable
private fun StatRowColored(
    label: String,
    value: String,
    valueColor: Color,
) {
    val typography = MaterialTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = typography.bodySmall,
            color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
        )
        Text(
            text = value,
            style = typography.bodySmall,
            color = valueColor,
        )
    }
}
