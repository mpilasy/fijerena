package org.njarasoa.fijerena.core.ui.sync

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.ui.R

/**
 * A devices-list line for what a device is playing, on TV and mobile alike: "▶ Playing · Malcolm X
 * · Kid", "▶ Playing · The King of Queens S1:E12 · Kid", "▶ Live · BBC World News — Newsday · Kid",
 * "⏸ Paused · …". The programme is left out when the channel has no guide data.
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
    val withProfile =
        if (nowPlaying.profileName.isBlank()) {
            what
        } else {
            stringResource(
                R.string.live_sync_now_profile,
                what,
                nowPlaying.profileName,
            )
        }
    val line =
        when {
            nowPlaying.state == SyncPayloads.NowPlaying.PAUSED -> R.string.live_sync_now_paused
            nowPlaying.isLive -> R.string.live_sync_now_live
            else -> R.string.live_sync_now_playing
        }
    return stringResource(line, withProfile)
}
