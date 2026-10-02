package org.njarasoa.fijerena.core.player.domain

data class ProviderCapabilities(
    val supportedContentTypes: Set<String>,
    val supportsEpg: Boolean,
    val supportsSearch: Boolean,
    val supportsAuthentication: Boolean,
    val supportsProgressSync: Boolean,
    val supportsServerUserData: Boolean = false,
    // TV-show episodes may roll on to the next one ("Play next episode automatically"). Xtream
    // only: Jellyfin keeps its own play state and up-next server-side.
    val supportsAutoplayNextEpisode: Boolean = false,
)
