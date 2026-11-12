@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.overlays

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.xtream.XtreamLoginPicker
import org.njarasoa.fijerena.core.player.audio.audioOutputLabel
import org.njarasoa.fijerena.core.player.audio.nightAudioGainLabel
import org.njarasoa.fijerena.core.player.display.ModeSpec
import org.njarasoa.fijerena.core.player.display.displayModeText
import org.njarasoa.fijerena.core.player.display.rateText
import org.njarasoa.fijerena.core.player.display.refreshRatesText
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.model.formatBitrate
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.GroupHeading
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.rememberConnectionLabel
import org.njarasoa.fijerena.core.ui.history.LocalSourceShortNames
import org.njarasoa.fijerena.core.ui.performance.AppPerformanceMonitor
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaWarning
import org.njarasoa.fijerena.ui.components.glassCard
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

data class StatsSnapshot(
    val videoCodec: String,
    val videoResolution: String,
    val videoFrameRate: String,
    val videoBitrate: String,
    val audioCodec: String,
    val audioSampleRate: String,
    val audioChannels: String,
    val audioBitrate: String,
    val audioOutput: String,
    val nightAudioGain: String?,
    val position: Long,
    val bufferedPosition: Long,
    val droppedFrames: Long,
    val networkSpeed: String,
    val bufferHealth: Int,
    val streamElapsed: String,
    val heap: AppPerformanceMonitor.HeapSnapshot,
    val uiFramesSkipped: Long,
)

@Composable
fun TvStatsOverlay(
    playbackState: PlaybackState,
    metadata: PlayerMetadata,
    onHide: () -> Unit = {},
) {
    val resources = LocalResources.current

    BackHandler(enabled = true) {
        onHide()
    }

    val naText = stringResource(R.string.player_stats_na)
    val connection = rememberConnectionLabel()
    val unknownText = stringResource(R.string.player_error_unknown)
    val vodTypeText = stringResource(R.string.player_stream_type_vod)
    val pcmOutputText = stringResource(R.string.player_stats_output_pcm)
    val passthroughOutputFormat = stringResource(R.string.player_stats_output_passthrough_format)
    val healthyText = stringResource(R.string.player_stats_health_healthy)
    val degradedFormat = stringResource(R.string.player_stats_health_degraded_format)
    val unstableFormat = stringResource(R.string.player_stats_health_unstable_format)

    var stats by remember {
        mutableStateOf(
            StatsSnapshot(
                videoCodec = naText,
                videoResolution = naText,
                videoFrameRate = naText,
                videoBitrate = naText,
                audioCodec = naText,
                audioSampleRate = naText,
                audioChannels = naText,
                audioBitrate = naText,
                audioOutput = naText,
                nightAudioGain = null,
                position = 0L,
                bufferedPosition = 0L,
                droppedFrames = 0L,
                networkSpeed = naText,
                bufferHealth = 0,
                streamElapsed = "0:00",
                heap = AppPerformanceMonitor.heapSnapshot(),
                uiFramesSkipped = 0L,
            ),
        )
    }

    val serviceDroppedFrames by StreamingPlaybackService.getInstance()?.droppedFrames?.collectAsStateWithLifecycle(0L)
        ?: remember { mutableStateOf(0L) }
    val serviceTotalFrames by StreamingPlaybackService.getInstance()?.totalFrames?.collectAsStateWithLifecycle(0L)
        ?: remember { mutableStateOf(0L) }

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
    val serviceContentFrameRate by StreamingPlaybackService.getInstance()?.contentFrameRate?.collectAsStateWithLifecycle(null)
        ?: remember { mutableStateOf<Float?>(null) }
    val displayModes = rememberDisplayModes()
    val serviceMeasuredDroppedFps by StreamingPlaybackService.getInstance()?.measuredDroppedFps?.collectAsStateWithLifecycle(0f)
        ?: remember { mutableStateOf(0f) }
    val streamHealthState by StreamingPlaybackService.getInstance()?.streamHealthState?.collectAsStateWithLifecycle(
        org.njarasoa.fijerena.core.player.network
            .StreamHealthState(),
    )
        ?: remember {
            mutableStateOf(
                org.njarasoa.fijerena.core.player.network
                    .StreamHealthState(),
            )
        }
    val serviceRecentDropRate by StreamingPlaybackService.getInstance()?.recentDropRate?.collectAsStateWithLifecycle(0f)
        ?: remember { mutableStateOf(0f) }

    val jankMonitor = remember { AppPerformanceMonitor.JankMonitor() }
    DisposableEffect(Unit) {
        jankMonitor.start()
        onDispose { jankMonitor.stop() }
    }

    LaunchedEffect(Unit) {
        // Kept across ticks, so an unchanged Tracks instance skips the group/track scan.
        var lastTracks: androidx.media3.common.Tracks? = null
        var selectedVideoFormat: androidx.media3.common.Format? = null
        var currentVideoBitrate = 0
        var currentAudioBitrate = 0

        var curVideoCodec = naText
        var curVideoResolution = naText
        var curVideoFrameRate = naText
        var curVideoBitrate = naText

        var curAudioCodec = naText
        var curAudioSampleRate = naText
        var curAudioChannels = naText
        var curAudioBitrate = naText

        var curPosition = 0L
        var curBufferedPosition = 0L
        var curDroppedFrames = 0L
        var curNetworkSpeed = naText
        var curBufferHealth = 0
        var curStreamElapsed = "0:00"

        while (true) {
            StreamingPlaybackService.getInstance()?.getPlayer()?.let { p ->
                curBufferedPosition = p.bufferedPosition

                val newDroppedFrames = serviceDroppedFrames
                curDroppedFrames = newDroppedFrames

                val currentPos = p.currentPosition
                curPosition = currentPos
                val buffered = p.bufferedPosition
                curBufferHealth =
                    if (buffered > currentPos) {
                        ((buffered - currentPos) / 1000).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }

                if (curVideoResolution == naText || curVideoResolution == "0 × 0") {
                    val size = p.videoSize
                    if (size.width > 0 && size.height > 0) {
                        curVideoResolution = "${size.width} × ${size.height}"
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
                                        curVideoCodec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: unknownText
                                        curVideoResolution = "${format.width} × ${format.height}"

                                        currentVideoBitrate = format.bitrate
                                        curVideoBitrate = if (currentVideoBitrate > 0) formatBitrate(currentVideoBitrate) else unknownText
                                    }
                                    if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                                        curAudioCodec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: unknownText
                                        curAudioSampleRate = if (format.sampleRate > 0) "${format.sampleRate / 1000}kHz" else naText
                                        curAudioChannels =
                                            if (format.channelCount > 0) {
                                                when (format.channelCount) {
                                                    1 -> resources.getString(R.string.audio_channel_mono)
                                                    2 -> resources.getString(R.string.audio_channel_stereo)
                                                    6 -> resources.getString(R.string.audio_channel_5_1)
                                                    8 -> resources.getString(R.string.audio_channel_7_1)
                                                    else -> resources.getString(R.string.audio_channel_custom, format.channelCount)
                                                }
                                            } else {
                                                naText
                                            }

                                        currentAudioBitrate = format.bitrate
                                        curAudioBitrate = if (currentAudioBitrate > 0) formatBitrate(currentAudioBitrate) else unknownText
                                    }
                                    break
                                }
                            }
                        }
                    }
                }

                val fmt = selectedVideoFormat
                val mFps = serviceMeasuredFps
                val contentFps = serviceContentFrameRate
                curVideoFrameRate =
                    if (fmt != null && fmt.frameRate > 0) {
                        resources.getString(R.string.player_stats_fps_unit, rateText(fmt.frameRate))
                    } else if (contentFps != null) {
                        // The rate the frame times show: two decimals tell 23.98 from 24.
                        resources.getString(R.string.player_stats_measured_fps, String.format(java.util.Locale.US, "%.2f", contentFps))
                    } else if (mFps > 0) {
                        resources.getString(R.string.player_stats_measured_fps, String.format(java.util.Locale.US, "%.1f", mFps))
                    } else {
                        naText
                    }

                val bw = serviceBandwidth
                if (currentVideoBitrate <= 0 && bw > 0) {
                    val estimatedBitrate = (bw * 0.9).toInt()
                    curVideoBitrate = "~" + formatBitrate(estimatedBitrate)
                }

                curNetworkSpeed =
                    if (bw > 0) {
                        formatBitrate(bw.toInt())
                    } else {
                        val totalBitrate =
                            (if (currentVideoBitrate > 0) currentVideoBitrate else 0) +
                                (if (currentAudioBitrate > 0) currentAudioBitrate else 0)
                        if (totalBitrate > 0) formatBitrate(totalBitrate) else naText
                    }
            }

            val curHeap = AppPerformanceMonitor.heapSnapshot()
            val curUiFramesSkipped = jankMonitor.skippedFrames

            val startTime = serviceStartTimeMs
            if (startTime > 0L) {
                val elapsedSec = (android.os.SystemClock.elapsedRealtime() - startTime) / 1000
                val hours = elapsedSec / 3600
                val minutes = (elapsedSec % 3600) / 60
                val seconds = elapsedSec % 60
                curStreamElapsed =
                    if (hours > 0) {
                        String.format("%d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        String.format("%d:%02d", minutes, seconds)
                    }
            }

            stats =
                StatsSnapshot(
                    videoCodec = curVideoCodec,
                    videoResolution = curVideoResolution,
                    videoFrameRate = curVideoFrameRate,
                    videoBitrate = curVideoBitrate,
                    audioCodec = curAudioCodec,
                    audioSampleRate = curAudioSampleRate,
                    audioChannels = curAudioChannels,
                    audioBitrate = curAudioBitrate,
                    audioOutput =
                        StreamingPlaybackService.getInstance()?.audioOutputEncoding?.value?.let {
                            audioOutputLabel(it, pcmOutputText, passthroughOutputFormat)
                        } ?: naText,
                    nightAudioGain = StreamingPlaybackService.getInstance()?.nightAudioGainDb()?.let(::nightAudioGainLabel),
                    position = curPosition,
                    bufferedPosition = curBufferedPosition,
                    droppedFrames = curDroppedFrames,
                    networkSpeed = curNetworkSpeed,
                    bufferHealth = curBufferHealth,
                    streamElapsed = curStreamElapsed,
                    heap = curHeap,
                    uiFramesSkipped = curUiFramesSkipped,
                )

            delay(CinemaAnimation.statsUpdateMs)
        }
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(TvDimensions.safeMarginVertical),
    ) {
        Box(
            modifier =
                Modifier
                    // Half-width panel in the top-right corner, under the clock: the OSD fills the bottom and drew over
                    // a panel there. Half the layout's width, not Configuration's, whose dp ignore the UI-scale density.
                    .width(maxWidth * 0.5f)
                    // As tall as its rows, up to most of the screen: it cannot take focus, so rows past a fixed height
                    // could not be scrolled to. The cap comes from the layout, not Configuration (see above).
                    .heightIn(max = maxHeight * 0.9f)
                    .align(Alignment.TopEnd)
                    .padding(top = Spacing.xxl)
                    .glassCard(),
            // Not focusable: keys pass on to the stream.
        ) {
            Box(
                modifier = Modifier.padding(Spacing.lg),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    CinemaText(
                        text = "📊 " + stringResource(R.string.player_stats_title),
                        role = TextRole.Heading,
                        tone = TextTone.Primary,
                        emphasis = true,
                    )
                    // The provider's names as sent: everywhere else they are shown cleaned (the tag
                    // as a badge, an episode's own name only).
                    StatNameRow(
                        stringResource(R.string.player_stats_raw_name),
                        if (metadata.isLive) metadata.channelName.ifBlank { metadata.title } else metadata.title,
                    )
                    metadata.showTitle?.let { StatNameRow(stringResource(R.string.player_stats_raw_series), it) }
                    // The source the stream plays from, with its badge short name when that differs.
                    metadata.sourceName?.let { name ->
                        val short = LocalSourceShortNames.current.byName[name]?.takeIf { it != name }
                        StatNameRow(stringResource(R.string.player_stats_source), short?.let { "$name ($it)" } ?: name)
                    }
                    // Shared logins: which of the source's logins this stream plays on.
                    XtreamLoginPicker.describe(metadata.streamUrl)?.let { (n, of) ->
                        StatNameRow(stringResource(R.string.player_stats_login), stringResource(R.string.player_stats_login_format, n, of))
                    }

                    // Polled (stats.position, every tick of the LaunchedEffect above), not read from playbackState:
                    // PlaybackState.Playing.position is a snapshot from the last state change, so during steady
                    // playback it looked frozen.
                    val position = stats.position

                    val duration =
                        when (playbackState) {
                            is PlaybackState.Playing -> playbackState.duration
                            is PlaybackState.Paused -> playbackState.duration
                            else -> 0L
                        }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                        ) {
                            SectionHeader(stringResource(R.string.player_stats_video))
                            CompactStatRow(stringResource(R.string.player_stats_codec), stats.videoCodec)
                            CompactStatRow(stringResource(R.string.player_stats_res), stats.videoResolution)
                            CompactStatRow(stringResource(R.string.player_stats_fps), stats.videoFrameRate)
                            StatNameRow(
                                stringResource(R.string.player_stats_display),
                                displayModes?.let { displayModeText(it.current) } ?: naText,
                            )
                            StatNameRow(
                                stringResource(R.string.player_stats_modes),
                                displayModes?.let { refreshRatesText(it.supported, it.current) } ?: naText,
                            )
                            CompactStatRow(stringResource(R.string.player_stats_bitrate), stats.videoBitrate)

                            SectionHeader(stringResource(R.string.player_stats_audio))
                            CompactStatRow(stringResource(R.string.player_stats_codec), stats.audioCodec)
                            CompactStatRow(stringResource(R.string.player_stats_rate), stats.audioSampleRate)
                            CompactStatRow(stringResource(R.string.player_stats_ch), stats.audioChannels)
                            CompactStatRow(stringResource(R.string.player_stats_bitrate), stats.audioBitrate)
                            CompactStatRow(stringResource(R.string.player_stats_output), stats.audioOutput)
                            stats.nightAudioGain?.let { CompactStatRow(stringResource(R.string.night_audio_title), it) }

                            SectionHeader(stringResource(R.string.player_stats_network))
                            CompactStatRow(
                                stringResource(R.string.device_info_connection),
                                connection?.let { stringResource(it) } ?: naText,
                            )
                            CompactStatRow(stringResource(R.string.player_stats_speed), stats.networkSpeed)
                            val bwEstimate = serviceBandwidth
                            CompactStatRow(
                                stringResource(R.string.player_stats_bandwidth),
                                if (bwEstimate >
                                    0
                                ) {
                                    formatBitrate(bwEstimate.toInt())
                                } else {
                                    naText
                                },
                            )
                            CompactStatRow(
                                stringResource(R.string.player_stats_buffer),
                                stringResource(R.string.player_stats_seconds_unit, stats.bufferHealth),
                            )
                            CompactStatRow(stringResource(R.string.player_stats_buffered), formatTime(stats.bufferedPosition))
                            val rebuffers = serviceRebufferCount
                            val rebufferTimeMs = serviceRebufferTimeMs
                            val rebufferColor =
                                when {
                                    rebuffers == 0 -> TextTone.Success
                                    rebuffers <= 3 -> TextTone.Warning
                                    else -> TextTone.Danger
                                }
                            CompactStatRowColored(stringResource(R.string.player_stats_rebuffers), "$rebuffers", rebufferColor)
                            if (rebufferTimeMs > 0) {
                                CompactStatRowColored(
                                    stringResource(R.string.player_stats_rebuf_time),
                                    stringResource(
                                        R.string.player_stats_rebuf_time_format,
                                        rebufferTimeMs / 1000,
                                        (rebufferTimeMs % 1000) / 100,
                                    ),
                                    rebufferColor,
                                )
                            }
                            val qSwitches = serviceQualitySwitches
                            if (qSwitches > 0) {
                                CompactStatRow(stringResource(R.string.player_stats_abr_switches), "$qSwitches")
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                        ) {
                            SectionHeader(stringResource(R.string.player_stats_playback))
                            CompactStatRow(stringResource(R.string.player_stats_pos), formatTime(position))
                            CompactStatRow(
                                stringResource(R.string.player_stats_dur),
                                if (duration > 0) formatTime(duration) else stringResource(R.string.player_live),
                            )

                            SectionHeader(stringResource(R.string.player_stats_performance))
                            val totalFrames = serviceTotalFrames
                            val dropRate =
                                if (totalFrames > 0) {
                                    (stats.droppedFrames.toFloat() / totalFrames * 100)
                                } else {
                                    0f
                                }

                            val dropColor =
                                when {
                                    dropRate < 0.5f -> TextTone.Success
                                    dropRate < 2.0f -> TextTone.Warning
                                    else -> TextTone.Danger
                                }

                            CompactStatRowColored(
                                stringResource(R.string.player_stats_dropped),
                                stringResource(R.string.player_stats_dropped_format, stats.droppedFrames, totalFrames),
                                dropColor,
                            )
                            if (totalFrames > 0) {
                                CompactStatRowColored(
                                    stringResource(R.string.player_stats_drop_rate),
                                    String.format(java.util.Locale.US, "%.2f%%", dropRate),
                                    dropColor,
                                )
                            }

                            // Short-window rate: the cumulative one above averages a bad burst
                            // away against however long the stream has already been clean.
                            val recentDropRate = serviceRecentDropRate
                            val recentDropColor =
                                when {
                                    recentDropRate < 0.5f -> TextTone.Success
                                    recentDropRate < 2.0f -> TextTone.Warning
                                    else -> TextTone.Danger
                                }
                            CompactStatRowColored(
                                stringResource(R.string.player_stats_drop_rate_recent),
                                String.format(java.util.Locale.US, "%.2f%%", recentDropRate),
                                recentDropColor,
                            )

                            val currentDropFps = serviceMeasuredDroppedFps
                            if (currentDropFps > 0f) {
                                val currentDropColor =
                                    when {
                                        currentDropFps < 1.0f -> TextTone.Success
                                        currentDropFps < 10.0f -> TextTone.Warning
                                        else -> TextTone.Danger
                                    }
                                CompactStatRowColored(
                                    stringResource(R.string.player_stats_drop_rate_per_sec),
                                    String.format(java.util.Locale.US, "%.1f fps", currentDropFps),
                                    currentDropColor,
                                )
                            }

                            SectionHeader(stringResource(R.string.player_stats_app))
                            val heapColor =
                                when {
                                    stats.heap.usedPercent < 60 -> TextTone.Success
                                    stats.heap.usedPercent < 85 -> TextTone.Warning
                                    else -> TextTone.Danger
                                }
                            CompactStatRowColored(
                                stringResource(R.string.player_stats_heap),
                                stringResource(
                                    R.string.player_stats_heap_format,
                                    stats.heap.usedMb,
                                    stats.heap.maxMb,
                                    stats.heap.usedPercent,
                                ),
                                heapColor,
                            )
                            CompactStatRow(
                                stringResource(R.string.player_stats_gc),
                                stringResource(R.string.player_stats_gc_format, stats.heap.gcCount, stats.heap.gcTimeMs),
                            )
                            CompactStatRowColored(
                                stringResource(R.string.player_stats_ui_skipped),
                                "${stats.uiFramesSkipped}",
                                if (stats.uiFramesSkipped == 0L) TextTone.Success else TextTone.Warning,
                            )

                            SectionHeader(stringResource(R.string.player_stats_stream))
                            CompactStatRow(
                                stringResource(R.string.player_stats_type),
                                if (metadata.isLive) stringResource(R.string.player_live) else vodTypeText,
                            )
                            CompactStatRow(stringResource(R.string.player_stats_retries), "$serviceRetryCount")

                            if (metadata.isLive) {
                                val health = streamHealthState
                                val healthText =
                                    when {
                                        health.isDegraded -> String.format(degradedFormat, health.degradedAttempts)
                                        !health.isHealthy -> String.format(unstableFormat, health.recycleAttempts)
                                        else -> healthyText
                                    }
                                val healthColor =
                                    when {
                                        health.isDegraded -> TextTone.Danger
                                        !health.isHealthy -> TextTone.Warning
                                        else -> TextTone.Success
                                    }
                                CompactStatRowColored(stringResource(R.string.player_stats_stream_health), healthText, healthColor)
                            }

                            CompactStatRow(stringResource(R.string.player_stats_uptime), stats.streamElapsed)
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

                    Column {
                        CinemaText(
                            text = stringResource(R.string.player_stats_hint),
                            role = TextRole.Label,
                            tone = TextTone.Secondary,
                            emphasis = true,
                        )
                        val caps =
                            remember {
                                org.njarasoa.fijerena.core.player.device.DeviceDetector
                                    .detect()
                            }
                        CinemaText(
                            text =
                                stringResource(
                                    R.string.player_stats_build_format,
                                    org.njarasoa.fijerena.BuildConfig.BUILD_TIME,
                                    org.njarasoa.fijerena.BuildConfig.GIT_HASH,
                                ),
                            role = TextRole.Caption,
                            tone = TextTone.Secondary,
                            modifier = Modifier.padding(top = Spacing.xxs),
                        )

                        CinemaText(
                            text = stringResource(R.string.player_stats_device_type_format, android.os.Build.MODEL, caps.deviceType),
                            role = TextRole.Caption,
                            tone = TextTone.Secondary,
                        )
                    }
                }
            }
        }
    }
}

/** The display's mode now and the modes it offers. */
private data class DisplayModes(
    val current: ModeSpec,
    val supported: List<ModeSpec>,
)

private fun Display.Mode.toSpec() = ModeSpec(modeId, physicalWidth, physicalHeight, refreshRate)

private fun readDisplayModes(display: Display?): DisplayModes? =
    display?.let { d -> DisplayModes(d.mode.toSpec(), d.supportedModes.map { it.toSpec() }) }

/** The modes of the display that shows the player, read again when the display changes its mode. */
@Composable
private fun rememberDisplayModes(): DisplayModes? {
    val view = LocalView.current
    var modes by remember(view) { mutableStateOf(readDisplayModes(view.display)) }
    DisposableEffect(view) {
        val manager = view.context.getSystemService(DisplayManager::class.java)
        val listener =
            object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = Unit

                override fun onDisplayRemoved(displayId: Int) = Unit

                override fun onDisplayChanged(displayId: Int) {
                    if (displayId == view.display?.displayId) modes = readDisplayModes(view.display)
                }
            }
        manager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager?.unregisterDisplayListener(listener) }
    }
    return modes
}

/** A label and a long value (a provider's raw name, the display modes): the label keeps its width, the value wraps to two lines. */
@Composable
private fun StatNameRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        CinemaText(text = label, role = TextRole.Label, tone = TextTone.Secondary)
        CinemaText(
            text = value,
            role = TextRole.Label,
            emphasis = true,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompactStatRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        CinemaText(text = label, role = TextRole.Label, tone = TextTone.Secondary, modifier = Modifier.weight(1f, fill = false))
        CinemaText(text = value, role = TextRole.Label, emphasis = true)
    }
}

@Composable
private fun CompactStatRowColored(
    label: String,
    value: String,
    valueTone: TextTone,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        CinemaText(text = label, role = TextRole.Label, tone = TextTone.Secondary, modifier = Modifier.weight(1f, fill = false))
        CinemaText(text = value, role = TextRole.Label, tone = valueTone, emphasis = true)
    }
}

@Composable
private fun SectionHeader(title: String) {
    GroupHeading(title, modifier = Modifier.padding(top = Spacing.xs))
}
