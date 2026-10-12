package org.njarasoa.fijerena.core.ui.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.ui.R

/**
 * A devices-list line for what a device is playing, on TV and mobile alike: "▶ Playing · Malcolm X
 * · 42:10 / 2:00:15 · Kid · My IPTV", "▶ Playing · The King of Queens S1:E12 · …", "▶ Live · BBC
 * World News — Newsday · Kid · My IPTV", "⏸ Paused · …". The programme is left out when the channel
 * has no guide data; the position (films and episodes) and the source when the sender didn't send
 * them. While it plays, the position runs on once a second from the sender's last report.
 */
@Composable
fun nowPlayingLine(nowPlaying: SyncPayloads.NowPlaying): String {
    val channel = nowPlaying.channelName ?: nowPlaying.title
    val programme = nowPlaying.programTitle
    val what =
        when {
            nowPlaying.isLive && programme != null -> stringResource(R.string.live_sync_now_programme, channel, programme)
            nowPlaying.isLive -> channel
            nowPlaying.showTitle != null -> listOfNotNull(nowPlaying.showTitle, nowPlaying.episodeLabel).joinToString(" ")
            else -> nowPlaying.title
        }
    val ticking = nowPlaying.state == SyncPayloads.NowPlaying.PLAYING && nowPlaying.positionAtMs != null
    val now by produceState(System.currentTimeMillis(), ticking) {
        while (ticking) {
            delay(TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    val progress =
        nowPlayingPosition(nowPlaying, now)?.let { position ->
            val duration = nowPlaying.durationMs?.takeIf { it > 0 }
            if (duration == null) {
                formatTime(position)
            } else {
                stringResource(R.string.live_sync_now_progress, formatTime(position), formatTime(duration))
            }
        }
    var details = what
    progress?.let { details = stringResource(R.string.live_sync_now_detail, details, it) }
    if (nowPlaying.profileName.isNotBlank()) {
        details = stringResource(R.string.live_sync_now_profile, details, nowPlaying.profileName)
    }
    nowPlaying.sourceName?.takeIf { it.isNotBlank() }?.let {
        details = stringResource(R.string.live_sync_now_detail, details, it)
    }
    val line =
        when {
            nowPlaying.state == SyncPayloads.NowPlaying.PAUSED -> R.string.live_sync_now_paused
            nowPlaying.isLive -> R.string.live_sync_now_live
            else -> R.string.live_sync_now_playing
        }
    return stringResource(line, details)
}

/**
 * Where [nowPlaying] is at [nowMs]: its reported position, run on by the time since the sender
 * read it while playing (never past the length). Null for Live TV and a sender that sent none.
 */
internal fun nowPlayingPosition(
    nowPlaying: SyncPayloads.NowPlaying,
    nowMs: Long,
): Long? {
    val position = nowPlaying.positionMs ?: return null
    val at = nowPlaying.positionAtMs
    if (nowPlaying.state != SyncPayloads.NowPlaying.PLAYING || at == null) return position
    val ran = position + (nowMs - at).coerceAtLeast(0L)
    val duration = nowPlaying.durationMs?.takeIf { it > 0 }
    return if (duration == null) ran else ran.coerceAtMost(duration)
}

private const val TICK_MS = 1_000L
