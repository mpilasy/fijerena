package org.njarasoa.fijerena.feature.episode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Which episode/season this episode-selection screen is anchored on, and whether that's because
 * the user picked it (Play/Resume button, an episode's own Play, a season tab or D-pad Left/Right)
 * or because the anchor auto-select effect guessed it from watch history.
 *
 * One object with named transitions, so the "don't clobber a manual pick" rule lives in exactly one
 * place and no independently-declared field (e.g. a plain `remember` among `rememberSaveable`s,
 * forgetting the pick across navigation to the player) can drift out of sync with the rest — see
 * docs/plans/archive/20260908_episode-selection-fragility-plan.md.
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
     * The user explicitly chose to play this episode — the hero Play/Resume button, or an
     * episode's own Play in its detail panel. Always moves the resume anchor; also claims the
     * season as a manual pick when known, so the auto-select effect never second-guesses a season
     * the user just navigated to and pressed Play in.
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
     * The user explicitly switched to this season — a tab click/focus, or D-pad Left/Right from
     * inside the episode list. No-op when it's already selected, so a focus-follow-select firing
     * on the tab that's already current doesn't churn state. Returns whether it actually changed.
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
     * Background recompute from watch history (the anchor `LaunchedEffect`, re-run on every entry
     * into this screen, including a return from the player). Never overrides a manual season pick;
     * the episode id itself is always safe to update since nothing else auto-derives it.
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
    // Defaults to "a season is known", but the caller may need to distinguish a real resume
    // season from a plain fallback (e.g. "first season" when there's no resume episode at all) —
    // only the former should seed hasManuallySelectedSeason, or the anchor effect's "jump to the
    // next unwatched season" auto-select can never run for a series with no watch history yet.
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
