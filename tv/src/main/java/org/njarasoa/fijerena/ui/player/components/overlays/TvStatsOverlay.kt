@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.overlays

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaWarning
import org.njarasoa.fijerena.core.player.model.formatBitrate
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.ui.theme.TvDimensions

@Composable
fun TvStatsOverlay(
    playbackState: PlaybackState,
    metadata: PlayerMetadata,
    onHide: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current

    // Handle Back button to close overlay
    BackHandler(enabled = true) {
        onHide()
    }

    val naText = stringResource(R.string.player_stats_na)
    val unknownText = stringResource(R.string.player_error_unknown)
    val vodTypeText = stringResource(R.string.player_stream_type_vod)
    val healthyText = stringResource(R.string.player_stats_health_healthy)
    val degradedFormat = stringResource(R.string.player_stats_health_degraded_format)
    val unstableFormat = stringResource(R.string.player_stats_health_unstable_format)

    // Get current track info
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

    // Collect dropped frames from service
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

    // Update stats periodically
    LaunchedEffect(Unit) {
        // Persisted across ticks so an unchanged Tracks instance (no track/quality switch)
        // skips the O(N) group/track scan entirely instead of repeating it every tick.
        var lastTracks: androidx.media3.common.Tracks? = null
        var selectedVideoFormat: androidx.media3.common.Format? = null
        var currentVideoBitrate = 0
        var currentAudioBitrate = 0

        while (true) {
            StreamingPlaybackService.getInstance()?.getPlayer()?.let { p ->
                // Update buffered position
                bufferedPosition = p.bufferedPosition

                // Get dropped frames from analytics
                val newDroppedFrames = serviceDroppedFrames
                if (newDroppedFrames != droppedFrames) droppedFrames = newDroppedFrames

                // Calculate buffer health (percentage of buffer vs target)
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
                if (videoResolution == naText || videoResolution == "0 × 0") {
                    val size = p.videoSize
                    if (size.width > 0 && size.height > 0) {
                        videoResolution = "${size.width} × ${size.height}"
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
                                        val newResolution = "${format.width} × ${format.height}"
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
                                                    1 -> context.getString(R.string.audio_channel_mono)
                                                    2 -> context.getString(R.string.audio_channel_stereo)
                                                    6 -> context.getString(R.string.audio_channel_5_1)
                                                    8 -> context.getString(R.string.audio_channel_7_1)
                                                    else -> context.getString(R.string.audio_channel_custom, format.channelCount)
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
                        context.getString(R.string.player_stats_fps_unit, fmt.frameRate.toInt())
                    } else if (mFps > 0) {
                        context.getString(R.string.player_stats_measured_fps, mFps)
                    } else {
                        naText
                    }
                if (newFrameRate != videoFrameRate) videoFrameRate = newFrameRate

                // If bitrate is still unknown for video but we have a bandwidth estimate, use a portion of it as a guess
                val bw = serviceBandwidth
                if (currentVideoBitrate <= 0 && bw > 0) {
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

    // Compact half-screen panel anchored to the bottom-right so the rest of the OSD stays visible.
    val overlayWidth = (configuration.screenWidthDp * 0.5).dp

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(TvDimensions.safeMarginVertical),
    ) {
        Box(
            modifier =
                Modifier
                    .width(overlayWidth)
                    .fillMaxHeight(0.5f)
                    .align(Alignment.BottomEnd)
                    .background(
                        CinemaGlassBackground,
                        shape = RoundedCornerShape(CinemaCornerRadius.medium),
                    ).border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(CinemaCornerRadius.medium),
                    ),
            // Not focusable - allows keys to pass to the stream
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Header
                    Text(
                        text = "📊 " + stringResource(R.string.player_stats_title),
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )

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

                    // Two-column layout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Left Column
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // Video stats
                            SectionHeader(stringResource(R.string.player_stats_video))
                            CompactStatRow(stringResource(R.string.player_stats_codec), videoCodec)
                            CompactStatRow(stringResource(R.string.player_stats_res), videoResolution)
                            CompactStatRow(stringResource(R.string.player_stats_fps), videoFrameRate)
                            CompactStatRow(stringResource(R.string.player_stats_bitrate), videoBitrate)

                            // Audio stats
                            SectionHeader(stringResource(R.string.player_stats_audio))
                            CompactStatRow(stringResource(R.string.player_stats_codec), audioCodec)
                            CompactStatRow(stringResource(R.string.player_stats_rate), audioSampleRate)
                            CompactStatRow(stringResource(R.string.player_stats_ch), audioChannels)
                            CompactStatRow(stringResource(R.string.player_stats_bitrate), audioBitrate)

                            // Network stats
                            SectionHeader(stringResource(R.string.player_stats_network))
                            CompactStatRow(stringResource(R.string.player_stats_speed), networkSpeed)
                            val bwEstimate = serviceBandwidth
                            CompactStatRow(stringResource(R.string.player_stats_bandwidth), if (bwEstimate > 0) formatBitrate(bwEstimate.toInt()) else naText)
                            CompactStatRow(stringResource(R.string.player_stats_buffer), stringResource(R.string.player_stats_seconds_unit, bufferHealth))
                            CompactStatRow(stringResource(R.string.player_stats_buffered), formatTime(bufferedPosition))
                            val rebuffers = serviceRebufferCount
                            val rebufferTimeMs = serviceRebufferTimeMs
                            val rebufferColor =
                                when {
                                    rebuffers == 0 -> CinemaSuccess
                                    rebuffers <= 3 -> CinemaWarning
                                    else -> CinemaError
                                }
                            CompactStatRowColored(stringResource(R.string.player_stats_rebuffers), "$rebuffers", rebufferColor)
                            if (rebufferTimeMs > 0) {
                                CompactStatRowColored(
                                    stringResource(R.string.player_stats_rebuf_time),
                                    stringResource(R.string.player_stats_rebuf_time_format, rebufferTimeMs / 1000, (rebufferTimeMs % 1000) / 100),
                                    rebufferColor,
                                )
                            }
                            val qSwitches = serviceQualitySwitches
                            if (qSwitches > 0) {
                                CompactStatRow(stringResource(R.string.player_stats_abr_switches), "$qSwitches")
                            }
                        }

                        // Right Column
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // Playback stats
                            SectionHeader(stringResource(R.string.player_stats_playback))
                            CompactStatRow(stringResource(R.string.player_stats_pos), formatTime(position))
                            CompactStatRow(
                                stringResource(R.string.player_stats_dur),
                                if (duration > 0) formatTime(duration) else stringResource(R.string.player_live)
                            )

                            // Performance metrics with color coding
                            SectionHeader(stringResource(R.string.player_stats_performance))
                            val totalFrames = serviceTotalFrames
                            val dropRate =
                                if (totalFrames > 0) {
                                    (droppedFrames.toFloat() / totalFrames * 100)
                                } else {
                                    0f
                                }

                            val dropColor =
                                when {
                                    dropRate < 0.5f -> CinemaSuccess // Green - Good
                                    dropRate < 2.0f -> CinemaWarning // Yellow - Warning
                                    else -> CinemaError // Red - Poor
                                }

                            CompactStatRowColored(
                                stringResource(R.string.player_stats_dropped),
                                stringResource(R.string.player_stats_dropped_format, droppedFrames, totalFrames),
                                dropColor,
                            )
                            if (totalFrames > 0) {
                                CompactStatRowColored(
                                    stringResource(R.string.player_stats_drop_rate),
                                    String.format("%.2f%%", dropRate),
                                    dropColor,
                                )
                            }
                            
                            val currentDropFps = serviceMeasuredDroppedFps
                            if (currentDropFps > 0f) {
                                val currentDropColor = when {
                                    currentDropFps < 1.0f -> CinemaSuccess
                                    currentDropFps < 10.0f -> CinemaWarning
                                    else -> CinemaError
                                }
                                CompactStatRowColored(
                                    stringResource(R.string.player_stats_drop_rate_per_sec),
                                    String.format("%.1f fps", currentDropFps),
                                    currentDropColor
                                )
                            }

                            // Stream info
                            SectionHeader(stringResource(R.string.player_stats_stream))
                            CompactStatRow(
                                stringResource(R.string.player_stats_type),
                                if (metadata.isLive) stringResource(R.string.player_live) else vodTypeText
                            )
                            CompactStatRow(stringResource(R.string.player_stats_retries), "$serviceRetryCount")
                            
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
                                CompactStatRowColored(stringResource(R.string.player_stats_stream_health), healthText, healthColor)
                            }
                            
                            CompactStatRow(stringResource(R.string.player_stats_uptime), streamElapsed)
                            CompactStatRow(stringResource(R.string.player_stats_url), metadata.streamUrl.substringAfterLast("/").take(20))

                            SectionHeader(stringResource(R.string.player_stats_device))
                            CompactStatRow(
                                stringResource(R.string.player_stats_model),
                                android.os.Build.MODEL
                                    .take(15),
                            )
                            CompactStatRow(stringResource(R.string.player_stats_api), "${android.os.Build.VERSION.SDK_INT}")
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Column {
                        Text(
                            text = stringResource(R.string.player_stats_hint),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow),
                            fontWeight = FontWeight.Medium,
                        )
                        val caps =
                            remember {
                                org.njarasoa.fijerena.core.player.device.DeviceDetector
                                    .detect()
                            }
                        Text(
                            text = stringResource(R.string.player_stats_build_format, org.njarasoa.fijerena.BuildConfig.BUILD_TIME, org.njarasoa.fijerena.BuildConfig.GIT_HASH),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = CinemaTextSecondary.copy(alpha = 0.3f),
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        Text(
                            text = stringResource(R.string.player_stats_device_type_format, android.os.Build.MODEL, caps.deviceType),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = CinemaTextSecondary.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactStatRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                ),
            color = CinemaTextSecondary,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = value,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                ),
            color = CinemaTextPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CompactStatRowColored(
    label: String,
    value: String,
    valueColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                ),
            color = CinemaTextSecondary,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = value,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                ),
            color = valueColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style =
            MaterialTheme.typography.labelMedium.copy(
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            ),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}
