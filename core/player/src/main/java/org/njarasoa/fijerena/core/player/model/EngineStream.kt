package org.njarasoa.fijerena.core.player.model

/**
 * The stream the playback engine has now and its state, process-wide (see
 * [StreamingPlaybackService.engineStream][org.njarasoa.fijerena.core.player.service.StreamingPlaybackService.engineStream]).
 * [streamUrl] is empty when the engine has no stream. In memory only: the URL can hold a login.
 */
data class EngineStream(
    val streamUrl: String = "",
    val state: PlaybackState = PlaybackState.Idle,
)
