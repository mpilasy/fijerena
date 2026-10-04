package org.njarasoa.fijerena.core.player.model

/**
 * What this device is playing, as live sync tells the other devices of the group — see
 * `docs/plans/archive/20261001_live-sync-now-playing-plan.md`. Built from the player's metadata and state
 * by [StreamingPlaybackService][org.njarasoa.fijerena.core.player.service.StreamingPlaybackService];
 * null there means nothing is playing. Never carries the stream URL: it can hold credentials.
 */
data class NowPlayingSnapshot(
    val title: String,
    val showTitle: String? = null,
    val episodeLabel: String? = null,
    val isLive: Boolean = false,
    /** Live TV only: the channel (the item's title on both platforms). */
    val channelName: String? = null,
    /** Live TV only: the programme on air, when the guide knows it. */
    val programTitle: String? = null,
    val paused: Boolean = false,
    /** VOD only, as of the last player state change. */
    val positionMs: Long? = null,
    val durationMs: Long? = null,
    /**
     * This playback's id, new on every `playStream` — what a remote Stop names, so a command can
     * only ever stop the playback it was sent for (Phase 4 of the plan).
     */
    val sessionId: String? = null,
) {
    /** Without the position: what another device shows, so what a change worth sending is. */
    fun shown(): NowPlayingSnapshot = copy(positionMs = null, durationMs = null)

    private fun sameItem(metadata: PlayerMetadata): Boolean =
        title == metadata.title && showTitle == metadata.showTitle && episodeLabel == metadata.episodeLabel && isLive == metadata.isLive

    companion object {
        /**
         * The snapshot for [metadata] in [state]: null when nothing plays (idle, ended, failed).
         * Buffering keeps [previous]'s playing or paused state when it is the same item — a
         * rebuffer isn't a change — and counts as playing at the start of a new one.
         */
        fun of(
            metadata: PlayerMetadata,
            state: PlaybackState,
            previous: NowPlayingSnapshot?,
            sessionId: String? = null,
        ): NowPlayingSnapshot? {
            val same = previous?.takeIf { it.sameItem(metadata) }
            val paused =
                when (state) {
                    is PlaybackState.Playing -> false
                    is PlaybackState.Paused -> true
                    PlaybackState.Buffering -> same?.paused ?: false
                    else -> null
                }
            val progress: Pair<Long?, Long?> =
                when (state) {
                    is PlaybackState.Playing -> state.position to state.duration
                    is PlaybackState.Paused -> state.position to state.duration
                    else -> same?.positionMs to same?.durationMs
                }
            return if (paused == null || metadata.streamUrl.isEmpty()) {
                null
            } else {
                NowPlayingSnapshot(
                    title = metadata.title,
                    showTitle = metadata.showTitle,
                    episodeLabel = metadata.episodeLabel,
                    isLive = metadata.isLive,
                    channelName = metadata.title.takeIf { metadata.isLive },
                    programTitle = metadata.programTitle.takeIf { metadata.isLive },
                    paused = paused,
                    positionMs = progress.first.takeIf { !metadata.isLive },
                    durationMs = progress.second.takeIf { !metadata.isLive },
                    sessionId = sessionId,
                )
            }
        }
    }
}
