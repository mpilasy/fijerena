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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.player.model.formatBitrate
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaWarning
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

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

    val naText = stringResource(R.string.player_stats_na)
    val unknownText = stringResource(R.string.player_error_unknown)
    val monoText = stringResource(R.string.audio_channel_mono)
    val stereoText = stringResource(R.string.audio_channel_stereo)
    val channels51Text = stringResource(R.string.audio_channel_5_1)
    val channels71Text = stringResource(R.string.audio_channel_7_1)
    val liveTypeText = stringResource(R.string.player_stream_type_live)
    val vodTypeText = stringResource(R.string.player_stream_type_vod)
    val healthyText = stringResource(R.string.player_stats_health_healthy)
    val degradedFormat = stringResource(R.string.player_stats_health_degraded_format)
    val unstableFormat = stringResource(R.string.player_stats_health_unstable_format)

    var videoCodec by remember { mutableStateOf(naText) }
    var videoResolution by remember { mutableStateOf(naText) }
    var videoFrameRate by remember { mutableStateOf(naText) }
    var videoBitrate by remember { mutableStateOf(naText) }

    var audioCodec by remember { mutableStateOf(naText) }
    var audioSampleRate by remember { mutableStateOf(naText) }
    var audioChannels by remember { mutableStateOf(naText) }
    var audioBitrate by remember { mutableStateOf(naText) }

    var bufferedPosition by remember { mutableStateOf(0L) }
    var droppedFrames by remember { mutableStateOf(0L) }
    var networkSpeed by remember { mutableStateOf(naText) }
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
    val serviceMeasuredDroppedFps by StreamingPlaybackService.getInstance()?.measuredDroppedFps?.collectAsStateWithLifecycle(0f)
        ?: remember { mutableStateOf(0f) }
    val streamHealthState by StreamingPlaybackService.getInstance()?.streamHealthState?.collectAsStateWithLifecycle(org.njarasoa.fijerena.core.player.network.StreamHealthState())
        ?: remember { mutableStateOf(org.njarasoa.fijerena.core.player.network.StreamHealthState()) }
    var streamElapsed by remember { mutableStateOf("0:00") }

    LaunchedEffect(Unit) {
        // Persisted across ticks so an unchanged Tracks instance (no track/quality switch)
        // skips the O(N) group/track scan entirely instead of repeating it every tick.
        var lastTracks: androidx.media3.common.Tracks? = null
        var selectedVideoFormat: androidx.media3.common.Format? = null
        var currentVideoBitrate = 0
        var currentAudioBitrate = 0

        while (true) {
            StreamingPlaybackService.getInstance()?.getPlayer()?.let { p ->
                bufferedPosition = p.bufferedPosition

                val newDroppedFrames = serviceDroppedFrames
                if (newDroppedFrames != droppedFrames) droppedFrames = newDroppedFrames

                val currentPos = p.currentPosition
                val buffered = p.bufferedPosition
                val newBufferHealth =
                    if (buffered > currentPos) {
                        ((buffered - currentPos) / 1000).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }
                if (newBufferHealth != bufferHealth) bufferHealth = newBufferHealth

                // Fallback resolution from videoSize
                if (videoResolution == naText || videoResolution == "0 x 0") {
                    val size = p.videoSize
                    if (size.width > 0 && size.height > 0) {
                        videoResolution = "${size.width} x ${size.height}"
                    }
                }

                val tracks = p.currentTracks
                if (tracks !== lastTracks) {
                    lastTracks = tracks
                    selectedVideoFormat = null
                    currentVideoBitrate = 0
                    currentAudioBitrate = 0

                    for (i in 0 until tracks.groups.size) {
                        val group = tracks.groups[i]
                        if (group.isSelected) {
                            for (j in 0 until group.length) {
                                if (group.isTrackSelected(j)) {
                                    val format = group.getTrackFormat(j)
                                    if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                                        selectedVideoFormat = format
                                        val newCodec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: unknownText
                                        if (newCodec != videoCodec) videoCodec = newCodec
                                        val newResolution = "${format.width} x ${format.height}"
                                        if (newResolution != videoResolution) videoResolution = newResolution

                                        currentVideoBitrate = format.bitrate
                                        val newVideoBitrate = if (currentVideoBitrate > 0) formatBitrate(currentVideoBitrate) else unknownText
                                        if (newVideoBitrate != videoBitrate) videoBitrate = newVideoBitrate
                                    }
                                    if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                                        val newAudioCodec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: unknownText
                                        if (newAudioCodec != audioCodec) audioCodec = newAudioCodec
                                        val newSampleRate = if (format.sampleRate > 0) "${format.sampleRate / 1000}kHz" else naText
                                        if (newSampleRate != audioSampleRate) audioSampleRate = newSampleRate
                                        val newChannels =
                                            if (format.channelCount > 0) {
                                                when (format.channelCount) {
                                                    1 -> monoText
                                                    2 -> stereoText
                                                    6 -> channels51Text
                                                    8 -> channels71Text
                                                    else -> "${format.channelCount}ch"
                                                }
                                            } else {
                                                naText
                                            }
                                        if (newChannels != audioChannels) audioChannels = newChannels

                                        currentAudioBitrate = format.bitrate
                                        val newAudioBitrate = if (currentAudioBitrate > 0) formatBitrate(currentAudioBitrate) else unknownText
                                        if (newAudioBitrate != audioBitrate) audioBitrate = newAudioBitrate
                                    }
                                    break // Found the selected track in this group
                                }
                            }
                        }
                    }
                }

                // Frame rate depends on the live measured-fps signal too (common fallback for
                // streams whose container doesn't report a static rate), so it's recomputed
                // every tick rather than gated behind the tracks-changed check above.
                val fmt = selectedVideoFormat
                val mFps = serviceMeasuredFps
                val newFrameRate =
                    if (fmt != null && fmt.frameRate > 0) {
                        "${fmt.frameRate.toInt()} fps"
                    } else if (mFps > 0) {
                        String.format(java.util.Locale.getDefault(), "%.1f fps (measured)", mFps)
                    } else {
                        naText
                    }
                if (newFrameRate != videoFrameRate) videoFrameRate = newFrameRate

                // If bitrate is still unknown for video but we have a bandwidth estimate, use a portion of it as a guess for VOD/IPTV
                val bw = serviceBandwidth
                if (currentVideoBitrate <= 0 && bw > 0) {
                    // Estimate video bitrate as ~90% of current throughput if audio is unknown
                    val estimatedBitrate = (bw * 0.9).toInt()
                    val newVideoBitrate = "~" + formatBitrate(estimatedBitrate)
                    if (newVideoBitrate != videoBitrate) videoBitrate = newVideoBitrate
                }

                // Use bandwidth estimate for network speed
                val newNetworkSpeed =
                    if (bw > 0) {
                        formatBitrate(bw.toInt())
                    } else {
                        val totalBitrate =
                            (if (currentVideoBitrate > 0) currentVideoBitrate else 0) +
                                (if (currentAudioBitrate > 0) currentAudioBitrate else 0)
                        if (totalBitrate > 0) formatBitrate(totalBitrate) else naText
                    }
                if (newNetworkSpeed != networkSpeed) networkSpeed = newNetworkSpeed
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
                        text = stringResource(R.string.player_stats_title),
                        style = typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(MobileDimensions.iconLarge),
                    ) {
                        Icon(
                            CinemaIcons.Close,
                            contentDescription = stringResource(R.string.common_close),
                            tint = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                            modifier = Modifier.size(MobileDimensions.iconSmall),
                        )
                    }
                }

                SectionHeader(stringResource(R.string.player_stats_video))
                StatRow(stringResource(R.string.player_stats_codec), videoCodec)
                StatRow(stringResource(R.string.player_stats_resolution), videoResolution)
                StatRow(stringResource(R.string.player_stats_frame_rate), videoFrameRate)
                StatRow(stringResource(R.string.player_stats_bitrate), videoBitrate)

                SectionHeader(stringResource(R.string.player_stats_audio))
                StatRow(stringResource(R.string.player_stats_codec), audioCodec)
                StatRow(stringResource(R.string.player_stats_sample_rate), audioSampleRate)
                StatRow(stringResource(R.string.player_stats_channels), audioChannels)
                StatRow(stringResource(R.string.player_stats_bitrate), audioBitrate)

                SectionHeader(stringResource(R.string.player_stats_network))
                StatRow(stringResource(R.string.player_stats_speed), networkSpeed)
                val bwEstimate = serviceBandwidth
                StatRow(stringResource(R.string.player_stats_bandwidth), if (bwEstimate > 0) formatBitrate(bwEstimate.toInt()) else naText)
                StatRow(stringResource(R.string.player_stats_buffer), "${bufferHealth}s")
                StatRow(stringResource(R.string.player_stats_buffered), formatTime(bufferedPosition))
                val rebuffers = serviceRebufferCount
                val rebufferTimeMs = serviceRebufferTimeMs
                val rebufferColor =
                    when {
                        rebuffers == 0 -> CinemaSuccess
                        rebuffers <= 3 -> CinemaWarning
                        else -> CinemaError
                    }
                StatRowColored(stringResource(R.string.player_stats_rebuffers), "$rebuffers", rebufferColor)
                if (rebufferTimeMs > 0) {
                    StatRowColored(stringResource(R.string.player_stats_rebuf_time), "${rebufferTimeMs / 1000}.${(rebufferTimeMs % 1000) / 100}s", rebufferColor)
                }
                val qSwitches = serviceQualitySwitches
                if (qSwitches > 0) {
                    StatRow(stringResource(R.string.player_stats_abr_switches), "$qSwitches")
                }

                SectionHeader(stringResource(R.string.player_stats_playback))
                StatRow(stringResource(R.string.player_stats_position), formatTime(position))
                StatRow(stringResource(R.string.player_stats_duration), if (duration > 0) formatTime(duration) else liveTypeText)

                SectionHeader(stringResource(R.string.player_stats_performance))
                StatRowColored(stringResource(R.string.player_stats_dropped), "$droppedFrames / $totalFrames", dropColor)
                if (totalFrames > 0) {
                    StatRowColored(stringResource(R.string.player_stats_drop_rate), String.format("%.2f%%", dropRate), dropColor)
                }

                val currentDropFps = serviceMeasuredDroppedFps
                if (currentDropFps > 0f) {
                    val currentDropColor = when {
                        currentDropFps < 1.0f -> CinemaSuccess
                        currentDropFps < 10.0f -> CinemaWarning
                        else -> CinemaError
                    }
                    StatRowColored(stringResource(R.string.player_stats_drop_rate_per_sec), String.format("%.1f fps", currentDropFps), currentDropColor)
                }

                SectionHeader(stringResource(R.string.player_stats_stream))
                StatRow(stringResource(R.string.player_stats_type), if (metadata.isLive) liveTypeText else vodTypeText)
                StatRow(stringResource(R.string.player_stats_retries), "$serviceRetryCount")

                if (metadata.isLive) {
                    val health = streamHealthState
                    val healthText = when {
                        health.isDegraded -> String.format(degradedFormat, health.degradedAttempts)
                        !health.isHealthy -> String.format(unstableFormat, health.recycleAttempts)
                        else -> healthyText
                    }
                    val healthColor = when {
                        health.isDegraded -> CinemaError
                        !health.isHealthy -> CinemaWarning
                        else -> CinemaSuccess
                    }
                    StatRowColored(stringResource(R.string.player_stats_stream_health), healthText, healthColor)
                }

                StatRow(stringResource(R.string.player_stats_uptime), streamElapsed)
                StatRow(stringResource(R.string.player_stats_url), metadata.streamUrl.substringAfterLast("/").take(25))

                SectionHeader(stringResource(R.string.player_stats_device))
                StatRow(stringResource(R.string.player_stats_model), android.os.Build.MODEL)
                StatRow(stringResource(R.string.player_stats_api), "${android.os.Build.VERSION.SDK_INT}")

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
