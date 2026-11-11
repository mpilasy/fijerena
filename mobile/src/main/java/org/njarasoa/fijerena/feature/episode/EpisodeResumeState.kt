package org.njarasoa.fijerena.feature.episode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.domain.ContentType

/**
 * The episode and season this episode-selection screen is anchored on, and whether the user picked
 * it (Play / Resume, an episode's own Play, a season tab or swipe) or the anchor effect guessed it
 * from watch history.
 *
 * One object with named transitions, so the "do not overwrite a manual pick" rule lives in one
 * place and no field can drift out of sync (see the episode selection fragility plan). TV has its
 * own EpisodeResumeState.kt: the screens are separate per platform.
 */
@Stable
class EpisodeResumeState(
    initialEpisodeId: String?,
    initialSeason: Int?,
    initialManualSeason: Boolean = initialSeason != null,
) {
    var resumeEpisodeId: String? by mutableStateOf(initialEpisodeId)
        private set
    var selectedSeason: Int? by mutableStateOf(initialSeason)
        private set
    var hasManuallySelectedSeason: Boolean by mutableStateOf(initialManualSeason)
        private set

    /**
     * The user chose to play this episode (the hero Play / Resume, or an episode's own Play in its
     * detail panel). Always moves the resume anchor; also claims the season as a manual pick when
     * known, so the auto-select effect never overrides a season the user just pressed Play in.
     */
    fun setResumeEpisode(
        episodeId: String,
        season: Int?,
    ) {
        resumeEpisodeId = episodeId
        if (season != null) {
            hasManuallySelectedSeason = true
            selectedSeason = season
        }
    }

    /**
     * The user switched to this season (a tab tap or a horizontal swipe). No-op when it is already
     * selected. Returns whether it changed.
     */
    fun selectSeason(season: Int): Boolean {
        val changed = season != selectedSeason
        if (changed) {
            hasManuallySelectedSeason = true
            selectedSeason = season
        }
        return changed
    }

    /**
     * Background recompute from watch history (the anchor `LaunchedEffect`, run again on every
     * entry, a return from the player too). Never overrides a manual season pick; the episode id is
     * always safe to update, as nothing else derives it.
     */
    fun applyAnchor(
        episodeId: String?,
        season: Int?,
    ) {
        if (episodeId != null) resumeEpisodeId = episodeId
        if (season != null && !hasManuallySelectedSeason) selectedSeason = season
    }
}

@Composable
fun rememberEpisodeResumeState(
    seriesId: String,
    initialEpisodeId: String?,
    initialSeason: Int?,
    // Defaults to "a season is known", but the caller must tell a real resume season from a
    // fallback (for example the first season with no resume episode). Only a real one may set
    // hasManuallySelectedSeason, or the "jump to the next unwatched season" auto-select never runs
    // for a series with no watch history.
    initialManualSeason: Boolean = initialSeason != null,
): EpisodeResumeState {
    val saver =
        listSaver<EpisodeResumeState, Any?>(
            save = { listOf(it.resumeEpisodeId, it.selectedSeason, it.hasManuallySelectedSeason) },
            restore = {
                EpisodeResumeState(
                    initialEpisodeId = it[0] as String?,
                    initialSeason = it[1] as Int?,
                    initialManualSeason = it[2] as Boolean,
                )
            },
        )
    return rememberSaveable(seriesId, saver = saver) {
        EpisodeResumeState(initialEpisodeId, initialSeason, initialManualSeason)
    }
}

/**
 * Whether [episodeId] has been played: a resume position or a play time, not only a watched mark.
 * The route's episode is the resume episode ("Continue watching") only then; an actor's episode
 * opens the screen on it without being one (the episode cast plan).
 */
internal suspend fun wasPlayed(
    mediaRepository: MediaRepository,
    episodeId: String,
): Boolean =
    mediaRepository
        .getPlaybackPositionSuspend(episodeId, ContentType.TV_SHOWS)
        ?.let { it.timestamp > 0L || it.playbackPosition > 0L } == true
