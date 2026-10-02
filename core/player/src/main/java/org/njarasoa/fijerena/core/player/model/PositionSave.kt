package org.njarasoa.fijerena.core.player.model

/**
 * A playback position the player wants saved: every 10 s while playing, on pause/play and
 * buffering changes, on a track pick, and once more on teardown. A track index is set only on
 * the save that a track pick triggered (-1 for subtitles off). Published on
 * `StreamingPlaybackService.positionSaves`; see
 * docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-04.
 */
data class PositionSave(
    val positionMs: Long,
    val durationMs: Long,
    val isPaused: Boolean,
    val audioTrackIndex: Int? = null,
    val subtitleTrackIndex: Int? = null,
)
