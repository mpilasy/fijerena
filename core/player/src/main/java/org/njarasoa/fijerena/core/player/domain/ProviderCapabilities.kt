package org.njarasoa.fijerena.core.player.domain

data class ProviderCapabilities(
    val supportedContentTypes: Set<String>,
    val supportsEpg: Boolean,
    val supportsSearch: Boolean,
    val supportsAuthentication: Boolean,
    val supportsProgressSync: Boolean,
    val supportsServerUserData: Boolean = false,
    // TV-show episodes may roll on to the next one ("Play next episode automatically"): Xtream and
    // Jellyfin (Remote M3U has no shows).
    val supportsAutoplayNextEpisode: Boolean = false,
    // Past programmes (and the one on air from its start) play from a channel's archive. Xtream
    // only. See the catchup plan.
    val supportsCatchup: Boolean = false,
    // Search reads only what is stored on the device (Xtream's full-text index), so it needs no
    // session: a source that is slow or unreachable still finds its titles.
    val searchesLocally: Boolean = false,
)
