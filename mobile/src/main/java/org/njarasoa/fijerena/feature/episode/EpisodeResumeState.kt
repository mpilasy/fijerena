package org.njarasoa.fijerena.feature.episode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver

/**
 * Which episode/season this episode-selection screen is anchored on, and whether that's because
 * the user picked it (Play/Resume button, an episode's own Play, a season tab or swipe) or
 * because the anchor auto-select effect guessed it from watch history.
 *
 * Was three independent `rememberSaveable`/`remember` vars — `resumeEpisodeId`,
 * `selectedSeasonNumber`, `hasManuallySelectedSeason` — written from several separate call sites
 * with the "don't clobber a manual pick" rule re-implemented at each one. Collecting them into one
 * object with named transitions instead of ad hoc assignments means that rule lives in exactly one
 * place, and there's no second, independently-declared field free to drift out of sync with the
 * rest — see docs/plans/episode-selection-fragility-plan.md. Mirrors TV's identical
 * EpisodeResumeState.kt (this app's screens are separate per platform, not shared, same as the
 * rest of feature/episode/).
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
     * The user explicitly switched to this season — a tab tap, or a horizontal swipe. No-op when
     * it's already selected. Returns whether it actually changed.
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
